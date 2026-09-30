package com.isd.wms.dto.inventory;

import com.isd.wms.dto.order.shortage.AffectedOrderLineResponse;
import com.isd.wms.dto.order.shortage.ShortageOrderResponse;
import java.util.List;

public record InventoryAdjustmentResponse(
        StockResponse stock,
        Integer previousQuantity,
        Integer newQuantity,
        Integer difference,
        String reason,
        String comment,
        Long inventoryHistoryId,
        boolean preview,
        boolean reallocationSucceeded,
        boolean partialShortageCreated,
        boolean orderCancelled,
        String message,
        List<ShortageOrderResponse> affectedOrders,
        List<AffectedOrderLineResponse> affectedOrderLines
) {
}
