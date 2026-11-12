export function defaultWinterSeasonYear(date = new Date()) {
  const year = date.getFullYear()
  return date.getMonth() < 2 ? year : year + 1
}
