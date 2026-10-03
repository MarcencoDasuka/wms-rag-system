import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { authApi } from '@/api/authApi'

const TOKEN_KEY = 'jwt_token'
const ROLE_KEY = 'user_role'
const USER_KEY = 'user'
const USER_ID_KEY = 'user_id'

// Purge any legacy token from localStorage to remediate S-5 (XSS protection)
if (typeof window !== 'undefined' && window.localStorage) {
  localStorage.removeItem(TOKEN_KEY)
}

const defaultRolesByUsername = {
  dev: 'ROLE_DEV',
  supervisor: 'ROLE_SUPERVISOR',
  operator: 'ROLE_OPERATOR'
}

const decodeJwtPayload = (token) => {
  try {
    const payload = token.split('.')[1]
    const normalizedPayload = payload.replace(/-/g, '+').replace(/_/g, '/')
    const paddedPayload = normalizedPayload.padEnd(normalizedPayload.length + ((4 - normalizedPayload.length % 4) % 4), '=')
    const decodedPayload = decodeURIComponent(
      atob(paddedPayload)
        .split('')
        .map((character) => `%${`00${character.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join('')
    )
    return JSON.parse(decodedPayload)
  } catch {
    return {}
  }
}

const normalizeRole = (role) => {
  if (!role) return null
  return role.startsWith('ROLE_') ? role : `ROLE_${role}`
}

const inferRoleFromUsername = (username) => {
  return normalizeRole(defaultRolesByUsername[username])
}

const safeDashboardForRole = (role) => {
  if (role === 'ROLE_DEV') return '/dev'
  if (role === 'ROLE_SUPERVISOR') return '/supervisor'
  if (role === 'ROLE_OPERATOR') return '/operator'
  return '/login'
}

export const useAuthStore = defineStore('auth', () => {
  // Token is strictly in-memory; credentials are primarily transmitted via HttpOnly cookie
  const token = ref(null)
  const role = ref(typeof window !== 'undefined' ? sessionStorage.getItem(ROLE_KEY) : null)
  const user = ref(typeof window !== 'undefined' ? JSON.parse(sessionStorage.getItem(USER_KEY) || 'null') : null)

  const isAuthenticated = computed(() => !!token.value || !!user.value)
  const dashboardPath = computed(() => safeDashboardForRole(role.value))

  const persistAuth = (authData) => {
    token.value = authData.token
    role.value = authData.role
    user.value = authData.user

    // Invariant: Never store JWT in localStorage/sessionStorage
    if (typeof window !== 'undefined') {
      localStorage.removeItem(TOKEN_KEY)
      sessionStorage.setItem(ROLE_KEY, role.value)
      sessionStorage.setItem(USER_KEY, JSON.stringify(user.value))

      if (user.value?.id) {
        sessionStorage.setItem(USER_ID_KEY, user.value.id)
      } else {
        sessionStorage.removeItem(USER_ID_KEY)
      }
    }
  }

  const login = async (username, password) => {
    const response = await authApi.login({ username, password })
    const responseToken = response.data?.token

    if (!responseToken) {
      throw new Error('Authentication response did not include a token.')
    }

    const claims = decodeJwtPayload(responseToken)
    const authenticatedUsername = claims.sub || username
    const detectedRole = normalizeRole(response.data?.role || claims.role || claims.authorities?.[0] || inferRoleFromUsername(authenticatedUsername))

    if (!detectedRole) {
      throw new Error('Authenticated role is not available in the backend response.')
    }

    token.value = responseToken
    role.value = detectedRole

    let authUser = response.data?.user || (response.data?.userId ? { id: response.data.userId, username: authenticatedUsername } : null)

    // Authoritative user profile resolution: fetch real database user ID from /api/auth/me
    if (!authUser?.id) {
      try {
        const meResponse = await authApi.getMe()
        if (meResponse?.data) {
          authUser = {
            id: meResponse.data.id,
            username: meResponse.data.username || authenticatedUsername
          }
          if (meResponse.data.role) {
            role.value = normalizeRole(meResponse.data.role)
          }
        }
      } catch {
        // Fail closed: do not substitute seeded or mock user IDs
      }
    }

    persistAuth({ token: responseToken, role: role.value, user: authUser })

    return { token: responseToken, role: role.value, user: authUser }
  }

  const fetchCurrentUser = async () => {
    try {
      const response = await authApi.getMe()
      if (response.data) {
        user.value = {
          id: response.data.id,
          username: response.data.username
        }
        role.value = normalizeRole(response.data.role)

        if (typeof window !== 'undefined') {
          sessionStorage.setItem(ROLE_KEY, role.value)
          sessionStorage.setItem(USER_KEY, JSON.stringify(user.value))
          if (response.data.id) {
            sessionStorage.setItem(USER_ID_KEY, response.data.id)
          }
        }
        return response.data
      }
    } catch (e) {
      await logout()
      throw e
    }
  }

  const logout = async () => {
    token.value = null
    role.value = null
    user.value = null

    if (typeof window !== 'undefined') {
      localStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem(ROLE_KEY)
      sessionStorage.removeItem(USER_KEY)
      sessionStorage.removeItem(USER_ID_KEY)
    }

    try {
      await authApi.logout()
    } catch {
      // Ignore network errors during logout
    }
  }

  const hasAnyRole = (allowedRoles = []) => {
    if (!allowedRoles.length) return true
    return allowedRoles.includes(role.value)
  }

  return {
    token,
    role,
    user,
    isAuthenticated,
    dashboardPath,
    login,
    logout,
    fetchCurrentUser,
    hasAnyRole
  }
})
