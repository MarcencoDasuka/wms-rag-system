package com.isd.wms.dto.location;

import com.isd.wms.enums.Zone;

public record LocationResponse(
        Long id,
        String name,
        String barcode,
        Zone zone,
        String description,
        Boolean available,
        Boolean isActive
) {
    public String locationCode() {
        return barcode;
    }
}
