import apiClient from '@/api'

const DEFAULT_ROLES = ['ROLE_SUPERVISOR', 'ROLE_OPERATOR', 'ROLE_DEV']

const normalizeRole = (role) => {
  if (!role) return role
  return role.startsWith('ROLE_') ? role : `ROLE_${role}`
}

const isMissingEndpoint = (error) => {
  return error.response && [404, 405].includes(error.response.status)
}

export const userService = {
  async getUsers(params = {}) {
    try {
      const response = await apiClient.get('/supervisor/users', { params })
      return response.data || []
    } catch (error) {
      if (isMissingEndpoint(error)) {
        throw new Error('User listing is not available from the backend yet.')
      }
      throw error
    }
  },

  async createUser(payload) {
    return apiClient.post('/supervisor/users/register', {
      username: payload.username,
      email: payload.email,
      password: payload.password,
      userRole: normalizeRole(payload.role)
    })
  },

  async disableUser(userId) {
    try {
      return await apiClient.patch(`/supervisor/users/${userId}/disable`)
    } catch (error) {
      if (isMissingEndpoint(error)) {
        throw new Error('User disabling is not available from the backend yet.')
      }
      throw error
    }
  },

  async updateUserRole(userId, role) {
    try {
      return await apiClient.patch(`/supervisor/users/${userId}/role`, {
        role: normalizeRole(role),
        userRole: normalizeRole(role)
      })
    } catch (error) {
      if (isMissingEndpoint(error)) {
        throw new Error('Role assignment is not available from the backend yet.')
      }
      throw error
    }
  },

  async getRoles() {
    try {
      const response = await apiClient.get('/supervisor/users/roles')
      return (response.data || DEFAULT_ROLES).map(normalizeRole)
    } catch (error) {
      if (!isMissingEndpoint(error)) {
        throw error
      }
      return DEFAULT_ROLES
    }
  }
}
