package com.isd.wms.repository;

import com.isd.wms.entity.Product;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.projections.ProductWithQuantityProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Product} entities.
 * <p>
 * Provides search capabilities by name and category, checks for unique barcode,
 * and projections that aggregate stock quantities by product per zone.
 * </p>
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Searches products by name (partial, case‑insensitive) and/or category ID.
     *
     * @param name       product name (optional)
     * @param categoryId category ID (optional)
     * @return list of matching products
     */
    @Query("""
        select product from Product product
        where (:name is null or lower(product.name) like lower(concat('%', :name, '%')))
          and (:categoryId is null or product.category.id = :categoryId)
        """)
    List<Product> search(@Param("name") String name, @Param("categoryId") Long categoryId);

    /**
     * Checks whether any product belongs to the given category.
     *
     * @param categoryId the category ID
     * @return true if there is at least one product in the category
     */
    boolean existsByCategoryId(Long categoryId);

    /**
     * Retrieves products with aggregated available quantities in a specific zone.
     * Only returns products with positive total available quantity.
     *
     * @param zone the zone (e.g., PICKING)
     * @return list of projections containing product details and total quantity
     */
    @Query("""
        SELECT p.id AS id, p.name AS name, p.barcode AS barcode, SUM(s.quantity - s.reservedQuantity) AS quantity FROM Product p
        JOIN Stock s ON p = s.product
        JOIN Location l ON l = s.location
        WHERE l.zone = :zone AND l.available = true
        GROUP BY p.id, p.name, p.barcode
        HAVING SUM(s.quantity - s.reservedQuantity) > 0
        """)
    List<ProductWithQuantityProjection> getProductsWithQuantities(
        @Param("zone") Zone zone
    );

    /**
     * Checks whether a product with the given barcode exists (case‑insensitive).
     *
     * @param barcode the barcode
     * @return true if exists
     */
    boolean existsByBarcodeIgnoreCase(String barcode);

    /**
     * Checks whether a product with the given barcode exists, excluding a specific ID.
     * Used for update uniqueness validation.
     *
     * @param barcode the barcode
     * @param id      the product ID to exclude
     * @return true if another product with the same barcode exists
     */
    boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);

    /**
     * Finds a product ID by its exact name.
     *
     * @param name the product name
     * @return an Optional containing the product ID, if found
     */
    @Query("""
            SELECT p.id FROM Product p
            WHERE p.name = :name
        """)
    Optional<Long> findProductIdByName(
        @Param("name") String name
    );

    /**
     * Finds a product by its exact barcode.
     *
     * @param barcode the barcode
     * @return an Optional containing the product, if found
     */
    Optional<Product> findByBarcode(String barcode);
}
