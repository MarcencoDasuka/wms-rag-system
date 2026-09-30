package com.isd.wms.entity;

import com.isd.wms.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a line item within an order.
 * <p>
 * Specifies the product, requested quantity, and tracks delivered quantity,
 * shortage quantity, and status. Each order line is optionally linked to a
 * {@link Task} that handles the picking of this line.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Order} – the parent order</li>
 *   <li>{@link Product} – the product to be picked</li>
 *   <li>{@link Task} – optional task responsible for fulfilling this line</li>
 * </ul>
 * </p>
 *
 * @see Order
 * @see Product
 * @see Task
 * @see Status
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "order_lines")
public class OrderLine extends BaseTimestampEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "line_gen")
    @SequenceGenerator(name = "line_gen", sequenceName = "order_lines_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "task_id", referencedColumnName = "id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "delivered_quantity", nullable = false)
    private Integer deliveredQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.CREATED;

    @Column(name = "shortage_quantity", nullable = false)
    private Integer shortageQuantity = 0;

    /**
     * Returns the associated task, if any.
     * An order line may or may not have a task (e.g., before assignment).
     *
     * @return an Optional containing the task, or empty if not assigned
     */
    public Optional<Task> getTask() {
        return Optional.ofNullable(task);
    }

    public OrderLine(Order order, Task task, Product product, Integer requestedQuantity) {
        this.order = order;
        this.task = task;
        this.product = product;
        this.requestedQuantity = requestedQuantity;
    }

    public OrderLine(Order order, Product product, Integer requestedQuantity) {
        this.order = order;
        this.product = product;
        this.requestedQuantity = requestedQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        OrderLine orderLine = (OrderLine) o;
        return id != null && Objects.equals(id, orderLine.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
