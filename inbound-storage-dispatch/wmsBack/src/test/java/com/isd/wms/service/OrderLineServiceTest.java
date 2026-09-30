package com.isd.wms.service;

import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.dto.order_line.OrderLineResponse;
import com.isd.wms.dto.order_line.OrderLineUpdateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.OrderLineNotFoundException;
import com.isd.wms.mapper.OrderLineMapper;
import com.isd.wms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderLineServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private TaskService taskService;

    @Spy private OrderLineMapper orderLineMapper = new OrderLineMapper();

    @InjectMocks
    private OrderLineService orderLineService;

    private Order orderWithId(Long id) {
        Order order = new Order("LOGIC-00" + id);
        ReflectionTestUtils.setField(order, "id", id);
        order.setStatus(OrderStatus.CREATED);
        return order;
    }

    private Product productWithId(Long id) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @Test
    void addOrderLine_validRequest_savesOrderLine_withoutTask() {
        Order order = orderWithId(1L);
        Product product = productWithId(1L);
        OrderLineCreateRequest request = new OrderLineCreateRequest(1L, 1L, 10);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderLineRepository.save(any(OrderLine.class))).thenAnswer(inv -> inv.getArgument(0));

        orderLineService.addOrderLine(order, request);

        verify(orderLineRepository).save(any(OrderLine.class));
    }

    @Test
    void deleteOrderLine_withoutTask_callsRepository() {
        OrderLine orderLine = new OrderLine(orderWithId(1L), productWithId(1L), 10);
        ReflectionTestUtils.setField(orderLine, "id", 1L);

        when(orderLineRepository.findById(1L)).thenReturn(Optional.of(orderLine));

        orderLineService.deleteOrderLine(1L);

        verify(orderLineRepository, times(1)).delete(orderLine);
    }

    @Test
    void getOrderLineById_existingId_returnsResponse() {
        Order order = orderWithId(1L);
        Product product = productWithId(1L);
        OrderLine orderLine = new OrderLine(order, product, 10);
        ReflectionTestUtils.setField(orderLine, "id", 1L);

        when(orderLineRepository.findById(1L)).thenReturn(Optional.of(orderLine));

        assertThat(orderLineService.getOrderLineById(1L).orderLineId()).isEqualTo(1L);
    }
}
