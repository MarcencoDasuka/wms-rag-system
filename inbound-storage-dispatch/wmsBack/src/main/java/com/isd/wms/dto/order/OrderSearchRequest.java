package com.isd.wms.dto.order;

import com.isd.wms.enums.OrderStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public record OrderSearchRequest(
        Long orderId,
        String logicId,
        Long destinationLocationId,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
