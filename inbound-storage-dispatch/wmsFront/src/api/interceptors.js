/**
 * Centralized HTTP error interceptors for handling 401, 403, and 409 responses.
 */

let isHandling401 = false
let last401Timestamp = 0
const DEBOUNCE_401_MS = 2000

export const _reset401Debounce = () => {
  isHandling401 = false
  last401Timestamp = 0
}

/**
 * Determines if a request was an authentication attempt to the login endpoint.
 * Remediates DEF-01: prevents credential rejection from triggering session expiry flow.
 *
 * @param {object} [config]
 * @returns {boolean}
 */
export const isAuthLoginRequest = (config) => {
  if (!config || !config.url) return false
  const method = (config.method || 'get').toLowerCase()
  if (method !== 'post') return false

  try {
    if (/^https?:\/\//i.test(config.url)) {
      const parsed = new URL(config.url)
      const pathname = parsed.pathname.replace(/\/+$/, '')
      return pathname.endsWith('/auth/login')
    }
  } catch {
    // Ignore URL parse error and fall back to string parsing
  }

  const cleanPath = config.url.split('?')[0].split('#')[0].replace(/\/+$/, '')
  return cleanPath.endsWith('/auth/login') || cleanPath === 'auth/login'
}

export const handle401Unauthorized = async (error, { getAuthStore, router, notifyError } = {}) => {
  // DEF-01: Rejecting invalid login credentials must NOT trigger session expiration or logout
  if (isAuthLoginRequest(error?.config)) {
    return
  }

  const now = Date.now()
  if (isHandling401 && now - last401Timestamp < DEBOUNCE_401_MS) {
    return
  }
  isHandling401 = true
  last401Timestamp = now

  try {
    const authStore = typeof getAuthStore === 'function' ? getAuthStore() : null
    if (authStore && typeof authStore.logout === 'function') {
      await authStore.logout()
    }
  } catch {
    // Ignore store cleanup failures during expired session
  }

  if (typeof notifyError === 'function') {
    notifyError('Session Expired', 'Your session has expired or is invalid. Please log in again.')
  }

  try {
    if (router && router.currentRoute && router.currentRoute.value) {
      const currentRoute = router.currentRoute.value
      if (currentRoute.name !== 'login') {
        const redirectPath = currentRoute.fullPath && currentRoute.fullPath !== '/'
          ? currentRoute.fullPath
          : undefined

        router.push({
          name: 'login',
          query: {
            sessionExpired: 'true',
            ...(redirectPath ? { redirect: redirectPath } : {})
          }
        })
      }
    }
  } catch {
    // Ignore navigation errors
  } finally {
    setTimeout(() => {
      isHandling401 = false
    }, DEBOUNCE_401_MS)
  }
}

export const handle403Forbidden = (error, { notifyError } = {}) => {
  // INVARIANT: 403 Forbidden MUST NOT destroy the active user session or log the user out!
  const backendMessage = error?.response?.data?.message || error?.response?.data?.error
  const detail = backendMessage && backendMessage !== 'Access denied'
    ? backendMessage
    : 'You do not have permission to perform this action.'

  if (typeof notifyError === 'function') {
    notifyError('Access Denied', detail)
  }
}

export const handle409Conflict = (error, { notifyWarning, dispatchEvent } = {}) => {
  // INVARIANT: 409 Conflict represents concurrency or business collision; session is preserved.
  const backendMessage = error?.response?.data?.message || error?.response?.data?.error
  const detail = backendMessage || 'A data conflict occurred. Another user or process may have updated this resource. Please refresh and try again.'

  if (typeof notifyWarning === 'function') {
    notifyWarning('Conflict Detected', detail)
  }

  const dispatch = dispatchEvent || (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function' ? window.dispatchEvent.bind(window) : null)
  if (typeof dispatch === 'function') {
    try {
      const event = typeof CustomEvent !== 'undefined'
        ? new CustomEvent('wms:conflict', { detail: { message: detail, status: 409, data: error?.response?.data } })
        : { type: 'wms:conflict', detail: { message: detail, status: 409, data: error?.response?.data } }
      dispatch(event)
    } catch {
      // Ignore event dispatch failure in non-browser environment
    }
  }
}

export const setupInterceptors = (apiClient, { getAuthStore, router, getRouter, notifyError, notifyWarning } = {}) => {
  apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error && error.response) {
        const status = error.response.status
        if (status === 401) {
          if (!isAuthLoginRequest(error.config)) {
            const activeRouter = typeof getRouter === 'function' ? getRouter() : router
            await handle401Unauthorized(error, { getAuthStore, router: activeRouter, notifyError })
          }
        } else if (status === 403) {
          handle403Forbidden(error, { notifyError })
        } else if (status === 409) {
          handle409Conflict(error, { notifyWarning })
        }
      }
      return Promise.reject(error)
    }
  )
}
