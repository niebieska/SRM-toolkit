import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, nextTick, h, ref } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

const file = new URL('../../forms/kadra/steps/Step1PersonalData.vue', import.meta.url)
const { descriptor } = parse(await readFile(file, 'utf8'))
let source = compileScript(descriptor, { id: 'staff-personal-data-test' }).content
source = source.replace(/import PhoneInput .*\n/, 'const PhoneInput = {};\n')
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path, file).href}'` : match
})
source = source.replace('__expose();', '').replace('return __returned__',
  '__expose({ local, birthSurname, errors, validate, goNext }); return __returned__')
const Step = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default
Step.render = () => null
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, createText: () => ({}), setText() {}, createElement: () => ({}), patchProp() {}, setElementText() {} })
function mount(t, formData) {
  const instance = ref(null), events = []
  const app = renderer.createApp({ render: () => h(Step, { formData, ref: instance,
    'onUpdate:formData': value => events.push(value) }) })
  app.mount({})
  t.after(() => app.unmount())
  return { vm: instance.value, events }
}
test('birth surname follows current surname by default and preserves manual text across toggles and remount', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const { vm } = mount(t, { lastName: 'Nowak', birthSurname: 'Kowalska', birthSurnameDifferent: false })
  assert.equal(vm.birthSurname, 'Nowak')
  vm.local.lastName = 'Nowak-Zając'
  await nextTick()
  assert.equal(vm.birthSurname, 'Nowak-Zając')
  vm.local.birthSurnameDifferent = true
  assert.equal(vm.birthSurname, 'Kowalska')
  vm.local.birthSurnameDifferent = false
  assert.equal(vm.birthSurname, 'Nowak-Zając')
  vm.local.birthSurnameDifferent = true
  const returned = mount(t, { ...vm.local })
  assert.equal(returned.vm.birthSurname, 'Kowalska')
  vm.validate()
  assert.ok(vm.errors.motherName)
  assert.ok(vm.errors.fatherName)
  vm.local.motherName = 'Maria'; vm.local.fatherName = 'Adam'; vm.local.birthSurname = ''
  vm.validate()
  assert.equal(vm.errors.motherName, undefined)
  assert.equal(vm.errors.fatherName, undefined)
  assert.ok(vm.errors.birthSurname)
  vm.local.birthSurnameDifferent = false
  vm.validate()
  assert.equal(vm.errors.birthSurname, undefined)
})
