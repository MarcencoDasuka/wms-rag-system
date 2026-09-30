package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskStatus;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.StockRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryAdjustmentPlanner {

    private static final List<Status> INACTIVE_ALLOCATION_STATUSES = List.of(Status.COMPLETED, Status.CANCELED);

    private final AllocationRepository allocationRepository;
    private final OrderLineRepository orderLineRepository;
    private final StockRepository stockRepository;

    public InventoryAdjustmentPlan buildPlan(InventoryAdjustmentContext context) {
        List<Allocation> adjustedStockAllocations = allocationRepository.findActiveByStockId(
                context.stockId(),
                INACTIVE_ALLOCATION_STATUSES
            ).stream()
            .sorted(Comparator.comparing(Allocation::getCreatedAt).thenComparing(Allocation::getId))
            .toList();

        Map<Long, Integer> preservedOnAdjustedStockByTask = new LinkedHashMap<>();
        Map<Long, Integer> reducedOnAdjustedStockByTask = new LinkedHashMap<>();
        int remainingAdjustedStockCapacity = context.newQuantity();
        int preservedQuantityOnAdjustedStock = 0;

        for (Allocation allocation : adjustedStockAllocations) {
            int originalQuantity = InventoryAdjustmentSupport.nullSafeQuantity(allocation.getQuantity());
            int preservedQuantity = Math.min(originalQuantity, remainingAdjustedStockCapacity);
            int reducedQuantity = originalQuantity - preservedQuantity;
            remainingAdjustedStockCapacity -= preservedQuantity;
            preservedQuantityOnAdjustedStock += preservedQuantity;

            Long taskId = allocation.getTask().getId();
            preservedOnAdjustedStockByTask.merge(taskId, preservedQuantity, Integer::sum);
            reducedOnAdjustedStockByTask.merge(taskId, reducedQuantity, Integer::sum);
        }

        Map<Long, Integer> availableAlternativeQuantityByStockId = loadAlternativeAvailability(context);
        List<AffectedTaskAdjustment> affectedTasks = adjustedStockAllocations.stream()
            .filter(allocation -> allocation.getTask().getTaskType() == TaskType.PICKING_ORDER)
            .map(allocation -> allocation.getTask().getId())
            .distinct()
            .map(taskId -> calculateAffectedTaskAdjustment(
                taskId,
                context,
                preservedOnAdjustedStockByTask,
                reducedOnAdjustedStockByTask,
                availableAlternativeQuantityByStockId
            ))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(AffectedTaskAdjustment::orderCreatedAt).thenComparing(AffectedTaskAdjustment::orderId))
            .toList();

        return new InventoryAdjustmentPlan(context, affectedTasks, preservedQuantityOnAdjustedStock);
    }

    private Map<Long, Integer> loadAlternativeAvailability(InventoryAdjustmentContext context) {
        Map<Long, Integer> availabilityByStockId = new LinkedHashMap<>();
        stockRepository.findAvailableStocksByProductIdAndZone(context.product().getId(), Zone.PICKING).stream()
            .filter(candidate -> !Objects.equals(candidate.getId(), context.stockId()))
            .sorted(Comparator.comparing(this::availableQuantity).reversed().thenComparing(Stock::getId))
            .forEach(stock -> availabilityByStockId.put(stock.getId(), availableQuantity(stock)));
        return availabilityByStockId;
    }

    private AffectedTaskAdjustment calculateAffectedTaskAdjustment(
        Long taskId,
        InventoryAdjustmentContext context,
        Map<Long, Integer> preservedOnAdjustedStockByTask,
        Map<Long, Integer> reducedOnAdjustedStockByTask,
        Map<Long, Integer> availableAlternativeQuantityByStockId
    ) {
        OrderLine orderLine = orderLineRepository.findByTaskId(taskId)
            .orElseThrow(() -> new InvalidRequestException("Order line not found for task " + taskId));
        if (InventoryAdjustmentSupport.nullSafeQuantity(orderLine.getDeliveredQuantity()) > 0) {
            return null;
        }
        Order order = orderLine.getOrder();
        Task task = orderLine.getTask()
            .orElseThrow(() -> new InvalidRequestException("Task not found for order line " + orderLine.getId()));

        List<Allocation> activeTaskAllocations = allocationRepository.findActiveByTaskIdOrderByCreatedAtAscIdAsc(
            taskId,
            INACTIVE_ALLOCATION_STATUSES
        );

        int allocatedFromOtherStocks = activeTaskAllocations.stream()
            .filter(allocation -> !Objects.equals(allocation.getStock().getId(), context.stockId()))
            .mapToInt(allocation -> InventoryAdjustmentSupport.nullSafeQuantity(allocation.getQuantity()))
            .sum();

        int preservedOnAdjustedStock = preservedOnAdjustedStockByTask.getOrDefault(taskId, 0);
        int reducedFromAdjustedStock = reducedOnAdjustedStockByTask.getOrDefault(taskId, 0);
        int allocatedAfterReduction = allocatedFromOtherStocks + preservedOnAdjustedStock;
        int requestedQuantity = InventoryAdjustmentSupport.nullSafeQuantity(orderLine.getRequestedQuantity());

        List<ReallocationPlanItem> reallocationPlan = new ArrayList<>();
        int remainingShortage = Math.max(0, requestedQuantity - allocatedAfterReduction);
        int allocatedFromAlternatives = 0;

        for (Map.Entry<Long, Integer> entry : availableAlternativeQuantityByStockId.entrySet()) {
            if (remainingShortage <= 0) {
                break;
            }

            int availableQuantity = entry.getValue();
            if (availableQuantity <= 0) {
                continue;
            }

            int quantityToReallocate = Math.min(remainingShortage, availableQuantity);
            reallocationPlan.add(new ReallocationPlanItem(entry.getKey(), quantityToReallocate));
            availableAlternativeQuantityByStockId.put(entry.getKey(), availableQuantity - quantityToReallocate);
            remainingShortage -= quantityToReallocate;
            allocatedFromAlternatives += quantityToReallocate;
        }

        int finalAllocatedQuantity = allocatedAfterReduction + allocatedFromAlternatives;
        int shortageQuantity = Math.max(0, requestedQuantity - finalAllocatedQuantity);
        boolean revalidationRequired = task.getStatus() == TaskStatus.IN_PROGRESS
            && (reducedFromAdjustedStock > 0 || allocatedFromAlternatives > 0 || shortageQuantity > 0);

        Status lineStatus = resolveLineStatus(task, orderLine.getStatus(), finalAllocatedQuantity, shortageQuantity);

        Long reallocatedLocationId = reallocationPlan.isEmpty() ? null : reallocationPlan.getFirst().stockId();
        String reallocatedLocationBarcode = reallocationPlan.isEmpty()
            ? null
            : stockRepository.findById(reallocationPlan.getFirst().stockId())
                .map(stock -> stock.getLocation() == null ? null : stock.getLocation().getBarcode())
                .orElse(null);

        return new AffectedTaskAdjustment(
            taskId,
            order.getId(),
            order.getLogicId(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            orderLine.getId(),
            task,
            orderLine,
            requestedQuantity,
            allocatedFromOtherStocks,
            preservedOnAdjustedStock,
            reducedFromAdjustedStock,
            allocatedFromAlternatives,
            shortageQuantity,
            lineStatus,
            revalidationRequired,
            context.stock().getLocation().getId(),
            context.stock().getLocation().getBarcode(),
            reallocatedLocationId,
            reallocatedLocationBarcode,
            reallocationPlan
        );
    }

    private int availableQuantity(Stock stock) {
        return InventoryAdjustmentSupport.nullSafeQuantity(stock.getQuantity())
            - InventoryAdjustmentSupport.nullSafeQuantity(stock.getReservedQuantity());
    }

    private Status resolveLineStatus(Task task, Status currentStatus, int finalAllocatedQuantity, int shortageQuantity) {
        if (shortageQuantity > 0) {
            return finalAllocatedQuantity > 0 ? Status.SHORTAGE : Status.CANCELED;
        }

        return switch (task.getStatus()) {
            case CREATED -> Status.CREATED;
            case ASSIGNED -> Status.ASSIGNED;
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case COMPLETED -> Status.COMPLETED;
            case CANCELED -> Status.CANCELED;
            case REQUIRES_REVALIDATION -> currentStatus == Status.SHORTAGE ? Status.ASSIGNED : currentStatus;
        };
    }
}
