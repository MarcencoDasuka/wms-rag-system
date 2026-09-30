package com.isd.wms.service.allocation;

import com.isd.wms.entity.Stock;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;
import com.isd.wms.service.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Allocation generation strategy for replenishment tasks.
 * <p>
 * When a replenishment task is created, this strategy allocates stock from the
 * {@link Zone#REPLENISHMENT} zone (bulk storage) and sorts available stock by
 * decreasing available quantity to optimise allocation.
 * </p>
 * <p>
 * This strategy supports only {@link TaskType#REPLENISHMENT}.
 * </p>
 *
 * @see StockAllocationStrategy
 * @see WorkflowService
 */
@Component
public class ReplenishmentAllocationStrategy implements StockAllocationStrategy {

    @Override
    public boolean support(TaskType taskType) {
        return taskType == TaskType.REPLENISHMENT;
    }

    @Override
    public Zone getSourceZone() {
        return Zone.REPLENISHMENT;
    }

    @Override
    public void sortStocks(List<Stock> availableStocks) {
        availableStocks.sort(Comparator.comparingInt(
            (Stock s) -> s.getQuantity() - s.getReservedQuantity()
        ).reversed());
    }
}
