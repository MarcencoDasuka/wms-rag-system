package com.isd.wms.service.imports.mapper;

import com.isd.wms.dto.product.ProductCreateRequest;
import com.isd.wms.entity.Category;
import com.isd.wms.repository.CategoryRepository;
import com.isd.wms.service.imports.dto.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting {@link ProductInfo} DTOs to {@link ProductCreateRequest} objects.
 * <p>
 * The category name is resolved (or created if missing) before building the request.
 * Default auto‑replenish settings (disabled, threshold = 10, replenish qty = 10)
 * are applied.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ProductImportMapper implements ImportMapper<ProductInfo, ProductCreateRequest> {

    private final CategoryRepository categoryRepository;

    @Override
    public ProductCreateRequest toEntity(ProductInfo info) {
        Category category = categoryRepository.findByNameIgnoreCase(info.getCategoryName())
            .orElseGet(() -> categoryRepository.saveAndFlush(new Category(info.getCategoryName())));

        return new ProductCreateRequest(
            info.getName(),
            info.getBarcode(),
            info.getDescription(),
            category.getId(),
            false,
            10,
            10
        );
    }

    @Override
    public Class<ProductInfo> supports() {
        return ProductInfo.class;
    }
}
