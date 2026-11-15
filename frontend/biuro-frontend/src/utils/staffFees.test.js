import test from 'node:test'
import assert from 'node:assert/strict'
import { hasStaffFee, staffFeeAmount, staffFeesError, visibleStaffFeeOptions, withStaffFee, withoutStaffFee } from './staffFees.js'

const animator = { role: 'animator', roleLabel: 'Animator', subrole: null, subroleLabel: null }
const candidate = { role: 'animator', roleLabel: 'Animator', subrole: 'candidate', subroleLabel: 'Kandydat' }
const options = [animator, candidate]

test('winter and summer editors show one candidate fee option', () => {
  const winterOptions = [
    { role: 'kucharka', subrole: null },
    { role: 'animator', subrole: null },
    { role: 'animator', subrole: 'kandydat_na_animatora' },
  ]
  assert.deepEqual(visibleStaffFeeOptions(winterOptions, 'WINTER'), [winterOptions[2]])
  assert.deepEqual(visibleStaffFeeOptions(winterOptions, 'WINTER', [{ role: 'kucharka', subrole: null }]),
    [winterOptions[0], winterOptions[2]])
  const summerOptions = [
    { role: 'kucharka', subrole: null },
    { role: 'kandydat_na_animatora', subrole: null },
    { role: 'animator', subrole: 'kandydat_na_animatora' },
    { role: 'animator', subrole: 'animator_wychowawca' },
  ]
  const summerCandidate = summerOptions[2]
  const summerMinorCandidate = summerOptions[1]
  assert.deepEqual(visibleStaffFeeOptions(summerOptions, 'SUMMER'), [summerCandidate])
  assert.deepEqual(visibleStaffFeeOptions(summerOptions, 'SUMMER', [
    { role: 'kandydat_na_animatora', subrole: null, amount: 750 },
  ]), [summerCandidate])
  assert.deepEqual(visibleStaffFeeOptions(summerOptions, 'SUMMER', [
    { role: 'kandydat_na_animatora', subrole: null, amount: 750 },
    { role: 'animator', subrole: 'kandydat_na_animatora', amount: 750 },
  ]), [summerCandidate])
  assert.deepEqual(visibleStaffFeeOptions(summerOptions, 'SUMMER', [
    { role: 'kandydat_na_animatora', subrole: null, amount: 750 },
    { role: 'animator', subrole: 'kandydat_na_animatora', amount: 800 },
  ]), [summerMinorCandidate, summerCandidate])

  const selected = withStaffFee([], summerCandidate, '750')
  assert.equal(hasStaffFee(selected, summerMinorCandidate), true)
  assert.equal(staffFeeAmount(selected, summerMinorCandidate), '750')
  assert.deepEqual(withStaffFee(selected, summerCandidate, '800'), [
    { role: 'animator', subrole: 'kandydat_na_animatora', amount: '800' },
  ])
})

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
