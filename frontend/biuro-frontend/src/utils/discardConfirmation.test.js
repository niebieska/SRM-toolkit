import test from 'node:test'
import assert from 'node:assert/strict'
import { createDiscardConfirmation } from './discardConfirmation.js'

test('stay and discard resolve the pending decision and clear the dialog', async () => {
  const dialog = createDiscardConfirmation()
  const stay = dialog.confirm()
  assert.equal(dialog.prompt.value.kind, 'leave')
  dialog.decide(false)
  assert.equal(await stay, false)
  assert.equal(dialog.prompt.value, null)
  const discard = dialog.confirm('logout')
  assert.equal(dialog.prompt.value.kind, 'logout')
  dialog.decide(true)
  assert.equal(await discard, true)
  assert.equal(dialog.prompt.value, null)
})
test('new requests cancel the old decision; teardown can cancel the pending one', async () => {
  const dialog = createDiscardConfirmation()
  const old = dialog.confirm('leave')
  const current = dialog.confirm('reload')
  assert.equal(await old, false)
  assert.equal(dialog.prompt.value.kind, 'reload')
  dialog.decide(false)
  assert.equal(await current, false)
})
