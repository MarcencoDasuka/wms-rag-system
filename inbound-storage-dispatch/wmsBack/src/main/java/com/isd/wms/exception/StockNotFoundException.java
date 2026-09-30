package com.isd.wms.exception;

public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(Long stockId) {
        super("Stock not found with id: " + stockId);
    }
}
