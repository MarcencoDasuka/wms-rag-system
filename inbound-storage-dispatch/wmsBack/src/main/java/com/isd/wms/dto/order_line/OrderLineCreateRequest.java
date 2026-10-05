package com.isd.wms.dto.order_line;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderLineCreateRequest(
    Long orderId,
    @NotNull(message = "Product id is required")
    Long productId,
    @NotNull(message = "Requested quantity is required")
    @Min(value = 0, message = "Minimal amount for requested quantity is 0.")
    Integer requestedQuantity
) {
    public OrderLineCreateRequest(OrderLineCreateRequest request, Long orderId) {
        this(orderId, request.productId(), request.requestedQuantity());
    }
}
