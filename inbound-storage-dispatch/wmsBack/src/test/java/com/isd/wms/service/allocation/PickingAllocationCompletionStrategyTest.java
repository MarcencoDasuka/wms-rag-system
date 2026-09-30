package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.AllocationCompletionStatus;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.OrderRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickingAllocationCompletionStrategyTest {

    @Mock private OrderLineRepository orderLineRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private PickingAllocationCompletionStrategy strategy;

    private Task task;
    private Order order;
    private OrderLine orderLine;

    @BeforeEach
    void setUp() {
        task = new Task();
        ReflectionTestUtils.setField(task, "id", 1L);

        order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);

        orderLine = new OrderLine(order, task, null, 10);
        ReflectionTestUtils.setField(orderLine, "id", 100L);
    }

    @Test
    void result_returnsCompletedStatus() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.getOrderByTask(task)).thenReturn(Optional.of(order));

        AllocationCompletionResult result = strategy.result(task);

        assertThat(result.status()).isEqualTo(AllocationCompletionStatus.COMPLETED);
        assertThat(result.taskType()).isEqualTo(TaskType.PICKING_ORDER);
    }

    @Test
    void updateStatus_computesPartialCompletionCorrectly() {
        orderLine.setDeliveredQuantity(5);
        when(orderLineRepository.findByTaskId(1L)).thenReturn(Optional.of(orderLine));
        when(orderLineRepository.findAllByOrderId(10L)).thenReturn(List.of(orderLine));

        boolean res = strategy.updateStatus(task);

        assertThat(res).isTrue();
        assertThat(orderLine.getStatus()).isEqualTo(Status.PARTIALLY_COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_COMPLETED);
        verify(orderRepository).save(order);
    }
}
