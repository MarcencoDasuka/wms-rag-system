package com.isd.wms.dto.order_line;

import jakarta.validation.constraints.Min;
import lombok.NonNull;

public record OrderLineCreateRequest(
    Long orderId,
    @NonNull Long productId,
    @NonNull @Min(value = 0, message = "Minimal amount for requested quantity is 0.")
    Integer requestedQuantity
) {
    public OrderLineCreateRequest(OrderLineCreateRequest request, Long orderId) {
        this(orderId, request.productId(), request.requestedQuantity());
    }
}
