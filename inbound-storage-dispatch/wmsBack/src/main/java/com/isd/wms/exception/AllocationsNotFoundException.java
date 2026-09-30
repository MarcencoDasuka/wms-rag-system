package com.isd.wms.exception;

public class AllocationsNotFoundException extends RuntimeException {
    public AllocationsNotFoundException(Long taskId) {
        super("Allocations not found of task with id: " + taskId);
    }
    public AllocationsNotFoundException(String operator) {
        super("Allocations not found for operator with username: " + operator);
    }
}
