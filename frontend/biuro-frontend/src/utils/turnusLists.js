const collator = new Intl.Collator('pl', { sensitivity: 'base', numeric: true })
export const availableListColumns = [
  { key: 'firstName', label: 'Imię' },
  { key: 'lastName', label: 'Nazwisko' },
  { key: 'pesel', label: 'PESEL' },
  { key: 'address', label: 'Adres' },
  { key: 'birthDate', label: 'Data urodzenia' },
  { key: 'gender', label: 'Płeć' },
  { key: 'age', label: 'Wiek' },
  { key: 'personPhone', label: 'Telefon' },
  { key: 'contactPhone', label: 'Telefon rodzica / ICE' },
  { key: 'email', label: 'E-mail osoby' },
  { key: 'contactName', label: 'Imię i nazwisko rodzica / ICE' },
  { key: 'guardianEmail', label: 'E-mail opiekuna' },
  { key: 'contactRelation', label: 'Relacja kontaktu' },
  { key: 'city', label: 'Miasto' },
  { key: 'qualifications', label: 'Uprawnienia kadry' },
  { key: 'imageConsent', label: 'Zgoda na wykorzystanie wizerunku' },
]

const generalColumnKeys = ['firstName', 'lastName', 'pesel', 'address', 'age', 'personPhone', 'contactPhone']
export const listColumns = availableListColumns.filter(column => generalColumnKeys.includes(column.key))

export const listViews = [
  { key: 'general', label: 'Ogólny', columns: listColumns.map(column => column.key), sortKey: 'lastName' },
  { key: 'insurance', label: 'Ubezpieczenie', columns: ['firstName', 'lastName', 'pesel'], sortKey: 'lastName' },
  { key: 'onsite', label: 'Lista na turnus', columns: ['firstName', 'lastName', 'address', 'age', 'personPhone', 'contactPhone'], sortKey: 'lastName' },
  { key: 'custom', label: 'Własny' },
]

export function columnsForList(view, customKeys, type) {
  const definition = listViews.find(item => item.key === view) || listViews[0]
  const selected = definition.key === 'custom' ? customKeys : definition.columns
  const columns = availableListColumns.filter(column => {
    if (definition.key === 'onsite' && type === 'STAFF' && ['address', 'age'].includes(column.key)) return false
    if (column.key === 'qualifications' && type !== 'STAFF') return false
    return selected.includes(column.key)
  })
  if (type !== 'STAFF' || !columns.length || definition.key === 'insurance') return columns
  const surname = columns.findIndex(column => column.key === 'lastName')
  const position = surname < 0 ? Math.min(2, columns.length) : surname + 1
  return [...columns.slice(0, position), { key: 'roleLabel', label: 'Rola / funkcja' }, ...columns.slice(position)]
}

export function listValue(entry, key) {
  const extra = entry.person.additionalData
  if (key === 'email' || key === 'guardianEmail') return extra?.[key] ?? null
  if (key === 'city') return entry.person.address?.city ?? null
  if (key === 'contactName') return [extra?.contactFirstName, extra?.contactLastName].filter(Boolean).join(' ') || null
  if (key === 'contactRelation') return contactRelation(extra)
  if (key === 'qualifications') return Array.isArray(entry.qualificationLabels) ? entry.qualificationLabels.join('; ') || null : null
  if (key === 'imageConsent') {
    if (extra?.imageConsent === true) return 'Tak'
    if (extra?.imageConsent === false) return 'Nie'
    return null
  }
  if (key === 'age') return entry.age
  if (key === 'birthDate') {
    const date = entry.person.birthDate
    return date ? date.split('-').reverse().join('.') : null
  }
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

function contactRelation(extra) {
  if (!extra?.contactRelation) return null
  const label = ({ matka: 'Matka', ojciec: 'Ojciec', opiekun_prawny: 'Opiekun prawny', inna: 'Inna' })[extra.contactRelation] || extra.contactRelation
  return extra.contactRelation === 'inna' && extra.contactRelationOther ? `${label}: ${extra.contactRelationOther}` : label
}

function compareValues(a, b, key, left, right) {
  if (key === 'age') return (left ?? 0) - (right ?? 0)
  // ISO dates must sort chronologically, not by the Polish display format.
  if (key === 'birthDate') return collator.compare(a.person.birthDate || '', b.person.birthDate || '')
  return collator.compare(left || '', right || '')
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
    const comparison = compareValues(a, b, sortKey, left, right)
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
