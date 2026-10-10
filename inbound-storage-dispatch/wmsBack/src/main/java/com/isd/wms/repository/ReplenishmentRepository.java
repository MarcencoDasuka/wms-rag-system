package com.isd.wms.repository;

import com.isd.wms.entity.Replenishment;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.Status;
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
 * Repository for {@link Replenishment} entities.
 * <p>
 * Provides methods for finding replenishments by status, task, and various
 * filter criteria. Also supports bulk status updates, existence checks,
 * and cleanup of old replenishments.
 * </p>
 */
@Repository
public interface ReplenishmentRepository extends JpaRepository<Replenishment, Long> {

    @Override
    @EntityGraph(attributePaths = {"destinationLocation", "product", "task", "task.operator"})
    List<Replenishment> findAll();

    /**
     * Finds all replenishments with a given status.
     *
     * @param status the status
     * @return list of replenishments
     */
    List<Replenishment> findByStatus(Status status);

    /**
     * Finds the replenishment associated with a given task.
     *
     * @param taskId the task ID
     * @return an Optional containing the replenishment, if found
     */
    Optional<Replenishment> findByTaskId(Long taskId);

    /**
     * Filters replenishments by optional criteria.
     *
     * @param taskId                task ID
     * @param productId             product ID
     * @param requestedQuantity     requested quantity
     * @param status                status
     * @param destinationLocationId destination location ID
     * @return list of matching replenishments
     */
    @EntityGraph(attributePaths = {"destinationLocation", "product", "task", "task.operator"})
    @Query("""
        SELECT r FROM Replenishment r
        LEFT JOIN r.task t
        WHERE (:taskId IS NULL OR r.task.id = :taskId)
        AND (:productId IS NULL OR r.product.id = :productId)
        AND (:requestedQuantity IS NULL OR r.requestedQuantity = :requestedQuantity)
        AND (:status IS NULL OR r.status = :status)
        AND (:destinationLocationId IS NULL OR r.destinationLocation.id = :destinationLocationId)
        """)
    List<Replenishment> filter(
        @Param("taskId") Long taskId,
        @Param("productId") Long productId,
        @Param("requestedQuantity") Integer requestedQuantity,
        @Param("status") Status status,
        @Param("destinationLocationId") Long destinationLocationId
    );

    /**
     * Checks whether there is an active replenishment (status in given collection)
     * for a product and destination location.
     *
     * @param productId          the product ID
     * @param destinationLocationId the destination location ID
     * @param statuses           allowed statuses (e.g., CREATED, ASSIGNED, IN_PROGRESS)
     * @return true if exists
     */
    boolean existsByProductIdAndDestinationLocationIdAndStatusIn(
        Long productId, Long destinationLocationId, Collection<Status> statuses
    );

    /**
     * Checks whether there is an active replenishment targeting the specified destination location.
     *
     * @param destinationLocationId the destination location ID
     * @param statuses              allowed statuses (e.g., CREATED, ASSIGNED, IN_PROGRESS)
     * @return true if exists
     */
    boolean existsByDestinationLocationIdAndStatusIn(
        Long destinationLocationId, Collection<Status> statuses
    );

    /**
     * Updates the replenishment status to COMPLETED when its task is completed.
     *
     * @param task the completed task
     */
    @Modifying
    @Query("""
            UPDATE Replenishment r
                SET r.status = com.isd.wms.enums.Status.COMPLETED
                WHERE r.task = :task
        """)
    void updateReplenishmentStatusByTask(
        @Param("task") Task task
    );

    /**
     * Deletes all terminal replenishments whose task was created before the cutoff date.
     * Only completed, partially completed, and canceled replenishments are deleted.
     *
     * @param cutoffDate the cutoff date
     * @return the number of deleted replenishments
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        DELETE FROM Replenishment r
        WHERE r.task.createdAt < :cutoffDate
          AND r.status IN (
              com.isd.wms.enums.Status.COMPLETED,
              com.isd.wms.enums.Status.PARTIALLY_COMPLETED,
              com.isd.wms.enums.Status.CANCELED
          )
        """)
    int deleteReplenishmentsByTaskCreatedAtOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Finds all replenishments accessible by a supervisor: either created by the supervisor
     * or having a task supervised by the supervisor.
     *
     * @param username the supervisor's username
     * @return list of replenishments
     */
    @EntityGraph(attributePaths = {"destinationLocation", "product", "task", "task.operator"})
    @Query("""
        SELECT r FROM Replenishment r
        LEFT JOIN r.task t
        LEFT JOIN t.supervisor u
        WHERE r.status = com.isd.wms.enums.Status.CREATED
           OR r.task IS NULL
           OR t.supervisor IS NULL
           OR r.createdBy IS NULL
           OR LOWER(r.createdBy) = 'system'
           OR LOWER(r.createdBy) = LOWER(:username)
           OR LOWER(u.username) = LOWER(:username)
        """)
    List<Replenishment> findAllAccessibleBySupervisor(@Param("username") String username);

    /**
     * Backward-compatible alias for findAllAccessibleBySupervisor.
     *
     * @param username the supervisor's username
     * @return list of replenishments
     */
    @EntityGraph(attributePaths = {"destinationLocation", "product", "task", "task.operator"})
    @Query("""
        SELECT r FROM Replenishment r
        LEFT JOIN r.task t
        LEFT JOIN t.supervisor u
        WHERE r.status = com.isd.wms.enums.Status.CREATED
           OR r.task IS NULL
           OR t.supervisor IS NULL
           OR r.createdBy IS NULL
           OR LOWER(r.createdBy) = 'system'
           OR LOWER(r.createdBy) = LOWER(:username)
           OR LOWER(u.username) = LOWER(:username)
        """)
    List<Replenishment> findAllByCreatedByUsername(@Param("username") String username);

    /**
     * Filters replenishments by optional criteria.
     *
     * @param id                    replenishment ID (NEW)
     * @param taskId                task ID
     * @param productId             product ID
     * @param requestedQuantity     requested quantity
     * @param status                status
     * @param destinationLocationId destination location ID
     * @return list of matching replenishments
     */
    @EntityGraph(attributePaths = {"destinationLocation", "product", "task", "task.operator"})
    @Query("""
        SELECT r FROM Replenishment r
        LEFT JOIN r.task t
        WHERE (:id IS NULL OR r.id = :id)
        AND (:taskId IS NULL OR r.task.id = :taskId)
        AND (:productId IS NULL OR r.product.id = :productId)
        AND (:requestedQuantity IS NULL OR r.requestedQuantity = :requestedQuantity)
        AND (:status IS NULL OR r.status = :status)
        AND (:destinationLocationId IS NULL OR r.destinationLocation.id = :destinationLocationId)
        """)
    List<Replenishment> filter(
        @Param("id") Long id,
        @Param("taskId") Long taskId,
        @Param("productId") Long productId,
        @Param("requestedQuantity") Integer requestedQuantity,
        @Param("status") Status status,
        @Param("destinationLocationId") Long destinationLocationId
    );

    @Query("SELECT r FROM Replenishment r WHERE LOWER(r.logicId) = LOWER(:logicId)")
    Optional<Replenishment> findByLogicIdIgnoreCase(@Param("logicId") String logicId);

    Optional<Replenishment> findByLogicId(String logicId);

    @Query("SELECT COUNT(r) > 0 FROM Replenishment r WHERE LOWER(r.logicId) = LOWER(:logicId)")
    boolean existsByLogicIdIgnoreCase(@Param("logicId") String logicId);

}

