package com.isd.wms.service;

import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.dto.inventory.InventoryAdjustmentResponse;
import com.isd.wms.entity.Stock;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.service.inventoryadjustment.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Service for handling inventory quantity adjustments with advanced logic
 * for managing shortages and reallocations.
 * <p>
 * Adjustments can be applied to a specific stock record. The service validates
 * the request, builds an adjustment plan that may affect multiple order lines,
 * and then either previews the plan or commits it. It also logs detailed
 * information about the adjustment process.
 * </p>
 * <p>
 * The adjustment process is delegated to several helper components:
 * {@link InventoryAdjustmentValidator}, {@link InventoryAdjustmentPlanner},
 * {@link InventoryAdjustmentApplier}, and {@link InventoryAdjustmentResponseMapper}.
 * </p>
 *
 * @see InventoryAdjustmentValidator
 * @see InventoryAdjustmentPlanner
 * @see InventoryAdjustmentApplier
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentValidator inventoryAdjustmentValidator;
    private final InventoryAdjustmentPlanner inventoryAdjustmentPlanner;
    private final InventoryAdjustmentApplier inventoryAdjustmentApplier;
    private final InventoryAdjustmentResponseMapper inventoryAdjustmentResponseMapper;
    private final StockRepository stockRepository;

    /**
     * Previews an adjustment without persisting any changes.
     *
     * @param stockId the ID of the stock to adjust
     * @param request the adjustment request (new quantity, reason, etc.)
     * @return a preview response showing the impact of the adjustment
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse previewAdjustment(Long stockId, InventoryAdjustmentRequest request) {
        return processAdjustment(stockId, request, true);
    }

    /**
     * Applies a stock adjustment permanently.
     * <p>
     * This method validates the adjustment, builds a plan, applies it (which may
     * affect order allocations and create inventory history), and returns the result.
     * </p>
     *
     * @param stockId the ID of the stock to adjust
     * @param request the adjustment request
     * @return the response containing the updated stock and affected orders
     */
    @Transactional
    public InventoryAdjustmentResponse adjustStock(Long stockId, InventoryAdjustmentRequest request) {
        return processAdjustment(stockId, request, false);
    }

    private InventoryAdjustmentResponse processAdjustment(
        Long stockId,
        InventoryAdjustmentRequest request,
        boolean preview
    ) {
        InventoryService.validateStockDate(request.manufactureDate(), request.expirationDate());
        InventoryAdjustmentContext context = inventoryAdjustmentValidator.validateAndLoad(stockId, request);
        log.info(
            "Stock adjustment started: stockId={}, previousQuantity={}, newQuantity={}, " +
                "difference={}, reason={}, userId={}, preview={}",
            stockId, context.previousQuantity(), context.newQuantity(), context.quantityDifference(),
            request.reason(), request.userId(), preview
        );

        InventoryAdjustmentPlan plan = inventoryAdjustmentPlanner.buildPlan(context);
        log.info(
            "Adjustment plan calculated: stockId={}, affectedOrderLines={}, " +
                "affectedOrders={}, preservedQuantityOnAdjustedStock={}",
            stockId, plan.affectedTasks().size(),
            plan.affectedTasks().stream().map(AffectedTaskAdjustment::orderId).distinct().count(),
            plan.preservedQuantityOnAdjustedStock()
        );

        Long inventoryHistoryId = preview ? null : inventoryAdjustmentApplier.applyAdjustmentPlan(plan);
        InventoryAdjustmentResponse response =
            inventoryAdjustmentResponseMapper.toResponse(plan, preview, inventoryHistoryId);

        log.info(
            "Stock adjustment completed: stockId={}, preview={}, affectedOrders={}, " +
                "affectedLines={}, reallocationSucceeded={}, partialShortage={}, orderCancelled={}",
            stockId, preview, response.affectedOrders().size(), response.affectedOrderLines().size(),
            response.reallocationSucceeded(), response.partialShortageCreated(), response.orderCancelled()
        );

        if (context.quantityDifference() < 0) {
            log.info("Stock reduction triggered shortage recalculation: stockId={}, reason={}",
                stockId, request.reason());
        }

        stockRepository.findById(stockId)
            .filter(s -> s.getQuantity() == 0)
            .ifPresent(s -> s.setAvailable(false));
        return response;
    }
}
