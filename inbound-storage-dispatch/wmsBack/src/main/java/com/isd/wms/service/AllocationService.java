package com.isd.wms.service;

import com.isd.wms.dto.allocation.AllocationOperatorResponse;
import com.isd.wms.dto.allocation.ShortAllocationResponse;
import com.isd.wms.entity.*;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.exception.AllocationsNotFoundException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.repository.projections.AllocationSupervisorProjection;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for querying and summarising allocation data.
 *
 * <p>Provides read-oriented methods for both supervisors (full allocation list) and
 * operators (a contextual response covering the current task, progress counters,
 * destination, and the active allocation item). Write operations and step-by-step
 * execution live in {@link AllocationExecutionService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final SecurityFacade securityFacade;
    private final OrderLineRepository orderLineRepository;
    private final ReplenishmentRepository replenishmentRepository;

    /**
     * Returns all allocations in the system projected for the supervisor view.
     *
     * @return a list of {@link AllocationSupervisorProjection} objects
     */
    public List<AllocationSupervisorProjection> getAllAllocations() {
        return allocationRepository.getAllAllocations();
    }

    /**
     * Builds the operator-facing allocation response for the authenticated operator's
     * current active task.
     *
     * <p>Resolves the task type, determines the destination location barcode and logical
     * ID (for picking-order tasks), calculates total and current position counters, and
     * returns the first non-completed allocation item.</p>
     *
     * @return an {@link AllocationOperatorResponse} containing task metadata and the
     *         active allocation details
     * @throws AllocationsNotFoundException if no active allocation exists for the operator
     */
    public AllocationOperatorResponse getAllocationsOperator() {
        String username = securityFacade.getCurrentUsername();
        Allocation allocation = getAllocationsForOperator(username);
        Task task = allocation.getTask();
        TaskType taskType = task.getTaskType();

        Integer total = allocationRepository.findAllByTaskId(task.getId()).size();
        Integer current = Math.toIntExact(allocationRepository.findAllByTaskId(task.getId()).stream()
            .filter(taskAllocation -> taskAllocation.getStatus() == Status.COMPLETED)
            .count()) + 1;
        String logicalId = null;
        String destinationLocationBarcode = null;

        Result result = getResult(taskType, task, username, logicalId, destinationLocationBarcode, total, current);

        return new AllocationOperatorResponse(
            result.total(), result.current(), result.logicalId(), taskType.name(), result.destinationLocationBarcode(),
            new ShortAllocationResponse(
                allocation.getId(),
                allocation.getStock().getProduct().map(Product::getName).orElse(null),
                allocation.getStock().getProduct().map(Product::getBarcode).orElse(null),
                allocation.getStock().getLocation().getName(),
                allocation.getStock().getLocation().getBarcode(),
                allocation.getQuantity()
            )
        );
    }

    private @NonNull Result getResult(
        TaskType taskType,
        Task task,
        String username,
        String logicalId,
        String destinationLocationBarcode,
        Integer total,
        Integer current) {
        if (taskType == TaskType.PICKING_ORDER) {
            OrderLine orderLine = orderLineRepository.findByTaskId(task.getId())
                .orElseThrow(() -> new AllocationsNotFoundException(username));
            Order order = orderLine.getOrder();
            logicalId = order.getLogicId();
            destinationLocationBarcode = order.getDestinationLocation().getBarcode();
            total = allocationRepository.countAllocationsInOrder(order.getId());
            current = allocationRepository.countCompletedAllocationsInOrder(order.getId()) + 1;
        } else if (taskType == TaskType.REPLENISHMENT) {
            Replenishment replenishment = replenishmentRepository.findByTaskId(task.getId())
                .orElseThrow(() -> new AllocationsNotFoundException(username));
            destinationLocationBarcode = replenishment.getDestinationLocation().getBarcode();
        }
        return new Result(total, current, logicalId, destinationLocationBarcode);
    }

    private Allocation getAllocationsForOperator(String username) {
        return allocationRepository.findFirstByTask_Operator_UsernameAndStatusInOrderByCreatedAtAscIdAsc(
                username,
                java.util.List.of(Status.ASSIGNED, Status.IN_PROGRESS)
            )
            .orElseThrow(() -> new AllocationsNotFoundException(username));
    }

    private record Result(Integer total, Integer current, String logicalId, String destinationLocationBarcode) {
    }
}
