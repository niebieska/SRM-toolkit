import test from 'node:test'
import assert from 'node:assert/strict'
import { nextTurnusCacheRefreshTime } from './turnusCacheSchedule.js'

test('returns the next full hour in Warsaw local time', () => {
  assert.equal(nextTurnusCacheRefreshTime(new Date('2026-11-09T10:15:30Z')), '12:00')
  assert.equal(nextTurnusCacheRefreshTime(new Date('2026-11-09T11:00:00Z')), '13:00')
})

test('handles Warsaw daylight-saving transitions', () => {
  assert.equal(nextTurnusCacheRefreshTime(new Date('2026-03-29T00:30:00Z')), '03:00')
  assert.equal(nextTurnusCacheRefreshTime(new Date('2026-10-25T00:30:00Z')), '02:00')
})
