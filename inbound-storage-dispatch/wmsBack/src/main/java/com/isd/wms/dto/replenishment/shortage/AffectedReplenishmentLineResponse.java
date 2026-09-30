package com.isd.wms.dto.replenishment.shortage;

import java.time.LocalDateTime;

public record AffectedReplenishmentLineResponse(
        Long replenishmentId,
        Long taskId,
        Long productId,
        String productName,
        Integer requestedQuantity,
        Integer deliveredQuantity,
        Integer shortageQuantity,
        Long originalLocationId,
        String originalLocationBarcode,
        Long destinationLocationId,
        String destinationLocationBarcode,
        String status,
        boolean revalidationRequired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
