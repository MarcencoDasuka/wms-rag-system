package com.isd.wms.repository;

import com.isd.wms.repository.projections.ShortLocationProjection;
import com.isd.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Location} entities.
 * <p>
 * Provides methods for checking uniqueness of barcode and name, retrieving
 * active locations, and filtering locations by zone (e.g., dispatch).
 * Also includes a projection for lightweight retrieval of dispatch locations.
 * </p>
 */
public interface LocationRepository extends JpaRepository<Location, Long> {

    /**
     * Checks whether a location with the given barcode exists (case‑insensitive).
     *
     * @param barcode the barcode
     * @return true if exists
     */
    boolean existsByBarcodeIgnoreCase(String barcode);

    /**
     * Finds all active locations (where isActive = true).
     *
     * @return list of active locations
     */
    List<Location> findAllByIsActiveTrue();

    /**
     * Checks whether a location with the given name exists (case‑insensitive).
     *
     * @param newCode the location name
     * @return true if exists
     */
    boolean existsByNameIgnoreCase(String newCode);

    /**
     * Retrieves a projection of dispatch locations (zone = DISPATCH, available = true, active = true).
     *
     * @return list of short projections containing ID and barcode
     */
    @Query("""
        SELECT l.id AS id, barcode AS barcode FROM Location l
        WHERE l.available = true
        AND l.isActive = true
        AND l.zone = com.isd.wms.enums.Zone.DISPATCH
        """)
    List<ShortLocationProjection> getLocationDispatch();

    /**
     * Finds a location ID by its name.
     *
     * @param name the location name
     * @return an Optional containing the location ID, if found
     */
    @Query("""
            SELECT l.id FROM Location l
            WHERE l.name = :name
        """)
    Optional<Long> findLocationIdByName(
        @Param("name") String name
    );
}
