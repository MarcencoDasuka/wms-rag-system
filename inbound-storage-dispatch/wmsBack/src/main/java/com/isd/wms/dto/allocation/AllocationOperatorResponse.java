package com.isd.wms.dto.allocation;

public record AllocationOperatorResponse(
    Integer totalOfAllocations,
    Integer currentIndexOfAllocation,
    String orderLogicalId,
    String taskType,
    String destinationLocationBarcode,
    ShortAllocationResponse allocations) {
}
