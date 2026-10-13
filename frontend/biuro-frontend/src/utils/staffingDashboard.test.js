import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, nextTick } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

const file = new URL('../views/DashboardView.vue', import.meta.url)
const { descriptor } = parse(await readFile(file, 'utf8'))
let source = compileScript(descriptor, { id: 'staffing-dashboard-test' }).content
source = source.replace(/import (RegistrationTable|BiuroHeader|StaffingStatistics) .*\n/g, (_, name) => `const ${name} = {};\n`)
source = source.replace(/import .* from '..\/stores\/auth'/, "const useAuthStore = () => ({ token: 'operator-token' });")
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path.endsWith('.js') ? path : path + '.js', file).href}'` : match
})
source = source.replace('__expose();', '')
source = source.replace('return __returned__', '__expose({ filters, stats, staffingRoles, statsLoading, statsError, loadTurnusStats }); return __returned__')
const { default: Dashboard } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
Dashboard.render = () => null
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, setText() {}, setElementText() {}, patchProp() {}, createText: () => ({}), createElement: () => ({}) })
async function settle() {
  for (let i = 0; i < 5; i++) { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
}
function stats(code, accepted = 3) {
  return { turnusCode: code, participants: {}, staff: {}, unmappedAcceptedStaff: 0,
    staffRoles: [{ categoryCode: 'animator', label: 'Animator', accepted, required: 6,
      subroles: [{ code: 'kandydat_na_animatora', label: 'Kandydat', accepted: 2 }] }] }
}
async function mount(t, handler) {
  const previous = globalThis.fetch
  globalThis.fetch = (url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer operator-token')
    return String(url).includes('/stats/') ? handler(url) : Promise.resolve(new Response('[]'))
  }
  const app = renderer.createApp(Dashboard)
  const vm = app.mount({})
  t.after(() => { app.unmount(); globalThis.fetch = previous })
  await settle()
  return { vm, app }
}
test('staff roles follow type filter, remaining independent of status and search', async t => {
  let calls = 0
  const { vm } = await mount(t, async () => { calls++; return new Response(JSON.stringify(stats('T1'))) })
  vm.filters.turnusCode = 'T1'; await settle()
  assert.equal(vm.staffingRoles[0].accepted, 3)
  vm.filters.status = 'NEW'; vm.filters.search = 'No match'; await settle()
  assert.equal(vm.staffingRoles[0].accepted, 3)
  assert.equal(calls, 1)
  vm.filters.registrationType = 'PARTICIPANT'; await nextTick()
  assert.deepEqual(vm.staffingRoles, [])
  vm.filters.registrationType = 'STAFF'; await nextTick()
  assert.equal(vm.staffingRoles.length, 1)
  vm.filters.registrationType = ''; await nextTick()
  assert.equal(vm.staffingRoles.length, 1)
})
test('Turnus races and deselection cannot show stale counts; status refresh updates them', async t => {
  let finishOld, current = 4
  const { vm } = await mount(t, url => String(url).endsWith('/T1')
    ? new Promise(resolve => { finishOld = () => resolve(new Response(JSON.stringify(stats('T1')))) })
    : Promise.resolve(new Response(JSON.stringify(stats('T2', current)))))
  vm.filters.turnusCode = 'T1'; await settle()
  vm.filters.turnusCode = 'T2'; await settle()
  finishOld(); await settle()
  assert.equal(vm.stats.turnusCode, 'T2')
  assert.equal(vm.staffingRoles[0].accepted, 4)
  current = 5
  await vm.loadTurnusStats()
  assert.equal(vm.staffingRoles[0].accepted, 5)
  vm.filters.turnusCode = ''; await settle()
  assert.equal(vm.stats, null)
  assert.deepEqual(vm.staffingRoles, [])
})
test('an upstream error clears role counts rather than keeping misleading old data', async t => {
  let failing = false
  const { vm } = await mount(t, async () => failing
    ? new Response(JSON.stringify({ message: 'Brak połączenia ze zgłoszeniami.' }), { status: 502 })
    : new Response(JSON.stringify(stats('T1'))))
  vm.filters.turnusCode = 'T1'; await settle()
  failing = true
  await vm.loadTurnusStats()
  assert.deepEqual(vm.staffingRoles, [])
  assert.equal(vm.statsError, 'Brak połączenia ze zgłoszeniami.')
})
