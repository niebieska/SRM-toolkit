const collator = new Intl.Collator('pl', { sensitivity: 'base', numeric: true })
export const listColumns = [
  { key: 'firstName', label: 'Imię' },
  { key: 'lastName', label: 'Nazwisko' },
  { key: 'pesel', label: 'PESEL' },
  { key: 'address', label: 'Adres' },
  { key: 'age', label: 'Wiek' },
  { key: 'personPhone', label: 'Telefon' },
  { key: 'contactPhone', label: 'Telefon rodzica / ICE' },
]

export function listValue(entry, key) {
  if (key === 'age') return entry.age
  if (key === 'contactPhone') return entry.person.minor ? entry.person.guardianPhone : entry.person.icePhone
  if (key === 'address') {
    const address = entry.person.address
    if (!address) return null
    const street = [address.street, address.houseNumber].filter(Boolean).join(' ')
    const city = [address.postalCode, address.city].filter(Boolean).join(' ')
    return [street, city].filter(Boolean).join(', ') || null
  }
  if (key === 'roleLabel') return [entry.roleLabel, entry.subroleLabel].filter(Boolean).join(' — ')
  if (key === 'gender') return ({ female: 'Kobieta', male: 'Mężczyzna' })[entry.person.gender] || entry.person.gender
  return entry.person[key]
}

export function filterAndSortList(entries, { search = '', role = '', type, sortKey = 'lastName', direction = 'asc' }) {
  const phrase = search.trim().toLocaleLowerCase('pl')
  return entries.filter(entry => {
    if (entry.person.registrationType !== type) return false
    if (type === 'STAFF' && role && entry.person.role !== role) return false
    const name = `${entry.person.firstName || ''} ${entry.person.lastName || ''}`.toLocaleLowerCase('pl')
    const reversed = `${entry.person.lastName || ''} ${entry.person.firstName || ''}`.toLocaleLowerCase('pl')
    return !phrase || name.includes(phrase) || reversed.includes(phrase)
  }).sort((a, b) => {
    const left = listValue(a, sortKey), right = listValue(b, sortKey)
    const missingLeft = left == null || left === '', missingRight = right == null || right === ''
    if (missingLeft !== missingRight) return missingLeft ? 1 : -1
    const comparison = sortKey === 'age' ? (left ?? 0) - (right ?? 0) : collator.compare(left || '', right || '')
    if (comparison) return direction === 'desc' ? -comparison : comparison
    return collator.compare(a.person.lastName || '', b.person.lastName || '')
      || collator.compare(a.person.firstName || '', b.person.firstName || '')
      || collator.compare(a.person.registrationCode, b.person.registrationCode)
  })
}

export function validateTurnusLists(body, code) {
  if (!body?.turnus || body.turnus.turnusCode !== code || !Array.isArray(body.entries)) return false
  return body.entries.every(entry => {
    if (!entry?.person || !['PARTICIPANT', 'STAFF'].includes(entry.person.registrationType)) return false
    if (typeof entry.person.registrationCode !== 'string') return false
    if (typeof entry.person.minor !== 'boolean') return false
    return entry.age == null || (Number.isInteger(entry.age) && entry.age >= 0)
  })
}
