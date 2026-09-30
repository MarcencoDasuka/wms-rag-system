package com.isd.wms.dto.allocation;

import com.isd.wms.enums.AllocationCompletionStatus;
import com.isd.wms.enums.TaskType;

public record AllocationCompletionResult(
    AllocationCompletionStatus status,
    TaskType taskType,
    Long id
) {
}
