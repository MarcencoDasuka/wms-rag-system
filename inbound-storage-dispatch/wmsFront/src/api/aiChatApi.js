import apiClient from './index.js'

/**
 * Normalizes a PendingConfirmation payload from the backend.
 * Handles both the backend's native 'token' field and 'operationId'.
 *
 * @param {any} item
 * @returns {{ operationId: string, actionType: string|null, targetId: string|null, details: string|null, expiresAt: string|null }|null}
 */
export const normalizePendingConfirmation = (item) => {
  if (!item || typeof item !== 'object') return null
  const operationId = item.token || item.operationId || null
  if (!operationId || typeof operationId !== 'string') return null

  return {
    operationId,
    actionType: item.actionType || null,
    targetId: item.targetId || null,
    details: item.details || null,
    expiresAt: item.expiresAt || null
  }
}

/**
 * Extracts an operation confirmation ID matching OP-[A-Za-z0-9-]+ from text.
 *
 * @param {string} text
 * @returns {string|null}
 */
export const extractOperationId = (text) => {
  if (typeof text !== 'string') return null
  const match = text.match(/\bOP-[A-Za-z0-9-]+\b/)
  return match ? match[0] : null
}

/**
 * Validates whether an href is a safe internal application route.
 * Rejects protocol-relative, absolute, scheme injection, or backslash-based targets.
 *
 * @param {string} href
 * @returns {boolean}
 */
export const isSafeInternalRoute = (href) => {
  if (typeof href !== 'string') return false
  const trimmed = href.trim()
  if (!trimmed) return false

  // Must begin with exactly one '/' and not '//' (protocol-relative)
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
    return false
  }

  // Backslashes are strictly prohibited
  if (trimmed.includes('\\')) {
    return false
  }

  // Reject URL scheme syntax (e.g., http:, https:, javascript:, data:)
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(trimmed)) {
    return false
  }

  // Check for dangerous protocols anywhere before query or hash
  const pathPart = trimmed.split(/[?#]/)[0]
  if (/(?:javascript|data|vbscript|file):/i.test(pathPart)) {
    return false
  }

  // Canonicalization / decode check to prevent encoded bypasses
  try {
    let decoded = trimmed
    let prev = ''
    let iterations = 0
    while (decoded !== prev && iterations < 3) {
      prev = decoded
      decoded = decodeURIComponent(decoded)
      iterations++
    }
    if (!decoded.startsWith('/') || decoded.startsWith('//') || decoded.includes('\\')) {
      return false
    }
    const decodedPath = decoded.split(/[?#]/)[0]
    if (/(?:javascript|data|vbscript|file):/i.test(decodedPath)) {
      return false
    }
    if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(decoded)) {
      return false
    }
  } catch {
    return false
  }

  return true
}

/**
 * Retrieves all pending Human-in-the-Loop operations requiring confirmation.
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export const getPendingConfirmations = () => {
  return apiClient.get('/v1/chat/confirmations/pending')
}

/**
 * Confirms and executes an out-of-band mutating operation.
 *
 * @param {string} operationId
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export const confirmOperation = (operationId) => {
  return apiClient.post(`/v1/chat/confirmations/${encodeURIComponent(operationId)}/confirm`)
}

/**
 * Rejects and cancels a pending out-of-band mutating operation.
 *
 * @param {string} operationId
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export const rejectOperation = (operationId) => {
  return apiClient.post(`/v1/chat/confirmations/${encodeURIComponent(operationId)}/reject`)
}

/**
 * Sends a chat message to the WMS AI assistant.
 *
 * @param {string} message
 * @param {string} [conversationId]
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export const sendChatMessage = (message, conversationId) => {
  const payload = conversationId ? { message, conversationId } : { message }
  return apiClient.post('/v1/chat', payload)
}

export const aiChatApi = {
  getPendingConfirmations,
  confirmOperation,
  rejectOperation,
  sendChatMessage,
  sendMessage: sendChatMessage,
  extractOperationId,
  isSafeInternalRoute,
  normalizePendingConfirmation
}

export default aiChatApi
