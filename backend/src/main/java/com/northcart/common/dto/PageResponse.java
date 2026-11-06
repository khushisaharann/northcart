package com.northcart.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable JSON shape for paginated results.
 *
 * <p>Why not return Spring's Page directly? Its JSON layout is an implementation detail
 * (Spring Data itself warns against serialising PageImpl), and it exposes fields the
 * frontend doesn't need. This record is our contract.</p>
 */
public record PageResponse<T>(
        List<T> content,
        int page,           // zero-based
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    /** Converts a page of entities into a page of DTOs. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
