package com.isd.wms.service;

import com.isd.wms.dto.category.CategoryCreateRequest;
import com.isd.wms.dto.category.CategoryResponse;
import com.isd.wms.dto.category.CategoryUpdateRequest;
import com.isd.wms.entity.Category;
import com.isd.wms.exception.CategoryInUseException;
import com.isd.wms.exception.CategoryNotFoundException;
import com.isd.wms.exception.DuplicateCategoryNameException;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.CategoryMapper;
import com.isd.wms.repository.CategoryRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.imports.dto.CategoryInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

/**
 * Service for managing product categories.
 * <p>
 * Provides operations for creating, updating, deleting, and retrieving categories.
 * Ensures unique category names (case‑insensitive) and prevents deletion of categories
 * that are currently referenced by products.
 * </p>
 * <p>
 * All write operations are transactional. Category names are trimmed and validated
 * to be non‑blank.
 * </p>
 *
 * @see Category
 * @see CategoryRepository
 * @see ProductRepository
 * @see CategoryMapper
 * @see ImportService
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final ImportService importService;

    /**
     * Creates a new category.
     *
     * @param request the creation request containing the category name
     * @return the created category as a response DTO
     * @throws InvalidRequestException if the name is null or blank
     * @throws DuplicateCategoryNameException if a category with the same name (case‑insensitive) already exists
     */
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        String name = validateAndNormalizeName(request.name());
        verifyUniqueName(name, null);
        return categoryMapper.toResponse(saveCategory(new Category(name), name));
    }

    /**
     * Updates an existing category's name.
     *
     * @param categoryId the ID of the category to update
     * @param request the update request with the new name
     * @return the updated category response
     * @throws CategoryNotFoundException if no category exists with the given ID
     * @throws DuplicateCategoryNameException if the new name conflicts with another category
     */
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryUpdateRequest request) {
        Category category = getCategory(categoryId);
        String name = validateAndNormalizeName(request.name());
        verifyUniqueName(name, categoryId);
        category.setName(name);
        return categoryMapper.toResponse(saveCategory(category, name));
    }

    /**
     * Deletes a category if it is not referenced by any product.
     *
     * @param categoryId the ID of the category to delete
     * @throws CategoryNotFoundException if the category does not exist
     * @throws CategoryInUseException if products are associated with this category
     */
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = getCategory(categoryId);
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new CategoryInUseException(categoryId);
        }
        categoryRepository.delete(category);
    }

    public CategoryResponse getCategoryById(Long categoryId) {
        return categoryMapper.toResponse(getCategory(categoryId));
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private String validateAndNormalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Category name is required");
        }
        return name.trim();
    }

    private void verifyUniqueName(String name, Long categoryId) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryNameException(name);
        };
    }

    private Category saveCategory(Category category, String name) {
        try {
            return categoryRepository.save(category);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateCategoryNameException(name);
        }
    }

    @Transactional
    public void importCategoriesFromFile(MultipartFile file) {
        List<Category> categories = importService.importData(file, CategoryInfo.class);
        try {
            categoryRepository.saveAllAndFlush(categories);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidRequestException("The imported file contains invalid category data.");
        }
    }
}
