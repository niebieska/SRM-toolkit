<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import RegistrationTable from '../components/RegistrationTable.vue'
import { fetchTurnusStats } from '../api/registrationApi'

const authStore = useAuthStore()
const router = useRouter()

const stats = ref(null)

const filters = reactive({
  status: '',
  registrationType: '',
  turnusCode: '',
  search: '',
})

const turnusOptions = [
  {
    code: 'ZAGLE26T1',
    name: 'Żagle 2026 - Turnus I',
    dates: '01.08.2026 - 11.08.2026',
  },
  {
    code: 'ZAGLE26T2',
    name: 'Żagle 2026 - Turnus II',
    dates: '11.08.2026 - 21.08.2026',
  },
]

watch(
    () => filters.turnusCode,
    async (turnusCode) => {
      console.log('turnus changed:', turnusCode)

      if (!turnusCode) {
        stats.value = null
        return
      }

      try {
        const result = await fetchTurnusStats(authStore.token, turnusCode)
        console.log('stats result:', result)
        stats.value = result
      } catch (e) {
        console.error('stats error:', e)
        stats.value = null
      }
    }
)

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

       <section class="bg-white rounded-xl shadow p-4 grid grid-cols-1 md:grid-cols-4 gap-3">
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
        <select v-model="filters.turnusCode" class="rounded border border-slate-300 px-3 py-2">
          <option value="">Wszystkie turnusy</option>
          <option
              v-for="turnus in turnusOptions"
              :key="turnus.code"
              :value="turnus.code"
          >
            {{ turnus.name }}
          </option>
        </select>
         <input
             v-model="filters.search"
             class="rounded border border-slate-300 px-3 py-2"
             placeholder="Wyszukaj"
         />
      </section>
      <section  v-if="stats" class="bg-white rounded-xl shadow p-3">
        <div class="flex flex-wrap items-center gap-6 text-sm">

          <div>
            <span class="text-slate-500">Turnus:</span>
            <span class="font-medium ml-1">{{ stats.turnusCode }}</span>
          </div>

          <div>
            <span class="text-slate-500">Zajęte miejsca:</span>
            <span class="font-medium ml-1">
        {{ stats.occupiedPlaces }}/{{ stats.occupiedPlaces + stats.availablePlaces }}
      </span>
          </div>

          <div>
            <span class="text-slate-500">Przyjęci uczestnicy:</span>
            <span class="font-medium ml-1">{{ stats.accepted }}</span>
            <span class="text-slate-400 ml-1">
        (Kobiety: {{ stats.acceptedFemale }}, Mężczyźni: {{ stats.acceptedMale }})
      </span>
          </div>

          <div>
            <span class="text-slate-500">Lista rezerwowa:</span>
            <span class="font-medium ml-1">{{ stats.waitlist }}</span>
            <span class="text-slate-400 ml-1">
        (Kobiety: {{ stats.waitlistFemale }}, Mężczyźni: {{ stats.waitlistMale }})
      </span>
          </div>

        </div>
      </section>
      <RegistrationTable :filters="filters"/>
    </div>
  </main>
</template>
