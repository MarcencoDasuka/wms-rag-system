package com.isd.wms.dto.order;

import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExtendedOrderCreateRequest(
        @NotNull(message = "Order details are required")
        @Valid
        OrderCreateRequest order,

        @NotNull(message = "Order lines are required")
        @NotEmpty(message = "Order lines must not be empty")
        List<@Valid OrderLineCreateRequest> lines
) {
}
