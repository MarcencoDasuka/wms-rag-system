package com.isd.wms.exception;

public class DuplicateBarcodeException extends RuntimeException {
    public DuplicateBarcodeException(String code) {
        super("Barcode '" + code + "' is already in use. Please choose a different barcode.");
    }
}
