package com.isd.wms.service;


import com.isd.wms.dto.location.LocationCreateRequest;
import com.isd.wms.dto.location.LocationResponse;
import com.isd.wms.dto.location.LocationUpdateRequest;
import com.isd.wms.entity.Location;
import com.isd.wms.exception.DuplicateBarcodeException;
import com.isd.wms.exception.DuplicateLocationNameException;
import com.isd.wms.exception.LocationNotFoundException;
import com.isd.wms.mapper.LocationMapper;
import com.isd.wms.repository.LocationRepository;
import com.isd.wms.repository.StockRepository;
import com.isd.wms.repository.projections.ShortLocationProjection;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.imports.dto.LocationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service for managing warehouse locations.
 * <p>
 * Supports creating, updating, deleting, and retrieving locations. Locations
 * have a unique barcode and name. A location that currently contains stock
 * cannot be modified in its core attributes (code, zone, availability) – only
 * its description may be updated until the location is emptied.
 * </p>
 * <p>
 * Deletion is soft: the location is marked inactive.
 * </p>
 *
 * @see Location
 * @see LocationRepository
 * @see StockRepository
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class LocationService {
    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;
    private final StockRepository stockRepository;
    private final ImportService importService;

    /**
     * Creates a new location.
     *
     * @param request the location creation request
     * @return the created location response
     * @throws DuplicateBarcodeException if the barcode is already used
     * @throws DuplicateLocationNameException if the name is already used
     */
    @Transactional
    public LocationResponse createLocation(LocationCreateRequest request) {
        String code = request.barcode().trim();
        String name = request.name().trim();

        validateBarcodeUniqueness(code);

        validateLocationNameUniqueness(name);

        Location location = new Location(
            name,
            code,
            request.zone(),
            request.description(),
            true
        );
        return locationMapper.toResponse(locationRepository.save(location));
    }

    /**
     * Updates a location. If the location contains products, only the description
     * can be changed; other fields are protected.
     *
     * @param locationId the ID of the location to update
     * @param request the update request
     * @return the updated location response
     * @throws LocationNotFoundException if the location does not exist
     * @throws IllegalStateException if attempting to change protected fields of an occupied location
     */
    @Transactional
    public LocationResponse updateLocation(Long locationId, LocationUpdateRequest request) {
        Location location = getLocation(locationId);
        validateLocationUpdate(locationId, request, location);

        updateLocation(request, location);

        return locationMapper.toResponse(locationRepository.save(location));
    }

    private void validateLocationUpdate(Long locationId, LocationUpdateRequest request, Location location) {
        String newName = request.name().trim();
        String newCode = request.barcode().trim();

        boolean isNameChanged = !location.getName().equalsIgnoreCase(newName);
        boolean isCodeChanged = !location.getBarcode().equalsIgnoreCase(newCode);
        boolean isZoneChanged = location.getZone() != request.zone();
        boolean isAvailableChanged = location.getAvailable() != request.available();

        boolean hasProducts = stockRepository.existsByLocationIdAndAvailableIsTrue(locationId);

        if (hasProducts && (isNameChanged || isCodeChanged || isZoneChanged || isAvailableChanged)) {
            log.warn("Attempt to edit protected fields of occupied location ID: {}", locationId);
            throw new IllegalStateException("Cannot change the code, zone, or availability of a location that contains products. Only the description can be updated. Please move the products first.");
        }

        if (isCodeChanged) {
            validateBarcodeUniqueness(newCode);
        }

        if (isNameChanged) {
            validateLocationNameUniqueness(newName);
        }
    }

    private static void updateLocation(LocationUpdateRequest request, Location location) {
        location.setName(request.name().trim());
        location.setBarcode(request.barcode().trim());
        location.setZone(request.zone());
        location.setDescription(request.description());
        location.setAvailable(request.available());
    }

    /**
     * Soft‑deletes a location (marks as inactive) if it is empty.
     *
     * @param locationId the ID of the location to delete
     * @throws IllegalStateException if the location still contains products
     */
    @Transactional
    public void deleteLocation(Long locationId) {
        boolean hasProducts = stockRepository.existsByLocationIdAndAvailableIsTrue(locationId);
        if (hasProducts) {
            log.warn("Attempt to delete occupied location ID: {}", locationId);
            throw new IllegalStateException("You cannot delete a location while there is a product in it");
        }

        Location location = getLocation(locationId);
        location.setIsActive(false);

        locationRepository.save(location);

        log.info("Location ID {} successfully deleted", locationId);
    }

    public List<LocationResponse> getAllLocations() {
        return locationRepository.findAllByIsActiveTrue().stream()
            .map(locationMapper::toResponse)
            .toList();
    }

    public LocationResponse getLocationById(Long locationId) {
        return locationMapper.toResponse(getLocation(locationId));
    }

    public List<ShortLocationProjection> getShortLocationsDispatch() {
        return locationRepository.getLocationDispatch();
    }

    private Location getLocation(Long locationId) {
        return locationRepository.findById(locationId)
            .orElseThrow(() -> new LocationNotFoundException(locationId));
    }

    private void validateBarcodeUniqueness(String code) {
        if (locationRepository.existsByBarcodeIgnoreCase(code)) {
            throw new DuplicateBarcodeException(code);
        }
    }

    private void validateLocationNameUniqueness(String name) {
        if (locationRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateLocationNameException(name);
        }
    }

    @Transactional
    public void importLocationsFromFile(MultipartFile file) {
        List<LocationCreateRequest> locations = importService.importData(file, LocationInfo.class);
        locations.forEach(this::createLocation);
    }
}
