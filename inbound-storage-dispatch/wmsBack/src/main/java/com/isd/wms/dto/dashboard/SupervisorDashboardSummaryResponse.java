package com.isd.wms.dto.dashboard;

public record SupervisorDashboardSummaryResponse(
        long ordersToday,
        long completedOrdersToday,
        long inProgressOrders,
        long shortageOrders,
        long canceledOrdersToday,
        long activeOperators,
        long totalOperators,
        long averageCompletionTimeMinutes,
        long lowStockProducts,
        long ordersWaitingForDispatch,
        long stockMovementsToday,
        long inventoryAdjustmentsToday,
        long failedScanAttemptsToday
) {
}
