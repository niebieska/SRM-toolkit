<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import RegistrationTable from '../components/RegistrationTable.vue'
import BiuroHeader from '../components/BiuroHeader.vue'
import StaffingStatistics from '../components/StaffingStatistics.vue'
import { visibleStaffingRoles } from '../utils/staffingStatistics.js'
import { fetchTurnuses } from '../api/turnusApi'
import { fetchTurnusStats } from '../api/registrationApi'

const authStore = useAuthStore()

const stats = ref(null)

const filters = reactive({
  status: '',
  registrationType: '',
  turnusCode: '',
  search: '',
})

const turnusOptions = ref([])
const catalogueError = ref('')
const statsError = ref('')
const statsLoading = ref(false)
let statsRequest = 0
onMounted(async () => {
  try { turnusOptions.value = await fetchTurnuses(authStore.token) }
  catch (e) { catalogueError.value = e.message }
})
const visibleStats = computed(() => !stats.value ? [] : [
  { type: 'PARTICIPANT', label: 'Uczestnicy', ...stats.value.participants },
  { type: 'STAFF', label: 'Kadra', ...stats.value.staff },
].filter(item => !filters.registrationType || filters.registrationType === item.type))

const staffingRoles = computed(() => visibleStaffingRoles(stats.value, filters.registrationType))
onBeforeUnmount(() => { statsRequest++ })

const hasActiveFilters = computed(() =>
  Boolean(filters.status || filters.registrationType || filters.turnusCode || filters.search)
)

function clearFilters() {
  filters.status = ''
  filters.registrationType = ''
  filters.turnusCode = ''
  filters.search = ''
}

async function loadTurnusStats() {
  const turnusCode = filters.turnusCode
  const request = ++statsRequest
  stats.value = null
  statsError.value = ''
  statsLoading.value = Boolean(turnusCode)
  if (!turnusCode) return
  try {
    const value = await fetchTurnusStats(authStore.token, turnusCode)
    if (request === statsRequest) stats.value = value
  } catch (e) {
    if (request === statsRequest) statsError.value = e.message
  } finally {
    if (request === statsRequest) statsLoading.value = false
  }
}

watch(
    () => filters.turnusCode,
    (turnusCode) => loadTurnusStats(turnusCode)
)

</script>

<template>
  <main class="min-h-screen p-4 md:p-8">
    <div class="max-w-6xl mx-auto space-y-4">
      <BiuroHeader />

       <section class="bg-white rounded-xl shadow p-4 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-[repeat(4,minmax(0,1fr))_auto] gap-3">
        <select aria-label="Status zgłoszenia" v-model="filters.status" class="min-w-0 rounded border border-slate-300 px-3 py-2">
          <option value="">Wszystkie statusy</option>
          <option value="NEW">Nowe</option>
          <option value="ACCEPTED">Zaakceptowane</option>
          <option value="WAITLIST">Lista rezerwowa</option>
          <option value="REJECTED">Odrzucone</option>
        </select>

        <select aria-label="Typ zgłoszenia" v-model="filters.registrationType" class="min-w-0 rounded border border-slate-300 px-3 py-2">
          <option value="">Wszystkie typy</option>
          <option value="PARTICIPANT">Uczestnik</option>
          <option value="STAFF">Kadra</option>
        </select>
        <select aria-label="Turnus" v-model="filters.turnusCode" class="min-w-0 rounded border border-slate-300 px-3 py-2">
          <option value="">Wszystkie turnusy</option>
          <option
              v-for="turnus in turnusOptions"
              :key="turnus.turnusCode"
              :value="turnus.turnusCode"
          >
            {{ turnus.turnusName }}
          </option>
        </select>
         <div class="relative min-w-0">
           <input
               aria-label="Wyszukaj zgłoszenia"
               v-model="filters.search"
               class="w-full rounded border border-slate-300 px-3 py-2 pr-9"
               placeholder="Wyszukaj"
           />
           <button
               v-if="filters.search"
               type="button"
               class="absolute inset-y-0 right-2 my-auto h-6 w-6 rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-800"
               aria-label="Wyczyść wyszukiwanie"
               @click="filters.search = ''"
           >
             ×
           </button>
         </div>
         <div class="md:col-span-2 lg:col-span-1 flex justify-end">
           <button
               type="button"
               class="whitespace-nowrap rounded border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
               :disabled="!hasActiveFilters"
               @click="clearFilters"
           >
             Wyczyść filtry
           </button>
         </div>
      </section>
      <p v-if="catalogueError" role="alert" class="rounded bg-red-50 text-red-800 p-3">Nie udało się wczytać filtrów turnusów: {{ catalogueError }}</p>
      <p v-if="statsError" role="alert" class="rounded bg-red-50 text-red-800 p-3">{{ statsError }}</p>
      <p v-if="statsLoading" role="status" class="text-slate-500">Wczytywanie statystyk…</p>
      <section v-for="item in visibleStats" :key="item.type" class="bg-white rounded-xl shadow p-3">
        <div class="flex flex-wrap items-center gap-6 text-sm">
          <div>
            <span class="text-slate-500">Turnus:</span>
            <span class="font-medium ml-1">{{ stats.turnusCode }} · {{ item.label }}</span>
          </div>
          <div>
            <span class="text-slate-500">Zajęte miejsca:</span>
            <span class="font-medium ml-1" :class="item.accepted >= item.capacity ? 'text-amber-800' : ''">{{ item.accepted }}/{{ item.capacity }}</span>
          </div>
          <div>
            <span class="text-slate-500">{{ item.type === 'PARTICIPANT' ? 'Przyjęci uczestnicy:' : 'Przyjęta kadra:' }}</span>
            <span class="font-medium ml-1">{{ item.accepted }}</span>
            <span class="text-slate-400 ml-1">(Kobiety: {{ item.acceptedFemale }}, Mężczyźni: {{ item.acceptedMale }})</span>
          </div>
          <div>
            <span class="text-slate-500">Lista rezerwowa:</span>
            <span class="font-medium ml-1">{{ item.waitlist }}</span>
            <span class="text-slate-400 ml-1">(Kobiety: {{ item.waitlistFemale }}, Mężczyźni: {{ item.waitlistMale }})</span>
          </div>
          <div v-if="item.overCapacity" class="font-medium text-red-700">Ponad limit: {{ item.overCapacity }}</div>
        </div>
        <StaffingStatistics
          v-if="item.type === 'STAFF'"
          :roles="staffingRoles"
          :unmapped="stats?.unmappedAcceptedStaff || 0"
        />
      </section>
      <RegistrationTable :filters="filters" @status-updated="loadTurnusStats"/>
    </div>
  </main>
</template>
