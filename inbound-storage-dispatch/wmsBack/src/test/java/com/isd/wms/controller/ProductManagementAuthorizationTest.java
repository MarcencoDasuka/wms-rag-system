package com.isd.wms.controller;

import com.isd.wms.dto.product.ProductResponse;
import com.isd.wms.exception.GlobalExceptionHandler;
import com.isd.wms.service.CategoryService;
import com.isd.wms.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = ProductManagementAuthorizationTest.TestConfig.class)
class ProductManagementAuthorizationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        reset(productService, categoryService);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorCanCreateProduct() throws Exception {
        when(productService.createProduct(any()))
                .thenReturn(new ProductResponse(1L, "Milk", "MILK-1", null, 1L, false, null, null, null, null));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Milk","sku":"MILK-1","categoryId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Milk"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Milk","sku":"MILK-1","categoryId":1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorCanUpdateProduct() throws Exception {
        when(productService.updateProduct(eq(1L), any()))
                .thenReturn(new ProductResponse(1L, "Updated Milk", "MILK-2", null, 1L, false, null, null, null, null));

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Milk","sku":"MILK-2","categoryId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Milk"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void creatingProductWithoutNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void creatingProductWithoutCategoryReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Milk"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.categoryId").exists());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({GlobalExceptionHandler.class})
    static class TestConfig {

        @Bean
        ProductController productController(ProductService productService) {
            return new ProductController(productService);
        }

        @Bean
        CategoryController categoryController(CategoryService categoryService) {
            return new CategoryController(categoryService);
        }

        @Bean
        ProductService productService() {
            return mock(ProductService.class);
        }

        @Bean
        CategoryService categoryService() {
            return mock(CategoryService.class);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
