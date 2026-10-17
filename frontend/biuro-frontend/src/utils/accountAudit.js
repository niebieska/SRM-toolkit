import { roleLabels } from './accountAccess.js'
export const eventLabels = { BOOTSTRAP_ADMIN: 'Utworzenie pierwszego administratora', ACCOUNT_CREATED: 'Utworzenie konta',
  ACCOUNT_UPDATED: 'Zmiana konta', PASSWORD_RESET: 'Reset hasła', PASSWORD_CHANGED: 'Zmiana hasła',
  LOGIN_SUCCEEDED: 'Logowanie', LOGIN_FAILED: 'Nieudane logowanie', LOGOUT: 'Wylogowanie' }
export function auditChanges(entry) {
  const before = entry.details?.before, after = entry.details?.after
  if (!before || !after) return ''
  const changes = []
  if (before.displayName !== after.displayName) changes.push(`Nazwa: ${before.displayName} → ${after.displayName}`)
  if (before.active !== after.active) changes.push(after.active ? 'Konto włączone' : 'Konto wyłączone')
  const roles = values => [...(values || [])].sort().map(role => roleLabels[role] || role).join(', ') || 'brak'
  const codes = values => [...(values || [])].sort().join(', ') || 'brak'
  if (roles(before.roles) !== roles(after.roles)) changes.push(`Role: ${roles(before.roles)} → ${roles(after.roles)}`)
  if (codes(before.turnusCodes) !== codes(after.turnusCodes)) changes.push(`Turnusy: ${codes(before.turnusCodes)} → ${codes(after.turnusCodes)}`)
  return changes.join('; ') || 'Zapis bez zmiany uprawnień i danych.'
}
