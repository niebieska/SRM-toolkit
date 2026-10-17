<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
const props = defineProps({ unsavedChanges: Boolean, busy: Boolean, confirmDiscard: Function })
const auth = useAuthStore(), router = useRouter(), leaving = ref(false), error = ref('')
async function logout() {
  if (props.busy || leaving.value) return
  if (props.unsavedChanges) {
    const confirmed = props.confirmDiscard ? await props.confirmDiscard('logout')
      : window.confirm('Wylogowanie usunie niezapisane zmiany. Kontynuować?')
    if (!confirmed) return
  }
  leaving.value = true; error.value = ''
  try { if (await auth.logout()) await router.push('/login') }
  catch (e) { error.value = e.message }
  finally { leaving.value = false }
}
</script>
<template>
  <header class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-2xl font-semibold">Panel Biuro</h1>
      <div class="flex flex-wrap items-center gap-3 text-sm">
        <span>{{ auth.user?.displayName }}</span>
        <RouterLink v-if="auth.isAuthenticated" to="/haslo" class="rounded underline">Zmień hasło</RouterLink>
        <button class="rounded bg-slate-700 px-4 py-2 text-white hover:bg-slate-800 disabled:opacity-50" :disabled="busy || leaving" @click="logout">{{ leaving ? 'Wylogowanie…' : 'Wyloguj' }}</button>
      </div>
    </div>
    <p v-if="error" role="alert" class="text-sm text-red-700">{{ error }}</p>
    <nav aria-label="Główna nawigacja" class="flex flex-wrap gap-1 border-b border-slate-300">
      <RouterLink v-if="auth.isOperator" to="/dashboard" class="border-b-2 px-4 py-3 text-sm font-medium" :class="$route.path === '/dashboard' ? 'border-slate-800' : 'border-transparent text-slate-500'">Zgłoszenia</RouterLink>
      <RouterLink v-if="auth.isOperator" to="/turnusy" class="border-b-2 px-4 py-3 text-sm font-medium" :class="$route.path.startsWith('/turnusy') ? 'border-slate-800' : 'border-transparent text-slate-500'">Turnusy</RouterLink>
      <RouterLink v-if="auth.isViewer && !auth.isOperator" to="/listy" class="border-b-2 px-4 py-3 text-sm font-medium" :class="$route.path === '/listy' || $route.name === 'turnus-lists' ? 'border-slate-800' : 'border-transparent text-slate-500'">Listy turnusów</RouterLink>
      <RouterLink v-if="auth.isAdmin" to="/konta" class="border-b-2 px-4 py-3 text-sm font-medium" :class="$route.path === '/konta' ? 'border-slate-800' : 'border-transparent text-slate-500'">Konta</RouterLink>
    </nav>
  </header>
</template>
