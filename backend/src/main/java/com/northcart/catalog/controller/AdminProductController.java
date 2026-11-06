package com.northcart.catalog.controller;

import com.northcart.catalog.dto.AdminProductResponse;
import com.northcart.catalog.dto.AdminProductSearchParams;
import com.northcart.catalog.dto.CreateProductRequest;
import com.northcart.catalog.dto.UpdateProductRequest;
import com.northcart.catalog.service.AdminProductService;
import com.northcart.common.dto.PageResponse;
import com.northcart.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Products")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @Operation(summary = "List all products incl. inactive; lowStock=true for inventory alerts")
    @GetMapping
    public PageResponse<AdminProductResponse> list(@Valid @ParameterObject AdminProductSearchParams params) {
        return adminProductService.list(params);
    }

    @Operation(summary = "Get one product for the edit form")
    @GetMapping("/{id}")
    public AdminProductResponse get(@PathVariable Long id) {
        return adminProductService.get(id);
    }

    @Operation(summary = "Create a product with its initial stock")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return adminProductService.create(request);
    }

    @Operation(summary = "Update details, price, category, images, active flag (set active=false to hide)")
    @PutMapping("/{id}")
    public AdminProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return adminProductService.update(id, request);
    }
}
