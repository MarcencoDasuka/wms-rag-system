import { test } from 'node:test'
import assert from 'node:assert'
import { handle409Conflict } from '../src/api/interceptors.js'
import { useConflictListener } from '../src/composables/useConflictListener.js'

test('DEF-05: handle409Conflict dispatches wms:conflict event on window with payload', () => {
  const dispatchedEvents = []
  const originalWindow = globalThis.window
  const originalCustomEvent = globalThis.CustomEvent

  class MockCustomEvent {
    constructor(type, init) {
      this.type = type
      this.detail = init?.detail
    }
  }

  globalThis.CustomEvent = MockCustomEvent
  globalThis.window = {
    dispatchEvent: (evt) => {
      dispatchedEvents.push(evt)
      return true
    }
  }

  try {
    const error = {
      response: {
        status: 409,
        data: {
          error: 'Conflict',
          message: 'Resource was modified by another operator.',
          resourceId: 101
        }
      }
    }

    const warningNotifications = []
    handle409Conflict(error, {
      notifyWarning: (summary, detail) => {
        warningNotifications.push({ summary, detail })
      }
    })

    assert.strictEqual(warningNotifications.length, 1)
    assert.strictEqual(warningNotifications[0].summary, 'Conflict Detected')
    assert.strictEqual(warningNotifications[0].detail, 'Resource was modified by another operator.')

    assert.strictEqual(dispatchedEvents.length, 1)
    assert.strictEqual(dispatchedEvents[0].type, 'wms:conflict')
    assert.strictEqual(dispatchedEvents[0].detail.status, 409)
    assert.strictEqual(dispatchedEvents[0].detail.message, 'Resource was modified by another operator.')
    assert.deepStrictEqual(dispatchedEvents[0].detail.data, error.response.data)
  } finally {
    globalThis.window = originalWindow
    globalThis.CustomEvent = originalCustomEvent
  }
})

test('DEF-05: useConflictListener registers window listener and triggers callback on conflict event', () => {
  const listeners = new Map()
  const originalWindow = globalThis.window

  globalThis.window = {
    addEventListener: (type, handler) => {
      if (!listeners.has(type)) listeners.set(type, [])
      listeners.get(type).push(handler)
    },
    removeEventListener: (type, handler) => {
      if (listeners.has(type)) {
        const list = listeners.get(type).filter((h) => h !== handler)
        listeners.set(type, list)
      }
    }
  }

  try {
    const invocations = []
    const callback = (detail) => invocations.push(detail)

    const { startListening, stopListening } = useConflictListener(callback)
    startListening()

    assert.strictEqual(listeners.get('wms:conflict')?.length, 1)

    // Trigger event
    const eventHandler = listeners.get('wms:conflict')[0]
    eventHandler({
      type: 'wms:conflict',
      detail: { message: 'Row locked by picker', status: 409 }
    })

    assert.strictEqual(invocations.length, 1)
    assert.strictEqual(invocations[0].message, 'Row locked by picker')
    assert.strictEqual(invocations[0].status, 409)

    // Detach and verify clean unmount behavior
    stopListening()
    assert.strictEqual(listeners.get('wms:conflict')?.length, 0)

    // Dispatched events after unmount must NOT trigger callback
    eventHandler({
      type: 'wms:conflict',
      detail: { message: 'Ignored after unmount' }
    })
    assert.strictEqual(invocations.length, 1)
  } finally {
    globalThis.window = originalWindow
  }
})

test('DEF-05: useConflictListener safely contains callback errors without crashing event loop', () => {
  const listeners = new Map()
  const originalWindow = globalThis.window

  globalThis.window = {
    addEventListener: (type, handler) => {
      if (!listeners.has(type)) listeners.set(type, [])
      listeners.get(type).push(handler)
    },
    removeEventListener: () => {}
  }

  try {
    const errorCallback = () => {
      throw new Error('Refetch failed')
    }

    const { startListening } = useConflictListener(errorCallback)
    startListening()

    const eventHandler = listeners.get('wms:conflict')[0]
    assert.doesNotThrow(() => {
      eventHandler({ type: 'wms:conflict', detail: { message: 'test' } })
    })
  } finally {
    globalThis.window = originalWindow
  }
})

test('DEF-05: multiple conflict listeners receive dispatched conflict events independently', () => {
  const listeners = new Map()
  const originalWindow = globalThis.window

  globalThis.window = {
    addEventListener: (type, handler) => {
      if (!listeners.has(type)) listeners.set(type, [])
      listeners.get(type).push(handler)
    },
    removeEventListener: (type, handler) => {
      if (listeners.has(type)) {
        const list = listeners.get(type).filter((h) => h !== handler)
        listeners.set(type, list)
      }
    }
  }

  try {
    const invocationsA = []
    const invocationsB = []

    const listenerA = useConflictListener((detail) => invocationsA.push(detail))
    const listenerB = useConflictListener((detail) => invocationsB.push(detail))

    listenerA.startListening()
    listenerB.startListening()

    assert.strictEqual(listeners.get('wms:conflict')?.length, 2)

    // Trigger event to all registered listeners
    const handlers = listeners.get('wms:conflict') || []
    handlers.forEach((h) => h({
      type: 'wms:conflict',
      detail: { message: 'Multi-view lock conflict', status: 409 }
    }))

    assert.strictEqual(invocationsA.length, 1)
    assert.strictEqual(invocationsB.length, 1)
    assert.strictEqual(invocationsA[0].message, 'Multi-view lock conflict')
    assert.strictEqual(invocationsB[0].message, 'Multi-view lock conflict')

    // Stop listener A, listener B remains active
    listenerA.stopListening()
    assert.strictEqual(listeners.get('wms:conflict')?.length, 1)

    // Trigger second event
    const remainingHandlers = listeners.get('wms:conflict') || []
    remainingHandlers.forEach((h) => h({
      type: 'wms:conflict',
      detail: { message: 'Second conflict', status: 409 }
    }))

    assert.strictEqual(invocationsA.length, 1) // Unsubscribed, unchanged
    assert.strictEqual(invocationsB.length, 2) // Still subscribed, received
  } finally {
    globalThis.window = originalWindow
  }
})
