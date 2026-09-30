package com.isd.wms.service.ai;

import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.ReplenishmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
@RequiredArgsConstructor
public class WarehouseAiTools {

    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private final OrderService orderService;
    private final ReplenishmentService replenishmentService;

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
        List<User> operators = userRepository.findAll().stream()
            .filter(u -> u.getUserRole().name().equals("ROLE_OPERATOR"))
            .toList();

        return operators.isEmpty() ? "No operators found in the system." : formatOperators(operators);
    }

    /**
     * Automatically distributes all unassigned orders and replenishments among
     * available operators using least‑loaded balancing (based on current task count).
     *
     * @return a summary of the number of orders and replenishments assigned
     */
    @Tool(description = "Automatically distributes ALL unassigned Orders and Replenishments among available operators (Least-Loaded balancing).")
    public String autoDistributeWorkload() {
        log.info("AI invoked autoDistributeWorkload");
        List<User> operators = userRepository.findAll().stream().filter(u -> u.getUserRole().name().equals("ROLE_OPERATOR")).toList();
        if (operators.isEmpty()) return "Error: No operators registered in the system.";

        List<Order> unassignedOrders = orderRepository.findAll().stream().filter(o -> o.getStatus() == OrderStatus.CREATED).toList();
        List<Replenishment> unassignedReplenishments = replenishmentRepository.findAll().stream().filter(r -> r.getStatus() == Status.CREATED).toList();

        if (unassignedOrders.isEmpty() && unassignedReplenishments.isEmpty()) {
            return "All tasks are already assigned. Workload is balanced.";
        }

        Map<User, Long> operatorLoadMap = calculateInitialWorkload(operators);
        int[] counts = executeDistribution(operators, operatorLoadMap, unassignedOrders, unassignedReplenishments);

        return String.format("Workload distribution completed successfully! Assigned %d Orders and %d Replenishments among %d operators.", counts[0], counts[1], operators.size());
    }

    private Map<User, Long> calculateInitialWorkload(List<User> operators) {
        List<Task> activeTasks = replenishmentRepository.findAll().stream()
            .map(Replenishment::getTask)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        return operators.stream().collect(Collectors.toMap(op -> op, op -> activeTasks.stream()
            .filter(t -> t.getOperator().isPresent() && t.getOperator().get().equals(op)).count()));
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
