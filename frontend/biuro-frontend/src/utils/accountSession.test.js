import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth.js'
import { homeFor, hasRole, passwordError, tokenExpiry } from './accountAccess.js'
import { request, onUnauthorized, onForbidden } from '../api/http.js'
import { loginRequest, changePassword } from '../api/authApi.js'
import { createAccount, updateAccount, resetAccountPassword } from '../api/accountApi.js'
const originalFetch = globalThis.fetch, originalStorage = globalThis.localStorage
const token = (seconds = 3600) => `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds })).toString('base64url')}.signature`
const user = (roles = ['OPERATOR'], username = 'operator') => ({ username, displayName: 'Operator', passwordChangeRequired: false, roles })
const json = (body, status = 200) => new Response(JSON.stringify(body), { status })
beforeEach(() => {
  const values = new Map()
  globalThis.localStorage = { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
  setActivePinia(createPinia())
  onUnauthorized(value => useAuthStore().expire(value))
  onForbidden(async () => {})
})
afterEach(() => { globalThis.fetch = originalFetch; globalThis.localStorage = originalStorage; onUnauthorized(() => {}); onForbidden(async () => {}) })

test('roles are additive; SYS_ADMIN has no operational access; mandatory change wins', () => {
  assert.equal(homeFor(user(['SYS_ADMIN'])), '/konta')
  assert.equal(hasRole(user(['SYS_ADMIN']), 'OPERATOR'), false)
  assert.equal(homeFor(user(['SYS_ADMIN', 'OPERATOR', 'VIEWER'])), '/dashboard')
  assert.equal(homeFor(user(['VIEWER'])), '/listy')
  assert.equal(homeFor({ ...user(), passwordChangeRequired: true }), '/haslo')
  assert.equal(hasRole({ ...user(), passwordChangeRequired: true }, 'OPERATOR'), false)
  assert.equal(tokenExpiry('malformed'), 0)
  assert.ok(passwordError('😀'.repeat(19)))
  assert.equal(passwordError('Valid-password-123'), '')
})

test('startup validates profile and rejects expired tokens without a request', async () => {
  localStorage.setItem('biuro_token', token(-1))
  let calls = 0
  globalThis.fetch = async () => { calls++; return json(user()) }
  const expired = useAuthStore()
  await expired.initialize()
  assert.equal(expired.token, null)
  assert.equal(calls, 0)
  setActivePinia(createPinia())
  const live = token()
  localStorage.setItem('biuro_token', live)
  const auth = useAuthStore()
  await Promise.all([auth.initialize(), auth.initialize()])
  assert.equal(calls, 1)
  assert.equal(auth.isOperator, true)
  assert.equal(auth.token, live)
})

test('startup outage does not destroy the stored session and can be retried', async () => {
  const live = token()
  localStorage.setItem('biuro_token', live)
  const auth = useAuthStore()
  globalThis.fetch = async () => json({ message: 'Chwilowa awaria.' }, 503)
  await assert.rejects(auth.initialize(), /Chwilowa awaria/)
  assert.equal(localStorage.getItem('biuro_token'), live)
  assert.equal(auth.user, null)
  auth.initialized = false
  globalThis.fetch = async () => json(user())
  await auth.initialize()
  assert.equal(auth.isAuthenticated, true)
})

test('401 blocks the current session; old responses cannot expire a newer token; no write replay', async () => {
  const auth = useAuthStore(), old = token(), newer = token(4000)
  auth.login({ token: old, user: user() })
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({ message: 'Sesja wygasła.' }, 401) }
  await assert.rejects(request('/write', { token: old, method: 'PATCH', payload: { active: true } }), e => e.status === 401)
  assert.equal(calls, 1)
  assert.equal(auth.expired, true)
  assert.equal(auth.token, null)
  assert.equal(auth.user.username, 'operator')
  assert.equal(auth.login({ token: newer, user: user() }), false)
  auth.expire(old)
  assert.equal(auth.token, newer)
  assert.equal(auth.expired, false)
})

test('reauthentication as a different user remounts the previous screen', () => {
  const auth = useAuthStore()
  auth.login({ token: token(), user: user() })
  auth.expire(auth.token)
  const version = auth.screenVersion
  assert.equal(auth.login({ token: token(), user: user(['VIEWER'], 'viewer') }), true)
  assert.equal(auth.screenVersion, version + 1)
  assert.equal(auth.home, '/listy')
})

test('logout revokes server session before clearing; network failure permits retry', async () => {
  const auth = useAuthStore(), live = token()
  auth.login({ token: live, user: user() })
  globalThis.fetch = async () => { throw new Error('offline') }
  await assert.rejects(auth.logout(), /Nie można połączyć/)
  assert.equal(auth.token, live)
  globalThis.fetch = async (url, options) => {
    assert.ok(url.endsWith('/api/auth/logout'))
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.Authorization, `Bearer ${live}`)
    assert.equal(auth.token, live)
    return new Response(null, { status: 204 })
  }
  await auth.logout()
  assert.equal(auth.token, null)
  assert.equal(auth.user, null)
})

test('login errors do not expire another session; forbidden refresh does not log out or retry', async () => {
  const auth = useAuthStore(), live = token()
  auth.login({ token: live, user: user() })
  let calls = 0, refreshed
  onForbidden(async value => { refreshed = value })
  globalThis.fetch = async () => { calls++; return json({ message: 'Brak uprawnień.' }, 403) }
  await assert.rejects(request('/protected', { token: live }), /Brak uprawnień/)
  assert.equal(refreshed, live)
  assert.equal(auth.token, live)
  assert.equal(calls, 1)
  globalThis.fetch = async () => json({ message: 'Nieprawidłowy login lub hasło' }, 401)
  await assert.rejects(loginRequest('wrong', 'wrong'), /Nieprawidłowy login/)
  assert.equal(auth.expired, false)
})

test('account writes retain versions; own password change receives replacement session', async () => {
  const calls = [], replacement = { token: token(), user: user() }
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return json(replacement) }
  await createAccount('admin', { username: 'new', roles: ['VIEWER'], turnusCodes: ['T1'], password: 'Temporary-1234' })
  await updateAccount('admin', 'A/B', { version: 4, active: false, roles: ['VIEWER'], turnusCodes: [] })
  await resetAccountPassword('admin', 'A/B', 5, 'Temporary-5678')
  assert.deepEqual(await changePassword('admin', 'old', 'New-password-123'), replacement)
  assert.ok(calls[1].url.endsWith('/A%2FB'))
  assert.equal(calls[1].method, 'PATCH')
  assert.equal(JSON.parse(calls[1].body).version, 4)
  assert.equal(JSON.parse(calls[2].body).version, 5)
  assert.equal(JSON.parse(calls[3].body).currentPassword, 'old')
  assert.ok(calls.every(call => call.headers.Authorization === 'Bearer admin'))
})

test('delayed logout cannot clear a newer session, including same-user reauthentication', async () => {
  for (const status of [204, 401, 503]) {
    const auth = useAuthStore(), old = token(), newer = token(5000)
    auth.login({ token: old, user: user() })
    let finish
    globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
    const pending = auth.logout()
    auth.expire(old)
    auth.login({ token: newer, user: user() })
    finish(status === 204 ? new Response(null, { status }) : json({}, status))
    assert.equal(await pending, false)
    assert.equal(auth.token, newer)
    assert.equal(localStorage.getItem('biuro_token'), newer)
    assert.equal(auth.isAuthenticated, true)
  }
})

test('confirmed logout can finish after its own session expires, without requiring another login', async () => {
  const auth = useAuthStore(), old = token()
  auth.login({ token: old, user: user() })
  let finish
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const pending = auth.logout()
  auth.expire(old)
  finish(new Response(null, { status: 204 }))
  assert.equal(await pending, true)
  assert.equal(auth.user, null)
  assert.equal(auth.expired, false)
})
