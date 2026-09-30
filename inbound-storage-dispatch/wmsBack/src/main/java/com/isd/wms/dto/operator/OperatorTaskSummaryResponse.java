package com.isd.wms.dto.operator;

import com.isd.wms.enums.OrderStatus;

import java.util.List;

public record OperatorTaskSummaryResponse(
    Long taskId,
    Long orderId,
    String orderLogicId,
    OrderStatus orderStatus,
    String taskType,
    String destinationLocationBarcode,
    Integer totalAllocations,
    Integer completedAllocations,
    boolean readyForCompletion,
    boolean tuScanned,
    OperatorAllocationSummaryResponse currentAllocation,
    List<OperatorOrderLineSummaryResponse> orderLines,
    List<OperatorAllocationSummaryResponse> allocations
) {
}
