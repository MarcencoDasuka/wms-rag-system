package com.isd.wms.service;

import com.isd.wms.dto.dashboard.ActiveLocationResponse;
import com.isd.wms.dto.dashboard.ActivityFeedResponse;
import com.isd.wms.dto.dashboard.CompletedOrdersTrendResponse;
import com.isd.wms.dto.dashboard.LowStockItemResponse;
import com.isd.wms.dto.dashboard.NeedsAttentionResponse;
import com.isd.wms.dto.dashboard.OperatorPerformanceResponse;
import com.isd.wms.dto.dashboard.OrderStatusCountResponse;
import com.isd.wms.dto.dashboard.SupervisorDashboardResponse;
import com.isd.wms.dto.dashboard.SupervisorDashboardSummaryResponse;
import com.isd.wms.dto.dashboard.TopPickedProductResponse;
import com.isd.wms.entity.InventoryHistory;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.InventoryOperationType;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.TaskStatus;
import com.isd.wms.repository.InventoryHistoryRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.TaskRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.validation.SecurityFacade;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that provides aggregated metrics and insights for the supervisor dashboard.
 * <p>
 * The dashboard includes summary statistics (orders, tasks, operators),
 * order status breakdown, performance trends, operator performance,
 * top picked products, low stock items, most active locations,
 * items needing attention, and a recent activity feed.
 * </p>
 * <p>
 * Data is filtered to the current supervisor's context where applicable.
 * All time comparisons are based on the current date (today).
 * </p>
 *
 * @see Order
 * @see Task
 * @see Stock
 * @see InventoryHistory
 * @see User
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupervisorDashboardService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;
    private static final int CRITICAL_LOW_STOCK_THRESHOLD = 2;
    private static final Duration DELAYED_ASSIGNED_ORDER_THRESHOLD = Duration.ofHours(1);
    private static final Set<InventoryOperationType> ADJUSTMENT_OPERATION_TYPES = EnumSet.of(
            InventoryOperationType.ADD_STOCK,
            InventoryOperationType.REMOVE_STOCK,
            InventoryOperationType.ADJUST_STOCK
    );

    private final OrderRepository orderRepository;
    private final TaskRepository taskRepository;
    private final StockRepository stockRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;
    private final UserRepository userRepository;
    private final SecurityFacade securityFacade;

    /**
     * Builds the complete supervisor dashboard response.
     *
     * @return a fully populated dashboard DTO
     */
    public SupervisorDashboardResponse getDashboard() {
        String username = securityFacade.getCurrentUsername();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

        log.info("Supervisor dashboard requested by '{}'", username);

        try {
            List<Order> orders = orderRepository.findAllByCreatedByUsername(username);
            List<Task> tasks = taskRepository.findAll();
            List<Stock> stocks = stockRepository.findAllByAvailableIsTrue();
            List<InventoryHistory> history = inventoryHistoryRepository.findAll();
            List<User> operators = userRepository.findAllByIsActiveTrue().stream()
                    .filter(user -> user.getUserRole() == Role.ROLE_OPERATOR)
                    .toList();

            List<Stock> lowStockStocks = findLowStockStocks(stocks);
            List<InventoryHistory> todayHistory = history.stream()
                    .filter(item -> isWithin(item.getTimestamp(), startOfToday, startOfTomorrow))
                    .toList();

            SupervisorDashboardSummaryResponse summary = buildSummary(
                    orders,
                    tasks,
                    operators,
                    lowStockStocks,
                    todayHistory,
                    startOfToday,
                    startOfTomorrow
            );

            return new SupervisorDashboardResponse(
                    summary,
                    buildOrdersByStatus(orders),
                    buildCompletedOrdersTrend(orders, today),
                    buildOperatorPerformance(operators, orders, tasks, startOfToday, startOfTomorrow),
                    buildTopPickedProducts(todayHistory),
                    buildLowStockItems(lowStockStocks),
                    buildMostActiveLocations(todayHistory),
                    buildNeedsAttention(orders, lowStockStocks, now),
                    buildActivityFeed(orders, todayHistory),
                    now
            );
        } catch (Exception exception) {
            log.warn("Failed to calculate supervisor dashboard for '{}': {}", username, exception.getMessage());
            throw exception;
        }
    }

    private SupervisorDashboardSummaryResponse buildSummary(
            List<Order> orders,
            List<Task> tasks,
            List<User> operators,
            List<Stock> lowStockStocks,
            List<InventoryHistory> todayHistory,
            LocalDateTime startOfToday,
            LocalDateTime startOfTomorrow
    ) {
        long ordersToday = orders.stream().filter(order -> isWithin(order.getCreatedAt(),
            startOfToday, startOfTomorrow)).count();
        long completedOrdersToday = orders.stream()
                .filter(order -> isCompletedLike(order.getStatus()))
                .filter(order -> isWithin(order.getUpdatedAt(), startOfToday, startOfTomorrow))
                .count();
        long inProgressOrders = orders.stream().filter(order ->
            order.getStatus() == OrderStatus.IN_PROGRESS).count();
        long canceledOrdersToday = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.CANCELED)
                .filter(order -> isWithin(order.getUpdatedAt(), startOfToday, startOfTomorrow))
                .count();
        long activeOperators = operators.stream()
                .filter(operator -> countActiveTasks(operator, tasks) > 0)
                .count();
        long averageCompletionTimeMinutes = averageOrderCompletionMinutes(
                orders.stream()
                        .filter(order -> isCompletedLike(order.getStatus()))
                        .filter(order -> isWithin(order.getUpdatedAt(), startOfToday, startOfTomorrow))
                        .toList()
        );
        long ordersWaitingForDispatch = orders.stream().filter(order ->
            order.getStatus() == OrderStatus.PICKED).count();
        long stockMovementsToday = todayHistory.size();
        long inventoryAdjustmentsToday = todayHistory.stream()
                .filter(item -> ADJUSTMENT_OPERATION_TYPES.contains(item.getOperationType()))
                .count();

        return new SupervisorDashboardSummaryResponse(
                ordersToday,
                completedOrdersToday,
                inProgressOrders,
                0,
                canceledOrdersToday,
                activeOperators,
                operators.size(),
                averageCompletionTimeMinutes,
                lowStockStocks.size(),
                ordersWaitingForDispatch,
                stockMovementsToday,
                inventoryAdjustmentsToday,
                0
        );
    }

    private List<OrderStatusCountResponse> buildOrdersByStatus(List<Order> orders) {
        Map<OrderStatus, Long> counts = orders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

        return Arrays.stream(OrderStatus.values())
                .map(status -> new OrderStatusCountResponse(status.name(), counts.getOrDefault(status, 0L)))
                .toList();
    }

    private List<CompletedOrdersTrendResponse> buildCompletedOrdersTrend(List<Order> orders, LocalDate today) {
        Map<LocalDate, Long> completedByDay = orders.stream()
                .filter(order -> isCompletedLike(order.getStatus()))
                .filter(order -> order.getUpdatedAt() != null)
                .collect(Collectors.groupingBy(order -> order.getUpdatedAt().toLocalDate(), Collectors.counting()));

        List<CompletedOrdersTrendResponse> trend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            trend.add(new CompletedOrdersTrendResponse(date, completedByDay.getOrDefault(date, 0L)));
        }
        return trend;
    }

    private List<OperatorPerformanceResponse> buildOperatorPerformance(
            List<User> operators,
            List<Order> orders,
            List<Task> tasks,
            LocalDateTime startOfToday,
            LocalDateTime startOfTomorrow
    ) {
        return operators.stream()
            .map(operator -> {
                long activeTasks = countActiveTasks(operator, tasks);
                long completedOrdersToday = orders.stream()
                        .filter(order -> isCompletedLike(order.getStatus()))
                        .filter(order -> isWithin(order.getUpdatedAt(), startOfToday, startOfTomorrow))
                        .filter(order -> orderHasOperator(order, operator))
                        .count();
                long averageCompletionMinutes = averageTaskCompletionMinutes(operator, tasks,
                    startOfToday, startOfTomorrow);
                String status = operatorStatus(operator, tasks);

                return new OperatorPerformanceResponse(
                        operator.getId(),
                        operator.getUsername(),
                        completedOrdersToday,
                        activeTasks,
                        averageCompletionMinutes,
                        status
                );
            })
            .sorted(Comparator.comparingLong(OperatorPerformanceResponse::completedOrdersToday).reversed()
                    .thenComparing(OperatorPerformanceResponse::operatorName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    private List<TopPickedProductResponse> buildTopPickedProducts(List<InventoryHistory> todayHistory) {
        Map<Long, ProductAggregate> aggregates = new HashMap<>();

        todayHistory.stream()
                .filter(item -> item.getOperationType() == InventoryOperationType.PICKING)
                .forEach(item -> {
                    Product product = item.getProduct();
                    if (product == null || product.getId() == null) {
                        return;
                    }

                    ProductAggregate aggregate = aggregates.computeIfAbsent(product.getId(), ignored ->
                            new ProductAggregate(product.getName(), Optional.ofNullable(product.getBarcode()).orElse("")));
                    aggregate.quantity += Math.abs(Optional.ofNullable(item.getAlteredQuantity()).orElse(0));
                });

        return aggregates.entrySet().stream()
                .map(entry -> new TopPickedProductResponse(
                        entry.getKey(),
                        entry.getValue().name,
                        entry.getValue().sku,
                        entry.getValue().quantity
                ))
                .sorted(Comparator.comparingLong(TopPickedProductResponse::pickedQuantity).reversed())
                .limit(5)
                .toList();
    }

    private List<LowStockItemResponse> buildLowStockItems(List<Stock> lowStockStocks) {
        return lowStockStocks.stream()
                .map(stock -> {
                    Product product = stock.getProduct().orElse(null);
                    int availableQuantity = Optional.ofNullable(stock.getQuantity()).orElse(0) -
                        Optional.ofNullable(stock.getReservedQuantity()).orElse(0);
                    int minimumQuantity = lowStockThreshold(product);
                    return new LowStockItemResponse(
                            product == null ? null : product.getId(),
                            product == null ? "Unknown product" : product.getName(),
                            product == null ? null : product.getBarcode(),
                            stock.getLocation() == null ? null : stock.getLocation().getBarcode(),
                            availableQuantity,
                            minimumQuantity,
                            availableQuantity <= CRITICAL_LOW_STOCK_THRESHOLD ? "CRITICAL" : "LOW"
                    );
                })
                .sorted(Comparator.comparingInt(LowStockItemResponse::quantity))
                .limit(10)
                .toList();
    }

    private List<ActiveLocationResponse> buildMostActiveLocations(List<InventoryHistory> todayHistory) {
        Map<Long, LocationAggregate> counts = new HashMap<>();

        todayHistory.forEach(item -> {
            if (item.getSourceLocation() != null && item.getSourceLocation().getId() != null) {
                LocationAggregate aggregate = counts.computeIfAbsent(item.getSourceLocation().getId(), ignored ->
                        new LocationAggregate(item.getSourceLocation().getBarcode()));
                aggregate.movements++;
            }
            if (item.getDestinationLocation() != null && item.getDestinationLocation().getId() != null) {
                LocationAggregate aggregate = counts.computeIfAbsent(item.getDestinationLocation().getId(), ignored ->
                        new LocationAggregate(item.getDestinationLocation().getBarcode()));
                aggregate.movements++;
            }
        });

        return counts.entrySet().stream()
                .map(entry -> new ActiveLocationResponse(entry.getKey(), entry.getValue().code, entry.getValue().movements))
                .sorted(Comparator.comparingLong(ActiveLocationResponse::movementsToday).reversed())
                .limit(5)
                .toList();
    }

    private List<NeedsAttentionResponse> buildNeedsAttention(List<Order> orders, List<Stock> lowStockStocks, LocalDateTime now) {
        List<NeedsAttentionResponse> items = new ArrayList<>();

        orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.ASSIGNED)
                .filter(order -> order.getUpdatedAt() != null)
                .filter(order -> Duration.between(order.getUpdatedAt(), now).compareTo(DELAYED_ASSIGNED_ORDER_THRESHOLD) > 0)
                .sorted(Comparator.comparing(Order::getUpdatedAt))
                .limit(5)
                .forEach(order -> items.add(new NeedsAttentionResponse(
                        "DELAYED_ASSIGNED_ORDER",
                        "WARNING",
                        "Order " + order.getLogicId() + " is waiting too long",
                        "The order is still assigned and has not moved recently.",
                        order.getId(),
                        order.getUpdatedAt()
                )));

        buildLowStockItems(lowStockStocks).stream()
                .limit(5)
                .forEach(item -> items.add(new NeedsAttentionResponse(
                        "LOW_STOCK",
                        Objects.equals(item.status(), "CRITICAL") ? "CRITICAL" : "WARNING",
                        item.productName() + " is low on stock",
                        "Location " + Optional.ofNullable(item.locationCode()).orElse("-")
                                + " has " + item.quantity() + " units getAvailableQuantity, minimum " + item.minimumQuantity() + ".",
                        item.productId(),
                        now
                )));

        return items.stream()
                .sorted(Comparator.comparing(NeedsAttentionResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .toList();
    }

    private List<ActivityFeedResponse> buildActivityFeed(List<Order> orders, List<InventoryHistory> todayHistory) {
        List<ActivityFeedResponse> feed = new ArrayList<>();

        orders.stream()
                .sorted(Comparator.comparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .forEach(order -> feed.add(new ActivityFeedResponse(
                        "ORDER_" + order.getStatus().name(),
                        "Order " + order.getLogicId() + " is " + order.getStatus().name().replace('_', ' ').toLowerCase() + ".",
                        null,
                        Optional.ofNullable(order.getUpdatedAt()).orElse(order.getCreatedAt())
                )));

        todayHistory.stream()
                .sorted(Comparator.comparing(InventoryHistory::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .forEach(item -> feed.add(new ActivityFeedResponse(
                        item.getOperationType().name(),
                        inventoryMessage(item),
                        item.getUser() == null ? null : item.getUser().getUsername(),
                        item.getTimestamp()
                )));

        return feed.stream()
                .filter(item -> item.createdAt() != null)
                .sorted(Comparator.comparing(ActivityFeedResponse::createdAt).reversed())
                .limit(10)
                .toList();
    }

    private String inventoryMessage(InventoryHistory item) {
        String productName = item.getProduct() == null ? "Unknown product" : item.getProduct().getName();
        return switch (item.getOperationType()) {
            case PICKING -> "Picked " + Math.abs(Optional.ofNullable(item.getAlteredQuantity()).orElse(0)) + " units of " + productName + ".";
            case ADD_STOCK -> "Added stock for " + productName + ".";
            case REMOVE_STOCK -> "Removed stock for " + productName + ".";
            case ADJUST_STOCK -> "Adjusted stock for " + productName + ".";
            case MOVE_STOCK -> "Moved stock for " + productName + ".";
            case PICKING_SHORTAGE -> "Registered picking shortage for " + productName + ".";
            case REPLENISHMENT_SHORTAGE -> "Registered replenishment shortage for " + productName + ".";
        };
    }

    private List<Stock> findLowStockStocks(List<Stock> stocks) {
        return stocks.stream()
                .filter(stock -> stock.getLocation() != null)
                .filter(stock -> {
                    Product product = stock.getProduct().orElse(null);
                    int availableQuantity = Optional.ofNullable(stock.getQuantity()).orElse(0) - Optional.ofNullable(stock.getReservedQuantity()).orElse(0);
                    return availableQuantity <= lowStockThreshold(product);
                })
                .sorted(Comparator.comparingInt(stock ->
                        Optional.ofNullable(stock.getQuantity()).orElse(0) - Optional.ofNullable(stock.getReservedQuantity()).orElse(0)))
                .toList();
    }

    private int lowStockThreshold(Product product) {
        if (product != null) {
            return product.getMinThreshold().orElse(DEFAULT_LOW_STOCK_THRESHOLD);
        }
        return DEFAULT_LOW_STOCK_THRESHOLD;
    }

    private boolean isWithin(LocalDateTime timestamp, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return timestamp != null && !timestamp.isBefore(startInclusive) && timestamp.isBefore(endExclusive);
    }

    private boolean isCompletedLike(OrderStatus status) {
        return status == OrderStatus.COMPLETED || status == OrderStatus.PARTIALLY_COMPLETED;
    }

    private long averageOrderCompletionMinutes(List<Order> orders) {
        return Math.round(orders.stream()
                .filter(order -> order.getCreatedAt() != null && order.getUpdatedAt() != null)
                .mapToLong(order -> Duration.between(order.getCreatedAt(), order.getUpdatedAt()).toMinutes())
                .average()
                .orElse(0));
    }

    private long averageTaskCompletionMinutes(User operator, List<Task> tasks, LocalDateTime startOfToday, LocalDateTime startOfTomorrow) {
        return Math.round(tasks.stream()
                .filter(task -> task.getOperator().map(User::getId).filter(operator.getId()::equals).isPresent())
                .filter(task -> task.getCompletedAt() != null)
                .filter(task -> isWithin(task.getCompletedAt(), startOfToday, startOfTomorrow))
                .filter(task -> task.getCreatedAt() != null)
                .mapToLong(task -> Duration.between(task.getCreatedAt(), task.getCompletedAt()).toMinutes())
                .average()
                .orElse(0));
    }

    private long countActiveTasks(User operator, List<Task> tasks) {
        return tasks.stream()
                .filter(task -> task.getOperator().map(User::getId).filter(operator.getId()::equals).isPresent())
                .filter(task -> task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELED)
                .count();
    }

    private String operatorStatus(User operator, List<Task> tasks) {
        List<Task> operatorTasks = tasks.stream()
                .filter(task -> task.getOperator().map(User::getId).filter(operator.getId()::equals).isPresent())
                .toList();

        boolean hasDelayedTask = operatorTasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELED)
                .anyMatch(task -> task.getCreatedAt() != null && Duration.between(task.getCreatedAt(), LocalDateTime.now()).toHours() >= 2);

        if (hasDelayedTask) {
            return "DELAYED";
        }
        if (operatorTasks.stream().anyMatch(task -> task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELED)) {
            return "ACTIVE";
        }
        return "IDLE";
    }

    private boolean orderHasOperator(Order order, User operator) {
        return orderRepository.isOrderAssignedToOperator(order, operator.getId());
    }

    private static final class ProductAggregate {
        private final String name;
        private final String sku;
        private long quantity;

        private ProductAggregate(String name, String sku) {
            this.name = name;
            this.sku = sku;
        }
    }

    private static final class LocationAggregate {
        private final String code;
        private long movements;

        private LocationAggregate(String code) {
            this.code = code;
        }
    }
}
