package com.isd.wms.service.allocation;

import com.isd.wms.entity.Allocation;
import com.isd.wms.entity.Stock;
import com.isd.wms.enums.TaskType;
import com.isd.wms.enums.Zone;

import java.util.List;

/**
 * Strategy interface for generating allocations for a task.
 * <p>
 * Implementations define from which warehouse zone stock should be taken and
 * in what order stocks should be used (e.g., largest available quantity first).
 * The strategy is selected based on the task type via the {@link #support(TaskType)}
 * method.
 * </p>
 *
 * @see TaskType
 * @see Zone
 * @see Stock
 * @see Allocation
 */
public interface StockAllocationStrategy {

    boolean support(TaskType taskType);

    Zone getSourceZone();

    void sortStocks(List<Stock> availableStocks);
}
