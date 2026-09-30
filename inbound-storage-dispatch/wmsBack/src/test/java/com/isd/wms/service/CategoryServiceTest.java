package com.isd.wms.service;

import com.isd.wms.dto.category.CategoryCreateRequest;
import com.isd.wms.dto.category.CategoryResponse;
import com.isd.wms.dto.category.CategoryUpdateRequest;
import com.isd.wms.entity.Category;
import com.isd.wms.exception.CategoryInUseException;
import com.isd.wms.exception.DuplicateCategoryNameException;
import com.isd.wms.mapper.CategoryMapper;
import com.isd.wms.repository.CategoryRepository;
import com.isd.wms.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;

    @Spy private CategoryMapper categoryMapper = new CategoryMapper();

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createsCategorySuccessfully() {
        when(categoryRepository.findByNameIgnoreCase("Dairy")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(new CategoryCreateRequest("Dairy"));

        assertThat(response.name()).isEqualTo("Dairy");
    }

    @Test
    void rejectsDuplicateCategory() {
        when(categoryRepository.findByNameIgnoreCase("Dairy")).thenReturn(Optional.of(category(1L, "Dairy")));

        assertThatThrownBy(() -> categoryService.createCategory(new CategoryCreateRequest("Dairy")))
            .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    void updatesCategorySuccessfully() {
        Category category = category(1L, "Dairy");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCase("Drinks")).thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(1L, new CategoryUpdateRequest("Drinks"));

        assertThat(response.name()).isEqualTo("Drinks");
    }

    @Test
    void deletesCategorySuccessfully() {
        Category category = category(1L, "Dairy");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void rejectsDeletingCategoryUsedByProduct() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Dairy")));
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
            .isInstanceOf(CategoryInUseException.class);
    }

    private Category category(Long id, String name) {
        Category result = new Category(name);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }
}
