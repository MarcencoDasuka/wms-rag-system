package com.isd.wms.controller;

import com.isd.wms.dto.inventory.*;
import com.isd.wms.service.InventoryAdjustmentService;
import com.isd.wms.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for managing warehouse inventory (stock).
 *
 * <p>Exposes endpoints for viewing stock levels, adding, removing, and adjusting
 * stock quantities, previewing adjustments before applying them, viewing adjustment
 * history, and bulk-importing stock from a file. All endpoints require the
 * {@code SUPERVISOR} or {@code DEV} role.</p>
 *
 * <p>Base path: {@code /api/inventory}</p>
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryAdjustmentService inventoryAdjustmentService;

    /**
     * Retrieves all stock entries in the warehouse.
     *
     * @return {@code 200 OK} with a list of {@link StockResponse} objects representing all current stock
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<StockResponse>> getAllStock() {
        return ResponseEntity.ok(inventoryService.getAllStock());
    }

    /**
     * Retrieves a single stock entry by its ID.
     *
     * @param stockId the ID of the stock entry to retrieve
     * @return {@code 200 OK} with the {@link StockResponse} for the specified stock entry
     */
    @GetMapping("/{stockId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<StockResponse> getStockById(@PathVariable Long stockId) {
        return ResponseEntity.ok(inventoryService.getStockById(stockId));
    }

    /**
     * Adds new stock to the warehouse inventory.
     *
     * @param request the request containing product, location, and quantity details; must be valid
     * @return {@code 201 Created} with the resulting {@link StockResponse}
     */
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<StockResponse> addStock(@Valid @RequestBody AddStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.addStock(request));
    }

    /**
     * Removes stock from the warehouse inventory.
     *
     * @param request the request specifying which stock and how much to remove; must be valid
     * @return {@code 200 OK} with the updated {@link StockResponse} after removal
     */
    @PostMapping("/remove")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<StockResponse> removeStock(@Valid @RequestBody RemoveStockRequest request) {
        return ResponseEntity.ok(inventoryService.removeStock(request));
    }

    @DeleteMapping("/{stockId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> deleteStock(@PathVariable Long stockId) {
        inventoryService.deleteStock(stockId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Applies a structured adjustment to a specific stock entry.
     *
     * @param stockId the ID of the stock entry to adjust
     * @param request the adjustment request; must be valid
     * @return {@code 200 OK} with the {@link InventoryAdjustmentResponse} reflecting the applied change
     */
    @PatchMapping("/{stockId}/adjust")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<InventoryAdjustmentResponse> adjustStock(
        @PathVariable Long stockId,
        @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return ResponseEntity.ok(inventoryAdjustmentService.adjustStock(stockId, request));
    }

    /**
     * Previews the result of an adjustment on a specific stock entry without persisting changes.
     *
     * @param stockId the ID of the stock entry to preview the adjustment for
     * @param request the adjustment request to preview; must be valid
     * @return {@code 200 OK} with an {@link InventoryAdjustmentResponse} showing the projected outcome
     */
    @PostMapping("/{stockId}/adjust/preview")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<InventoryAdjustmentResponse> previewAdjustment(
        @PathVariable Long stockId,
        @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return ResponseEntity.ok(inventoryAdjustmentService.previewAdjustment(stockId, request));
    }

    /**
     * Retrieves the full adjustment history for all stock entries.
     *
     * @return {@code 200 OK} with a list of {@link InventoryHistoryResponse} objects covering all historical adjustments
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<InventoryHistoryResponse>> getAllHistory() {
        return ResponseEntity.ok(inventoryService.getAllHistory());
    }

    /**
     * Retrieves the adjustment history for a specific stock entry.
     *
     * @param stockId the ID of the stock entry whose history to retrieve
     * @return {@code 200 OK} with a list of {@link InventoryHistoryResponse} objects for the specified stock
     */
    @GetMapping("/{stockId}/history")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<InventoryHistoryResponse>> getStockHistory(@PathVariable Long stockId) {
        return ResponseEntity.ok(inventoryService.getHistoryForStock(stockId));
    }

    /**
     * Imports stock entries in bulk from an uploaded file.
     *
     * @param file the multipart file containing stock data to import
     * @return {@code 200 OK} with a confirmation message on success
     */
    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> importStockFromFile(@RequestParam("file") MultipartFile file) {
        inventoryService.importStocksFromFile(file);
        return ResponseEntity.ok("Stocks were successfully imported.");
    }
}
