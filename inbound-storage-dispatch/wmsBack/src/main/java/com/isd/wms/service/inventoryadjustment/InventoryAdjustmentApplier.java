package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.InventoryHistory;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskStatus;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.StockNotFoundException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.InventoryHistoryRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryAdjustmentApplier {

    private static final List<Status> INACTIVE_ALLOCATION_STATUSES = List.of(Status.COMPLETED, Status.CANCELED);

    private final StockRepository stockRepository;
    private final AllocationRepository allocationRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderRepository orderRepository;
    private final TaskRepository taskRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;

    public Long applyAdjustmentPlan(InventoryAdjustmentPlan plan) {
        InventoryAdjustmentContext context = plan.context();
        Stock stock = context.stock();
        stock.setQuantity(context.newQuantity());
        stock.setReservedQuantity(plan.preservedQuantityOnAdjustedStock());
        stock.updateDate(context.request().manufactureDate(), context.request().expirationDate());
        stockRepository.save(stock);

        List<Allocation> allocationsToSave = updateAdjustedStockAllocations(context);
        Map<Long, Stock> stocksById = loadStocksById(plan);
        List<Stock> stocksToSave = new ArrayList<>();
        List<OrderLine> linesToSave = new ArrayList<>();
        List<Task> tasksToSave = new ArrayList<>();
        List<Long> impactedOrderIds = new ArrayList<>();

        for (AffectedTaskAdjustment affectedTask : plan.affectedTasks()) {
            updateOrderLine(affectedTask);
            linesToSave.add(affectedTask.orderLine());

            if (affectedTask.revalidationRequired()) {
                affectedTask.task().setStatus(TaskStatus.REQUIRES_REVALIDATION);
                tasksToSave.add(affectedTask.task());
            }

            logLineImpact(affectedTask);

            for (ReallocationPlanItem reallocationPlanItem : affectedTask.reallocationPlan()) {
                Stock alternativeStock = stocksById.get(reallocationPlanItem.stockId());
                if (alternativeStock == null) {
                    throw new StockNotFoundException(reallocationPlanItem.stockId());
                }

                applyReallocation(affectedTask, reallocationPlanItem, alternativeStock, allocationsToSave, stocksToSave);
                log.info("Reallocation created: taskId={}, orderLineId={}, stockId={}, quantity={}",
                    affectedTask.taskId(), affectedTask.orderLineId(), reallocationPlanItem.stockId(), reallocationPlanItem.quantity());
            }

            impactedOrderIds.add(affectedTask.orderId());
        }

        allocationRepository.saveAll(allocationsToSave);
        stockRepository.saveAll(stocksToSave);
        orderLineRepository.saveAll(linesToSave);
        taskRepository.saveAll(tasksToSave);
        saveImpactedOrders(impactedOrderIds);

        InventoryHistory history = new InventoryHistory(
            stock.getProduct().orElse(null),
            stock.getProduct().map(Product::getBarcode).orElse(null),
            context.quantityDifference(),
            context.newQuantity(),
            context.previousQuantity(),
            stock.getLocation(),
            stock.getLocation(),
            InventoryOperationType.ADJUST_STOCK,
            context.request().reason(),
            context.request().comment(),
            context.user()
        );
        history.setTimestamp(LocalDateTime.now());
        InventoryHistory savedHistory = inventoryHistoryRepository.save(history);
        log.info("Inventory history created: stockId={}, historyId={}", context.stockId(), savedHistory.getId());
        return savedHistory.getId();
    }

    private List<Allocation> updateAdjustedStockAllocations(InventoryAdjustmentContext context) {
        List<Allocation> allocationsToSave = new ArrayList<>();
        List<Allocation> adjustedStockAllocations = allocationRepository.findActiveByStockId(
                context.stockId(),
                INACTIVE_ALLOCATION_STATUSES
            ).stream()
            .sorted(Comparator.comparing(Allocation::getCreatedAt).thenComparing(Allocation::getId))
            .toList();

        int remainingCapacity = context.newQuantity();
        for (Allocation allocation : adjustedStockAllocations) {
            int originalQuantity = InventoryAdjustmentSupport.nullSafeQuantity(allocation.getQuantity());
            int preservedQuantity = Math.min(originalQuantity, remainingCapacity);
            remainingCapacity -= preservedQuantity;

            if (preservedQuantity <= 0) {
                allocation.setStatus(Status.CANCELED);
                allocation.setQuantity(0);
            } else {
                allocation.setQuantity(preservedQuantity);
            }
            allocationsToSave.add(allocation);
        }
        return allocationsToSave;
    }

    private Map<Long, Stock> loadStocksById(InventoryAdjustmentPlan plan) {
        List<Long> stockIds = plan.affectedTasks().stream()
            .flatMap(task -> task.reallocationPlan().stream())
            .map(ReallocationPlanItem::stockId)
            .distinct()
            .toList();

        Map<Long, Stock> stocksById = new LinkedHashMap<>();
        stockRepository.findAllById(stockIds).forEach(stock -> stocksById.put(stock.getId(), stock));
        return stocksById;
    }

    private void updateOrderLine(AffectedTaskAdjustment affectedTask) {
        Integer currentDeliveredQuantity = affectedTask.orderLine().getDeliveredQuantity();
        if (currentDeliveredQuantity == null || currentDeliveredQuantity <= 0) {
            affectedTask.orderLine().setDeliveredQuantity(affectedTask.finalAllocatedQuantity());
        }
        affectedTask.orderLine().setShortageQuantity(affectedTask.shortageQuantity());
        if (affectedTask.shortageQuantity() > 0) {
            affectedTask.orderLine().setStatus(affectedTask.lineStatus());
        }
    }

    private void applyReallocation(
        AffectedTaskAdjustment affectedTask,
        ReallocationPlanItem reallocationPlanItem,
        Stock alternativeStock,
        List<Allocation> allocationsToSave,
        List<Stock> stocksToSave
    ) {
        List<Allocation> existingAllocations = allocationRepository.findActiveByTaskIdAndStockIdOrderByCreatedAtAscIdAsc(
            affectedTask.taskId(),
            reallocationPlanItem.stockId(),
            INACTIVE_ALLOCATION_STATUSES
        );

        Allocation allocationToUpdate = existingAllocations.isEmpty() ? null : existingAllocations.getFirst();
        int quantityDelta = reallocationPlanItem.quantity();

        alternativeStock.setReservedQuantity(
            InventoryAdjustmentSupport.nullSafeQuantity(alternativeStock.getReservedQuantity()) + quantityDelta
        );
        stocksToSave.add(alternativeStock);

        if (allocationToUpdate == null) {
            allocationsToSave.add(new Allocation(
                affectedTask.task(),
                alternativeStock,
                quantityDelta,
                resolveAllocationStatusForTask(affectedTask.task())
            ));
            return;
        }

        allocationToUpdate.setQuantity(InventoryAdjustmentSupport.nullSafeQuantity(allocationToUpdate.getQuantity()) + quantityDelta);
        allocationToUpdate.setStatus(resolveAllocationStatusForTask(affectedTask.task()));
        allocationsToSave.add(allocationToUpdate);
        if (existingAllocations.size() > 1) {
            existingAllocations.stream()
                .skip(1)
                .forEach(allocation -> {
                    allocation.setStatus(Status.CANCELED);
                    allocation.setQuantity(0);
                    allocationsToSave.add(allocation);
                });
        }
    }

    private void saveImpactedOrders(List<Long> impactedOrderIds) {
        List<Order> ordersToSave = impactedOrderIds.stream()
            .distinct()
            .map(orderId -> orderRepository.findById(orderId)
                .orElseThrow(() -> new InvalidRequestException("Order not found: " + orderId)))
            .peek(order -> order.setStatus(determineOrderStatus(order)))
            .toList();
        orderRepository.saveAll(ordersToSave);
    }

    private void logLineImpact(AffectedTaskAdjustment affectedTask) {
        if (affectedTask.shortageQuantity() > 0 && affectedTask.lineStatus() == Status.PARTIALLY_COMPLETED) {
            log.warn("Partial shortage detected: orderId={}, orderLineId={}, deliveredQuantity={}, shortageQuantity={}",
                affectedTask.orderId(),
                affectedTask.orderLineId(),
                affectedTask.finalAllocatedQuantity(),
                affectedTask.shortageQuantity());
        }
        if (affectedTask.lineStatus() == Status.CANCELED) {
            log.warn("Order line canceled because no quantity can be fulfilled: orderId={}, orderLineId={}",
                affectedTask.orderId(),
                affectedTask.orderLineId());
        }
    }

    private Status resolveAllocationStatusForTask(Task task) {
        return switch (task.getStatus()) {
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case ASSIGNED, REQUIRES_REVALIDATION -> Status.ASSIGNED;
            case COMPLETED -> Status.COMPLETED;
            case CANCELED -> Status.CANCELED;
            default -> Status.CREATED;
        };
    }

    private OrderStatus determineOrderStatus(Order order) {
        List<OrderLine> orderLines = orderLineRepository.findAllByOrderId(order.getId());
        if (orderLines.isEmpty()) {
            return order.getStatus();
        }

        boolean allCanceled = orderLines.stream().allMatch(line -> line.getStatus() == Status.CANCELED);
        if (allCanceled) {
            return OrderStatus.CANCELED;
        }

        boolean hasPartialCompletion = orderLines.stream().anyMatch(line ->
            line.getStatus() == Status.CANCELED
                || line.getStatus() == Status.SHORTAGE
                || line.getStatus() == Status.PARTIALLY_COMPLETED
                || line.getStatus() == Status.IN_PROGRESS
                || line.getStatus() == Status.ASSIGNED
                || line.getTask().map(Task::getStatus).orElse(TaskStatus.CREATED) == TaskStatus.IN_PROGRESS
                || line.getDeliveredQuantity() != null && line.getRequestedQuantity() != null
                    && line.getDeliveredQuantity() < line.getRequestedQuantity()
        );
        if (hasPartialCompletion) {
            return OrderStatus.PARTIALLY_COMPLETED;
        }

        return order.getStatus() == OrderStatus.IN_PROGRESS || order.getStatus() == OrderStatus.ASSIGNED
            ? order.getStatus()
            : OrderStatus.COMPLETED;
    }
}
