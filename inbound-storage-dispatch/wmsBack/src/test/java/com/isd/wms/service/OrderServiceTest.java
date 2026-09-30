package com.isd.wms.service;

import com.isd.wms.dto.order.*;
import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.TransportUnit;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.OrderNotFoundException;
import com.isd.wms.mapper.ExtendedOrderMapper;
import com.isd.wms.mapper.OrderMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.validation.SecurityFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private ExtendedOrderMapper extendedOrderMapper;
    @Mock private OrderLineService orderLineService;
    @Mock private AllocationRepository allocationRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private TransportUnitRepository transportUnitRepository;
    @Mock private TaskService taskService;
    @Mock private ImportService importService;
    @Mock private SecurityFacade securityFacade;

    @InjectMocks
    private OrderService orderService;

    private Order orderWithId(Long id, String logicId) {
        Order order = new Order(logicId);
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private OrderResponse sampleOrderResponse() {
        return new OrderResponse(1L, "LOGIC-001", 1L, OrderStatus.CREATED, null, null);
    }

    @Test
    void addExtendedOrder_withLines_savesOrderAndDelegatesLines() {
        OrderLineCreateRequest lineRequest = new OrderLineCreateRequest(null, 1L, 5);
        ExtendedOrderCreateRequest request = new ExtendedOrderCreateRequest(
            new OrderCreateRequest("LOGIC-001", 1L),
            List.of(lineRequest)
        );
        Order saved = orderWithId(1L, "LOGIC-001");

        when(locationRepository.findById(1L)).thenReturn(Optional.of(new com.isd.wms.entity.Location()));
        when(orderRepository.save(any(Order.class))).thenReturn(saved);
        when(orderMapper.toResponse(eq(saved), any())).thenReturn(sampleOrderResponse());
        doNothing().when(orderLineService).addOrderLine(eq(saved), any(OrderLineCreateRequest.class));

        OrderResponse result = orderService.addExtendedOrder(request);

        assertThat(result.id()).isEqualTo(1L);
        verify(orderLineService, times(1)).addOrderLine(eq(saved), any(OrderLineCreateRequest.class));
    }

    @Test
    void deleteOrderById_releasesTransportUnit() {
        Order order = orderWithId(1L, "LOGIC-001");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderLineRepository.findAllByOrderId(1L)).thenReturn(List.of());

        TransportUnit tu = new TransportUnit("TU111111");
        tu.setOrder(order);
        when(transportUnitRepository.findByOrder(order)).thenReturn(Optional.of(tu));

        orderService.deleteOrderById(1L);

        assertThat(tu.getOrder()).isNull();
        verify(transportUnitRepository).save(tu);
        verify(orderRepository).delete(order);
    }

    @Test
    void assignOrder_completedOrder_throwsInvalidRequestException() {
        Order order = orderWithId(1L, "LOGIC-001");
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.assignOrder(1L, 10L))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("not allowed");

        verify(orderRepository, never()).updateStatus(any(), any());
    }
}
