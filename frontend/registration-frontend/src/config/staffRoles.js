// Options come from Registration's cached, Biuro-owned season configuration.
export function ageAllowed(option, isAdult) {
  return isAdult ? !option.minorOnly : !option.adultOnly
}
export function availableFunctions(role, isAdult) {
  return (role?.subroles || []).filter(option => ageAllowed(option, isAdult))
}
export function availableRoles(catalogue, isAdult) {
  return (catalogue?.roles || []).filter(role => ageAllowed(role, isAdult) || availableFunctions(role, isAdult).length > 0)
}
export function functionRequired(role, isAdult) {
  return !!role && (role.subroleRequired || !ageAllowed(role, isAdult))
}
export function selectionError(catalogue, roleCode, functionCode, isAdult) {
  const role = availableRoles(catalogue, isAdult).find(role => role.value === roleCode)
  if (!role) return 'Wybierz rolę w kadrze.'
  if (!functionCode) return functionRequired(role, isAdult) ? 'Wybierz funkcję.' : ''
  return availableFunctions(role, isAdult).some(option => option.value === functionCode) ? '' : 'Wybierz prawidłową funkcję.'
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
