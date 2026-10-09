import { test, beforeEach } from 'node:test'
import assert from 'node:assert'
import { createPinia, setActivePinia } from 'pinia'
import { authApi } from '../src/api/authApi.js'
import { useAuthStore } from '../src/stores/auth.js'

test('DEF-22: validateSessionOnReload overwrites tampered sessionStorage role with server-verified role', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)

  // Simulate tampered sessionStorage before reload
  const storage = new Map()
  storage.set('user_role', 'ROLE_SUPERVISOR') // Forged role
  storage.set('user', JSON.stringify({ id: 5, username: 'operator_bob' }))

  const originalWindow = globalThis.window
  const originalSessionStorage = globalThis.sessionStorage
  const mockSessionStorage = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, val) => storage.set(key, String(val)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }

  globalThis.sessionStorage = mockSessionStorage
  globalThis.window = {
    sessionStorage: mockSessionStorage,
    localStorage: { removeItem: () => {}, getItem: () => null, setItem: () => {} }
  }

  // Mock authApi.getMe returning true role: ROLE_OPERATOR
  const originalGetMe = authApi.getMe
  authApi.getMe = async () => ({
    data: {
      id: 5,
      username: 'operator_bob',
      role: 'OPERATOR'
    }
  })

  try {
    const authStore = useAuthStore()
    // Initial client state was spoofed in sessionStorage
    assert.strictEqual(authStore.role, 'ROLE_SUPERVISOR')

    // Execute server verification on reload
    await authStore.validateSessionOnReload()

    // Verified state must be enforced from server response
    assert.strictEqual(authStore.role, 'ROLE_OPERATOR')
    assert.strictEqual(storage.get('user_role'), 'ROLE_OPERATOR')
    assert.strictEqual(authStore.isSessionValidated, true)

    // Supervisor routes must be blocked
    assert.strictEqual(authStore.hasAnyRole(['ROLE_SUPERVISOR', 'ROLE_DEV']), false)
    assert.strictEqual(authStore.hasAnyRole(['ROLE_OPERATOR']), true)
  } finally {
    globalThis.window = originalWindow
    globalThis.sessionStorage = originalSessionStorage
    authApi.getMe = originalGetMe
  }
})

test('DEF-22: validateSessionOnReload clears session and logs out when server rejects credentials', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)

  const storage = new Map()
  storage.set('user_role', 'ROLE_SUPERVISOR')
  storage.set('user', JSON.stringify({ id: 10, username: 'supervisor_alice' }))

  const originalWindow = globalThis.window
  const originalSessionStorage = globalThis.sessionStorage
  const mockSessionStorage = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, val) => storage.set(key, String(val)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }

  globalThis.sessionStorage = mockSessionStorage
  globalThis.window = {
    sessionStorage: mockSessionStorage,
    localStorage: { removeItem: () => {}, getItem: () => null, setItem: () => {} }
  }

  // Mock authApi.getMe rejecting with 401 Unauthorized
  const originalGetMe = authApi.getMe
  const originalLogout = authApi.logout
  authApi.getMe = async () => {
    const error = new Error('Unauthorized')
    error.response = { status: 401 }
    throw error
  }
  authApi.logout = async () => {}

  try {
    const authStore = useAuthStore()
    assert.strictEqual(authStore.isAuthenticated, true)

    await authStore.validateSessionOnReload()

    // Must fail-closed: user logged out, session cleared
    assert.strictEqual(authStore.isAuthenticated, false)
    assert.strictEqual(authStore.user, null)
    assert.strictEqual(authStore.role, null)
    assert.strictEqual(storage.has('user_role'), false)
    assert.strictEqual(storage.has('user'), false)
  } finally {
    globalThis.window = originalWindow
    globalThis.sessionStorage = originalSessionStorage
    authApi.getMe = originalGetMe
    authApi.logout = originalLogout
  }
})
