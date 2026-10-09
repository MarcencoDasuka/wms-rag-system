package com.isd.wms.repository;

import com.isd.wms.entity.InventoryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link InventoryHistory} entities.
 * <p>
 * Provides data access for inventory history records, including finding
 * history related to a product and location, and deleting history records
 * for a specific stock (used when cleaning up removed stock).
 * </p>
 */
public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, Long> {

    @Override
    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation", "user"})
    List<InventoryHistory> findAll();

    @Override
    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation", "user"})
    Page<InventoryHistory> findAll(Pageable pageable);

    /**
     * Finds inventory history records that involve a given product and location
     * as either the source or destination.
     *
     * @param sourceProductId       product ID for source side
     * @param sourceLocationId      location ID for source side
     * @param destinationProductId  product ID for destination side
     * @param destinationLocationId location ID for destination side
     * @return list of matching history records
     */
    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation", "user"})
    List<InventoryHistory> findByProductIdAndSourceLocationIdOrProductIdAndDestinationLocationId(
        Long sourceProductId,
        Long sourceLocationId,
        Long destinationProductId,
        Long destinationLocationId
    );

    @EntityGraph(attributePaths = {"product", "sourceLocation", "destinationLocation", "user"})
    Page<InventoryHistory> findByProductIdAndSourceLocationIdOrProductIdAndDestinationLocationId(
        Long sourceProductId,
        Long sourceLocationId,
        Long destinationProductId,
        Long destinationLocationId,
        Pageable pageable
    );
}
