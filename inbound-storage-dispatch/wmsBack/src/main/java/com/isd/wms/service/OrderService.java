package com.isd.wms.service;

import com.isd.wms.dto.order.*;
import com.isd.wms.dto.order.shortage.AffectedOrderLineResponse;
import com.isd.wms.dto.order.shortage.ShortageDetailsResponse;
import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.LocationNotFoundException;
import com.isd.wms.exception.OrderNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.isd.wms.mapper.ExtendedOrderMapper;
import com.isd.wms.mapper.OrderMapper;
import com.isd.wms.repository.*;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.imports.dto.ExtendedOrderInfo;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing customer orders.
 * <p>
 * Provides full lifecycle management: creation, update, deletion, assignment
 * to operators, and retrieval. Orders can be imported from files. The service
 * also handles shortage reporting and extended order details.
 * </p>
 * <p>
 * When an order is assigned, tasks and allocations are generated. Deleting an
 * order releases any reserved stock and disassociates any transport unit.
 * </p>
 *
 * @see Order
 * @see OrderLine
 * @see Task
 * @see Allocation
 * @see TransportUnit
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {
    private final ExtendedOrderMapper extendedOrderMapper;
    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final LocationRepository locationRepository;
    private final OrderLineService orderLineService;
    private final AllocationRepository allocationRepository;
    private final TaskRepository taskRepository;
    private final OrderLineRepository orderLineRepository;
    private final ImportService importService;
    private final SecurityFacade securityFacade;
    private final TaskService taskService;
    private final TransportUnitRepository transportUnitRepository;

    /**
     * Creates an extended order (order with multiple lines).
     *
     * @param request the extended order creation request
     * @return the created order response
     */
    @Transactional
    public OrderResponse addExtendedOrder(ExtendedOrderCreateRequest request) {
        Order order = addOrder(request.order());
        for (OrderLineCreateRequest oRequest : request.lines()) {
            oRequest = new OrderLineCreateRequest(oRequest, order.getId());
            orderLineService.addOrderLine(order, oRequest);
        }
        return orderMapper.toResponse(order, null);
    }

    @Transactional
    public Order addOrder(OrderCreateRequest request) {
        String logicId = request.logicId();

        if (logicId == null || logicId.isBlank()) {
            logicId = generateUniqueOrderLogicId();
        } else if (orderRepository.findByLogicIdIgnoreCase(logicId.trim()).isPresent()) {
            throw new InvalidRequestException("An order with logicId " + logicId + " already exists");
        }

        Order order = new Order(logicId.trim(), getDestinationLocation(request.destinationLocationId()), securityFacade.getCurrentUsername());
        return orderRepository.save(order);
    }

    public void validateOrderAccess(Order order) {
        if (securityFacade.hasRole(Role.ROLE_DEV)) {
            return;
        }
        String currentUsername = securityFacade.getCurrentUsername();
        boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equalsIgnoreCase(currentUsername);
        List<String> supervisors = orderRepository.findSupervisorUsernamesByOrder(order);
        boolean isTaskSupervisor = supervisors.stream().anyMatch(s -> s.equalsIgnoreCase(currentUsername));

        if (!isCreator && !isTaskSupervisor) {
            log.warn("Access denied to order {} (createdBy: {}, taskSupervisors: {}) for user {}",
                order.getId(), order.getCreatedBy(), supervisors, currentUsername);
            throw new AccessDeniedException(
                "Access denied: Order " + order.getLogicId() + " belongs to another supervisor."
            );
        }
    }

    @Transactional
    public OrderResponse updateOrder(Long id, OrderUpdateRequest request) {
        if (!request.status().equals(OrderStatus.CREATED)) {
            throw new InvalidRequestException("Order status must be CREATED to update");
        }
        Order order = getOrder(id);
        validateOrderAccess(order);

        if (request.logicId() != null && !request.logicId().trim().equalsIgnoreCase(order.getLogicId())) {
            if (orderRepository.findByLogicIdIgnoreCase(request.logicId().trim()).isPresent()) {
                throw new InvalidRequestException("An order with logicId " + request.logicId() + " already exists");
            }
        }

        updateOrder(request, order);

        orderRepository.saveAndFlush(order);
        Long operatorId = orderRepository.findOperatorIdByOrderId(order.getId()).orElse(null);
        return orderMapper.toResponse(order, operatorId);
    }

    private void updateOrder(OrderUpdateRequest request, Order order) {
        order.setLogicId(request.logicId().trim());
        order.setDestinationLocation(getDestinationLocation(request.destinationLocationId()));
        order.setStatus(request.status());
    }

    @Transactional
    public ExtendedOrderResponse updateExtendedOrder(Long id, ExtendedOrderCreateRequest request) {
        Order order = getOrder(id);
        validateOrderAccess(order);

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidRequestException("Cannot modify lines of an order that is already assigned or in progress.");
        }

        if (request.order().logicId() != null && !request.order().logicId().trim().equalsIgnoreCase(order.getLogicId())) {
            if (orderRepository.findByLogicIdIgnoreCase(request.order().logicId().trim()).isPresent()) {
                throw new InvalidRequestException("An order with logicId " + request.order().logicId() + " already exists");
            }
        }

        order.setLogicId(request.order().logicId().trim());
        order.setDestinationLocation(getDestinationLocation(request.order().destinationLocationId()));
        Order savedOrder = orderRepository.save(order);

        List<OrderLine> oldLines = orderLineRepository.findAllByOrderId(savedOrder.getId());
        orderLineRepository.deleteAll(oldLines);

        for (OrderLineCreateRequest oRequest : request.lines()) {
            oRequest = new OrderLineCreateRequest(oRequest, savedOrder.getId());
            orderLineService.addOrderLine(savedOrder, oRequest);
        }

        orderLineRepository.flush();

        Long operatorId = orderRepository.findOperatorIdByOrderId(savedOrder.getId()).orElse(null);
        return extendedOrderMapper.toResponse(getOrder(savedOrder.getId()), operatorId);
    }

    /**
     * Deletes an order and releases any reserved stock.
     * Only orders in CREATED or CANCELED status can be deleted.
     *
     * @param id the ID of the order to delete
     */
    @Transactional
    public void deleteOrderById(Long id) {
        Order order = getOrder(id);
        validateOrderAccess(order);

        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.CANCELED) {
            throw new InvalidRequestException("Cannot delete order with status: " + order.getStatus() +
                ". Only CREATED or CANCELED orders can be deleted.");
        }

        releaseReservedStock(order);

        transportUnitRepository.findByOrder(order).ifPresent(tu -> {
            tu.setOrder(null);
            transportUnitRepository.save(tu);
            log.info("Successfully released Transport Unit {} from deleted order {}", tu.getBarcode(), id);
        });

        orderRepository.delete(order);
        log.info("Deleted order and released reserved stock: orderId={}", id);
    }

    public List<OrderResponse> getAllOrders() {
        List<Order> orders = securityFacade.hasRole(Role.ROLE_DEV)
            ? orderRepository.findAll()
            : orderRepository.findAllAccessibleBySupervisor(securityFacade.getCurrentUsername());
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> operatorIdMap = resolveOrderOperatorIds(orderIds);
        Map<Long, String> tuBarcodeMap = resolveOrderTuBarcodes(orderIds);
        return orders.stream()
            .map(order -> orderMapper.toResponse(order, operatorIdMap.get(order.getId()), tuBarcodeMap.get(order.getId())))
            .toList();
    }

    public OrderResponse getOrderById(@NonNull Long orderId) {
        Order order = getOrder(orderId);
        validateOrderAccess(order);
        return orderMapper.toResponse(order, orderRepository.findOperatorIdByOrderId(orderId).orElse(null));
    }

    public Order getOrder(@NonNull Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Assigns an order to an operator. This generates tasks and allocations,
     * updates the order status to ASSIGNED, and links tasks to the operator.
     *
     * @param orderId the ID of the order to assign
     * @param operatorId the ID of the operator
     * @throws InvalidRequestException if the order status does not allow assignment
     */
    @Transactional
    public void assignOrder(Long orderId, Long operatorId) {
        Order order = getOrder(orderId);
        validateOrderAccess(order);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidRequestException("Order assignment is not allowed for this order");
        }

        order.setStatus(OrderStatus.ASSIGNED);
        orderRepository.saveAndFlush(order);

        assignTasks(order);
        assignOrderCascade(orderId, operatorId);
    }

    private void assignTasks(Order order) {
        List<OrderLine> sortedLines = order.getOrderLines().stream()
            .sorted(Comparator.comparing(ol -> ol.getProduct().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        for (OrderLine orderLine : sortedLines) {
            Task task = taskService.createTask(
                TaskType.PICKING_ORDER,
                orderLine.getRequestedQuantity(),
                orderLine.getProduct().getId()
            );
            orderLine.setTask(task);
        }
        orderLineRepository.saveAllAndFlush(order.getOrderLines());
    }

    private void assignOrderCascade(Long orderId, Long operatorId) {
        int tasksUpdated = taskRepository.updateOperatorByOrderId(orderId, operatorId);
        log.info("Updated {} tasks for order {}", tasksUpdated, orderId);

        int allocationsUpdated = allocationRepository.updateStatusByOrderId(orderId, Status.ASSIGNED);
        log.info("Updated {} allocations for order {}", allocationsUpdated, orderId);

        int orderLinesUpdated = orderLineRepository.updateStatusByOrderId(orderId, Status.ASSIGNED);
        log.info("Updated {} order lines for order {}", orderLinesUpdated, orderId);
    }

    private Location getLocation(Long locationId) {
        return locationRepository.findById(locationId)
            .orElseThrow(() -> new LocationNotFoundException(locationId));
    }

    private Location getDestinationLocation(Long locationId) {
        Location location = getLocation(locationId);
        if (!Boolean.TRUE.equals(location.getIsActive()) || !Boolean.TRUE.equals(location.getAvailable())) {
            throw new InvalidRequestException("Cannot route order to inactive or unavailable destination location: " + location.getBarcode());
        }
        return location;
    }

    private String generateUniqueOrderLogicId() {
        for (int i = 0; i < 10; i++) {
            String candidate = "ORD-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!orderRepository.existsByLogicIdIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "ORD-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void releaseReservedStock(Order order) {
        List<OrderLine> orderLines = orderLineRepository.findAllByOrderId(order.getId());
        for (OrderLine orderLine : orderLines) {
            if (orderLine.getTask().isEmpty()) {
                continue;
            }
            List<Allocation> allocations = allocationRepository.findAllByTaskId(orderLine.getTask()
                .orElseThrow(() -> new InvalidRequestException("No allocations for order."))
                .getId());
            for (Allocation allocation : allocations) {
                if (allocation.getStatus() == Status.COMPLETED || allocation.getStatus() == Status.CANCELED) {
                    continue;
                }
                Stock stock = allocation.getStock();
                int updatedReservedQuantity = Math.max(0, stock.getReservedQuantity() -
                    Optional.ofNullable(allocation.getQuantity()).orElse(0));
                stock.setReservedQuantity(updatedReservedQuantity);
                log.info("Released reserved stock on order delete: orderId={}, orderLineId={}, " +
                    "stockId={}, releasedQuantity={}, remainingReserved={}", order.getId(), orderLine.getId(),
                    stock.getId(), allocation.getQuantity(), updatedReservedQuantity);
            }
        }
    }

    public ExtendedOrderResponse getExtendedOrderById(Long orderId) {
        Order order = getOrder(orderId);
        validateOrderAccess(order);
        Long operatorId = orderRepository.findOperatorIdByOrderId(order.getId()).orElse(null);
        return extendedOrderMapper.toResponse(order, operatorId);
    }

    public List<OrderResponse> searchOrders(OrderSearchRequest request) {
        List<Order> orders = orderRepository.filter(
                request.logicId(),
                request.destinationLocationId(),
                request.status(),
                request.createdAt(),
                request.updatedAt()
            );
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> operatorIdMap = resolveOrderOperatorIds(orderIds);
        Map<Long, String> tuBarcodeMap = resolveOrderTuBarcodes(orderIds);
        return orders.stream()
            .map(order -> orderMapper.toResponse(order, operatorIdMap.get(order.getId()), tuBarcodeMap.get(order.getId())))
            .toList();
    }

    public List<ExtendedOrderResponse> getAllExtendedOrders() {
        List<Order> orders = securityFacade.hasRole(Role.ROLE_DEV)
            ? orderRepository.findAll()
            : orderRepository.findAllAccessibleBySupervisor(securityFacade.getCurrentUsername());
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, Long> operatorIdMap = resolveOrderOperatorIds(orderIds);
        Map<Long, String> tuBarcodeMap = resolveOrderTuBarcodes(orderIds);
        return orders.stream()
            .map(order -> extendedOrderMapper.toResponse(order, operatorIdMap.get(order.getId()), tuBarcodeMap.get(order.getId())))
            .toList();
    }

    @Transactional
    public void importOrdersFromFile(MultipartFile file) {
        List<ExtendedOrderCreateRequest> orders = importService.importData(file, ExtendedOrderInfo.class);

        String autoGeneratedId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            orders.stream()
                .map(req -> {
                    if (req.order().logicId() == null) {
                        return new ExtendedOrderCreateRequest(
                            new OrderCreateRequest(autoGeneratedId, req.order().destinationLocationId()),
                            req.lines()
                        );
                    }
                    return req;
                })
                .collect(Collectors.groupingBy(r -> r.order().logicId()))
                .values()
                .stream()
                .peek(this::validateSameOrder)
                .map(group -> new ExtendedOrderCreateRequest(
                    group.getFirst().order(),
                    group.stream()
                        .map(ExtendedOrderCreateRequest::lines)
                        .flatMap(List::stream)
                        .toList()
                ))
                .forEach(this::addExtendedOrder);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidRequestException("The imported file contains invalid order data.");
        }
    }

    private void validateSameOrder(List<ExtendedOrderCreateRequest> group) {
        OrderCreateRequest base = group.getFirst().order();

        boolean inconsistent = group.stream()
            .map(ExtendedOrderCreateRequest::order)
            .anyMatch(o ->
                !Objects.equals(o.destinationLocationId(), base.destinationLocationId())
            );

        if (inconsistent) {
            throw new InvalidRequestException(
                "Invalid import data: same order " + group.getFirst().order().logicId()
                    + " has conflicting field destination."
            );
        }
    }

    /**
     * Retrieves all orders with shortage conditions (partially completed,
     * shortage, canceled) for the current user.
     *
     * @return list of shortage order summaries
     */
    public List<ShortageOrderResponse> getShortageOrders() {
        List<Order> orders = securityFacade.hasRole(Role.ROLE_DEV)
            ? orderRepository.findAll()
            : orderRepository.findAllAccessibleBySupervisor(securityFacade.getCurrentUsername());
        return orders.stream()
            .filter(this::isShortageOrder)
            .map(this::toShortageOrderResponse)
            .sorted((left, right) ->
                right.updatedAt().compareTo(left.updatedAt()))
            .toList();
    }

    public ShortageDetailsResponse getShortageDetails(Long orderId) {
        Order order = getOrder(orderId);
        validateOrderAccess(order);
        List<OrderLine> lines = orderLineRepository.findAllByOrderId(order.getId());
        List<Allocation> allocations = allocationRepository.findAllByOrder(order);

        List<AffectedOrderLineResponse> shortageLines = lines.stream()
            .filter(line -> line.getStatus() == Status.PARTIALLY_COMPLETED
                || line.getStatus() == Status.SHORTAGE
                || line.getStatus() == Status.CANCELED
                || Optional.ofNullable(line.getShortageQuantity()).orElse(0) > 0)
            .map(line -> toAffectedOrderLineResponse(order, line, allocations))
            .toList();

        return new ShortageDetailsResponse(
            order.getId(),
            order.getLogicId(),
            order.getDestinationLocation().getId(),
            order.getDestinationLocation().getBarcode(),
            order.getStatus().name(),
            shortageLines
        );
    }

    private boolean isShortageOrder(Order order) {
        List<OrderLine> lines = order.getOrderLines();
        boolean allCanceled = !lines.isEmpty() && lines.stream()
            .allMatch(line -> line.getStatus() == Status.CANCELED);
        boolean hasShortage = lines.stream().anyMatch(line ->
            line.getStatus() == Status.PARTIALLY_COMPLETED
                || line.getStatus() == Status.SHORTAGE
                || line.getStatus() == Status.CANCELED
                || Optional.ofNullable(line.getShortageQuantity()).orElse(0) > 0
        );
        return hasShortage
            || allCanceled
            || order.getStatus() == OrderStatus.PARTIALLY_COMPLETED
            || order.getStatus() == OrderStatus.SHORTAGE
            || order.getStatus() == OrderStatus.CANCELED;
    }

    private ShortageOrderResponse toShortageOrderResponse(Order order) {
        List<OrderLine> lines = order.getOrderLines();
        long shortageLines = lines.stream()
            .filter(line -> line.getStatus() == Status.PARTIALLY_COMPLETED
                || line.getStatus() == Status.SHORTAGE
                || line.getStatus() == Status.CANCELED
                || Optional.ofNullable(line.getShortageQuantity()).orElse(0) > 0)
            .count();
        return new ShortageOrderResponse(
            order.getId(),
            order.getLogicId(),
            order.getDestinationLocation().getBarcode(),
            order.getStatus().name(),
            lines.size(),
            Math.toIntExact(shortageLines),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    private AffectedOrderLineResponse toAffectedOrderLineResponse(
        Order order,
        OrderLine line,
        List<Allocation> allocations) {
        List<Allocation> lineAllocations = allocations.stream()
            .filter(allocation -> allocation.getTask().getId()
                .equals(line.getTask().map(Task::getId).orElse(null)))
            .sorted(Comparator.comparing(BaseTimestampEntity::getCreatedAt))
            .toList();

        int deliveredQuantity = resolveDeliveredQuantity(line, lineAllocations);
        int shortageQuantity = Optional.ofNullable(line.getShortageQuantity())
            .orElse(Math.max(0, line.getRequestedQuantity() - deliveredQuantity));
        Long originalLocationId = lineAllocations.isEmpty() ? null :
            lineAllocations.getFirst().getStock().getLocation().getId();
        String originalLocationBarcode = lineAllocations.isEmpty() ? null :
            lineAllocations.getFirst().getStock().getLocation().getBarcode();
        Long reallocatedLocationId = lineAllocations.stream()
            .map(allocation -> allocation.getStock().getLocation())
            .filter(location -> location != null && !Objects.equals(location.getId(), originalLocationId))
            .map(Location::getId)
            .findFirst()
            .orElse(null);
        String reallocatedLocationBarcode = lineAllocations.stream()
            .map(allocation -> allocation.getStock().getLocation())
            .filter(location -> location != null && !Objects.equals(location.getId(), originalLocationId))
            .map(Location::getBarcode)
            .findFirst()
            .orElse(null);
        boolean revalidationRequired =
            line.getTask().map(Task::getStatus).orElse(null) == com.isd.wms.enums.TaskStatus.REQUIRES_REVALIDATION;

        return new AffectedOrderLineResponse(
            order.getId(),
            order.getLogicId(),
            line.getId(),
            line.getTask()
                .map(Task::getId)
                .orElse(null),
            line.getProduct().getId(),
            line.getProduct().getName(),
            line.getRequestedQuantity(),
            deliveredQuantity,
            shortageQuantity,
            originalLocationId,
            originalLocationBarcode,
            reallocatedLocationId,
            reallocatedLocationBarcode,
            line.getStatus().name(),
            revalidationRequired,
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    private int resolveDeliveredQuantity(OrderLine line, List<Allocation> lineAllocations) {
        if (line.getOrder().getStatus() != OrderStatus.COMPLETED
            && line.getOrder().getStatus() != OrderStatus.PARTIALLY_COMPLETED) {
            return 0;
        }

        Integer deliveredQuantity = line.getDeliveredQuantity();
        if (deliveredQuantity != null && deliveredQuantity > 0) {
            return deliveredQuantity;
        }

        return lineAllocations.stream()
            .filter(allocation -> allocation.getStatus() != Status.CANCELED)
            .mapToInt(allocation -> Optional.ofNullable(allocation.getQuantity()).orElse(0))
            .sum();
    }

    private Map<Long, Long> resolveOrderOperatorIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<com.isd.wms.repository.projections.OrderOperatorProjection> list = orderRepository.findOperatorIdsByOrderIds(orderIds);
        Map<Long, Long> map = new HashMap<>();
        for (var proj : list) {
            if (proj.getOrderId() != null && proj.getOperatorId() != null) {
                map.putIfAbsent(proj.getOrderId(), proj.getOperatorId());
            }
        }
        return map;
    }

    private Map<Long, String> resolveOrderTuBarcodes(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TransportUnit> tus = transportUnitRepository.findAllByOrderIds(orderIds);
        Map<Long, String> map = new HashMap<>();
        for (TransportUnit tu : tus) {
            if (tu.getOrder() != null && tu.getOrder().getId() != null) {
                map.putIfAbsent(tu.getOrder().getId(), tu.getBarcode());
            }
        }
        return map;
    }
}
