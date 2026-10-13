import test from 'node:test'
import assert from 'node:assert/strict'
import { requirementCount, withRequirement, requirementsError } from './staffRequirements.js'
const categories = [{ code: 'animator' }, { code: 'kucharka' }]
test('requirements support adding, editing and removing zero without changing other categories', () => {
  const source = [{ categoryCode: 'kucharka', requiredCount: 2 }]
  const added = withRequirement(source, 'animator', '6')
  assert.equal(requirementCount(added, 'animator'), 6)
  assert.equal(requirementCount(source, 'animator'), 0)
  assert.equal(requirementCount(withRequirement(added, 'animator', '4'), 'animator'), 4)
  assert.deepEqual(withRequirement(added, 'animator', '0'), source)
  assert.equal(requirementsError(added, categories), '')
})
test('invalid inputs and categories are rejected rather than silently discarded', () => {
  for (const value of ['', '-1', '1.5', '2147483648'])
    assert.ok(requirementsError(withRequirement([], 'animator', value), categories))
  assert.ok(requirementsError([{ categoryCode: 'sternik', requiredCount: 1 }], categories))
  assert.ok(requirementsError([{ categoryCode: 'animator', requiredCount: 1 }, { categoryCode: 'animator', requiredCount: 2 }], categories))
  assert.ok(requirementsError([{ categoryCode: 'animator', requiredCount: 2 }], []))
  assert.equal(requirementsError([], []), '')
})
