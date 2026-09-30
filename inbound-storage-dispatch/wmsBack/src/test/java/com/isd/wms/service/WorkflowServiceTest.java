package com.isd.wms.service;

import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.AllocationCompletionStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TaskRepository;
import com.isd.wms.service.allocation.AllocationCompletionStrategy;
import com.isd.wms.service.allocation.StockAllocationStrategy;
import com.isd.wms.enums.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceTest {

    @Mock private AllocationRepository allocationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private TaskRepository taskRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private Task task;
    private StockAllocationStrategy mockStockStrategy;
    private AllocationCompletionStrategy mockCompletionStrategy;

    @BeforeEach
    void setUp() {
        task = new Task();
        task.setId(1L);
        task.setTaskType(TaskType.PICKING_ORDER);

        mockStockStrategy = mock(StockAllocationStrategy.class);
        when(mockStockStrategy.support(TaskType.PICKING_ORDER)).thenReturn(true);
        when(mockStockStrategy.getSourceZone()).thenReturn(Zone.PICKING);

        mockCompletionStrategy = mock(AllocationCompletionStrategy.class);
        when(mockCompletionStrategy.support(TaskType.PICKING_ORDER)).thenReturn(true);
        when(mockCompletionStrategy.result(any())).thenReturn(new AllocationCompletionResult(AllocationCompletionStatus.COMPLETED, TaskType.PICKING_ORDER, 1L));

        List<StockAllocationStrategy> allocStrategies = new ArrayList<>();
        allocStrategies.add(mockStockStrategy);
        List<AllocationCompletionStrategy> compStrategies = new ArrayList<>();
        compStrategies.add(mockCompletionStrategy);

        ReflectionTestUtils.setField(workflowService, "allocationStrategies", allocStrategies);
        ReflectionTestUtils.setField(workflowService, "allocationCompletionStrategies", compStrategies);
    }

    @Test
    void generateAllocationsForTask_success_createsOneAllocation() {
        Stock stock = new Stock();
        stock.setId(10L);
        stock.setQuantity(100);
        stock.setReservedQuantity(0);

        when(stockRepository.findAvailableStocksByProductIdAndZone(1L, Zone.PICKING))
            .thenReturn(new ArrayList<>(List.of(stock)));

        workflowService.generateAllocationsForTask(task, 1L, 50);

        verify(allocationRepository, times(1)).saveAll(anyList());
        assertThat(stock.getReservedQuantity()).isEqualTo(50);
    }

    @Test
    void executeAllocationCompletion_partialPick_skipsQuantityRemoval() {
        Stock stock = new Stock();
        stock.setQuantity(100);
        stock.setReservedQuantity(10);
        Allocation allocation = new Allocation(task, stock, 10, Status.IN_PROGRESS);
        allocation.setPickedQuantity(5);

        workflowService.executeAllocationCompletion(allocation);

        assertThat(stock.getQuantity()).isEqualTo(100);
        verify(stockRepository, never()).save(stock);
        verify(mockCompletionStrategy).handle(allocation);
    }
}
