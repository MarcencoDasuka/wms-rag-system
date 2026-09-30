package com.isd.wms.mapper;

import com.isd.wms.dto.order_line.OrderLineResponse;
import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderLineMapper {
    public OrderLineResponse toResponse(OrderLine orderLine
    ) {
        Long taskId = orderLine.getTask().map(Task::getId).orElse(null);
        return new OrderLineResponse(
                orderLine.getId(),
                orderLine.getOrder().getId(),
                taskId,
                orderLine.getProduct().getId(),
                orderLine.getProduct().getName(),
                orderLine.getRequestedQuantity(),
                resolveDeliveredQuantity(orderLine),
                orderLine.getStatus(),
                orderLine.getCreatedAt(),
                orderLine.getUpdatedAt()
        );
    }

    private Integer resolveDeliveredQuantity(OrderLine orderLine) {
        if (orderLine.getOrder().getStatus() != OrderStatus.COMPLETED
            && orderLine.getOrder().getStatus() != OrderStatus.PARTIALLY_COMPLETED) {
            return 0;
        }

        Integer deliveredQuantity = orderLine.getDeliveredQuantity();
        if (deliveredQuantity != null && deliveredQuantity > 0) {
            return deliveredQuantity;
        }

        return orderLine.getTask()
            .map(task -> task.getAllocations().stream()
                .filter(a -> a.getStatus() != Status.CANCELED)
                .mapToInt(a -> Optional.ofNullable(a.getQuantity()).orElse(0))
                .sum()
            )
            .orElse(0);
    }
}
