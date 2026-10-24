import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { availableRoles, availableFunctions, selectionError, resolveCertificateSource, certificatesFor } from '../../config/staffRoles.js'
const load = season => JSON.parse(fs.readFileSync(new URL(`../../../../../backend/biuro-api/src/main/resources/staff-seasons/${season}.json`, import.meta.url)))
const summer = load('summer'), winter = load('winter')

test('Summer preserves legacy role choices and dedicated certificate precedence', () => {
  assert.deepEqual(availableRoles(summer, false).map(r => r.value), ['kandydat_na_animatora', 'sternik_z_opiekunem'])
  assert.equal(resolveCertificateSource(summer, 'ksiadz', 'sternik'), 'ksiadz_sternik')
  assert.equal(resolveCertificateSource(summer, 'ksiadz', 'prowadzacy'), 'ksiadz_prowadzacy')
  assert.equal(resolveCertificateSource(summer, 'ksiadz', 'pomoc'), 'ksiadz')
  assert.equal(resolveCertificateSource(summer, 'animator', 'animator_wychowawca'), 'animator')
  assert.ok(selectionError(summer, 'animator', '', true))
  assert.equal(selectionError(summer, 'animator', 'animator_wychowawca', true), '')
})

test('Winter minors can select only Animator candidate; adults can also select it', () => {
  assert.deepEqual(availableRoles(winter, false).map(r => r.value), ['animator'])
  const animator = winter.roles.find(r => r.value === 'animator')
  assert.deepEqual(availableFunctions(animator, false).map(f => f.value), ['kandydat_na_animatora'])
  for (const adult of [false, true]) assert.equal(selectionError(winter, 'animator', 'kandydat_na_animatora', adult), '')
  assert.ok(selectionError(winter, 'animator', '', false))
  assert.equal(selectionError(winter, 'animator', '', true), '')
  assert.ok(selectionError(winter, 'instruktor', 'narty', false))
})

test('Winter instructor requires one discipline and priest permits at most one function', () => {
  assert.ok(selectionError(winter, 'instruktor', '', true))
  assert.equal(selectionError(winter, 'instruktor', 'narty', true), '')
  assert.equal(selectionError(winter, 'instruktor', 'snowboard', true), '')
  assert.ok(selectionError(winter, 'instruktor', 'narty,snowboard', true))
  assert.equal(selectionError(winter, 'ksiadz', '', true), '')
  for (const f of ['prowadzacy', 'pomoc', 'instruktor_narty', 'instruktor_snowboard']) {
    assert.equal(selectionError(winter, 'ksiadz', f, true), '')
  }
  assert.ok(selectionError(winter, 'ksiadz', 'sternik', true))
})

test('changing season invalidates unavailable roles/functions and certificates follow the new catalogue', () => {
  assert.ok(selectionError(winter, 'sternik', '', true))
  assert.ok(selectionError(winter, 'animator', 'animator_wychowawca', true))
  assert.deepEqual(certificatesFor(winter, 'instruktor', 'narty').map(c => c.id), [
    'wychowawca_wypoczynku', 'kpp', 'prawo_jazdy_b', 'instruktor_narciarstwa', 'inne',
  ])
  const changed = structuredClone(winter)
  changed.certificatesByRole.instruktor_narty.push({id:'narty_test',label:'Added in configuration',hasDetails:false})
  assert.equal(certificatesFor(changed, 'instruktor', 'narty').length, 6)
})

test('Winter offers role-appropriate Summer certificates and one discipline certificate', () => {
  const ids = (role, subrole = '') => certificatesFor(winter, role, subrole).map(certificate => certificate.id)

  assert.deepEqual(ids('kucharka'), ['ksiazeczka_sanepid', 'inne'])
  assert.deepEqual(ids('kierowca'), ['prawo_jazdy_b', 'inne'])
  assert.deepEqual(ids('animator'), ['wychowawca_wypoczynku', 'kpp', 'prawo_jazdy_b', 'inne'])
  assert.deepEqual(ids('animator', 'kandydat_na_animatora'), ['kurs_wychowawcy_w_trakcie', 'inne'])
  assert.deepEqual(ids('ksiadz', 'prowadzacy'), ['kierownik_wypoczynku', 'inne'])

  assert.ok(ids('instruktor', 'narty').includes('instruktor_narciarstwa'))
  assert.ok(!ids('instruktor', 'narty').includes('instruktor_snowboardu'))
  assert.ok(ids('instruktor', 'snowboard').includes('instruktor_snowboardu'))
  assert.ok(!ids('instruktor', 'snowboard').includes('instruktor_narciarstwa'))
  assert.ok(ids('ksiadz', 'instruktor_narty').includes('instruktor_narciarstwa'))
  assert.ok(ids('ksiadz', 'instruktor_snowboard').includes('instruktor_snowboardu'))
})
