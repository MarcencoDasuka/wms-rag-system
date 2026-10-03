import { test } from 'node:test'
import assert from 'node:assert'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { sanitizeRedirect } from '../src/utils/redirectSanitizer.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const DEFAULT_FALLBACK = '/supervisor'

test('DEF-04: allows strictly safe internal relative paths', () => {
  assert.strictEqual(sanitizeRedirect('/orders', DEFAULT_FALLBACK), '/orders')
  assert.strictEqual(sanitizeRedirect('/inventory', DEFAULT_FALLBACK), '/inventory')
  assert.strictEqual(sanitizeRedirect('/foo?x=1', DEFAULT_FALLBACK), '/foo?x=1')
  assert.strictEqual(sanitizeRedirect('/supervisor/dashboard', DEFAULT_FALLBACK), '/supervisor/dashboard')
  assert.strictEqual(sanitizeRedirect('/app/view#section', DEFAULT_FALLBACK), '/app/view#section')
  assert.strictEqual(sanitizeRedirect('/inventory?filter=active&sort=desc', DEFAULT_FALLBACK), '/inventory?filter=active&sort=desc')
})

test('DEF-04: rejects protocol-relative external redirect URLs', () => {
  assert.strictEqual(sanitizeRedirect('//evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('///evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('////evil.example/path', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('//attacker.com/login', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
})

test('DEF-04: rejects absolute external URLs with explicit schemes', () => {
  assert.strictEqual(sanitizeRedirect('https://evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('http://evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('http://evil.example/orders', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('ftp://evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('javascript:alert(1)', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('data:text/html,evil', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('vbscript:msgbox(1)', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
})

test('DEF-04: rejects backslash-based evasion attempts', () => {
  assert.strictEqual(sanitizeRedirect('/\\evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('\\\\evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('\\orders', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('/orders\\evil', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('/\\/\\evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('\\/evil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
})

test('DEF-04: rejects encoded and normalized evasion attempts', () => {
  // Encoded slashes/backslashes
  assert.strictEqual(sanitizeRedirect('/%2fevil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('%2f%2fevil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('/%5cevil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('%5c%5cevil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('/%252fevil.example', DEFAULT_FALLBACK), DEFAULT_FALLBACK)

  // Control characters and CRLF injection
  assert.strictEqual(sanitizeRedirect('/orders\r\nevil', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('/orders%0a%0dSet-Cookie:evil=1', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('/orders%00evil', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
})

test('DEF-04: handles null, undefined, empty, and array inputs gracefully', () => {
  assert.strictEqual(sanitizeRedirect(null, DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect(undefined, DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect('   ', DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect(12345, DEFAULT_FALLBACK), DEFAULT_FALLBACK)
  assert.strictEqual(sanitizeRedirect({ path: '/orders' }, DEFAULT_FALLBACK), DEFAULT_FALLBACK)

  // Array query parameter from Vue Router
  assert.strictEqual(sanitizeRedirect(['/inventory'], DEFAULT_FALLBACK), '/inventory')
  assert.strictEqual(sanitizeRedirect(['//evil.com'], DEFAULT_FALLBACK), DEFAULT_FALLBACK)
})

test('DEF-04: Static analysis confirms LoginView.vue sanitizes redirect before router.push', () => {
  const loginViewPath = path.join(__dirname, '../src/views/auth/LoginView.vue')
  const content = fs.readFileSync(loginViewPath, 'utf-8')

  assert.ok(
    content.includes("import { sanitizeRedirect } from '@/utils/redirectSanitizer'"),
    'LoginView.vue must import sanitizeRedirect'
  )
  assert.ok(
    content.includes('router.push(sanitizeRedirect(route.query.redirect, authStore.dashboardPath))'),
    'LoginView.vue must wrap redirect in sanitizeRedirect'
  )
  assert.ok(
    !content.includes('router.push(route.query.redirect ||'),
    'LoginView.vue must NOT push unsanitized route.query.redirect'
  )
})
