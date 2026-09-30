package com.isd.wms.mapper;

import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.entity.*;
import com.isd.wms.repository.TransportUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReplenishmentMapper {

    private final TransportUnitRepository transportUnitRepository;

    public ReplenishmentResponse toResponse(Replenishment replenishment) {
        Product product = replenishment.getProduct();
        Location destinationLocation = replenishment.getDestinationLocation();

        Long operatorId = replenishment.getTask()
            .flatMap(Task::getOperator)
            .map(User::getId)
            .orElse(null);

        String tuBarcode = transportUnitRepository.findFirstByReplenishmentOrderByCreatedAtAscIdAsc(replenishment)
            .map(TransportUnit::getBarcode)
            .orElse(null);

        return new ReplenishmentResponse(
            replenishment.getId(),
            replenishment.getLogicId(),
            replenishment.getTask()
                .map(Task::getId)
                .orElse(null),
            product.getId(),
            replenishment.getRequestedQuantity(),
            replenishment.getStatus(),
            destinationLocation.getId(),
            operatorId,
            tuBarcode,
            replenishment.getCreatedAt()
        );
    }
}
