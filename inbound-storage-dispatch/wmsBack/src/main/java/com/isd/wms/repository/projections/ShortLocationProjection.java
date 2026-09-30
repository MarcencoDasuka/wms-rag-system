package com.isd.wms.repository.projections;

public interface ShortLocationProjection {
    Long getId();
    String getBarcode();

    default String getLocationCode() {
        return getBarcode();
    }
}
