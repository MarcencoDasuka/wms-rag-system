package com.isd.wms.dto.order.shortage;

import java.time.LocalDateTime;

public record AffectedOrderLineResponse(
        Long orderId,
        String orderNumber,
        Long orderLineId,
        Long taskId,
        Long productId,
        String productName,
        Integer requestedQuantity,
        Integer deliveredQuantity,
        Integer shortageQuantity,
        Long originalLocationId,
        String originalLocationBarcode,
        Long reallocatedLocationId,
        String reallocatedLocationBarcode,
        String status,
        boolean revalidationRequired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
