package com.northcart.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminCustomerResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        boolean enabled,
        long orderCount,
        BigDecimal totalSpent,   // paid, not cancelled
        Instant memberSince
) {
}
