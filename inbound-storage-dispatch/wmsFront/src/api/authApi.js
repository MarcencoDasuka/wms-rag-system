import apiClient from './index'

export const authApi = {
  login(payload) {
    return apiClient.post('/auth/login', payload)
  },
  logout() {
    return apiClient.post('/auth/logout')
  },
  getMe() {
    return apiClient.get('/auth/me')
  },
  verify(payload) {
    return apiClient.post('/auth/verify', payload)
  }
}
