import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
import { canVisit } from './utils/accountAccess.js'
import './style.css'
import { onUnauthorized, onForbidden } from './api/http.js'
import { useAuthStore } from './stores/auth.js'
import { fetchProfile } from './api/authApi.js'

const app = createApp(App)
app.use(createPinia())
onUnauthorized(token => useAuthStore().expire(token))
onForbidden(async token => {
  const auth = useAuthStore()
  if (token !== auth.token) return
  const user = await fetchProfile(token)
  if (token !== auth.token) return
  auth.user = user
  if (user.passwordChangeRequired || !canVisit(user, router.currentRoute.value)) await router.replace(auth.home)
})
app.use(router)
app.mount('#app')
