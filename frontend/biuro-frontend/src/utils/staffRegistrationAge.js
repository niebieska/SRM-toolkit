function birthDateParts(pesel) {
  if (typeof pesel !== 'string' || !/^\d{11}$/.test(pesel)) return null

  const year = Number(pesel.slice(0, 2))
  let month = Number(pesel.slice(2, 4))
  const day = Number(pesel.slice(4, 6))
  let century

  if (month >= 1 && month <= 12) century = 1900
  else if (month >= 21 && month <= 32) { month -= 20; century = 2000 }
  else if (month >= 41 && month <= 52) { month -= 40; century = 2100 }
  else if (month >= 61 && month <= 72) { month -= 60; century = 2200 }
  else if (month >= 81 && month <= 92) { month -= 80; century = 1800 }
  else return null

  const fullYear = century + year
  const date = new Date(fullYear, month - 1, day)
  if (date.getFullYear() !== fullYear || date.getMonth() !== month - 1 || date.getDate() !== day) return null

  const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3, 1]
  const checksumValid = pesel.split('').reduce((sum, digit, index) => sum + Number(digit) * weights[index], 0) % 10 === 0
  if (!checksumValid) return null

  return { year: fullYear, month, day }
}

function referenceDateParts(value) {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return { year: value.getFullYear(), month: value.getMonth() + 1, day: value.getDate() }
  }
  if (typeof value !== 'string') return null

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const [, year, month, day] = match.map(Number)
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return { year, month, day }
}

export function isAdultOnDate(pesel, referenceDate) {
  const birth = birthDateParts(pesel)
  const reference = referenceDateParts(referenceDate)
  if (!birth || !reference) return false

  let age = reference.year - birth.year
  if (reference.month < birth.month || (reference.month === birth.month && reference.day < birth.day)) age--
  return age >= 18
}

export function parseStaffPesel(pesel, today = new Date()) {
  const birth = birthDateParts(pesel)
  if (!birth) return { isAdult: false, gender: null, valid: false }
  return {
    isAdult: isAdultOnDate(pesel, today),
    gender: Number(pesel[9]) % 2 === 0 ? 'female' : 'male',
    valid: true,
  }
}

export function isStaffOptionAgeEligible(option, adultAtTurnusStart) {
  return adultAtTurnusStart ? !option.minorOnly : !option.adultOnly
}

export function isStaffSubroleRequired(role, adultAtTurnusStart) {
  return !!role && (!!role.subroleRequired || !isStaffOptionAgeEligible(role, adultAtTurnusStart))
}

export function filterStaffRolesByAge(roles, adultAtTurnusStart) {
  return (roles || []).filter(role =>
    isStaffOptionAgeEligible(role, adultAtTurnusStart)
      || (role.subroles || []).some(subrole => isStaffOptionAgeEligible(subrole, adultAtTurnusStart)))
}

export function filterStaffSubrolesByAge(subroles, adultAtTurnusStart) {
  return (subroles || []).filter(subrole => isStaffOptionAgeEligible(subrole, adultAtTurnusStart))
}
