package com.northcart.catalog.service;

import com.northcart.catalog.dto.AdminProductResponse;
import com.northcart.catalog.dto.AdminProductSearchParams;
import com.northcart.catalog.dto.CreateProductRequest;
import com.northcart.catalog.dto.ProductImageRequest;
import com.northcart.catalog.dto.ProductSearchCriteria;
import com.northcart.catalog.dto.UpdateProductRequest;
import com.northcart.catalog.entity.Product;
import com.northcart.catalog.entity.ProductImage;
import com.northcart.catalog.mapper.ProductMapper;
import com.northcart.catalog.repository.ProductRepository;
import com.northcart.catalog.repository.ProductSearchRepository;
import com.northcart.common.dto.PageResponse;
import com.northcart.common.exception.DuplicateResourceException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.common.util.SlugUtils;
import com.northcart.inventory.entity.Inventory;
import com.northcart.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin product management. Products are never hard-deleted: set active=false instead. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> list(AdminProductSearchParams params) {
        Page<Long> idPage = searchRepository.searchIds(ProductSearchCriteria.forAdmin(params),
                params.pageOrDefault(), params.sizeOrDefault());
        Map<Long, Product> products = idPage.getContent().isEmpty() ? Map.of()
                : productRepository.findAllWithDetailsByIdIn(idPage.getContent()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Inventory> stock = inventoryService.findByProductIds(idPage.getContent());
        return PageResponse.from(idPage, id -> ProductMapper.toAdmin(products.get(id), stock.get(id)));
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        Product product = findWithDetails(id);
        return ProductMapper.toAdmin(product, inventoryService.getForProduct(id));
    }

    /** Product + inventory row are created in ONE transaction: never a product without stock data. */
    @Transactional
    public AdminProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("A product with this SKU already exists");
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setSlug(SlugUtils.uniqueSlug(product.getName(), productRepository::existsBySlug));
        product.setDescription(trimToNull(request.description()));
        product.setPrice(request.price());
        product.setCategory(categoryService.getCategory(request.categoryId()));
        product.setActive(request.active() == null || request.active());
        replaceImages(product, request.images());
        productRepository.save(product); // cascades to images

        Inventory inventory = inventoryService.createFor(product, request.initialStock(), request.lowStockThreshold());
        log.info("Product created: id={}, sku={}", product.getId(), sku);
        return ProductMapper.toAdmin(product, inventory);
    }

    @Transactional
    public AdminProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findWithDetails(id);
        product.setName(request.name().trim());
        product.setDescription(trimToNull(request.description()));
        product.setPrice(request.price()); // past orders are unaffected: they hold a price snapshot
        product.setCategory(categoryService.getCategory(request.categoryId()));
        product.setActive(request.active());
        replaceImages(product, request.images());

        inventoryService.updateLowStockThreshold(id, request.lowStockThreshold());
        return ProductMapper.toAdmin(product, inventoryService.getForProduct(id));
    }

    // ------------------------------------------------------------------

    private Product findWithDetails(Long id) {
        return productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    /** Clearing the list deletes the old rows (orphanRemoval); the new list order = sort_order. */
    private static void replaceImages(Product product, List<ProductImageRequest> images) {
        List.copyOf(product.getImages()).forEach(product::removeImage);
        if (images == null) {
            return;
        }
        for (int i = 0; i < images.size(); i++) {
            ProductImageRequest img = images.get(i);
            product.addImage(new ProductImage(img.url().trim(), trimToNull(img.altText()), i));
        }
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
