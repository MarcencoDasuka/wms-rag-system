package com.isd.wms.service.ai;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.User;
import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Dedicated service for mutating inventory-related AI operations.
 * Enforces server-side authorization, audit logging, and two-phase confirmation
 * boundaries for stock write-offs and adjustments.
 */
@Slf4j
@Service("inventoryMutatingAiTools")
@RequiredArgsConstructor
public class InventoryMutatingAiTools {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final SecurityFacade securityFacade;
    private final AiToolSecurityBoundary securityBoundary;

    @Tool(description = "Receives new inbound stock from external suppliers directly into a specific warehouse location. Requires SUPERVISOR or DEV role.")
    public String receiveInboundStock(
        @ToolParam(description = "Barcode of the product being received") String productBarcode,
        @ToolParam(description = "Quantity of the product being received") Integer quantity,
        @ToolParam(description = "Barcode of the destination location (usually a REPL zone)") String locationBarcode) {

        securityBoundary.enforceSupervisorOrDev("receiveInboundStock");
        log.info("AI invoked receiveInboundStock for product {}, qty: {}, loc: {}", productBarcode, quantity, locationBarcode);

        Product product = findProductOrNull(productBarcode);
        if (product == null) return "Error: Product with barcode " + productBarcode + " not found.";

        Location loc = findLocationOrNull(locationBarcode);
        if (loc == null) return "Error: Location with barcode " + locationBarcode + " not found.";
        if (loc.getZone() != null && loc.getZone() == Zone.DISPATCH) {
            return "Error: Cannot receive inbound stock into a DISPATCH location.";
        }

        try {
            User currentUser = userRepository.findByUsername(securityFacade.getCurrentUsername()).orElseThrow();
            AddStockRequest req = new AddStockRequest(product.getId(), loc.getId(), quantity, 0, null, null, currentUser.getId());
            inventoryService.addStock(req);
            securityBoundary.auditMutation("receiveInboundStock", productBarcode + "@" + locationBarcode, "Received qty " + quantity);
            return "Success! " + quantity + " units of " + product.getName() + " were successfully received into location " + locationBarcode + ".";
        } catch (Exception e) {
            return "Failed to receive stock: " + e.getMessage();
        }
    }

    @Tool(description = "Adjusts or writes off inventory stock when items are damaged, lost, stolen, or have an inventory mismatch. High-impact operation: requires confirmation token. If confirmationToken is omitted, a pending confirmation token will be generated. Pass the token to confirm adjustment. Requires SUPERVISOR or DEV role.")
    public String adjustInventoryStock(
        @ToolParam(description = "Barcode of the product") String productBarcode,
        @ToolParam(description = "Barcode of the location") String locationBarcode,
        @ToolParam(description = "The NEW absolute physical quantity that is actually on the shelf") Integer newQuantity,
        @ToolParam(description = "Reason for adjustment. MUST be exactly one of: DAMAGED, LOST, STOLEN, INVENTORY_MISMATCH") String reason,
        @ToolParam(description = "Optional comment explaining the adjustment", required = false) String comment,
        @ToolParam(description = "Confirmation token for the adjustment. Leave empty on first invocation to request confirmation.", required = false) String confirmationToken) {

        securityBoundary.enforceSupervisorOrDev("adjustInventoryStock");
        log.info("AI invoked adjustInventoryStock for product {}, loc: {}", productBarcode, locationBarcode);

        Product product = findProductOrNull(productBarcode);
        if (product == null) return "Error: Product not found.";

        Location loc = findLocationOrNull(locationBarcode);
        if (loc == null) return "Error: Location not found.";

        Stock stock = stockRepository.findAllByAvailableIsTrue().stream()
            .filter(s -> s.getProduct().isPresent() && s.getProduct().get().getId().equals(product.getId()) && s.getLocation().getId().equals(loc.getId()))
            .findFirst()
            .orElse(null);

        if (stock == null) return "Error: No existing stock record found for this product at this location.";

        String targetId = productBarcode + "@" + locationBarcode;
        String details = "Adjust quantity from " + stock.getQuantity() + " to " + newQuantity + " (reason: " + reason + ")";

        String confirmationResult = securityBoundary.requireConfirmation(
            "ADJUST_INVENTORY_STOCK",
            targetId,
            details,
            confirmationToken
        );

        if (confirmationResult != null) {
            return confirmationResult;
        }

        try {
            User currentUser = userRepository.findByUsername(securityFacade.getCurrentUsername()).orElseThrow();
            InventoryAdjustmentReason adjReason = InventoryAdjustmentReason.valueOf(reason.toUpperCase());

            InventoryAdjustmentRequest req = new InventoryAdjustmentRequest(
                newQuantity, currentUser.getId(), adjReason, comment, null, null);

            inventoryAdjustmentService.adjustStock(stock.getId(), req);
            securityBoundary.auditMutation("adjustInventoryStock", targetId, details);
            return String.format("Success! Stock adjusted to %d. Reason: %s.", newQuantity, adjReason.name());
        } catch (IllegalArgumentException e) {
            return "Error: Invalid reason. Allowed reasons are exactly: DAMAGED, LOST, STOLEN, INVENTORY_MISMATCH.";
        } catch (Exception e) {
            return "Failed to adjust stock: " + e.getMessage();
        }
    }

    public String adjustInventoryStock(String productBarcode, String locationBarcode, Integer newQuantity, String reason, String comment) {
        return adjustInventoryStock(productBarcode, locationBarcode, newQuantity, reason, comment, null);
    }

    private Product findProductOrNull(String barcode) {
        return productRepository.findByBarcode(barcode).orElse(null);
    }

    private Location findLocationOrNull(String barcode) {
        return locationRepository.findAll().stream().filter(l -> l.getBarcode().equalsIgnoreCase(barcode)).findFirst().orElse(null);
    }
}
