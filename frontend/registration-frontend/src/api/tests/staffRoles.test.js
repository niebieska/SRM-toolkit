import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveCertificateSource } from '../../config/staffRoles.js'

test('priest functions use dedicated certificate lists before generic functions', () => {
  assert.equal(resolveCertificateSource('ksiadz', 'sternik'), 'ksiadz_sternik')
  assert.equal(resolveCertificateSource('ksiadz', 'prowadzacy'), 'ksiadz_prowadzacy')
  assert.equal(resolveCertificateSource('ksiadz', 'pomoc'), 'ksiadz')
  assert.equal(resolveCertificateSource('sternik', ''), 'sternik')
  assert.equal(resolveCertificateSource('animator', 'animator_wychowawca'), 'animator')
})
