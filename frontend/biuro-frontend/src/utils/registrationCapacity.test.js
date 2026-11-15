import test from 'node:test'
import assert from 'node:assert/strict'
import { canAcceptRegistration, isRegistrationCapacityFull } from './registrationCapacity.js'

test('capacity is full when accepted registrations reach the limit', () => {
  assert.equal(isRegistrationCapacityFull({ accepted: 3, capacity: 3 }), true)
  assert.equal(isRegistrationCapacityFull({ accepted: 4, capacity: 3 }), true)
})

test('capacity is not full while places remain', () => {
  assert.equal(isRegistrationCapacityFull({ accepted: 2, capacity: 3 }), false)
})

test('missing or malformed statistics do not hide acceptance', () => {
  assert.equal(isRegistrationCapacityFull(null), false)
  assert.equal(isRegistrationCapacityFull({ accepted: undefined, capacity: 3 }), false)
})

test('zero capacity has no available places', () => {
  assert.equal(isRegistrationCapacityFull({ accepted: 0, capacity: 0 }), true)
})

test('acceptance is available without capacity statistics unless already accepted', () => {
  assert.equal(canAcceptRegistration({ status: 'NEW' }, null), true)
  assert.equal(canAcceptRegistration({ status: 'ACCEPTED' }, null), false)
  assert.equal(canAcceptRegistration(null, null), false)
})

test('acceptance is hidden at capacity and available below capacity', () => {
  assert.equal(canAcceptRegistration({ status: 'NEW' }, { accepted: 3, capacity: 3 }), false)
  assert.equal(canAcceptRegistration({ status: 'WAITLIST' }, { accepted: 2, capacity: 3 }), true)
})
