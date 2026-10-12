import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { fetchTurnusy } from '../turnusApi.js'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('participant and staff forms request independent catalogue options', async () => {
  const requests = []
  globalThis.fetch = async url => {
    requests.push(url)
    return new Response(JSON.stringify([{ turnusCode: 'T1' }]), { status: 200 })
  }
  await fetchTurnusy()
  await fetchTurnusy('STAFF')
  assert.equal(requests[0], 'http://localhost:8080/api/turnusy?registrationType=PARTICIPANT')
  assert.equal(requests[1], 'http://localhost:8080/api/turnusy?registrationType=STAFF')
})
