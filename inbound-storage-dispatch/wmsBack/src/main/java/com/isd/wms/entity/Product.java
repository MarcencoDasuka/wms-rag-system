package com.isd.wms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a product managed in the warehouse.
 * <p>
 * Each product has a unique barcode, a name, an optional description, and belongs
 * to a category. It also contains auto‑replenishment settings: whether automatic
 * replenishment is enabled, the minimum threshold for stock at picking locations,
 * and the replenishment quantity.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Category} – many‑to‑one, the product's category</li>
 *   <li>{@link Stock} – one‑to‑many, stock records for this product</li>
 * </ul>
 * </p>
 *
 * @see Category
 * @see Stock
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "products")
public class Product extends BaseTimestampEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "products_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100, unique = true)
    private String barcode;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "auto_replenish", nullable = false)
    private Boolean autoReplenish = false;

    @Column(name = "min_threshold")
    private Integer minThreshold;

    @Column(name = "replenish_qty")
    private Integer replenishQty;

    /**
     * Returns the minimum threshold as an Optional.
     *
     * @return the threshold if set, otherwise empty
     */
    public Optional<Integer> getMinThreshold() {
        return Optional.ofNullable(this.minThreshold);
    }

    /**
     * Returns the replenish quantity as an Optional.
     *
     * @return the replenish quantity if set, otherwise empty
     */
    public Optional<Integer> getReplenishQty() {
        return Optional.ofNullable(this.replenishQty);
    }
    public Product(String name, String barcode, String description, Category category) {
        this.name = name;
        this.barcode = barcode;
        this.description = description;
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Product product = (Product) o;
        return id != null && Objects.equals(id, product.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
