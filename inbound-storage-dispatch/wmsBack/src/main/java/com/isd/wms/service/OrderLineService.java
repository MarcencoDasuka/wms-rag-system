package com.isd.wms.service;

import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.dto.order_line.OrderLineResponse;
import com.isd.wms.dto.order_line.OrderLineUpdateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.OrderLineNotFoundException;
import com.isd.wms.exception.OrderNotFoundException;
import com.isd.wms.exception.ProductNotFoundException;
import com.isd.wms.exception.TaskNotFoundException;
import com.isd.wms.mapper.OrderLineMapper;
import com.isd.wms.repository.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing order lines (line items) within orders.
 * <p>
 * Handles creation, update, deletion, and retrieval of order lines.
 * When an order line is deleted, any reserved stock associated with its task
 * is released (unless the allocations are already completed or canceled).
 * </p>
 *
 * @see OrderLine
 * @see Order
 * @see Task
 * @see Allocation
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class OrderLineService {
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineMapper orderLineMapper;
    private final ProductRepository productRepository;
    private final TaskRepository taskRepository;
    private final AllocationRepository allocationRepository;
    private final StockRepository stockRepository;

    /**
     * Adds a new order line to an order.
     *
     * @param order the parent order
     * @param request the creation request containing product and quantity
     */
    @Transactional
    public void addOrderLine(Order order, OrderLineCreateRequest request) {
        Product product = getProduct(request.productId());
        OrderLine orderLine = new OrderLine(order, product, request.requestedQuantity());
        orderLineRepository.save(orderLine);
    }

    /**
     * Updates an existing order line.
     *
     * @param id the ID of the order line to update
     * @param request the update request
     * @return the updated order line response
     */
    @Transactional
    public OrderLineResponse updateOrderLine(Long id, OrderLineUpdateRequest request) {
        OrderLine orderLine = getOrderLine(id);

        updateOrderLineFields(orderLine, request);

        return orderLineMapper.toResponse(orderLineRepository.save(orderLine));
    }

    private void updateOrderLineFields(OrderLine orderLine, OrderLineUpdateRequest request) {
        orderLine.setOrder(getOrder(request.orderId()));
        orderLine.setTask(getTask(request.taskId()));
        orderLine.setProduct(getProduct(request.productId()));
        orderLine.setRequestedQuantity(request.requestedQuantity());
        orderLine.setStatus(request.status());
    }

    /**
     * Deletes an order line and releases any reserved stock associated with it.
     *
     * @param orderLineId the ID of the order line to delete
     */
    @Transactional
    public void deleteOrderLine(Long orderLineId) {
        OrderLine orderLine = getOrderLine(orderLineId);
        releaseReservedStock(orderLine);
        orderLineRepository.delete(orderLine);
    }

    public List<OrderLineResponse> getAll() {
        return orderLineRepository.findAll().stream()
            .map(orderLineMapper::toResponse)
            .toList();
    }

    private Order getOrder(@NonNull Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public OrderLine getOrderLine(@NonNull Long orderLineId) {
        return orderLineRepository.findById(orderLineId)
            .orElseThrow(() -> new OrderLineNotFoundException(orderLineId));
    }

    public OrderLineResponse getOrderLineById(@NonNull Long orderLineId) {
        return orderLineMapper.toResponse(getOrderLine(orderLineId));
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private void releaseReservedStock(OrderLine orderLine) {
        if (orderLine.getTask().isEmpty()) {
            return;
        }

        List<Allocation> allocations = allocationRepository.findAllByTaskId(orderLine.getTask()
            .map(Task::getId).orElse(null));
        for (Allocation allocation : allocations) {
            if (allocation.getStatus() == Status.COMPLETED || allocation.getStatus() == Status.CANCELED) {
                continue;
            }
            Stock stock = allocation.getStock();
            stock.setReservedQuantity(Math.max(0, stock.getReservedQuantity() - allocation.getQuantity()));
            stockRepository.save(stock);
        }
    }
}
