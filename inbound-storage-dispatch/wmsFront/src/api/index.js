import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import * as notificationService from '@/services/notificationService'
import {
  setupInterceptors,
  handle401Unauthorized,
  handle403Forbidden,
  handle409Conflict
} from './interceptors.js'

const currentHostname = typeof window !== 'undefined' && window.location ? window.location.hostname : 'localhost'

const API_BASE_URL = `http://${currentHostname}:8080/api`

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true
})

apiClient.interceptors.request.use((config) => {
  // If in-memory token is available, pass it in Authorization header;
  // otherwise, the browser transmits the HttpOnly cookie automatically via withCredentials: true.
  try {
    const authStore = useAuthStore()
    if (authStore && authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
  } catch {
    // Pinia not active yet in current execution context
  }
  return config
})

setupInterceptors(apiClient, {
  getAuthStore: () => {
    try {
      return useAuthStore()
    } catch {
      return null
    }
  },
  router,
  notifyError: notificationService.notifyError,
  notifyWarning: notificationService.notifyWarning
})

export { handle401Unauthorized, handle403Forbidden, handle409Conflict }
export default apiClient

