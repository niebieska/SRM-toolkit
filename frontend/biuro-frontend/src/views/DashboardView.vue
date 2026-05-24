<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import RegistrationTable from '../components/RegistrationTable.vue'

const authStore = useAuthStore()
const router = useRouter()

const filters = reactive({
  status: '',
  registrationType: '',
  turnusCode: '',
})

async function logout() {
  authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <main class="min-h-screen p-4 md:p-8">
    <div class="max-w-6xl mx-auto space-y-4">
      <header class="flex items-center justify-between gap-3">
        <h1 class="text-2xl font-semibold">Panel Biuro</h1>
        <button class="rounded bg-slate-700 text-white px-4 py-2 hover:bg-slate-800" @click="logout">Wyloguj</button>
      </header>

      <section class="bg-white rounded-xl shadow p-4 grid grid-cols-1 md:grid-cols-3 gap-3">
        <select v-model="filters.status" class="rounded border border-slate-300 px-3 py-2">
          <option value="">Wszystkie statusy</option>
          <option value="NEW">Nowe</option>
          <option value="ACCEPTED">Zaakceptowane</option>
          <option value="REJECTED">Odrzucone</option>
        </select>

        <select v-model="filters.registrationType" class="rounded border border-slate-300 px-3 py-2">
          <option value="">Wszystkie typy</option>
          <option value="PARTICIPANT">Uczestnik</option>
          <option value="STAFF">Kadra</option>
        </select>

        <input
          v-model="filters.turnusCode"
          class="rounded border border-slate-300 px-3 py-2"
          placeholder="Kod turnusu"
        />
      </section>

      <RegistrationTable :filters="filters" />
    </div>
  </main>
</template>
