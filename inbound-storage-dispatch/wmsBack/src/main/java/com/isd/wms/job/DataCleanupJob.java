package com.isd.wms.job;

import com.isd.wms.repository.OrderRepository;
import com.isd.wms.repository.OrderLineRepository;
import com.isd.wms.repository.TaskRepository;
import com.isd.wms.repository.AllocationRepository;
import com.isd.wms.repository.ReplenishmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupJob {

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final TaskRepository taskRepository;
    private final AllocationRepository allocationRepository;
    private final ReplenishmentRepository replenishmentRepository;

    @Value("${wms.cleanup.retention-days:14}")
    private int retentionDays;

    @Scheduled(cron = "${wms.cleanup.cron}")
    @Transactional
    public void cleanOldData() {
        log.info("The automatic database cleanup process has begun...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        log.info("Retention period set to {} days. Cutoff date: {}", retentionDays, cutoffDate);

        try {
            int deletedAllocations = allocationRepository.deleteAllocationsOlderThan(cutoffDate);
            log.info("Deleted allocations: {}", deletedAllocations);

            int deletedReplenishments = replenishmentRepository.deleteReplenishmentsByTaskCreatedAtOlderThan(cutoffDate);
            log.info("Deleted replenishments: {}", deletedReplenishments);

            int deletedOrderLines = orderLineRepository.deleteOrderLinesByOrderCreatedAtOlderThan(cutoffDate);
            log.info("Deleted order lines: {}", deletedOrderLines);

            int deletedOrders = orderRepository.deleteOrdersOlderThan(cutoffDate);
            log.info("Deleted orders: {}", deletedOrders);

            int deletedTasks = taskRepository.deleteTasksOlderThan(cutoffDate);
            log.info("Deleted tasks: {}", deletedTasks);

            log.info("Cleanup complete successfully!");
        } catch (Exception e) {
            log.error("Critical error while running the cleanup task:", e);
            throw e;
        }
    }
}
