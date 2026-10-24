export const roleLabels = { SYS_ADMIN: 'Administrator systemu', OPERATOR: 'Operator', VIEWER: 'Obserwator – Podgląd list' }
export function hasRole(user, role) { return !user?.passwordChangeRequired && user?.roles?.includes(role) === true }
export function homeFor(user) {
  if (user?.passwordChangeRequired) return '/haslo'
  if (hasRole(user, 'OPERATOR')) return '/dashboard'
  if (hasRole(user, 'SYS_ADMIN')) return '/konta'
  if (hasRole(user, 'VIEWER')) return '/listy'
  return '/brak-dostepu'
}
export function tokenExpiry(token) {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const exp = JSON.parse(atob(part)).exp
    return Number.isFinite(exp) ? exp * 1000 : 0
  } catch { return 0 }
}
export function passwordError(password) {
  if (!password?.trim() || [...password].length < 12 || new TextEncoder().encode(password).length > 72)
    return 'Hasło musi mieć co najmniej 12 znaków i najwyżej 72 bajty UTF-8.'
  return ''
}

export function canVisit(user, route) {
  return !route.meta.roles || route.meta.roles.some(role => hasRole(user, role))
}
