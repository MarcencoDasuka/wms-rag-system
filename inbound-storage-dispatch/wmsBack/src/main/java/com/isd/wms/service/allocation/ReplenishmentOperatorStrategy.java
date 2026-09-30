package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationCompletionResponse;
import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.*;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.OperatorSummaryMapper;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TransportUnitRepository;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.PickingFlowService;
import com.isd.wms.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishmentOperatorStrategy implements OperatorExecutionStrategy {

    private final AllocationRepository allocationRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private final TransportUnitRepository tuRepository;
    private final StockRepository stockRepository;
    private final InventoryService inventoryService;
    private final WorkflowService workflowService;
    private final ShortageResolver shortageResolver;
    private final PickingFlowService pickingFlowService;
    private final OperatorSummaryMapper summaryMapper;

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.REPLENISHMENT;
    }

    @Override
    public AllocationCompletionResponse complete(Allocation allocation, int pickedQuantity, User operator) {
        boolean partialPick = pickedQuantity < allocation.getQuantity();
        int shortageQuantity = partialPick ? Math.max(0, allocation.getQuantity() - pickedQuantity) : 0;

        if (shortageQuantity > 0) {
            inventoryService.recordShortageAdjustment(allocation.getStock(), shortageQuantity, operator,
                InventoryOperationType.REPLENISHMENT_SHORTAGE, "Replenishment shortage");
        }

        List<Allocation> shortageAllocations = shortageQuantity > 0
            ? shortageResolver.resolveShortage(allocation, shortageQuantity, "Replenishment") : List.of();

        AllocationCompletionResult result = workflowService.executeAllocationCompletion(allocation);
        autoAdvanceFlow(allocation);

        List<Allocation> allTaskAllocations = allocationRepository.findAllByTaskId(allocation.getTask().getId());
        boolean allTaskAllocationsCanceled = allTaskAllocations.stream().allMatch(a -> a.getStatus() == Status.CANCELED);

        if (pickedQuantity == 0 && shortageAllocations.isEmpty() && allTaskAllocationsCanceled) {
            log.info("All allocations for replenishment task {} are canceled. Releasing TU.", allocation.getTask().getId());
            releaseTu(allocation);
        }

        Allocation currentAllocation = findCurrentAllocation(allocation.getTask().getId());
        Replenishment replenishment = replenishmentRepository.findByTaskId(allocation.getTask().getId()).orElseThrow();
        boolean isTuScanned = tuRepository.existsByReplenishment(replenishment);

        String message = shortageQuantity > 0
            ? (shortageAllocations.isEmpty() ? "No alternative stock found. Partially completed." : "Alternative stock found. New task created.")
            : "Allocation completed successfully.";

        return new AllocationCompletionResponse(
            result.status(), result.taskType(), result.id(), pickedQuantity, shortageQuantity,
            !shortageAllocations.isEmpty(), shortageAllocations.isEmpty() ? null : shortageAllocations.getFirst().getId(),
            null, null, message,
            summaryMapper.toReplenishmentSummary(allocation.getTask(), replenishment, allTaskAllocations, currentAllocation, isTuScanned)
        );
    }

    @Override
    public void dispatch(Allocation allocation, String tuBarcode) {
        Task task = allocation.getTask();
        Replenishment replenishment = replenishmentRepository.findByTaskId(task.getId())
            .orElseThrow(() -> new InvalidRequestException("Replenishment not found"));
        Location destinationLocation = replenishment.getDestinationLocation();

        List<Allocation> taskAllocations = allocationRepository.findAllByTaskId(task.getId());
        for (Allocation alloc : taskAllocations) {
            if (alloc.getStatus() == Status.COMPLETED || alloc.getStatus() == Status.PARTIALLY_COMPLETED) {
                int quantityToMove = alloc.getPickedQuantity().orElse(alloc.getQuantity());
                if (quantityToMove > 0) {
                    Product product = alloc.getStock().getProduct().orElseThrow();
                    stockRepository.findByLocationId(destinationLocation.getId()).ifPresentOrElse(existingStock -> {
                        Product existingProduct = existingStock.getProduct().orElse(null);
                        if (existingProduct != null && !existingProduct.getId().equals(product.getId())) {
                            if (existingStock.getQuantity() == 0 && existingStock.getReservedQuantity() == 0) {
                                existingStock.setProduct(product);
                                existingStock.setQuantity(existingStock.getQuantity() + quantityToMove);
                                existingStock.updateDate(alloc.getStock().getManufactureDate(), alloc.getStock().getExpirationDate());
                                existingStock.setAvailable(true);
                            } else {
                                throw new IllegalStateException("Location is already occupied by a different product!");
                            }
                        } else {
                            if (existingProduct == null) existingStock.setProduct(product);
                            existingStock.addQuantity(quantityToMove);
                            existingStock.updateDate(alloc.getStock().getManufactureDate(), alloc.getStock().getExpirationDate());
                            existingStock.setAvailable(true);
                        }
                        stockRepository.save(existingStock);
                    }, () -> {
                        Stock newStock = new Stock(product, destinationLocation, quantityToMove, alloc.getStock().getManufactureDate(), alloc.getStock().getExpirationDate());
                        newStock.setAvailable(true);
                        stockRepository.save(newStock);
                    });
                }
            }
        }
        releaseTu(allocation);
    }

    private void autoAdvanceFlow(Allocation completedAllocation) {
        List<Allocation> replAllocations = allocationRepository.findAllByTaskId(completedAllocation.getTask().getId());
        pickingFlowService.findNextExecutableAllocationAfter(replAllocations, completedAllocation).ifPresent(next -> {
            if (next.getStatus() == Status.CREATED || next.getStatus() == Status.ASSIGNED) next.setStatus(Status.IN_PROGRESS);
            if (next.getStock().getLocation().getId().equals(completedAllocation.getStock().getLocation().getId())) next.setSourceLocationScanned(true);
            allocationRepository.save(next);
        });
    }

    private void releaseTu(Allocation allocation) {
        replenishmentRepository.findByTaskId(allocation.getTask().getId()).ifPresent(replenishment ->
            tuRepository.findAllByReplenishment(replenishment).forEach(tu -> {
                tu.setOrder(null);
                tu.setReplenishment(null);
                tuRepository.save(tu);
            }));
    }

    private Allocation findCurrentAllocation(Long taskId) {
        return allocationRepository.findAllByTaskId(taskId).stream()
            .sorted(Comparator.comparing(Allocation::getCreatedAt).thenComparing(Allocation::getId))
            .filter(a -> a.getStatus() == Status.CREATED || a.getStatus() == Status.ASSIGNED || a.getStatus() == Status.IN_PROGRESS)
            .findFirst().orElse(null);
    }
}
