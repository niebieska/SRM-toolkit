import { defineStore } from 'pinia'

const TOKEN_KEY = 'biuro_token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY),
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
  },
  actions: {
    login(token) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
    },
    logout() {
      this.token = null
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})
