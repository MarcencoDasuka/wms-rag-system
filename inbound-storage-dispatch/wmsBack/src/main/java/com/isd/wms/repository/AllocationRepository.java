package com.isd.wms.repository;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Order;
import com.isd.wms.entity.User;
import com.isd.wms.enums.Status;
import com.isd.wms.repository.projections.AllocationSupervisorProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Allocation} entities.
 * <p>
 * Provides data access methods for allocations, including queries to find
 * allocations by task, stock, operator, and order. Also includes methods
 * to update allocation statuses in bulk and retrieve projections for
 * supervisor and operator dashboards.
 * </p>
 *
 * @see Allocation
 * @see Status
 */
@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {
    /**
     * Finds all allocations belonging to a given task.
     *
     * @param taskId the task ID
     * @return list of allocations for the task
     */
    List<Allocation> findAllByTaskId(Long taskId);

    /**
     * Finds active allocations for a task, excluding specified statuses,
     * ordered by creation time.
     *
     * @param taskId           the task ID
     * @param excludedStatuses statuses to exclude (e.g., COMPLETED, CANCELED)
     * @return ordered list of active allocations
     */
    @Query("""
            SELECT a FROM Allocation a
            WHERE a.task.id = :taskId
              AND a.status NOT IN (:excludedStatuses)
            ORDER BY a.createdAt, a.id
        """)
    List<Allocation> findActiveByTaskIdOrderByCreatedAtAscIdAsc(
        @Param("taskId") Long taskId,
        @Param("excludedStatuses") List<Status> excludedStatuses
    );

    /**
     * Finds active allocations for a task and a specific stock,
     * excluding given statuses, ordered by creation time.
     *
     * @param taskId           the task ID
     * @param stockId          the stock ID
     * @param excludedStatuses statuses to exclude
     * @return ordered list of active allocations
     */
    @Query("""
            SELECT a FROM Allocation a
            WHERE a.task.id = :taskId
              AND a.stock.id = :stockId
              AND a.status NOT IN (:excludedStatuses)
            ORDER BY a.createdAt, a.id
        """)
    List<Allocation> findActiveByTaskIdAndStockIdOrderByCreatedAtAscIdAsc(
        @Param("taskId") Long taskId,
        @Param("stockId") Long stockId,
        @Param("excludedStatuses") List<Status> excludedStatuses
    );

    /**
     * Finds all active allocations for a stock, excluding given statuses,
     * ordered by creation time.
     *
     * @param stockId          the stock ID
     * @param excludedStatuses statuses to exclude
     * @return ordered list of active allocations
     */
    @Query("""
            SELECT a FROM Allocation a
            WHERE a.stock.id = :stockId
              AND a.status NOT IN (:excludedStatuses)
            ORDER BY a.createdAt, a.id
        """)
    List<Allocation> findActiveByStockId(
        @Param("stockId") Long stockId,
        @Param("excludedStatuses") List<Status> excludedStatuses
    );

    /**
     * Finds allocations for a specific operator with given statuses,
     * ordered by creation time.
     *
     * @param operator the operator user
     * @param statuses the allowed statuses
     * @return ordered list of allocations
     */
    @Query("""
            SELECT a FROM Allocation a
            JOIN Task t ON t = a.task
            WHERE t.operator = :operator
              AND a.status IN (:statuses)
            ORDER BY a.createdAt, a.id
        """)
    List<Allocation> findByOperatorAndStatuses(User operator, List<Status> statuses);

    /**
     * Finds allocations for an operator (by username) with given statuses.
     *
     * @param username the operator's username
     * @param statuses the allowed statuses
     * @return ordered list of allocations
     */
    @Query("""
            SELECT a FROM Allocation a
            JOIN Task t ON t = a.task
            JOIN User u ON u = t.operator
            WHERE u.username = :username
              AND a.status IN (:statuses)
            ORDER BY a.createdAt, a.id
        """)
    List<Allocation> findByOperatorUsernameAndStatuses(
        @Param("username") String username,
        @Param("statuses") List<Status> statuses
    );

    /**
     * Atomically finds and updates the oldest ASSIGNED or IN_PROGRESS allocation
     * for an operator, setting its status to IN_PROGRESS, and returns its ID.
     * Used for picking flow.
     *
     * @param username the operator's username
     * @return the allocation ID that was updated
     */
    @Query(value = """
            with oldest_allocation as (select a.id as allocation_id from allocations a
                                           join tasks t on t.id = a.task_id
                                           join users u on t.operator_id = u.id
                                  where u.username = :userName
                                    and a.status in ('ASSIGNED', 'IN_PROGRESS')
                                  order by a.created_at, a.id
                                  limit 1)
        update allocations a
        set status = 'IN_PROGRESS'
        where a.id = (select allocation_id from oldest_allocation)
        returning a.id
        """, nativeQuery = true)
    Optional<Long> findOldestAssignedAllocationId(String username);

    /**
     * Finds the first allocation for an operator (by username) with given statuses,
     * ordered by creation time.
     *
     * @param username the operator's username
     * @param statuses the allowed statuses
     * @return an Optional containing the allocation, if any
     */
    Optional<Allocation> findFirstByTask_Operator_UsernameAndStatusInOrderByCreatedAtAscIdAsc(
        String username,
        List<Status> statuses
    );

    /**
     * Finds all allocations belonging to a given order.
     *
     * @param order the order
     * @return list of allocations for the order
     */
    @Query("""
            SELECT a FROM Allocation a
            JOIN OrderLine o ON o.task.id = a.task.id
            WHERE o.order = :order
            GROUP BY a, o.order.id
            ORDER BY a.createdAt, a.id
        """)
    List<Allocation> findAllByOrder(
        @Param("order") Order order
    );

    /**
     * Counts non‑canceled allocations in a given order.
     *
     * @param orderId the order ID
     * @return the number of allocations
     */
    @Query("""
            SELECT COUNT(DISTINCT a) FROM Allocation a
            JOIN OrderLine ol ON ol.task = a.task
            WHERE ol.order.id = :orderId
            AND NOT a.status = com.isd.wms.enums.Status.CANCELED
        """)
    Integer countAllocationsInOrder(
        @Param("orderId") Long orderId
    );

    /**
     * Counts completed allocations in a given order.
     *
     * @param orderId the order ID
     * @return the number of completed allocations
     */
    @Query("""
            SELECT COUNT(DISTINCT a) FROM Allocation a
            JOIN OrderLine ol ON ol.task = a.task
            WHERE ol.order.id = :orderId
            AND a.status = com.isd.wms.enums.Status.COMPLETED
        """)
    Integer countCompletedAllocationsInOrder(
        @Param("orderId") Long orderId
    );

    /**
     * Bulk‑updates the status of all allocations belonging to a given order.
     *
     * @param orderId the order ID
     * @param status  the new status
     * @return the number of updated allocations
     */
    @Modifying
    @Query("""
            UPDATE Allocation a
            SET a.status = :status
            WHERE a.task.id IN (
                SELECT ol.task.id
                FROM OrderLine ol
                WHERE ol.order.id = :orderId
            )
        """)
    int updateStatusByOrderId(
        @Param("orderId") Long orderId,
        @Param("status") Status status);

    /**
     * Retrieves a list of all allocations with detailed information for supervisor views,
     * including order/replenishment references, quantities, and statuses.
     *
     * @return list of {@link AllocationSupervisorProjection} projections
     */
    @Query("""
            SELECT
                a.id AS allocationId,
                r.id AS replenishmentId,
            ol.order.id AS orderId,
            t.taskType AS type,
            a.stock.id AS stockId,
            a.stock.product.name AS productName,
            a.stock.location.name AS locationName,
            a.quantity AS requestedQuantity,
            COALESCE(
                a.pickedQuantity,
                CASE
                    WHEN a.status IN (
                        com.isd.wms.enums.Status.COMPLETED,
                        com.isd.wms.enums.Status.PARTIALLY_COMPLETED
                    ) THEN a.quantity
                    ELSE 0
                END
            ) AS deliveredQuantity,
            a.status AS status,
            a.sourceLocationScanned AS sourceLocationScanned,
            a.productScanned AS productScanned
        FROM Allocation a
        JOIN Task t ON a.task = t
        JOIN User u ON u = t.supervisor
        LEFT JOIN OrderLine ol ON ol.task = a.task
        LEFT JOIN Replenishment r ON r.task = a.task
        ORDER BY a.createdAt, a.id
        """)
    List<AllocationSupervisorProjection> getAllAllocations();

    /**
     * Deletes all allocations created before a given cutoff date.
     * Used for cleanup of historical data.
     *
     * @param cutoffDate the cutoff date
     * @return the number of deleted records
     */
    @Modifying
    @Query("DELETE FROM Allocation a WHERE a.createdAt < :cutoffDate")
    int deleteAllocationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
