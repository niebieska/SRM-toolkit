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
  '__expose({ data, error, loading, groups, roles, group, role, search, sort, clearFilters, load }); return __returned__')
const View = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default
View.render = () => null // Setup/watchers are exercised; visual acceptance is separate.
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, createText: () => ({}), setText() {}, createElement: () => ({}), patchProp() {}, setElementText() {} })
const settle = async () => { await nextTick(); await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
const body = code => ({ turnus: { turnusCode: code, turnusName: 'Turnus', startDate: '2027-01-17' }, entries: [
  { person: { registrationCode: 'P1', registrationType: 'PARTICIPANT', firstName: 'Anna', lastName: 'Nowak' }, age: 17 },
  { person: { registrationCode: 'S1', registrationType: 'STAFF', firstName: 'Jan', lastName: 'Kowalski', role: 'animator' }, age: 22, roleLabel: 'Animator' },
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
    view.search = 'Anna'; await settle()
    assert.equal(view.groups[0].rows.length, 1)
    assert.equal(view.groups[1].rows.length, 0)
    view.clearFilters(); view.role = 'animator'; view.group = 'PARTICIPANT'; await settle()
    assert.equal(view.role, '')
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
