package com.isd.wms.repository;

import com.isd.wms.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository for {@link Task} entities.
 * <p>
 * Provides bulk update methods for assigning operators to tasks by order,
 * marking tasks as completed when all allocations are finished, and
 * cleaning up old tasks.
 * </p>
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Updates the operator for all tasks belonging to a given order.
     *
     * @param orderId    the order ID
     * @param operatorId the new operator ID
     * @return the number of updated tasks
     */
    @Modifying
    @Query(value = """
            WITH t_by_o AS (
                SELECT DISTINCT t.id FROM tasks t
                JOIN order_lines ol ON ol.task_id = t.id
                WHERE ol.order_id = :orderId)

            UPDATE tasks t
                SET operator_id = :operatorId
                WHERE id = ANY(SELECT id FROM t_by_o)
        """, nativeQuery = true)
    int updateOperatorByOrderId(
        @Param("orderId") Long orderId,
        @Param("operatorId") Long operatorId);

    /**
     * Marks a task as COMPLETED if it has no remaining active allocations
     * (i.e., all allocations are CANCELED, COMPLETED, or PARTIALLY_COMPLETED).
     *
     * @param taskId the task ID
     * @return the number of updated rows (0 or 1)
     */
    @Modifying
    @Query("""
             UPDATE Task t
             SET t.status = com.isd.wms.enums.TaskStatus.COMPLETED
             WHERE t.id = :taskId
               AND NOT EXISTS (
                   SELECT 1 FROM Allocation a
                   WHERE a.task = t
                     AND a.status NOT IN (
                             com.isd.wms.enums.Status.CANCELED,
                             com.isd.wms.enums.Status.COMPLETED,
                             com.isd.wms.enums.Status.PARTIALLY_COMPLETED)
               )
        \s""")
    int markTaskAsCompleted(@Param("taskId") Long taskId);

    /**
     * Deletes all tasks created before the cutoff date.
     *
     * @param cutoffDate the cutoff date
     * @return the number of deleted tasks
     */
    @Modifying
    @Query("DELETE FROM Task t WHERE t.createdAt < :cutoffDate")
    int deleteTasksOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}
