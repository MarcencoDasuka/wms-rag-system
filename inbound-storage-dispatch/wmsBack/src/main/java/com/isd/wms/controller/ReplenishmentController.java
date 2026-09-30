package com.isd.wms.controller;

import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.dto.replenishment.ReplenishmentResponse;
import com.isd.wms.dto.replenishment.ReplenishmentSearchRequest;
import com.isd.wms.dto.replenishment.ReplenishmentUpdateRequest;
import com.isd.wms.dto.replenishment.shortage.ShortageReplenishmentDetailsResponse;
import com.isd.wms.dto.replenishment.shortage.ShortageReplenishmentResponse;
import com.isd.wms.entity.Task;
import com.isd.wms.service.ReplenishmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for managing warehouse replenishment tasks.
 *
 * <p>Provides endpoints for creating, retrieving, updating, deleting, assigning,
 * cancelling, searching, and bulk-importing replenishment tasks. Write operations
 * require the {@code SUPERVISOR} or {@code DEV} role; search and read operations
 * are publicly accessible.</p>
 *
 * <p>Base path: {@code /api/replenishments}</p>
 */
@RestController
@RequestMapping("/api/replenishments")
@RequiredArgsConstructor
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;

    /**
     * Creates a new replenishment task.
     *
     * @param request the replenishment creation request; must be valid
     * @return {@code 201 Created} with the created {@link ReplenishmentResponse}
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ReplenishmentResponse> createReplenishment(@Valid @RequestBody ReplenishmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(replenishmentService.createReplenishment(request));
    }

    /**
     * Retrieves all replenishment tasks.
     *
     * @return {@code 200 OK} with a list of all {@link ReplenishmentResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<ReplenishmentResponse>> getAllReplenishments() {
        return ResponseEntity.ok(replenishmentService.getAllReplenishments());
    }

    /**
     * Retrieves a single replenishment task by its ID.
     *
     * @param id the ID of the replenishment task to retrieve
     * @return {@code 200 OK} with the {@link ReplenishmentResponse} for the specified task
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReplenishmentResponse> getReplenishmentById(@PathVariable Long id) {
        return ResponseEntity.ok(replenishmentService.getReplenishmentById(id));
    }

    /**
     * Updates an existing replenishment task.
     *
     * @param id      the ID of the replenishment task to update
     * @param request the update request containing the new task data; must be valid
     * @return {@code 200 OK} with the updated {@link ReplenishmentResponse}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ReplenishmentResponse> updateReplenishment(@PathVariable Long id, @Valid @RequestBody ReplenishmentUpdateRequest request) {
        return ResponseEntity.ok(replenishmentService.updateReplenishment(id, request));
    }

    /**
     * Deletes a replenishment task by its ID.
     *
     * @param id the ID of the replenishment task to delete
     * @return {@code 204 No Content} on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<Void> deleteReplenishment(@PathVariable Long id) {
        replenishmentService.deleteReplenishment(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Searches for replenishment tasks matching the given criteria (query-parameter binding).
     *
     * @param request the search criteria bound from query parameters via {@code @ModelAttribute}
     * @return {@code 200 OK} with a list of matching {@link ReplenishmentResponse} objects
     */
    @PostMapping("/filter")
    public ResponseEntity<List<ReplenishmentResponse>> searchReplenishments(@ModelAttribute ReplenishmentSearchRequest request) {
        return ResponseEntity.ok(replenishmentService.searchReplenishments(request));
    }

    /**
     * Searches for replenishment tasks matching the given criteria (request-body binding).
     *
     * @param request the search criteria supplied in the request body
     * @return {@code 200 OK} with a list of matching {@link ReplenishmentResponse} objects
     */
    @PostMapping("/search")
    public ResponseEntity<List<ReplenishmentResponse>> searchReplenishmentsFromBody(@RequestBody ReplenishmentSearchRequest request) {
        return ResponseEntity.ok(replenishmentService.searchReplenishments(request));
    }

    /**
     * Assigns a replenishment task to a specific operator.
     *
     * @return {@code 200 OK} with a confirmation message on success
     */
    @GetMapping("/shortages")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<ShortageReplenishmentResponse>> getShortageReplenishments() {
        return ResponseEntity.ok(replenishmentService.getShortageReplenishments());
    }

    @GetMapping("/{id}/shortage-details")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ShortageReplenishmentDetailsResponse> getShortageDetails(@PathVariable Long id) {
        return ResponseEntity.ok(replenishmentService.getShortageDetails(id));
    }

    @PostMapping("/{replenishmentId}/operators/{operatorId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> assignReplenishment(@PathVariable Long replenishmentId, @PathVariable Long operatorId) {
        replenishmentService.assignReplenishment(replenishmentId, operatorId);
        return ResponseEntity.ok("Replenishment assigned with success.");
    }

    /**
     * Cancels a replenishment task by its ID.
     *
     * @param id the ID of the replenishment task to cancel
     * @return {@code 200 OK} with the updated {@link ReplenishmentResponse} reflecting the cancelled state
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ReplenishmentResponse> cancelReplenishment(@PathVariable Long id) {
        return ResponseEntity.ok(replenishmentService.cancelReplenishment(id));
    }

    /**
     * Imports replenishment tasks in bulk from an uploaded file.
     *
     * @param file the multipart file containing replenishment data to import
     * @return {@code 200 OK} with a confirmation message on success
     */
    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> importProducts(@RequestParam("file") MultipartFile file) {
        replenishmentService.importReplenishmentsFromFile(file);
        return ResponseEntity.ok("Replenishments were successfully imported.");
    }
}
