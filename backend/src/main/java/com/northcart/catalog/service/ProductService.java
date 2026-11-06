package com.northcart.catalog.service;

import com.northcart.catalog.dto.ProductDetailResponse;
import com.northcart.catalog.dto.ProductSearchCriteria;
import com.northcart.catalog.dto.ProductSearchParams;
import com.northcart.catalog.dto.ProductSummaryResponse;
import com.northcart.catalog.entity.Product;
import com.northcart.catalog.mapper.ProductMapper;
import com.northcart.catalog.repository.ProductRepository;
import com.northcart.catalog.repository.ProductSearchRepository;
import com.northcart.common.dto.PageResponse;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.inventory.entity.Inventory;
import com.northcart.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Customer-facing catalog reads. Only active products in active categories are visible. */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final InventoryService inventoryService;

    /**
     * Three queries per page, regardless of page size (no N+1):
     *   1. ids of the page (+ count)   2. products with category & images   3. their inventory rows
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchParams params) {
        ProductSearchCriteria criteria = ProductSearchCriteria.forCustomer(params);
        Page<Long> idPage = searchRepository.searchIds(criteria, params.pageOrDefault(), params.sizeOrDefault());

        Map<Long, Product> products = loadProducts(idPage);
        Map<Long, Inventory> stock = inventoryService.findByProductIds(idPage.getContent());

        // Map in the ORDER of the id page; the IN (...) query returns rows in arbitrary order.
        return PageResponse.from(idPage, id -> ProductMapper.toSummary(products.get(id), stock.get(id)));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .filter(p -> p.getCategory().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        return ProductMapper.toDetail(product, inventoryService.getForProduct(product.getId()));
    }

    /** Used by cart and checkout (Phases 6-7): a product customers can buy, or 404. */
    @Transactional(readOnly = true)
    public Product getActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(ProductService::isPurchasable)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    /** Called by orders: +quantity when an order is placed, -quantity when it is cancelled. */
    @Transactional
    public void recordSale(Long productId, int quantityDelta) {
        productRepository.addToSoldCount(productId, quantityDelta);
    }

    /** Visible to customers = product active AND its category active. */
    public static boolean isPurchasable(Product product) {
        return product.isActive() && product.getCategory().isActive();
    }

    private Map<Long, Product> loadProducts(Page<Long> idPage) {
        if (idPage.getContent().isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllWithDetailsByIdIn(idPage.getContent()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
