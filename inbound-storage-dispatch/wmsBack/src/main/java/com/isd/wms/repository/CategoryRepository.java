package com.isd.wms.repository;

import com.isd.wms.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link Category} entities.
 * <p>
 * Provides basic data access methods, including case‑insensitive checks
 * for uniqueness and lookup by name.
 * </p>
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Checks whether a category with the given name (case‑insensitive) exists.
     *
     * @param name the category name
     * @return true if exists
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Finds a category by its name (case‑insensitive).
     *
     * @param name the category name
     * @return an Optional containing the category, if found
     */
    Optional<Category> findByNameIgnoreCase(String name);
}
