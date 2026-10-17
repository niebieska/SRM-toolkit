import { request } from './http.js'
const base = '/api/admin/accounts'
export const fetchAccounts = token => request(base, { token })
export const fetchAccountTurnuses = token => request(`${base}/turnus-options`, { token })
export const createAccount = (token, payload) => request(base, { token, method: 'POST', payload })
export const updateAccount = (token, id, payload) => request(`${base}/${encodeURIComponent(id)}`, { token, method: 'PATCH', payload })
export const resetAccountPassword = (token, id, version, password) => request(`${base}/${encodeURIComponent(id)}/password`, {
  token, method: 'POST', payload: { version, password },
})
export const fetchAccountAudit = (token, id) => request(`${base}/${encodeURIComponent(id)}/audit?limit=100`, { token })
