package com.northcart.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The slug is generated from the name; clients don't choose it. */
public record CreateCategoryRequest(
        @Schema(example = "Electronics") @NotBlank @Size(max = 80) String name,
        @Schema(example = "Gadgets and accessories") @Size(max = 500) String description
) {
}
