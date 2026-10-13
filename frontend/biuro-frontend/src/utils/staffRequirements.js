export function requirementCount(requirements, code) {
  return (requirements || []).find(item => item.categoryCode === code)?.requiredCount ?? 0
}
export function withRequirement(requirements, code, input) {
  const requiredCount = input === '' ? '' : Number(input)
  const remaining = (requirements || []).filter(item => item.categoryCode !== code)
  return requiredCount === 0 ? remaining : [...remaining, { categoryCode: code, requiredCount }]
}
export function requirementsError(requirements, categories) {
  const allowed = new Set(categories.map(category => category.code))
  const seen = new Set()
  for (const item of requirements || []) {
    if (!allowed.has(item.categoryCode)) return 'Wymagana kadra zawiera kategorię niedostępną w wybranym sezonie.'
    if (seen.has(item.categoryCode)) return 'Kategoria wymaganej kadry nie może się powtarzać.'
    if (!Number.isInteger(item.requiredCount) || item.requiredCount < 0 || item.requiredCount > 2147483647)
      return 'Podaj nieujemne liczby całkowite wymaganych osób.'
    seen.add(item.categoryCode)
  }
  return ''
}
