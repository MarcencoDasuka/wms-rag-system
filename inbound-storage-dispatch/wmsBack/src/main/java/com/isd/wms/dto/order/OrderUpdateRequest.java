package com.isd.wms.dto.order;

import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import lombok.NonNull;

public record OrderUpdateRequest(
        String logicId,
        @NonNull Long destinationLocationId,
        @NonNull OrderStatus status
) {
}
