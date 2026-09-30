import apiClient from './index'

export const replenishmentApi = {
  getAll() {
    return apiClient.get('/replenishments')
  },
  getById(id) {
    return apiClient.get(`/replenishments/${id}`)
  },
  create(payload) {
    return apiClient.post('/replenishments', payload)
  },
  update(id, payload) {
    return apiClient.put(`/replenishments/${id}`, payload)
  },
  assign(replenishmentId, operatorId) {
    return apiClient.post(`/replenishments/${replenishmentId}/operators/${operatorId}`)
  },
  delete(id) {
    return apiClient.delete(`/replenishments/${id}`)
  },
  cancel(id) {
    return apiClient.post(`/replenishments/${id}/cancel`)
  },
  filter(filters) {
    return apiClient.post('/replenishments/filter', null, { params: filters })
  },
  importReplenishments(payload) {
    return apiClient.post('/replenishments/imports', payload, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  getShortageReplenishments() {
    return apiClient.get('/replenishments/shortages')
  },
  getShortageDetails(replenishmentId) {
    return apiClient.get(`/replenishments/${replenishmentId}/shortage-details`)
  },
}
