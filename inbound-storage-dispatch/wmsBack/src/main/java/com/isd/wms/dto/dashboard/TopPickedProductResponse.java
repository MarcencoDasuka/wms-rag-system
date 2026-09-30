package com.isd.wms.dto.dashboard;

public record TopPickedProductResponse(
        Long productId,
        String productName,
        String sku,
        long pickedQuantity
) {
}
