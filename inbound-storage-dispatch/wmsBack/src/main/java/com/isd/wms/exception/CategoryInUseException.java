package com.isd.wms.exception;

public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(Long categoryId) {
        super("Category cannot be deleted because products are assigned to it: " + categoryId);
    }
}
