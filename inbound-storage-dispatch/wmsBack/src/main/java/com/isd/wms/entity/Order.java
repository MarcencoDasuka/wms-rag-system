package com.isd.wms.entity;

import com.isd.wms.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a customer order.
 * <p>
 * An order contains one or more order lines ({@link OrderLine}) and is destined
 * for a dispatch location. It progresses through a lifecycle of statuses
 * (CREATED, ASSIGNED, IN_PROGRESS, PICKED, COMPLETED, etc.). Each order has a
 * unique logical ID (e.g., "ORD-123").
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link OrderLine} – one‑to‑many, the lines of this order</li>
 *   <li>{@link Location} – the destination dispatch location</li>
 * </ul>
 * </p>
 *
 * @see OrderStatus
 * @see OrderLine
 * @see Location
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "orders")
public class Order extends BaseTimestampEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_gen")
    @SequenceGenerator(name = "order_gen", sequenceName = "orders_sequence", allocationSize = 1)
    private Long id;

    public Order(String logicId) {
        this.logicId = logicId;
    }

    @Column(name = "logic_id", nullable = false, unique = true)
    private String logicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Location destinationLocation;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> orderLines = new ArrayList<>();

    public Order(String logicId, Location destinationLocation) {
        this.logicId = logicId;
        this.destinationLocation = destinationLocation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Order order = (Order) o;
        return id != null && Objects.equals(id, order.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
