import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { authApi } from '@/api/authApi'

const TOKEN_KEY = 'jwt_token'
const ROLE_KEY = 'user_role'
const USER_KEY = 'user'
const USER_ID_KEY = 'user_id'

const seededUsers = {
  dev: { id: 1, role: 'ROLE_DEV' },
  supervisor: { id: 2, role: 'ROLE_SUPERVISOR' },
  operator: { id: 3, role: 'ROLE_OPERATOR' }
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
  return normalizeRole(seededUsers[username]?.role)
}

const safeDashboardForRole = (role) => {
  if (role === 'ROLE_DEV') return '/dev'
  if (role === 'ROLE_SUPERVISOR') return '/supervisor'
  if (role === 'ROLE_OPERATOR') return '/operator'
  return '/login'
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || null)
  const role = ref(localStorage.getItem(ROLE_KEY) || null)
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'))

  const isAuthenticated = computed(() => !!token.value)
  const dashboardPath = computed(() => safeDashboardForRole(role.value))

  const persistAuth = (authData) => {
    token.value = authData.token
    role.value = authData.role
    user.value = authData.user

    localStorage.setItem(TOKEN_KEY, token.value)
    localStorage.setItem(ROLE_KEY, role.value)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))

    if (user.value?.id) {
      localStorage.setItem(USER_ID_KEY, user.value.id)
    } else {
      localStorage.removeItem(USER_ID_KEY)
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
    const seededUser = seededUsers[authenticatedUsername]
    const detectedRole = normalizeRole(response.data?.role || claims.role || claims.authorities?.[0] || inferRoleFromUsername(authenticatedUsername))

    if (!detectedRole) {
      throw new Error('Authenticated role is not available in the backend response.')
    }

    const authUser = response.data?.user || {
      id: response.data?.userId || seededUser?.id || null,
      username: authenticatedUsername
    }

    persistAuth({ token: responseToken, role: detectedRole, user: authUser })

    return { token: responseToken, role: detectedRole, user: authUser }
  }

  const logout = () => {
    token.value = null
    role.value = null
    user.value = null

    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(ROLE_KEY)
    localStorage.removeItem(USER_KEY)
    localStorage.removeItem(USER_ID_KEY)
  }

  const hasAnyRole = (allowedRoles = []) => {
    if (!allowedRoles.length) return true
    return allowedRoles.includes(role.value)
  }

  return { token, role, user, isAuthenticated, dashboardPath, login, logout, hasAnyRole }
})
