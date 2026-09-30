package com.isd.wms.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productName) {
        super(String.format("Product %s is not found.", productName));
    }

    public ProductNotFoundException(Long productId) {
        super(String.format("Product with id %d is not found.", productId));
    }
}
