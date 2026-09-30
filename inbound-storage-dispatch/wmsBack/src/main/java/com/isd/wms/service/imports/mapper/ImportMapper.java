package com.isd.wms.service.imports.mapper;

import com.isd.wms.entity.Category;
import com.isd.wms.service.imports.dto.CategoryInfo;

/**
 * Mapper interface for converting import DTOs to business objects.
 * <p>
 * Implementations define how data from an import file (represented as DTOs)
 * is transformed into entities, request objects, or other domain objects
 * that can be processed by the core services.
 * </p>
 * <p>
 * Each mapper declares which DTO class it supports via {@link #supports()}.
 * </p>
 *
 * @param <T> the DTO type (e.g., {@link CategoryInfo})
 * @param <E> the resulting business object type (e.g., {@link Category})
 */
public interface ImportMapper<T, E> {

    /**
     * Converts a DTO into a business object.
     *
     * @param dto the import DTO
     * @return the converted business object
     */
    E toEntity(T dto);

    /**
     * Returns the DTO class that this mapper supports.
     *
     * @return the DTO class
     */
    Class<T> supports();
}
