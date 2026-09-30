package com.isd.wms.controller;

import com.isd.wms.dto.product.ProductCreateRequest;
import com.isd.wms.dto.product.ProductResponse;
import com.isd.wms.dto.product.ProductUpdateRequest;
import com.isd.wms.enums.Zone;
import com.isd.wms.repository.projections.ProductWithQuantityProjection;
import com.isd.wms.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for managing warehouse products.
 *
 * <p>Provides CRUD operations for products, along with search, quantity projection,
 * and bulk-import endpoints. Write operations (create, update, delete, import) are
 * restricted to users with the {@code SUPERVISOR} or {@code DEV} role. Read and
 * search operations are publicly accessible.</p>
 *
 * <p>Base path: {@code /api/products}</p>
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    /**
     * Creates a new product.
     *
     * @param request the product creation request; must be valid
     * @return {@code 201 Created} with the created {@link ProductResponse}
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        log.info("Create product request: name={}, categoryId={}", request.name(), request.categoryId());
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    /**
     * Retrieves all products.
     *
     * @return {@code 200 OK} with a list of all {@link ProductResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        log.info("Get all products request");
        return ResponseEntity.ok(productService.getAllProducts());
    }

    /**
     * Retrieves all products with their current stock quantities for the specified warehouse zone.
     *
     * @param zone the warehouse zone to filter quantities by
     * @return {@code 200 OK} with a list of {@link ProductWithQuantityProjection}
     * objects including quantity data
     */
    @GetMapping("/quantities")
    public ResponseEntity<List<ProductWithQuantityProjection>> getAllProductsWithQuantity(@RequestParam Zone zone) {
        return ResponseEntity.ok(productService.getAllProductsWithQuantity(zone));
    }

    /**
     * Retrieves a single product by its ID.
     *
     * @param id the ID of the product to retrieve
     * @return {@code 200 OK} with the {@link ProductResponse} for the specified product
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        log.info("Get product by id request: productId={}", id);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * Updates an existing product.
     *
     * @param id      the ID of the product to update
     * @param request the update request containing the new product data; must be valid
     * @return {@code 200 OK} with the updated {@link ProductResponse}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        log.info("Update product request: productId={}, name={}, categoryId={}", id, request.name(), request.categoryId());
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    /**
     * Deletes a product by its ID.
     *
     * @param id the ID of the product to delete
     * @return {@code 204 No Content} on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("Delete product request: productId={}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Searches for products by name and/or category.
     *
     * <p>Both parameters are optional; omitting them returns all products.</p>
     *
     * @param name       the name (or partial name) to search for, or {@code null} to skip name filtering
     * @param categoryId the ID of the category to filter by, or {@code null} to skip category filtering
     * @return {@code 200 OK} with a list of matching {@link ProductResponse} objects
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) Long categoryId
    ) {
        log.info("Search products request: name={}, categoryId={}", name, categoryId);
        return ResponseEntity.ok(productService.searchProducts(name, categoryId));
    }

    /**
     * Imports products in bulk from an uploaded file.
     *
     * @param file the multipart file containing product data to import
     * @return {@code 200 OK} with a confirmation message on success
     */
    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> importProducts(@RequestParam("file") MultipartFile file) {
        productService.importProductsFromFile(file);
        return ResponseEntity.ok("Products were successfully imported.");
    }
}
