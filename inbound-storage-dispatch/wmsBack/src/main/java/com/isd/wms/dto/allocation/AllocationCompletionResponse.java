package com.isd.wms.dto.allocation;

import com.isd.wms.enums.AllocationCompletionStatus;
import com.isd.wms.enums.OrderStatus;
import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;
import com.isd.wms.dto.operator.OperatorTaskSummaryResponse;

public record AllocationCompletionResponse(
    AllocationCompletionStatus status,
    TaskType taskType,
    Long id,
    Integer confirmedQuantity,
    Integer shortageQuantity,
    boolean newProcessCreated,
    Long newProcessId,
    Status orderLineStatus,
    OrderStatus orderStatus,
    String message,
    OperatorTaskSummaryResponse summary
) {
    public AllocationCompletionResponse(AllocationCompletionResult result) {
        this(result.status(), result.taskType(), result.id(), null, null, false, null, null, null, null, null);
    }
}
