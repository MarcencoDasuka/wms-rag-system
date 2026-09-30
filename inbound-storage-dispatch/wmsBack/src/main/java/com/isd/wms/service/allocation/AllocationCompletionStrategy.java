package com.isd.wms.service.allocation;

import com.isd.wms.dto.allocation.AllocationCompletionResult;
import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Task;
import com.isd.wms.enums.TaskType;

/**
 * Strategy interface for handling the completion of an allocation within a workflow.
 * <p>
 * Different task types (e.g., picking orders vs. replenishments) require different
 * completion logic. Implementations of this interface define how the status of
 * the parent task and its associated entities (order, replenishment) should be
 * updated after an allocation is completed.
 * </p>
 * <p>
 * Each strategy also provides a result object that summarises the outcome.
 * </p>
 *
 * @see Allocation
 * @see Task
 * @see TaskType
 * @see PickingAllocationCompletionStrategy
 * @see ReplenishmentAllocationCompletionStrategy
 */
public interface AllocationCompletionStrategy {

    default void handle(Allocation allocation) {
    }

    boolean updateStatus(Task task);

    boolean support(TaskType taskType);

    AllocationCompletionResult result(Task task);
}
