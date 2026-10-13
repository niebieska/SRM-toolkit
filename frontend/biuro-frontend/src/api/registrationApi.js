import { validStaffingStatistics } from '../utils/staffingStatistics.js'

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
}

export async function fetchRegistrations(token, filters = {}) {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.registrationType) params.set('registrationType', filters.registrationType)
  if (filters.turnusCode) params.set('turnusCode', filters.turnusCode)

  const query = params.toString()
  const response = await fetch(`http://localhost:8081/api/biuro/registrations${query ? `?${query}` : ''}`, {
    headers: authHeaders(token),
  })

  if (!response.ok) {
    throw new Error('FETCH_FAILED')
  }

  return response.json()
}

export async function updateRegistrationStatus(token, code, payload) {
  const response = await fetch(`http://localhost:8081/api/biuro/registrations/${code}/status`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw new Error('UPDATE_FAILED')
  }

  return response.json()
}

export async function fetchRegistrationDetail(token, code) {
  const response = await fetch(`http://localhost:8081/api/biuro/registrations/${code}`, {
    headers: authHeaders(token),
  })

  if (!response.ok) {
    throw new Error('FETCH_FAILED')
  }

  return response.json()
}

export async function fetchTurnusStats(token, turnusCode) {
  let response
  try {
    response = await fetch(`http://localhost:8081/api/biuro/registrations/stats/${encodeURIComponent(turnusCode)}`, {
      headers: authHeaders(token),
    })
  } catch {
    throw new Error('Nie można połączyć się z Biurem. Spróbuj ponownie.')
  }
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message || 'Nie udało się wczytać statystyk. Spróbuj ponownie.')
  }
  if (!body?.participants || !body?.staff || !validStaffingStatistics(body)) {
    throw new Error('Biuro zwróciło nieprawidłowe statystyki. Spróbuj ponownie.')
  }
  return body
}
