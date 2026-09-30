package com.isd.wms.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long stockId, Integer requestedQuantity, Integer availableQuantity) {
        super("Insufficient stock for id " + stockId + ": requested " + requestedQuantity
                + ", available " + availableQuantity);
    }
}
