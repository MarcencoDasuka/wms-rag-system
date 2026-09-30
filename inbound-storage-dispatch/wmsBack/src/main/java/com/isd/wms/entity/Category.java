package com.isd.wms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a product category.
 * <p>
 * Categories group products for organizational and reporting purposes.
 * Each category has a unique name and may contain multiple products.
 * </p>
 * <p>
 * Relationships:
 * <ul>
 *   <li>{@link Product} – one‑to‑many relationship (a category can have many products)</li>
 * </ul>
 * </p>
 *
 * @see Product
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "categories")
public class Category{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "category_seq")
    @SequenceGenerator(name = "category_seq", sequenceName = "categories_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @OneToMany(mappedBy = "category")
    private List<Product> products = new ArrayList<>();

    public Category(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Category category = (Category) o;
        return id != null && Objects.equals(id, category.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
