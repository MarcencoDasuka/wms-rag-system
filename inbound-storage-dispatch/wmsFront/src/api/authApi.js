import apiClient from './index'

export const authApi = {
  login(payload) {
    return apiClient.post('/auth/login', payload)
  },
  verify(payload) {
    return apiClient.post('/auth/verify', payload)
  }
}
