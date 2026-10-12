export async function fetchStaffCatalogue(turnusCode) {
  let response
  try {
    response = await fetch(`http://localhost:8080/api/turnusy/staff-options?turnusCode=${encodeURIComponent(turnusCode)}`)
  } catch {
    throw new Error('Nie można wczytać opcji kadry. Sprawdź połączenie i spróbuj ponownie.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message || 'Opcje kadry są chwilowo niedostępne. Spróbuj ponownie.')
  if (!body || !Array.isArray(body.roles) || !body.certificatesByRole) {
    throw new Error('Nieprawidłowe opcje kadry. Spróbuj ponownie.')
  }
  return body
}
