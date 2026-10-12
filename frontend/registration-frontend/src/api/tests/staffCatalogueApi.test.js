import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { fetchStaffCatalogue } from '../staffCatalogueApi.js'
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('staff options request uses selected Turnus and no service credentials', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:8080/api/turnusy/staff-options?turnusCode=A%2FB')
    assert.equal(options, undefined)
    return new Response(JSON.stringify({ seasonType:'WINTER',roles:[],certificatesByRole:{} }), { status:200 })
  }
  assert.equal((await fetchStaffCatalogue('A/B')).seasonType, 'WINTER')
})

test('outage and malformed replies show errors instead of falling back to Summer options', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ message:'Opcje kadry są chwilowo niedostępne.' }), { status:503 })
  await assert.rejects(fetchStaffCatalogue('T1'), /chwilowo niedostępne/)
  globalThis.fetch = async () => new Response('{}', { status:200 })
  await assert.rejects(fetchStaffCatalogue('T1'), /Nieprawidłowe opcje/)
  globalThis.fetch = async () => { throw new TypeError('offline') }
  await assert.rejects(fetchStaffCatalogue('T1'), /Nie można wczytać/)
})
