package com.isd.wms.dto.replenishment;

import jakarta.validation.constraints.Min;
import lombok.NonNull;

public record ReplenishmentCreateRequest(
        @NonNull Long productId,
        @NonNull @Min(0) Integer requestedQuantity,
        @NonNull Long destinationLocationId
) {
}
