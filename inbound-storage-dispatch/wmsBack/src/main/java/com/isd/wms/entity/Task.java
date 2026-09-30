package com.isd.wms.entity;

import com.isd.wms.enums.TaskStatus;
import com.isd.wms.enums.TaskType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a unit of work to be performed by an operator.
 * <p>
 * Tasks can be of type {@link TaskType#PICKING_ORDER} or {@link TaskType#REPLENISHMENT}.
 * They are created by a supervisor, optionally assigned to an operator, and track
 * requested quantity, completion timestamp, and status. Each task contains a list
 * of {@link Allocation}s that reserve the required stock.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link User} – supervisor who created the task</li>
 *   <li>{@link User} – operator assigned to the task (optional)</li>
 *   <li>{@link Allocation} – one‑to‑many, the allocations for this task</li>
 * </ul>
 * </p>
 *
 * @see TaskType
 * @see TaskStatus
 * @see Allocation
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "tasks")
public class Task extends BaseTimestampEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "task_gen")
    @SequenceGenerator(name = "task_gen", sequenceName = "tasks_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.CREATED;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Allocation> allocations = new ArrayList<>();

    /**
     * Returns the assigned operator, if any.
     *
     * @return an Optional containing the operator, or empty if not assigned
     */
    public Optional<User> getOperator() {
        return Optional.ofNullable(operator);
    }

    public Task(User supervisor, TaskType taskType, Integer requestedQuantity) {
        this.supervisor = supervisor;
        this.taskType = taskType;
        this.requestedQuantity = requestedQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Task task = (Task) o;
        return id != null && Objects.equals(id, task.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
