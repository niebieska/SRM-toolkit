import test from 'node:test'
import assert from 'node:assert/strict'
import { isAdultOnDate } from '../../composables/usePesel.js'

function pesel(birthDate) {
  const prefix = `${birthDate}1234`
  const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3]
  const sum = [...prefix].reduce((total, digit, index) => total + Number(digit) * weights[index], 0)
  return `${prefix}${(10 - sum % 10) % 10}`
}

test('adulthood is evaluated on the supplied date, including the 18th birthday', () => {
  const code = pesel('083210') // 10 December 2008

  assert.equal(isAdultOnDate(code, '2026-12-09'), false)
  assert.equal(isAdultOnDate(code, '2026-12-10'), true)
  assert.equal(isAdultOnDate(code, '2027-01-18'), true)
})

test('invalid PESEL date or missing reference date does not confer adult eligibility', () => {
  assert.equal(isAdultOnDate('not-a-pesel', '2027-01-18'), false)
  assert.equal(isAdultOnDate(null, '2027-01-18'), false)
  assert.equal(isAdultOnDate(pesel('082230'), '2027-01-18'), false) // 30 February 2008
  assert.equal(isAdultOnDate(pesel('083210'), null), false)
  assert.equal(isAdultOnDate(pesel('083210'), '2027-02-30'), false)
})
