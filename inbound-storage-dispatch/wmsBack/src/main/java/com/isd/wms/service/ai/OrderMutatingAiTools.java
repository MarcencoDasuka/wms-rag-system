package com.isd.wms.service.ai;

import com.isd.wms.dto.order.ExtendedOrderCreateRequest;
import com.isd.wms.dto.order.OrderCreateRequest;
import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated service for mutating order-related AI operations.
 * Enforces server-side authorization, audit logging, and two-phase confirmation
 * boundaries for destructive operations.
 */
@Slf4j
@Service("orderMutatingAiTools")
@RequiredArgsConstructor
public class OrderMutatingAiTools {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AiToolSecurityBoundary securityBoundary;

    @Tool(description = "Creates a new Customer Order WITH order lines (products). Requires a destination DISPATCH location, and a list of items to pick. Requires SUPERVISOR or DEV role.")
    public String createOrder(
        @ToolParam(description = "Optional. Logical order ID. If the user doesn't provide one, leave this empty.") String logicId,
        @ToolParam(description = "Barcode of the destination location (Dispatch zone)") String destinationLocationBarcode,
        @ToolParam(description = "List of products and quantities to include in the order") List<OrderAiTools.AiOrderItem> items) {

        securityBoundary.enforceSupervisorOrDev("createOrder");
        log.info("AI invoked createOrder");

        Location dest = findLocationOrNull(destinationLocationBarcode);
        if (dest == null) return "Error: Destination location barcode not found.";
        if (dest.getZone() != null && dest.getZone() != Zone.DISPATCH) {
            return "Error: Destination location must be in a DISPATCH zone.";
        }

        String finalLogicId = (logicId == null || logicId.trim().isEmpty())
            ? "ORD-AI-" + (System.currentTimeMillis() % 100000)
            : logicId.trim();

        List<OrderLineCreateRequest> lineRequests = new ArrayList<>();
        for (OrderAiTools.AiOrderItem item : items) {
            Product p = findProductOrNull(item.productBarcode());
            if (p == null) return "Error: Product with barcode " + item.productBarcode() + " not found.";
            lineRequests.add(new OrderLineCreateRequest(null, p.getId(), item.quantity()));
        }

        try {
            orderService.addExtendedOrder(new ExtendedOrderCreateRequest(new OrderCreateRequest(finalLogicId, dest.getId()), lineRequests));
            securityBoundary.auditMutation("createOrder", finalLogicId, "Created order with " + lineRequests.size() + " lines");
            return "Success! Order '" + finalLogicId + "' created and picking tasks generated. Tell the user the generated Order ID.";
        } catch (Exception e) {
            return "Failed to create order: " + e.getMessage();
        }
    }

    @Tool(description = "Assigns an existing Order to a specific Operator. Requires SUPERVISOR or DEV role.")
    public String assignOrderToOperator(
        @ToolParam(description = "Logical order ID (e.g. 'ORD-123')") String logicId,
        @ToolParam(description = "Exact username of the operator") String operatorUsername) {

        securityBoundary.enforceSupervisorOrDev("assignOrderToOperator");
        log.info("AI invoked assignOrderToOperator");

        Order order = orderRepository.findByLogicIdIgnoreCase(logicId).orElse(null);
        if (order == null) return "Error: Order with logical ID " + logicId + " not found.";

        securityBoundary.enforceOrderAccess(order);

        User operator = findOperatorOrNull(operatorUsername);
        securityBoundary.enforceTargetOperator(operator);

        try {
            orderService.assignOrder(order.getId(), operator.getId());
            securityBoundary.auditMutation("assignOrderToOperator", logicId, "Assigned to " + operatorUsername);
            return "Success! Order " + logicId + " has been assigned to operator " + operatorUsername;
        } catch (Exception e) {
            return "Failed to assign order: " + e.getMessage();
        }
    }

    @Tool(description = "Requests deletion of an existing order by its logical ID. High-impact destructive operation: initiates a pending deletion request requiring out-of-band human supervisor approval via the management interface. Cannot be executed autonomously by AI. Requires SUPERVISOR or DEV role.")
    public String deleteOrder(
        @ToolParam(description = "Logical order ID to delete (e.g. 'ORD-123')") String logicId) {

        securityBoundary.enforceSupervisorOrDev("deleteOrder");
        log.info("AI invoked deleteOrder for logicId {}", logicId);

        Order order = orderRepository.findByLogicIdIgnoreCase(logicId).orElse(null);
        if (order == null) return "Error: Order with logical ID " + logicId + " not found.";

        securityBoundary.enforceOrderAccess(order);

        return securityBoundary.initiatePendingOperation(
            "DELETE_ORDER",
            logicId,
            order.getId(),
            "Delete order " + logicId
        );
    }

    public String deleteOrder(String logicId, String confirmationToken) {
        if (confirmationToken != null && !confirmationToken.trim().isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Autonomous AI tool confirmation is disabled. Destructive operations require human approval via trusted management endpoint.");
        }
        return deleteOrder(logicId);
    }

    private Product findProductOrNull(String barcode) {
        return productRepository.findByBarcode(barcode).orElse(null);
    }

    private Location findLocationOrNull(String barcode) {
        return locationRepository.findAll().stream().filter(l -> l.getBarcode().equalsIgnoreCase(barcode)).findFirst().orElse(null);
    }

    private User findOperatorOrNull(String username) {
        return userRepository.findAll().stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst().orElse(null);
    }
}
