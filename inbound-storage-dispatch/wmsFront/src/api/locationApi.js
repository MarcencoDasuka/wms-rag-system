import apiClient from './index'

export const locationApi = {
  getAll() {
    return apiClient.get('/locations')
  },
  getById(id) {
    return apiClient.get(`/locations/${id}`)
  },
  create(payload) {
    return apiClient.post('/locations', payload)
  },
  update(id, payload) {
    return apiClient.put(`/locations/${id}`, payload)
  },
  delete(id) {
    return apiClient.delete(`/locations/${id}`)
  },
  importLocations(payload) {
    return apiClient.post('/locations/imports', payload, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
