package com.isd.wms.dto.allocation;

public record AllocationExecutionResponse(
        Long allocationId,
        String status,
        boolean sourceLocationScanned,
        boolean productScanned,
        Integer requiredQuantity,
        Integer pickedQuantity
) {
}
