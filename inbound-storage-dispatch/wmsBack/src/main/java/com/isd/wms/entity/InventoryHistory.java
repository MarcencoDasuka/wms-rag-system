package com.isd.wms.entity;

import com.isd.wms.enums.InventoryAdjustmentReason;
import com.isd.wms.enums.InventoryOperationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a historical record of an inventory operation.
 * <p>
 * Tracks every change to stock, including additions, removals, adjustments, picks,
 * and moves. Records the product, location(s), quantity changes, operation type,
 * adjustment reason (if applicable), and the user who performed the operation.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Product} – the product affected</li>
 *   <li>{@link Location} – source and/or destination location</li>
 *   <li>{@link User} – the user who performed the operation</li>
 * </ul>
 * </p>
 *
 * @see InventoryOperationType
 * @see InventoryAdjustmentReason
 */
@Entity
@Table(name = "inventory_history")
@Getter
@Setter
@NoArgsConstructor
public class InventoryHistory{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inventory_history_seq")
    @SequenceGenerator(name = "inventory_history_seq", sequenceName = "inventory_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 100)
    private String barcode;

    @Column(name = "altered_quantity", nullable = false)
    private Integer alteredQuantity;

    @Column(name = "quantity_after_change", nullable = false)
    private Integer quantityAfterChange;

    @Column(name = "previous_quantity")
    private Integer previousQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id")
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private Location destinationLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private InventoryOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_reason", length = 50)
    private InventoryAdjustmentReason adjustmentReason;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public InventoryHistory(Product product, String barcode, Integer alteredQuantity, Integer quantityAfterChange, Integer previousQuantity, Location sourceLocation, Location destinationLocation, InventoryOperationType operationType, InventoryAdjustmentReason adjustmentReason, String comment, User user) {
        this.product = product;
        this.barcode = barcode;
        this.alteredQuantity = alteredQuantity;
        this.quantityAfterChange = quantityAfterChange;
        this.previousQuantity = previousQuantity;
        this.sourceLocation = sourceLocation;
        this.destinationLocation = destinationLocation;
        this.operationType = operationType;
        this.adjustmentReason = adjustmentReason;
        this.comment = comment;
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        InventoryHistory that = (InventoryHistory) o;
        return id != null && Objects.equals(id, that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
