package com.isd.wms.dto.transport_unit;

public record ScanTuRequest(
    String barcode,
    boolean isOrder
) {}
