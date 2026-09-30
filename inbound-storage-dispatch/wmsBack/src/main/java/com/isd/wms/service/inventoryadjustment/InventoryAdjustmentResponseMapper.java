package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.dto.inventory.InventoryAdjustmentResponse;
import com.isd.wms.dto.inventory.StockResponse;
import com.isd.wms.dto.order.shortage.AffectedOrderLineResponse;
import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.entity.Stock;
import com.isd.wms.enums.Status;
import com.isd.wms.mapper.StockMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryAdjustmentResponseMapper {

    private final StockMapper stockMapper;

    public InventoryAdjustmentResponse toResponse(InventoryAdjustmentPlan plan, boolean preview, Long inventoryHistoryId) {
        List<AffectedOrderLineResponse> affectedOrderLines = plan.affectedTasks().stream()
            .map(this::toAffectedOrderLineResponse)
            .toList();

        List<ShortageOrderResponse> affectedOrders = plan.affectedTasks().stream()
            .collect(Collectors.groupingBy(AffectedTaskAdjustment::orderId, LinkedHashMap::new, Collectors.toList()))
            .values()
            .stream()
            .map(this::toShortageOrderResponse)
            .toList();

        boolean reallocationSucceeded = plan.reallocationSucceeded();
        boolean partialShortageCreated = affectedOrderLines.stream()
            .anyMatch(line -> Status.PARTIALLY_COMPLETED.name().equals(line.status()));
        boolean orderCancelled = affectedOrderLines.stream()
            .anyMatch(line -> Status.CANCELED.name().equals(line.status()));

        return new InventoryAdjustmentResponse(
            toStockResponse(plan.context().stock(), plan.context().newQuantity(), plan.preservedQuantityOnAdjustedStock()),
            plan.context().previousQuantity(),
            plan.context().newQuantity(),
            plan.context().quantityDifference(),
            plan.context().request().reason().name(),
            plan.context().request().comment(),
            inventoryHistoryId,
            preview,
            reallocationSucceeded,
            partialShortageCreated,
            orderCancelled,
            buildMessage(affectedOrders, reallocationSucceeded, partialShortageCreated, orderCancelled),
            affectedOrders,
            affectedOrderLines
        );
    }

    private String buildMessage(
        List<ShortageOrderResponse> affectedOrders,
        boolean reallocationSucceeded,
        boolean partialShortageCreated,
        boolean orderCancelled
    ) {
        if (orderCancelled && affectedOrders.stream().allMatch(order -> order.shortageLines().equals(order.totalLines()))) {
            return "Order cancelled because no lines can be fulfilled.";
        }
        if (partialShortageCreated) {
            return "Partial completion created and affected order lines were recalculated.";
        }
        if (reallocationSucceeded) {
            return "Reallocation succeeded for all affected order lines.";
        }
        return "Inventory adjustment completed.";
    }

    private AffectedOrderLineResponse toAffectedOrderLineResponse(AffectedTaskAdjustment affectedTask) {
        return new AffectedOrderLineResponse(
            affectedTask.orderId(),
            affectedTask.orderNumber(),
            affectedTask.orderLineId(),
            affectedTask.taskId(),
            affectedTask.orderLine().getProduct().getId(),
            affectedTask.orderLine().getProduct().getName(),
            affectedTask.requestedQuantity(),
            affectedTask.finalAllocatedQuantity(),
            affectedTask.shortageQuantity(),
            affectedTask.originalLocationId(),
            affectedTask.originalLocationBarcode(),
            affectedTask.reallocatedLocationId(),
            affectedTask.reallocatedLocationBarcode(),
            affectedTask.lineStatus().name(),
            affectedTask.revalidationRequired(),
            affectedTask.orderCreatedAt(),
            affectedTask.orderUpdatedAt()
        );
    }

    private ShortageOrderResponse toShortageOrderResponse(List<AffectedTaskAdjustment> affectedTasks) {
        AffectedTaskAdjustment firstAffectedTask = affectedTasks.getFirst();
        long shortageLines = affectedTasks.stream()
            .filter(task -> task.shortageQuantity() > 0 || task.lineStatus() == Status.CANCELED)
            .count();

        return new ShortageOrderResponse(
            firstAffectedTask.orderId(),
            firstAffectedTask.orderNumber(),
            firstAffectedTask.orderLine().getOrder().getDestinationLocation().getBarcode(),
            firstAffectedTask.orderLine().getOrder().getStatus().name(),
            affectedTasks.size(),
            Math.toIntExact(shortageLines),
            firstAffectedTask.orderCreatedAt(),
            firstAffectedTask.orderUpdatedAt()
        );
    }

    private StockResponse toStockResponse(Stock stock, int quantity, int reservedQuantity) {
        Stock snapshot = new Stock();
        snapshot.setId(stock.getId());
        snapshot.setQuantity(quantity);
        snapshot.setReservedQuantity(reservedQuantity);
        snapshot.setManufactureDate(stock.getManufactureDate());
        snapshot.setExpirationDate(stock.getExpirationDate());
        snapshot.setLocation(stock.getLocation());
        snapshot.setProduct(stock.getProduct().orElse(null));
        return stockMapper.toResponse(snapshot);
    }
}
