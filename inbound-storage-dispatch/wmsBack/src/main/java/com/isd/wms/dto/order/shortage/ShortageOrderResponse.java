package com.isd.wms.dto.order.shortage;

import java.time.LocalDateTime;

public record ShortageOrderResponse(
        Long orderId,
        String orderNumber,
        String destination,
        String status,
        Integer totalLines,
        Integer shortageLines,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
