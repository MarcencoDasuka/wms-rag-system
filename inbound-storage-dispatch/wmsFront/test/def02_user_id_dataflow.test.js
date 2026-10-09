import { test } from 'node:test'
import assert from 'node:assert'
import { reactive, ref } from 'vue'
import { resolveCurrentUserId, useCurrentUserId } from '../src/composables/useCurrentUserId.js'

test('DEF-02: resolveCurrentUserId strictly returns authenticated user ID', () => {
  // 1. Authenticated user id = X
  const storeX = { user: { id: 42, username: 'supervisor_alice' }, role: 'ROLE_SUPERVISOR' }
  assert.strictEqual(resolveCurrentUserId(storeX), 42)

  // 2. Authenticated user id = Y
  const storeY = { user: { id: 99, username: 'supervisor_bob' }, role: 'ROLE_SUPERVISOR' }
  assert.strictEqual(resolveCurrentUserId(storeY), 99)

  // String numeric ID coercion
  const storeStr = { user: { id: '105', username: 'dev_carol' }, role: 'ROLE_DEV' }
  assert.strictEqual(resolveCurrentUserId(storeStr), 105)
})

test('DEF-02: resolveCurrentUserId fails closed when user or user.id is absent/invalid', () => {
  // authStore or user is absent
  assert.strictEqual(resolveCurrentUserId(null), null)
  assert.strictEqual(resolveCurrentUserId({ user: null }), null)
  assert.strictEqual(resolveCurrentUserId({}), null)

  // user.id is absent/invalid
  assert.strictEqual(resolveCurrentUserId({ user: { id: null } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: undefined } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: '' } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: 0 } }), null, 'Zero ID must be rejected')
  assert.strictEqual(resolveCurrentUserId({ user: { id: -1 } }), null, 'Negative ID must be rejected')
  assert.strictEqual(resolveCurrentUserId({ user: { id: 'not-a-number' } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: NaN } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: 12.34 } }), null, 'Non-integer float ID must be rejected')
})

test('DEF-02: resolveCurrentUserId NEVER falls back to role-based mock IDs (1, 2, 3)', () => {
  // Roles must never provide fallback IDs when user.id is missing
  assert.strictEqual(resolveCurrentUserId({ user: null, role: 'ROLE_DEV' }), null)
  assert.strictEqual(resolveCurrentUserId({ user: null, role: 'ROLE_SUPERVISOR' }), null)
  assert.strictEqual(resolveCurrentUserId({ user: null, role: 'ROLE_OPERATOR' }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: null }, role: 'ROLE_SUPERVISOR' }), null)
})

test('DEF-02: resolveCurrentUserId ignores any legacy localStorage.user_id', () => {
  const originalLocalStorage = globalThis.localStorage
  globalThis.localStorage = {
    getItem: (key) => (key === 'user_id' ? '1' : null)
  }

  try {
    const store = { user: null, role: 'ROLE_SUPERVISOR' }
    assert.strictEqual(resolveCurrentUserId(store), null, 'Must ignore localStorage.user_id')
  } finally {
    globalThis.localStorage = originalLocalStorage
  }
})

test('DEF-02: useCurrentUserId composable produces dynamic getter tracking store lifecycle', () => {
  const store = reactive({
    user: null,
    role: null
  })

  const currentUserId = useCurrentUserId(store)
  assert.strictEqual(typeof currentUserId, 'function')

  // 1. Initially unauthenticated
  assert.strictEqual(currentUserId(), null, 'Should return null when user is null')

  // 2. User logs in
  store.user = { id: 101, username: 'operator1' }
  store.role = 'ROLE_OPERATOR'
  assert.strictEqual(currentUserId(), 101, 'Should resolve authenticated ID after login')

  // 3. User switches / updates profile
  store.user = { id: 202, username: 'supervisor1' }
  store.role = 'ROLE_SUPERVISOR'
  assert.strictEqual(currentUserId(), 202, 'Should dynamically reflect updated user ID')

  // 4. User logs out
  store.user = null
  store.role = null
  assert.strictEqual(currentUserId(), null, 'Should return null after logout')
})

test('DEF-02: useCurrentUserId composable fails closed when reactive user id becomes invalid', () => {
  const store = reactive({
    user: { id: 42, username: 'valid_user' }
  })

  const currentUserId = useCurrentUserId(store)
  assert.strictEqual(currentUserId(), 42)

  // Mutate to zero ID
  store.user.id = 0
  assert.strictEqual(currentUserId(), null, 'Zero ID must fail closed')

  // Mutate to empty string
  store.user.id = ''
  assert.strictEqual(currentUserId(), null, 'Empty string ID must fail closed')

  // Mutate to negative ID
  store.user.id = -5
  assert.strictEqual(currentUserId(), null, 'Negative ID must fail closed')

  // Mutate to non-integer float
  store.user.id = 42.9
  assert.strictEqual(currentUserId(), null, 'Non-integer float must fail closed')

  // Restore valid integer string
  store.user.id = '77'
  assert.strictEqual(currentUserId(), 77, 'String numeric ID must coerce to valid integer')
})

test('DEF-02: useCurrentUserId works with ref-wrapped auth state', () => {
  const userRef = ref(null)
  const store = {
    get user() {
      return userRef.value
    }
  }

  const currentUserId = useCurrentUserId(store)
  assert.strictEqual(currentUserId(), null)

  userRef.value = { id: 88, username: 'ref_user' }
  assert.strictEqual(currentUserId(), 88)

  userRef.value = null
  assert.strictEqual(currentUserId(), null)
})
