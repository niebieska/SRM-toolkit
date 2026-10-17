import { defineStore } from 'pinia'
import { fetchProfile, logoutRequest } from '../api/authApi.js'
import { hasRole, homeFor, tokenExpiry } from '../utils/accountAccess.js'
const TOKEN_KEY = 'biuro_token'
const initializations = new WeakMap()
export const useAuthStore = defineStore('auth', {
  state: () => ({ token: localStorage.getItem(TOKEN_KEY), user: null, initialized: false,
    expired: false, previousUsername: null, screenVersion: 0, sessionVersion: 0, sessionMessage: '' }),
  getters: {
    isAuthenticated: state => !!state.token && !!state.user && !state.expired,
    isOperator: state => hasRole(state.user, 'OPERATOR'),
    isAdmin: state => hasRole(state.user, 'SYS_ADMIN'),
    isViewer: state => hasRole(state.user, 'VIEWER'),
    home: state => homeFor(state.user),
  },
  actions: {
    async initialize() {
      if (this.initialized) return
      if (initializations.has(this)) return initializations.get(this)
      const initialization = (async () => {
        try {
          if (!this.token) return
          if (tokenExpiry(this.token) <= Date.now()) { this.clear(); this.sessionMessage = 'Sesja wygasła. Zaloguj się ponownie.'; return }
          const token = this.token
          const user = await fetchProfile(token)
          if (this.token === token) { this.user = user; this.sessionMessage = '' }
        } finally { this.initialized = true }
      })()
      initializations.set(this, initialization)
      try { return await initialization } finally { initializations.delete(this) }
    },
    login(response) {
      this.sessionVersion++
      const differentUser = this.previousUsername && this.previousUsername !== response.user.username
      if (differentUser) this.screenVersion++
      this.token = response.token; this.user = response.user
      this.expired = false; this.previousUsername = null; this.sessionMessage = ''; this.initialized = true
      localStorage.setItem(TOKEN_KEY, response.token)
      return Boolean(differentUser)
    },
    expire(token) {
      if (token !== this.token) return
      this.previousUsername = this.user?.username || null
      this.token = null; this.expired = true
      localStorage.removeItem(TOKEN_KEY)
      this.sessionMessage = 'Sesja wygasła lub została zakończona. Zaloguj się ponownie. Zapis nie zostanie ponowiony automatycznie.'
    },
    clear() {
      this.sessionVersion++
      this.token = null; this.user = null; this.expired = false; this.previousUsername = null; this.screenVersion++
      localStorage.removeItem(TOKEN_KEY)
    },
    async logout() {
      const token = this.token
      const version = this.sessionVersion
      // Keep the session on a connection failure so logout can be retried.
      if (token) {
        try { await logoutRequest(token) } catch (error) {
          if (version !== this.sessionVersion) return false
          if (error.status !== 401) throw error
        }
      }
      // Expiry may happen while waiting, but a newer login/clear owns its state.
      if (version !== this.sessionVersion) return false
      this.clear()
      return true
    },
  },
})
