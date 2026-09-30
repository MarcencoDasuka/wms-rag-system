package com.isd.wms.controller;

import com.isd.wms.dto.operator.OperatorTaskSummaryResponse;
import com.isd.wms.dto.allocation.*;
import com.isd.wms.dto.transport_unit.ScanTuRequest;
import com.isd.wms.dto.transport_unit.TaskActionResponse;
import com.isd.wms.repository.projections.AllocationSupervisorProjection;
import com.isd.wms.service.AllocationExecutionService;
import com.isd.wms.service.AllocationService;
import com.isd.wms.service.TransportUnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing warehouse allocations.
 *
 * <p>Provides endpoints for both supervisors and operators to view, start, execute,
 * and complete allocation tasks. Operator-facing endpoints cover the full task
 * lifecycle including location scanning, product scanning, quantity confirmation,
 * transport unit (TU) handling, and dispatch.</p>
 *
 * <p>Base path: {@code /api/v1/allocations}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/allocations")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;
    private final AllocationExecutionService allocationExecutionService;
    private final TransportUnitService tuService;

    /**
     * Retrieves all allocations in the system, projected for supervisor view.
     *
     * @return {@code 200 OK} with a list of {@link AllocationSupervisorProjection} representing all allocations
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<List<AllocationSupervisorProjection>> getAllAllocations() {
        return ResponseEntity.ok(allocationService.getAllAllocations());
    }

    /**
     * Retrieves allocations visible to the currently authenticated operator.
     *
     * @return {@code 200 OK} with an {@link AllocationOperatorResponse} containing the operator's allocations
     */
    @GetMapping("/operators")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<AllocationOperatorResponse> getAllocationsOperator() {
        return ResponseEntity.ok(allocationService.getAllocationsOperator());
    }

    /**
     * Retrieves a summary of the current active task for the authenticated operator.
     *
     * @return {@code 200 OK} with an {@link OperatorTaskSummaryResponse} if a task is active,
     *         or {@code 204 No Content} if no current task exists
     */
    @GetMapping("/operator/current/summary")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<OperatorTaskSummaryResponse> getCurrentSummary() {
        return allocationExecutionService.getCurrentSummary()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Starts the current task assigned to the authenticated operator.
     *
     * @return {@code 200 OK} with an {@link OperatorTaskSummaryResponse} for the started task
     */
    @PostMapping("/operator/current/start")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<OperatorTaskSummaryResponse> startCurrentTask() {
        log.info("Operator requested start for current task");
        return ResponseEntity.ok(allocationExecutionService.startCurrentTask());
    }

    /**
     * Processes a transport unit (TU) scan for the specified allocation.
     *
     * <p>Marks the scanned TU as occupied and returns an updated task summary.</p>
     *
     * @param id      the ID of the allocation being processed
     * @param request the scan request containing the TU barcode and whether it is an order TU
     * @return {@code 200 OK} with the updated {@link OperatorTaskSummaryResponse}
     * @throws IllegalStateException if no current task summary can be generated after the scan
     */
    @PostMapping("/{id}/scan-tu")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<OperatorTaskSummaryResponse> scanTransportUnit(
        @PathVariable Long id,
        @Valid @RequestBody ScanTuRequest request) {

        log.info("Received TU scan for Allocation ID: {}. Barcode: {}", id, request.barcode());

        tuService.occupyTransportUnit(request.barcode(), id, request.isOrder());

        OperatorTaskSummaryResponse updatedSummary = allocationExecutionService.getCurrentSummary()
            .orElseThrow(() -> new IllegalStateException("Could not generate summary after scanning TU"));

        return ResponseEntity.ok(updatedSummary);
    }

    /**
     * Dispatches the specified allocation and releases the associated transport unit.
     * For replenishments, this physically moves the gathered stock to the destination location.
     *
     * @param id             the ID of the allocation to dispatch
     * @param currentBarcode the barcode of the transport unit currently linked to the allocation
     * @return {@code 200 OK} with a {@link TaskActionResponse} indicating successful completion
     */
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<TaskActionResponse> dispatchAllocation(
        @PathVariable Long id,
        @RequestParam String currentBarcode) {

        allocationExecutionService.dispatchAllocation(id, currentBarcode);

        TaskActionResponse response = new TaskActionResponse(
            "COMPLETED",
            false,
            null,
            "Drop TU at destination",
            "Task completed successfully. Transport Unit has been released."
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Marks the current order as complete for the authenticated operator.
     *
     * @return {@code 204 No Content} on success
     */
    @PostMapping("/operator/current/complete")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<Void> completeCurrentOrder() {
        allocationExecutionService.completeCurrentOrder();
        return ResponseEntity.noContent().build();
    }

    /**
     * Initiates a new allocation and returns its generated ID.
     *
     * @return {@code 200 OK} with a message containing the new allocation's ID
     */
    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<String> startAllocation() {
        return ResponseEntity.ok("Allocation started with id: " + allocationExecutionService.startAllocation());
    }

    /**
     * Processes a source location barcode scan for the specified allocation.
     *
     * @param id      the ID of the allocation
     * @param request the barcode scan request containing the scanned location code
     * @return {@code 200 OK} with an {@link AllocationExecutionResponse} reflecting the updated state
     */
    @PostMapping("/{id}/location")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<AllocationExecutionResponse> scanSourceLocation(@PathVariable Long id, @Valid @RequestBody BarcodeScanRequest request) {
        return ResponseEntity.ok(allocationExecutionService.scanSourceLocation(id, request));
    }

    /**
     * Processes a product barcode scan for the specified allocation.
     *
     * @param id      the ID of the allocation
     * @param request the barcode scan request containing the scanned product code
     * @return {@code 200 OK} with an {@link AllocationExecutionResponse} reflecting the updated state
     */
    @PostMapping("/{id}/product")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<AllocationExecutionResponse> scanProduct(@PathVariable Long id, @Valid @RequestBody BarcodeScanRequest request) {
        return ResponseEntity.ok(allocationExecutionService.scanProduct(id, request));
    }

    /**
     * Confirms the quantity of items picked for the specified allocation.
     *
     * @param id      the ID of the allocation
     * @param request the request containing the confirmed picked quantity
     * @return {@code 200 OK} with an {@link AllocationExecutionResponse} reflecting the updated state
     */
    @PostMapping("/{id}/confirm-quantity")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<AllocationExecutionResponse> confirmPickedQuantity(@PathVariable Long id, @Valid @RequestBody ConfirmPickedQuantityRequest request) {
        return ResponseEntity.ok(allocationExecutionService.confirmPickedQuantity(id, request));
    }

    /**
     * Marks the specified allocation as fully completed.
     *
     * @param id the ID of the allocation to complete
     * @return {@code 200 OK} with an {@link AllocationCompletionResponse} summarizing the completion result
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OPERATOR', 'DEV')")
    public ResponseEntity<AllocationCompletionResponse> completeAssignedAllocation(@PathVariable Long id) {
        return ResponseEntity.ok(allocationExecutionService.completeAssignedAllocation(id));
    }
}
