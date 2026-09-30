package com.isd.wms.dto.replenishment;

import com.isd.wms.enums.Status;

public record ReplenishmentSearchRequest(
        Long id,
        Long taskId,
        Long productId,
        Integer requestedQuantity,
        Status status,
        Long destinationLocationId
) {
}
