import test from 'node:test'
import assert from 'node:assert/strict'

// Explicit opt-in: run against the synthetic TEST stack, never a production form.
const base = process.env.PUBLIC_REGISTRATION_TEST_URL?.replace(/\/$/, '')
const credentials = process.env.REGISTRATION_SERVICE_PASSWORD
  ? `Basic ${Buffer.from(`${process.env.REGISTRATION_SERVICE_USERNAME || 'biuro-service'}:${process.env.REGISTRATION_SERVICE_PASSWORD}`).toString('base64')}`
  : null

async function check(path, status, options = {}) {
  const response = await fetch(`${base}${path}`, { ...options, signal: AbortSignal.timeout(10_000), redirect: 'manual' })
  assert.equal(response.status, status, `${options.method || 'GET'} ${path}`)
  return response
}

test('public proxy blocks management and unknown API routes', { skip: !base }, async () => {
  for (const path of ['/api', '/api/registrations', '/api/registrations/REG-PROBE',
    '/api/registrations/accepted-counts', '/api/registrations/turnus-list', '/api/email/send']) {
    await check(path, 404)
  }
  await check('/api/registrations/REG-PROBE/status', 404, { method: 'PATCH', body: '{}' })
})

test('management remains blocked with valid service credentials', { skip: !base || !credentials }, async () => {
  const url = new URL(base)
  assert.ok(url.protocol === 'https:' || ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname),
    'Service credentials require HTTPS except for a loopback test')
  await check('/api/registrations', 404, { headers: { Authorization: credentials } })
  await check('/api/registrations/REG-PROBE/status', 404, {
    method: 'PATCH', headers: { Authorization: credentials, 'Content-Type': 'application/json' }, body: '{}',
  })
})

test('public routes reject inappropriate methods', { skip: !base }, async () => {
  for (const path of ['/api/registrations/participant', '/api/registrations/staff']) {
    await check(path, 405)
    await check(path, 405, { method: 'DELETE' })
  }
  for (const path of ['/api/turnusy', '/api/turnuses', '/api/turnusy/staff-options', '/api/turnuses/staff-options']) {
    await check(path, 405, { method: 'POST', body: '{}' })
  }
})

test('catalogue aliases and form validation still reach Registration', { skip: !base }, async () => {
  const catalogue = await (await check('/api/turnusy', 200)).json()
  assert.ok(Array.isArray(catalogue))
  await check('/api/turnuses', 200)
  if (catalogue.length) {
    for (const path of ['/api/turnusy/staff-options', '/api/turnuses/staff-options']) {
      await check(`${path}?turnusCode=${encodeURIComponent(catalogue[0].turnusCode)}`, 200)
    }
  }
  // Invalid payloads exercise routing without saving registrations or sending mail.
  for (const type of ['participant', 'staff']) {
    await check(`/api/registrations/${type}`, 400, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
    })
  }
})
