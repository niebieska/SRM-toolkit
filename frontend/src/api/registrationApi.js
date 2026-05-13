const API_BASE = 'http://localhost:8080'

export async function submitParticipantRegistration(formData) {
  const response = await fetch(`${API_BASE}/api/registrations/participant`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(formData),
  })
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
  return response.json()
}

export async function submitStaffRegistration(formData) {
  const response = await fetch(`${API_BASE}/api/registrations/staff`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(formData),
  })
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
  return response.json()
}
