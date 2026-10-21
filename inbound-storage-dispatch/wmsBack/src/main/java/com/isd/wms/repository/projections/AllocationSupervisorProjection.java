package com.isd.wms.repository.projections;

import com.isd.wms.entity.Order;
import com.isd.wms.entity.User;
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

    Long getSupervisorId();
    String getSupervisorUsername();

    default Order getOrder() {
        if (getOrderId() == null) {
            return null;
        }
        Order o = new Order();
        o.setId(getOrderId());
        if (getSupervisorId() != null) {
            User u = new User();
            u.setId(getSupervisorId());
            u.setUsername(getSupervisorUsername());
            o.setSupervisor(u);
        }
        return o;
    }
}
