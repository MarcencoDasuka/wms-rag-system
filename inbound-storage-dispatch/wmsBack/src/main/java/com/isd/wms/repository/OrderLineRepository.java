package com.isd.wms.repository;

import com.isd.wms.entity.OrderLine;
import com.isd.wms.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link OrderLine} entities.
 * <p>
 * Provides methods for finding order lines by order, by task, and bulk
 * updating statuses. Also includes a cleanup method for old lines.
 * </p>
 */
@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    /**
     * Finds all order lines for a given order.
     *
     * @param orderId the order ID
     * @return list of order lines
     */
    List<OrderLine> findAllByOrderId(Long orderId);

    /**
     * Finds the order line associated with a given task.
     *
     * @param taskId the task ID
     * @return an Optional containing the order line, if found
     */
    Optional<OrderLine> findByTaskId(Long taskId);

    /**
     * Bulk‑updates the status of all non‑canceled order lines for a given order.
     *
     * @param orderId the order ID
     * @param status  the new status
     * @return the number of updated lines
     */
    @Modifying
    @Query("""
            UPDATE OrderLine ol
                SET ol.status = :status
                WHERE ol.order.id = :orderId
                        AND ol.status NOT IN (com.isd.wms.enums.Status.CANCELED)
        """)
    int updateStatusByOrderId(
        @Param("orderId") Long orderId,
        @Param("status") Status status);

    /**
     * Deletes all order lines whose order was created before the cutoff date.
     *
     * @param cutoffDate the cutoff date
     * @return the number of deleted lines
     */
    @Modifying
    @Query("DELETE FROM OrderLine ol WHERE ol.order.createdAt < :cutoffDate")
    int deleteOrderLinesByOrderCreatedAtOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
