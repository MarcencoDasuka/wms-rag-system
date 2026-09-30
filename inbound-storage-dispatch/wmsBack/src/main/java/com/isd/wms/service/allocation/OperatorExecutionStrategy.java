package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationCompletionResponse;
import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.User;
import com.isd.wms.enums.TaskType;

public interface OperatorExecutionStrategy {
    boolean supports(TaskType taskType);
    AllocationCompletionResponse complete(Allocation allocation, int pickedQuantity, User operator);
    void dispatch(Allocation allocation, String tuBarcode);
}
