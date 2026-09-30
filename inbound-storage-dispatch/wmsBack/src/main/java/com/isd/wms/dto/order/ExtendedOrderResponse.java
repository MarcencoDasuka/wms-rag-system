package com.isd.wms.dto.order;

import com.isd.wms.dto.order_line.OrderLineResponse;

import java.util.List;

public record ExtendedOrderResponse(
        OrderResponse order,
        List<OrderLineResponse> lines,
        Integer totalDeliveredQuantity
) {
}
