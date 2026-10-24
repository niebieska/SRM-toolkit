import test from 'node:test'
import assert from 'node:assert/strict'
import { registrationSections, combineQualificationSections, formatFieldValue } from './registrationDetails.js'

const registration = (registrationType, minor) => ({ registrationType, minor, age: 25,
  payload: { person: { firstName: 'Anna', isAdult: !minor }, address: { city: 'Łódź' },
    guardian: { firstName: 'Maria' }, ice: { firstName: 'Adam' },
    role: 'animator', subrole: 'kandydat_na_animatora', certificates: { kpp: true },
    certificateDetails: { kpp: 'Kurs' }, consents: { imageUsage: false },
    health: { q1: { question: 'Zapisane pytanie?', answer: 'tak', detail: 'Pierwsza\ndruga linia' } } } })
const paths = sections => sections.flatMap(section => section.fields.map(field => field.path))

test('participant/staff and adult/minor variants use saved classification rather than current age', () => {
  for (const type of ['PARTICIPANT', 'STAFF']) {
    for (const minor of [false, true]) {
      const data = registration(type, minor)
      const before = JSON.stringify(data)
      const sections = registrationSections(data), keys = sections.map(section => section.key)
      assert.equal(keys.includes('guardian'), minor)
      assert.equal(keys.includes('ice'), !minor)
      assert.ok(keys.includes('person'))
      assert.ok(keys.includes('address'))
      assert.ok(keys.includes('consents'))
      assert.equal(keys.includes('certificates'), type === 'STAFF')
      assert.equal(keys.includes('certificateDetails'), type === 'STAFF')
      assert.equal(paths(sections).includes('role'), type === 'STAFF')
      assert.equal(paths(sections).includes('subrole'), type === 'STAFF')
      assert.equal(JSON.stringify(data), before)
    }
  }
})

test('health questions stay with answers, details and unknown saved fields', () => {
  const data = registration('STAFF', false)
  data.payload.health.additionalInfo = { question: 'Informacje organizacyjne', value: 'Późny przyjazd' }
  data.payload.health.q1.legacyNote = 'keep'
  const questions = registrationSections(data).find(section => section.key === 'health').questions
  assert.equal(questions[0].question, 'Zapisane pytanie?')
  assert.deepEqual(questions[0].fields.map(field => field.path), ['health.q1.answer', 'health.q1.detail', 'health.q1.legacyNote'])
  assert.equal(questions[0].fields[1].value, 'Pierwsza\ndruga linia')
  assert.equal(questions[1].question, 'Informacje organizacyjne')
  assert.equal(questions[1].fields[0].value, 'Późny przyjazd')
})

test('parent and guardian names are shown last in guardian details', () => {
  const data = registration('PARTICIPANT', true)
  data.payload.guardian = {
    names: 'Maria i Jan Kowalscy',
    contact: { phone: '123456789', email: 'rodzic@example.test' },
    firstName: 'Maria',
    lastName: 'Kowalska',
  }
  const guardian = registrationSections(data).find(section => section.key === 'guardian')
  assert.equal(guardian.fields.at(-1).path, 'guardian.names')
})

test('consent details omit mandatory acknowledgments but retain optional and unknown entries', () => {
  const data = registration('PARTICIPANT', false)
  data.payload.consents = {
    dataProcessing: true,
    regulations: true,
    truthDeclaration: true,
    imageUsage: false,
    futureOptionalConsent: true,
  }
  const consents = registrationSections(data).find(section => section.key === 'consents')
  assert.deepEqual(consents.fields.map(field => field.path), [
    'consents.imageUsage', 'consents.futureOptionalConsent',
  ])
})

test('consent section is omitted when it contains only mandatory acknowledgments', () => {
  const data = registration('PARTICIPANT', false)
  data.payload.consents = { dataProcessing: true, regulations: true, truthDeclaration: true }
  assert.equal(registrationSections(data).some(section => section.key === 'consents'), false)
})

test('qualification sections are omitted when none are selected and stale details are ignored', () => {
  const data = registration('STAFF', false)
  data.payload.certificates = { kpp: false }
  data.payload.certificateDetails = { kpp: 'Stara informacja' }
  const sections = registrationSections(data)
  assert.equal(sections.some(section => ['certificates', 'certificateDetails'].includes(section.key)), false)

  data.payload.certificates = { kpp: true }
  const selectedSections = registrationSections(data)
  assert.deepEqual(selectedSections.find(section => section.key === 'certificates').fields.map(field => field.path), ['certificates.kpp'])
  assert.deepEqual(selectedSections.find(section => section.key === 'certificateDetails').fields.map(field => field.path), ['certificateDetails.kpp'])
})

test('the generic Inne selection is represented by its description, not an Inne: Tak row', () => {
  const data = registration('STAFF', false)
  data.payload.certificates = { inne: true }
  data.payload.certificateDetails = { inne: 'Instruktor' }
  const sections = registrationSections(data)
  assert.equal(sections.some(section => section.fields.some(field => field.path === 'certificates.inne')), false)
  assert.deepEqual(sections.find(section => section.key === 'certificateDetails').fields.map(field => ({
    label: field.label,
    value: field.value,
  })), [{ label: 'Inne uprawnienia', value: 'Instruktor' }])

  data.payload.certificates = {}
  const legacySections = registrationSections(data)
  assert.equal(legacySections.some(section => section.fields.some(field => field.path === 'certificates.inne')), false)
  assert.deepEqual(legacySections.find(section => section.key === 'certificateDetails').fields.map(field => field.value), ['Instruktor'])
})

test('custom qualification details without a checkbox row stay beside the staff role', () => {
  const data = registration('STAFF', false)
  data.payload.certificates = { inne: true }
  data.payload.certificateDetails = { inne: 'pomocnik instruktora sitn' }
  const sections = combineQualificationSections(registrationSections(data))
  const keys = sections.map(section => section.key)
  assert.equal(keys.indexOf('certificates'), keys.indexOf('role') + 1)
  assert.ok(keys.indexOf('health') > keys.indexOf('certificates'))
  assert.deepEqual(sections.find(section => section.key === 'certificates').fields.map(field => ({
    label: field.label,
    value: field.value,
  })), [{ label: 'Inne uprawnienia', value: 'pomocnik instruktora sitn' }])
})

test('older missing fields and unfamiliar sections remain readable without fabricating values', () => {
  const data = { payload: { person: { firstName: 'Anna' }, guardian: null, ice: null,
    extra: 'saved', customSection: { nested: { value: 0 }, entries: [{ flag: false }] } } }
  const sections = registrationSections(data)
  assert.ok(paths(sections).includes('guardian'))
  assert.ok(paths(sections).includes('ice'))
  const other = sections.find(section => section.key === 'other')
  assert.equal(other.label, 'Pozostałe informacje')
  assert.deepEqual(other.fields.map(field => field.path), ['extra', 'customSection.nested.value', 'customSection.entries'])
  assert.equal(formatFieldValue(false), 'Nie')
  assert.equal(formatFieldValue(null), 'Brak danych')
  assert.equal(formatFieldValue(0), '0')
  assert.equal(formatFieldValue('unknown', 'person.gender'), 'unknown')
  assert.equal(formatFieldValue('tak', 'health.q1.answer'), 'Tak')
  assert.equal(formatFieldValue('opiekun_prawny', 'guardian.relation'), 'Opiekun prawny')
})

test('non-object legacy payload and unanswered health questions retain a fallback', () => {
  assert.equal(registrationSections({ payload: 'legacy' })[0].fields[0].value, 'legacy')
  const sections = registrationSections({ payload: { health: { q1: { answer: null } } } })
  assert.equal(sections[0].questions[0].question, 'Pytanie 1')
  assert.equal(formatFieldValue(sections[0].questions[0].fields[0].value), 'Brak danych')
  const onlyQuestion = registrationSections({ payload: { health: { q1: { question: 'Pytanie?' } } } })
  assert.equal(formatFieldValue(onlyQuestion[0].questions[0].fields[0].value), 'Brak danych')
  assert.equal(registrationSections({ payload: { health: {} } })[0].questions, undefined)
})

test('summary Turnus is not repeated and staff role fields have their own section', () => {
  const data = registration('STAFF', false)
  data.turnusCode = 'T1'
  data.payload.turnusCode = 'T1'
  const sections = registrationSections(data)
  assert.ok(!sections.some(section => section.key === 'basic'))
  assert.ok(!paths(sections).includes('turnusCode'))
  assert.deepEqual(sections.find(section => section.key === 'role').fields.map(field => field.path), ['role', 'subrole'])
  data.payload.turnusCode = 'LEGACY'
  assert.ok(registrationSections(data).find(section => section.key === 'other').fields.some(field => field.value === 'LEGACY'))
})
