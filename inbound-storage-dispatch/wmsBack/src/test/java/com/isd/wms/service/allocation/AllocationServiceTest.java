package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationOperatorResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.AllocationsNotFoundException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.service.AllocationService;
import com.isd.wms.service.validation.SecurityFacade;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    @Mock private AllocationRepository allocationRepository;
    @Mock private SecurityFacade securityFacade;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private ReplenishmentRepository replenishmentRepository;

    @InjectMocks
    private AllocationService allocationService;

    private User operator;
    private Allocation allocation;
    private Task task;
    private OrderLine orderLine;
    private Order order;
    private Product product;

    @BeforeEach
    void setUp() {
        operator = new User();
        ReflectionTestUtils.setField(operator, "id", 1L);
        operator.setUsername("testOperator");

        product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setName("Test Product");
        product.setBarcode("PROD-001");

        Location location = new Location();
        location.setBarcode("ZONE-A");
        location.setName("Zone A");

        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setLocation(location);

        task = new Task();
        ReflectionTestUtils.setField(task, "id", 100L);
        task.setOperator(operator);
        task.setTaskType(TaskType.PICKING_ORDER);

        allocation = new Allocation();
        ReflectionTestUtils.setField(allocation, "id", 50L);
        allocation.setQuantity(10);
        allocation.setStatus(Status.ASSIGNED);
        allocation.setStock(stock);
        allocation.setTask(task);

        Location destLocation = new Location();
        destLocation.setBarcode("DISP-01");

        order = new Order("LOGIC-001");
        ReflectionTestUtils.setField(order, "id", 200L);
        order.setDestinationLocation(destLocation);

        orderLine = new OrderLine(order, task, product, 10);
    }

    @Test
    void getAllocationsOperator_returnsMappedResponseForPicking() {
        when(securityFacade.getCurrentUsername()).thenReturn("testOperator");

        when(allocationRepository.findFirstByTask_Operator_UsernameAndStatusInOrderByCreatedAtAscIdAsc(
            eq("testOperator"),
            anyList()))
            .thenReturn(Optional.of(allocation));

        when(allocationRepository.findAllByTaskId(100L)).thenReturn(List.of(allocation));
        when(orderLineRepository.findByTaskId(100L)).thenReturn(Optional.of(orderLine));
        when(allocationRepository.countAllocationsInOrder(200L)).thenReturn(1);
        when(allocationRepository.countCompletedAllocationsInOrder(200L)).thenReturn(0);

        AllocationOperatorResponse response = allocationService.getAllocationsOperator();

        assertThat(response).isNotNull();
        assertThat(response.allocations()).isNotNull();
        assertThat(response.allocations().productName()).isEqualTo("Test Product");
        assertThat(response.taskType()).isEqualTo("PICKING_ORDER");
        assertThat(response.orderLogicalId()).isEqualTo("LOGIC-001");
        assertThat(response.destinationLocationBarcode()).isEqualTo("DISP-01");
    }

    @Test
    void getAllocationsOperator_emptyList_throwsException() {
        when(securityFacade.getCurrentUsername()).thenReturn("testOperator");

        when(allocationRepository.findFirstByTask_Operator_UsernameAndStatusInOrderByCreatedAtAscIdAsc(
            anyString(),
            anyList()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> allocationService.getAllocationsOperator())
            .isInstanceOf(AllocationsNotFoundException.class)
            .hasMessageContaining("testOperator");

        verify(orderLineRepository, never()).findByTaskId(any());
    }

    @Test
    void getAllocationsOperator_returnsMappedResponseForReplenishment() {
        task.setTaskType(TaskType.REPLENISHMENT);

        Location destLocation = new Location();
        destLocation.setBarcode("PICK-A-01");
        Replenishment repl = new Replenishment(product, 10, destLocation);

        when(securityFacade.getCurrentUsername()).thenReturn("testOperator");
        when(allocationRepository.findFirstByTask_Operator_UsernameAndStatusInOrderByCreatedAtAscIdAsc(
            eq("testOperator"),
            anyList()))
            .thenReturn(Optional.of(allocation));

        when(allocationRepository.findAllByTaskId(100L)).thenReturn(List.of(allocation));
        when(replenishmentRepository.findByTaskId(100L)).thenReturn(Optional.of(repl));

        AllocationOperatorResponse response = allocationService.getAllocationsOperator();

        assertThat(response).isNotNull();
        assertThat(response.taskType()).isEqualTo("REPLENISHMENT");
        assertThat(response.destinationLocationBarcode()).isEqualTo("PICK-A-01");
    }
}
