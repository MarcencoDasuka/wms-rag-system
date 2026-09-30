package com.isd.wms.entity;

import com.isd.wms.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single allocation of stock to a task.
 * <p>
 * An allocation reserves a specific quantity of a product from a particular stock
 * location for a given task (either order picking or replenishment). It tracks
 * the status of the allocation (CREATED, ASSIGNED, IN_PROGRESS, etc.), whether
 * the source location and product have been scanned during picking, and optionally
 * a picked quantity (for partial picks).
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Task} – the task that this allocation belongs to</li>
 *   <li>{@link Stock} – the stock location from which the product is reserved</li>
 * </ul>
 * </p>
 *
 * @see Task
 * @see Stock
 * @see Status
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "allocations")
public class Allocation extends BaseTimestampEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "allocation_gen")
    @SequenceGenerator(name = "allocation_gen", sequenceName = "allocations_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private Integer quantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "source_location_scanned", nullable = false)
    private boolean sourceLocationScanned = false;

    @Column(name = "product_scanned", nullable = false)
    private boolean productScanned = false;

    @Column(name = "picked_quantity")
    private Integer pickedQuantity;

    /**
     * Returns the picked quantity as an {@link Optional}.
     * If no pick was performed (e.g., allocation is still pending), the Optional is empty.
     *
     * @return the picked quantity if set, otherwise empty
     */
    public Optional<Integer> getPickedQuantity() {
        return Optional.ofNullable(pickedQuantity);
    }

    public Allocation(Task task, Stock stock, Integer quantity, Status status) {
        this.task = task;
        this.stock = stock;
        this.quantity = quantity;
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Allocation allocation = (Allocation) o;
        return id != null && Objects.equals(id, allocation.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
