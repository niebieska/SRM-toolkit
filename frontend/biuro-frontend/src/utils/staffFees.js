function sameSelection(fee, option) {
  return fee.role === option.role && (fee.subrole ?? null) === (option.subrole ?? null)
}

export function staffFeeAmount(fees, option) {
  return (fees || []).find(fee => sameSelection(fee, option))?.amount ?? ''
}

export function hasStaffFee(fees, option) {
  return (fees || []).some(fee => sameSelection(fee, option))
}

export function withStaffFee(fees, option, amount) {
  const remaining = (fees || []).filter(fee => !sameSelection(fee, option))
  return [...remaining, { role: option.role, subrole: option.subrole ?? null, amount }]
}

export function withoutStaffFee(fees, option) {
  return (fees || []).filter(fee => !sameSelection(fee, option))
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
