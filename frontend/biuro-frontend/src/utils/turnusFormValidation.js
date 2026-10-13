import { requirementsError } from './staffRequirements.js'

function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

/** Mirrors existing Turnus request bounds; does not add capacity/staffing restrictions. */
export function validateTurnusForm(form, categories) {
  const errors = {}
  for (const [key, label, max] of [
    ['turnusName', 'nazwę turnusu', 200], ['turnusLocation', 'miejsce turnusu', 500],
  ]) {
    if (!String(form[key] ?? '').trim()) errors[key] = `Podaj ${label}.`
    else if (String(form[key]).length > max) errors[key] = `Maksymalnie ${max} znaków.`
  }
  if (!/^[A-Z0-9][A-Z0-9_-]{0,49}$/.test(form.turnusCode || ''))
    errors.turnusCode = 'Podaj kod: 1–50 znaków, wielkie litery, cyfry, myślnik lub podkreślenie.'
  if (!['SUMMER', 'WINTER'].includes(form.seasonType)) errors.seasonType = 'Wybierz sezon.'
  for (const [key, min, max, message] of [
    ['seasonYear', 2000, 9999, 'Podaj rok od 2000 do 9999.'],
    ['minAge', 0, 120, 'Podaj wiek od 0 do 120 lat.'],
    ['participantCapacity', 1, 2147483647, 'Podaj co najmniej 1 miejsce (liczba całkowita).'],
    ['staffCapacity', 1, 2147483647, 'Podaj co najmniej 1 miejsce (liczba całkowita).'],
  ]) {
    const value = form[key]
    if (!Number.isInteger(value) || value < min || value > max) errors[key] = message
  }
  if (!validDate(form.startDate)) errors.startDate = 'Wybierz poprawną datę początku.'
  if (!validDate(form.endDate)) errors.endDate = 'Wybierz poprawną datę końca.'
  if (!errors.startDate && !errors.endDate && form.endDate < form.startDate)
    errors.endDate = 'Data końca nie może poprzedzać daty początku.'
  if (String(form.turnusDescription ?? '').length > 4000) errors.turnusDescription = 'Opis może mieć maksymalnie 4000 znaków.'
  const staffing = requirementsError(form.staffRequirements, categories)
  if (staffing) errors.staffRequirements = staffing
  return errors
}
