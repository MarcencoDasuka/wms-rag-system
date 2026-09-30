package com.isd.wms.service.allocation;

import com.isd.wms.entity.Stock;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;
import com.isd.wms.service.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Allocation generation strategy for picking orders.
 * <p>
 * When a picking task is created, this strategy determines that stock should be
 * allocated from the {@link Zone#PICKING} zone, and sorts available stock
 * by decreasing available quantity to minimise the number of allocations.
 * </p>
 * <p>
 * This strategy supports only {@link TaskType#PICKING_ORDER}.
 * </p>
 *
 * @see StockAllocationStrategy
 * @see WorkflowService
 */
@Component
public class PickingAllocationStrategy implements StockAllocationStrategy {

    @Override
    public boolean support(TaskType taskType) {
        return taskType == TaskType.PICKING_ORDER;
    }

    @Override
    public Zone getSourceZone() {
        return Zone.PICKING;
    }

    @Override
    public void sortStocks(List<Stock> availableStocks) {
        availableStocks.sort(
            Comparator.comparingInt(Stock::getAvailableQuantity)
                .thenComparing(
                    Stock::getId,
                    Comparator.nullsLast(Comparator.naturalOrder())
                )
        );
    }
}
