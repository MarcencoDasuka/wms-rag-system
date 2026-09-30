package com.isd.wms.service;

import com.isd.wms.dto.product.ProductCreateRequest;
import com.isd.wms.dto.product.ProductResponse;
import com.isd.wms.dto.product.ProductUpdateRequest;
import com.isd.wms.entity.Category;
import com.isd.wms.entity.Product;
import com.isd.wms.enums.Zone;
import com.isd.wms.exception.CategoryNotFoundException;
import com.isd.wms.exception.InvalidRequestException;
import com.isd.wms.exception.ProductNotFoundException;
import com.isd.wms.mapper.ProductMapper;
import com.isd.wms.repository.CategoryRepository;
import com.isd.wms.repository.ProductRepository;
import com.isd.wms.repository.projections.ProductWithQuantityProjection;
import com.isd.wms.service.ai.ProductVectorIndexer;
import com.isd.wms.service.imports.ImportService;
import com.isd.wms.service.imports.dto.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service for managing products.
 * <p>
 * Provides CRUD operations for products, including validation of unique barcodes
 * and association with categories. When a product is created or updated, its
 * vector representation is indexed for AI‑based search via
 * {@link ProductVectorIndexer}.
 * </p>
 * <p>
 * Product search supports filtering by name and category. Bulk import from files
 * is also supported.
 * </p>
 *
 * @see Product
 * @see Category
 * @see ProductVectorIndexer
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductVectorIndexer productVectorIndexer;
    private final ImportService importService;

    /**
     * Creates a new product.
     *
     * @param request the product creation request
     * @return the created product response
     * @throws InvalidRequestException if the barcode already exists
     */
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        log.info("Creating product: name={}, barcode={}, categoryId={}", request.name(), request.barcode(), request.categoryId());
        String barcode = request.barcode().trim();
        if (productRepository.existsByBarcodeIgnoreCase(barcode)) {
            throw new InvalidRequestException("Product barcode already exists");
        }
        Category category = getCategory(request.categoryId());
        Product product = new Product(request.name().trim(), barcode, request.description(), category);

        product.setAutoReplenish(request.autoReplenish() != null && request.autoReplenish());
        product.setMinThreshold(request.minThreshold());
        product.setReplenishQty(request.replenishQty());

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully: productId={}, categoryId={}", savedProduct.getId(), category.getId());
        productVectorIndexer.indexProduct(savedProduct);
        return productMapper.toResponse(savedProduct);
    }

    /**
     * Updates an existing product.
     *
     * @param productId the ID of the product to update
     * @param request the update request
     * @return the updated product response
     * @throws InvalidRequestException if the new barcode conflicts with another product
     */
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductUpdateRequest request) {
        log.info("Updating product: productId={}, name={}, barcode={}, categoryId={}",
            productId, request.name(), request.barcode(), request.categoryId());
        String barcode = request.barcode().trim();
        Product product = getProduct(productId);

        if (productRepository.existsByBarcodeIgnoreCaseAndIdNot(barcode, productId)) {
            throw new InvalidRequestException("Product barcode already exists");
        }

        updateProductMembers(request, product, barcode);

        productVectorIndexer.indexProduct(product);
        return productMapper.toResponse(product);
    }

    private void updateProductMembers(ProductUpdateRequest request, Product product, String barcode) {
        Category category = getCategory(request.categoryId());

        product.setName(request.name().trim());
        product.setBarcode(barcode);
        product.setDescription(request.description());
        product.setCategory(category);

        product.setAutoReplenish(request.autoReplenish() != null && request.autoReplenish());
        product.setMinThreshold(request.minThreshold());
        product.setReplenishQty(request.replenishQty());

        productRepository.saveAndFlush(product);
        log.info("Product updated successfully: productId={}, categoryId={}", product.getId(), category.getId());
    }

    /**
     * Deletes a product by ID.
     *
     * @param productId the ID of the product to delete
     */
    @Transactional
    public void deleteProduct(Long productId) {
        log.info("Deleting product: productId={}", productId);
        productRepository.delete(getProduct(productId));
        productVectorIndexer.removeProduct(productId);
        log.info("Product deleted successfully: productId={}", productId);
    }

    public ProductResponse getProductById(Long productId) {
        log.info("Getting product by id: productId={}", productId);
        Product product = getProduct(productId);
        log.debug("Product found: productId={}, categoryId={}", product.getId(), product.getCategory().getId());
        return productMapper.toResponse(product);
    }

    public List<ProductResponse> getAllProducts() {
        log.info("Getting all products");
        List<ProductResponse> products = productRepository.findAll().stream()
            .map(productMapper::toResponse)
            .toList();
        log.info("Products fetched successfully: count={}", products.size());
        return products;
    }

    public List<ProductWithQuantityProjection> getAllProductsWithQuantity(Zone zone) {
        return productRepository.getProductsWithQuantities(zone);
    }

    /**
     * Searches products by name and/or category.
     *
     * @param name the product name (partial match, case‑insensitive)
     * @param categoryId the category ID (optional)
     * @return a list of matching products
     * @throws InvalidRequestException if no search parameters are provided
     */
    public List<ProductResponse> searchProducts(String name, Long categoryId) {
        log.info("Searching products: name={}, categoryId={}", name, categoryId);
        String searchName = name == null || name.isBlank() ? null : name.trim();
        if (searchName == null && categoryId == null) {
            log.warn("Invalid product search request: missing search parameters");
            throw new InvalidRequestException("At least one search parameter is required");
        }

        List<ProductResponse> products = productRepository.search(searchName, categoryId).stream()
            .map(productMapper::toResponse)
            .toList();
        log.info("Product search completed: name={}, categoryId={}, count={}",
            searchName, categoryId, products.size());
        return products;
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> {
                log.warn("Product not found: productId={}", productId);
                return new ProductNotFoundException(productId);
            });
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> {
                log.warn("Category not found for product operation: categoryId={}", categoryId);
                return new CategoryNotFoundException(categoryId);
            });
    }

    @Transactional
    public void importProductsFromFile(MultipartFile file) {
        List<ProductCreateRequest> products = importService.importData(file, ProductInfo.class);
        products.forEach(this::createProduct);
    }
}
