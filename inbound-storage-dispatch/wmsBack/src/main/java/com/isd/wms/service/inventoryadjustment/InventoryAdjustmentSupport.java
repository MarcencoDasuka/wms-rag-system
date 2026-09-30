package com.isd.wms.service.inventoryadjustment;

import java.util.Optional;

public final class InventoryAdjustmentSupport {

    private InventoryAdjustmentSupport() {
    }

    public static int nullSafeQuantity(Integer quantity) {
        return Optional.ofNullable(quantity).orElse(0);
    }
}
