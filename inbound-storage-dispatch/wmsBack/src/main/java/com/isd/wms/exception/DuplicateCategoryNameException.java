package com.isd.wms.exception;

public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String categoryName) {
        super("Category name already exists: " + categoryName);
    }
}
