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
  const token = localStorage.getItem('jwt_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      const authStore = useAuthStore()

      authStore.logout()

      if (router.currentRoute.value.name !== 'login') {
        router.push('/login?loggedOut=true')
      }
    }

    return Promise.reject(error)
  }
)

export default apiClient
