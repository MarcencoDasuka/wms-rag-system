import axios from 'axios'
import * as notificationService from '../services/notificationService.js'
import {
  setupInterceptors,
  handle401Unauthorized,
  handle403Forbidden,
  handle409Conflict
} from './interceptors.js'

let appRouter = null
export const setApiRouter = (router) => {
  appRouter = router
}

let authStoreGetter = null
export const setAuthStoreGetter = (fn) => {
  authStoreGetter = fn
}

export const resolveBaseUrl = (env = typeof import.meta !== 'undefined' ? import.meta.env : {}) => {
  if (env && env.VITE_API_URL && typeof env.VITE_API_URL === 'string' && env.VITE_API_URL.trim().length > 0) {
    return env.VITE_API_URL.trim()
  }
  return '/api'
}

export const API_BASE_URL = resolveBaseUrl()

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true
})

apiClient.interceptors.request.use((config) => {
  // If in-memory token is available, pass it in Authorization header;
  // otherwise, the browser transmits the HttpOnly cookie automatically via withCredentials: true.
  try {
    const authStore = typeof authStoreGetter === 'function' ? authStoreGetter() : null
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
      return typeof authStoreGetter === 'function' ? authStoreGetter() : null
    } catch {
      return null
    }
  },
  getRouter: () => appRouter,
  notifyError: notificationService.notifyError,
  notifyWarning: notificationService.notifyWarning
})

export { handle401Unauthorized, handle403Forbidden, handle409Conflict }
export default apiClient

