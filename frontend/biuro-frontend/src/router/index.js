import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import TurnusListView from '../views/TurnusListView.vue'
import TurnusEditorView from '../views/TurnusEditorView.vue'
import TurnusListsView from '../views/TurnusListsView.vue'
import PasswordView from '../views/PasswordView.vue'
import AccountsView from '../views/AccountsView.vue'
import ViewerTurnusesView from '../views/ViewerTurnusesView.vue'
import SessionCheckView from '../views/SessionCheckView.vue'
import { useAuthStore } from '../stores/auth.js'
import { fetchProfile } from '../api/authApi.js'
import { canVisit } from '../utils/accountAccess.js'
const operator = { requiresAuth: true, roles: ['OPERATOR'] }
const lists = { requiresAuth: true, roles: ['OPERATOR', 'VIEWER'] }
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    { path: '/sesja', component: SessionCheckView },
    { path: '/haslo', component: PasswordView, meta: { requiresAuth: true } },
    { path: '/konta', component: AccountsView, meta: { requiresAuth: true, roles: ['SYS_ADMIN'] } },
    { path: '/listy', component: ViewerTurnusesView, meta: lists },
    { path: '/brak-dostepu', component: SessionCheckView, meta: { requiresAuth: true } },
    { path: '/dashboard', component: DashboardView, meta: operator },
    { path: '/turnusy', name: 'turnus-list', component: TurnusListView, meta: operator },
    { path: '/turnusy/nowy', name: 'turnus-create', component: TurnusEditorView, meta: operator },
    { path: '/turnusy/:code', name: 'turnus-detail', component: TurnusEditorView, meta: operator },
    { path: '/turnusy/:code/listy', name: 'turnus-lists', component: TurnusListsView, meta: lists },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
router.beforeEach(async to => {
  const auth = useAuthStore()
  if (to.path === '/sesja') return true
  try {
    const alreadyInitialized = auth.initialized
    await auth.initialize()
    if (alreadyInitialized && auth.token && auth.user && !auth.expired) {
      const token = auth.token
      const user = await fetchProfile(token)
      if (token === auth.token) { auth.user = user; auth.sessionMessage = '' }
    }
  } catch (error) {
    if (error.status !== 401) { auth.sessionMessage = error.message; return '/sesja' }
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) return '/login'
  if (!auth.isAuthenticated) return true
  if (auth.user.passwordChangeRequired && to.path !== '/haslo') return '/haslo'
  if (to.path === '/login' || !canVisit(auth.user, to)) return auth.home
  return true
})
export default router
