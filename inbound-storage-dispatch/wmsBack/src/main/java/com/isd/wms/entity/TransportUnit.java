package com.isd.wms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;

/**
 * Represents a physical transport unit (e.g., pallet, cart) used to move goods.
 * <p>
 * Each transport unit has a unique barcode and can be associated with either
 * an order (for picking) or a replenishment (for moving stock). It can be
 * "occupied" by linking to one of these processes, and "released" when the
 * process is complete.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Order} – optional order that this TU is assigned to</li>
 *   <li>{@link Replenishment} – optional replenishment that this TU is assigned to</li>
 * </ul>
 * </p>
 *
 * @see Order
 * @see Replenishment
 */
@Entity
@Table(name = "transport_units")
@Getter
@Setter
@NoArgsConstructor
public class TransportUnit extends BaseTimestampEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transport_units_seq")
    @SequenceGenerator(name = "transport_units_seq", sequenceName = "transport_units_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replenishment_id")
    private Replenishment replenishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportUnit that = (TransportUnit) o;
        return id != null && Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
