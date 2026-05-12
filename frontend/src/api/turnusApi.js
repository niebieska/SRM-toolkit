const API_BASE = 'http://localhost:8080'

export async function fetchTurnusy() {
  const response = await fetch(`${API_BASE}/api/turnusy`)
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
  return response.json()
}
