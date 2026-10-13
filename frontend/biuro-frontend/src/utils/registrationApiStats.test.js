import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { fetchTurnusStats } from '../api/registrationApi.js'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('statistics use operator credentials and return both type groups', async () => {
  const stats = { turnusCode: 'A/B', participants: { capacity: 60, accepted: 61, overCapacity: 1 }, staff: { capacity: 10, accepted: 0 }, staffRoles: [], unmappedAcceptedStaff: 0 }
  globalThis.fetch = async (url, options) => {
    assert.ok(url.endsWith('/stats/A%2FB'))
    assert.equal(options.headers.Authorization, 'Bearer operator')
    return new Response(JSON.stringify(stats), { status: 200 })
  }
  assert.deepEqual(await fetchTurnusStats('operator', 'A/B'), stats)
})

test('statistics preserve safe backend messages and reject unavailable or malformed replies', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Brak połączenia ze zgłoszeniami.' }), { status: 502 })
  await assert.rejects(fetchTurnusStats('operator', 'T1'), /Brak połączenia ze zgłoszeniami/)
  globalThis.fetch = async () => new Response('{}', { status: 200 })
  await assert.rejects(fetchTurnusStats('operator', 'T1'), /nieprawidłowe statystyki/)
  globalThis.fetch = async () => { throw new TypeError('offline') }
  await assert.rejects(fetchTurnusStats('operator', 'T1'), /Nie można połączyć/)
})


test('staffing data is preserved and malformed role statistics cannot look like zero counts', async () => {
  const stats = { turnusCode: 'T1', participants: {}, staff: {}, staffRoles: [
    { categoryCode: 'animator', label: 'Animator', accepted: 2, required: 6,
      subroles: [{ code: 'kandydat_na_animatora', label: 'Kandydat', accepted: 1 }] },
  ], unmappedAcceptedStaff: 0 }
  globalThis.fetch = async () => new Response(JSON.stringify(stats))
  assert.deepEqual(await fetchTurnusStats('operator', 'T1'), stats)
  for (const malformed of [ { ...stats, staffRoles: null },
    { ...stats, staffRoles: [{ ...stats.staffRoles[0], accepted: -1 }] },
    { ...stats, staffRoles: [{ ...stats.staffRoles[0], required: 0 }] },
    { ...stats, unmappedAcceptedStaff: null } ]) {
    globalThis.fetch = async () => new Response(JSON.stringify(malformed))
    await assert.rejects(fetchTurnusStats('operator', 'T1'), /nieprawidłowe statystyki/)
  }
})
