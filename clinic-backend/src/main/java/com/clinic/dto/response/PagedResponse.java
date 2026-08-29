package com.clinic.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Pagination shape mandated by API contract 6: content plus page, size,
 * totalElements and totalPages.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
