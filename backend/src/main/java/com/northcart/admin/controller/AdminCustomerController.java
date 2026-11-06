package com.northcart.admin.controller;

import com.northcart.admin.dto.AdminCustomerResponse;
import com.northcart.admin.dto.AdminCustomerSearchParams;
import com.northcart.admin.dto.UpdateCustomerStatusRequest;
import com.northcart.admin.service.AdminCustomerService;
import com.northcart.common.dto.PageResponse;
import com.northcart.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Customers")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    @Operation(summary = "Customers with order count and total spent; search by name or email")
    @GetMapping
    public PageResponse<AdminCustomerResponse> search(@Valid @ParameterObject AdminCustomerSearchParams params) {
        return adminCustomerService.search(params);
    }

    @Operation(summary = "Enable or disable a customer account (takes effect on their next request)")
    @PatchMapping("/{id}/status")
    public AdminCustomerResponse setStatus(@PathVariable Long id, @Valid @RequestBody UpdateCustomerStatusRequest request) {
        return adminCustomerService.setEnabled(id, request.enabled());
    }
}
