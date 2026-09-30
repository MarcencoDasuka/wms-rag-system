package com.isd.wms.repository;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
    @Query("""
        SELECT o FROM Order o
        JOIN OrderLine ol ON ol.order = o
        JOIN Task t ON t = ol.task
        JOIN User u ON u = t.supervisor
        WHERE (:logicId IS NULL OR o.logicId = :logicId)
        AND (:destinationId IS NULL OR o.destinationLocation.id = :destinationId)
        AND (:status IS NULL OR o.status = :status)
        AND (:createdAt IS NULL OR o.createdAt = :createdAt)
        AND (:updatedAt IS NULL OR o.updatedAt = :updatedAt)
        """)
    List<Order> filter(
        @Param("logicId") String logicId,
        @Param("destinationId") Long destinationId,
        @Param("status") OrderStatus status,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("updatedAt") LocalDateTime updatedAt
    );

   /**
     * Finds all orders created by a specific supervisor (by username).
     *
     * @param username the supervisor's username
     * @return list of orders
     */
    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN OrderLine ol ON ol.order = o
        JOIN Task t ON t = ol.task
        JOIN User u ON u = t.supervisor
        WHERE u.username = :username
        """)
    List<Order> findAllByCreatedByUsername(@Param("username") String username);

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
     * Deletes all orders created before the cutoff date.
     *
     * @param cutoffDate the cutoff date
     * @return the number of deleted orders
     */
    @Modifying
    @Query("DELETE FROM Order o WHERE o.createdAt < :cutoffDate")
    int deleteOrdersOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    Optional<Order> findByLogicId(String logicId);

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

    Optional<Order> findByLogicIdIgnoreCase(String logicId);
}
