package com.isd.wms.exception;

public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException(Long id) {
        super("Location with ID " + id + " not found");
    }
    public LocationNotFoundException(String locationName) {
        super(String.format("Location %s is not found", locationName));
    }
}
