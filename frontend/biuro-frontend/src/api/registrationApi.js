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
export const fetchPaymentRegister = token => request(`${base}/payments`, { token })
export const addRegistrationPayment = (token, code, payload, idempotencyKey) => request(`${base}/${encodeURIComponent(code)}/payments`, {
  token, method: 'POST', payload, headers: { 'Idempotency-Key': idempotencyKey },
})
export const editRegistrationPayment = (token, code, paymentId, payload) => request(`${base}/${encodeURIComponent(code)}/payments/${encodeURIComponent(paymentId)}`, { token, method: 'PUT', payload })
export const setRegistrationDiscount = (token, code, payload) => request(`${base}/${encodeURIComponent(code)}/discount`, { token, method: 'PUT', payload })
export async function fetchTurnusStats(token, turnusCode) {
  const body = await request(`${base}/stats/${encodeURIComponent(turnusCode)}`, { token })
  if (!body?.participants || !body?.staff || !validStaffingStatistics(body))
    throw new Error('Biuro zwróciło nieprawidłowe statystyki. Spróbuj ponownie.')
  return body
}
