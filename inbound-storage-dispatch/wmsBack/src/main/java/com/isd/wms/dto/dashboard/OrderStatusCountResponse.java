package com.isd.wms.dto.dashboard;

public record OrderStatusCountResponse(
        String status,
        long count
) {
}
