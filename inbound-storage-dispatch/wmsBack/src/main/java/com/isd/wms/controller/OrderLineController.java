package com.isd.wms.controller;


import com.isd.wms.dto.order_line.OrderLineResponse;
import com.isd.wms.dto.order_line.OrderLineUpdateRequest;
import com.isd.wms.service.OrderLineService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing individual order lines.
 *
 * <p>Provides endpoints for retrieving, updating, and deleting order lines.
 * All endpoints require the {@code SUPERVISOR} or {@code DEV} role.</p>
 *
 * <p>Base path: {@code /api/v1/order-lines}</p>
 */
@RestController
@RequestMapping("/api/v1/order-lines")
@RequiredArgsConstructor
public class OrderLineController {
    private final OrderLineService orderLineService;

    /**
     * Retrieves all order lines in the system.
     *
     * @return {@code 200 OK} with a list of all {@link OrderLineResponse} objects
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<OrderLineResponse>> getAllOrderLines() {
        return ResponseEntity.ok(orderLineService.getAll());
    }

    /**
     * Retrieves a single order line by its ID.
     *
     * @param id the ID of the order line to retrieve
     * @return {@code 200 OK} with the {@link OrderLineResponse} for the specified order line
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<OrderLineResponse> getOrderLineById(@PathVariable Long id) {
        return ResponseEntity.ok(orderLineService.getOrderLineById(id));
    }

    /**
     * Updates an existing order line.
     *
     * @param id      the ID of the order line to update
     * @param request the update request containing the new order line data; must be valid and non-null
     * @return {@code 200 OK} with the updated {@link OrderLineResponse}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<OrderLineResponse> updateOrderLine(@PathVariable Long id, @Valid @NonNull OrderLineUpdateRequest request) {
        return ResponseEntity.ok(orderLineService.updateOrderLine(id, request));
    }

    /**
     * Deletes an order line by its ID.
     *
     * @param id the ID of the order line to delete
     * @return {@code 204 No Content} on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> deleteOrderLine(@PathVariable Long id) {
        orderLineService.deleteOrderLine(id);
        return ResponseEntity.noContent().build();
    }
}
