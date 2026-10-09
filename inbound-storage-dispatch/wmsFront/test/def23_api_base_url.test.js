import { test } from 'node:test'
import assert from 'node:assert'
import { resolveBaseUrl, API_BASE_URL } from '../src/api/index.js'

test('DEF-23: resolveBaseUrl uses VITE_API_URL when configured in environment', () => {
  const customEnv = { VITE_API_URL: 'https://wms.prod.example.com/api' }
  const url = resolveBaseUrl(customEnv)
  assert.strictEqual(url, 'https://wms.prod.example.com/api')
})

test('DEF-23: resolveBaseUrl trims whitespace from VITE_API_URL', () => {
  const customEnv = { VITE_API_URL: '  https://api.internal/v1  ' }
  const url = resolveBaseUrl(customEnv)
  assert.strictEqual(url, 'https://api.internal/v1')
})

test('DEF-23: resolveBaseUrl falls back to relative /api when VITE_API_URL is absent or empty', () => {
  assert.strictEqual(resolveBaseUrl({}), '/api')
  assert.strictEqual(resolveBaseUrl({ VITE_API_URL: '' }), '/api')
  assert.strictEqual(resolveBaseUrl({ VITE_API_URL: '   ' }), '/api')
  assert.strictEqual(resolveBaseUrl(null), '/api')
  assert.strictEqual(resolveBaseUrl(undefined), '/api')
})

test('DEF-23: default API_BASE_URL does not contain hardcoded http://localhost:8080', () => {
  assert.ok(!API_BASE_URL.includes(':8080'), 'API_BASE_URL must not hardcode port 8080')
  assert.ok(!API_BASE_URL.startsWith('http://'), 'API_BASE_URL must not hardcode insecure http:// protocol')
})
