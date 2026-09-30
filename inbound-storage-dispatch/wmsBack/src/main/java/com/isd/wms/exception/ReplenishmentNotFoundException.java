package com.isd.wms.exception;

public class ReplenishmentNotFoundException extends RuntimeException {
    public ReplenishmentNotFoundException(Long replenishmentId) {
        super("Replenishment is not found with id: " + replenishmentId);
    }
}

