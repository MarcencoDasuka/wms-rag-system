package com.isd.wms.service.inventoryadjustment;

import com.isd.wms.dto.inventory.InventoryAdjustmentRequest;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.entity.User;

public record InventoryAdjustmentContext(
    Long stockId,
    InventoryAdjustmentRequest request,
    Stock stock,
    User user,
    Product product,
    int previousQuantity
) {
    public int newQuantity() {
        return request.newQuantity();
    }

    public int quantityDifference() {
        return newQuantity() - previousQuantity;
    }
}
