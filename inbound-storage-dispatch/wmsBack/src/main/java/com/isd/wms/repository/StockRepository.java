package com.isd.wms.repository;

import com.isd.wms.entity.Location;
import com.isd.wms.entity.Product;
import com.isd.wms.entity.Stock;
import com.isd.wms.enums.Zone;
import jakarta.validation.constraints.Min;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Stock} entities.
 * <p>
 * Provides methods for finding stock by product, location, zone, and checking
 * availability. Also includes queries to find stocks with positive getAvailableQuantity
 * quantity, sorted by availability.
 * </p>
 */
public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Finds stock for a specific product and location by their IDs.
     *
     * @param productId  the product ID
     * @param locationId the location ID
     * @return an Optional containing the stock, if found
     */
    Optional<Stock> findByProductIdAndLocationIdAndAvailableIsTrue(Long productId, Long locationId);

    /**
     * Checks whether a location has any stock with quantity greater than the given value.
     *
     * @param locationId the location ID
     * @return true if there is stock with quantity > threshold
     */
    boolean existsByLocationIdAndAvailableIsTrue(Long locationId);

    /**
     * Finds stocks for a product in a specific zone with positive available quantity.
     *
     * @param productId the product ID
     * @param zone      the zone (e.g., REPLENISHMENT, PICKING)
     * @return list of stocks
     */
    @Query("""
        SELECT s FROM Stock s
        WHERE s.product.id = :productId
          AND s.available = true
          AND s.location.zone = :zone
          AND (s.quantity - s.reservedQuantity) > 0
        """)
    List<Stock> findAvailableStocksByProductIdAndZone(@Param("productId") Long productId, @Param("zone") Zone zone);

    /**
     * Finds the stock located at a given location.
     *
     * @param locationId the location ID
     * @return an Optional containing the stock, if found
     */
    Optional<Stock> findByLocationId(Long locationId);

    List<Stock> findAllByAvailableIsTrue();

    boolean existsByLocationAndAvailableIsTrueAndProductIsNot(Location location, Product product);

    Optional<Stock> findByProductIdAndLocationId(Long id, Long id1);

    Optional<Stock> findByLocationIdAndProductId(Long id, Long id1);

    Optional<Stock> findByLocationIdAndAvailableIsTrue(Long id);

    boolean findByIdAndQuantityLessThan(Long id, @Min(0) Integer quantity);
}
