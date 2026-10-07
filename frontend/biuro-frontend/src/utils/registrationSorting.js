const collator = new Intl.Collator('pl', { numeric: true, sensitivity: 'base' })

function sortValue(registration, key) {
  if (key === 'createdAt') {
    if (!registration.createdAt) return null
    const timestamp = Date.parse(registration.createdAt)
    return Number.isNaN(timestamp) ? null : timestamp
  }
  if (key === 'name') {
    return `${registration.firstName || ''} ${registration.lastName || ''}`.trim() || null
  }
  if (key === 'registrationType') {
    return { PARTICIPANT: 'Uczestnik', STAFF: 'Kadra' }[registration.registrationType] ?? null
  }
  if (key === 'status') {
    return {
      NEW: 'Nowe', ACCEPTED: 'Zaakceptowane', WAITLIST: 'Lista rezerwowa', REJECTED: 'Odrzucone',
    }[registration.status] ?? null
  }
  const value = registration[key]
  return value == null || value === '' ? null : value
}

export function sortRegistrations(registrations, key, direction = 'asc') {
  const multiplier = direction === 'desc' ? -1 : 1
  return [...registrations].sort((a, b) => {
    const left = sortValue(a, key)
    const right = sortValue(b, key)
    // Unknown values belong last in both directions.
    if (left === null && right !== null) return 1
    if (right === null && left !== null) return -1
    const comparison = left === null ? 0 : typeof left === 'number' && typeof right === 'number'
      ? left - right
      : collator.compare(String(left), String(right))
    return comparison * multiplier || collator.compare(a.registrationCode || '', b.registrationCode || '')
  })
}
