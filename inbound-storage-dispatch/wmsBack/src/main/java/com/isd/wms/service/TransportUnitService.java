package com.isd.wms.service;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.TransportUnit;
import com.isd.wms.exception.TransportUnitNotFoundException;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.TransportUnitRepository;
import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.ReplenishmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing transport units (TU) used for moving goods.
 * <p>
 * A transport unit is a physical container (e.g., a pallet or cart) identified
 * by a unique barcode. This service handles the occupation (linking to an order
 * or replenishment) and release of transport units. A TU can be associated with
 * only one active process at a time.
 * </p>
 * <p>
 * Barcodes must follow the pattern "TU" followed by 6 digits.
 * </p>
 *
 * @see TransportUnit
 * @see Allocation
 * @see Order
 * @see Replenishment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransportUnitService {

    private final TransportUnitRepository tuRepository;
    private final AllocationRepository allocationRepository;
    private final OrderRepository orderRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private static final String BARCODE_REGEX = "^TU\\d{6}$";

    /**
     * Occupies a transport unit by linking it to either an order or a replenishment,
     * based on the task associated with the given allocation.
     *
     * @param barcode the TU barcode
     * @param taskId the ID of the allocation (which indirectly identifies the task)
     * @param isOrder true if linking to an order, false for a replenishment
     * @throws IllegalArgumentException if the barcode format is invalid
     * @throws EntityNotFoundException if the allocation, task, order, or replenishment is not found
     * @throws IllegalStateException if the TU is already active or the allocation lacks a task
     */
    @Transactional
    public void occupyTransportUnit(String barcode, Long taskId, boolean isOrder) {
        log.info("Starting validation and occupation for TU barcode: {}, Allocation ID: {}, Process type: {}",
            barcode, taskId, isOrder ? "ORDER" : "REPLENISHMENT");

        if (barcode == null || !barcode.matches(BARCODE_REGEX)) {
            log.warn("Validation failed: Barcode format '{}' is invalid", barcode);
            throw new IllegalArgumentException("Invalid format! Barcode must start with 'TU' followed by 6 digits.");
        }

        Allocation allocation = allocationRepository.findById(taskId)
            .orElseThrow(() -> {
                log.error("Allocation with ID {} not found", taskId);
                return new EntityNotFoundException("Allocation with ID " + taskId + " not found");
            });

        if (allocation.getTask() == null) {
            log.error("Allocation ID {} does not have any Task associated!", taskId);
            throw new IllegalStateException("Allocation is missing its parent Task!");
        }

        Long realTaskId = allocation.getTask().getId();

        TransportUnit tu = tuRepository.findByBarcode(barcode)
            .orElseThrow(() -> {
                log.error("Scanned barcode does not exist in database: {}", barcode);
                return new TransportUnitNotFoundException(barcode);
            });

        if (tu.getOrder() != null || tu.getReplenishment() != null) {
            log.warn("Attempted to occupy an active TU: {}. Associated Order: {}, Associated Replenishment: {}",
                barcode,
                tu.getOrder() != null ? tu.getOrder().getId() : "N/A",
                tu.getReplenishment() != null ? tu.getReplenishment().getId() : "N/A");
            throw new IllegalStateException("This TU is already locked in another active process!");
        }

        if (isOrder) {
            var order = orderRepository.getOrderByTask(allocation.getTask())
                .orElseThrow(() -> {
                    log.error("No Order found associated with Task ID {}", realTaskId);
                    return new EntityNotFoundException("Order not found for the associated Task.");
                });

            log.info("TU {} successfully linked to Order ID: {}", barcode, order.getId());
            tu.setOrder(order);
            tu.setReplenishment(null);
        } else {
            var replenishment = replenishmentRepository.findByTaskId(realTaskId)
                .orElseThrow(() -> {
                    log.error("No Replenishment found for Task ID {}", realTaskId);
                    return new EntityNotFoundException("Replenishment not found for Task ID " + realTaskId);
                });

            if (tuRepository.existsByReplenishment(replenishment)) {
                throw new IllegalStateException("This replenishment already has an active transport unit!");
            }

            log.info("TU {} successfully linked to Replenishment ID: {}", barcode, replenishment.getId());
            tu.setReplenishment(replenishment);
            tu.setOrder(null);
        }

        tuRepository.save(tu);
        log.info("Changes for TU {} successfully saved.", barcode);
    }

    /**
     * Releases a transport unit, clearing any order or replenishment association.
     *
     * @param barcode the TU barcode
     * @throws EntityNotFoundException if the TU does not exist
     */
    @Transactional
    public void releaseTransportUnit(String barcode) {
        log.info("Starting release process for TU: {}", barcode);

        TransportUnit tu = tuRepository.findByBarcode(barcode)
            .orElseThrow(() -> {
                log.error("Release failed: TU with barcode {} does not exist", barcode);
                return new EntityNotFoundException("The specified Transport Unit was not found.");
            });

        tu.setOrder(null);
        tu.setReplenishment(null);
        tuRepository.save(tu);

        log.info("TU {} is now free and available for use.", barcode);
    }
}
