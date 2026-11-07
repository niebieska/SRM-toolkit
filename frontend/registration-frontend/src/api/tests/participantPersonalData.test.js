import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, h, ref } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

const file = new URL('../../forms/uczestnik/steps/Step1PersonalData.vue', import.meta.url)
const { descriptor } = parse(await readFile(file, 'utf8'))
let source = compileScript(descriptor, { id: 'participant-personal-data-test' }).content
source = source.replace(/import PhoneInput .*\n/, 'const PhoneInput = {};\n')
source = source.replace(/from '([^']+)'/g, (match, path) => {
  if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
  return path.startsWith('.') ? `from '${new URL(path, file).href}'` : match
})
source = source.replace('__expose();', '').replace('return __returned__',
  '__expose({ local, errors, validate }); return __returned__')
const Step = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default
Step.render = () => null
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, createText: () => ({}), setText() {}, createElement: () => ({}), patchProp() {}, setElementText() {} })

function mount(t, formData) {
  const instance = ref(null)
  const app = renderer.createApp({ render: () => h(Step, { formData, ref: instance }) })
  app.mount({})
  t.after(() => app.unmount())
  return instance.value
}

test('hidden participant ICE relation text does not block the step after choosing a standard relation', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const vm = mount(t, {
    pesel: '90010112345', turnusCode: 'TEST', firstName: 'Anna', lastName: 'Nowak',
    email: 'anna@example.com', phone: '123456789', iceFirstName: 'Jan', iceLastName: 'Nowak',
    icePhone: '123456789', iceRelation: 'matka', iceRelationOther: 'x'.repeat(81),
  })

  vm.validate()

  assert.equal(vm.errors.iceRelationOther, undefined)
})

test('participant ICE relation text is limited when the custom relation is selected', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const vm = mount(t, {
    pesel: '90010112345', turnusCode: 'TEST', firstName: 'Anna', lastName: 'Nowak',
    email: 'anna@example.com', phone: '123456789', iceFirstName: 'Jan', iceLastName: 'Nowak',
    icePhone: '123456789', iceRelation: 'inna', iceRelationOther: 'x'.repeat(81),
  })

  vm.validate()

  assert.equal(vm.errors.iceRelationOther, 'Relacja ICE może mieć maksymalnie 80 znaków.')
})

test('minor participant can omit personal email while the adult email remains required', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const minor = mount(t, {pesel: '10210112312', email: ''})
  const adult = mount(t, {pesel: '90010112345', email: ''})

  minor.validate()
  adult.validate()

  assert.equal(minor.errors.email, undefined)
  assert.equal(adult.errors.email, 'Podaj adres e-mail.')
})

test('minor participant email remains validated when provided', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const vm = mount(t, {pesel: '10210112312', email: 'nieprawidlowy-adres'})

  vm.validate()

  assert.equal(vm.errors.email, 'Podaj prawidłowy adres e-mail.')
})
