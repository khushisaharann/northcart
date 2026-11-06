package com.northcart.catalog.dto;

/** Small category reference embedded in product responses. */
public record CategorySummary(Long id, String name, String slug) {
}
