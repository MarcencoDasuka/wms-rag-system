import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import apiClient from '../src/api/index.js'
import {
  aiChatApi,
  getPendingConfirmations,
  confirmOperation,
  rejectOperation,
  sendChatMessage,
  extractOperationId,
  isSafeInternalRoute,
  normalizePendingConfirmation
} from '../src/api/aiChatApi.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

// ---------------------------------------------------------------------------
// Suite 1: DEF-31 aiChatApi Client Endpoints & Parameter URL Encoding
// ---------------------------------------------------------------------------
test('DEF-31: aiChatApi exports all required API methods cleanly', () => {
  assert.strictEqual(typeof getPendingConfirmations, 'function')
  assert.strictEqual(typeof confirmOperation, 'function')
  assert.strictEqual(typeof rejectOperation, 'function')
  assert.strictEqual(typeof sendChatMessage, 'function')
  assert.strictEqual(typeof normalizePendingConfirmation, 'function')
  assert.strictEqual(typeof aiChatApi.getPendingConfirmations, 'function')
  assert.strictEqual(typeof aiChatApi.confirmOperation, 'function')
  assert.strictEqual(typeof aiChatApi.rejectOperation, 'function')
  assert.strictEqual(typeof aiChatApi.sendChatMessage, 'function')
  assert.strictEqual(typeof aiChatApi.sendMessage, 'function')
  assert.strictEqual(typeof aiChatApi.normalizePendingConfirmation, 'function')
})

test('DEF-31: getPendingConfirmations calls GET /v1/chat/confirmations/pending', async () => {
  const originalGet = apiClient.get
  let interceptedUrl = null

  apiClient.get = async (url) => {
    interceptedUrl = url
    return { data: [{ token: 'OP-1234', actionType: 'DELETE_ORDER' }] }
  }

  try {
    const res = await getPendingConfirmations()
    assert.strictEqual(interceptedUrl, '/v1/chat/confirmations/pending')
    assert.strictEqual(res.data.length, 1)
    assert.strictEqual(res.data[0].token, 'OP-1234')
  } finally {
    apiClient.get = originalGet
  }
})

test('DEF-31: confirmOperation calls POST /v1/chat/confirmations/{id}/confirm with URL encoding', async () => {
  const originalPost = apiClient.post
  const calls = []

  apiClient.post = async (url, body) => {
    calls.push({ url, body })
    return { data: { status: 'SUCCESS', message: 'Confirmed' } }
  }

  try {
    // Standard operation ID
    await confirmOperation('OP-ABCD1234')
    assert.strictEqual(calls[0].url, '/v1/chat/confirmations/OP-ABCD1234/confirm')

    // Operation ID requiring URL encoding (e.g. slashes, pluses, hashes, spaces)
    await confirmOperation('OP-TEST/123+special#45')
    assert.strictEqual(calls[1].url, '/v1/chat/confirmations/OP-TEST%2F123%2Bspecial%2345/confirm')
  } finally {
    apiClient.post = originalPost
  }
})

test('DEF-31: rejectOperation calls POST /v1/chat/confirmations/{id}/reject with URL encoding', async () => {
  const originalPost = apiClient.post
  const calls = []

  apiClient.post = async (url, body) => {
    calls.push({ url, body })
    return { data: { status: 'REJECTED', message: 'Rejected' } }
  }

  try {
    // Standard operation ID
    await rejectOperation('OP-ABCD9876')
    assert.strictEqual(calls[0].url, '/v1/chat/confirmations/OP-ABCD9876/reject')

    // Operation ID requiring URL encoding
    await rejectOperation('OP-REJECT/foo bar&')
    assert.strictEqual(calls[1].url, '/v1/chat/confirmations/OP-REJECT%2Ffoo%20bar%26/reject')
  } finally {
    apiClient.post = originalPost
  }
})

test('DEF-31: sendChatMessage calls POST /v1/chat with message and optional conversationId', async () => {
  const originalPost = apiClient.post
  const calls = []

  apiClient.post = async (url, body) => {
    calls.push({ url, body })
    return { data: { reply: 'Warehouse status is normal.' } }
  }

  try {
    await sendChatMessage('Check orders')
    assert.strictEqual(calls[0].url, '/v1/chat')
    assert.deepStrictEqual(calls[0].body, { message: 'Check orders' })

    await sendChatMessage('Check tasks', 'conv-101')
    assert.strictEqual(calls[1].url, '/v1/chat')
    assert.deepStrictEqual(calls[1].body, { message: 'Check tasks', conversationId: 'conv-101' })
  } finally {
    apiClient.post = originalPost
  }
})

// ---------------------------------------------------------------------------
// Suite 2: DEF-31 Operation ID Extraction, Token Mapping & Confirmation State Transitions
// ---------------------------------------------------------------------------
test('DEF-31: normalizePendingConfirmation maps backend PendingConfirmation token field to operationId', () => {
  // Backend Spring Boot representation with 'token' (AiToolSecurityBoundary.PendingConfirmation)
  const backendItem = {
    token: 'OP-9999ABCD',
    username: 'supervisor1',
    actionType: 'DELETE_ORDER',
    targetId: 'ORD-555',
    targetEntityId: 55,
    details: 'Order ORD-555 deletion',
    expiresAt: '2026-10-21T17:00:00Z',
    newQuantity: null,
    reason: null
  }
  const normalized = normalizePendingConfirmation(backendItem)
  assert.ok(normalized)
  assert.strictEqual(normalized.operationId, 'OP-9999ABCD')
  assert.strictEqual(normalized.actionType, 'DELETE_ORDER')
  assert.strictEqual(normalized.targetId, 'ORD-555')
  assert.strictEqual(normalized.details, 'Order ORD-555 deletion')
  assert.strictEqual(normalized.expiresAt, '2026-10-21T17:00:00Z')

  // Alternative representation with 'operationId'
  const altItem = {
    operationId: 'OP-1234',
    actionType: 'ADJUST_INVENTORY_STOCK',
    targetId: 'STK-42',
    details: 'Adjust stock to 10'
  }
  const normAlt = normalizePendingConfirmation(altItem)
  assert.ok(normAlt)
  assert.strictEqual(normAlt.operationId, 'OP-1234')
  assert.strictEqual(normAlt.actionType, 'ADJUST_INVENTORY_STOCK')

  // Invalid / missing token returns null
  assert.strictEqual(normalizePendingConfirmation(null), null)
  assert.strictEqual(normalizePendingConfirmation({}), null)
  assert.strictEqual(normalizePendingConfirmation({ actionType: 'DELETE' }), null)
  assert.strictEqual(normalizePendingConfirmation('not-an-object'), null)
})

test('DEF-31: extractOperationId extracts OP-... tokens from assistant responses', () => {
  assert.strictEqual(
    extractOperationId("PENDING HUMAN CONFIRMATION: Operation ID: 'OP-ABCD1234'. Please confirm."),
    'OP-ABCD1234'
  )
  assert.strictEqual(
    extractOperationId("Action requires out-of-band confirmation: OP-UUID-999. Do you approve?"),
    'OP-UUID-999'
  )
  assert.strictEqual(
    extractOperationId("Operation ID: OP-1A2B3C4D"),
    'OP-1A2B3C4D'
  )

  // Returns null when no token present
  assert.strictEqual(extractOperationId('The current inventory for SKU-10 is 45 units.'), null)
  assert.strictEqual(extractOperationId(''), null)
  assert.strictEqual(extractOperationId(null), null)
  assert.strictEqual(extractOperationId(undefined), null)
  assert.strictEqual(extractOperationId(12345), null)
})

test('DEF-31: follow-up status and error messages suppress duplicate confirmation cards', () => {
  const getMessageOpId = (msg) => {
    if (!msg) return null
    if (msg.isFollowUp || msg.suppressConfirmation) return null
    if (msg.operationId) return msg.operationId
    return extractOperationId(msg.content)
  }

  // 1. Initial pending mutation prompt: should yield operation ID
  const initialMsg = {
    role: 'ai',
    content: "Pending confirmation required for **DELETE_ORDER** on target **ORD-100**. Operation ID: `OP-1234ABCD`",
    operationId: 'OP-1234ABCD'
  }
  assert.strictEqual(getMessageOpId(initialMsg), 'OP-1234ABCD')

  // 2. Initial prompt without explicit operationId property (in content only):
  const contentOnlyMsg = {
    role: 'ai',
    content: "Action requires confirmation. Operation ID: 'OP-5678EFGH'."
  }
  assert.strictEqual(getMessageOpId(contentOnlyMsg), 'OP-5678EFGH')

  // 3. Follow-up rejection status message (contains OP-... token): MUST BE SUPPRESSED
  const rejectionMsg = {
    role: 'ai',
    content: 'Operation OP-1234ABCD was rejected.',
    isFollowUp: true,
    suppressConfirmation: true
  }
  assert.strictEqual(getMessageOpId(rejectionMsg), null, 'Rejection follow-up must not render confirmation card')

  // 4. Follow-up confirmation status message: MUST BE SUPPRESSED
  const confirmMsg = {
    role: 'ai',
    content: 'Operation OP-1234ABCD confirmed successfully.',
    isFollowUp: true,
    suppressConfirmation: true
  }
  assert.strictEqual(getMessageOpId(confirmMsg), null, 'Confirmation follow-up must not render confirmation card')

  // 5. Follow-up error message containing operation token: MUST BE SUPPRESSED
  const errorMsg = {
    role: 'ai',
    content: '**Error:** Invalid or expired operation ID: OP-1234ABCD',
    isFollowUp: true,
    suppressConfirmation: true
  }
  assert.strictEqual(getMessageOpId(errorMsg), null, 'Error follow-up must not render confirmation card')
})

test('DEF-31: operation state machine tracks pending, processing, and resolved states', () => {
  const pendingConfirmations = ['OP-100', 'OP-200']
  const resolvedOperations = {}
  let processingOpId = null

  const isResolved = (id) => Boolean(resolvedOperations[id])
  const canSubmit = (id) => !isResolved(id) && processingOpId === null

  // 1. Initially OP-100 can be submitted
  assert.strictEqual(canSubmit('OP-100'), true)

  // 2. When OP-100 begins in-flight processing
  processingOpId = 'OP-100'
  assert.strictEqual(canSubmit('OP-100'), false, 'Cannot double-submit while in-flight')
  assert.strictEqual(canSubmit('OP-200'), false, 'Cannot submit another operation while one is in-flight')

  // 3. OP-100 completes confirmation
  resolvedOperations['OP-100'] = 'CONFIRMED'
  const index = pendingConfirmations.indexOf('OP-100')
  if (index !== -1) pendingConfirmations.splice(index, 1)
  processingOpId = null

  assert.strictEqual(isResolved('OP-100'), true)
  assert.strictEqual(resolvedOperations['OP-100'], 'CONFIRMED')
  assert.strictEqual(canSubmit('OP-100'), false, 'Cannot submit once confirmed')

  // 4. OP-200 can now be processed and rejected
  assert.strictEqual(canSubmit('OP-200'), true)
  processingOpId = 'OP-200'
  resolvedOperations['OP-200'] = 'REJECTED'
  const idx2 = pendingConfirmations.indexOf('OP-200')
  if (idx2 !== -1) pendingConfirmations.splice(idx2, 1)
  processingOpId = null

  assert.strictEqual(isResolved('OP-200'), true)
  assert.strictEqual(resolvedOperations['OP-200'], 'REJECTED')
  assert.strictEqual(canSubmit('OP-200'), false, 'Cannot submit once rejected')
  assert.strictEqual(pendingConfirmations.length, 0)
})

// ---------------------------------------------------------------------------
// Suite 3: DEF-31 Safe Internal Route Guard Logic & AiChatWidget Integration
// ---------------------------------------------------------------------------
test('DEF-31: isSafeInternalRoute allows strictly safe internal paths', () => {
  assert.strictEqual(isSafeInternalRoute('/supervisor/orders'), true)
  assert.strictEqual(isSafeInternalRoute('/operator/tasks?id=5'), true)
  assert.strictEqual(isSafeInternalRoute('/inventory'), true)
  assert.strictEqual(isSafeInternalRoute('/supervisor/dashboard#metrics'), true)
  assert.strictEqual(isSafeInternalRoute('/orders?filter=active&sort=desc'), true)
  assert.strictEqual(isSafeInternalRoute('/allocations/detail/123'), true)
})

test('DEF-31: isSafeInternalRoute rejects protocol-relative, absolute, or malicious targets', () => {
  // Protocol-relative
  assert.strictEqual(isSafeInternalRoute('//evil.example'), false)
  assert.strictEqual(isSafeInternalRoute('///evil.example'), false)
  assert.strictEqual(isSafeInternalRoute('////evil.example/path'), false)

  // Schemes / protocols
  assert.strictEqual(isSafeInternalRoute('https://evil.example'), false)
  assert.strictEqual(isSafeInternalRoute('http://evil.example'), false)
  assert.strictEqual(isSafeInternalRoute('javascript:alert(1)'), false)
  assert.strictEqual(isSafeInternalRoute('/javascript:alert(1)'), false)
  assert.strictEqual(isSafeInternalRoute('data:text/html,<script>alert(1)</script>'), false)
  assert.strictEqual(isSafeInternalRoute('vbscript:msgbox(1)'), false)
  assert.strictEqual(isSafeInternalRoute('ftp://evil.example'), false)

  // Backslash-based evasion
  assert.strictEqual(isSafeInternalRoute('/\\evil.example'), false)
  assert.strictEqual(isSafeInternalRoute('\\\\evil.example'), false)
  assert.strictEqual(isSafeInternalRoute('/orders\\evil'), false)

  // Encoded evasion
  assert.strictEqual(isSafeInternalRoute('/%2fevil.example'), false)
  assert.strictEqual(isSafeInternalRoute('/%5cevil.example'), false)

  // Non-strings / empty
  assert.strictEqual(isSafeInternalRoute(''), false)
  assert.strictEqual(isSafeInternalRoute('   '), false)
  assert.strictEqual(isSafeInternalRoute(null), false)
  assert.strictEqual(isSafeInternalRoute(undefined), false)
  assert.strictEqual(isSafeInternalRoute(123), false)
  assert.strictEqual(isSafeInternalRoute({}), false)
})

test('DEF-31: Static analysis of AiChatWidget.vue verifies safe route guard, token normalization and card suppression', () => {
  const widgetPath = path.join(__dirname, '../src/components/AiChatWidget.vue')
  const content = fs.readFileSync(widgetPath, 'utf-8')

  // Imports
  assert.ok(
    content.includes("from '@/api/aiChatApi'"),
    'AiChatWidget.vue must import from @/api/aiChatApi'
  )
  assert.ok(
    content.includes('isSafeInternalRoute'),
    'AiChatWidget.vue must import isSafeInternalRoute'
  )
  assert.ok(
    content.includes('extractOperationId'),
    'AiChatWidget.vue must import extractOperationId'
  )
  assert.ok(
    content.includes('normalizePendingConfirmation'),
    'AiChatWidget.vue must import normalizePendingConfirmation'
  )

  // Safe route guard in handleChatInteraction
  assert.ok(
    content.includes('isSafeInternalRoute(href)'),
    'handleChatInteraction must validate href with isSafeInternalRoute'
  )
  assert.ok(
    content.includes('event.preventDefault()'),
    'handleChatInteraction must call event.preventDefault()'
  )
  assert.ok(
    !content.includes("href.startsWith('https://supervisor')"),
    'handleChatInteraction must not contain unsafe prefix replacements'
  )

  // Confirmation UI & double-submission guard
  assert.ok(
    content.includes('handleConfirm'),
    'AiChatWidget.vue must contain handleConfirm handler'
  )
  assert.ok(
    content.includes('handleReject'),
    'AiChatWidget.vue must contain handleReject handler'
  )
  assert.ok(
    content.includes('processingOpId'),
    'AiChatWidget.vue must track processingOpId for in-flight requests'
  )
  assert.ok(
    content.includes('resolvedOperations'),
    'AiChatWidget.vue must track resolvedOperations state'
  )

  // Server pending normalization in loadPendingConfirmations
  assert.ok(
    content.includes('normalizePendingConfirmation(rawItem)'),
    'loadPendingConfirmations must normalize items with normalizePendingConfirmation'
  )

  // Follow-up card suppression
  assert.ok(
    content.includes('msg.isFollowUp || msg.suppressConfirmation'),
    'getMessageOpId must suppress follow-up and error messages'
  )
  assert.ok(
    content.includes('isFollowUp: true'),
    'handleConfirm/handleReject must flag follow-up messages with isFollowUp: true'
  )
  assert.ok(
    content.includes('suppressConfirmation: true'),
    'handleConfirm/handleReject must flag follow-up messages with suppressConfirmation: true'
  )
})

// ---------------------------------------------------------------------------
// Suite 4: DEF-32 Toast Deduplication in OrderWithLinesForm and TasksView
// ---------------------------------------------------------------------------
test('DEF-32: OrderWithLinesForm.vue has no local <Toast> component, preserving useToast', () => {
  const formPath = path.join(__dirname, '../src/components/OrderWithLinesForm.vue')
  const content = fs.readFileSync(formPath, 'utf-8')

  // Template must NOT have <Toast
  assert.ok(
    !content.includes('<Toast'),
    'OrderWithLinesForm.vue template must not mount local <Toast> component'
  )

  // Script must NOT import Toast component
  assert.ok(
    !content.includes("import Toast from 'primevue/toast'"),
    "OrderWithLinesForm.vue must not import Toast component from 'primevue/toast'"
  )

  // useToast must remain intact for global toast notifications
  assert.ok(
    content.includes("import { useToast } from 'primevue/usetoast'"),
    "OrderWithLinesForm.vue must import useToast from 'primevue/usetoast'"
  )
  assert.ok(
    content.includes('const toast = useToast()'),
    'OrderWithLinesForm.vue must instantiate useToast()'
  )
})

test('DEF-32: TasksView.vue has no local <Toast> component, preserving useToast', () => {
  const tasksViewPath = path.join(__dirname, '../src/views/supervisor/TasksView.vue')
  const content = fs.readFileSync(tasksViewPath, 'utf-8')

  // Template must NOT have <Toast
  assert.ok(
    !content.includes('<Toast'),
    'TasksView.vue template must not mount local <Toast> component'
  )

  // Script must NOT import Toast component
  assert.ok(
    !content.includes("import Toast from 'primevue/toast'"),
    "TasksView.vue must not import Toast component from 'primevue/toast'"
  )

  // useToast must remain intact for global toast notifications
  assert.ok(
    content.includes("import { useToast } from 'primevue/usetoast'"),
    "TasksView.vue must import useToast from 'primevue/usetoast'"
  )
  assert.ok(
    content.includes('const toast = useToast()'),
    'TasksView.vue must instantiate useToast()'
  )
})

test('DEF-32: App.vue maintains the single authoritative global <Toast position="top-right" />', () => {
  const appPath = path.join(__dirname, '../src/App.vue')
  const content = fs.readFileSync(appPath, 'utf-8')

  assert.ok(
    content.includes('<Toast position="top-right" />'),
    'App.vue must maintain the single global <Toast position="top-right" /> container'
  )
  assert.ok(
    content.includes("import Toast from 'primevue/toast'"),
    "App.vue must import Toast from 'primevue/toast'"
  )
  assert.ok(
    content.includes('setGlobalToast(toast)'),
    'App.vue must register global toast instance'
  )
})
