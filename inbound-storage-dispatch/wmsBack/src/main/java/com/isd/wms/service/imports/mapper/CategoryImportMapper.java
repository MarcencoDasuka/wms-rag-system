package com.isd.wms.service.imports.mapper;

import com.isd.wms.entity.Category;
import com.isd.wms.service.imports.dto.CategoryInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting {@link CategoryInfo} DTOs to {@link Category} entities.
 * <p>
 * Simply creates a new Category with the provided name.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CategoryImportMapper implements ImportMapper<CategoryInfo, Category> {

    @Override
    public Category toEntity(CategoryInfo info) {
        return new Category(info.getCategoryName());
    }

    @Override
    public Class<CategoryInfo> supports() {
        return CategoryInfo.class;
    }
}
