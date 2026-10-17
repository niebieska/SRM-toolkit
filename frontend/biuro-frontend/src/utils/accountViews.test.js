import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth.js'
import { setupView, settle } from '../testSupport/vueSetup.js'
import { auditChanges } from './accountAudit.js'
const originalFetch = globalThis.fetch, originalStorage = globalThis.localStorage, originalWindow = globalThis.window
const user = { username: 'root', displayName: 'Admin', passwordChangeRequired: false, roles: ['SYS_ADMIN'] }
const account = { id: 'A1', username: 'viewer', displayName: 'Viewer', active: true, passwordChangeRequired: false, version: 2, roles: ['VIEWER'], turnusCodes: ['T1'] }
const json = (body, status = 200) => new Response(JSON.stringify(body), { status })
let mounted
beforeEach(() => {
  globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} }
  globalThis.window = { addEventListener() {}, removeEventListener() {} }
  setActivePinia(createPinia()); useAuthStore().login({ token: 'admin-token', user: { ...user, roles: [...user.roles] } })
})
afterEach(() => { mounted?.unmount(); mounted = undefined; globalThis.fetch = originalFetch; globalThis.localStorage = originalStorage; globalThis.window = originalWindow })
const router = () => ({ replace: async () => {} })
function baseFetch(url) {
  if (url.endsWith('/turnus-options')) return json([{ turnusCode: 'T1', turnusName: 'Zima' }])
  if (url.includes('/audit')) return json([])
  return json([account])
}

test('account edit preserves assignments on conflict and reload explicitly discards edits', async () => {
  globalThis.fetch = async (url, options) => options.method === 'PATCH' ? json({ message: 'Odśwież konto.' }, 409) : baseFetch(url)
  mounted = await setupView('AccountsView', router()); await settle()
  const v = mounted.view
  await v.select(account); v.form.displayName = 'Changed'; v.form.turnusCodes = []
  await v.save()
  assert.equal(v.error, 'Odśwież konto.')
  assert.equal(v.form.displayName, 'Changed')
  assert.deepEqual(v.form.turnusCodes, [])
  assert.equal(v.form.version, 2)
  const reload = v.load(); await settle()
  assert.equal(v.prompt.kind, 'reload')
  v.decide(false); await reload
  assert.equal(v.form.displayName, 'Changed')
  const discard = v.load(); await settle(); v.decide(true); await discard
  assert.equal(v.form.displayName, 'Viewer')
  assert.deepEqual(v.form.turnusCodes, ['T1'])
})

test('creation prevents duplicate submits, sends fixed roles and clears temporary password on success', async () => {
  let finish, writes = 0, payload
  globalThis.fetch = async (url, options) => {
    if (options.method === 'POST') { writes++; payload = JSON.parse(options.body); return new Promise(resolve => { finish = resolve }) }
    return baseFetch(url)
  }
  mounted = await setupView('AccountsView', router()); await settle()
  const v = mounted.view
  await v.select(null)
  v.form.username = 'new'; v.form.displayName = 'New'; v.form.roles = ['VIEWER']; v.form.turnusCodes = ['T1']
  v.password = v.confirmation = 'Temporary-password-123'
  const pending = v.save(); await settle(); await v.save()
  assert.equal(writes, 1)
  assert.deepEqual(payload.roles, ['VIEWER']); assert.deepEqual(payload.turnusCodes, ['T1'])
  finish(json({ ...account, id: 'A2', username: 'new', version: 0, passwordChangeRequired: true })); await pending
  assert.equal(v.password, ''); assert.equal(v.confirmation, '')
  assert.equal(v.selected, 'A2'); assert.equal(v.dirty, '')
})

test('password reset sends the displayed version, clears secrets and preserves settings on failure', async () => {
  let writes = 0, payload
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/password')) { writes++; payload = JSON.parse(options.body); return json({ ...account, version: 3, passwordChangeRequired: true }) }
    return baseFetch(url)
  }
  mounted = await setupView('AccountsView', router()); await settle()
  const v = mounted.view
  await v.select(account); v.form.displayName = 'Unsaved'
  v.resetPassword = v.resetConfirmation = 'Temporary-password-123'
  await v.reset(); assert.equal(writes, 0); assert.match(v.error, /Najpierw zapisz/)
  v.form.displayName = 'Viewer'; await v.reset()
  assert.equal(writes, 1); assert.equal(payload.version, 2)
  assert.equal(v.form.version, 3); assert.equal(v.resetPassword, '')
})

test('mandatory password change checks confirmation and replaces session without replay', async () => {
  const auth = useAuthStore(), nav = [], replacement = { token: 'replacement', user: { ...user, passwordChangeRequired: false } }
  auth.user.passwordChangeRequired = true
  let calls = 0
  globalThis.fetch = async (url, options) => {
    calls++; assert.ok(url.endsWith('/api/auth/password'))
    assert.equal(JSON.parse(options.body).currentPassword, 'current-password')
    return json(replacement)
  }
  mounted = await setupView('PasswordView', { replace: async path => nav.push(path) })
  const v = mounted.view
  v.current = 'current-password'; v.password = 'New-password-123'; v.confirmation = 'mismatch'
  await v.submit(); assert.equal(calls, 0); assert.match(v.error, /takie same/)
  v.confirmation = v.password; await v.submit()
  assert.equal(calls, 1); assert.equal(auth.token, 'replacement')
  assert.equal(v.current, ''); assert.deepEqual(nav, ['/konta'])
})

test('account audit renders allowlisted changes as Polish text', () => {
  assert.match(auditChanges({ details: { before: account, after: { ...account, active: false, roles: ['OPERATOR'], turnusCodes: [] } } }), /Konto wyłączone/)
  assert.match(auditChanges({ details: { before: account, after: { ...account, roles: ['OPERATOR'] } } }), /Operator/)
  assert.equal(auditChanges({ details: { password: 'never display raw details' } }), '')
})


test('late password response cannot replace a newer login session', async () => {
  const auth = useAuthStore()
  let finish
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const nav = router()
  mounted = await setupView('PasswordView', nav)
  const v = mounted.view
  v.current = 'Current-password'; v.password = v.confirmation = 'New-password-123'
  const pending = v.submit()
  assert.equal(nav.guard(), false)
  auth.login({ token: 'newer-token', user: { ...user } })
  assert.equal(nav.guard(), true)
  finish(json({ token: 'obsolete-token', user: { ...user } })); await pending
  assert.equal(auth.token, 'newer-token')
})

test('abandoned sign-in does not apply its delayed response to the next screen', async () => {
  const auth = useAuthStore()
  let finish
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  mounted = await setupView('SignInForm', router())
  const v = mounted.view
  v.username = 'other'; v.password = 'Other-password-123'
  const pending = v.submit()
  mounted.unmount(); mounted = undefined
  finish(json({ token: 'obsolete-token', user: { ...user, username: 'other' } })); await pending
  assert.equal(auth.token, 'admin-token')
  assert.equal(auth.user.username, 'root')
})

test('new-account reload discards all draft fields and passwords only after confirmed successful reload', async () => {
  globalThis.fetch = async url => baseFetch(url)
  mounted = await setupView('AccountsView', router()); await settle()
  const v = mounted.view
  await v.select(null)
  v.form.username = 'draft'; v.form.displayName = 'Draft'; v.form.roles = ['VIEWER']; v.form.turnusCodes = ['T1']
  v.password = v.confirmation = 'Temporary-password-123'
  const cancelled = v.load(); await settle(); v.decide(false); await cancelled
  assert.equal(v.form.displayName, 'Draft')
  const confirmed = v.load(); await settle(); v.decide(true); await confirmed
  assert.equal(v.form.username, ''); assert.equal(v.form.displayName, '')
  assert.deepEqual(v.form.roles, []); assert.deepEqual(v.form.turnusCodes, [])
  assert.equal(v.password, ''); assert.equal(v.confirmation, '')
  assert.equal(Boolean(v.dirty), false)
  assert.equal(v.isNew, true)
})

test('password change blocks navigation while pending and permits its own success redirect', async () => {
  let finish
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const nav = { replace: async () => { assert.equal(nav.guard(), true) } }
  mounted = await setupView('PasswordView', nav)
  const v = mounted.view
  v.current = 'Current-password'; v.password = v.confirmation = 'New-password-123'
  const pending = v.submit()
  assert.equal(nav.guard(), false)
  finish(json({ token: 'replacement', user: { ...user } })); await pending
  assert.equal(nav.guard(), true)
  assert.equal(useAuthStore().token, 'replacement')
})

test('unmounted password screen never redirects after its pending request completes', async () => {
  let finish
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  const paths = []
  mounted = await setupView('PasswordView', { replace: async path => paths.push(path) })
  const v = mounted.view
  v.current = 'Current-password'; v.password = v.confirmation = 'New-password-123'
  const pending = v.submit()
  mounted.unmount(); mounted = undefined
  finish(json({ token: 'replacement', user: { ...user } })); await pending
  assert.deepEqual(paths, [])
})

test('new-account username pattern is valid for browser Unicode-set validation', async () => {
  const { readFile } = await import('node:fs/promises')
  const { parse } = await import('@vue/compiler-sfc')
  const { baseParse } = await import('@vue/compiler-dom')
  const file = await readFile(new URL('../views/AccountsView.vue', import.meta.url), 'utf8')
  const ast = baseParse(parse(file).descriptor.template.content)
  let pattern
  function visit(node) {
    if (node.tag === 'input') {
      const field = node.props?.find(prop => prop.name === 'pattern')
      if (field) pattern = field.value.content
    }
    node.children?.forEach(visit)
  }
  visit(ast)
  assert.equal(typeof pattern, 'string')
  const regex = new RegExp(`^(?:${pattern})$`, 'v')
  for (const valid of ['admin', 'First.Last', 'user-name', 'user_123']) assert.ok(regex.test(valid), valid)
  for (const invalid of ['ab', 'user name', 'żaneta', '-admin', 'a'.repeat(65)]) assert.equal(regex.test(invalid), false, invalid)
})


test('late logout completion never navigates a reauthenticated user back to login', async () => {
  const auth = useAuthStore(), paths = []
  let finish
  globalThis.fetch = async () => new Promise(resolve => { finish = resolve })
  mounted = await setupView('BiuroHeader', { push: async path => paths.push(path) })
  const pending = mounted.view.logout()
  auth.expire(auth.token)
  auth.login({ token: 'newer-token', user: { ...user } })
  finish(new Response(null, { status: 204 })); await pending
  assert.equal(auth.token, 'newer-token')
  assert.deepEqual(paths, [])
})

test('failed new-account reload retains the draft even after confirmation', async () => {
  globalThis.fetch = async url => baseFetch(url)
  mounted = await setupView('AccountsView', router()); await settle()
  const v = mounted.view
  await v.select(null); v.form.displayName = 'Draft'; v.password = 'Temporary-password-123'
  globalThis.fetch = async () => json({ message: 'Chwilowa awaria.' }, 503)
  const pending = v.load(); await settle(); v.decide(true); await pending
  assert.equal(v.error, 'Chwilowa awaria.')
  assert.equal(v.form.displayName, 'Draft'); assert.equal(v.password, 'Temporary-password-123')
  assert.equal(Boolean(v.dirty), true)
})

test('failed password change releases navigation and allows correction', async () => {
  globalThis.fetch = async () => json({ message: 'Obecne hasło jest nieprawidłowe.' }, 400)
  const nav = router()
  mounted = await setupView('PasswordView', nav)
  const v = mounted.view
  v.current = 'Wrong-password'; v.password = v.confirmation = 'New-password-123'
  await v.submit()
  assert.equal(v.busy, false); assert.equal(nav.guard(), true)
  assert.equal(v.error, 'Obecne hasło jest nieprawidłowe.')
  assert.equal(useAuthStore().token, 'admin-token')
})
