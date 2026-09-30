package com.isd.wms.repository;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.TransportUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TransportUnit} entities.
 * <p>
 * Provides methods for finding transport units by barcode, and checking
 * whether a unit is currently associated with an order or replenishment.
 * </p>
 */
@Repository
public interface TransportUnitRepository extends JpaRepository<TransportUnit, Long> {

    /**
     * Finds a transport unit by its barcode.
     *
     * @param barcode the barcode
     * @return an Optional containing the transport unit, if found
     */
    Optional<TransportUnit> findByBarcode(String barcode);

    /**
     * Checks whether any transport unit is associated with the given order.
     *
     * @param order the order
     * @return true if a transport unit is linked to the order
     */
    boolean existsByOrder(Order order);

    /**
     * Checks whether any transport unit is associated with the given replenishment.
     *
     * @param replenishment the replenishment
     * @return true if a transport unit is linked to the replenishment
     */
    boolean existsByReplenishment(Replenishment replenishment);

    /**
     * Finds the transport unit associated with an order, if any.
     *
     * @param order the order
     * @return an Optional containing the transport unit, if found
     */
    Optional<TransportUnit> findByOrder(Order order);

    /**
     * Finds the transport unit associated with a replenishment, if any.
     *
     * @param replenishment the replenishment
     * @return an Optional containing the transport unit, if found
     */
    Optional<TransportUnit> findByReplenishment(Replenishment replenishment);

    List<TransportUnit> findAllByReplenishment(Replenishment replenishment);

    Optional<TransportUnit> findFirstByReplenishmentOrderByCreatedAtAscIdAsc(Replenishment replenishment);
}
