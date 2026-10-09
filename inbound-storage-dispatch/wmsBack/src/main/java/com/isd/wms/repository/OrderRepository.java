package com.isd.wms.repository;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.repository.projections.OrderOperatorProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Order} entities.
 * <p>
 * Provides advanced query capabilities: filtering orders by multiple criteria,
 * retrieving orders by the supervisor who created them, checking order
 * assignment to operators, and bulk status updates. Also includes methods
 * for finding the next order for an operator during picking flow.
 * </p>
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = {"destinationLocation"})
    List<Order> findAll();

    /**
     * Filters orders by optional criteria: logic ID, destination location,
     * status, and creation/update timestamps.
     *
     * @param logicId        logical order ID (exact match)
     * @param destinationId  destination location ID
     * @param status         order status
     * @param createdAt      exact creation timestamp
     * @param updatedAt      exact update timestamp
     * @return list of matching orders
     */
    @EntityGraph(attributePaths = {"destinationLocation"})
    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN OrderLine ol ON ol.order = o
        JOIN Task t ON t = ol.task
        JOIN User u ON u = t.supervisor
        WHERE (:logicId IS NULL OR o.logicId = :logicId)
        AND (:destinationId IS NULL OR o.destinationLocation.id = :destinationId)
        AND (:status IS NULL OR o.status = :status)
        AND (cast(:createdAt as timestamp) IS NULL OR o.createdAt = :createdAt)
        AND (cast(:updatedAt as timestamp) IS NULL OR o.updatedAt = :updatedAt)
        """)
    List<Order> filter(
        @Param("logicId") String logicId,
        @Param("destinationId") Long destinationId,
        @Param("status") OrderStatus status,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    @EntityGraph(attributePaths = {"destinationLocation"})
    @Query(value = """
        SELECT DISTINCT o FROM Order o
        JOIN OrderLine ol ON ol.order = o
        JOIN Task t ON t = ol.task
        JOIN User u ON u = t.supervisor
        WHERE (:logicId IS NULL OR o.logicId = :logicId)
        AND (:destinationId IS NULL OR o.destinationLocation.id = :destinationId)
        AND (:status IS NULL OR o.status = :status)
        AND (cast(:createdAt as timestamp) IS NULL OR o.createdAt = :createdAt)
        AND (cast(:updatedAt as timestamp) IS NULL OR o.updatedAt = :updatedAt)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT o) FROM Order o
        JOIN OrderLine ol ON ol.order = o
        JOIN Task t ON t = ol.task
        JOIN User u ON u = t.supervisor
        WHERE (:logicId IS NULL OR o.logicId = :logicId)
        AND (:destinationId IS NULL OR o.destinationLocation.id = :destinationId)
        AND (:status IS NULL OR o.status = :status)
        AND (cast(:createdAt as timestamp) IS NULL OR o.createdAt = :createdAt)
        AND (cast(:updatedAt as timestamp) IS NULL OR o.updatedAt = :updatedAt)
        """)
    Page<Order> filter(
        @Param("logicId") String logicId,
        @Param("destinationId") Long destinationId,
        @Param("status") OrderStatus status,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("updatedAt") LocalDateTime updatedAt,
        Pageable pageable
    );

    /**
     * Checks whether an order targeting the given destination location exists with any of the specified statuses.
     *
     * @param destinationLocationId destination location ID
     * @param statuses              collection of order statuses
     * @return true if matching order exists
     */
    boolean existsByDestinationLocationIdAndStatusIn(Long destinationLocationId, java.util.Collection<OrderStatus> statuses);

   /**
     * Finds all orders accessible by a supervisor: either created by the supervisor
     * or having tasks supervised by the supervisor.
     *
     * @param username the supervisor's username
     * @return list of orders
     */
    @EntityGraph(attributePaths = {"destinationLocation"})
    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN o.orderLines ol
        LEFT JOIN ol.task t
        LEFT JOIN t.supervisor u
        WHERE LOWER(o.createdBy) = LOWER(:username) OR LOWER(u.username) = LOWER(:username)
        """)
    List<Order> findAllAccessibleBySupervisor(@Param("username") String username);

    @EntityGraph(attributePaths = {"destinationLocation"})
    @Query(value = """
        SELECT DISTINCT o FROM Order o
        LEFT JOIN o.orderLines ol
        LEFT JOIN ol.task t
        LEFT JOIN t.supervisor u
        WHERE LOWER(o.createdBy) = LOWER(:username) OR LOWER(u.username) = LOWER(:username)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT o) FROM Order o
        LEFT JOIN o.orderLines ol
        LEFT JOIN ol.task t
        LEFT JOIN t.supervisor u
        WHERE LOWER(o.createdBy) = LOWER(:username) OR LOWER(u.username) = LOWER(:username)
        """)
    Page<Order> findAllAccessibleBySupervisor(@Param("username") String username, Pageable pageable);

   /**
     * Backward-compatible alias for findAllAccessibleBySupervisor.
     *
     * @param username the supervisor's username
     * @return list of orders
     */
    @EntityGraph(attributePaths = {"destinationLocation"})
    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN o.orderLines ol
        LEFT JOIN ol.task t
        LEFT JOIN t.supervisor u
        WHERE LOWER(o.createdBy) = LOWER(:username) OR LOWER(u.username) = LOWER(:username)
        """)
    List<Order> findAllByCreatedByUsername(@Param("username") String username);

    /**
     * Finds operator IDs assigned to orders in a single batch query.
     *
     * @param orderIds collection of order IDs
     * @return list of order-to-operator projections
     */
    @Query("""
        SELECT DISTINCT o.id AS orderId, u.id AS operatorId
        FROM Order o
        JOIN o.orderLines ol
        JOIN ol.task t
        JOIN t.operator u
        WHERE o.id IN :orderIds
    """)
    List<OrderOperatorProjection> findOperatorIdsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /**
     * Finds the oldest PICKED or PARTIALLY_COMPLETED order for an operator.
     * Used to continue picking after dispatch.
     *
     * @param operatorId the operator's user ID
     * @return an Optional containing the order, if any
     */
    @Query(value = """
        SELECT DISTINCT o.* FROM orders o
        JOIN order_lines ol ON o.id = ol.order_id
        JOIN tasks t ON ol.task_id = t.id
        WHERE t.operator_id = :operatorId
          AND o.status IN ('PICKED', 'PARTIALLY_COMPLETED')
        ORDER BY o.created_at, o.id
        LIMIT 1
    """, nativeQuery = true)
    Optional<Order> findOldestPickedOrderAssignedToOperator(
        @Param("operatorId") Long operatorId
    );

    /**
     * Finds the operator ID assigned to a given order.
     *
     * @param orderId the order ID
     * @return an Optional containing the operator ID, if assigned
     */
    @Query("""
            SELECT DISTINCT u.id FROM Order o
            JOIN o.orderLines ol
            JOIN ol.task t
            JOIN t.operator u
            WHERE o.id = :orderId
        """)
    Optional<Long> findOperatorIdByOrderId(@Param("orderId") Long orderId);

    /**
     * Bulk‑updates the status of a single order.
     *
     * @param orderId     the order ID
     * @param orderStatus the new status
     * @return the number of updated rows (0 or 1)
     */
    @Modifying
    @Query("""
            UPDATE Order o
                SET o.status = :orderStatus
                WHERE o.id = :orderId
        """)
    int updateStatus(
        @Param("orderId") Long orderId,
        @Param("orderStatus") OrderStatus orderStatus);

    /**
     * Finds the order associated with a given task.
     *
     * @param task the task
     * @return an Optional containing the order, if found
     */
    @Query("""
            SELECT o FROM Order o
            JOIN OrderLine ol ON ol.order = o
            WHERE ol.task = :task
        """)


    Optional<Order> getOrderByTask(
        @Param("task") Task task
    );

    /**
     * Deletes all terminal orders created before the cutoff date.
     * Only completed, partially completed, and canceled orders without remaining lines
     * are deleted to protect active orders and stock reservations.
     *
     * @param cutoffDate the cutoff date
     * @return the number of deleted orders
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM Order o
        WHERE o.createdAt < :cutoffDate
          AND o.status IN (
              com.isd.wms.enums.OrderStatus.COMPLETED,
              com.isd.wms.enums.OrderStatus.PARTIALLY_COMPLETED,
              com.isd.wms.enums.OrderStatus.CANCELED
          )
          AND NOT EXISTS (
              SELECT 1 FROM OrderLine ol WHERE ol.order = o
          )
        """)
    int deleteOrdersOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    Optional<Order> findByLogicId(String logicId);

    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE LOWER(o.logicId) = LOWER(:logicId)")
    boolean existsByLogicIdIgnoreCase(@Param("logicId") String logicId);

    /**
     * Checks whether a specific order is assigned to a given operator.
     *
     * @param order      the order
     * @param operatorId the operator's user ID
     * @return true if the order is assigned to the operator
     */
    @Query("""
            SELECT COUNT(o) > 0
            FROM Order o
            JOIN o.orderLines ol
            JOIN ol.task t
            WHERE t.operator.id = :operatorId
              AND o = :order
        """)
    boolean isOrderAssignedToOperator(
        @Param("order") Order order,
        @Param("operatorId") Long operatorId
    );

    /**
     * Finds the username of the operator assigned to a given order.
     *
     * @param order the order
     * @return an Optional containing the operator's username, if assigned
     */
    @Query("""
            SELECT u.username
            FROM Order o
            JOIN o.orderLines ol
            JOIN ol.task t
            JOIN t.operator u
                WHERE o = :order
        """)
    Optional<String> findOperatorUsernameByOrder(@Param("order") Order order);

    /**
     * Finds usernames of all supervisors who own/supervise tasks for a given order.
     *
     * @param order the order
     * @return list of distinct supervisor usernames
     */
    @Query("""
            SELECT DISTINCT u.username
            FROM Order o
            JOIN o.orderLines ol
            JOIN ol.task t
            JOIN t.supervisor u
            WHERE o = :order
        """)
    List<String> findSupervisorUsernamesByOrder(@Param("order") Order order);

    @Query("SELECT o FROM Order o WHERE LOWER(o.logicId) = LOWER(:logicId)")
    Optional<Order> findByLogicIdIgnoreCase(@Param("logicId") String logicId);
}
