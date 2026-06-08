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

const turnusOptions = ['ZAGLE26T1', 'ZAGLE26T2']

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
          <option v-for="code in turnusOptions" :key="code" :value="code" >
            {{ code }}
          </option>
        </select>
         <input
             v-model="filters.search"
             class="rounded border border-slate-300 px-3 py-2"
             placeholder="Wyszukaj"
         />
      </section>
      <section
          v-if="stats"
          class="bg-white rounded-xl shadow p-4"
      >
        <h2 class="font-semibold mb-3">
          Statystyki {{ stats.turnusCode }}
        </h2>

        <div class="grid grid-cols-3 gap-4 text-sm">
          <div>
            <div class="text-slate-500">Miejsca</div>
            <div class="font-semibold">
              {{ stats.occupiedPlaces }} / {{ stats.occupiedPlaces + stats.availablePlaces }}
            </div>
            <div class="text-xs text-slate-500">
              Wolne: {{ stats.availablePlaces }}
            </div>
          </div>

          <div>
            <div class="text-slate-500">Przyjęci</div>
            <div>
              ♀ {{ stats.acceptedFemale }}
              | ♂ {{ stats.acceptedMale }}
            </div>
          </div>

          <div>
            <div class="text-slate-500">Lista rezerwowa</div>
            <div>
              ♀ {{ stats.waitlistFemale }}
              | ♂ {{ stats.waitlistMale }}
            </div>
          </div>
        </div>
      </section>
      <RegistrationTable :filters="filters"/>
    </div>
  </main>
</template>
