import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import KadraFormView from '../forms/kadra/KadraFormView.vue'
import UczestnikFormView from '../forms/uczestnik/UczestnikFormView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/kadra', component: KadraFormView },
    { path: '/uczestnik', component: UczestnikFormView },
  ],
})

export default router
