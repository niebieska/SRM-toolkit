import test from 'node:test'
import assert from 'node:assert/strict'
import { defaultWinterSeasonYear } from './turnusDefaults.js'

test('winter season year follows the next January/February turnus date', () => {
  assert.equal(defaultWinterSeasonYear(new Date(2026, 10, 12)), 2027)
  assert.equal(defaultWinterSeasonYear(new Date(2027, 0, 1)), 2027)
  assert.equal(defaultWinterSeasonYear(new Date(2027, 1, 28)), 2027)
  assert.equal(defaultWinterSeasonYear(new Date(2027, 2, 1)), 2028)
})
