package com.isd.wms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the inventory of a specific product at a specific location.
 * <p>
 * Tracks the total physical quantity, the quantity reserved for active tasks,
 * and optional manufacture and expiration dates. The available quantity is
 * the difference between total and reserved. Stock records are versioned
 * for optimistic locking.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Product} – the product in stock</li>
 *   <li>{@link Location} – the location where the stock resides</li>
 *   <li>{@link Allocation} – one‑to‑many, allocations that reserve from this stock</li>
 * </ul>
 * </p>
 *
 * @see Product
 * @see Location
 * @see Allocation
 */
@Entity
@Table(name = "stocks")
@Getter
@Setter
@NoArgsConstructor
public class Stock extends BaseTimestampEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_seq")
    @SequenceGenerator(name = "stock_seq", sequenceName = "stocks_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @Getter(AccessLevel.NONE)
    private Product product;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Min(0)
    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(nullable = false)
    private Boolean available = Boolean.TRUE;

    @Column(name = "quantity_reserved", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Version
    private Long version;

    public Stock(Product product, Location location) {
        this.product = product;
        this.location = location;
    }

    public Stock(Product product, Location location, Integer quantity,
                 LocalDate manufactureDate, LocalDate expirationDate) {
        this.product = product;
        this.location = location;
        this.quantity = quantity;
        this.manufactureDate = manufactureDate;
        this.expirationDate = expirationDate;
    }

    public Stock(Product product, Location location, Integer quantity, Integer reservedQuantity,
                 LocalDate manufactureDate, LocalDate expirationDate) {
        this.product = product;
        this.location = location;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.manufactureDate = manufactureDate;
        this.expirationDate = expirationDate;
    }

    public Stock(Long id, Product product, Location location, Integer quantity, Integer reservedQuantity,
                 LocalDate manufactureDate, LocalDate expirationDate, Long version) {
        this.id = id;
        this.product = product;
        this.location = location;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.manufactureDate = manufactureDate;
        this.expirationDate = expirationDate;
        this.version = version;
    }

    /**
     * Returns the product if present; stock may be associated with a product.
     *
     * @return an Optional containing the product, or empty if none
     */
    public Optional<Product> getProduct() {
        return Optional.ofNullable(product);
    }

    /**
     * Removes a given quantity from both the total and reserved quantities.
     * Used when stock is actually picked or moved.
     *
     * @param quantityToMove the quantity to remove
     */
    public void removeQuantity(int quantityToMove) {
        this.quantity -= quantityToMove;
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantityToMove);
    }

    /**
     * Adds a quantity to the total stock.
     *
     * @param quantity the quantity to add
     */
    public void addQuantity(int quantity) {
        this.quantity += quantity;
    }

    /**
     * Computes the currently available (unreserved) quantity.
     *
     * @return available quantity
     */
    public int getAvailableQuantity() {return this.quantity - this.reservedQuantity;}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Stock stock = (Stock) o;
        return id != null && Objects.equals(id, stock.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Updates the manufacture and expiration dates, keeping the latter of the two
     * for each date (i.e., preserves the newest dates).
     *
     * @param newManufactureDate the new manufacture date
     * @param newExpirationDate  the new expiration date
     */
    public void updateDate(LocalDate newManufactureDate, LocalDate newExpirationDate) {
        if (newManufactureDate != null) {
            if (this.manufactureDate == null || newManufactureDate.isAfter(this.manufactureDate)) {
                this.manufactureDate = newManufactureDate;
            }
        }

        if (newExpirationDate != null) {
            if (this.expirationDate == null || newExpirationDate.isAfter(this.expirationDate)) {
                this.expirationDate = newExpirationDate;
            }
        }
    }
}
