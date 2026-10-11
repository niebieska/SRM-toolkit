const collator = new Intl.Collator('pl', { numeric: true, sensitivity: 'base' })
function value(turnus, key) {
  if (key === 'availability') return !turnus.active ? 'Nieaktywny' : turnus.registrationOpen ? 'Otwarte' : 'Zamknięte'
  return turnus[key] == null || turnus[key] === '' ? null : turnus[key]
}
export function sortTurnuses(turnuses, key = 'startDate', direction = 'asc') {
  const multiplier = direction === 'desc' ? -1 : 1
  return [...turnuses].sort((a, b) => {
    const left = value(a, key), right = value(b, key)
    if (left === null && right !== null) return 1
    if (right === null && left !== null) return -1
    const comparison = left === null ? 0 : typeof left === 'number' && typeof right === 'number'
      ? left - right : collator.compare(String(left), String(right))
    return comparison * multiplier || collator.compare(a.turnusCode || '', b.turnusCode || '')
  })
}
