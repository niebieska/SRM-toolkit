import { API_BASE } from './config.js'

export async function fetchTurnusy(registrationType = 'PARTICIPANT') {
  const response = await fetch(`${API_BASE}/api/turnusy?registrationType=${encodeURIComponent(registrationType)}`)
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  return response.json()
}
