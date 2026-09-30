package com.isd.wms.dto.operator;

import com.isd.wms.enums.Status;

import java.util.List;

public record OperatorOrderLineSummaryResponse(
    Long taskId,
    Long orderLineId,
    Long productId,
    String productName,
    String productBarcode,
    Integer requiredQuantity,
    Integer pickedQuantity,
    List<String> sourceLocationBarcodes,
    String destinationLocationBarcode,
    Status status
) {
}
