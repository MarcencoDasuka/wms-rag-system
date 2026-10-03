let onMountedHook = null
let onUnmountedHook = null

try {
  const vue = await import('vue').catch(() => null)
  if (vue) {
    onMountedHook = vue.onMounted
    onUnmountedHook = vue.onUnmounted
  }
} catch {
  // Running in isolated test environment without vue installed
}

/**
 * Composable that listens for 'wms:conflict' CustomEvent dispatched on window
 * (originating from Axios response interceptors upon receiving HTTP 409 Conflict)
 * and triggers a reactive handler (such as silently refetching stale tabular data)
 * while ensuring clean unmount lifecycle cleanup to prevent memory leaks.
 *
 * @param {Function} onConflictCallback - Handler function called with conflict detail.
 * @param {Object} [options]
 * @param {boolean} [options.immediate=false] - Whether to invoke callback on mount.
 * @param {Function} [options.onMounted] - Optional custom mount hook.
 * @param {Function} [options.onUnmounted] - Optional custom unmount hook.
 * @returns {{ startListening: Function, stopListening: Function, cleanup: Function }}
 */
export function useConflictListener(onConflictCallback, options = {}) {
  if (typeof onConflictCallback !== 'function') {
    return {
      startListening: () => {},
      stopListening: () => {},
      cleanup: () => {}
    }
  }

  let isListening = false

  const handleConflictEvent = (event) => {
    if (!isListening) return
    try {
      const detail = event?.detail || {}
      onConflictCallback(detail)
    } catch (err) {
      console.error('[wms:conflict] Listener callback threw an error:', err)
    }
  }

  const startListening = () => {
    if (isListening) return
    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('wms:conflict', handleConflictEvent)
      isListening = true
    }
  }

  const stopListening = () => {
    if (!isListening) return
    if (typeof window !== 'undefined' && typeof window.removeEventListener === 'function') {
      window.removeEventListener('wms:conflict', handleConflictEvent)
      isListening = false
    }
  }

  const mountFn = options.onMounted || onMountedHook
  const unmountFn = options.onUnmounted || onUnmountedHook

  if (typeof mountFn === 'function') {
    mountFn(() => {
      startListening()
      if (options.immediate) {
        onConflictCallback({})
      }
    })
  } else {
    // If not in a Vue component lifecycle context, start immediately
    startListening()
    if (options.immediate) {
      onConflictCallback({})
    }
  }

  if (typeof unmountFn === 'function') {
    unmountFn(() => {
      stopListening()
    })
  }

  return {
    startListening,
    stopListening,
    cleanup: stopListening
  }
}
