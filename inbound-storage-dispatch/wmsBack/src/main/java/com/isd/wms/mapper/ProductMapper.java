package com.isd.wms.mapper;

import com.isd.wms.dto.product.ProductResponse;
import com.isd.wms.entity.Category;
import com.isd.wms.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getBarcode(),
                product.getDescription(),
                category == null ? null : category.getId(),
                product.getAutoReplenish(),
                product.getMinThreshold().orElse(null),
                product.getReplenishQty().orElse(null),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
