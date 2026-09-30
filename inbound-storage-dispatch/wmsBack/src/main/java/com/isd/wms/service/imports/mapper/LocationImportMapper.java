package com.isd.wms.service.imports.mapper;

import com.isd.wms.dto.location.LocationCreateRequest;
import com.isd.wms.service.LocationService;
import com.isd.wms.service.imports.dto.LocationInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting {@link LocationInfo} DTOs to {@link LocationCreateRequest} objects.
 * <p>
 * The resulting request can be passed directly to {@link LocationService#createLocation}.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class LocationImportMapper implements ImportMapper<LocationInfo, LocationCreateRequest> {

    @Override
    public LocationCreateRequest toEntity(LocationInfo info) {
        return new LocationCreateRequest(
            info.getName(),
            info.getBarcode(),
            info.getZone(),
            info.getDescription()
        );
    }

    @Override
    public Class<LocationInfo> supports() {
        return LocationInfo.class;
    }
}
