import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const modalSource = await readFile(new URL('../components/ManualStaffRegistrationModal.vue', import.meta.url), 'utf8')

test('manual staff form uses a native modal dialog and routes Escape through discard confirmation', () => {
  assert.match(modalSource, /<dialog[^>]*ref="dialogElement"[^>]*aria-modal="true"/)
  assert.match(modalSource, /@cancel\.prevent="requestClose"/)
  assert.match(modalSource, /dialogElement\.value\?\.showModal\(\)/)
})

test('manual staff form closes its native dialog and restores the opener focus', () => {
  assert.match(modalSource, /dialogElement\.value\.close\(\)/)
  assert.match(modalSource, /previouslyFocusedElement\?\.isConnected\) previouslyFocusedElement\.focus\(\)/)
})
