import { validStaffingStatistics } from '../utils/staffingStatistics.js'
import { request } from './http.js'
const base = '/api/biuro/registrations'
export function fetchRegistrations(token, filters = {}) {
  const params = new URLSearchParams()
  for (const key of ['status', 'registrationType', 'turnusCode']) if (filters[key]) params.set(key, filters[key])
  const query = params.toString()
  return request(`${base}${query ? `?${query}` : ''}`, { token })
}
export const updateRegistrationStatus = (token, code, payload) => request(`${base}/${encodeURIComponent(code)}/status`, { token, method: 'PATCH', payload })
export const fetchRegistrationDetail = (token, code) => request(`${base}/${encodeURIComponent(code)}`, { token })
export const updateRegistrationData = (token, code, payload) => request(`${base}/${encodeURIComponent(code)}/data`, { token, method: 'PATCH', payload })
export async function fetchTurnusStats(token, turnusCode) {
  const body = await request(`${base}/stats/${encodeURIComponent(turnusCode)}`, { token })
  if (!body?.participants || !body?.staff || !validStaffingStatistics(body))
    throw new Error('Biuro zwróciło nieprawidłowe statystyki. Spróbuj ponownie.')
  return body
}
