package com.isd.wms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class TransportUnitNotFoundException extends RuntimeException {
    public TransportUnitNotFoundException(String tuCode) {
        super("This Transport Unit is not registered in the system: " + tuCode);
    }
}
