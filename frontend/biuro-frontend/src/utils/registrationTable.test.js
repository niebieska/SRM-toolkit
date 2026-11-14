import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, nextTick, reactive, watch } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

const file = new URL('../components/RegistrationTable.vue', import.meta.url)
const { descriptor } = parse(await readFile(file, 'utf8'))
const detailFile = new URL('../components/RegistrationDetailModal.vue', import.meta.url)
const { descriptor: detailDescriptor } = parse(await readFile(detailFile, 'utf8'))
let source = compileScript(descriptor, { id: 'registration-table-test' }).content
source = source.replace("import {useAuthStore} from '../stores/auth'", "const useAuthStore = () => ({ token: 'test-token' })")
source = source.replace("import {fetchRegistrations} from '../api/registrationApi'", "const fetchRegistrations = (...args) => globalThis.__registrationTableTest.fetchRegistrations(...args)")
source = source.replace(/import (StatusBadge|AcceptRejectModal|RegistrationDetailModal) from '[^']+'\n/g, 'const $1 = {};\n')
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path.endsWith('.js') ? path : path + '.js', file).href}'` : match
})
source = source.replace('__expose();', '')
source = source.replace('return __returned__', '__expose({ loading, errorMessage, hasLoadedRegistrations, registrations, sortKey, sortDirection, ariaSort, toggleSort, loadRegistrations }); return __returned__')
const { default: RegistrationTable } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
RegistrationTable.render = () => null

const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, setText() {}, setElementText() {}, patchProp() {}, createText: () => ({}), createElement: () => ({}) })

async function settle() {
  for (let i = 0; i < 5; i++) { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
}

async function mount(t, fetchRegistrations, onAutoRefresh = () => {}) {
  const previous = { window: globalThis.window, document: globalThis.document, shell: globalThis.__registrationTableTest }
  const listeners = new Map()
  const intervals = new Map()
  let nextTimerId = 0
  globalThis.window = {
    setInterval(callback) { const id = ++nextTimerId; intervals.set(id, callback); return id },
    clearInterval(id) { intervals.delete(id) },
  }
  globalThis.document = {
    visibilityState: 'hidden',
    addEventListener(name, callback) { listeners.set(name, callback) },
    removeEventListener(name, callback) { listeners.delete(name) },
    dispatchVisibilityChange() { listeners.get('visibilitychange')?.() },
  }
  globalThis.__registrationTableTest = { fetchRegistrations }
  const filters = reactive({ status: '', registrationType: '', turnusCode: '', search: '' })
  const app = renderer.createApp(RegistrationTable, { filters, onAutoRefresh })
  const vm = app.mount({})
  t.after(() => {
    app.unmount()
    globalThis.window = previous.window
    globalThis.document = previous.document
    globalThis.__registrationTableTest = previous.shell
  })
  await settle()
  return { vm, filters, browser: { listeners, intervals, tick: () => [...intervals.values()].forEach(callback => callback()) } }
}

function deferred() {
  let resolve, reject
  const promise = new Promise((resolvePromise, rejectPromise) => { resolve = resolvePromise; reject = rejectPromise })
  return { promise, resolve, reject }
}

test('default queue has no false date sort indication until a header is selected', async t => {
  const { vm } = await mount(t, async () => [])

  assert.equal(vm.sortKey, null)
  assert.equal(vm.ariaSort('createdAt'), 'none')
  vm.toggleSort('createdAt')
  assert.equal(vm.ariaSort('createdAt'), 'ascending')
})

test('capacity eligibility controls accept buttons in the table and detail modal', () => {
  assert.match(descriptor.template.content,
    /v-if="canAcceptRegistration\(registration, capacityInfoFor\(registration\)\)"/)
  assert.match(descriptor.template.content, /:capacity-info="detailCapacityInfo"/)
  assert.match(detailDescriptor.template.content,
    /v-if="canAcceptRegistration\(detail, capacityInfo\)"/)
})

test('automatic registration refresh requests a stats refresh on tab return and polling', async t => {
  let statsRefreshes = 0
  const { browser } = await mount(t, async () => [], () => { statsRefreshes += 1 })

  globalThis.document.visibilityState = 'visible'
  globalThis.document.dispatchVisibilityChange()
  await settle()
  assert.equal(statsRefreshes, 1)

  browser.tick()
  await settle()
  assert.equal(statsRefreshes, 2)
})

test('filter changes hide old rows until matching data loads and keep them hidden on failure', async t => {
  const nextFilterRequest = deferred()
  const calls = []
  const { vm, filters } = await mount(t, async (_token, requestFilters) => {
    calls.push({ ...requestFilters })
    if (requestFilters.status === 'NEW') return nextFilterRequest.promise
    return [{ registrationCode: 'OLD', status: 'ACCEPTED', createdAt: '2026-10-01T10:00:00Z' }]
  })

  await vm.loadRegistrations()
  assert.equal(vm.hasLoadedRegistrations, true)
  assert.equal(vm.registrations[0].registrationCode, 'OLD')

  vm.errorMessage = 'Poprzedni błąd'
  filters.status = 'NEW'
  await settle()

  assert.deepEqual(calls.at(-1), { status: 'NEW', registrationType: '', turnusCode: '' })
  assert.equal(vm.loading, true)
  assert.equal(vm.hasLoadedRegistrations, false)
  assert.equal(vm.errorMessage, '')
  assert.match(descriptor.template.content, /<table v-if="hasLoadedRegistrations"/)
  assert.match(descriptor.template.content, /<slot name="header-actions"\s*\/>/)
  assert.match(descriptor.template.content, /v-if="visibleRegistrations\.length === 0"/)

  nextFilterRequest.reject(new Error('Brak połączenia'))
  await settle()
  assert.equal(vm.loading, false)
  assert.equal(vm.hasLoadedRegistrations, false)
  assert.equal(vm.errorMessage, 'Brak połączenia')
})

test('a stale first load cannot clear the loading state for a queued filter request', async t => {
  const initialRequest = deferred()
  const filteredRequest = deferred()
  let calls = 0
  const { vm, filters } = await mount(t, (_token, requestFilters) => {
    calls += 1
    return requestFilters.status === 'NEW' ? filteredRequest.promise : initialRequest.promise
  })
  const loadingTransitions = []
  watch(() => vm.loading, value => loadingTransitions.push(value), { flush: 'sync' })

  const firstLoad = vm.loadRegistrations()
  filters.status = 'NEW'
  await settle()
  assert.equal(vm.loading, true)
  loadingTransitions.length = 0

  initialRequest.resolve([{ registrationCode: 'OLD', status: 'NEW' }])
  await settle()
  assert.equal(calls, 2)
  assert.equal(vm.loading, true)
  assert.deepEqual(loadingTransitions, [])

  filteredRequest.resolve([{ registrationCode: 'NEW', status: 'NEW' }])
  await firstLoad
  assert.equal(vm.loading, false)
  assert.deepEqual(loadingTransitions, [false])
  assert.equal(vm.registrations[0].registrationCode, 'NEW')
})
