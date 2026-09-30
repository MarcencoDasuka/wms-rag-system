import apiClient from './index'

export const userApi = {
  getAll() {
    return apiClient.get('/supervisor/users')
  },
  register(payload) {
    return apiClient.post('/supervisor/users/register', payload)
  },
  update(id, payload) {
    return apiClient.put(`/supervisor/users/${id}`, payload)
  },
  delete(id) {
    return apiClient.delete(`/supervisor/users/${id}`)
  }
}
