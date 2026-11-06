package com.northcart.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AdminCustomerSearchParams(
        @Schema(description = "Name or email (contains)") @Size(max = 100) String q,
        @Min(0) Integer page,
        @Min(1) @Max(50) Integer size
) {
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
