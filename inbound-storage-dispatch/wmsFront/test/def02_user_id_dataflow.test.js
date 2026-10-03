import { test, beforeEach } from 'node:test'
import assert from 'node:assert'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveCurrentUserId } from '../src/composables/useCurrentUserId.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

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
  // 3. authStore.user is absent
  assert.strictEqual(resolveCurrentUserId(null), null)
  assert.strictEqual(resolveCurrentUserId({ user: null }), null)
  assert.strictEqual(resolveCurrentUserId({}), null)

  // 4. user.id is absent/invalid
  assert.strictEqual(resolveCurrentUserId({ user: { id: null } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: undefined } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: '' } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: 0 } }), null, 'Zero ID must be rejected')
  assert.strictEqual(resolveCurrentUserId({ user: { id: -1 } }), null, 'Negative ID must be rejected')
  assert.strictEqual(resolveCurrentUserId({ user: { id: 'not-a-number' } }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: NaN } }), null)
})

test('DEF-02: resolveCurrentUserId NEVER falls back to role-based mock IDs (1, 2, 3)', () => {
  // Roles must never provide fallback IDs when user.id is missing
  assert.strictEqual(resolveCurrentUserId({ user: null, role: 'ROLE_DEV' }), null)
  assert.strictEqual(resolveCurrentUserId({ user: null, role: 'ROLE_SUPERVISOR' }), null)
  assert.strictEqual(resolveCurrentUserId({ user: null, role: 'ROLE_OPERATOR' }), null)
  assert.strictEqual(resolveCurrentUserId({ user: { id: null }, role: 'ROLE_SUPERVISOR' }), null)
})

test('DEF-02: resolveCurrentUserId ignores any legacy localStorage.user_id', () => {
  // Even if localStorage mock exists in global scope
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

test('DEF-02: Inventory adjustment business flow uses authenticated user ID and blocks unauthenticated requests', async () => {
  const executeAdjustment = async (authStore, changedStocks, apiMock, toastMock) => {
    const userId = resolveCurrentUserId(authStore)
    if (!userId) {
      toastMock.add({
        severity: 'error',
        summary: 'Missing user',
        detail: 'User id is required for stock changes.'
      })
      return false
    }

    await Promise.all(
      changedStocks.map((stock) =>
        apiMock.adjustStock(stock.id, {
          newQuantity: stock.quantity,
          manufactureDate: stock.manufactureDate || null,
          expirationDate: stock.expirationDate || null,
          userId,
          reason: 'INVENTORY_MISMATCH',
          comment: null
        })
      )
    )
    return true
  }

  const calls = []
  const apiMock = {
    adjustStock: async (stockId, payload) => {
      calls.push({ stockId, payload })
      return { data: { success: true } }
    }
  }

  const toasts = []
  const toastMock = {
    add: (t) => toasts.push(t)
  }

  // 1. Authenticated user id = 42
  calls.length = 0
  toasts.length = 0
  const success42 = await executeAdjustment(
    { user: { id: 42 } },
    [{ id: 10, quantity: 15 }],
    apiMock,
    toastMock
  )
  assert.strictEqual(success42, true)
  assert.strictEqual(calls.length, 1)
  assert.strictEqual(calls[0].payload.userId, 42, 'Payload must contain authenticated user ID 42')

  // 2. Authenticated user id = 99
  calls.length = 0
  toasts.length = 0
  const success99 = await executeAdjustment(
    { user: { id: 99 } },
    [{ id: 10, quantity: 20 }],
    apiMock,
    toastMock
  )
  assert.strictEqual(success99, true)
  assert.strictEqual(calls.length, 1)
  assert.strictEqual(calls[0].payload.userId, 99, 'Payload must contain authenticated user ID 99')

  // 3. authStore.user is absent -> request NOT sent
  calls.length = 0
  toasts.length = 0
  const failNoUser = await executeAdjustment(
    { user: null },
    [{ id: 10, quantity: 20 }],
    apiMock,
    toastMock
  )
  assert.strictEqual(failNoUser, false)
  assert.strictEqual(calls.length, 0, 'No request should be sent when user is absent')
  assert.strictEqual(toasts.length, 1)
  assert.strictEqual(toasts[0].severity, 'error')

  // 4. user.id is invalid -> request NOT sent
  calls.length = 0
  toasts.length = 0
  const failInvalidId = await executeAdjustment(
    { user: { id: 0 } },
    [{ id: 10, quantity: 20 }],
    apiMock,
    toastMock
  )
  assert.strictEqual(failInvalidId, false)
  assert.strictEqual(calls.length, 0, 'No request should be sent when user.id is invalid')
  assert.strictEqual(toasts.length, 1)

  // 5. Legacy localStorage.user_id present but user is null -> request NOT sent
  const originalLocalStorage = globalThis.localStorage
  globalThis.localStorage = { getItem: (key) => (key === 'user_id' ? '2' : null) }
  try {
    calls.length = 0
    toasts.length = 0
    const failWithLocalStorage = await executeAdjustment(
      { user: null, role: 'ROLE_SUPERVISOR' },
      [{ id: 10, quantity: 20 }],
      apiMock,
      toastMock
    )
    assert.strictEqual(failWithLocalStorage, false)
    assert.strictEqual(calls.length, 0, 'Must NOT send request using legacy localStorage fallback')
  } finally {
    globalThis.localStorage = originalLocalStorage
  }
})

test('DEF-02: Inventory addStock business flow uses authenticated user ID and blocks unauthenticated requests', async () => {
  const executeAddStock = async (authStore, payload, apiMock, toastMock) => {
    const userId = resolveCurrentUserId(authStore)
    if (!userId) {
      toastMock.add({
        severity: 'error',
        summary: 'Missing user',
        detail: 'User id is required for stock changes.'
      })
      return false
    }

    await apiMock.addStock({ ...payload, userId })
    return true
  }

  const calls = []
  const apiMock = {
    addStock: async (payload) => {
      calls.push(payload)
      return { data: { id: 1 } }
    }
  }

  const toasts = []
  const toastMock = { add: (t) => toasts.push(t) }

  // 1. Authenticated user id = 77
  calls.length = 0
  const success77 = await executeAddStock(
    { user: { id: 77 } },
    { productId: 1, locationId: 2, quantity: 5 },
    apiMock,
    toastMock
  )
  assert.strictEqual(success77, true)
  assert.strictEqual(calls.length, 1)
  assert.strictEqual(calls[0].userId, 77)

  // 2. Unauthenticated -> fails closed
  calls.length = 0
  toasts.length = 0
  const failNoUser = await executeAddStock(
    { user: null },
    { productId: 1, locationId: 2, quantity: 5 },
    apiMock,
    toastMock
  )
  assert.strictEqual(failNoUser, false)
  assert.strictEqual(calls.length, 0)
  assert.strictEqual(toasts.length, 1)
})

test('DEF-02: OrderWithLinesForm submission blocks when user is not authenticated', async () => {
  const executeSubmitOrder = async (authStore, formData, apiMock, toastMock) => {
    const userId = resolveCurrentUserId(authStore)
    if (!userId) {
      toastMock.add({
        severity: 'error',
        summary: 'Authentication error',
        detail: 'Authenticated user ID is required to create an order.'
      })
      return false
    }

    const payload = {
      order: {
        logicId: formData.logicId,
        destinationLocationId: formData.location
      },
      lines: formData.lines.map((l) => ({
        orderId: null,
        productId: l.product,
        requestedQuantity: l.quantity
      }))
    }

    await apiMock.create(payload)
    return true
  }

  const calls = []
  const apiMock = {
    create: async (payload) => {
      calls.push(payload)
      return { data: { id: 501 } }
    }
  }

  const toasts = []
  const toastMock = { add: (t) => toasts.push(t) }

  // Authenticated user
  calls.length = 0
  const success = await executeSubmitOrder(
    { user: { id: 42 } },
    { logicId: 'ORD-TEST-001', location: 10, lines: [{ product: 1, quantity: 2 }] },
    apiMock,
    toastMock
  )
  assert.strictEqual(success, true)
  assert.strictEqual(calls.length, 1)

  // Unauthenticated user -> fail closed
  calls.length = 0
  toasts.length = 0
  const fail = await executeSubmitOrder(
    { user: null },
    { logicId: 'ORD-TEST-001', location: 10, lines: [{ product: 1, quantity: 2 }] },
    apiMock,
    toastMock
  )
  assert.strictEqual(fail, false)
  assert.strictEqual(calls.length, 0, 'Order creation request must NOT be sent without authenticated user')
  assert.strictEqual(toasts.length, 1)
  assert.strictEqual(toasts[0].severity, 'error')
})

test('DEF-02: Static analysis verifies no legacy localStorage.user_id or fallback IDs in source files', () => {
  const inventoryViewPath = path.join(__dirname, '../src/views/supervisor/InventoryView.vue')
  const orderFormPath = path.join(__dirname, '../src/components/OrderWithLinesForm.vue')
  const authStorePath = path.join(__dirname, '../src/stores/auth.js')

  const inventoryContent = fs.readFileSync(inventoryViewPath, 'utf-8')
  const orderContent = fs.readFileSync(orderFormPath, 'utf-8')
  const authContent = fs.readFileSync(authStorePath, 'utf-8')

  // Invariant 1: No localStorage.getItem('user_id')
  assert.ok(
    !inventoryContent.includes("localStorage.getItem('user_id')"),
    'InventoryView.vue must not access localStorage.getItem("user_id")'
  )
  assert.ok(
    !orderContent.includes("localStorage.getItem('user_id')"),
    'OrderWithLinesForm.vue must not access localStorage.getItem("user_id")'
  )

  // Invariant 2: No fallback to 1 or 2
  assert.ok(
    !inventoryContent.includes('|| 2'),
    'InventoryView.vue must not have fallback || 2'
  )
  assert.ok(
    !orderContent.includes('|| 1'),
    'OrderWithLinesForm.vue must not have fallback || 1'
  )

  // Invariant 3: No seededUsers mock user IDs in auth.js
  assert.ok(
    !authContent.includes('seededUsers'),
    'auth.js must not reference seededUsers'
  )
  assert.ok(
    !authContent.includes('seededUser?.id'),
    'auth.js must not use seededUser?.id fallback'
  )
})
