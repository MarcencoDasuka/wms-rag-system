package com.isd.wms.repository.projections;

public interface ProductWithQuantityProjection {
    Long getId();
    String getName();
    String getBarcode();
    Integer getQuantity();
}
