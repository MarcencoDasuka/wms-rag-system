package com.isd.wms.controller;

import com.isd.wms.dto.order.*;
import com.isd.wms.dto.order.shortage.ShortageDetailsResponse;
import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import com.isd.wms.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for managing warehouse orders.
 *
 * <p>Provides endpoints for creating, reading, updating, and deleting orders in both
 * basic and extended forms. Also supports operator assignment, order filtering,
 * bulk import, and shortage reporting. All endpoints require the {@code SUPERVISOR}
 * or {@code DEV} role.</p>
 *
 * <p>Base path: {@code /api/v1/orders}</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    /**
     * Retrieves all orders in the system.
     *
     * @return {@code 200 OK} with a list of {@link OrderResponse} objects
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<OrderResponse>> getOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /**
     * Retrieves all orders with extended details including order lines.
     *
     * @return {@code 200 OK} with a list of {@link ExtendedOrderResponse} objects
     */
    @GetMapping("/extended")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<ExtendedOrderResponse>> getExtendedOrders() {
        return ResponseEntity.ok(orderService.getAllExtendedOrders());
    }

    /**
     * Retrieves a single order by its ID.
     *
     * @param id the ID of the order to retrieve
     * @return {@code 200 OK} with the {@link OrderResponse} for the specified order
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * Retrieves a single order with extended details by its ID.
     *
     * @param id the ID of the order to retrieve
     * @return {@code 200 OK} with the {@link ExtendedOrderResponse} for the specified order
     */
    @GetMapping("/extended/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ExtendedOrderResponse> getExtendedOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getExtendedOrderById(id));
    }

    /**
     * Creates a new order with its associated order lines.
     *
     * @param request the extended order creation request; must be valid
     * @return {@code 201 Created} with the created {@link OrderResponse}
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody ExtendedOrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addExtendedOrder(request));
    }

    /**
     * Updates the basic fields of an existing order.
     *
     * @param id      the ID of the order to update
     * @param request the update request containing the new order data; must be valid
     * @return {@code 200 OK} with the updated {@link OrderResponse}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable Long id, @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    /**
     * Updates an existing order together with its order lines.
     *
     * @param id      the ID of the order to update
     * @param request the extended update request containing the new order and line data; must be valid
     * @return {@code 200 OK} with the updated {@link ExtendedOrderResponse}
     */
    @PutMapping("/extended/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ExtendedOrderResponse> updateExtendedOrder(@PathVariable Long id, @Valid @RequestBody ExtendedOrderCreateRequest request) {
        return ResponseEntity.ok(orderService.updateExtendedOrder(id, request));
    }

    /**
     * Deletes an order by its ID.
     *
     * @param id the ID of the order to delete
     * @return {@code 204 No Content} on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrderById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Assigns an order to a specific operator.
     *
     * @param orderId    the ID of the order to assign
     * @param operatorId the ID of the operator to assign the order to
     * @return {@code 204 No Content} on successful assignment
     */
    @PostMapping("/{orderId}/operators/{operatorId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> assignOrder(@PathVariable Long orderId, @PathVariable Long operatorId) {
        orderService.assignOrder(orderId, operatorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Searches for orders matching the given filter criteria.
     *
     * @param request the search criteria bound from query parameters
     * @return {@code 200 OK} with a list of matching {@link OrderResponse} objects
     */
    @GetMapping("/filter")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<OrderResponse>> searchOrders(@ModelAttribute OrderSearchRequest request) {
        return ResponseEntity.ok(orderService.searchOrders(request));
    }

    /**
     * Imports orders in bulk from an uploaded file.
     *
     * @param file the multipart file containing order data to import
     * @return {@code 200 OK} with a confirmation message on success
     */
    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> importOrders(@RequestParam("file") MultipartFile file) {
        orderService.importOrdersFromFile(file);
        return ResponseEntity.ok("Orders were successfully imported.");
    }

    /**
     * Retrieves all orders that have unfulfilled stock shortages.
     *
     * @return {@code 200 OK} with a list of {@link ShortageOrderResponse} objects
     */
    @GetMapping("/shortages")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<ShortageOrderResponse>> getShortageOrders() {
        return ResponseEntity.ok(orderService.getShortageOrders());
    }

    /**
     * Retrieves the detailed shortage information for a specific order.
     *
     * @param id the ID of the order to retrieve shortage details for
     * @return {@code 200 OK} with the {@link ShortageDetailsResponse} for the specified order
     */
    @GetMapping("/{id}/shortage-details")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ShortageDetailsResponse> getShortageDetails(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getShortageDetails(id));
    }
}
