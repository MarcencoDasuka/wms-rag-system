package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationCompletionResponse;
import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.*;
import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.OperatorSummaryMapper;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TransportUnitRepository;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.PickingFlowService;
import com.isd.wms.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PickingOperatorStrategy implements OperatorExecutionStrategy {

    private final AllocationRepository allocationRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderRepository orderRepository;
    private final TransportUnitRepository tuRepository;
    private final StockRepository stockRepository;
    private final InventoryService inventoryService;
    private final WorkflowService workflowService;
    private final ShortageResolver shortageResolver;
    private final PickingFlowService pickingFlowService;
    private final OperatorSummaryMapper summaryMapper;

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.PICKING_ORDER;
    }

    @Override
    public AllocationCompletionResponse complete(Allocation allocation, int pickedQuantity, User operator) {
        boolean partialPick = pickedQuantity < allocation.getQuantity();
        int shortageQuantity = partialPick ? Math.max(0, allocation.getQuantity() - pickedQuantity) : 0;

        OrderLine orderLine = orderLineRepository.findByTaskId(allocation.getTask().getId())
            .orElseThrow(() -> new InvalidRequestException("Order line not found"));

        int currentDelivered = Optional.ofNullable(orderLine.getDeliveredQuantity()).orElse(0);
        int prevShortage = Optional.ofNullable(orderLine.getShortageQuantity()).orElse(0);

        orderLine.setDeliveredQuantity(currentDelivered + pickedQuantity);

        if (pickedQuantity > 0) {
            inventoryService.recordPickingHistory(allocation.getStock(), pickedQuantity, operator,
                shortageQuantity > 0 ? InventoryAdjustmentReason.PICKING_SHORTAGE : null,
                shortageQuantity > 0 ? "Picking shortage" : null);
        }

        if (partialPick) {
            inventoryService.recordShortageAdjustment(allocation.getStock(), shortageQuantity, operator,
                InventoryOperationType.PICKING_SHORTAGE, "Picking shortage");
        }

        List<Allocation> shortageAllocations = shortageQuantity > 0
            ? shortageResolver.resolveShortage(allocation, shortageQuantity, "Picking") : List.of();

        int unresolvedShortage = Math.max(0, shortageQuantity - shortageAllocations.stream().mapToInt(Allocation::getQuantity).sum());

        if (pickedQuantity == 0 && shortageAllocations.isEmpty()) {
            orderLine.setStatus(Status.CANCELED);
            unresolvedShortage = orderLine.getRequestedQuantity() - currentDelivered;
        }

        AllocationCompletionResult result = workflowService.executeAllocationCompletion(allocation);

        if (allocation.getStock().getQuantity() == 0 && allocation.getStock().getReservedQuantity() == 0) {
            allocation.getStock().setAvailable(false);
            stockRepository.save(allocation.getStock());
        }

        autoAdvanceFlow(allocation, orderLine.getOrder());

        orderLine.setShortageQuantity(prevShortage + unresolvedShortage);
        orderLineRepository.save(orderLine);

        Order order = orderLine.getOrder();
        handleOrderCompletion(order);

        List<OrderLine> orderLines = orderLineRepository.findAllByOrderId(order.getId());
        List<Allocation> orderedAllocations = pickingFlowService.orderAllocationsBySourceLocation(allocationRepository.findAllByOrder(order));
        Allocation currentAllocation = pickingFlowService.findCurrentExecutableAllocation(orderedAllocations).orElse(null);

        String message = pickedQuantity == 0 && shortageAllocations.isEmpty() ? "No stock found. Order line canceled." :
            (shortageQuantity > 0 ? (shortageAllocations.isEmpty() ? "Partially completed." : "New task created.") : "Completed.");

        return new AllocationCompletionResponse(
            result.status(), result.taskType(), result.id(), pickedQuantity, shortageQuantity,
            !shortageAllocations.isEmpty(), shortageAllocations.isEmpty() ? null : shortageAllocations.getFirst().getId(),
            orderLine.getStatus(), order.getStatus(), message,
            summaryMapper.toPickingSummary(order, orderLines, orderedAllocations, currentAllocation, tuRepository.existsByOrder(order))
        );
    }

    @Override
    public void dispatch(Allocation allocation, String tuBarcode) {
        orderLineRepository.findByTaskId(allocation.getTask().getId())
            .map(OrderLine::getOrder)
            .ifPresent(this::releaseTuForOrder);
    }

    private void handleOrderCompletion(Order order) {
        List<Allocation> allOrderAllocations = allocationRepository.findAllByOrder(order);
        boolean processingFinished = allOrderAllocations.stream().allMatch(a ->
            a.getStatus() == Status.COMPLETED || a.getStatus() == Status.PARTIALLY_COMPLETED || a.getStatus() == Status.CANCELED);

        if (processingFinished) {
            boolean allCanceled = orderLineRepository.findAllByOrderId(order.getId()).stream().allMatch(l -> l.getStatus() == Status.CANCELED);
            if (allCanceled) {
                releaseTuForOrder(order);
                order.setStatus(OrderStatus.CANCELED);
            } else {
                order.setStatus(OrderStatus.PARTIALLY_COMPLETED);
            }
            orderRepository.save(order);
        }
    }

    private void autoAdvanceFlow(Allocation completedAllocation, Order order) {
        List<Allocation> orderAllocations = allocationRepository.findAllByOrder(order);
        pickingFlowService.findNextExecutableAllocationAfter(orderAllocations, completedAllocation).ifPresent(next -> {
            if (next.getStatus() == Status.CREATED || next.getStatus() == Status.ASSIGNED) next.setStatus(Status.IN_PROGRESS);
            if (next.getStock().getLocation().getId().equals(completedAllocation.getStock().getLocation().getId())) next.setSourceLocationScanned(true);
            allocationRepository.save(next);
        });
    }

    private void releaseTuForOrder(Order order) {
        tuRepository.findByOrder(order).ifPresent(tu -> {
            tu.setOrder(null);
            tu.setReplenishment(null);
            tuRepository.save(tu);
        });
    }
}
