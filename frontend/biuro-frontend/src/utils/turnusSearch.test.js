import { test } from 'node:test'
import assert from 'node:assert/strict'
import { matchesTurnusSearch } from './turnusSearch.js'
const turnus = { turnusCode: 'ZAGLE26T1', turnusName: 'Żagle', turnusLocation: 'Opartowo',
  seasonYear: 2026, startDate: '2026-08-01', endDate: '2026-08-11' }

test('search matches displayed and ISO dates, partial dates, and the displayed range', () => {
  for (const phrase of ['01.08.2026', '11.08', '2026-08-01', '2026-08-11', '01.08.2026 – 11.08.2026'])
    assert.equal(matchesTurnusSearch(turnus, phrase), true, phrase)
  assert.equal(matchesTurnusSearch(turnus, '12.08.2026'), false)
})
test('existing case-insensitive text search and empty search remain supported', () => {
  for (const phrase of ['  żAGLE ', 'zagle26t1', 'OPARTOWO', '2026', ''])
    assert.equal(matchesTurnusSearch(turnus, phrase), true, phrase)
  assert.equal(matchesTurnusSearch({}, 'unknown'), false)
})
