package com.isd.wms.exception;

public class OrderLineNotFoundException extends RuntimeException {
    public OrderLineNotFoundException(Long orderLineId) {
        super("Order line not found with id: " + orderLineId);
    }
}