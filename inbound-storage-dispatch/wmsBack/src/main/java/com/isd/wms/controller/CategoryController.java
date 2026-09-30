package com.isd.wms.controller;

import com.isd.wms.dto.category.CategoryCreateRequest;
import com.isd.wms.dto.category.CategoryResponse;
import com.isd.wms.dto.category.CategoryUpdateRequest;
import com.isd.wms.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for managing product categories.
 *
 * <p>Provides CRUD operations for categories as well as a bulk-import endpoint.
 * Write operations (create, update, delete, import) are restricted to users with
 * the {@code SUPERVISOR} or {@code DEV} role. Read operations are publicly accessible.</p>
 *
 * <p>Base path: {@code /api/categories}</p>
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Creates a new product category.
     *
     * @param request the category creation request; must be valid
     * @return {@code 201 Created} with the created {@link CategoryResponse}
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    /**
     * Retrieves all product categories.
     *
     * @return {@code 200 OK} with a list of all {@link CategoryResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    /**
     * Retrieves a single product category by its ID.
     *
     * @param id the ID of the category to retrieve
     * @return {@code 200 OK} with the {@link CategoryResponse} for the specified category
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    /**
     * Updates an existing product category.
     *
     * @param id      the ID of the category to update
     * @param request the update request containing the new category data; must be valid
     * @return {@code 200 OK} with the updated {@link CategoryResponse}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    /**
     * Deletes a product category by its ID.
     *
     * @param id the ID of the category to delete
     * @return {@code 204 No Content} on successful deletion
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Imports product categories in bulk from an uploaded file.
     *
     * @param file the multipart file containing category data to import
     * @return {@code 200 OK} with a confirmation message on success
     */
    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'DEV')")
    public ResponseEntity<String> importCategories(@RequestParam("file") MultipartFile file) {
        categoryService.importCategoriesFromFile(file);
        return ResponseEntity.ok("Categories were successfully imported.");
    }
}
