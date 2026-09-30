package com.isd.wms.service.ai;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.User;
import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service("inventoryAiTools")
@RequiredArgsConstructor
public class InventoryAiTools {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final SecurityFacade securityFacade;
    private final VectorStore vectorStore;

    @Tool(description = "Searches for products by their name, description, or semantic meaning. Use this when the user asks about a product without a barcode, or describes a product conceptually.")
    public String searchProductByName(@ToolParam(description = "The name, part of the name, or conceptual description of the product") String nameQuery) {
        log.info("AI invoked searchProductByName (Vector Search) for query: {}", nameQuery);

        List<Document> similarDocuments = vectorStore.similaritySearch(
            org.springframework.ai.vectorstore.SearchRequest.builder().query(nameQuery).topK(3).build()
        );

        if (similarDocuments.isEmpty()) {
            return "No products found semantically matching: '" + nameQuery + "'.";
        }

        StringBuilder response = new StringBuilder("Found the following closest product matches:\n");
        for (Document doc : similarDocuments) {
            String name = (String) doc.getMetadata().get("name");
            String barcode = (String) doc.getMetadata().get("barcode");
            response.append(String.format("- Name: %s | Barcode: %s\n", name, barcode));
        }

        response.append("\nHint for AI: Use the exact barcode from this list with the checkStockByBarcode tool to verify inventory before taking actions.");
        return response.toString();
    }

    @Tool(description = "Use this to check ALL detailed information about a product and its stock in the warehouse by barcode.")
    public String checkStockByBarcode(@ToolParam(description = "The exact barcode of the product") String productBarcode) {
        log.info("AI invoked checkStockByBarcode tool for barcode: {}", productBarcode);
        Product product = findProductOrNull(productBarcode);
        if (product == null) return "Product with barcode " + productBarcode + " not found in the database.";

        List<Stock> stocks = stockRepository.findAllByAvailableIsTrue().stream()
            .filter(s -> s.getProduct().isPresent() && s.getProduct().get().getId().equals(product.getId()))
            .toList();

        return formatStockDetails(product, stocks);
    }

    @Tool(description = "Checks the inventory/stock currently sitting in a specific physical location (shelf) by its location barcode.")
    public String checkLocationItems(@ToolParam(description = "The exact barcode of the location (e.g., REPL-A-01)") String locationBarcode) {
        log.info("AI invoked checkLocationItems tool for location: {}", locationBarcode);

        Location loc = findLocationOrNull(locationBarcode);
        if (loc == null) return "Error: Location with barcode " + locationBarcode + " not found.";

        List<Stock> stocksInLocation = stockRepository.findAllByAvailableIsTrue().stream()
            .filter(s -> s.getLocation().getId().equals(loc.getId()))
            .toList();

        if (stocksInLocation.isEmpty()) {
            return "Location " + locationBarcode + " is currently completely empty.";
        }

        StringBuilder response = new StringBuilder("Items currently at location " + locationBarcode + ":\n");
        for (Stock s : stocksInLocation) {
            String pName = s.getProduct().map(Product::getName).orElse("Unknown Product");
            String pBarcode = s.getProduct().map(Product::getBarcode).orElse("N/A");
            response.append(String.format("- Product: %s (%s) | Total Physical: %d | Reserved: %d | Available: %d\n",
                pName, pBarcode, s.getQuantity(), s.getReservedQuantity(), (s.getQuantity() - s.getReservedQuantity())));
        }
        return response.toString();
    }

    @Tool(description = "Checks the database for products that have low stock across the entire warehouse.")
    public String getLowStockWarning() {
        log.info("AI invoked getLowStockWarning tool");
        return formatLowStockWarnings(productRepository.findAll());
    }

    @Tool(description = "Receives new inbound stock from external suppliers directly into a specific warehouse location.")
    public String receiveInboundStock(
        @ToolParam(description = "Barcode of the product being received") String productBarcode,
        @ToolParam(description = "Quantity of the product being received") Integer quantity,
        @ToolParam(description = "Barcode of the destination location (usually a REPL zone)") String locationBarcode) {

        log.info("AI invoked receiveInboundStock for product {}, qty: {}, loc: {}", productBarcode, quantity, locationBarcode);

        Product product = findProductOrNull(productBarcode);
        if (product == null) return "Error: Product with barcode " + productBarcode + " not found.";

        Location loc = findLocationOrNull(locationBarcode);
        if (loc == null) return "Error: Location with barcode " + locationBarcode + " not found.";

        try {
            User currentUser = userRepository.findByUsername(securityFacade.getCurrentUsername()).orElseThrow();
            AddStockRequest req = new AddStockRequest(product.getId(), loc.getId(), quantity, 0, null, null, currentUser.getId());
            inventoryService.addStock(req);
            return "Success! " + quantity + " units of " + product.getName() + " were successfully received into location " + locationBarcode + ".";
        } catch (Exception e) {
            return "Failed to receive stock: " + e.getMessage();
        }
    }

    @Tool(description = "Adjusts or writes off inventory stock when items are damaged, lost, stolen, or have an inventory mismatch.")
    public String adjustInventoryStock(
        @ToolParam(description = "Barcode of the product") String productBarcode,
        @ToolParam(description = "Barcode of the location") String locationBarcode,
        @ToolParam(description = "The NEW absolute physical quantity that is actually on the shelf") Integer newQuantity,
        @ToolParam(description = "Reason for adjustment. MUST be exactly one of: DAMAGED, LOST, STOLEN, INVENTORY_MISMATCH") String reason,
        @ToolParam(description = "Optional comment explaining the adjustment") String comment) {

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

        try {
            User currentUser = userRepository.findByUsername(securityFacade.getCurrentUsername()).orElseThrow();
            InventoryAdjustmentReason adjReason = InventoryAdjustmentReason.valueOf(reason.toUpperCase());

            InventoryAdjustmentRequest req = new InventoryAdjustmentRequest(
                newQuantity, currentUser.getId(), adjReason, comment, null, null);

            inventoryAdjustmentService.adjustStock(stock.getId(), req);
            return String.format("Success! Stock adjusted to %d. Reason: %s.", newQuantity, adjReason.name());
        } catch (IllegalArgumentException e) {
            return "Error: Invalid reason. Allowed reasons are exactly: DAMAGED, LOST, STOLEN, INVENTORY_MISMATCH.";
        } catch (Exception e) {
            return "Failed to adjust stock: " + e.getMessage();
        }
    }

    private Product findProductOrNull(String barcode) {
        return productRepository.findByBarcode(barcode).orElse(null);
    }

    private Location findLocationOrNull(String barcode) {
        return locationRepository.findAll().stream().filter(l -> l.getBarcode().equalsIgnoreCase(barcode)).findFirst().orElse(null);
    }

    private String formatStockDetails(Product product, List<Stock> stocks) {
        StringBuilder sb = new StringBuilder();
        String encodedName = URLEncoder.encode(product.getName(), StandardCharsets.UTF_8).replace("+", "%20");

        String productUrl = String.format("/supervisor/products?productId=%d&barcode=%s&product=%s",
            product.getId(), product.getBarcode(), encodedName);

        sb.append(String.format("### [%s](%s)\n", product.getName(), productUrl));

        sb.append("- **Barcode:** ").append(product.getBarcode()).append("\n");
        sb.append("- **Category:** ").append(product.getCategory() != null ? product.getCategory().getName() : "None").append("\n");
        sb.append("- **Auto-Replenish:** ").append(Boolean.TRUE.equals(product.getAutoReplenish()) ? "Enabled" : "Disabled").append("\n");

        if (Boolean.TRUE.equals(product.getAutoReplenish())) {
            sb.append("- **Min Threshold:** ").append(product.getMinThreshold()).append("\n");
            sb.append("- **Replenish Qty:** ").append(product.getReplenishQty()).append("\n");
        }

        if (stocks.isEmpty()) {
            return sb.append("\n**Stock Status:** Product is currently fully out of stock (0 pcs in warehouse).").toString();
        }

        sb.append("\n#### Detailed Stock Locations\n");
        sb.append("| Location | Total Physical | Reserved (in tasks) | Available |\n");
        sb.append("|----------|----------------|---------------------|-----------|\n");

        int totalQty = 0, totalReserved = 0, totalAvailable = 0;
        for (Stock stock : stocks) {
            int qty = stock.getQuantity(), res = stock.getReservedQuantity(), avail = qty - res;
            sb.append(String.format("| %s | %d | %d | %d |\n", stock.getLocation().getBarcode(), qty, res, avail));
            totalQty += qty;
            totalReserved += res;
            totalAvailable += avail;
        }

        sb.append("\n#### Warehouse Summary\n");
        sb.append(String.format("- **Total Physical:** %d\n", totalQty));
        sb.append(String.format("- **Total Reserved:** %d\n", totalReserved));
        sb.append(String.format("- **Total Available:** %d\n", totalAvailable));
        return sb.toString();
    }

    private String formatLowStockWarnings(List<Product> products) {
        StringBuilder sb = new StringBuilder("### Low Stock Warnings\n");
        boolean found = false;

        sb.append("| Product | Barcode | Available | Threshold |\n");
        sb.append("|---------|---------|-----------|-----------|\n");

        for (Product p : products) {
            if (p.getMinThreshold().isPresent()) {
                int totalAvailable = stockRepository.findAllByAvailableIsTrue().stream()
                    .filter(s -> s.getProduct().isPresent() && s.getProduct().get().getId().equals(p.getId()))
                    .mapToInt(s -> s.getQuantity() - s.getReservedQuantity())
                    .sum();

                if (totalAvailable <= p.getMinThreshold().orElseThrow()) {
                    sb.append(String.format("| %s | %s | %d | %d |\n", p.getName(), p.getBarcode(),
                        totalAvailable, p.getMinThreshold().orElse(0)));
                    found = true;
                }
            }
        }
        return found ? sb.toString() : "All products are currently above their minimum thresholds.";
    }
}
