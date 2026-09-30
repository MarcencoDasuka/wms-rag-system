package com.isd.wms.controller;

import com.isd.wms.dto.location.LocationCreateRequest;
import com.isd.wms.dto.location.LocationResponse;
import com.isd.wms.dto.location.LocationUpdateRequest;
import com.isd.wms.repository.projections.ShortLocationProjection;
import com.isd.wms.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing warehouse locations.
 *
 * <p>Provides CRUD operations for warehouse locations and a bulk-import endpoint.
 * Write operations (create, update, delete, import) are restricted to users with
 * the {@code SUPERVISOR} or {@code DEV} role. Read operations are publicly accessible.</p>
 *
 * <p>Base path: {@code /api/locations}</p>
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * Creates a new warehouse location.
     *
     * @param request the location creation request; must be valid
     * @return {@code 201 Created} with the created {@link LocationResponse}
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody LocationCreateRequest request) {
        LocationResponse response = locationService.createLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all warehouse locations.
     *
     * @return a list of all {@link LocationResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<LocationResponse>> getAllLocations() {
        return ResponseEntity.ok(locationService.getAllLocations());
    }

    /**
     * Retrieves a condensed list of dispatch locations.
     *
     * @return {@code 200 OK} with a list of {@link ShortLocationProjection} objects for dispatch-type locations
     */
    @GetMapping("/dispatches")
    public ResponseEntity<List<ShortLocationProjection>> getLocationsDispatch() {
        return ResponseEntity.ok(locationService.getShortLocationsDispatch());
    }

    /**
     * Retrieves a single warehouse location by its ID.
     *
     * @param id the ID of the location to retrieve
     * @return {@code 200 OK} with the {@link LocationResponse} for the specified location
     */
    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> getLocationById(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    /**
     * Updates an existing warehouse location.
     *
     * @param id      the ID of the location to update
     * @param request the update request containing the new location data; must be valid
     * @return {@code 200 OK} with the updated {@link LocationResponse}, or an error body on failure
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<LocationResponse> updateLocation(@PathVariable Long id, @Valid @RequestBody LocationUpdateRequest request) {
        LocationResponse response = locationService.updateLocation(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a warehouse location by its ID.
     *
     * @param id the ID of the location to delete
     * @return {@code 200 OK} with a success message, or an error body on failure
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<?> deleteLocation(@PathVariable Long id) {
        locationService.deleteLocation(id);
        return ResponseEntity.ok(Map.of("message", "Location successfully deleted"));
    }

    /**
     * Imports warehouse locations in bulk from an uploaded file.
     *
     * @param file the multipart file containing location data to import
     * @return {@code 200 OK} with a confirmation message on success
     */
    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> importLocations(@RequestParam("file") MultipartFile file) {
        locationService.importLocationsFromFile(file);
        return ResponseEntity.ok("Locations were successfully imported.");
    }
}
