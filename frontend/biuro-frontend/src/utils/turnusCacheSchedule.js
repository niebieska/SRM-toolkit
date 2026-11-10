const HOUR_MS = 60 * 60 * 1000

export function nextTurnusCacheRefreshTime(now = new Date()) {
  const nextHour = new Date((Math.floor(now.getTime() / HOUR_MS) + 1) * HOUR_MS)
  return new Intl.DateTimeFormat('pl-PL', {
    timeZone: 'Europe/Warsaw',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(nextHour)
}
