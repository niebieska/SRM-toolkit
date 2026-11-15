function sameSelection(fee, option) {
  return fee.role === option.role && (fee.subrole ?? null) === (option.subrole ?? null)
}

function isSummerCandidate(selection) {
  return (selection.role === 'kandydat_na_animatora' && !selection.subrole)
    || (selection.role === 'animator' && selection.subrole === 'kandydat_na_animatora')
}

function sameBillingSelection(fee, option) {
  return sameSelection(fee, option) || (isSummerCandidate(fee) && isSummerCandidate(option))
}

export function staffFeeAmount(fees, option) {
  const matchingFees = fees || []
  return matchingFees.find(fee => sameSelection(fee, option))?.amount
    ?? matchingFees.find(fee => sameBillingSelection(fee, option))?.amount
    ?? ''
}

export function hasStaffFee(fees, option) {
  return (fees || []).some(fee => sameBillingSelection(fee, option))
}

export function withStaffFee(fees, option, amount) {
  const remaining = (fees || []).filter(fee => !sameBillingSelection(fee, option))
  return [...remaining, { role: option.role, subrole: option.subrole ?? null, amount }]
}

export function withoutStaffFee(fees, option) {
  return (fees || []).filter(fee => !sameBillingSelection(fee, option))
}

export function visibleStaffFeeOptions(options, season, existingFees = []) {
  const selected = new Set((existingFees || []).map(fee => `${fee.role}\u0000${fee.subrole ?? ''}`))
  if (season === 'SUMMER') {
    const minor = (existingFees || []).find(fee => fee.role === 'kandydat_na_animatora' && !fee.subrole)
    const adult = (existingFees || []).find(fee => fee.role === 'animator' && fee.subrole === 'kandydat_na_animatora')
    const legacyAmountsDiffer = minor && adult && Number(minor.amount) !== Number(adult.amount)
    return options.filter(option => {
      const key = `${option.role}\u0000${option.subrole ?? ''}`
      if (option.role === 'animator' && option.subrole === 'kandydat_na_animatora') return true
      if (option.role === 'kandydat_na_animatora' && !option.subrole) return !!legacyAmountsDiffer
      return selected.has(key)
    })
  }
  if (season !== 'WINTER') return options
  return options.filter(option => {
    const key = `${option.role}\u0000${option.subrole ?? ''}`
    return (option.role === 'animator' && option.subrole === 'kandydat_na_animatora') || selected.has(key)
  })
}

export function staffFeesError(fees, options) {
  const allowed = new Set(options.map(option => `${option.role}\u0000${option.subrole ?? ''}`))
  const seen = new Set()
  const baseRoles = new Set()
  const subroleRoles = new Set()
  for (const fee of fees || []) {
    const subrole = fee.subrole ?? ''
    const key = `${fee.role}\u0000${subrole}`
    const amount = Number(fee.amount)
    if (!allowed.has(key)) return 'Opłaty kadry zawierają pozycję spoza sezonowego katalogu ról.'
    if (seen.has(key)) return 'Opłata dla tej roli i funkcji powtarza się.'
    if (!Number.isFinite(amount) || amount <= 0 || amount > 99999999.99 || Number(amount.toFixed(2)) !== amount)
      return 'Podaj dodatnią opłatę kadry z maksymalnie dwoma miejscami po przecinku.'
    if (subrole) subroleRoles.add(fee.role)
    else baseRoles.add(fee.role)
    seen.add(key)
  }
  if ([...baseRoles].some(role => subroleRoles.has(role)))
    return 'Ustaw opłatę dla roli albo jej funkcji — opłaty nie mogą się nakładać.'
  return ''
}
