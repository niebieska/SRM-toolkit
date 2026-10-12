import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, nextTick, reactive } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

// Run the real editor setup/watchers; browser/router shells are stubbed, not layout.
const file = new URL('../views/TurnusEditorView.vue', import.meta.url)
const { descriptor } = parse(await readFile(file, 'utf8'))
let source = compileScript(descriptor, { id: 'turnus-staff-editor-test' }).content
source = source.replace(/import .* from 'vue-router'/, `const useRoute = () => globalThis.__turnusEditorTest.route, useRouter = () => globalThis.__turnusEditorTest.router;
const onBeforeRouteLeave = () => {}, onBeforeRouteUpdate = () => {};`)
source = source.replace(/import BiuroHeader .*\n/, 'const BiuroHeader = {};\n')
source = source.replace(/import .* from '..\/stores\/auth'/, "const useAuthStore = () => ({ token: 'operator-token', isAuthenticated: true });")
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path + '.js', file).href}'` : match
})
source = source.replace('__expose();', '')
source = source.replace('return __returned__', '__expose({ form, categoriesLoading, categoriesError, dirty, save, setRequirement, loadCategories }); return __returned__')
const { default: Editor } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
Editor.render = () => null
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {},
  parentNode: () => null, nextSibling: () => null, setText() {}, setElementText() {},
  patchProp() {}, createText: () => ({}), createElement: () => ({}) })
async function settle() {
  for (let i = 0; i < 5; i++) { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
}
const definition = { turnusCode: 'T1', turnusName: 'Winter', turnusDescription: null,
  seasonType: 'WINTER', startDate: '2027-01-17', endDate: '2027-01-24', version: 3,
  participantCapacity: 50, staffCapacity: 10, active: true, participantRegistrationOpen: false,
  staffRegistrationOpen: true, staffRequirements: [{ categoryCode: 'animator', requiredCount: 6 }] }
async function mount(t, fetch, isNew = false) {
  const previous = { fetch: globalThis.fetch, window: globalThis.window, shell: globalThis.__turnusEditorTest }
  globalThis.fetch = fetch
  globalThis.window = { addEventListener() {}, removeEventListener() {}, confirm: () => true }
  globalThis.__turnusEditorTest = { route: reactive({ name: isNew ? 'turnus-create' : 'turnus-detail',
    params: { code: 'T1' }, fullPath: isNew ? '/turnusy/new' : '/turnusy/T1' }), router: { replace() {}, push() {} } }
  const app = renderer.createApp(Editor)
  const vm = app.mount({})
  t.after(() => { app.unmount(); globalThis.fetch = previous.fetch; globalThis.window = previous.window;
    globalThis.__turnusEditorTest = previous.shell })
  await settle()
  return vm
}
test('new Summer editor loads categories before any season change', async t => {
  const calls = []
  const vm = await mount(t, async url => { calls.push(url); return new Response('[]') }, true)
  assert.ok(calls.some(url => url.endsWith('staffing-categories?seasonType=SUMMER')))
  assert.equal(vm.categoriesLoading, false)
  assert.equal(vm.dirty, false)
})
test('Winter targets save with version and flags; zero removes the target', async t => {
  let submitted
  const vm = await mount(t, async (url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer operator-token')
    if (url.includes('staffing-categories')) return new Response(JSON.stringify([{ code: 'animator' }, { code: 'kucharka' }]))
    if (options.method === 'PATCH') {
      submitted = JSON.parse(options.body)
      return new Response(JSON.stringify({ ...submitted, version: 4 }))
    }
    return new Response(JSON.stringify(definition))
  })
  assert.equal(vm.dirty, false)
  vm.setRequirement('animator', '0'); vm.setRequirement('kucharka', '2')
  assert.equal(vm.dirty, true)
  await vm.save()
  assert.deepEqual(submitted.staffRequirements, [{ categoryCode: 'kucharka', requiredCount: 2 }])
  assert.equal(submitted.version, 3); assert.equal(submitted.staffCapacity, 10)
  assert.equal(submitted.staffRegistrationOpen, true)
  assert.equal(vm.form.version, 4); assert.equal(vm.dirty, false)
})
test('category failure preserves targets and blocks saving until retry succeeds', async t => {
  let failing = true, writes = 0
  const vm = await mount(t, async (url, options) => {
    if (url.includes('staffing-categories')) return failing ? new Response('{}', { status: 503 })
      : new Response(JSON.stringify([{ code: 'animator' }]))
    if (options.method === 'PATCH') { writes++; return new Response(JSON.stringify(definition)) }
    return new Response(JSON.stringify(definition))
  })
  assert.ok(vm.categoriesError)
  assert.deepEqual(vm.form.staffRequirements, definition.staffRequirements)
  await vm.save(); assert.equal(writes, 0)
  failing = false
  await vm.loadCategories(); assert.equal(vm.categoriesError, '')
  vm.setRequirement('animator', '7')
  await vm.save(); assert.equal(writes, 1)
})
