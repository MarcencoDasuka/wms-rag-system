package com.isd.wms.dto.operator;

import com.isd.wms.enums.Status;

public record OperatorAllocationSummaryResponse(
    Long allocationId,
    Long taskId,
    Long orderLineId,
    Long productId,
    String productName,
    String productBarcode,
    String sourceLocationBarcode,
    String destinationLocationBarcode,
    Integer requiredQuantity,
    Integer pickedQuantity,
    Status status,
    boolean sourceLocationScanned,
    boolean productScanned,
    boolean isTuScanned
) {
}
