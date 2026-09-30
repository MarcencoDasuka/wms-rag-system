package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.enums.Status;
import java.util.List;

public record InventoryAdjustmentPlan(
    InventoryAdjustmentContext context,
    List<AffectedTaskAdjustment> affectedTasks,
    int preservedQuantityOnAdjustedStock
) {
    public boolean reallocationSucceeded() {
        return affectedTasks.stream().noneMatch(task -> task.shortageQuantity() > 0 || task.lineStatus() == Status.CANCELED);
    }
}
