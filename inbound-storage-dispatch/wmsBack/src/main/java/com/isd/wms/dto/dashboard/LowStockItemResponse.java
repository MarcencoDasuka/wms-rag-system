package com.isd.wms.dto.dashboard;

public record LowStockItemResponse(
        Long productId,
        String productName,
        String sku,
        String locationCode,
        int quantity,
        int minimumQuantity,
        String status
) {
}
