package com.isd.wms.dto.order;

import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
    String logicId,
    @NotNull(message = "Destination location id is required")
    Long destinationLocationId
) {
}
