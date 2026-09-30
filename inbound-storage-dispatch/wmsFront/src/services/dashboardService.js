import apiClient from '@/api'

const emptySummary = {
  ordersToday: 0,
  completedOrdersToday: 0,
  inProgressOrders: 0,
  shortageOrders: 0,
  canceledOrdersToday: 0,
  activeOperators: 0,
  totalOperators: 0,
  averageCompletionTimeMinutes: 0,
  lowStockProducts: 0,
  ordersWaitingForDispatch: 0,
  stockMovementsToday: 0,
  inventoryAdjustmentsToday: 0,
  failedScanAttemptsToday: 0
}

const normalizeNumber = (value) => {
  const numberValue = Number(value ?? 0)
  return Number.isFinite(numberValue) ? numberValue : 0
}

const normalizeSummary = (summary = {}) => ({
  ...emptySummary,
  ...Object.fromEntries(Object.keys(emptySummary).map((key) => [key, normalizeNumber(summary[key])]))
})

const normalizeDashboard = (payload = {}) => ({
  summary: normalizeSummary(payload.summary),
  ordersByStatus: Array.isArray(payload.ordersByStatus) ? payload.ordersByStatus : [],
  completedOrdersTrend: Array.isArray(payload.completedOrdersTrend) ? payload.completedOrdersTrend : [],
  operatorPerformance: Array.isArray(payload.operatorPerformance) ? payload.operatorPerformance : [],
  topPickedProducts: Array.isArray(payload.topPickedProducts) ? payload.topPickedProducts : [],
  lowStockItems: Array.isArray(payload.lowStockItems) ? payload.lowStockItems : [],
  mostActiveLocations: Array.isArray(payload.mostActiveLocations) ? payload.mostActiveLocations : [],
  needsAttention: Array.isArray(payload.needsAttention) ? payload.needsAttention : [],
  activityFeed: Array.isArray(payload.activityFeed) ? payload.activityFeed : [],
  generatedAt: payload.generatedAt || new Date().toISOString()
})

export const dashboardService = {
  async getSupervisorDashboard() {
    const response = await apiClient.get('/supervisor/dashboard')
    return normalizeDashboard(response.data)
  },

  async getDashboardSummary() {
    const dashboard = await this.getSupervisorDashboard()
    return dashboard.summary
  }
}
