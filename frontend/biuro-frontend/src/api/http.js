const baseUrl = (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8081').replace(/\/$/, '')
let unauthorized = () => {}
let forbidden = async () => {}
export function onUnauthorized(handler) { unauthorized = handler }
export function onForbidden(handler) { forbidden = handler }

// Requests are never retried automatically, especially writes.
export async function request(path, { token, method = 'GET', payload, session = true } = {}) {
  let response
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
    })
  } catch {
    throw new Error('Nie można połączyć się z Biurem. Sprawdź połączenie i spróbuj ponownie.')
  }
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && session && token) unauthorized(token)
    if (response.status === 403 && session && token) await forbidden(token).catch(() => {})
    const defaults = { 401: 'Sesja wygasła. Zaloguj się ponownie.', 403: 'Brak dostępu. Zaloguj się ponownie.',
      404: 'Nie znaleziono zasobu.', 409: 'Dane zostały zmienione. Wczytaj aktualną wersję przed zapisem.' }
    const error = new Error(body?.message || defaults[response.status] || 'Nie udało się wykonać operacji. Spróbuj ponownie.')
    error.status = response.status
    throw error
  }
  if (body === null && response.status !== 204) throw new Error('Biuro zwróciło nieprawidłową odpowiedź. Spróbuj ponownie.')
  return body
}
