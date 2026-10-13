export function visibleStaffingRoles(stats, registrationType) {
  if (!stats || (registrationType && registrationType !== 'STAFF')) return []
  return (stats.staffRoles || []).filter(role => role.required > 0)
}

function nonnegativeCount(value) { return Number.isInteger(value) && value >= 0 }
function validSubrole(subrole) {
  if (!subrole || typeof subrole.code !== 'string' || typeof subrole.label !== 'string') return false
  return nonnegativeCount(subrole.accepted)
}
function validRole(role) {
  if (!role || typeof role.categoryCode !== 'string' || typeof role.label !== 'string') return false
  if (!nonnegativeCount(role.accepted)) return false
  if (!Number.isInteger(role.required) || role.required < 1) return false
  return Array.isArray(role.subroles) && role.subroles.every(validSubrole)
}
export function validStaffingStatistics(body) {
  if (!Array.isArray(body?.staffRoles) || !nonnegativeCount(body.unmappedAcceptedStaff)) return false
  return body.staffRoles.every(validRole)
}
