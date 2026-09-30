package com.isd.wms.service.imports.mapper;

import com.isd.wms.dto.inventory.AddStockRequest;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.LocationNotFoundException;
import com.isd.wms.exception.ProductNotFoundException;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.service.imports.dto.StockInfo;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockImportMapper implements ImportMapper<StockInfo, AddStockRequest> {

    private final SecurityFacade securityFacade;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    @Override
    public AddStockRequest toEntity(StockInfo info) {
        try {
            return new AddStockRequest(
                getProductId(info.getProductName()),
                getLocationId(info.getLocationName()),
                info.getQuantity(),
                info.getReservedQuantity(),
                parseFlexibleDate(info.getManufactureDateRaw()),
                parseFlexibleDate(info.getExpirationDateRaw()),
                securityFacade.getCurrentUser().getId()
            );
        } catch (Exception e) {
            throw new InvalidRequestException(
                String.format("An error occurred at parsing the stock of product %s at location %s.",
                    info.getProductName(), info.getLocationName())
            );
        }
    }

    private LocalDate parseFlexibleDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }
        String text = rawDate.trim();
        try {
            if (text.contains(".")) {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else if (text.contains("/")) {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } else {
                return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        } catch (Exception e) {
            log.warn("Could not parse date '{}'. It will be saved as null.", text);
            return null;
        }
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
    public Class<StockInfo> supports() {
        return StockInfo.class;
    }
}
