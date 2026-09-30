package com.isd.wms.service.ai;

import com.isd.wms.dto.order.ExtendedOrderCreateRequest;
import com.isd.wms.dto.order.OrderCreateRequest;
import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.dto.order_line.OrderLineCreateRequest;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
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

@Slf4j
@Service("orderAiTools")
@RequiredArgsConstructor
public class OrderAiTools {

    public record AiOrderItem(String productBarcode, Integer quantity) {
    }

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Tool(description = "Returns a summary of all active customer orders. Use this when the user asks about current orders, tasks in orders, or unassigned orders.")
    public String getActiveOrdersInfo() {
        log.info("AI invoked getActiveOrdersInfo tool");

        List<Order> activeOrders = orderRepository.findAll().stream()
            .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELED)
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

    @Tool(description = "Creates a new Customer Order WITH order lines (products). Requires a destination DISPATCH location, and a list of items to pick.")
    public String createOrder(
        @ToolParam(description = "Optional. Logical order ID. If the user doesn't provide one, leave this empty.") String logicId,
        @ToolParam(description = "Barcode of the destination location (Dispatch zone)") String destinationLocationBarcode,
        @ToolParam(description = "List of products and quantities to include in the order") List<AiOrderItem> items) {

        log.info("AI invoked createOrder");
        Location dest = findLocationOrNull(destinationLocationBarcode);
        if (dest == null) return "Error: Destination location barcode not found.";

        String finalLogicId = (logicId == null || logicId.trim().isEmpty())
            ? "ORD-AI-" + (System.currentTimeMillis() % 100000)
            : logicId.trim();

        List<OrderLineCreateRequest> lineRequests = new ArrayList<>();
        for (AiOrderItem item : items) {
            Product p = findProductOrNull(item.productBarcode());
            if (p == null) return "Error: Product with barcode " + item.productBarcode() + " not found.";
            lineRequests.add(new OrderLineCreateRequest(null, p.getId(), item.quantity()));
        }

        try {
            orderService.addExtendedOrder(new ExtendedOrderCreateRequest(new OrderCreateRequest(finalLogicId, dest.getId()), lineRequests));
            return "Success! Order '" + finalLogicId + "' created and picking tasks generated. Tell the user the generated Order ID.";
        } catch (Exception e) {
            return "Failed to create order: " + e.getMessage();
        }
    }

    @Tool(description = "Assigns an existing Order to a specific Operator.")
    public String assignOrderToOperator(
        @ToolParam(description = "Logical order ID (e.g. 'ORD-123')") String logicId,
        @ToolParam(description = "Exact username of the operator") String operatorUsername) {

        log.info("AI invoked assignOrderToOperator");
        Order order = orderRepository.findByLogicIdIgnoreCase(logicId).orElse(null);
        if (order == null) return "Error: Order with logical ID " + logicId + " not found.";

        User operator = findOperatorOrNull(operatorUsername);
        if (operator == null) return "Error: Operator '" + operatorUsername + "' not found.";

        try {
            orderService.assignOrder(order.getId(), operator.getId());
            return "Success! Order " + logicId + " has been assigned to operator " + operatorUsername;
        } catch (Exception e) {
            return "Failed to assign order: " + e.getMessage();
        }
    }

    @Tool(description = "Deletes an existing order by its logical ID.")
    public String deleteOrder(@ToolParam(description = "Logical order ID to delete (e.g. 'ORD-123')") String logicId) {
        log.info("AI invoked deleteOrder");
        Order order = orderRepository.findByLogicIdIgnoreCase(logicId).orElse(null);
        if (order == null) return "Error: Order with logical ID " + logicId + " not found.";

        try {
            orderService.deleteOrderById(order.getId());
            return "Success! Order " + logicId + " has been deleted.";
        } catch (Exception e) {
            return "Failed to delete order: " + e.getMessage();
        }
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
