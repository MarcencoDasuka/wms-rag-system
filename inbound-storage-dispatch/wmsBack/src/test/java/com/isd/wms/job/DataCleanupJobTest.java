package com.isd.wms.job;

import com.isd.wms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataCleanupJobTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private ReplenishmentRepository replenishmentRepository;

    @InjectMocks
    private DataCleanupJob dataCleanupJob;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataCleanupJob, "retentionDays", 14);
    }

    @Test
    void cleanOldData_delegatesDeletionToRepositories() {
        when(allocationRepository.deleteAllocationsOlderThan(any(LocalDateTime.class))).thenReturn(5);
        when(replenishmentRepository.deleteReplenishmentsByTaskCreatedAtOlderThan(any(LocalDateTime.class))).thenReturn(2);
        when(orderLineRepository.deleteOrderLinesByOrderCreatedAtOlderThan(any(LocalDateTime.class))).thenReturn(10);
        when(orderRepository.deleteOrdersOlderThan(any(LocalDateTime.class))).thenReturn(3);
        when(taskRepository.deleteTasksOlderThan(any(LocalDateTime.class))).thenReturn(4);

        dataCleanupJob.cleanOldData();

        verify(allocationRepository).deleteAllocationsOlderThan(any(LocalDateTime.class));
        verify(replenishmentRepository).deleteReplenishmentsByTaskCreatedAtOlderThan(any(LocalDateTime.class));
        verify(orderLineRepository).deleteOrderLinesByOrderCreatedAtOlderThan(any(LocalDateTime.class));
        verify(orderRepository).deleteOrdersOlderThan(any(LocalDateTime.class));
        verify(taskRepository).deleteTasksOlderThan(any(LocalDateTime.class));
    }
}
