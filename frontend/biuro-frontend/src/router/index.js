import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import TurnusListView from '../views/TurnusListView.vue'
import TurnusEditorView from '../views/TurnusEditorView.vue'
import TurnusListsView from '../views/TurnusListsView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true } },
    { path: '/turnusy', name: 'turnus-list', component: TurnusListView, meta: { requiresAuth: true } },
    { path: '/turnusy/nowy', name: 'turnus-create', component: TurnusEditorView, meta: { requiresAuth: true } },
    { path: '/turnusy/:code', name: 'turnus-detail', component: TurnusEditorView, meta: { requiresAuth: true } },
    { path: '/turnusy/:code/listy', name: 'turnus-lists', component: TurnusListsView, meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return '/login'
  }
  if (to.path === '/login' && authStore.isAuthenticated) {
    return '/dashboard'
  }
  return true
})

export default router
