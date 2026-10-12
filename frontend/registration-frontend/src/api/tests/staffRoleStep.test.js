import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, nextTick, reactive, h, ref } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

// Execute the real component's setup/watchers using Vue's renderer without a DOM.
// These tests cover form state and emitted navigation, not visual layout.
const componentUrl = new URL('../../forms/kadra/steps/Step2Role.vue', import.meta.url)
const { descriptor } = parse(await readFile(componentUrl, 'utf8'))
let source = compileScript(descriptor, { id: 'staff-role-step-test' }).content
source = source.replace("from 'vue'", `from '${import.meta.resolve('vue')}'`)
source = source.replace(/from '([^']+)'/g, (match, path) =>
  path.startsWith('.') ? `from '${new URL(path, componentUrl).href}'` : match)
source = source.replace('__expose();', '')
source = source.replace('return __returned__', '__expose({ local, selectedCertificates, catalogue, loading, roleError, goPrev, goNext }); return __returned__')
const { default: Step } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
Step.render = () => null
const renderer = createRenderer({
  createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, setText() {}, setElementText() {}, patchProp() {},
  createText: () => ({}), createElement: () => ({}),
})
const catalogue = async season => JSON.parse(await readFile(new URL(
  `../../../../../backend/biuro-api/src/main/resources/staff-seasons/${season}.json`, import.meta.url), 'utf8'))
const summer = await catalogue('summer'), winter = await catalogue('winter')
async function settle() {
  for (let i = 0; i < 5; i++) { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
}
async function mount(t, formData, isAdult = true) {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async url => new Response(JSON.stringify(String(url).includes('WINTER') ? winter : summer))
  const props = reactive({ formData, isAdult })
  const events = []
  const instance = ref(null)
  const app = renderer.createApp({ render: () => h(Step, {
    ...props, ref: instance,
    'onUpdate:formData': value => events.push(['data', value]),
    onPrev: () => events.push(['prev']), onNext: () => events.push(['next']),
  }) })
  app.mount({})
  t.after(() => { app.unmount(); globalThis.fetch = originalFetch })
  await settle()
  return { vm: instance.value, events, async update(values) {
    Object.assign(props, values)
    await settle()
  } }
}

test('Back and remount preserve selected Summer certificates and descriptions', async t => {
  const form = { turnusCode: 'SUMMER', firstName: 'Anna', role: 'ksiadz', subrole: 'pomoc',
    certificates: { kpp: true, inne: true }, certificateDetails: { inne: 'Additional training' } }
  const { vm, events } = await mount(t, form)
  vm.goPrev()
  assert.equal(events.at(-1)[0], 'prev')
  const saved = events.find(event => event[0] === 'data')[1]
  assert.deepEqual(saved.certificates, form.certificates)
  assert.deepEqual(saved.certificateDetails, form.certificateDetails)
  const returned = await mount(t, saved)
  returned.vm.goNext()
  assert.equal(returned.events.at(-1)[0], 'next')
  assert.deepEqual(returned.events[0][1].certificates, form.certificates)
})

test('Turnus change preserves personal data and removes unavailable certificate selections', async t => {
  const { vm, events, update } = await mount(t, { turnusCode: 'SUMMER', firstName: 'Anna',
    role: 'ksiadz', subrole: 'pomoc', certificates: { kpp: true }, certificateDetails: {} })
  await update({ formData: { turnusCode: 'WINTER' } })
  vm.goNext()
  const saved = events.find(event => event[0] === 'data')[1]
  assert.equal(saved.turnusCode, 'WINTER')
  assert.equal(saved.firstName, 'Anna')
  assert.deepEqual(saved.certificates, {})
})

test('Winter minor candidate can advance; instructor needs exactly one discipline', async t => {
  const { vm, events, update } = await mount(t, { turnusCode: 'WINTER', role: 'animator',
    subrole: 'kandydat_na_animatora', certificates: {}, certificateDetails: {} }, false)
  vm.goNext()
  assert.equal(events.at(-1)[0], 'next')
  await update({ isAdult: true })
  vm.local.role = 'instruktor'
  vm.local.subrole = ''
  await settle()
  events.length = 0
  vm.goNext()
  assert.equal(events.length, 0)
  assert.ok(vm.roleError)
  vm.local.subrole = 'narty'
  await settle()
  vm.goNext()
  assert.equal(events.at(-1)[0], 'next')
  await update({ isAdult: false })
  assert.equal(vm.local.role, '')
  assert.equal(vm.local.subrole, '')
})
