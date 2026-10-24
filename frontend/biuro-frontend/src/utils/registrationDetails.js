const FIELD_LABELS = {
  turnusCode: 'Turnus',

  'person.firstName': 'Imię',
  'person.lastName': 'Nazwisko',
  'person.pesel': 'PESEL',
  'person.gender': 'Płeć',
  'person.isAdult': 'Osoba pełnoletnia',
  'person.contact.email': 'E-mail',
  'person.contact.phone': 'Telefon',

  'address.street': 'Ulica',
  'address.houseNumber': 'Nr domu',
  'address.postalCode': 'Kod pocztowy',
  'address.city': 'Miasto',
  'address.sameAddress': 'Adres opiekuna taki sam',
  'address.guardianAddress.street': 'Ulica (opiekun)',
  'address.guardianAddress.houseNumber': 'Nr domu (opiekun)',
  'address.guardianAddress.postalCode': 'Kod pocztowy (opiekun)',
  'address.guardianAddress.city': 'Miasto (opiekun)',

  'guardian.firstName': 'Imię',
  'guardian.lastName': 'Nazwisko',
  'guardian.relation': 'Relacja',
  'guardian.names': 'Imiona i nazwiska rodziców/opiekunów',
  'guardian.contact.email': 'E-mail',
  'guardian.contact.phone': 'Telefon',

  'ice.firstName': 'Imię',
  'ice.lastName': 'Nazwisko',
  'ice.relationOther': 'Doprecyzowanie relacji',
  'ice.relation': 'Relacja',
  'ice.phone': 'Telefon',

  'certificateDetails.inne': 'Inne uprawnienia',

  role: 'Rola',
  subrole: 'Funkcja',

  'consents.dataProcessing': 'Zgoda na przetwarzanie danych',
  'consents.imageUsage': 'Zgoda na wykorzystanie wizerunku',
  'consents.regulations': 'Akceptacja regulaminu',
  'consents.truthDeclaration': 'Oświadczenie o prawdziwości danych',

  'health.q1.question': 'Pytanie',
  'health.q1.answer': 'Odpowiedź',
  'health.q1.detail': 'Szczegóły',
  'health.q2.question': 'Pytanie',
  'health.q2.answer': 'Odpowiedź',
  'health.q2.detail': 'Szczegóły',
  'health.q3.question': 'Pytanie',
  'health.q3.answer': 'Odpowiedź',
  'health.q3.detail': 'Szczegóły',
  'health.additionalInfo': 'Dodatkowe informacje',
  'health.additionalInfo.value': 'Informacje',
}

const SECTION_LABELS = {
  person: 'Dane osobowe',
  address: 'Adres',
  guardian: 'Dane opiekuna',
  ice: 'Kontakt awaryjny',
  role: 'Rola',
  certificates: 'Uprawnienia',
  certificateDetails: 'Szczegóły uprawnień',
  health: 'Kwestionariusz',
  consents: 'Zgody',
  turnusCode: 'Turnus',
}

const MANDATORY_CONSENT_PATHS = new Set([
  'consents.dataProcessing',
  'consents.regulations',
  'consents.truthDeclaration',
])

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isAffirmative(value) {
  return value === true || (typeof value === 'string' && ['tak', 'yes'].includes(value.trim().toLocaleLowerCase('pl-PL')))
}

function toLabel(path, key) {
  const pathKey = path.join('.')
  if (FIELD_LABELS[pathKey]) return FIELD_LABELS[pathKey]
  if (SECTION_LABELS[key]) return SECTION_LABELS[key]
  return key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/^./, (char) => char.toUpperCase())
}

export function registrationSections(registration) {
  const payload = registration.payload
  // The saved classification takes precedence over age today or browser values.
  const minor = typeof registration.minor === 'boolean' ? registration.minor : null
  const type = registration.registrationType
  const sections = rawPayloadSections(payload)
  const selectedCertificateIds = new Set(
    Object.entries(isPlainObject(payload?.certificates) ? payload.certificates : {})
      .filter(([, selected]) => selected === true)
      .map(([id]) => id),
  )
  const known = new Set(['person', 'address', 'guardian', 'ice', 'role', 'subrole',
    'certificates', 'certificateDetails', 'health', 'consents', 'turnusCode'])
  const result = []
  const other = []
  for (const section of sections) {
    if (section.key === 'guardian' && minor === false) continue
    if (section.key === 'ice' && minor === true) continue
    if (type === 'PARTICIPANT' && ['certificates', 'certificateDetails'].includes(section.key)) continue
    if (section.key === 'certificates') {
      section.fields = section.fields.filter(field => field.path.startsWith('certificates.')
        && field.value === true && field.path !== 'certificates.inne')
      if (!section.fields.length) continue
    }
    if (section.key === 'certificateDetails') {
      section.fields = section.fields.filter(field => {
        const match = field.path.match(/^certificateDetails\.([^.]+)/)
        // Older payloads can retain the custom qualification description without its checkbox flag.
        const legacyCustomQualification = match?.[1] === 'inne' && payload?.certificates?.inne === undefined
        return match && (selectedCertificateIds.has(match[1]) || legacyCustomQualification)
          && field.value !== null && field.value !== undefined && String(field.value).trim() !== ''
      })
      if (!section.fields.length) continue
    }
    if (section.key === 'basic') {
      for (const field of section.fields) {
        const key = field.path
        if (key === 'guardian' && minor === false || key === 'ice' && minor === true) continue
        if (type === 'PARTICIPANT' && ['role', 'subrole'].includes(key)) continue
        if (key === 'turnusCode' && field.value === registration.turnusCode) continue
        if (['role', 'subrole'].includes(key)) {
          let role = result.find(item => item.key === 'role')
          if (!role) { role = { key: 'role', label: 'Rola i funkcja', fields: [] }; result.push(role) }
          role.fields.push(field)
        } else if (known.has(key) && key !== 'turnusCode') {
          result.push({ key, label: SECTION_LABELS[key] || field.label, fields: [field] })
        } else other.push(field)
      }
      continue
    } else if (!known.has(section.key) && section.key !== 'payload') {
      other.push(...section.fields)
      continue
    }
    if (section.key === 'health' && isPlainObject(payload?.health) && Object.keys(payload.health).length) {
      section.questions = Object.entries(payload.health).map(([key, value]) => {
        const question = isPlainObject(value) && typeof value.question === 'string' && value.question.trim()
          ? value.question : /^q\d+$/.test(key) ? `Pytanie ${key.slice(1)}` : toLabel(['health', key], key)
        const fields = flattenFields(value, ['health', key])
          .filter(field => field.path !== `health.${key}.question` || typeof field.value !== 'string' || !field.value.trim())
          .filter(field => field.path !== `health.${key}.detail` || isAffirmative(value?.answer))
        if (!fields.length) fields.push({ path: `health.${key}.answer`, label: 'Odpowiedź', value: null })
        return { key, question, fields }
      })
    }
    if (section.key === 'consents') {
      section.fields = section.fields.filter(field => !MANDATORY_CONSENT_PATHS.has(field.path))
      if (!section.fields.length) continue
    }
    if (section.fields.length) result.push(section)
  }
  if (other.length) result.push({ key: 'other', label: 'Pozostałe informacje', fields: other })
  const guardian = result.find(section => section.key === 'guardian')
  if (guardian) {
    guardian.fields.sort((a, b) => Number(a.path === 'guardian.names') - Number(b.path === 'guardian.names'))
  }
  const order = ['person', 'address', 'guardian', 'ice', 'role', 'certificates', 'certificateDetails', 'health', 'consents', 'payload', 'other']
  return result.sort((a, b) => order.indexOf(a.key) - order.indexOf(b.key))
}

export function combineQualificationSections(sections) {
  const result = sections.map(section => ({ ...section, fields: [...section.fields] }))
  const certificateIndex = result.findIndex(section => section.key === 'certificates')
  const detailsIndex = result.findIndex(section => section.key === 'certificateDetails')
  if (detailsIndex < 0) return result

  const details = result[detailsIndex]
  if (certificateIndex >= 0) {
    result[certificateIndex].fields.push(...details.fields)
  } else {
    const roleIndex = result.findIndex(section => section.key === 'role')
    const nextSectionIndex = result.findIndex(section => ['health', 'consents', 'other'].includes(section.key))
    const insertIndex = roleIndex >= 0
      ? roleIndex + 1
      : nextSectionIndex >= 0 ? nextSectionIndex : result.length
    result.splice(insertIndex, 0, { ...details, key: 'certificates', label: 'Uprawnienia' })
  }

  return result.filter(section => section.key !== 'certificateDetails')
}

function flattenFields(node, path = []) {
  if (Array.isArray(node)) {
    return [{
      path: path.join('.'),
      label: toLabel(path, path[path.length - 1] || 'value'),
      value: node.length === 0 ? null : node,
    }]
  }

  if (isPlainObject(node)) {
    const entries = Object.entries(node)
    if (entries.length === 0) {
      return [{
        path: path.join('.'),
        label: toLabel(path, path[path.length - 1] || 'value'),
        value: null,
      }]
    }
    return entries.flatMap(([key, value]) => flattenFields(value, [...path, key]))
  }

  return [{
    path: path.join('.'),
    label: toLabel(path, path[path.length - 1] || 'value'),
    value: node,
  }]
}

function rawPayloadSections(payload) {
  if (!isPlainObject(payload)) {
    return [{
      key: 'payload',
      label: 'Dane zgłoszenia',
      fields: [{
        path: 'payload',
        label: 'Dane',
        value: payload,
      }],
    }]
  }

  const topLevel = Object.entries(payload)
  const scalarFields = []
  const sections = []

  for (const [key, value] of topLevel) {
    if (isPlainObject(value) || Array.isArray(value)) {
      sections.push({
        key,
        label: SECTION_LABELS[key] || toLabel([key], key),
        fields: flattenFields(value, [key]),
      })
    } else {
      scalarFields.push({
        path: key,
        label: toLabel([key], key),
        value,
      })
    }
  }

  if (scalarFields.length) {
    sections.unshift({
      key: 'basic',
      label: 'Podstawowe informacje',
      fields: scalarFields,
    })
  }

  return sections
}

export function formatFieldValue(value,path = '') {
  if (value === null || value === undefined || value === '') return 'Brak danych'
  if (path === 'person.gender') {
    return ({ male: 'Mężczyzna', female: 'Kobieta' })[value] || String(value)
  }
  if (['guardian.relation', 'ice.relation'].includes(path))
    return ({ matka: 'Matka', ojciec: 'Ojciec', opiekun_prawny: 'Opiekun prawny', inna: 'Inna' })[value] || String(value)
  if (/^health\.q\d+\.answer$/.test(path)) return ({ tak: 'Tak', nie: 'Nie' })[value] || String(value)
  if (typeof value === 'boolean') return value ? 'Tak' : 'Nie'
  if (Array.isArray(value)) {
    if (value.every((entry) => ['string', 'number', 'boolean'].includes(typeof entry))) {
      return value.join(', ')
    }
    return JSON.stringify(value)
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

