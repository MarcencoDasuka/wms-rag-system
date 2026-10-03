/**
 * Global notification service connecting PrimeVue Toast to non-component contexts (such as Axios interceptors).
 */

let globalToast = null
const recentToasts = new Map()
const DEBOUNCE_MS = 1500

export const setGlobalToast = (toastInstance) => {
  globalToast = toastInstance
}

export const getGlobalToast = () => {
  return globalToast
}

export const showToast = ({ severity = 'info', summary = '', detail = '', life = 4000 }) => {
  const messageKey = `${severity}:${summary}:${detail}`
  const now = Date.now()
  const lastShown = recentToasts.get(messageKey)

  if (lastShown && now - lastShown < DEBOUNCE_MS) {
    return false // Suppress duplicate toast within debounce window
  }
  recentToasts.set(messageKey, now)

  // Periodically clean stale entries from recentToasts
  if (recentToasts.size > 50) {
    for (const [key, timestamp] of recentToasts.entries()) {
      if (now - timestamp > DEBOUNCE_MS * 2) {
        recentToasts.delete(key)
      }
    }
  }

  if (globalToast && typeof globalToast.add === 'function') {
    globalToast.add({ severity, summary, detail, life })
    return true
  } else if (typeof console !== 'undefined') {
    const logFn = severity === 'error' ? console.error : severity === 'warn' ? console.warn : console.log
    logFn(`[${severity.toUpperCase()}] ${summary}: ${detail}`)
    return true
  }
  return false
}

export const notifyError = (summary, detail, life = 5000) => {
  return showToast({ severity: 'error', summary, detail, life })
}

export const notifyWarning = (summary, detail, life = 5000) => {
  return showToast({ severity: 'warn', summary, detail, life })
}

export const notifyInfo = (summary, detail, life = 4000) => {
  return showToast({ severity: 'info', summary, detail, life })
}

export const notifySuccess = (summary, detail, life = 4000) => {
  return showToast({ severity: 'success', summary, detail, life })
}
