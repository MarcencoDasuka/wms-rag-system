package com.isd.wms.repository.projections;

import com.isd.wms.enums.Status;
import com.isd.wms.enums.TaskType;

public interface AllocationSupervisorProjection {
    Long getAllocationId();
    Long getReplenishmentId();
    Long getOrderId();
    TaskType getType();
    Long getStockId();
    String getProductName();
    String getLocationName();
    Integer getRequestedQuantity();
    Integer getDeliveredQuantity();
    Status getStatus();
    Boolean getSourceLocationScanned();
    Boolean getProductScanned();
}
