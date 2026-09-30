package com.isd.wms.mapper;

import com.isd.wms.dto.location.LocationResponse;
import com.isd.wms.entity.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationMapper {
    public LocationResponse toResponse(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getBarcode(),
                location.getZone(),
                location.getDescription(),
                location.getAvailable(),
                location.getIsActive()
        );
    }
}
