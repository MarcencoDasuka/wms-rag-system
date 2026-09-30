package com.isd.wms.entity;

import com.isd.wms.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a replenishment task to move stock from a bulk storage zone
 * (REPLENISHMENT) to a picking zone.
 * <p>
 * A replenishment specifies a product, a requested quantity, and a destination
 * location. It is linked to a {@link Task} that handles the actual movement.
 * The replenishment has its own lifecycle status (CREATED, ASSIGNED, etc.).
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Product} – the product to be replenished</li>
 *   <li>{@link Location} – the destination picking location</li>
 *   <li>{@link Task} – optional task that executes the replenishment</li>
 * </ul>
 * </p>
 *
 * @see Task
 * @see Product
 * @see Location
 * @see Status
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "replenishments")
public class Replenishment extends BaseTimestampEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "replenishment_seq")
    @SequenceGenerator(name = "replenishment_seq", sequenceName = "replenishments_sequence", allocationSize = 1)
    private Long id;

    @Column(name = "logic_id", unique = true, length = 50)
    private String logicId;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "task_id", referencedColumnName = "id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @NonNull
    private Integer requestedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @NonNull
    private Status status = Status.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private Location destinationLocation;

    public Replenishment(Product product, Integer requestedQuantity, Location destinationLocation) {
        this.product = product;
        this.requestedQuantity = requestedQuantity;
        this.destinationLocation = destinationLocation;
    }

    /**
     * Returns the associated task, if any.
     *
     * @return an Optional containing the task, or empty if not assigned
     */
    public Optional<Task> getTask() {
        return Optional.ofNullable(task);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Replenishment that = (Replenishment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
