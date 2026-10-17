<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import BiuroHeader from '../components/BiuroHeader.vue'
const auth = useAuthStore(), router = useRouter(), busy = ref(false)
async function retry() {
  if (busy.value) return
  busy.value = true
  auth.initialized = false
  try { await auth.initialize(); await router.replace(auth.isAuthenticated ? auth.home : '/login') }
  catch (e) { auth.sessionMessage = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <main class="min-h-screen p-4 md:p-8"><div class="mx-auto max-w-6xl space-y-6">
    <BiuroHeader />
    <section class="space-y-4 rounded-xl border bg-white p-6">
      <h2 class="text-xl font-semibold">Dostęp do Biura</h2>
      <p role="alert">{{ auth.sessionMessage || 'Brak uprawnień do tej części systemu. Skontaktuj się z administratorem.' }}</p>
      <button :disabled="busy" class="rounded border px-4 py-2 disabled:opacity-50" @click="retry">{{ busy ? 'Sprawdzanie…' : 'Sprawdź ponownie' }}</button>
    </section>
  </div></main>
</template>
