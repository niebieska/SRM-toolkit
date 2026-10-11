export function formatTurnusDate(value) {
  return value ? value.split('-').reverse().join('.') : '—'
}

export function matchesTurnusSearch(turnus, phrase) {
  const query = phrase.trim().toLocaleLowerCase('pl')
  const text = [
    turnus.turnusName, turnus.turnusCode, turnus.turnusLocation, turnus.seasonYear,
    turnus.startDate, turnus.endDate,
    `${formatTurnusDate(turnus.startDate)} – ${formatTurnusDate(turnus.endDate)}`,
  ].filter(value => value != null).join(' ').toLocaleLowerCase('pl')
  return text.includes(query)
}
