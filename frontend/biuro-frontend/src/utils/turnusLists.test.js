import test from 'node:test'
import assert from 'node:assert/strict'
import { columnsForList, customColumnGroups, filterAndSortList, listValue, validateTurnusLists } from './turnusLists.js'

const row = (code, type, firstName, lastName, age, role = null) => ({
  person: { registrationCode: code, registrationType: type, firstName, lastName, gender: 'female', role, minor: false },
  age, roleLabel: role, subroleLabel: null,
})
const rows = [row('1', 'PARTICIPANT', 'Żaneta', 'Nowak', 9), row('2', 'PARTICIPANT', 'Anna', 'Adamska', 17),
  row('3', 'STAFF', 'Jan', 'Kowalski', 22, 'animator'), row('4', 'STAFF', 'Piotr', 'Nowak', 31, 'kierowca'),
  row('5', 'PARTICIPANT', 'Jan', 'Nowak', null)]

test('keeps participants and staff separate; role filter applies only to staff', () => {
  assert.equal(filterAndSortList(rows, { type: 'PARTICIPANT', role: 'animator' }).length, 3)
  assert.deepEqual(filterAndSortList(rows, { type: 'STAFF', role: 'animator' }).map(r => r.person.registrationCode), ['3'])
})
test('searches names in both orders, case insensitively, without changing source data', () => {
  assert.equal(filterAndSortList(rows, { type: 'PARTICIPANT', search: '  NOWAK żaneta ' }).length, 1)
  assert.equal(filterAndSortList(rows, { type: 'PARTICIPANT', search: 'Żaneta Nowak' }).length, 1)
  assert.equal(filterAndSortList(rows, { type: 'STAFF', search: 'missing' }).length, 0)
  assert.equal(rows[0].person.registrationCode, '1')
})
test('sorts age numerically and keeps unknown ages last in both directions', () => {
  assert.deepEqual(filterAndSortList(rows, { type: 'PARTICIPANT', sortKey: 'age' }).map(r => r.age), [9, 17, null])
  assert.deepEqual(filterAndSortList(rows, { type: 'PARTICIPANT', sortKey: 'age', direction: 'desc' }).map(r => r.age), [17, 9, null])
  assert.equal(filterAndSortList(rows, { type: 'PARTICIPANT' })[0].person.lastName, 'Adamska')
})
test('formats role/function and gender without manufacturing missing values', () => {
  assert.equal(listValue({ ...rows[2], roleLabel: 'Ksiądz', subroleLabel: 'Sternik' }, 'roleLabel'), 'Ksiądz — Sternik')
  assert.equal(listValue(rows[0], 'gender'), 'Kobieta')
  assert.equal(listValue(rows[0], 'guardianPhone'), undefined)
})
test('custom columns expose health details and staff-only family and role fields', () => {
  const participant = { ...rows[0], person: { ...rows[0].person, additionalData: {
    healthDetails: { chronicConditionDetails: 'Astma', dietaryDetails: 'Orzechy', specialNeedsDetails: 'Spokojne miejsce' },
  } } }
  assert.equal(listValue(participant, 'chronicConditionDetails'), 'Astma')
  assert.equal(listValue(participant, 'dietaryDetails'), 'Orzechy')
  assert.equal(listValue(participant, 'specialNeedsAndAdditionalInfo'), 'Spokojne miejsce')

  const staff = { ...rows[2], person: { ...rows[2].person, birthDate: '2000-01-01', additionalData: {
    staffDetails: { motherName: 'Maria', fatherName: 'Jan', birthSurname: 'Kowalska', organizationalInfo: 'Późniejszy przyjazd' },
  } } }
  assert.equal(listValue(staff, 'motherName'), 'Maria')
  assert.equal(listValue(staff, 'fatherName'), 'Jan')
  assert.equal(listValue(staff, 'birthSurname'), 'Kowalska')
  assert.equal(listValue(staff, 'birthYear'), '2000')
  assert.equal(listValue(staff, 'specialNeedsAndAdditionalInfo'), 'Późniejszy przyjazd')
  assert.ok(customColumnGroups.find(group => group.key === 'staff').columns.some(column => column.key === 'roleLabel'))
  assert.equal(listValue({ ...rows[0], person: { ...rows[0].person, additionalData: null } }, 'dietaryDetails'), null)
})
test('validates selected Turnus, empty lists, group values and age fields', () => {
  const body = { turnus: { turnusCode: 'T1' }, entries: rows }
  assert.equal(validateTurnusLists(body, 'T1'), true)
  assert.equal(validateTurnusLists({ ...body, entries: [] }, 'T1'), true)
  assert.equal(validateTurnusLists(body, 'T2'), false)
  assert.equal(validateTurnusLists({ ...body, entries: [null] }, 'T1'), false)
  assert.equal(validateTurnusLists({ ...body, entries: [{ ...rows[0], age: -1 }] }, 'T1'), false)
  assert.equal(validateTurnusLists({ ...body, entries: [{ ...rows[0], person: { ...rows[0].person, minor: undefined } }] }, 'T1'), false)
})

test('contact number uses stored minor status, independently of displayed age', () => {
  const entry = { ...rows[0], age: 18, person: { ...rows[0].person, minor: true, guardianPhone: '111222333', icePhone: '444555666' } }
  assert.equal(listValue(entry, 'contactPhone'), '111222333')
  entry.person.minor = false
  assert.equal(listValue(entry, 'contactPhone'), '444555666')
  entry.person.icePhone = null
  assert.equal(listValue(entry, 'contactPhone'), null) // Never silently use the other contact.
  entry.person.minor = true
  entry.person.guardianPhone = null
  entry.person.icePhone = '444555666'
  assert.equal(listValue(entry, 'contactPhone'), null)
})

test('formats complete and partial addresses without undefined or invented values', () => {
  const entry = { ...rows[0], person: { ...rows[0].person, address: { street: 'Polna', houseNumber: '2', postalCode: '00-001', city: 'Warszawa' } } }
  assert.equal(listValue(entry, 'address'), 'Polna 2, 00-001 Warszawa')
  entry.person.address = { city: 'Warszawa' }
  assert.equal(listValue(entry, 'address'), 'Warszawa')
  entry.person.address = {}
  assert.equal(listValue(entry, 'address'), null)
  assert.equal(listValue(rows[0], 'address'), null)
})

test('presets select agreed fields and keep staff role/function in the staff section', () => {
  const keys = (view, type = 'PARTICIPANT') => columnsForList(view, [], type).map(column => column.key)
  assert.deepEqual(keys('insurance'), ['firstName', 'lastName', 'pesel'])
  assert.deepEqual(keys('insurance', 'STAFF'), ['firstName', 'lastName', 'pesel'])
  assert.deepEqual(keys('onsite'), ['firstName', 'lastName', 'address', 'age', 'personPhone', 'contactPhone'])
  assert.deepEqual(keys('onsite', 'STAFF'), ['firstName', 'lastName', 'roleLabel', 'personPhone', 'contactPhone'])
  assert.ok(!keys('general').includes('gender'))
  for (const view of ['general', 'onsite']) {
    assert.ok(keys(view, 'STAFF').includes('roleLabel'))
    assert.ok(!keys(view).includes('roleLabel'))
  }
})

test('custom columns use known fields once and empty selection is explicit', () => {
  const columns = columnsForList('custom', ['pesel', 'pesel', 'unknown', 'gender'], 'STAFF')
  assert.deepEqual(columns.map(column => column.key), ['pesel', 'gender'])
  assert.deepEqual(columnsForList('custom', ['roleLabel'], 'STAFF').map(column => column.key), ['roleLabel'])
  assert.deepEqual(columnsForList('custom', [], 'STAFF'), [])
  assert.deepEqual(columnsForList('custom', [], 'PARTICIPANT'), [])
})

test('birth dates display in Polish format but sort chronologically with unknown dates last', () => {
  const values = ['2010-12-01', '2009-01-31', null].map((birthDate, index) => ({
    ...rows[0], person: { ...rows[0].person, registrationCode: String(index), birthDate },
  }))
  assert.equal(listValue(values[0], 'birthDate'), '01.12.2010')
  assert.equal(listValue(values[2], 'birthDate'), null)
  assert.deepEqual(filterAndSortList(values, { type: 'PARTICIPANT', sortKey: 'birthDate' })
    .map(entry => entry.person.birthDate), ['2009-01-31', '2010-12-01', null])
  assert.deepEqual(filterAndSortList(values, { type: 'PARTICIPANT', sortKey: 'birthDate', direction: 'desc' })
    .map(entry => entry.person.birthDate), ['2010-12-01', '2009-01-31', null])
})

test('additional optional columns format contact names relationships city qualifications and image consent', () => {
  const entry = { ...rows[0], person: { ...rows[0].person, address: { city: 'Warszawa' }, additionalData: {
    email: 'person@example.org', contactFirstName: 'Jan', contactLastName: 'Nowak',
    guardianEmail: 'guardian@example.org', contactRelation: 'inna', contactRelationOther: 'Brat', imageConsent: false,
  } }, qualificationLabels: ['Patent żeglarski', 'Inne: Instruktor'] }
  assert.equal(listValue(entry, 'email'), 'person@example.org')
  assert.equal(listValue(entry, 'guardianEmail'), 'guardian@example.org')
  assert.equal(listValue(entry, 'contactName'), 'Jan Nowak')
  assert.equal(listValue(entry, 'contactRelation'), 'Inna: Brat')
  assert.equal(listValue(entry, 'city'), 'Warszawa')
  assert.equal(listValue(entry, 'qualifications'), 'Patent żeglarski; Inne: Instruktor')
  assert.equal(listValue(entry, 'imageConsent'), 'Nie')
  entry.person.additionalData.imageConsent = true
  assert.equal(listValue(entry, 'imageConsent'), 'Tak')
  for (const key of ['email', 'guardianEmail', 'contactName', 'contactRelation', 'city', 'qualifications', 'imageConsent']) {
    assert.equal(listValue(rows[0], key), null)
  }
})

test('additional fields are optional in custom view and do not expand general or other presets', () => {
  const extras = ['email', 'contactName', 'guardianEmail', 'contactRelation', 'city', 'qualifications', 'imageConsent']
  for (const view of ['general', 'insurance', 'onsite']) {
    assert.ok(columnsForList(view, [], 'PARTICIPANT').every(column => !extras.includes(column.key)))
  }
  assert.deepEqual(columnsForList('custom', extras, 'PARTICIPANT').map(column => column.key), extras.filter(key => key !== 'qualifications'))
  assert.ok(columnsForList('custom', extras, 'STAFF').some(column => column.key === 'qualifications'))
  assert.deepEqual(columnsForList('custom', ['qualifications'], 'PARTICIPANT'), [])
})
