package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.*;
import com.isd.wms.enums.AllocationCompletionStatus;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplenishmentAllocationCompletionStrategyTest {

    @Mock private ReplenishmentRepository replenishmentRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private ReplenishmentAllocationCompletionStrategy strategy;

    private Task task;
    private Replenishment replenishment;
    private Allocation allocation;
    private Stock sourceStock;
    private User operator;

    @BeforeEach
    void setUp() {
        operator = new User();
        ReflectionTestUtils.setField(operator, "id", 1L);

        task = new Task();
        task.setOperator(operator);
        ReflectionTestUtils.setField(task, "id", 1L);

        Product product = new Product("Test Product", "BARCODE", null, null);
        ReflectionTestUtils.setField(product, "id", 10L);

        Location destinationLocation = new Location();
        ReflectionTestUtils.setField(destinationLocation, "id", 2L);

        replenishment = new Replenishment(product, 10, destinationLocation);
        ReflectionTestUtils.setField(replenishment, "id", 10L);
        replenishment.setTask(task);

        sourceStock = new Stock();
        sourceStock.setProduct(product);
        sourceStock.setQuantity(20);
        sourceStock.setReservedQuantity(10);

        allocation = new Allocation();
        allocation.setQuantity(10);
        allocation.setStock(sourceStock);
        allocation.setTask(task);
        ReflectionTestUtils.setField(allocation, "id", 100L);
    }

    @Test
    void handle_withShortage_callsInventoryService() {
        allocation.setPickedQuantity(7);
        when(replenishmentRepository.findByTaskId(1L)).thenReturn(Optional.of(replenishment));

        strategy.handle(allocation);

        verify(inventoryService).recordShortageAdjustment(
            eq(sourceStock),
            eq(3),
            eq(operator),
            eq(InventoryOperationType.REPLENISHMENT_SHORTAGE),
            eq("Replenishment shortage")
        );
    }

    @Test
    void updateStatus_withPendingAllocations_setsInProgress() {
        Allocation pendingAllocation = new Allocation();
        pendingAllocation.setStatus(Status.IN_PROGRESS);

        when(replenishmentRepository.findByTaskId(1L)).thenReturn(Optional.of(replenishment));
        when(allocationRepository.findAllByTaskId(1L)).thenReturn(List.of(allocation, pendingAllocation));

        boolean result = strategy.updateStatus(task);

        assertThat(result).isTrue();
        assertThat(replenishment.getStatus()).isEqualTo(Status.IN_PROGRESS);
        verify(replenishmentRepository).save(replenishment);
    }

    @Test
    void updateStatus_withPartialHistoryAndNoPending_setsPartiallyCompleted() {
        allocation.setStatus(Status.SHORTAGE);

        when(replenishmentRepository.findByTaskId(1L)).thenReturn(Optional.of(replenishment));
        when(allocationRepository.findAllByTaskId(1L)).thenReturn(List.of(allocation));

        boolean result = strategy.updateStatus(task);

        assertThat(result).isTrue();
        assertThat(replenishment.getStatus()).isEqualTo(Status.PARTIALLY_COMPLETED);
        verify(replenishmentRepository).save(replenishment);
    }

    @Test
    void result_returnsCorrectStatusBasedOnReplenishment() {
        replenishment.setStatus(Status.COMPLETED);
        when(replenishmentRepository.findByTaskId(1L)).thenReturn(Optional.of(replenishment));

        AllocationCompletionResult result = strategy.result(task);

        assertThat(result.status()).isEqualTo(AllocationCompletionStatus.COMPLETED);
        assertThat(result.taskType()).isEqualTo(TaskType.REPLENISHMENT);
    }
}
