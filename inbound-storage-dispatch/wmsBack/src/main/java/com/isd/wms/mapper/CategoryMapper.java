package com.isd.wms.mapper;

import com.isd.wms.dto.category.CategoryResponse;
import com.isd.wms.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
