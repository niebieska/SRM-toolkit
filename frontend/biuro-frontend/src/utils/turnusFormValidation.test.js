import test from 'node:test'
import assert from 'node:assert/strict'
import { validateTurnusForm } from './turnusFormValidation.js'
const form = { turnusCode: 'NARTY2027T1', turnusName: 'Narty z Jezusem', turnusLocation: 'Gliczarów Górny',
  seasonType: 'WINTER', seasonYear: 2027, minAge: 14, participantCapacity: 50, staffCapacity: 10,
  startDate: '2027-01-17', endDate: '2027-01-24', turnusDescription: null, staffRequirements: [] }
test('staffing requirements may equal but cannot exceed staff capacity', () => {
  assert.deepEqual(validateTurnusForm(form, []), {})
  const categories = [{ code: 'animator' }, { code: 'kucharka' }]
  assert.deepEqual(validateTurnusForm({ ...form, endDate: form.startDate, minAge: 0,
    staffRequirements: [{ categoryCode: 'animator', requiredCount: 6 }, { categoryCode: 'kucharka', requiredCount: 4 }] }, categories), {})
  assert.match(validateTurnusForm({ ...form,
    staffRequirements: [{ categoryCode: 'animator', requiredCount: 6 }, { categoryCode: 'kucharka', requiredCount: 5 }] }, categories).staffRequirements,
  /Suma ról przekracza liczbę miejsc dla kadry \(11\/10\)/)
  assert.deepEqual(validateTurnusForm({ ...form, staffCapacity: 11,
    staffRequirements: [{ categoryCode: 'animator', requiredCount: 6 }, { categoryCode: 'kucharka', requiredCount: 5 }] }, categories), {})
})
test('required fields, codes, date range and impossible dates have specific errors', () => {
  const errors = validateTurnusForm({ ...form, turnusName: ' ', turnusCode: 'bad code',
    turnusLocation: '', endDate: '2027-01-16' }, [])
  for (const key of ['turnusName', 'turnusCode', 'turnusLocation', 'endDate']) assert.ok(errors[key])
  assert.ok(validateTurnusForm({ ...form, startDate: '2027-02-29' }, []).startDate)
  assert.ok(validateTurnusForm({ ...form, endDate: '' }, []).endDate)
})
test('exact backend numeric and text bounds are respected', () => {
  for (const key of ['participantCapacity', 'staffCapacity']) {
    for (const value of [0, -1, 1.5, '', 2147483648]) assert.ok(validateTurnusForm({ ...form, [key]: value }, [])[key])
    assert.equal(validateTurnusForm({ ...form, [key]: 2147483647 }, [])[key], undefined)
  }
  for (const [key, max] of [['turnusName', 200], ['turnusLocation', 500], ['turnusDescription', 4000]]) {
    assert.equal(validateTurnusForm({ ...form, [key]: 'x'.repeat(max) }, [])[key], undefined)
    assert.ok(validateTurnusForm({ ...form, [key]: 'x'.repeat(max + 1) }, [])[key])
  }
  assert.ok(validateTurnusForm({ ...form, minAge: 121 }, []).minAge)
  assert.ok(validateTurnusForm({ ...form, seasonYear: 1999 }, []).seasonYear)
})
test('participant and staff fees follow monetary bounds and seasonal role selections', () => {
  const options = [{ role: 'kucharka', roleLabel: 'Kucharka', subrole: null, subroleLabel: null }]
  assert.deepEqual(validateTurnusForm({ ...form, participantFee: 1200.5,
    staffFees: [{ role: 'kucharka', subrole: null, amount: 300 }] }, [], options), {})
  for (const value of [-1, 100000000, 10.001, 'invalid'])
    assert.ok(validateTurnusForm({ ...form, participantFee: value }, [], options).participantFee)
  assert.ok(validateTurnusForm({ ...form, staffFees: [{ role: 'unknown', amount: 200 }] }, [], options).staffFees)
})
