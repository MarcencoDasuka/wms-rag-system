package com.isd.wms.service;

import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.dto.order_line.OrderLineResponse;
import com.isd.wms.dto.order_line.OrderLineUpdateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.OrderLineNotFoundException;
import com.isd.wms.exception.OrderNotFoundException;
import com.isd.wms.exception.ProductNotFoundException;
import com.isd.wms.exception.TaskNotFoundException;
import com.isd.wms.mapper.OrderLineMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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
    private final SecurityFacade securityFacade;

    public void validateOrderAccess(Order order) {
        if (order == null) {
            return;
        }
        if (securityFacade.hasRole(Role.ROLE_DEV)) {
            return;
        }
        String currentUsername = null;
        Long currentUserId = null;
        try {
            currentUsername = securityFacade.getCurrentUsername();
            currentUserId = securityFacade.getCurrentUserId();
        } catch (Exception ignored) {}
        if (currentUsername == null && currentUserId == null) {
            return;
        }

        final String finalUsername = currentUsername;
        boolean isCreator = order.getCreatedBy() != null && finalUsername != null && order.getCreatedBy().equalsIgnoreCase(finalUsername);
        boolean isSupervisor = currentUserId != null && order.getSupervisor() != null && Objects.equals(order.getSupervisor().getId(), currentUserId);
        List<String> supervisors = orderRepository.findSupervisorUsernamesByOrder(order);
        boolean isTaskSupervisor = finalUsername != null && supervisors.stream().anyMatch(s -> s.equalsIgnoreCase(finalUsername));

        if (!isCreator && !isSupervisor && !isTaskSupervisor) {
            throw new AccessDeniedException(
                "Access denied: Order " + order.getLogicId() + " belongs to another supervisor."
            );
        }
    }

    /**
     * Creates and adds a new order line based on request.
     *
     * @param request the creation request containing orderId, productId, and quantity
     * @return the created OrderLineResponse
     */
    @Transactional
    public OrderLineResponse createOrderLine(OrderLineCreateRequest request) {
        if (request.orderId() == null) {
            throw new InvalidRequestException("Order ID is required to create order line.");
        }
        Order order = getOrder(request.orderId());
        validateOrderAccess(order);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot create order line for order with status: " + order.getStatus() + ". Order status must be CREATED.");
        }
        Product product = getProduct(request.productId());
        OrderLine orderLine = new OrderLine(order, product, request.requestedQuantity());
        return orderLineMapper.toResponse(orderLineRepository.save(orderLine));
    }

    /**
     * Adds a new order line to an order.
     *
     * @param order the parent order
     * @param request the creation request containing product and quantity
     */
    @Transactional
    public void addOrderLine(Order order, OrderLineCreateRequest request) {
        validateOrderAccess(order);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot add order line for order with status: " + order.getStatus() + ". Order status must be CREATED.");
        }
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
        Order currentOrder = orderLine.getOrder();
        validateOrderAccess(currentOrder);
        if (currentOrder.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot update order line for order with status: " + currentOrder.getStatus() + ". Order status must be CREATED.");
        }

        Order targetOrder = getOrder(request.orderId());
        validateOrderAccess(targetOrder);
        if (targetOrder.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot update order line to target order with status: " + targetOrder.getStatus() + ". Target order status must be CREATED.");
        }

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
        Order order = orderLine.getOrder();
        validateOrderAccess(order);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot delete order line for order with status: " + order.getStatus() + ". Order status must be CREATED.");
        }
        releaseReservedStock(orderLine);
        orderLineRepository.delete(orderLine);
    }

    public List<OrderLineResponse> getAll() {
        List<OrderLine> lines = orderLineRepository.findAll();
        if (securityFacade.hasRole(Role.ROLE_DEV)) {
            return lines.stream().map(orderLineMapper::toResponse).toList();
        }
        Long currentUserId = null;
        String currentUsername = null;
        try {
            currentUsername = securityFacade.getCurrentUsername();
            currentUserId = securityFacade.getCurrentUserId();
        } catch (Exception ignored) {}

        if (currentUsername == null && currentUserId == null) {
            return lines.stream().map(orderLineMapper::toResponse).toList();
        }

        final Long finalUserId = currentUserId;
        final String finalUsername = currentUsername;
        return lines.stream()
            .filter(ol -> {
                Order order = ol.getOrder();
                if (order == null) return false;
                if (finalUserId != null && order.getSupervisor() != null && Objects.equals(order.getSupervisor().getId(), finalUserId)) {
                    return true;
                }
                if (order.getCreatedBy() != null && finalUsername != null && order.getCreatedBy().equalsIgnoreCase(finalUsername)) {
                    return true;
                }
                List<String> supervisors = orderRepository.findSupervisorUsernamesByOrder(order);
                return finalUsername != null && supervisors.stream().anyMatch(s -> s.equalsIgnoreCase(finalUsername));
            })
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
        OrderLine orderLine = getOrderLine(orderLineId);
        validateOrderAccess(orderLine.getOrder());
        return orderLineMapper.toResponse(orderLine);
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
