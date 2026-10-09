import test from 'node:test'
import assert from 'node:assert/strict'
import { buildParticipantPayload, buildStaffPayload, submitParticipantRegistration, submitStaffRegistration } from '../registrationApi.js'

const form = () => ({ isAdult: true, iceRelation: 'inna', iceRelationOther: 'Przyjaciel', health: {} })

test('both payloads preserve other ICE relationship and omit guardian for adults', () => {
  for (const build of [buildParticipantPayload, buildStaffPayload]) {
    const payload = build(form())
    assert.equal(payload.ice.relation, 'inna')
    assert.equal(payload.ice.relationOther, 'Przyjaciel')
    assert.equal(payload.guardian, null)
    assert.equal(build({ ...form(), iceRelation: 'matka' }).ice.relationOther, null)
  }
})

test('minor payload uses guardian and omits ICE', () => {
  for (const build of [buildParticipantPayload, buildStaffPayload]) {
    const payload = build({ ...form(), isAdult: false, guardianFirstName: 'Anna', parentNames: 'Anna Testowa' })
    assert.equal(payload.ice, null)
    assert.equal(payload.guardian.firstName, 'Anna')
    assert.equal(payload.guardian.names, 'Anna Testowa')
  }
})

test('both submission paths retain backend code, message and status', async (t) => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  globalThis.fetch = async () => ({ ok: false, status: 422, json: async () => ({ code: 'AGE_TOO_LOW', message: 'Wiek jest zbyt niski.' }) })
  for (const submit of [submitParticipantRegistration, submitStaffRegistration]) {
    await assert.rejects(submit(form()), error => error.code === 'AGE_TOO_LOW' && error.status === 422 && error.message === 'Wiek jest zbyt niski.')
  }
})

test('non-JSON gateway error keeps a usable status fallback', async (t) => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  globalThis.fetch = async () => ({ ok: false, status: 502, json: async () => { throw new SyntaxError('HTML') } })
  await assert.rejects(submitParticipantRegistration(form()), error => error.status === 502 && error.message.includes('502'))
})

test('successful submission returns the issued registration code', async (t) => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  globalThis.fetch = async () => ({ ok: true, json: async () => ({ registrationCode: 'REG-P-T-1' }) })
  assert.deepEqual(await submitParticipantRegistration(form()), { registrationCode: 'REG-P-T-1' })
})


test('JSON null error responses preserve HTTP status', async (t) => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  globalThis.fetch = async () => ({ ok: false, status: 409, json: async () => null })
  for (const submit of [submitParticipantRegistration, submitStaffRegistration]) {
    await assert.rejects(submit(form()), error => error.status === 409)
  }
})
