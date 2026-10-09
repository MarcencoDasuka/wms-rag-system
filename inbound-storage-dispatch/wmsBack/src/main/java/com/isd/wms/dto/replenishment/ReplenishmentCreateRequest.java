package com.isd.wms.dto.replenishment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReplenishmentCreateRequest(
        @NotNull(message = "Product ID is required") Long productId,
        @NotNull(message = "Requested quantity is required") @Min(value = 1, message = "Requested quantity must be at least 1") Integer requestedQuantity,
        @NotNull(message = "Destination location ID is required") Long destinationLocationId
) {
}
