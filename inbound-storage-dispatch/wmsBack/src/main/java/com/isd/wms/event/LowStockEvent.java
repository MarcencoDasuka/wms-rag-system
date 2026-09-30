package com.isd.wms.event;

import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;

public record LowStockEvent(Product product, Location location, int availableQuantity) {
}
