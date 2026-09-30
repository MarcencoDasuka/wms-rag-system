package com.isd.wms.dto.order;

import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import lombok.NonNull;

import java.util.List;

public record ExtendedOrderCreateRequest(
        @NonNull OrderCreateRequest order,
        @NonNull List<OrderLineCreateRequest> lines
) {
}
