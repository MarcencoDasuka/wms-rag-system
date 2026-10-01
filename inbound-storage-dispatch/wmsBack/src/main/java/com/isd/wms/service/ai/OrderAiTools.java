package com.isd.wms.service.ai;

import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI tools for read-only order queries and shortage analysis.
 * Mutating operations have been separated into {@link OrderMutatingAiTools}
 * to establish strict authorization and confirmation boundaries.
 */
@Slf4j
@Service("orderAiTools")
@RequiredArgsConstructor
public class OrderAiTools {

    public record AiOrderItem(String productBarcode, Integer quantity) {
    }

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderMutatingAiTools orderMutatingAiTools;

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
