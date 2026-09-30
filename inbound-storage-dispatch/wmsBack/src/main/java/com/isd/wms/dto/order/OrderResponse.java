package com.isd.wms.dto.order;

import com.isd.wms.enums.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
    Long id,
    String logicId,
    Long destinationLocationId,
    OrderStatus Status,
    Long assignedOperatorId,
    String transportUnitBarcode,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public OrderResponse(
        Long id,
        String logicId,
        Long destinationLocationId,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this(id, logicId, destinationLocationId, status, null, null, createdAt, updatedAt);
    }

    public OrderStatus status() {
        return Status;
    }
}
