package com.isd.wms.dto.product;

import java.time.LocalDateTime;

public record ProductResponse(
    Long id,
    String name,
    String barcode,
    String description,
    Long categoryId,
    Boolean autoReplenish,
    Integer minThreshold,
    Integer replenishQty,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
