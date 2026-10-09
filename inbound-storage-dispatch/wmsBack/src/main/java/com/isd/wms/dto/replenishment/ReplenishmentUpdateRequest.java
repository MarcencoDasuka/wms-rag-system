package com.isd.wms.dto.replenishment;

import com.isd.wms.enums.Status;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReplenishmentUpdateRequest(
        Long taskId,
        @NotNull(message = "Product ID is required") Long productId,
        @NotNull(message = "Requested quantity is required") @Min(value = 1, message = "Requested quantity must be at least 1") Integer requestedQuantity,
        Status status,
        @NotNull(message = "Destination location ID is required") Long destinationLocationId
) {
}
