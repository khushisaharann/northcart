package com.northcart.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/** Images are referenced by URL (e.g. hosted on a CDN); the list order becomes sort_order. */
public record ProductImageRequest(
        @Schema(example = "https://picsum.photos/seed/mouse/800/800")
        @NotBlank @Size(max = 500) @URL(protocol = "https", message = "must be an https URL")
        String url,
        @Schema(example = "Wireless mouse, top view") @Size(max = 150) String altText
) {
}
