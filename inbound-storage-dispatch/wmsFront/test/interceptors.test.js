import { test, beforeEach } from 'node:test'
import assert from 'node:assert'
import {
  setGlobalToast,
  showToast,
  notifyError,
  notifyWarning,
  notifyInfo,
  notifySuccess
} from '../src/services/notificationService.js'
import {
  handle401Unauthorized,
  handle403Forbidden,
  handle409Conflict,
  setupInterceptors
} from '../src/api/interceptors.js'

test('NotificationService - delegates to global toast instance', () => {
  const toasts = []
  setGlobalToast({
    add: (t) => toasts.push(t)
  })

  notifyError('Error Title', 'Error Detail Message')
  assert.strictEqual(toasts.length, 1)
  assert.strictEqual(toasts[0].severity, 'error')
  assert.strictEqual(toasts[0].summary, 'Error Title')
  assert.strictEqual(toasts[0].detail, 'Error Detail Message')
})

test('NotificationService - debounces duplicate identical messages', () => {
  const toasts = []
  setGlobalToast({
    add: (t) => toasts.push(t)
  })

  const first = notifyWarning('Warning Title', 'Duplicate detail text')
  const second = notifyWarning('Warning Title', 'Duplicate detail text')
  const thirdDifferent = notifyWarning('Warning Title', 'Different detail text')

  assert.strictEqual(first, true)
  assert.strictEqual(second, false, 'Second identical message should be debounced')
  assert.strictEqual(thirdDifferent, true, 'Different message should not be debounced')
  assert.strictEqual(toasts.length, 2)
})

test('F-1: handle401Unauthorized logs out user, notifies session expired and redirects to login with redirect query', async () => {
  let logoutCallCount = 0
  const mockAuthStore = {
    logout: async () => {
      logoutCallCount++
    }
  }

  const errorMessages = []
  const mockNotifyError = (summary, detail) => {
    errorMessages.push({ summary, detail })
  }

  const routerPushes = []
  const mockRouter = {
    currentRoute: {
      value: {
        name: 'inventory',
        fullPath: '/supervisor/inventory'
      }
    },
    push: (target) => {
      routerPushes.push(target)
    }
  }

  const error401 = {
    response: {
      status: 401,
      data: { message: 'JWT token has expired' }
    }
  }

  // First 401
  await handle401Unauthorized(error401, {
    getAuthStore: () => mockAuthStore,
    router: mockRouter,
    notifyError: mockNotifyError
  })

  assert.strictEqual(logoutCallCount, 1, '401 Unauthorized must trigger authStore.logout()')
  assert.strictEqual(errorMessages.length, 1)
  assert.strictEqual(errorMessages[0].summary, 'Session Expired')
  assert.strictEqual(routerPushes.length, 1)
  assert.deepStrictEqual(routerPushes[0], {
    name: 'login',
    query: {
      sessionExpired: 'true',
      redirect: '/supervisor/inventory'
    }
  })

  // Second immediate 401 should be debounced and not trigger duplicate logout or redirect
  await handle401Unauthorized(error401, {
    getAuthStore: () => mockAuthStore,
    router: mockRouter,
    notifyError: mockNotifyError
  })

  assert.strictEqual(logoutCallCount, 1, 'Concurrent 401 must be debounced')
  assert.strictEqual(routerPushes.length, 1, 'Concurrent 401 must not trigger duplicate router redirects')
})

test('F-1: handle403Forbidden preserves auth session and displays Access Denied toast', () => {
  let logoutCalled = false
  const mockAuthStore = {
    logout: () => { logoutCalled = true }
  }

  const errorMessages = []
  const mockNotifyError = (summary, detail) => {
    errorMessages.push({ summary, detail })
  }

  const error403 = {
    response: {
      status: 403,
      data: {
        message: 'Operator does not have permission to view supervisor audit logs.'
      }
    }
  }

  handle403Forbidden(error403, {
    notifyError: mockNotifyError
  })

  // INVARIANT: 403 MUST NOT destroy the session
  assert.strictEqual(logoutCalled, false, '403 Forbidden MUST NOT trigger authStore.logout()')
  assert.strictEqual(errorMessages.length, 1)
  assert.strictEqual(errorMessages[0].summary, 'Access Denied')
  assert.strictEqual(errorMessages[0].detail, 'Operator does not have permission to view supervisor audit logs.')
})

test('F-1: handle403Forbidden uses fallback message if backend message is generic or absent', () => {
  const errorMessages = []
  const mockNotifyError = (summary, detail) => {
    errorMessages.push({ summary, detail })
  }

  handle403Forbidden({ response: { status: 403, data: {} } }, { notifyError: mockNotifyError })
  assert.strictEqual(errorMessages.length, 1)
  assert.strictEqual(errorMessages[0].summary, 'Access Denied')
  assert.strictEqual(errorMessages[0].detail, 'You do not have permission to perform this action.')
})

test('F-1: handle409Conflict preserves auth session, displays Conflict warning and dispatches conflict event', () => {
  let logoutCalled = false
  const mockAuthStore = {
    logout: () => { logoutCalled = true }
  }

  const warningMessages = []
  const mockNotifyWarning = (summary, detail) => {
    warningMessages.push({ summary, detail })
  }

  const dispatchedEvents = []
  const mockDispatchEvent = (event) => {
    dispatchedEvents.push(event)
  }

  const error409 = {
    response: {
      status: 409,
      data: {
        message: 'Concurrent modification conflict. The resource was modified by another transaction, please retry.'
      }
    }
  }

  handle409Conflict(error409, {
    notifyWarning: mockNotifyWarning,
    dispatchEvent: mockDispatchEvent
  })

  // INVARIANT: 409 MUST NOT destroy the session
  assert.strictEqual(logoutCalled, false, '409 Conflict MUST NOT trigger authStore.logout()')
  assert.strictEqual(warningMessages.length, 1)
  assert.strictEqual(warningMessages[0].summary, 'Conflict Detected')
  assert.strictEqual(
    warningMessages[0].detail,
    'Concurrent modification conflict. The resource was modified by another transaction, please retry.'
  )

  // Event dispatch check
  assert.strictEqual(dispatchedEvents.length, 1)
  assert.strictEqual(dispatchedEvents[0].type, 'wms:conflict')
  assert.strictEqual(dispatchedEvents[0].detail.status, 409)
})

test('F-1: setupInterceptors wires up response handlers correctly', async () => {
  let onResponseSuccess = null
  let onResponseError = null

  const fakeApiClient = {
    interceptors: {
      response: {
        use: (onSuccess, onError) => {
          onResponseSuccess = onSuccess
          onResponseError = onError
        }
      }
    }
  }

  const warnings = []
  const errors = []
  let logoutCalled = false

  setupInterceptors(fakeApiClient, {
    getAuthStore: () => ({ logout: () => { logoutCalled = true } }),
    router: { currentRoute: { value: { name: 'inventory' } }, push: () => {} },
    notifyError: (s, d) => errors.push({ s, d }),
    notifyWarning: (s, d) => warnings.push({ s, d })
  })

  assert.strictEqual(typeof onResponseSuccess, 'function')
  assert.strictEqual(typeof onResponseError, 'function')

  // 1. Success pass-through
  const goodResponse = { status: 200, data: { items: [1, 2, 3] } }
  assert.strictEqual(onResponseSuccess(goodResponse), goodResponse)

  // 2. 403 handling
  await assert.rejects(
    async () => {
      await onResponseError({
        response: { status: 403, data: { message: 'Forbidden access' } }
      })
    },
    (err) => err.response.status === 403
  )
  assert.strictEqual(errors.length, 1)
  assert.strictEqual(errors[0].s, 'Access Denied')

  // 3. 409 handling
  await assert.rejects(
    async () => {
      await onResponseError({
        response: { status: 409, data: { message: 'Conflict detected' } }
      })
    },
    (err) => err.response.status === 409
  )
  assert.strictEqual(warnings.length, 1)
  assert.strictEqual(warnings[0].s, 'Conflict Detected')

  // 4. 500 error pass-through (no 401/403/409 triggers)
  const prevErrorsCount = errors.length
  const prevWarningsCount = warnings.length
  await assert.rejects(
    async () => {
      await onResponseError({
        response: { status: 500, data: { message: 'Internal Server Error' } }
      })
    },
    (err) => err.response.status === 500
  )
  assert.strictEqual(errors.length, prevErrorsCount, '500 error must not trigger 401/403/409 notification')
  assert.strictEqual(warnings.length, prevWarningsCount, '500 error must not trigger 401/403/409 notification')
})
