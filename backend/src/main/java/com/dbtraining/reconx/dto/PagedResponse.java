package com.dbtraining.reconx.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * TICKET-ADV053 — Tiny wrapper that flattens Spring Data Page<T> into a
 * JSON-friendly shape. Avoids exposing Spring Data internals to clients.
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * Projects a page of entities into a page of DTOs, so no entity reaches the
     * serialiser. The mapper runs wherever the caller calls this — with
     * {@code open-in-view: false} a mapper that walks LAZY relations must be
     * handed a page whose associations the query already fetched.
     */
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /** Kept for callers written before {@link #of(Page, Function)} existed. */
    public static <S, T> PagedResponse<T> from(Page<S> src, Function<S, T> mapper) {
        return of(src, mapper);
    }
}
