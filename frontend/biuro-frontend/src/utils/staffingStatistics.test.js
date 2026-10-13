import test from 'node:test'
import assert from 'node:assert/strict'
import { visibleStaffingRoles, validStaffingStatistics } from './staffingStatistics.js'
const role = { categoryCode: 'animator', label: 'Animator', accepted: 0, required: 6,
  subroles: [{ code: 'kandydat_na_animatora', label: 'Kandydat', accepted: 0 }] }
test('shows configured roles for All/Staff including zero accepted, hiding Participant and zero targets', () => {
  const stats = { staffRoles: [role, { ...role, categoryCode: 'kucharka', required: 0 }] }
  assert.deepEqual(visibleStaffingRoles(stats, ''), [role])
  assert.deepEqual(visibleStaffingRoles(stats, 'STAFF'), [role])
  assert.deepEqual(visibleStaffingRoles(stats, 'PARTICIPANT'), [])
  assert.deepEqual(visibleStaffingRoles(null, ''), [])
  assert.deepEqual(visibleStaffingRoles({ staffRoles: [] }, ''), [])
})
test('accepts over-target counts and subroles, rejecting incomplete and negative data', () => {
  const stats = { staffRoles: [{ ...role, accepted: 8 }], unmappedAcceptedStaff: 1 }
  assert.equal(validStaffingStatistics(stats), true)
  assert.equal(validStaffingStatistics({ staffRoles: [], unmappedAcceptedStaff: 0 }), true)
  assert.equal(validStaffingStatistics({ ...stats, staffRoles: [{ ...role, subroles: null }] }), false)
  assert.equal(validStaffingStatistics({ ...stats, unmappedAcceptedStaff: -1 }), false)
})
