import apiClient from './index'

export const orderApi = {
  getAll() {
    return apiClient.get('/v1/orders/extended')
  },
  getById(id) {
    return apiClient.get(`/v1/orders/extended/${id}`)
  },
  create(payload) {
    return apiClient.post('/v1/orders', payload)
  },
  update(id, payload) {
    return apiClient.put(`/v1/orders/${id}`, payload)
  },
  updateExtended(id, payload) {
    return apiClient.put(`/v1/orders/extended/${id}`, payload)
  },
  delete(id) {
    return apiClient.delete(`/v1/orders/${id}`)
  },
  assign(orderId, operatorId) {
    return apiClient.post(`/v1/orders/${orderId}/operators/${operatorId}`)
  },
  assignTask(taskId, operatorId) {
    return apiClient.post(`/tasks/${taskId}/operators/${operatorId}`)
  },
  getProducts() {
    return apiClient.get('/products/quantities')
  },
  getLocationsForDispatch() {
    return apiClient.get('/locations/dispatches')
  },
  importOrders(payload) {
    return apiClient.post('/v1/orders/imports', payload, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  getShortageOrders() {
    return apiClient.get('/v1/orders/shortages')
  },
  getShortageDetails(orderId) {
    return apiClient.get(`/v1/orders/${orderId}/shortage-details`)
  },
}
