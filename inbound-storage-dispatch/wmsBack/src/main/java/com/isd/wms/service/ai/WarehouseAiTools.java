package com.isd.wms.service.ai;

import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.enums.Status;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ReplenishmentService;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI tool functions for warehouse‑wide and operator management.
 * <p>
 * Provides tools for listing all warehouse locations, listing available operators,
 * and automatically distributing unassigned orders and replenishments among
 * operators using a least‑loaded balancing algorithm.
 * </p>
 *
 * @see Location
 * @see User
 * @see OrderService
 * @see ReplenishmentService
 */
@Slf4j
@Service("warehouseAiTools")
public class WarehouseAiTools {

    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private final OrderService orderService;
    private final ReplenishmentService replenishmentService;
    private final AiToolSecurityBoundary securityBoundary;
    private final SecurityFacade securityFacade;

    @Autowired
    public WarehouseAiTools(
            LocationRepository locationRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            ReplenishmentRepository replenishmentRepository,
            OrderService orderService,
            ReplenishmentService replenishmentService,
            AiToolSecurityBoundary securityBoundary,
            SecurityFacade securityFacade) {
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.replenishmentRepository = replenishmentRepository;
        this.orderService = orderService;
        this.replenishmentService = replenishmentService;
        this.securityBoundary = securityBoundary;
        this.securityFacade = securityFacade;
    }

    public WarehouseAiTools(
            LocationRepository locationRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            ReplenishmentRepository replenishmentRepository,
            OrderService orderService,
            ReplenishmentService replenishmentService,
            AiToolSecurityBoundary securityBoundary) {
        this(locationRepository, userRepository, orderRepository, replenishmentRepository, orderService, replenishmentService, securityBoundary, null);
    }

    /**
     * Lists all physical locations (shelves) in the warehouse with their zones.
     *
     * @return a Markdown table of locations
     */
    @Tool(description = "Lists all available physical locations (shelves) in the warehouse.")
    public String getWarehouseLocations() {
        log.info("AI invoked getWarehouseLocations tool");
        List<Location> locations = locationRepository.findAll();
        return locations.isEmpty() ? "No locations found in the warehouse." : formatLocations(locations);
    }

    /**
     * Lists all operators registered in the system.
     *
     * @return a Markdown table of operators with usernames and IDs
     */
    @Tool(description = "Lists all registered Operators in the system.")
    public String getAvailableOperators() {
        log.info("AI invoked getAvailableOperators tool");
        List<User> operators = userRepository.findByUserRoleAndIsActiveTrue(Role.ROLE_OPERATOR);

        return operators.isEmpty() ? "No operators found in the system." : formatOperators(operators);
    }

    /**
     * Automatically distributes all unassigned orders and replenishments among
     * available operators using least‑loaded balancing (based on current task count).
     *
     * @return a summary of the number of orders and replenishments assigned
     */
    @Tool(description = "Automatically distributes ALL unassigned Orders and Replenishments among available operators (Least-Loaded balancing). Requires SUPERVISOR or DEV role.")
    public String autoDistributeWorkload() {
        securityBoundary.enforceSupervisorOrDev("autoDistributeWorkload");
        log.info("AI invoked autoDistributeWorkload");
        List<User> operators = userRepository.findByUserRoleAndIsActiveTrue(Role.ROLE_OPERATOR);
        if (operators.isEmpty()) return "Error: No operators registered in the system.";

        List<Order> unassignedOrders = orderRepository.findAll().stream().filter(o -> o.getStatus() == OrderStatus.CREATED).toList();
        List<Replenishment> unassignedReplenishments = replenishmentRepository.findAll().stream().filter(r -> r.getStatus() == Status.CREATED).toList();

        if (securityFacade != null && !securityFacade.hasRole(Role.ROLE_DEV)) {
            Long currentUserId = null;
            try {
                currentUserId = securityFacade.getCurrentUserId();
            } catch (Exception ignored) {}
            String currentUsername = securityFacade.getCurrentUsername();
            final Long finalUserId = currentUserId;

            unassignedOrders = unassignedOrders.stream()
                .filter(o -> {
                    boolean isSupervisorById = finalUserId != null && o.getSupervisor() != null && Objects.equals(o.getSupervisor().getId(), finalUserId);
                    boolean isCreator = currentUsername != null && o.getCreatedBy() != null && o.getCreatedBy().equalsIgnoreCase(currentUsername);
                    boolean isUnowned = (o.getCreatedBy() == null || "system".equalsIgnoreCase(o.getCreatedBy()))
                        && o.getSupervisor() == null;
                    return isSupervisorById || isCreator || isUnowned;
                })
                .toList();

            unassignedReplenishments = unassignedReplenishments.stream()
                .filter(r -> {
                    String taskSupervisor = r.getTask().map(Task::getSupervisor).map(User::getUsername).orElse(null);
                    boolean isSupervisorById = finalUserId != null && r.getSupervisor() != null && Objects.equals(r.getSupervisor().getId(), finalUserId);
                    boolean isCreator = currentUsername != null && r.getCreatedBy() != null && r.getCreatedBy().equalsIgnoreCase(currentUsername);
                    boolean isTaskSupervisor = currentUsername != null && taskSupervisor != null && taskSupervisor.equalsIgnoreCase(currentUsername);
                    boolean isUnowned = (r.getCreatedBy() == null || "system".equalsIgnoreCase(r.getCreatedBy()))
                        && r.getSupervisor() == null
                        && taskSupervisor == null;
                    return isSupervisorById || isCreator || isTaskSupervisor || isUnowned;
                })
                .toList();
        }

        if (unassignedOrders.isEmpty() && unassignedReplenishments.isEmpty()) {
            return "All tasks are already assigned. Workload is balanced.";
        }

        Map<User, Long> operatorLoadMap = calculateInitialWorkload(operators);
        int[] counts = executeDistribution(operators, operatorLoadMap, unassignedOrders, unassignedReplenishments);
        securityBoundary.auditMutation("autoDistributeWorkload", "WAREHOUSE",
            String.format("Assigned %d orders and %d replenishments among %d operators", counts[0], counts[1], operators.size()));

        return String.format("Workload distribution completed successfully! Assigned %d Orders and %d Replenishments among %d operators.", counts[0], counts[1], operators.size());
    }

    private Map<User, Long> calculateInitialWorkload(List<User> operators) {
        List<Task> activeReplTasks = replenishmentRepository.findAll().stream()
            .filter(r -> r.getStatus() == Status.ASSIGNED || r.getStatus() == Status.IN_PROGRESS)
            .map(Replenishment::getTask)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        List<Order> activeOrders = orderRepository.findAll().stream()
            .filter(o -> o.getStatus() == OrderStatus.ASSIGNED || o.getStatus() == OrderStatus.IN_PROGRESS)
            .toList();

        Map<Long, Long> orderOperatorMap = new HashMap<>();
        List<Long> orderIds = activeOrders.stream().map(Order::getId).filter(Objects::nonNull).toList();
        if (!orderIds.isEmpty()) {
            for (var proj : orderRepository.findOperatorIdsByOrderIds(orderIds)) {
                if (proj.getOrderId() != null && proj.getOperatorId() != null) {
                    orderOperatorMap.putIfAbsent(proj.getOrderId(), proj.getOperatorId());
                }
            }
        }

        Map<User, Long> loadMap = new HashMap<>();
        for (User op : operators) {
            long replCount = activeReplTasks.stream()
                .filter(t -> t.getOperator().isPresent() && (Objects.equals(t.getOperator().get().getId(), op.getId()) || t.getOperator().get().equals(op)))
                .count();

            long orderCount = activeOrders.stream()
                .filter(o -> {
                    Long assignedOpId = orderOperatorMap.get(o.getId());
                    if (assignedOpId != null && assignedOpId.equals(op.getId())) {
                        return true;
                    }
                    if (o.getOrderLines() != null) {
                        return o.getOrderLines().stream()
                            .map(OrderLine::getTask)
                            .flatMap(Optional::stream)
                            .anyMatch(t -> t.getOperator().isPresent() && (Objects.equals(t.getOperator().get().getId(), op.getId()) || t.getOperator().get().equals(op)));
                    }
                    return false;
                })
                .count();

            loadMap.put(op, replCount + orderCount);
        }
        return loadMap;
    }

    private int[] executeDistribution(List<User> operators, Map<User, Long> loadMap, List<Order> orders, List<Replenishment> replenishments) {
        int assignedOrders = 0, assignedRepls = 0;

        for (Order order : orders) {
            User leastLoaded = getLeastLoadedOperator(operators, loadMap);
            try {
                orderService.assignOrder(order.getId(), leastLoaded.getId());
                loadMap.put(leastLoaded, loadMap.get(leastLoaded) + 1);
                assignedOrders++;
            } catch (Exception ignored) {}
        }
        for (Replenishment repl : replenishments) {
            User leastLoaded = getLeastLoadedOperator(operators, loadMap);
            try {
                replenishmentService.assignReplenishment(repl.getId(), leastLoaded.getId());
                loadMap.put(leastLoaded, loadMap.get(leastLoaded) + 1);
                assignedRepls++;
            } catch (Exception ignored) {}
        }
        return new int[]{assignedOrders, assignedRepls};
    }

    private User getLeastLoadedOperator(List<User> operators, Map<User, Long> loadMap) {
        return loadMap.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue)).map(Map.Entry::getKey).orElse(operators.get(0));
    }

    private String formatLocations(List<Location> locations) {
        StringBuilder sb = new StringBuilder("### Available Locations\n");
        sb.append("| Location Barcode | Zone |\n");
        sb.append("|------------------|------|\n");
        locations.forEach(l -> sb.append(String.format("| %s | %s |\n", l.getBarcode(), l.getZone() != null ? l.getZone().name() : "N/A")));
        return sb.toString();
    }

    private String formatOperators(List<User> operators) {
        StringBuilder sb = new StringBuilder("### Available Operators\n");
        sb.append("| Username | Operator ID |\n");
        sb.append("|----------|-------------|\n");
        operators.forEach(op -> sb.append(String.format("| %s | %d |\n", op.getUsername(), op.getId())));
        return sb.toString();
    }
}
