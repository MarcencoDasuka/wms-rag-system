package com.isd.wms.dto.order;

import com.isd.wms.enums.OrderStatus;
import java.time.LocalDateTime;

public record OrderFilterRequest(
    Long orderId,
    String logicId,
    Long destinationLocationId,
    OrderStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public OrderSearchRequest toSearchRequest() {
        return new OrderSearchRequest(orderId, logicId, destinationLocationId, status, createdAt, updatedAt);
    }
}
