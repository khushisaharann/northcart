package com.northcart.catalog.controller;

import com.northcart.catalog.dto.ProductDetailResponse;
import com.northcart.catalog.dto.ProductSearchParams;
import com.northcart.catalog.dto.ProductSummaryResponse;
import com.northcart.catalog.service.ProductService;
import com.northcart.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog (no token needed: GET /api/v1/products/** is permitAll in SecurityConfig). */
@Tag(name = "Products")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Search, filter, sort and paginate products")
    @GetMapping
    public PageResponse<ProductSummaryResponse> search(@Valid @ParameterObject ProductSearchParams params) {
        return productService.search(params);
    }

    @Operation(summary = "Product details by slug")
    @GetMapping("/{slug}")
    public ProductDetailResponse getBySlug(@PathVariable String slug) {
        return productService.getBySlug(slug);
    }
}
