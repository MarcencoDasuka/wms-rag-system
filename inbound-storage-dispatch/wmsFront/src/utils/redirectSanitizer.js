/**
 * Validates and sanitizes redirect query parameters to prevent Open Redirect vulnerabilities (DEF-04).
 * Allows strictly safe internal relative paths (e.g. '/orders', '/inventory', '/foo?x=1').
 * Rejects protocol-relative, absolute, backslash-based, control-char, or encoded external targets.
 *
 * @param {any} redirectParam
 * @param {string} [fallbackPath='/']
 * @returns {string}
 */
export const sanitizeRedirect = (redirectParam, fallbackPath = '/') => {
  const candidate = Array.isArray(redirectParam) ? redirectParam[0] : redirectParam

  if (typeof candidate !== 'string') {
    return fallbackPath
  }

  const trimmed = candidate.trim()
  if (!trimmed) {
    return fallbackPath
  }

  // Backslashes are strictly prohibited in internal URLs (prevents browser URL normalization exploits)
  if (trimmed.includes('\\')) {
    return fallbackPath
  }

  // Control characters, tabs, newlines prohibited
  if (/[\x00-\x1F\x7F]/.test(trimmed)) {
    return fallbackPath
  }

  // Reject URL scheme syntax (e.g., 'http:', 'https:', 'javascript:', 'data:')
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(trimmed)) {
    return fallbackPath
  }

  // Canonicalization / recursive decoding check (guards against %2f%2f, %5c, %252f, etc.)
  let decoded = trimmed
  try {
    let previous = ''
    let iterations = 0
    while (decoded !== previous && iterations < 3) {
      previous = decoded
      decoded = decodeURIComponent(decoded)
      iterations++
    }
  } catch {
    // Malformed URI encoding -> reject
    return fallbackPath
  }

  // Check decoded string for forbidden elements
  if (decoded.includes('\\') || /[\x00-\x1F\x7F]/.test(decoded)) {
    return fallbackPath
  }

  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(decoded)) {
    return fallbackPath
  }

  // Must begin with a single '/' and not '//' (protocol-relative)
  if (!decoded.startsWith('/') || decoded.startsWith('//')) {
    return fallbackPath
  }

  // WHATWG URL parser verification against localhost origin
  try {
    const dummyOrigin = 'http://localhost'
    const parsed = new URL(trimmed, dummyOrigin)

    if (parsed.origin !== dummyOrigin) {
      return fallbackPath
    }

    if (!parsed.pathname.startsWith('/') || parsed.pathname.startsWith('//')) {
      return fallbackPath
    }

    return parsed.pathname + parsed.search + parsed.hash
  } catch {
    return fallbackPath
  }
}
