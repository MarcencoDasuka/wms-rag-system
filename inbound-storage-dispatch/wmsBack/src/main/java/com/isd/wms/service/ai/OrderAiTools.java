package com.isd.wms.service.ai;

import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Role;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.service.OrderService;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * AI tools for read-only order queries and shortage analysis.
 * Mutating operations have been separated into {@link OrderMutatingAiTools}
 * to establish strict authorization and confirmation boundaries.
 */
@Slf4j
@Service("orderAiTools")
public class OrderAiTools {

    public record AiOrderItem(String productBarcode, Integer quantity) {
    }

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderMutatingAiTools orderMutatingAiTools;
    private final SecurityFacade securityFacade;

    @Autowired
    public OrderAiTools(
            OrderService orderService,
            OrderRepository orderRepository,
            OrderMutatingAiTools orderMutatingAiTools,
            SecurityFacade securityFacade) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.orderMutatingAiTools = orderMutatingAiTools;
        this.securityFacade = securityFacade;
    }

    public OrderAiTools(
            OrderService orderService,
            OrderRepository orderRepository,
            OrderMutatingAiTools orderMutatingAiTools) {
        this(orderService, orderRepository, orderMutatingAiTools, null);
    }

    @Tool(description = "Returns a summary of all active customer orders. Use this when the user asks about current orders, tasks in orders, or unassigned orders.")
    public String getActiveOrdersInfo() {
        log.info("AI invoked getActiveOrdersInfo tool");

        List<Order> allOrders = (securityFacade != null && !securityFacade.hasRole(Role.ROLE_DEV))
            ? orderRepository.findAllAccessibleBySupervisor(securityFacade.getCurrentUsername())
            : orderRepository.findAll();

        Long currentUserId = null;
        try {
            currentUserId = (securityFacade != null) ? securityFacade.getCurrentUserId() : null;
        } catch (Exception ignored) {}
        boolean isDev = (securityFacade == null) || securityFacade.hasRole(Role.ROLE_DEV);
        String currentUsername = (securityFacade != null) ? securityFacade.getCurrentUsername() : null;

        final Long finalUserId = currentUserId;
        List<Order> activeOrders = allOrders.stream()
            .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELED)
            .filter(o -> isDev || (finalUserId != null && o.getSupervisor() != null && Objects.equals(o.getSupervisor().getId(), finalUserId))
                          || (currentUsername != null && o.getCreatedBy() != null && o.getCreatedBy().equalsIgnoreCase(currentUsername))
                          || (o.getSupervisor() == null && (o.getCreatedBy() == null || "system".equalsIgnoreCase(o.getCreatedBy()))))
            .toList();

        if (activeOrders.isEmpty()) {
            return "There are currently no active orders.";
        }

        StringBuilder sb = new StringBuilder("### Active Orders\n");
        sb.append("| Order ID (Logic) | Destination | Status | Lines/Items | Assigned Operator |\n");
        sb.append("|------------------|-------------|--------|-------------|-------------------|\n");

        for (Order o : activeOrders) {
            String dest = o.getDestinationLocation() != null ? o.getDestinationLocation().getBarcode() : "N/A";
            int linesCount = o.getOrderLines() != null ? o.getOrderLines().size() : 0;
            String operatorName = orderRepository.findOperatorUsernameByOrder(o).orElse("Unassigned");

            sb.append(String.format("| %s | %s | %s | %d | %s |\n",
                o.getLogicId(), dest, o.getStatus().name(), linesCount, operatorName));
        }

        return sb.toString();
    }

    @Tool(description = "Returns a list of all orders that are currently blocked due to inventory shortages.")
    public String getShortageOrdersInfo() {
        log.info("AI invoked getShortageOrdersInfo tool");
        List<ShortageOrderResponse> shortages = orderService.getShortageOrders();

        if (shortages.isEmpty()) {
            return "Great news! There are no orders currently blocked by shortages.";
        }

        StringBuilder sb = new StringBuilder("### Orders with Shortages\n");
        sb.append("| Order ID (Logic) | Destination | Shortage Lines / Total Lines | Status |\n");
        sb.append("|------------------|-------------|-----------------------------|--------|\n");

        for (var s : shortages) {
            sb.append(String.format("| %s | %s | %d / %d | %s |\n",
                s.orderNumber(), s.destination(), s.shortageLines(), s.totalLines(), s.status()));
        }
        return sb.toString();
    }

    /**
     * Backward-compatible forwarding method to {@link OrderMutatingAiTools}.
     * Note: mutating tools are registered with Spring AI from {@link OrderMutatingAiTools}.
     */
    public String createOrder(String logicId, String destinationLocationBarcode, List<AiOrderItem> items) {
        return orderMutatingAiTools.createOrder(logicId, destinationLocationBarcode, items);
    }

    /**
     * Backward-compatible forwarding method to {@link OrderMutatingAiTools}.
     */
    public String assignOrderToOperator(String logicId, String operatorUsername) {
        return orderMutatingAiTools.assignOrderToOperator(logicId, operatorUsername);
    }

    /**
     * Backward-compatible forwarding method to {@link OrderMutatingAiTools}.
     */
    public String deleteOrder(String logicId, String confirmationToken) {
        return orderMutatingAiTools.deleteOrder(logicId, confirmationToken);
    }

    public String deleteOrder(String logicId) {
        return orderMutatingAiTools.deleteOrder(logicId);
    }
}
