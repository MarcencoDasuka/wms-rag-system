package com.isd.wms.dto.dashboard;

public record OperatorPerformanceResponse(
        Long operatorId,
        String operatorName,
        long completedOrdersToday,
        long activeTasks,
        long averageCompletionTimeMinutes,
        String status
) {
}
