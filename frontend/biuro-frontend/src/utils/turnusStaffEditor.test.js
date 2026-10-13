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
source = source.replace(/import TurnusFormField .*\n/, 'const TurnusFormField = {};\n')
source = source.replace(/import DiscardChangesDialog .*\n/, 'const DiscardChangesDialog = {};\n')
source = source.replace(/import .* from '..\/stores\/auth'/, "const useAuthStore = () => ({ token: 'operator-token', isAuthenticated: true });")
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path + '.js', file).href}'` : match
})Ca
source = source.replace('__expose();', '')
source = source.replace('return __returned__', '__expose({ error, validationError, validateField, canLeave, reload, discardPrompt, decideDiscard, form, fieldErrors, plannedStaff, categoriesLoading, categoriesError, dirty, save, setRequirement, loadCategories }); return __returned__')
const { default: Editor } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
Editor.render = () => null
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {},
  parentNode: () => null, nextSibling: () => null, setText() {}, setElementText() {},
  patchProp() {}, createText: () => ({}), createElement: () => ({}) })
async function settle() {
  for (let i = 0; i < 5; i++) { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
}
const definition = { turnusCode: 'T1', turnusName: 'Winter', turnusDescription: null,
  turnusLocation: 'Gliczarów Górny', seasonYear: 2027, minAge: 14, seasonType: 'WINTER', startDate: '2027-01-17', endDate: '2027-01-24', version: 3,
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


test('invalid dates and capacities block writes with field feedback', async t => {
  let writes = 0
  const vm = await mount(t, async (url, options) => {
    if (url.includes('staffing-categories')) return new Response(JSON.stringify([{ code: 'animator' }]))
    if (options.method === 'PATCH') writes++
    return new Response(JSON.stringify(definition))
  })
  vm.form.endDate = '2027-01-16'
  vm.form.staffCapacity = 0
  await vm.save()
  assert.equal(writes, 0)
  assert.ok(vm.fieldErrors.endDate)
  assert.ok(vm.fieldErrors.staffCapacity)
  vm.form.endDate = definition.endDate
  vm.form.staffCapacity = 10
  await vm.save()
  assert.equal(writes, 1)
})

test('staffing total reacts independently of staff capacity', async t => {
  const vm = await mount(t, async url => new Response(JSON.stringify(url.includes('staffing-categories')
    ? [{ code: 'animator' }, { code: 'kucharka' }] : definition)))
  assert.equal(vm.plannedStaff, 6)
  vm.setRequirement('kucharka', '5')
  assert.equal(vm.plannedStaff, 11)
  assert.equal(vm.form.staffCapacity, 10)
  vm.setRequirement('animator', '0')
  assert.equal(vm.plannedStaff, 5)
})

test('creation preserves defaults, all fields and navigation, preventing duplicate writes', async t => {
  let posts = 0, finish
  const calls = []
  const vm = await mount(t, async (url, options) => {
    if (url.includes('staffing-categories')) return new Response('[]')
    posts++
    const value = JSON.parse(options.body)
    calls.push(value)
    await new Promise(resolve => { finish = resolve })
    return new Response(JSON.stringify({ ...value, version: 0 }))
  }, true)
  Object.assign(vm.form, definition, { staffRequirements: [], turnusDescription: '', seasonType: 'SUMMER',
    active: false, participantRegistrationOpen: false, staffRegistrationOpen: false, version: null })
  await settle()
  const first = vm.save()
  await settle()
  await vm.save()
  assert.equal(posts, 1)
  assert.equal(calls[0].turnusLocation, definition.turnusLocation)
  assert.equal(calls[0].minAge, 14)
  assert.equal(calls[0].participantRegistrationOpen, false)
  assert.equal(calls[0].staffRegistrationOpen, false)
  const navigations = []
  let completeNavigation
  globalThis.__turnusEditorTest.router.replace = value => {
    navigations.push(value)
    return new Promise(resolve => { completeNavigation = resolve })
  }
  finish(); await settle()
  await vm.save()
  assert.equal(posts, 1, 'submit stays locked while successful creation navigates')
  completeNavigation(); await first
  assert.deepEqual(navigations, [{ name: 'turnus-detail', params: { code: definition.turnusCode } }])
  assert.equal(vm.dirty, false)
})


test('unsaved navigation offers stay/discard without clearing form data', async t => {
  const vm = await mount(t, async url => new Response(JSON.stringify(url.includes('staffing-categories')
    ? [{ code: 'animator' }] : definition)))
  assert.equal(vm.canLeave({ path: '/turnusy', name: 'turnus-list', params: {} }), true)
  vm.form.turnusName = 'Unsaved name'
  const stay = vm.canLeave({ path: '/turnusy', name: 'turnus-list', params: {} })
  assert.equal(vm.discardPrompt.kind, 'leave')
  vm.decideDiscard(false)
  assert.equal(await stay, false)
  assert.equal(vm.form.turnusName, 'Unsaved name')
  const leave = vm.canLeave({ path: '/turnusy', name: 'turnus-list', params: {} })
  vm.decideDiscard(true)
  assert.equal(await leave, true)
})

test('reload cancels safely or replaces edits only after confirmation', async t => {
  const vm = await mount(t, async url => new Response(JSON.stringify(url.includes('staffing-categories')
    ? [{ code: 'animator' }] : definition)))
  vm.form.turnusName = 'Unsaved name'
  const cancelled = vm.reload()
  assert.equal(vm.discardPrompt.kind, 'reload')
  vm.decideDiscard(false); await cancelled
  assert.equal(vm.form.turnusName, 'Unsaved name')
  const approved = vm.reload()
  vm.decideDiscard(true); await approved
  assert.equal(vm.form.turnusName, definition.turnusName)
  assert.equal(vm.dirty, false)
})

// Verify the editor's optional confirmation hook is honored before logout.
const headerFile = new URL('../components/BiuroHeader.vue', import.meta.url)
const headerDescriptor = parse(await readFile(headerFile, 'utf8')).descriptor
let headerSource = compileScript(headerDescriptor, { id: 'biuro-header-confirmation-test' }).content
headerSource = headerSource.replace(/import .* from 'vue-router'/, 'const useRouter = () => globalThis.__headerConfirmationTest.router;')
headerSource = headerSource.replace(/import .* from '..\/stores\/auth'/, 'const useAuthStore = () => globalThis.__headerConfirmationTest.auth;')
headerSource = headerSource.replace('__expose();', '')
headerSource = headerSource.replace('return __returned__', '__expose({ logout }); return __returned__')
const { default: Header } = await import(`data:text/javascript;base64,${Buffer.from(headerSource).toString('base64')}`)
Header.render = () => null

test('logout honors stay/discard and busy state before clearing authentication', async t => {
  const events = []
  const previous = globalThis.__headerConfirmationTest
  globalThis.__headerConfirmationTest = { auth: { logout: () => events.push('clear-auth') },
    router: { push: path => events.push(path) } }
  const apps = []
  t.after(() => { apps.forEach(app => app.unmount()); globalThis.__headerConfirmationTest = previous })
  function header(confirmed, busy = false) {
    const app = renderer.createApp(Header, { unsavedChanges: true, busy,
      confirmDiscard: async kind => { events.push(kind); return confirmed } })
    apps.push(app)
    return app.mount({})
  }
  await header(false).logout()
  assert.deepEqual(events, ['logout']) // The confirmation kind only; auth was not cleared.
  events.length = 0
  await header(true).logout()
  assert.deepEqual(events, ['logout', 'clear-auth', '/login'])
  events.length = 0
  await header(true, true).logout()
  assert.deepEqual(events, [])
})


test('validation summary clears after the last correction, not the first', async t => {
  const vm = await mount(t, async url => new Response(JSON.stringify(url.includes('staffing-categories')
    ? [{ code: 'animator' }] : definition)))
  vm.form.staffCapacity = 0
  vm.form.minAge = -1
  await vm.save()
  assert.ok(vm.validationError)
  vm.form.minAge = 14
  vm.validateField('minAge')
  assert.ok(vm.validationError)
  assert.ok(vm.fieldErrors.staffCapacity)
  vm.form.staffCapacity = 10
  vm.validateField('staffCapacity')
  assert.deepEqual(vm.fieldErrors, {})
  assert.equal(vm.validationError, '')
})

test('correcting fields does not dismiss a genuine backend failure', async t => {
  const vm = await mount(t, async (url, options) => {
    if (url.includes('staffing-categories')) return new Response(JSON.stringify([{ code: 'animator' }]))
    if (options.method === 'PATCH') return new Response(JSON.stringify({ message: 'Biuro chwilowo niedostępne.' }), { status: 503 })
    return new Response(JSON.stringify(definition))
  })
  vm.form.turnusName = 'Changed name'
  await vm.save()
  assert.equal(vm.error, 'Biuro chwilowo niedostępne.')
  vm.form.staffCapacity = 0
  vm.validateField('staffCapacity')
  vm.form.staffCapacity = 10
  vm.validateField('staffCapacity')
  assert.equal(vm.error, 'Biuro chwilowo niedostępne.')
  assert.deepEqual(vm.fieldErrors, {})
  assert.equal(vm.dirty, true)
})
