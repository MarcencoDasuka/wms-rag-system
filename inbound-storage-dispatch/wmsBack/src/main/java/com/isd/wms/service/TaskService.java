package com.isd.wms.service;

import com.isd.wms.entity.Task;
import com.isd.wms.entity.User;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Status;
import com.isd.wms.exception.TaskNotFoundException;
import com.isd.wms.exception.UserNotFoundException;
import com.isd.wms.repository.TaskRepository;
import com.isd.wms.repository.UserRepository;
import com.isd.wms.repository.AllocationRepository  ;
import com.isd.wms.repository.ReplenishmentRepository;
import com.isd.wms.service.validation.SecurityFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing tasks within the workflow.
 * <p>
 * Tasks represent units of work (picking orders or replenishments). This service
 * creates tasks, assigns them to operators, and interacts with the allocation
 * and replenishment systems to maintain consistency.
 * </p>
 * <p>
 * When a task is created, allocations are automatically generated via
 * {@link WorkflowService}. Assigning a task updates its status and cascades
 * the assignment to its allocations and to the parent replenishment (if any).
 * </p>
 *
 * @see Task
 * @see WorkflowService
 * @see AllocationRepository
 * @see ReplenishmentRepository
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AllocationRepository  allocationRepository ;
    private final ReplenishmentRepository replenishmentRepository;
    private final SecurityFacade securityFacade;
    private final WorkflowService workflowService;

    /**
     * Creates a new task of the given type and generates allocations to satisfy
     * the requested quantity for the specified product.
     *
     * @param type the task type (PICKING_ORDER or REPLENISHMENT)
     * @param requestedQuantity the quantity needed
     * @param productId the product ID
     * @return the created task
     */
    @Transactional
    public Task createTask(TaskType type, Integer requestedQuantity, Long productId) {
        User supervisor = securityFacade.getCurrentUser();

        Task task = new Task(supervisor, type, requestedQuantity);
        task = taskRepository.saveAndFlush(task);

        workflowService.generateAllocationsForTask(task, productId, requestedQuantity);

        return task;
    }

    /**
     * Assigns a task to an operator. This also updates the status of associated
     * allocations and (if applicable) the parent replenishment.
     *
     * @param taskId the task ID
     * @param operatorId the operator ID
     * @throws TaskNotFoundException if the task does not exist
     * @throws UserNotFoundException if the operator does not exist
     */
    @Transactional
    public void assignTask(Long taskId, Long operatorId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));
        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new UserNotFoundException(operatorId));

        if (task.getOperator().filter(current -> current.equals(operator)).isPresent()) {
            return;
        }

        task.setOperator(operator);
        taskRepository.save(task);

        var allocations = allocationRepository.findAllByTaskId(taskId);
        allocations.forEach((allocation) -> allocation.setStatus(Status.ASSIGNED));

        allocationRepository.saveAll(allocations);

        replenishmentRepository.findByTaskId(taskId).ifPresent(replenishment -> {
            replenishment.setStatus(Status.ASSIGNED);
            replenishmentRepository.save(replenishment);
        });

    }

}
