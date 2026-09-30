package com.isd.wms.mapper;

import com.isd.wms.dto.operator.OperatorAllocationSummaryResponse;
import com.isd.wms.dto.operator.OperatorOrderLineSummaryResponse;
import com.isd.wms.dto.operator.OperatorTaskSummaryResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class OperatorSummaryMapper {

    public OperatorTaskSummaryResponse toPickingSummary(
        Order order,
        List<OrderLine> orderLines,
        List<Allocation> orderedAllocations,
        Allocation currentAllocation,
        boolean isTuScanned) {

        List<OperatorOrderLineSummaryResponse> lineSummaries = orderLines.stream()
            .sorted(Comparator.comparing(OrderLine::getCreatedAt).thenComparing(OrderLine::getId))
            .map(orderLine -> toLineSummary(order, orderLine, orderedAllocations))
            .toList();

        long completedAllocationCount = orderedAllocations.stream()
            .filter(allocation -> allocation.getStatus() == Status.COMPLETED)
            .count();

        boolean readyForCompletion = (order.getStatus() == OrderStatus.PICKED
            || order.getStatus() == OrderStatus.PARTIALLY_COMPLETED)
            && orderedAllocations.stream().allMatch(allocation ->
            allocation.getStatus() == Status.COMPLETED
                || allocation.getStatus() == Status.PARTIALLY_COMPLETED
                || allocation.getStatus() == Status.CANCELED
        );

        Long taskId = currentAllocation != null ? currentAllocation.getTask().getId()
            : orderLines.stream().findFirst().map(line -> line.getTask().map(Task::getId).orElse(null)).orElse(null);

        return new OperatorTaskSummaryResponse(
            taskId,
            order.getId(),
            order.getLogicId(),
            order.getStatus(),
            TaskType.PICKING_ORDER.name(),
            order.getDestinationLocation().getBarcode(),
            orderedAllocations.size(),
            Math.toIntExact(completedAllocationCount),
            readyForCompletion,
            isTuScanned,
            currentAllocation != null ? toAllocationSummary(currentAllocation, order, isTuScanned) : null,
            lineSummaries,
            orderedAllocations.stream().map(allocation -> toAllocationSummary(allocation, order, isTuScanned)).toList()
        );
    }

    public OperatorTaskSummaryResponse toReplenishmentSummary(
        Task task,
        Replenishment replenishment,
        List<Allocation> taskAllocations,
        Allocation currentAllocation,
        boolean isTuScanned) {

        long completedAllocationCount = taskAllocations.stream()
            .filter(allocation -> allocation.getStatus() == Status.COMPLETED)
            .count();

        return new OperatorTaskSummaryResponse(
            task.getId(),
            null,
            null,
            null,
            task.getTaskType().name(),
            replenishment.getDestinationLocation().getBarcode(),
            taskAllocations.size(),
            Math.toIntExact(completedAllocationCount),
            currentAllocation == null,
            isTuScanned,
            currentAllocation != null ? toAllocationSummary(currentAllocation, replenishment.getDestinationLocation().getBarcode(), isTuScanned) : null,
            List.of(),
            taskAllocations.stream()
                .map(allocation -> toAllocationSummary(allocation, replenishment.getDestinationLocation().getBarcode(), isTuScanned))
                .toList()
        );
    }

    private OperatorOrderLineSummaryResponse toLineSummary(Order order, OrderLine orderLine, List<Allocation> orderedAllocations) {
        Long taskId = orderLine.getTask().map(Task::getId).orElse(null);

        List<Allocation> lineAllocations = orderedAllocations.stream()
            .filter(allocation -> allocation.getTask().getId().equals(taskId))
            .toList();

        int pickedQuantity = lineAllocations.stream()
            .filter(allocation -> allocation.getStatus() != Status.CANCELED)
            .mapToInt(allocation -> allocation.getPickedQuantity().orElse(0))
            .sum();

        List<String> sourceLocationBarcodes = lineAllocations.stream()
            .map(allocation -> allocation.getStock().getLocation().getBarcode())
            .distinct()
            .toList();

        return new OperatorOrderLineSummaryResponse(
            taskId,
            orderLine.getId(),
            orderLine.getProduct().getId(),
            orderLine.getProduct().getName(),
            orderLine.getProduct().getBarcode(),
            orderLine.getRequestedQuantity(),
            pickedQuantity,
            sourceLocationBarcodes,
            order.getDestinationLocation().getBarcode(),
            orderLine.getStatus()
        );
    }

    private OperatorAllocationSummaryResponse toAllocationSummary(Allocation allocation, Order order, boolean isTuScanned) {
        return toAllocationSummary(allocation, order.getDestinationLocation().getBarcode(), isTuScanned);
    }

    private OperatorAllocationSummaryResponse toAllocationSummary(Allocation allocation, String destinationLocationBarcode, boolean isTuScanned) {
        Stock stock = allocation.getStock();
        Long orderLineId = allocation.getTask().getTaskType() == TaskType.PICKING_ORDER
            ? null : null;

        return new OperatorAllocationSummaryResponse(
            allocation.getId(),
            allocation.getTask().getId(),
            orderLineId,
            stock.getProduct().map(Product::getId).orElse(null),
            stock.getProduct().map(Product::getName).orElse(null),
            stock.getProduct().map(Product::getBarcode).orElse(null),
            stock.getLocation().getBarcode(),
            destinationLocationBarcode,
            allocation.getQuantity(),
            allocation.getPickedQuantity().orElse(0),
            allocation.getStatus(),
            allocation.isSourceLocationScanned(),
            allocation.isProductScanned(),
            isTuScanned
        );
    }
}
