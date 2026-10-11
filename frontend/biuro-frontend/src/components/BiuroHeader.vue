<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const props = defineProps({ unsavedChanges: Boolean, busy: Boolean })
const auth = useAuthStore()
const router = useRouter()
async function logout() {
  if (props.busy || (props.unsavedChanges && !window.confirm('Wylogowanie usunie niezapisane zmiany. Kontynuować?'))) return
  auth.logout()
  await router.push('/login')
}
</script>

<template>
  <header class="space-y-4">
    <div class="flex items-center justify-between gap-3">
      <h1 class="text-2xl font-semibold">Panel Biuro</h1>
      <button class="rounded bg-slate-700 text-white px-4 py-2 hover:bg-slate-800 disabled:opacity-50" :disabled="busy" @click="logout">Wyloguj</button>
    </div>
    <nav aria-label="Główna nawigacja" class="flex gap-1 border-b border-slate-300">
      <RouterLink to="/dashboard" class="px-4 py-3 border-b-2 text-sm font-medium"
        :class="$route.path === '/dashboard' ? 'border-slate-800 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-900'">Zgłoszenia</RouterLink>
      <RouterLink to="/turnusy" class="px-4 py-3 border-b-2 text-sm font-medium"
        :class="$route.path.startsWith('/turnusy') ? 'border-slate-800 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-900'">Turnusy</RouterLink>
    </nav>
  </header>
</template>
