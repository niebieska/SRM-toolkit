import { test } from 'node:test'
import assert from 'node:assert/strict'
import { sortTurnuses } from './turnusSorting.js'
const codes = rows => rows.map(row => row.turnusCode)

test('participantCapacity sorts numerically in both directions without mutating catalogue', () => {
  const rows = [{ turnusCode: 'T1', participantCapacity: 60 }, { turnusCode: 'T2', participantCapacity: 8 }, { turnusCode: 'T3', participantCapacity: 100 }]
  assert.deepEqual(codes(sortTurnuses(rows, 'participantCapacity')), ['T2', 'T1', 'T3'])
  assert.deepEqual(codes(sortTurnuses(rows, 'participantCapacity', 'desc')), ['T3', 'T1', 'T2'])
  assert.deepEqual(codes(rows), ['T1', 'T2', 'T3'])
})
test('dates sort chronologically across years, with absent dates last', () => {
  const rows = [{ turnusCode: 'T1', startDate: '2027-01-01' }, { turnusCode: 'T2', startDate: '2026-12-31' }, { turnusCode: 'T3' }]
  assert.deepEqual(codes(sortTurnuses(rows)), ['T2', 'T1', 'T3'])
  assert.deepEqual(codes(sortTurnuses(rows, 'startDate', 'desc')), ['T1', 'T2', 'T3'])
})
test('names use Polish natural ordering and equal values use code as a tie breaker', () => {
  const rows = [{ turnusCode: 'T3', turnusName: 'Żagle 10' }, { turnusCode: 'T2', turnusName: 'Żagle 2' }, { turnusCode: 'T1', turnusName: 'Żagle 2' }]
  assert.deepEqual(codes(sortTurnuses(rows, 'turnusName')), ['T1', 'T2', 'T3'])
})
test('availability sorts by the displayed labels including inactive open records', () => {
  const rows = [{ turnusCode: 'CLOSED', active: true, participantRegistrationOpen: false }, { turnusCode: 'OPEN', active: true, participantRegistrationOpen: true }, { turnusCode: 'ARCHIVED', active: false, participantRegistrationOpen: true }]
  assert.deepEqual(codes(sortTurnuses(rows, 'availability')), ['ARCHIVED', 'OPEN', 'CLOSED'])
})


test('staff capacity sorts independently and staff-only registration is open', () => {
  const rows = [
    { turnusCode: 'A', active: true, participantRegistrationOpen: false, staffRegistrationOpen: false, staffCapacity: 20 },
    { turnusCode: 'B', active: true, participantRegistrationOpen: false, staffRegistrationOpen: true, staffCapacity: 3 },
  ]
  assert.deepEqual(codes(sortTurnuses(rows, 'staffCapacity')), ['B', 'A'])
  assert.deepEqual(codes(sortTurnuses(rows, 'availability')), ['B', 'A'])
})
