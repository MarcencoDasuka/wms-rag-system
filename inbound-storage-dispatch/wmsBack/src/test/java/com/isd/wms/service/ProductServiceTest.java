package com.isd.wms.service;

import com.isd.wms.dto.product.ProductCreateRequest;
import com.isd.wms.dto.product.ProductResponse;
import com.isd.wms.dto.product.ProductUpdateRequest;
import com.isd.wms.entity.Category;
import com.isd.wms.entity.Product;
import com.isd.wms.exception.CategoryNotFoundException;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.mapper.ProductMapper;
import com.isd.wms.repository.CategoryRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.service.ai.ProductVectorIndexer;
import com.isd.wms.service.imports.ImportService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductVectorIndexer productVectorIndexer;
    @Mock private ImportService importService;

    @Spy private ProductMapper productMapper = new ProductMapper();

    @InjectMocks
    private ProductService productService;

    private Category category;
    private Validator validator;

    @BeforeEach
    void setUp() {
        category = category(1L, "Dairy");
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void createsProductSuccessfully() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.createProduct(new ProductCreateRequest("Milk", "MILK-1", "Whole milk", 1L, false, null, null));

        assertThat(response.name()).isEqualTo("Milk");
        assertThat(response.barcode()).isEqualTo("MILK-1");
        assertThat(response.categoryId()).isEqualTo(1L);
        verify(productVectorIndexer).indexProduct(any(Product.class));
    }

    @Test
    void rejectsProductWithoutName() {
        assertThat(validator.validate(new ProductCreateRequest(" ", "MILK-1", null, 1L, false, null, null)))
            .isNotEmpty();
    }

    @Test
    void updatesProductSuccessfully() {
        Product product = product(10L, "Milk", category);
        Category drinks = category(2L, "Drinks");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(drinks));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.updateProduct(10L, new ProductUpdateRequest("Juice", "JUICE-1", null, 2L, false, null, null));

        assertThat(response.name()).isEqualTo("Juice");
        assertThat(response.barcode()).isEqualTo("JUICE-1");
        verify(productVectorIndexer).indexProduct(any(Product.class));
    }

    @Test
    void deletesProductSuccessfully() {
        Product product = product(10L, "Milk", category);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        productService.deleteProduct(10L);

        verify(productRepository).delete(product);
        verify(productVectorIndexer).removeProduct(10L);
    }

    private Category category(Long id, String name) {
        Category result = new Category(name);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private Product product(Long id, String name, Category productCategory) {
        Product result = new Product(name, "SKU-" + id, null, productCategory);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }
}
