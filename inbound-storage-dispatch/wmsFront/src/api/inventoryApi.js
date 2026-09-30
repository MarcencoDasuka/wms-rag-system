import apiClient from './index'

export const inventoryApi = {
  getAllStock() {
    return apiClient.get('/inventory')
  },
  getStockById(stockId) {
    return apiClient.get(`/inventory/${stockId}`)
  },
  addStock(payload) {
    return apiClient.post('/inventory/add', payload)
  },
  removeStock(payload) {
    return apiClient.post('/inventory/remove', payload)
  },
  adjustStock(stockId, payload) {
    return apiClient.patch(`/inventory/${stockId}/adjust`, payload)
  },
  previewAdjustment(stockId, payload) {
    return apiClient.post(`/inventory/${stockId}/adjust/preview`, payload)
  },
  deleteStock(stockId) {
    return apiClient.delete(`/inventory/${stockId}`)
  },
  getAllHistory() {
    return apiClient.get('/inventory/history')
  },
  getStockHistory(stockId) {
    return apiClient.get(`/inventory/${stockId}/history`)
  },
  getProducts() {
    return apiClient.get('/products')
  },
  getLocations() {
    return apiClient.get('/locations')
  },
  importStocks(payload) {
    return apiClient.post('/inventory/imports', payload, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
