package com.isd.wms.mapper;

import com.isd.wms.dto.order.ExtendedOrderResponse;
import com.isd.wms.entity.Order;
import com.isd.wms.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ExtendedOrderMapper {
    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;

    public ExtendedOrderResponse toResponse(Order order, @Nullable Long operatorId, @Nullable String tuBarcode) {
        return new ExtendedOrderResponse(
            orderMapper.toResponse(order, operatorId, tuBarcode),
            order.getOrderLines().stream().map(orderLineMapper::toResponse).toList(),
            resolveTotalDeliveredQuantity(order)
        );
    }

    public ExtendedOrderResponse toResponse(Order order, @Nullable Long operatorId) {
        return toResponse(order, operatorId, null);
    }

    private int resolveTotalDeliveredQuantity(Order order) {
        if (order.getStatus() != OrderStatus.COMPLETED
            && order.getStatus() != OrderStatus.PARTIALLY_COMPLETED) {
            return 0;
        }

        return order.getOrderLines().stream()
            .mapToInt(orderLine -> Optional.ofNullable(orderLine.getDeliveredQuantity()).orElse(0))
            .sum();
    }
}
