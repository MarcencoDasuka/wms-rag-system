package com.isd.wms.service;

import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TaskRepository;
import com.isd.wms.service.allocation.AllocationCompletionStrategy;
import com.isd.wms.service.allocation.StockAllocationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final AllocationRepository allocationRepository;
    private final StockRepository stockRepository;
    private final TaskRepository taskRepository;

    private final List<AllocationCompletionStrategy> allocationCompletionStrategies;
    private final List<StockAllocationStrategy> allocationStrategies;

    @Transactional
    public void generateAllocationsForTask(Task task, Long productId, int remainingQuantity) {
        StockAllocationStrategy strategy = allocationStrategies.stream()
            .filter(s -> s.support(task.getTaskType()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No allocation strategy found for task type: "
                + task.getTaskType()));

        List<Stock> availableStocks = new ArrayList<>(
            stockRepository.findAvailableStocksByProductIdAndZone(productId, strategy.getSourceZone())
        );

        if (availableStocks.isEmpty()) {
            throw new InvalidRequestException(
                String.format("Insufficient stock for Product ID: %d in %s zone.", productId,
                    strategy.getSourceZone().name())
            );
        }

        strategy.sortStocks(availableStocks);

        List<Allocation> allocationsToSave = allocateStockToAllocations(
            task, availableStocks, remainingQuantity, productId, strategy.getSourceZone().name());

        allocationRepository.saveAll(allocationsToSave);
        stockRepository.saveAll(availableStocks);
    }

    private List<Allocation> allocateStockToAllocations(
        Task task,
        List<Stock> availableStocks,
        int quantityNeeded,
        Long productId,
        String zoneName
    ) {
        List<Allocation> allocations = new ArrayList<>();
        int qtyNeeded = quantityNeeded;

        for (Stock stock : availableStocks) {
            if (qtyNeeded <= 0) break;
            if (stock.getAvailableQuantity() <= 0) continue;

            int quantityToTake = Math.min(stock.getAvailableQuantity(), qtyNeeded);

            Allocation allocation = new Allocation(task, stock, quantityToTake, Status.CREATED);
            allocations.add(allocation);

            stock.setReservedQuantity(stock.getReservedQuantity() + quantityToTake);
            qtyNeeded -= quantityToTake;
        }

        if (qtyNeeded > 0) {
            throw new InvalidRequestException(
                String.format("Insufficient stock for Product ID: %d in %s zone. Missing %d pcs.",
                    productId, zoneName, qtyNeeded)
            );
        }
        return allocations;
    }

    @Transactional
    public void updateTask(Task task, Long productId, Integer requestedQuantity) {
        List<Allocation> oldAllocations = allocationRepository.findAllByTaskId(task.getId());
        for (Allocation allocation : oldAllocations) {
            Stock stock = allocation.getStock();
            int newReserved = Math.max(0, stock.getReservedQuantity() - allocation.getQuantity());
            stock.setReservedQuantity(newReserved);
            stockRepository.save(stock);
        }

        allocationRepository.deleteAllInBatch(oldAllocations);
        allocationRepository.flush();

        generateAllocationsForTask(task, productId, requestedQuantity);
    }

    @Transactional
    public AllocationCompletionResult executeAllocationCompletion(Allocation allocation) {
        Stock sourceStock = allocation.getStock();
        int quantityToMove = allocation.getPickedQuantity().orElseGet(allocation::getQuantity);

        if (quantityToMove > 0) {
            int newQty = Math.max(0, sourceStock.getQuantity() - quantityToMove);
            int newRes = Math.max(0, sourceStock.getReservedQuantity() - quantityToMove);
            sourceStock.setQuantity(newQty);
            sourceStock.setReservedQuantity(newRes);

            if (sourceStock.getQuantity() == 0 && sourceStock.getReservedQuantity() == 0) {
                sourceStock.setAvailable(Boolean.FALSE);
            } else {
                stockRepository.save(sourceStock);
            }
        }

        Task task = allocation.getTask();
        AllocationCompletionStrategy strategy = allocationCompletionStrategies.stream()
            .filter(s -> s.support(task.getTaskType()))
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException(
                    "No completion strategy found for task type: " + task.getTaskType()
                ));

        strategy.handle(allocation);

        boolean hasActiveAllocations = allocationRepository.findAllByTaskId(task.getId()).stream()
            .anyMatch(a -> a.getStatus() == Status.CREATED || a.getStatus() == Status.ASSIGNED || a.getStatus() == Status.IN_PROGRESS);

        if (!hasActiveAllocations) {
            taskRepository.markTaskAsCompleted(task.getId());
        }

        strategy.updateStatus(task);
        return strategy.result(task);
    }
}
