package com.isd.wms.dto.allocation;

public record ShortAllocationResponse(
    Long id,
    String productName,
    String productBarcode,
    String locationName,
    String locationBarcode,
    Integer quantity) {
}
