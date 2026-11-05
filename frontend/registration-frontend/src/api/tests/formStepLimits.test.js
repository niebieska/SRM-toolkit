import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRenderer, h, ref } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'

const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null,
  nextSibling: () => null, createText: () => ({}), setText() {}, createElement: () => ({}), patchProp() {}, setElementText() {} })

async function loadStep(relativePath, id) {
  const file = new URL(relativePath, import.meta.url)
  const { descriptor } = parse(await readFile(file, 'utf8'))
  let source = compileScript(descriptor, { id }).content
  source = source.replace(/import HealthQuestion .*\n/, 'const HealthQuestion = {};\n')
  source = source.replace(/from '([^']+)'/g, (match, path) => {
    if (path === 'vue') return `from '${import.meta.resolve('vue')}'`
    return path.startsWith('.') ? `from '${new URL(path, file).href}'` : match
  })
  source = source.replace('__expose();', '').replace('return __returned__',
    '__expose({ local, errors, validate }); return __returned__')
  const Step = (await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)).default
  Step.render = () => null
  return Step
}

async function mount(t, Step, props) {
  const instance = ref(null)
  const app = renderer.createApp({ render: () => h(Step, { ...props, ref: instance }) })
  app.mount({})
  t.after(() => app.unmount())
  return instance.value
}

const AddressStep = await loadStep('../../forms/uczestnik/steps/Step2Address.vue', 'participant-address-limits-test')
const ParticipantHealthStep = await loadStep('../../forms/uczestnik/steps/Step3Health.vue', 'participant-health-limits-test')
const StaffHealthStep = await loadStep('../../forms/kadra/steps/Step3Health.vue', 'staff-health-limits-test')

test('participant address limits validate participant and separate guardian address fields', async t => {
  const vm = await mount(t, AddressStep, { isAdult: false, formData: {
    street: 'A'.repeat(101), houseNumber: '1'.repeat(21), postalCode: '00-001', city: 'B'.repeat(81),
    sameAddress: false, parentStreet: 'C'.repeat(101), parentHouseNumber: '2'.repeat(21),
    parentPostalCode: '11-111', parentCity: 'D'.repeat(81),
  } })

  vm.validate()

  assert.equal(vm.errors.street, 'Ulica może mieć maksymalnie 100 znaków.')
  assert.equal(vm.errors.houseNumber, 'Nr domu/mieszkania może mieć maksymalnie 20 znaków.')
  assert.equal(vm.errors.city, 'Miejscowość może mieć maksymalnie 80 znaków.')
  assert.equal(vm.errors.parentStreet, 'Ulica opiekuna może mieć maksymalnie 100 znaków.')
  assert.equal(vm.errors.parentHouseNumber, 'Nr domu/mieszkania opiekuna może mieć maksymalnie 20 znaków.')
  assert.equal(vm.errors.parentCity, 'Miejscowość opiekuna może mieć maksymalnie 80 znaków.')
})

test('participant health questionnaire enforces detail length while preserving answers', async t => {
  const detail = 'a'.repeat(501)
  const vm = await mount(t, ParticipantHealthStep, { gender: 'female', formData: { health: {
    q1: { answer: 'tak', detail }, q2: { answer: 'nie', detail: '' }, q3: { answer: 'nie', detail: '' },
  } } })

  vm.validate()

  assert.equal(vm.errors.q1Detail, 'Szczegóły odpowiedzi może mieć maksymalnie 500 znaków.')
  assert.equal(vm.local.health.q1.detail, detail)
})

test('staff health questionnaire enforces health details and optional additional information', async t => {
  const longDetail = 'a'.repeat(501)
  const vm = await mount(t, StaffHealthStep, { gender: 'male', formData: { health: {
    q1: { answer: 'tak', detail: longDetail }, q2: { answer: 'nie', detail: '' },
    additionalInfo: longDetail,
  } } })

  vm.validate()

  assert.equal(vm.errors.q1Detail, 'Szczegóły chorób może mieć maksymalnie 500 znaków.')
  assert.equal(vm.errors.additionalInfo, 'Dodatkowe informacje może mieć maksymalnie 500 znaków.')
  assert.equal(vm.local.health.additionalInfo, longDetail)
})
