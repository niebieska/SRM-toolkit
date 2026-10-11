<script setup>
import { computed, onMounted, ref } from 'vue'
import BiuroHeader from '../components/BiuroHeader.vue'
import { useAuthStore } from '../stores/auth'
import { fetchTurnuses } from '../api/turnusApi'
import { sortTurnuses } from '../utils/turnusSorting'
import { matchesTurnusSearch, formatTurnusDate as date } from '../utils/turnusSearch'
const auth = useAuthStore()
const turnuses = ref([])
const loading = ref(false)
const error = ref('')
const search = ref('')
const state = ref('')
const sortKey = ref('startDate')
const sortDirection = ref('asc')
const columns = [
  { key: 'turnusName', label: 'Turnus' },
  { key: 'startDate', label: 'Termin' },
  { key: 'turnusLocation', label: 'Miejsce' },
  { key: 'capacity', label: 'Liczba miejsc' },
  { key: 'availability', label: 'Zapisy' },
]
function toggleSort(key) {
  if (sortKey.value === key) sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  else { sortKey.value = key; sortDirection.value = 'asc' }
}
function ariaSort(key) {
  return sortKey.value === key ? (sortDirection.value === 'asc' ? 'ascending' : 'descending') : 'none'
}
const filtered = computed(() => turnuses.value.filter(t => {
  const matches = matchesTurnusSearch(t, search.value)
  return matches && (!state.value || (state.value === 'OPEN' ? t.active && t.registrationOpen
    : state.value === 'CLOSED' ? t.active && !t.registrationOpen : !t.active))
}))
const sorted = computed(() => sortTurnuses(filtered.value, sortKey.value, sortDirection.value))
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try { turnuses.value = await fetchTurnuses(auth.token) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <main class="min-h-screen p-4 md:p-8">
    <div class="max-w-6xl mx-auto space-y-6">
      <BiuroHeader />
      <section class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div class="p-4 flex flex-wrap items-center gap-3 border-b border-slate-200">
          <label class="w-full sm:w-56 sm:shrink-0"><span class="sr-only">Stan turnusu</span><select v-model="state" class="block h-10 w-full rounded-lg border border-slate-300 px-3 py-2"><option value="">Wszystkie turnusy</option><option value="OPEN">Zapisy otwarte</option><option value="CLOSED">Zapisy zamknięte</option><option value="ARCHIVED">Nieaktywne</option></select></label>
          <div class="relative w-full sm:w-56 sm:shrink-0">
            <label><span class="sr-only">Wyszukaj turnus po nazwie, kodzie, miejscu lub dacie</span><input v-model="search" placeholder="Wyszukaj" class="block h-10 w-full rounded-lg border border-slate-300 px-3 py-2 pr-9" /></label>
            <button v-if="search" type="button" class="absolute inset-y-0 right-2 my-auto h-6 w-6 rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-800" aria-label="Wyczyść wyszukiwanie" @click="search = ''">×</button>
          </div>
          <RouterLink to="/turnusy/nowy" class="sm:ml-auto whitespace-nowrap rounded-lg bg-slate-800 text-white px-4 py-2 hover:bg-slate-900">+ Dodaj turnus</RouterLink>
        </div>
        <p v-if="error" role="alert" class="m-4 rounded-lg bg-red-50 text-red-800 p-4">{{ error }}</p>
        <p v-else-if="loading" role="status" class="p-8 text-center text-slate-500">Wczytywanie turnusów…</p>
        <template v-else>
          <div v-if="filtered.length" class="overflow-x-auto">
            <table class="w-full text-sm text-left">
              <thead class="bg-slate-50 text-slate-500"><tr>
                <th v-for="column in columns" :key="column.key" scope="col" :aria-sort="ariaSort(column.key)" class="px-5 py-3">
                  <button type="button" class="inline-flex items-center gap-1.5 rounded hover:text-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-700"
                    :title="`Sortuj: ${column.label} — ${sortKey === column.key && sortDirection === 'asc' ? 'malejąco' : 'rosnąco'}`" @click="toggleSort(column.key)">
                    {{ column.label }}<span aria-hidden="true" class="inline-block w-3 text-xs text-slate-500">{{ sortKey === column.key ? (sortDirection === 'asc' ? '↑' : '↓') : '' }}</span>
                  </button>
                </th>
                <th class="px-5 py-3"><span class="sr-only">Szczegóły</span></th>
              </tr></thead>
              <tbody class="divide-y divide-slate-100"><tr v-for="t in sorted" :key="t.turnusCode" class="hover:bg-slate-50">
                <td class="px-5 py-4"><RouterLink :to="`/turnusy/${encodeURIComponent(t.turnusCode)}`" class="font-semibold text-slate-900 hover:underline">{{ t.turnusName }}</RouterLink><div class="text-xs text-slate-500 mt-1">{{ t.turnusCode }} · {{ t.seasonType === 'WINTER' ? 'Zima' : 'Lato' }} {{ t.seasonYear }}</div></td>
                <td class="px-5 py-4 whitespace-nowrap">{{ date(t.startDate) }} – {{ date(t.endDate) }}</td><td class="px-5 py-4">{{ t.turnusLocation }}</td><td class="px-5 py-4 whitespace-nowrap">{{ t.capacity }} osób</td>
                <td class="px-5 py-4"><span class="inline-flex whitespace-nowrap rounded-full px-3 py-1 text-xs font-medium" :class="!t.active ? 'bg-slate-100 text-slate-500' : t.registrationOpen ? 'bg-emerald-50 text-emerald-800' : 'bg-amber-50 text-amber-800'">{{ !t.active ? 'Nieaktywny' : t.registrationOpen ? 'Otwarte' : 'Zamknięte' }}</span></td>
                <td class="px-5 py-4"><RouterLink :to="`/turnusy/${encodeURIComponent(t.turnusCode)}`" class="inline-block rounded bg-slate-600 text-white px-3 py-1 hover:bg-slate-700" :aria-label="`Szczegóły turnusu ${t.turnusName}`">Szczegóły</RouterLink></td>
              </tr></tbody>
            </table>
          </div>
          <p v-else class="p-10 text-center text-slate-500">{{ turnuses.length ? 'Brak turnusów pasujących do filtrów.' : 'Brak turnusów. Dodaj pierwszy turnus.' }}</p>
          <p class="px-5 py-3 text-xs text-slate-500 border-t border-slate-200">{{ filtered.length }} z {{ turnuses.length }} turnusów</p>
        </template>
      </section>
    </div>
  </main>
</template>
