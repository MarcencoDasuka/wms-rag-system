package com.isd.wms.exception;

public class DuplicateLocationNameException extends RuntimeException {

    public DuplicateLocationNameException(String locationName) {
        super("Location with name '" + locationName + "' already exists. Please choose a different name.");
    }

}
