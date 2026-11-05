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

test('staff personal data step rejects values above frontend limits without changing them', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const { vm } = mount(t, {
    firstName: 'A'.repeat(61),
    lastName: 'B'.repeat(81),
    motherName: 'C'.repeat(61),
    fatherName: 'D'.repeat(60),
    birthSurnameDifferent: true,
    birthSurname: 'E'.repeat(81),
    email: 'test@example.com',
    street: 'F'.repeat(101),
    houseNumber: '1'.repeat(21),
    city: 'G'.repeat(81),
  })

  vm.validate()

  assert.equal(vm.errors.firstName, 'Imię może mieć maksymalnie 60 znaków.')
  assert.equal(vm.errors.lastName, 'Nazwisko może mieć maksymalnie 80 znaków.')
  assert.equal(vm.errors.motherName, 'Imię matki może mieć maksymalnie 60 znaków.')
  assert.equal(vm.errors.fatherName, undefined)
  assert.equal(vm.errors.birthSurname, 'Nazwisko rodowe może mieć maksymalnie 80 znaków.')
  assert.equal(vm.errors.street, 'Ulica może mieć maksymalnie 100 znaków.')
  assert.equal(vm.errors.houseNumber, 'Nr domu/mieszkania może mieć maksymalnie 20 znaków.')
  assert.equal(vm.errors.city, 'Miejscowość może mieć maksymalnie 80 znaków.')
  assert.equal(vm.local.firstName.length, 61)
  assert.equal(vm.local.street.length, 101)
})

test('hidden staff ICE relation text does not block the step after choosing a standard relation', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const { vm } = mount(t, {
    pesel: '90010112345', turnusCode: 'TEST', firstName: 'Anna', lastName: 'Nowak',
    motherName: 'Maria', fatherName: 'Adam', birthSurnameDifferent: false,
    email: 'anna@example.com', phone: '123456789', street: 'Testowa', houseNumber: '1',
    postalCode: '00-001', city: 'Warszawa', iceFirstName: 'Jan', iceLastName: 'Nowak',
    icePhone: '123456789', iceRelation: 'matka', iceRelationOther: 'x'.repeat(81),
  })

  vm.validate()

  assert.equal(vm.errors.iceRelationOther, undefined)
})

test('staff ICE relation text is limited when the custom relation is selected', async t => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('[]')
  t.after(() => { globalThis.fetch = originalFetch })
  const { vm } = mount(t, {
    pesel: '90010112345', turnusCode: 'TEST', firstName: 'Anna', lastName: 'Nowak',
    motherName: 'Maria', fatherName: 'Adam', birthSurnameDifferent: false,
    email: 'anna@example.com', phone: '123456789', street: 'Testowa', houseNumber: '1',
    postalCode: '00-001', city: 'Warszawa', iceFirstName: 'Jan', iceLastName: 'Nowak',
    icePhone: '123456789', iceRelation: 'inna', iceRelationOther: 'x'.repeat(81),
  })

  vm.validate()

  assert.equal(vm.errors.iceRelationOther, 'Relacja ICE może mieć maksymalnie 80 znaków.')
})
