package com.isd.wms.mapper;

import com.isd.wms.dto.order.OrderResponse;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.TransportUnit;
import com.isd.wms.repository.TransportUnitRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final TransportUnitRepository transportUnitRepository;

    public OrderResponse toResponse(Order order, @Nullable Long operatorId) {
        String tuBarcode = transportUnitRepository.findByOrder(order)
            .map(TransportUnit::getBarcode)
            .orElse(null);

        return new OrderResponse(
            order.getId(),
            order.getLogicId(),
            order.getDestinationLocation().getId(),
            order.getStatus(),
            operatorId,
            tuBarcode,
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
