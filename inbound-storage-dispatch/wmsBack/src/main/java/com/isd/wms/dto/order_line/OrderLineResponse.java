package com.isd.wms.dto.order_line;

import com.isd.wms.enums.Status;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public record OrderLineResponse(
        Long orderLineId,
        Long orderId,
        Long taskId,
        Long productId,
        String productName,
        Integer requestedQuantity,
        Integer deliveredQuantity,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
