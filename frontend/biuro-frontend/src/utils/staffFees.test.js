import test from 'node:test'
import assert from 'node:assert/strict'
import { hasStaffFee, staffFeeAmount, staffFeesError, withStaffFee, withoutStaffFee } from './staffFees.js'

const animator = { role: 'animator', roleLabel: 'Animator', subrole: null, subroleLabel: null }
const candidate = { role: 'animator', roleLabel: 'Animator', subrole: 'candidate', subroleLabel: 'Kandydat' }
const options = [animator, candidate]

test('staff fee selection helpers add, update and remove exact role/subrole fees', () => {
  const selected = withStaffFee([], animator, '250')
  assert.equal(hasStaffFee(selected, animator), true)
  assert.equal(staffFeeAmount(selected, animator), '250')
  const updated = withStaffFee(selected, animator, '300')
  assert.equal(updated.length, 1)
  assert.equal(staffFeeAmount(updated, animator), '300')
  assert.deepEqual(withoutStaffFee(updated, animator), [])
})

test('fee validation accepts valid individual seasonal options', () => {
  assert.equal(staffFeesError([{ role: 'animator', subrole: null, amount: '250.50' }], options), '')
  assert.equal(staffFeesError([{ role: 'animator', subrole: 'candidate', amount: '125' }], options), '')
})

test('fee validation rejects invalid amounts, duplicate entries and overlapping base/subrole fees', () => {
  for (const amount of ['', 0, -1, 100000000, 10.001])
    assert.ok(staffFeesError([{ role: 'animator', subrole: null, amount }], options))
  assert.match(staffFeesError([
    { role: 'animator', subrole: null, amount: 250 },
    { role: 'animator', subrole: null, amount: 250 },
  ], options), /powtarza/)
  assert.match(staffFeesError([
    { role: 'animator', subrole: null, amount: 250 },
    { role: 'animator', subrole: 'candidate', amount: 125 },
  ], options), /nie mogą się nakładać/)
})

test('fee validation rejects role/subrole pairs outside the selected season catalogue', () => {
  assert.match(staffFeesError([{ role: 'instructor', subrole: 'ski', amount: 250 }], options), /spoza sezonowego katalogu/)
})
