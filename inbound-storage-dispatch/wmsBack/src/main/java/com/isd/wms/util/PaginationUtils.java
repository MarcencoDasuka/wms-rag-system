package com.isd.wms.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Universal pagination utility for normalizing client pageable requests
 * and converting Page results into flat List responses with pagination headers.
 */
public final class PaginationUtils {

    public static final int ORDER_DEFAULT_SIZE = 100;
    public static final int ORDER_MAX_SIZE = 500;

    public static final int INVENTORY_DEFAULT_SIZE = 200;
    public static final int INVENTORY_MAX_SIZE = 1000;

    public static final int USER_DEFAULT_SIZE = 100;
    public static final int USER_MAX_SIZE = 500;

    public static final int PRODUCT_DEFAULT_SIZE = 100;
    public static final int PRODUCT_MAX_SIZE = 500;

    private PaginationUtils() {}

    /**
     * Normalizes a client Pageable: enforces non-negative page number and clamps page size
     * between 1 and maxSize (falling back to defaultSize if non-positive or unpaged).
     */
    public static Pageable clampPageable(Pageable pageable, int defaultSize, int maxSize) {
        int pageNumber = 0;
        int pageSize = defaultSize;
        Sort sort = Sort.by("id").ascending();

        if (pageable != null && pageable.isPaged()) {
            pageNumber = Math.max(0, pageable.getPageNumber());
            int requestedSize = pageable.getPageSize();
            if (requestedSize <= 0) {
                pageSize = defaultSize;
            } else {
                pageSize = Math.min(requestedSize, maxSize);
            }
            if (pageable.getSort().isSorted()) {
                sort = pageable.getSort();
            }
        }

        return PageRequest.of(pageNumber, pageSize, sort);
    }

    /**
     * Generates standard pagination headers according to DEF-07 requirements.
     */
    public static HttpHeaders createPaginationHeaders(Page<?> page) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Total-Count", String.valueOf(page.getTotalElements()));
        headers.set("X-Total-Pages", String.valueOf(page.getTotalPages()));
        headers.set("X-Current-Page", String.valueOf(page.getNumber()));
        headers.set("X-Page-Size", String.valueOf(page.getSize()));
        return headers;
    }

    /**
     * Packages a Spring Data Page into a ResponseEntity containing a flat List body
     * and the standard pagination metadata headers, preserving frontend JSON-array contracts.
     */
    public static <T> ResponseEntity<List<T>> toPagedResponse(Page<T> page) {
        return ResponseEntity.ok()
                .headers(createPaginationHeaders(page))
                .body(page.getContent());
    }
}
