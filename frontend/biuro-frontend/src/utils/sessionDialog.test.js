import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth.js'
import { setupView, settle } from '../testSupport/vueSetup.js'

test('expiry blocks editing, same-user login preserves screen, changed permissions redirect safely', async () => {
  const oldStorage = globalThis.localStorage
  globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} }
  setActivePinia(createPinia())
  const auth = useAuthStore()
  const user = { username: 'operator', roles: ['OPERATOR'], passwordChangeRequired: false }
  const token = `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.signature`
  auth.login({ token, user })
  const paths = [], currentRoute = ref({ path: '/turnusy/T1', meta: { roles: ['OPERATOR'] } })
  const mounted = await setupView('App', { currentRoute, replace: async path => paths.push(path) })
  try {
    const v = mounted.view
    v.dialog = { open: false, showModal() { this.open = true }, close() { this.open = false } }
    const version = auth.screenVersion
    auth.expire(token); await settle()
    assert.equal(v.blocked, true); assert.equal(v.dialog.open, true)
    const different = auth.login({ token, user })
    await v.signedIn(different); await settle()
    assert.equal(auth.screenVersion, version)
    assert.equal(v.dialog.open, false); assert.deepEqual(paths, [])
    auth.expire(token); await settle()
    const changed = auth.login({ token, user: { ...user, roles: ['VIEWER'] } })
    await v.signedIn(changed)
    assert.deepEqual(paths, ['/listy'])
  } finally { mounted.unmount(); globalThis.localStorage = oldStorage }
})
