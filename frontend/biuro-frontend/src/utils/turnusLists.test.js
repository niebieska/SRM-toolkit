import test from 'node:test'
import assert from 'node:assert/strict'
import { filterAndSortList, listValue, validateTurnusLists } from './turnusLists.js'

const row = (code, type, firstName, lastName, age, role = null) => ({
  person: { registrationCode: code, registrationType: type, firstName, lastName, gender: 'female', role },
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
test('validates selected Turnus, empty lists, group values and age fields', () => {
  const body = { turnus: { turnusCode: 'T1' }, entries: rows }
  assert.equal(validateTurnusLists(body, 'T1'), true)
  assert.equal(validateTurnusLists({ ...body, entries: [] }, 'T1'), true)
  assert.equal(validateTurnusLists(body, 'T2'), false)
  assert.equal(validateTurnusLists({ ...body, entries: [null] }, 'T1'), false)
  assert.equal(validateTurnusLists({ ...body, entries: [{ ...rows[0], age: -1 }] }, 'T1'), false)
})
