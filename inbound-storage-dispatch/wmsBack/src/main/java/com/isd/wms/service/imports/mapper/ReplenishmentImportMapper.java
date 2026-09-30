package com.isd.wms.service.imports.mapper;

import com.isd.wms.dto.replenishment.ReplenishmentCreateRequest;
import com.isd.wms.exception.LocationNotFoundException;
import com.isd.wms.exception.ProductNotFoundException;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.service.imports.dto.ReplenishmentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting {@link ReplenishmentInfo} DTOs to
 * {@link ReplenishmentCreateRequest} objects.
 * <p>
 * Resolves product and location IDs by name; throws appropriate exceptions
 * if either is not found.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ReplenishmentImportMapper implements ImportMapper<ReplenishmentInfo, ReplenishmentCreateRequest> {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    @Override
    public ReplenishmentCreateRequest toEntity(ReplenishmentInfo info) {
    return new ReplenishmentCreateRequest(
            getProductId(info.getProduct()),
            info.getRequestedQuantity(),
            getLocationId(info.getDestinationLocationName())
        );
    }

    private Long getProductId(String name) {
        return productRepository.findProductIdByName(name)
            .orElseThrow(() -> new ProductNotFoundException(name));
    }

    private Long getLocationId(String name) {
        return locationRepository.findLocationIdByName(name)
            .orElseThrow(() -> new LocationNotFoundException(name));
    }


    @Override
    public Class<ReplenishmentInfo> supports() {
        return ReplenishmentInfo.class;
    }
}
