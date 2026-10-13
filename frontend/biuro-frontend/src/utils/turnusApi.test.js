import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { fetchTurnuses, fetchTurnus, createTurnus, updateTurnus, fetchStaffingCategories } from '../api/turnusApi.js'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('catalogue GET uses operator Bearer credentials and preserves inactive entries', async () => {
  const definitions = [{ turnusCode: 'T1', active: false, participantRegistrationOpen: false }]
  globalThis.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:8081/api/turnuses')
    assert.equal(options.headers.Authorization, 'Bearer operator-token')
    assert.equal(options.method, 'GET')
    return new Response(JSON.stringify(definitions), { status: 200 })
  }
  assert.deepEqual(await fetchTurnuses('operator-token'), definitions)
})

test('detail codes are encoded, create uses POST and edit sends its version with PATCH', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, ...options })
    return new Response(JSON.stringify({ turnusCode: 'T1', version: 4 }), { status: 200 })
  }
  await fetchTurnus('token', 'A/B')
  await createTurnus('token', { turnusCode: 'T1', active: false, participantRegistrationOpen: false })
  await updateTurnus('token', 'T1', { turnusCode: 'T1', version: 3, active: true })
  assert.ok(calls[0].url.endsWith('/A%2FB'))
  assert.equal(calls[1].method, 'POST')
  assert.equal(JSON.parse(calls[1].body).participantRegistrationOpen, false)
  assert.equal(calls[2].method, 'PATCH')
  assert.equal(JSON.parse(calls[2].body).version, 3)
})

test('conflicts preserve backend message and status for reload UI', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Odśwież dane przed zapisem.' }), { status: 409 })
  await assert.rejects(updateTurnus('token', 'T1', {}), error => error.status === 409 && error.message === 'Odśwież dane przed zapisem.')
})

test('non-JSON forbidden responses remain authentication errors rather than parse failures', async () => {
  globalThis.fetch = async () => new Response('Forbidden', { status: 403 })
  await assert.rejects(fetchTurnuses('token'), error => error.status === 403 && error.message.includes('Zaloguj'))
})

test('network and invalid success responses produce actionable errors', async () => {
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
  await assert.rejects(fetchTurnuses('token'), /Nie można połączyć/)
  globalThis.fetch = async () => new Response('<html>', { status: 200 })
  await assert.rejects(fetchTurnuses('token'), /nieprawidłową odpowiedź/)
})


test('staffing category lookup uses Bearer authentication and the selected season', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:8081/api/turnuses/staffing-categories?seasonType=WINTER')
    assert.equal(options.headers.Authorization, 'Bearer token')
    return new Response(JSON.stringify([{ code: 'animator', label: 'Animatorzy' }]))
  }
  assert.deepEqual(await fetchStaffingCategories('token', 'WINTER'), [{ code: 'animator', label: 'Animatorzy' }])
})
