package com.isd.wms.dto.replenishment;

import com.isd.wms.enums.Status;
import jakarta.validation.constraints.Min;
import lombok.NonNull;

public record ReplenishmentUpdateRequest(
        Long taskId,
        @NonNull Long productId,
        @NonNull @Min(0) Integer requestedQuantity,
        Status status,
        @NonNull Long destinationLocationId
) {
}
