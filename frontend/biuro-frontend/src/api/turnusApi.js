const baseUrl = (import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8081').replace(/\/$/, '')

async function request(token, path = '', method = 'GET', payload) {
  let response
  try {
    response = await fetch(`${baseUrl}/api/turnuses${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
    })
  } catch {
    throw new Error('Nie można połączyć się z Biurem. Sprawdź połączenie i spróbuj ponownie.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    const defaults = {
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Brak dostępu. Zaloguj się ponownie.',
      404: 'Nie znaleziono turnusu.',
      409: 'Turnus został zmieniony. Wczytaj aktualną wersję przed zapisem.',
    }
    const error = new Error(body?.message || defaults[response.status] || 'Nie udało się wykonać operacji. Spróbuj ponownie.')
    error.status = response.status
    throw error
  }
  if (body === null) throw new Error('Biuro zwróciło nieprawidłową odpowiedź. Spróbuj ponownie.')
  return body
}

export const fetchTurnuses = (token) => request(token)
export const fetchTurnus = (token, code) => request(token, `/${encodeURIComponent(code)}`)
export const createTurnus = (token, payload) => request(token, '', 'POST', payload)
export const updateTurnus = (token, code, payload) => request(token, `/${encodeURIComponent(code)}`, 'PATCH', payload)
