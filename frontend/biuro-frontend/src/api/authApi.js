import { request } from './http.js'
export const loginRequest = (username, password) => request('/api/auth/login', {
  method: 'POST', payload: { username, password }, session: false,
})
export const fetchProfile = token => request('/api/auth/me', { token })
export const changePassword = (token, currentPassword, newPassword) => request('/api/auth/password', {
  token, method: 'POST', payload: { currentPassword, newPassword },
})
export const logoutRequest = token => request('/api/auth/logout', { token, method: 'POST', session: false })
