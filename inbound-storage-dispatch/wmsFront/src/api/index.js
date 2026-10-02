import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const currentHostname = window.location.hostname;

const API_BASE_URL = `http://${currentHostname}:8080/api`;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true
});

apiClient.interceptors.request.use((config) => {
  // If in-memory token is available, pass it in Authorization header;
  // otherwise, the browser transmits the HttpOnly cookie automatically via withCredentials: true.
  try {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
  } catch {
    // Pinia not active yet in current execution context
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  async (error) => {
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      try {
        const authStore = useAuthStore()
        await authStore.logout()
      } catch {
        // ignore
      }

      if (router.currentRoute.value.name !== 'login') {
        router.push('/login?loggedOut=true')
      }
    }

    return Promise.reject(error)
  }
)

export default apiClient
