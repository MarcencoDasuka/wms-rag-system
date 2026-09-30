package com.isd.wms.repository.projections;

public interface OperatorAllocationProjection {
    Long getOldestOrderId();
    String getOrderName();
    Long getAllocationId();
    String getProductName();
    String getProductBarcode();
    String getLocationName();
    String getLocationBarcode();
    Integer getQuantity();
}
