import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth.js'
import { onUnauthorized } from '../api/http.js'
import { canVisit } from './accountAccess.js'
const file = new URL('../router/index.js', import.meta.url)
let source = await readFile(file, 'utf8')
source = source.replace(/import .* from 'vue-router'/, 'const createWebHistory = () => null; const createRouter = config => ({ ...config, beforeEach(fn) { this.guard = fn } });')
source = source.replace(/import (\w+) from '.*\.vue'/g, 'const $1 = {}')
source = source.replace(/from '([^']+)'/g, (match, path) => path.startsWith('.') ? `from '${new URL(path, file).href}'` : match)
const { default: router } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
const originalFetch = globalThis.fetch, originalStorage = globalThis.localStorage
const token = `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.signature`
const profile = roles => ({ username: 'user', displayName: 'User', roles, passwordChangeRequired: false })
const route = path => { const found = router.routes.find(r => r.path === path); return { ...found, meta: found.meta || {} } }
beforeEach(() => {
  globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} }
  setActivePinia(createPinia())
  onUnauthorized(value => useAuthStore().expire(value))
})
afterEach(() => { globalThis.fetch = originalFetch; globalThis.localStorage = originalStorage; onUnauthorized(() => {}) })
function loggedIn(user) { useAuthStore().login({ token, user }); globalThis.fetch = async () => new Response(JSON.stringify(user)) }

test('anonymous deep links redirect to login before operational rendering', async () => {
  assert.equal(await router.guard(route('/turnusy/:code')), '/login')
})

test('SYS_ADMIN without OPERATOR opens accounts but not operational routes', async () => {
  loggedIn(profile(['SYS_ADMIN']))
  assert.equal(await router.guard(route('/konta')), true)
  assert.equal(await router.guard(route('/dashboard')), '/konta')
  assert.equal(await router.guard(route('/turnusy/:code/listy')), '/konta')
})

test('VIEWER uses shared list screen but never definition or account editor', async () => {
  loggedIn(profile(['VIEWER']))
  assert.equal(await router.guard(route('/turnusy/:code/listy')), true)
  assert.equal(await router.guard(route('/turnusy/:code')), '/listy')
  assert.equal(await router.guard(route('/konta')), '/listy')
  assert.equal(await router.guard(route('/login')), '/listy')
})

test('mandatory password change overrides every role and deep link', async () => {
  loggedIn({ ...profile(['SYS_ADMIN', 'OPERATOR']), passwordChangeRequired: true })
  assert.equal(await router.guard(route('/dashboard')), '/haslo')
  assert.equal(await router.guard(route('/haslo')), true)
})

test('role removal is checked at navigation; outage preserves token and renders no protected page', async () => {
  const auth = useAuthStore()
  loggedIn(profile(['SYS_ADMIN', 'OPERATOR']))
  globalThis.fetch = async () => new Response(JSON.stringify(profile(['VIEWER'])))
  assert.equal(await router.guard(route('/dashboard')), '/listy')
  assert.equal(auth.isOperator, false)
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Awaria bazy.' }), { status: 503 })
  assert.equal(await router.guard(route('/turnusy/:code/listy')), '/sesja')
  assert.equal(auth.token, token)
  assert.equal(auth.sessionMessage, 'Awaria bazy.')
})

test('revoked server session redirects to login and additive roles retain their permitted routes', async () => {
  loggedIn(profile(['SYS_ADMIN', 'OPERATOR', 'VIEWER']))
  assert.equal(canVisit(useAuthStore().user, route('/konta')), true)
  assert.equal(canVisit(useAuthStore().user, route('/dashboard')), true)
  globalThis.fetch = async () => new Response('{}', { status: 401 })
  assert.equal(await router.guard(route('/dashboard')), '/login')
  assert.equal(useAuthStore().token, null)
})
