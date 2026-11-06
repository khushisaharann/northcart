package com.northcart.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCustomerStatusRequest(@NotNull Boolean enabled) {
}
