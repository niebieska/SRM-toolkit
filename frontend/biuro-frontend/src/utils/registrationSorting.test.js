import test from 'node:test'
import assert from 'node:assert/strict'
import { sortRegistrations } from './registrationSorting.js'

const codes = (rows) => rows.map(row => row.registrationCode)

test('code suffixes use numeric order in both directions without modifying the source', () => {
  const rows = [10, 2, 1].map(n => ({ registrationCode: `REG-P-T1-${n}` }))
  assert.deepEqual(codes(sortRegistrations(rows, 'registrationCode')), ['REG-P-T1-1', 'REG-P-T1-2', 'REG-P-T1-10'])
  assert.deepEqual(codes(sortRegistrations(rows, 'registrationCode', 'desc')), ['REG-P-T1-10', 'REG-P-T1-2', 'REG-P-T1-1'])
  assert.deepEqual(codes(rows), ['REG-P-T1-10', 'REG-P-T1-2', 'REG-P-T1-1'])
})

test('submission ordering uses actual timestamps, including seconds and time zone offsets', () => {
  const rows = [
    { registrationCode: 'late', createdAt: '2026-10-07T09:00:30+02:00' },
    { registrationCode: 'early', createdAt: '2026-10-07T06:59:59Z' },
    { registrationCode: 'middle', createdAt: '2026-10-07T09:00:00+02:00' },
  ]
  assert.deepEqual(codes(sortRegistrations(rows, 'createdAt')), ['early', 'middle', 'late'])
  assert.deepEqual(codes(sortRegistrations(rows, 'createdAt', 'desc')), ['late', 'middle', 'early'])
})

test('missing and invalid submission dates stay last in either direction', () => {
  const rows = [
    { registrationCode: 'unknown', createdAt: null },
    { registrationCode: 'invalid', createdAt: 'invalid-date' },
    { registrationCode: 'known', createdAt: '2026-10-07T09:00:00' },
  ]
  for (const direction of ['asc', 'desc']) {
    assert.equal(sortRegistrations(rows, 'createdAt', direction)[0].registrationCode, 'known')
  }
})

test('ages sort numerically, preserve zero, and put missing ages last', () => {
  const rows = [
    { registrationCode: 'unknown', age: null }, { registrationCode: 'adult', age: 20 },
    { registrationCode: 'child', age: 9 }, { registrationCode: 'baby', age: 0 },
  ]
  assert.deepEqual(codes(sortRegistrations(rows, 'age')), ['baby', 'child', 'adult', 'unknown'])
  assert.deepEqual(codes(sortRegistrations(rows, 'age', 'desc')), ['adult', 'child', 'baby', 'unknown'])
})

test('names use Polish alphabet ordering and tolerate missing parts', () => {
  const rows = [
    { registrationCode: 'l', firstName: 'Łukasz', lastName: 'Nowak' },
    { registrationCode: 'z', firstName: 'Zofia' },
    { registrationCode: 'a', firstName: 'Anna', lastName: 'Kowalska' },
    { registrationCode: 'unknown' },
  ]
  assert.deepEqual(codes(sortRegistrations(rows, 'name')), ['a', 'l', 'z', 'unknown'])
})

test('type and status sort by their displayed Polish labels', () => {
  const rows = [
    { registrationCode: 'p', registrationType: 'PARTICIPANT', status: 'ACCEPTED' },
    { registrationCode: 's', registrationType: 'STAFF', status: 'WAITLIST' },
  ]
  assert.deepEqual(codes(sortRegistrations(rows, 'registrationType')), ['s', 'p'])
  assert.deepEqual(codes(sortRegistrations(rows, 'status')), ['s', 'p'])
})

test('equal dates have deterministic numeric code order and filtered subsets can be sorted', () => {
  const rows = [10, 2, 1].map(n => ({
    registrationCode: `REG-P-T1-${n}`, createdAt: '2026-10-07T09:00:00',
  }))
  assert.deepEqual(codes(sortRegistrations(rows, 'createdAt')), ['REG-P-T1-1', 'REG-P-T1-2', 'REG-P-T1-10'])
  assert.deepEqual(codes(sortRegistrations(rows.filter(r => r.registrationCode !== 'REG-P-T1-1'), 'registrationCode')),
    ['REG-P-T1-2', 'REG-P-T1-10'])
})
