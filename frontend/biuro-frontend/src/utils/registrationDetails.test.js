import test from 'node:test'
import assert from 'node:assert/strict'
import { registrationSections, formatFieldValue } from './registrationDetails.js'

const registration = (registrationType, minor) => ({ registrationType, minor, age: 25,
  payload: { person: { firstName: 'Anna', isAdult: !minor }, address: { city: 'Łódź' },
    guardian: { firstName: 'Maria' }, ice: { firstName: 'Adam' },
    role: 'animator', subrole: 'kandydat_na_animatora', certificates: { kpp: false },
    certificateDetails: { inne: 'Kurs' }, consents: { imageUsage: false },
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
