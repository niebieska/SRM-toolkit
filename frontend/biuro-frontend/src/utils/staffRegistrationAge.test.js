import test from 'node:test'
import assert from 'node:assert/strict'
import {
  filterStaffRolesByAge,
  filterStaffSubrolesByAge,
  isAdultOnDate,
  isStaffSubroleRequired,
  parseStaffPesel,
} from './staffRegistrationAge.js'

function peselForBirthDate(year, month, day) {
  const encodedMonth = month + (year >= 2000 && year < 2100 ? 20 : 0)
  const prefix = `${String(year % 100).padStart(2, '0')}${String(encodedMonth).padStart(2, '0')}${String(day).padStart(2, '0')}1234`
  const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3]
  const checksum = (10 - prefix.split('').reduce((sum, digit, index) => sum + Number(digit) * weights[index], 0) % 10) % 10
  return `${prefix}${checksum}`
}

test('staff role eligibility uses age on Turnus start date, not age today', () => {
  const pesel = peselForBirthDate(2008, 12, 10)
  const current = new Date(2026, 10, 12)
  const options = [
    { value: 'animator', adultOnly: true },
    { value: 'kandydat', minorOnly: true },
  ]

  assert.equal(parseStaffPesel(pesel, current).isAdult, false)
  assert.equal(isAdultOnDate(pesel, '2027-01-18'), true)
  assert.deepEqual(filterStaffRolesByAge(options, true).map(option => option.value), ['animator'])
  assert.deepEqual(filterStaffRolesByAge(options, false).map(option => option.value), ['kandydat'])
})

test('staff role eligibility handles the 18th birthday boundary and subroles', () => {
  const pesel = peselForBirthDate(2009, 1, 18)
  const roles = [{
    value: 'animator',
    subroles: [
      { value: 'kandydat', minorOnly: true },
      { value: 'wychowawca', adultOnly: true },
    ],
  }]

  assert.equal(isAdultOnDate(pesel, '2027-01-17'), false)
  assert.equal(isAdultOnDate(pesel, '2027-01-18'), true)
  assert.deepEqual(filterStaffRolesByAge(roles, false), roles)
  assert.deepEqual(filterStaffSubrolesByAge(roles[0].subroles, false).map(option => option.value), ['kandydat'])
  assert.deepEqual(filterStaffSubrolesByAge(roles[0].subroles, true).map(option => option.value), ['wychowawca'])
})

test('requires a subrole when the selected base role is not age-eligible', () => {
  const animator = { value: 'animator', adultOnly: true, subroles: [{ value: 'candidate', minorOnly: true }] }

  assert.equal(isStaffSubroleRequired(animator, false), true)
  assert.equal(isStaffSubroleRequired(animator, true), false)
  assert.equal(isStaffSubroleRequired({ value: 'leader', subroleRequired: true }, true), true)
  assert.equal(isStaffSubroleRequired({ value: 'helper' }, false), false)
})

test('invalid PESEL or Turnus date cannot be classified as adult', () => {
  assert.equal(isAdultOnDate('not-a-pesel', '2027-01-18'), false)
  assert.equal(isAdultOnDate(peselForBirthDate(2008, 12, 10), '2027-02-30'), false)
})
