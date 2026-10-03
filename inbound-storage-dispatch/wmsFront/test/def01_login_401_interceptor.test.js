import { test } from 'node:test'
import assert from 'node:assert'
import {
  handle401Unauthorized,
  isAuthLoginRequest,
  setupInterceptors,
  _reset401Debounce
} from '../src/api/interceptors.js'

test('DEF-01: isAuthLoginRequest accurately identifies login authentication requests', () => {
  // Valid POST login variations
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/login', method: 'post' }), true)
  assert.strictEqual(isAuthLoginRequest({ url: '/api/auth/login', method: 'POST' }), true)
  assert.strictEqual(isAuthLoginRequest({ url: 'auth/login', method: 'post' }), true)
  assert.strictEqual(isAuthLoginRequest({ url: 'http://localhost:8080/api/auth/login', method: 'post' }), true)
  assert.strictEqual(isAuthLoginRequest({ url: 'https://warehouse.local/api/auth/login', method: 'POST' }), true)
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/login?source=mobile', method: 'post' }), true)
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/login#form', method: 'post' }), true)

  // Protected or non-login auth endpoints must NOT be treated as login
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/me', method: 'get' }), false)
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/logout', method: 'post' }), false)
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/verify', method: 'post' }), false)
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/register', method: 'post' }), false)

  // Protected resource endpoints
  assert.strictEqual(isAuthLoginRequest({ url: '/v1/orders', method: 'get' }), false)
  assert.strictEqual(isAuthLoginRequest({ url: '/inventory', method: 'get' }), false)
  assert.strictEqual(isAuthLoginRequest({ url: '/inventory/add', method: 'post' }), false)

  // Method mismatch (GET to /auth/login is not credentials submission)
  assert.strictEqual(isAuthLoginRequest({ url: '/auth/login', method: 'get' }), false)

  // Subpath false positives
  assert.strictEqual(isAuthLoginRequest({ url: '/orders/auth/login/items', method: 'post' }), false)
  assert.strictEqual(isAuthLoginRequest(null), false)
  assert.strictEqual(isAuthLoginRequest({}), false)
})

test('DEF-01: 401 on /auth/login does NOT trigger logout or session-expired flow', async () => {
  let logoutCalled = false
  const mockAuthStore = {
    logout: async () => {
      logoutCalled = true
    }
  }

  const errorNotifications = []
  const mockNotifyError = (summary, detail) => {
    errorNotifications.push({ summary, detail })
  }

  const routerPushes = []
  const mockRouter = {
    currentRoute: { value: { name: 'login', fullPath: '/login' } },
    push: (target) => routerPushes.push(target)
  }

  const login401Error = {
    config: {
      url: '/auth/login',
      method: 'post'
    },
    response: {
      status: 401,
      data: { error: 'Bad credentials' }
    }
  }

  await handle401Unauthorized(login401Error, {
    getAuthStore: () => mockAuthStore,
    router: mockRouter,
    notifyError: mockNotifyError
  })

  // INVARIANT: Login failure must NEVER trigger logout or session-expired notification
  assert.strictEqual(logoutCalled, false, 'Login 401 must NOT trigger authStore.logout()')
  assert.strictEqual(errorNotifications.length, 0, 'Login 401 must NOT show Session Expired toast')
  assert.strictEqual(routerPushes.length, 0, 'Login 401 must NOT redirect router')
})

test('DEF-01: 401 on protected endpoints DOES trigger existing session-expired behavior', async () => {
  let logoutCalled = false
  const mockAuthStore = {
    logout: async () => {
      logoutCalled = true
    }
  }

  const errorNotifications = []
  const mockNotifyError = (summary, detail) => {
    errorNotifications.push({ summary, detail })
  }

  const routerPushes = []
  const mockRouter = {
    currentRoute: { value: { name: 'inventory', fullPath: '/supervisor/inventory' } },
    push: (target) => routerPushes.push(target)
  }

  const protectedEndpoints = [
    { url: '/v1/orders/extended', method: 'get' },
    { url: '/inventory', method: 'get' },
    { url: '/auth/me', method: 'get' },
    { url: '/inventory/add', method: 'post' }
  ]

  for (const ep of protectedEndpoints) {
    _reset401Debounce()
    logoutCalled = false
    errorNotifications.length = 0
    routerPushes.length = 0

    const error = {
      config: ep,
      response: {
        status: 401,
        data: { message: 'JWT token expired' }
      }
    }

    await handle401Unauthorized(error, {
      getAuthStore: () => mockAuthStore,
      router: mockRouter,
      notifyError: mockNotifyError
    })

    assert.strictEqual(logoutCalled, true, `401 on ${ep.method} ${ep.url} MUST trigger logout()`)
    assert.strictEqual(errorNotifications.length, 1, `401 on ${ep.method} ${ep.url} MUST notify session expired`)
    assert.strictEqual(errorNotifications[0].summary, 'Session Expired')
    assert.strictEqual(routerPushes.length, 1)
    assert.deepStrictEqual(routerPushes[0], {
      name: 'login',
      query: { sessionExpired: 'true', redirect: '/supervisor/inventory' }
    })
  }
})

test('DEF-01: setupInterceptors wires Axios interceptor to preserve login 401 error', async () => {
  let logoutCalled = false
  const mockAuthStore = {
    logout: async () => {
      logoutCalled = true
    }
  }

  const errorNotifications = []
  const mockNotifyError = (summary, detail) => {
    errorNotifications.push({ summary, detail })
  }

  let rejectedHandler = null
  const mockAxiosInstance = {
    interceptors: {
      response: {
        use: (onSuccess, onError) => {
          rejectedHandler = onError
        }
      }
    }
  }

  setupInterceptors(mockAxiosInstance, {
    getAuthStore: () => mockAuthStore,
    router: { currentRoute: { value: { name: 'login' } }, push: () => {} },
    notifyError: mockNotifyError
  })

  assert.ok(rejectedHandler, 'setupInterceptors must register response error interceptor')

  // 1. Rejection from POST /auth/login
  const loginError = {
    config: { url: '/auth/login', method: 'post' },
    response: { status: 401, data: { message: 'Bad credentials' } }
  }

  await assert.rejects(
    async () => {
      await rejectedHandler(loginError)
    },
    (err) => {
      assert.strictEqual(err, loginError)
      return true
    }
  )

  assert.strictEqual(logoutCalled, false, 'Axios interceptor must NOT trigger logout on login 401')
  assert.strictEqual(errorNotifications.length, 0, 'Axios interceptor must NOT notify session expired on login 401')

  // 2. Rejection from protected GET /orders
  _reset401Debounce()
  logoutCalled = false
  errorNotifications.length = 0
  const ordersError = {
    config: { url: '/v1/orders', method: 'get' },
    response: { status: 401, data: { message: 'Token expired' } }
  }

  await assert.rejects(
    async () => {
      await rejectedHandler(ordersError)
    },
    (err) => {
      assert.strictEqual(err, ordersError)
      return true
    }
  )

  assert.strictEqual(logoutCalled, true, 'Axios interceptor MUST trigger logout on protected 401')
  assert.strictEqual(errorNotifications.length, 1, 'Axios interceptor MUST notify session expired on protected 401')
})
