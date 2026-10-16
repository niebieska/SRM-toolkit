import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, nextTick, reactive } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

const file = new URL('../views/TurnusListsView.vue', import.meta.url)
const { descriptor } = parse(await readFile(file, 'utf8'))
let source = compileScript(descriptor, { id: 'turnus-lists-test' }).content
source = source.replace(/import .* from 'vue-router'/, 'const useRoute = () => globalThis.__listsTestRoute;')
source = source.replace(/import BiuroHeader .*\n/, 'const BiuroHeader = {};\n')
source = source.replace(/import .* from '..\/stores\/auth'/, "const useAuthStore = () => ({ token: 'operator-token' });")
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path.endsWith('.js') ? path : path + '.js', file).href}'` : match
})
source = source.replace('__expose();', '').replace('return __returned__',
  '__expose({ data, error, loading, groups, group, search, sort, sortKey, direction, selectedView, customColumns, selectView, setColumn, clearFilters, load, exportList, exportFormat, exportCount, exporting, exportError, exportSuccess }); return __returned__')
const View = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default
View.render = () => null // Setup/watchers are exercised; visual acceptance is separate.
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, createText: () => ({}), setText() {}, createElement: () => ({}), patchProp() {}, setElementText() {} })
const settle = async () => { await nextTick(); await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
const body = code => ({ turnus: { turnusCode: code, turnusName: 'Turnus', startDate: '2027-01-17' }, entries: [
  { person: { registrationCode: 'P1', registrationType: 'PARTICIPANT', firstName: 'Anna', lastName: 'Nowak', minor: true }, age: 17 },
  { person: { registrationCode: 'S1', registrationType: 'STAFF', firstName: 'Jan', lastName: 'Kowalski', role: 'animator', minor: false }, age: 22, roleLabel: 'Animator' },
] })
const response = value => ({ ok: true, json: async () => value })
function mount() {
  globalThis.__listsTestRoute = reactive({ params: { code: 'T1' } })
  const app = renderer.createApp(View), view = app.mount({})
  return { app, view, route: globalThis.__listsTestRoute }
}

test('loads with operator credentials; separates groups and filters without additional requests', async () => {
  const previousFetch = globalThis.fetch
  let requests = 0
  globalThis.fetch = async (url, options) => {
    requests++
    assert.equal(url, 'http://localhost:8081/api/turnuses/T1/lists')
    assert.equal(options.headers.Authorization, 'Bearer operator-token')
    return response(body('T1'))
  }
  const { app, view } = mount()
  try {
    await settle()
    assert.equal(view.groups.length, 2)
    assert.equal(view.groups[0].rows[0].person.registrationType, 'PARTICIPANT')
    assert.equal(view.groups[1].rows[0].person.registrationType, 'STAFF')
    for (const group of view.groups) {
      const keys = group.columns.map(column => column.key)
      assert.ok(keys.includes('pesel'))
      assert.ok(keys.includes('address'))
      assert.ok(keys.includes('contactPhone'))
      assert.ok(!keys.includes('gender'))
      assert.ok(!keys.includes('guardianPhone'))
      assert.ok(!keys.includes('icePhone'))
      assert.equal(group.columns.find(column => column.key === 'personPhone').label, 'Telefon')
    }
    view.search = 'Anna'; await settle()
    assert.equal(view.groups[0].rows.length, 1)
    assert.equal(view.groups[1].rows.length, 0)
    view.clearFilters(); view.group = 'PARTICIPANT'; await settle()
    assert.equal(view.groups.length, 1)
    assert.equal(requests, 1)
  } finally { app.unmount(); globalThis.fetch = previousFetch }
})

test('switching Turnus ignores an older response and resets filters', async () => {
  const previousFetch = globalThis.fetch
  let resolveFirst
  globalThis.fetch = url => url.includes('/T1/') ? new Promise(resolve => { resolveFirst = resolve }) : Promise.resolve(response(body('T2')))
  const { app, view, route } = mount()
  try {
    view.search = 'old'; route.params.code = 'T2'; await settle()
    assert.equal(view.data.turnus.turnusCode, 'T2')
    assert.equal(view.search, '')
    resolveFirst(response(body('T1'))); await settle()
    assert.equal(view.data.turnus.turnusCode, 'T2')
  } finally { app.unmount(); globalThis.fetch = previousFetch }
})

test('failure clears personal data and retry can restore the list', async () => {
  const previousFetch = globalThis.fetch
  globalThis.fetch = async () => response(body('T1'))
  const { app, view } = mount()
  try {
    await settle()
    globalThis.fetch = async () => { throw new Error('connection refused') }
    await view.load()
    assert.equal(view.data, null)
    assert.match(view.error, /Nie można połączyć/)
    assert.equal(view.loading, false)
    globalThis.fetch = async () => response(body('T1'))
    await view.load()
    assert.equal(view.error, '')
    assert.equal(view.groups.length, 2)
  } finally { app.unmount(); globalThis.fetch = previousFetch }
})

test('presets and custom columns reuse the loaded people and preserve filters', async () => {
  const previousFetch = globalThis.fetch
  let requests = 0
  const payload = body('T1')
  payload.entries.push({ person: { registrationCode: 'P2', registrationType: 'PARTICIPANT', firstName: 'Jan', lastName: 'Adamski', minor: true }, age: 15 })
  globalThis.fetch = async () => { requests++; return response(payload) }
  const { app, view } = mount()
  try {
    await settle()
    view.selectView('insurance'); await settle()
    assert.deepEqual(view.groups[0].columns.map(column => column.key), ['firstName', 'lastName', 'pesel', 'address'])
    assert.ok(view.groups[1].columns.some(column => column.key === 'roleLabel'))
    view.selectView('groups'); await settle()
    assert.deepEqual(view.groups[0].rows.map(entry => entry.age), [15, 17])
    assert.equal(view.sortKey, 'age')
    view.search = 'Anna'
    view.selectView('custom'); await settle()
    assert.equal(view.search, 'Anna')
    assert.ok(view.customColumns.includes('gender'))
    assert.ok(!view.customColumns.includes('pesel'))
    view.setColumn('age', false); await settle()
    assert.equal(view.sortKey, 'lastName')
    view.setColumn('pesel', true); await settle()
    assert.ok(view.groups[0].columns.some(column => column.key === 'pesel'))
    for (const key of [...view.customColumns]) view.setColumn(key, false)
    await settle()
    assert.equal(view.groups[0].columns.length, 0)
    assert.equal(view.groups[1].columns.length, 0)
    view.selectView('onsite'); await settle()
    assert.equal(view.groups[0].columns.length, 5)
    assert.equal(requests, 1)
  } finally { app.unmount(); globalThis.fetch = previousFetch }
})

test('retry preserves the chosen view and columns; a different Turnus resets them', async () => {
  const previousFetch = globalThis.fetch
  globalThis.fetch = async url => response(body(url.includes('/T2/') ? 'T2' : 'T1'))
  const { app, view, route } = mount()
  try {
    await settle()
    view.selectView('groups'); view.selectView('custom'); view.setColumn('pesel', true)
    view.search = 'Anna'
    await view.load()
    assert.equal(view.selectedView, 'custom')
    assert.ok(view.customColumns.includes('pesel'))
    assert.equal(view.search, 'Anna')
    route.params.code = 'T2'; await settle()
    assert.equal(view.selectedView, 'general')
    assert.ok(!view.customColumns.includes('gender'))
    assert.equal(view.search, '')
  } finally { app.unmount(); globalThis.fetch = previousFetch }
})

test('export counts filtered people, prevents repeated clicks and disables empty selections', async () => {
  const previousFetch = globalThis.fetch, previousDocument = globalThis.document
  let downloads = 0, requests = 0
  globalThis.fetch = async () => { requests++; return response(body('T1')) }
  globalThis.document = { body: { appendChild() {} }, createElement: () => ({ click() { downloads++ }, remove() {} }) }
  const { app, view } = mount()
  try {
    await settle()
    assert.equal(view.exportCount, 2)
    view.search = 'Anna'; await settle()
    assert.equal(view.exportCount, 1)
    view.exportFormat = 'csv'
    const first = view.exportList()
    assert.equal(view.exporting, true)
    await view.exportList(); await first
    assert.equal(downloads, 1)
    assert.equal(view.exporting, false)
    assert.match(view.exportSuccess, /CSV/)
    assert.equal(requests, 1)
    view.search = 'not-found'; await settle()
    assert.equal(view.exportCount, 0)
    await view.exportList()
    assert.equal(downloads, 1)
    view.search = ''; view.selectView('custom')
    for (const key of [...view.customColumns]) view.setColumn(key, false)
    await settle()
    assert.equal(view.exportCount, 0)
  } finally { app.unmount(); globalThis.fetch = previousFetch; globalThis.document = previousDocument }
})

test('too-wide PDF gives actionable feedback and route changes cancel an obsolete download', async () => {
  const previousFetch = globalThis.fetch, previousDocument = globalThis.document
  let downloads = 0
  globalThis.fetch = async url => response(body(url.includes('/T2/') ? 'T2' : 'T1'))
  globalThis.document = { body: { appendChild() {} }, createElement: () => ({ click() { downloads++ }, remove() {} }) }
  const { app, view, route } = mount()
  try {
    await settle()
    view.selectView('custom')
    for (const key of ['email', 'guardianEmail', 'contactName', 'qualifications', 'address']) view.setColumn(key, true)
    view.exportFormat = 'pdf'
    await view.exportList()
    assert.match(view.exportError, /mniej kolumn/)
    assert.equal(downloads, 0)
    assert.equal(view.exporting, false)
    view.exportFormat = 'csv'
    const pending = view.exportList()
    route.params.code = 'T2'
    await pending; await settle()
    assert.equal(downloads, 0)
    assert.equal(view.data.turnus.turnusCode, 'T2')
  } finally { app.unmount(); globalThis.fetch = previousFetch; globalThis.document = previousDocument }
})
