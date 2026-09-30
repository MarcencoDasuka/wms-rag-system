package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.entity.OrderLine;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.Status;
import java.time.LocalDateTime;
import java.util.List;

public record AffectedTaskAdjustment(
    Long taskId,
    Long orderId,
    String orderNumber,
    LocalDateTime orderCreatedAt,
    LocalDateTime orderUpdatedAt,
    Long orderLineId,
    Task task,
    OrderLine orderLine,
    int requestedQuantity,
    int allocatedFromOtherStocks,
    int preservedOnAdjustedStock,
    int reducedFromAdjustedStock,
    int allocatedFromAlternatives,
    int shortageQuantity,
    Status lineStatus,
    boolean revalidationRequired,
    Long originalLocationId,
    String originalLocationBarcode,
    Long reallocatedLocationId,
    String reallocatedLocationBarcode,
    List<ReallocationPlanItem> reallocationPlan
) {
    public int finalAllocatedQuantity() {
        return allocatedFromOtherStocks + preservedOnAdjustedStock + allocatedFromAlternatives;
    }
}
