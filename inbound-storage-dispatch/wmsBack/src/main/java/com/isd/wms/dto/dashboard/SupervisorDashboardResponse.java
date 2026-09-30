package com.isd.wms.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record SupervisorDashboardResponse(
        SupervisorDashboardSummaryResponse summary,
        List<OrderStatusCountResponse> ordersByStatus,
        List<CompletedOrdersTrendResponse> completedOrdersTrend,
        List<OperatorPerformanceResponse> operatorPerformance,
        List<TopPickedProductResponse> topPickedProducts,
        List<LowStockItemResponse> lowStockItems,
        List<ActiveLocationResponse> mostActiveLocations,
        List<NeedsAttentionResponse> needsAttention,
        List<ActivityFeedResponse> activityFeed,
        LocalDateTime generatedAt
) {
}
