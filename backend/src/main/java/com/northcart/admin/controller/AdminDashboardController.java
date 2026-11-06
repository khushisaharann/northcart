package com.northcart.admin.controller;

import com.northcart.admin.dto.DashboardResponse;
import com.northcart.admin.service.AdminDashboardService;
import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.ErrorCode;
import com.northcart.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All /api/v1/admin/** endpoints require ROLE_ADMIN (SecurityConfig). */
@Tag(name = "Admin - Dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "KPIs, daily sales for the last N days (7-90), best sellers, low stock, recent orders")
    @GetMapping
    public DashboardResponse dashboard(@RequestParam(defaultValue = "30") int days) {
        if (days < 7 || days > 90) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "days must be between 7 and 90");
        }
        return dashboardService.dashboard(days);
    }
}
