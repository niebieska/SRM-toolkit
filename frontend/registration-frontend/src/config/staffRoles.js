// Options come from Registration's cached, Biuro-owned season configuration.
export function ageAllowed(option, isAdultAtTurnusStart) {
  return isAdultAtTurnusStart ? !option.minorOnly : !option.adultOnly
}
export function availableFunctions(role, isAdultAtTurnusStart) {
  return (role?.subroles || []).filter(option => ageAllowed(option, isAdultAtTurnusStart))
}
export function availableRoles(catalogue, isAdultAtTurnusStart) {
  return (catalogue?.roles || []).filter(role => ageAllowed(role, isAdultAtTurnusStart) || availableFunctions(role, isAdultAtTurnusStart).length > 0)
}
export function functionRequired(role, isAdultAtTurnusStart) {
  return !!role && (role.subroleRequired || !ageAllowed(role, isAdultAtTurnusStart))
}
export function selectionError(catalogue, roleCode, functionCode, isAdultAtTurnusStart) {
  const role = availableRoles(catalogue, isAdultAtTurnusStart).find(role => role.value === roleCode)
  if (!role) return 'Wybierz rolę w kadrze.'
  if (!functionCode) return functionRequired(role, isAdultAtTurnusStart) ? 'Wybierz funkcję.' : ''
  return availableFunctions(role, isAdultAtTurnusStart).some(option => option.value === functionCode) ? '' : 'Wybierz prawidłową funkcję.'
}
export function resolveCertificateSource(catalogue, role, subrole) {
  const options = catalogue?.certificatesByRole || {}
  const combined = `${role}_${subrole}`
  if (subrole && options[combined]) return combined
  if (subrole && options[subrole]) return subrole
  return role
}
export function certificatesFor(catalogue, role, subrole) {
  return catalogue?.certificatesByRole?.[resolveCertificateSource(catalogue, role, subrole)] || []
}
