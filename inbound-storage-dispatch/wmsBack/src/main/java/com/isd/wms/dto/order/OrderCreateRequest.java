package com.isd.wms.dto.order;

import lombok.NonNull;

public record OrderCreateRequest(
    String logicId,
    @NonNull Long destinationLocationId
) {
}
