<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import { useAuthStore } from '../stores/auth'
import { fetchTurnusLists, fetchViewerLists } from '../api/turnusApi'
import { availableListColumns, columnsForList, customColumnGroups, filterAndSortList, listColumns, listValue, listViews, validateTurnusLists } from '../utils/turnusLists.js'
import { downloadTurnusList, PdfLayoutError } from '../utils/turnusListExport.js'

const route = useRoute(), auth = useAuthStore()
const data = ref(null), error = ref(''), loading = ref(false)
const search = ref(''), group = ref('')
const sortKey = ref('lastName'), direction = ref('asc')
const selectedView = ref('general')
const customColumns = ref(listColumns.map(column => column.key))
const exportFormat = ref('xlsx'), exporting = ref(false), exportError = ref(''), exportSuccess = ref('')
let exportRequest = 0
let sequence = 0
const entries = computed(() => data.value?.entries || [])
const groups = computed(() => [
  { type: 'PARTICIPANT', label: 'Uczestnicy' }, { type: 'STAFF', label: 'Kadra' },
].filter(item => !group.value || item.type === group.value).map(item => ({
  ...item,
  total: entries.value.filter(e => e.person.registrationType === item.type).length,
  rows: filterAndSortList(entries.value, { type: item.type, search: search.value,
    sortKey: sortKey.value, direction: direction.value }),
  columns: columnsForList(selectedView.value, customColumns.value, item.type),
})))
const visibleCustomColumnGroups = computed(() => customColumnGroups.filter(item =>
  item.key === 'common'
  || (item.key === 'staff' && group.value !== 'PARTICIPANT')))
const incompleteCount = computed(() => entries.value.filter(e => e.person.incompleteData).length)
const exportCount = computed(() => groups.value.reduce((sum, item) => sum + (item.columns.length ? item.rows.length : 0), 0))
const filtered = computed(() => Boolean(search.value || group.value))
function clearFilters() { search.value = ''; group.value = '' }
function selectView(value) {
  const definition = listViews.find(item => item.key === value)
  if (!definition || value === selectedView.value) return
  if (value === 'custom') {
    customColumns.value = columnsForList(selectedView.value, customColumns.value, 'PARTICIPANT').map(column => column.key)
  } else {
    sortKey.value = definition.sortKey
    direction.value = 'asc'
  }
  selectedView.value = value
}
function setColumn(key, selected) {
  if (!availableListColumns.some(column => column.key === key)) return
  const keys = new Set(customColumns.value)
  if (selected) keys.add(key)
  else keys.delete(key)
  customColumns.value = availableListColumns.filter(column => keys.has(column.key)).map(column => column.key)
  if (!customColumns.value.includes(sortKey.value) && sortKey.value !== 'roleLabel') {
    sortKey.value = customColumns.value.includes('lastName') ? 'lastName' : customColumns.value[0] || 'lastName'
    direction.value = 'asc'
  }
}
function sort(key) {
  if (sortKey.value === key) direction.value = direction.value === 'asc' ? 'desc' : 'asc'
  else { sortKey.value = key; direction.value = 'asc' }
}
async function exportList() {
  if (exporting.value || !data.value || !exportCount.value) return
  const request = ++exportRequest
  const code = String(route.params.code)
  exporting.value = true; exportError.value = ''; exportSuccess.value = ''
  const format = exportFormat.value
  try {
    const downloaded = await downloadTurnusList({ turnus: data.value.turnus, groups: groups.value,
      viewKey: selectedView.value, viewLabel: listViews.find(view => view.key === selectedView.value).label },
    format, () => auth.isAuthenticated && request === exportRequest && String(route.params.code) === code)
    if (downloaded && request === exportRequest) exportSuccess.value = `Przygotowano plik ${format.toUpperCase()}.`
  } catch (exception) {
    if (request === exportRequest) exportError.value = exception instanceof PdfLayoutError
      ? exception.message : 'Nie udało się przygotować pliku. Spróbuj ponownie.'
  } finally {
    if (request === exportRequest) exporting.value = false
  }
}
async function load() {
  const request = ++sequence, code = String(route.params.code)
  data.value = null; error.value = ''; loading.value = true
  try {
    const result = await (auth.isOperator ? fetchTurnusLists : fetchViewerLists)(auth.token, code)
    if (!validateTurnusLists(result, code)) throw new Error('Biuro zwróciło nieprawidłową listę turnusu.')
    if (request === sequence) data.value = result
  } catch (exception) {
    if (request === sequence) error.value = exception.message
  } finally {
    if (request === sequence) loading.value = false
  }
}
watch(() => route.params.code, () => {
  exportRequest++; exporting.value = false; exportError.value = ''; exportSuccess.value = ''
  clearFilters()
  selectedView.value = 'general'
  customColumns.value = listColumns.map(column => column.key)
  sortKey.value = 'lastName'; direction.value = 'asc'
  load()
}, { immediate: true })
onBeforeUnmount(() => { sequence++; exportRequest++ })
function displayDate(value) {
  if (!value) return '—'
  return value.split('-').reverse().join('.')
}
</script>

<template>
  <main class="min-h-screen bg-slate-100 px-4 py-5 sm:px-6 lg:px-8">
    <div class="mx-auto max-w-6xl space-y-5">
      <BiuroHeader />
      <nav aria-label="Ścieżka nawigacji" class="text-sm text-slate-600">
        <RouterLink :to="auth.isOperator ? '/turnusy' : '/listy'" class="rounded hover:text-slate-900 focus-visible:ring-2 focus-visible:ring-slate-500">Turnusy</RouterLink>
        <span aria-hidden="true" class="mx-2">/</span><span aria-current="page">Listy</span>
      </nav>
      <header class="space-y-1">
        <h2 class="min-w-0 break-words text-xl font-semibold text-slate-900">{{ data?.turnus.turnusName || 'Listy turnusu' }}</h2>
        <p v-if="data" class="text-xs text-slate-500">{{ data.turnus.turnusCode }} · {{ displayDate(data.turnus.startDate) }} – {{ displayDate(data.turnus.endDate) }}</p>
      </header>
      <nav aria-label="Sekcje turnusu" class="flex gap-1 border-b border-slate-200 text-sm">
        <RouterLink v-if="auth.isOperator" :to="{ name: 'turnus-detail', params: { code: route.params.code } }" class="rounded-t px-4 py-2 text-slate-600 hover:bg-white focus-visible:ring-2 focus-visible:ring-slate-500">Szczegóły</RouterLink>
        <RouterLink :to="{ name: 'turnus-lists', params: { code: route.params.code } }" aria-current="page" class="border-b-2 border-slate-800 px-4 py-2 font-semibold text-slate-900">Listy</RouterLink>
      </nav>
      <p v-if="loading" role="status" class="text-sm text-slate-600">Wczytywanie list…</p>
      <div v-else-if="error" role="alert" class="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
        {{ error }} <button type="button" class="ml-2 rounded underline focus-visible:ring-2 focus-visible:ring-red-600" @click="load">Spróbuj ponownie</button>
      </div>
      <template v-else-if="data">
        <fieldset :disabled="exporting" aria-label="Filtry i eksport list" class="flex min-w-0 flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white p-3">
          <legend class="sr-only">Filtry i eksport list</legend>
          <label class="flex w-full items-center gap-2 text-sm text-slate-600 sm:w-auto">
            <span class="shrink-0">Typ listy</span>
            <select :value="selectedView" class="h-10 min-w-0 flex-1 rounded border border-slate-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-slate-500 sm:w-40 sm:flex-none" @change="selectView($event.target.value)">
              <option v-for="view in listViews" :key="view.key" :value="view.key">{{ view.label }}</option>
            </select>
          </label>
          <select v-model="group" aria-label="Osoby" class="h-10 w-full rounded border border-slate-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-slate-500 sm:w-40 sm:shrink-0">
            <option value="">Wszyscy</option><option value="PARTICIPANT">Uczestnicy</option><option value="STAFF">Kadra</option>
          </select>
          <div class="relative w-full sm:w-40 sm:shrink-0">
            <input v-model="search" aria-label="Wyszukaj osobę" placeholder="Wyszukaj" class="h-10 w-full rounded border border-slate-300 px-3 py-2 pr-9 text-sm focus-visible:ring-2 focus-visible:ring-slate-500" />
            <button v-if="search" type="button" aria-label="Wyczyść wyszukiwanie" class="absolute inset-y-0 right-2 my-auto h-6 w-6 rounded text-slate-500 hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-slate-500" @click="search = ''">×</button>
          </div>
          <button type="button" class="shrink-0 rounded border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-slate-500" :disabled="!filtered" @click="clearFilters">Wyczyść filtry</button>
          <div role="group" aria-label="Eksport listy" class="flex w-full flex-wrap items-center gap-2 sm:ml-auto sm:w-auto sm:shrink-0">
            <label class="flex items-center gap-2 text-sm text-slate-600">
              Format
              <select v-model="exportFormat" :disabled="exporting" class="rounded border border-slate-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-slate-500">
                <option value="xlsx">XLSX</option><option value="csv">CSV</option><option value="pdf">PDF</option>
              </select>
            </label>
            <button type="button" class="rounded bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-slate-500" :disabled="exporting || !exportCount" :aria-busy="exporting" @click="exportList">
              {{ exporting ? 'Przygotowywanie pliku…' : `Eksportuj (${exportCount})` }}
            </button>
          </div>
        </fieldset>
        <p v-if="exportError" role="alert" class="rounded-lg bg-red-50 p-3 text-sm text-red-800">{{ exportError }}</p>
        <p v-if="exportSuccess" role="status" class="text-sm text-slate-600">{{ exportSuccess }}</p>
        <fieldset v-if="selectedView === 'custom'" :disabled="exporting" class="rounded-xl border border-slate-200 bg-white px-3 pb-3">
          <legend class="px-1 text-sm font-semibold text-slate-800">Kolumny</legend>
          <div class="space-y-3">
            <fieldset v-for="columnGroup in visibleCustomColumnGroups" :key="columnGroup.key" class="rounded-lg border border-slate-200 px-3 pb-2">
              <legend class="px-1 text-xs font-semibold text-slate-600">{{ columnGroup.label }}</legend>
              <div class="columns-1 gap-x-8 sm:columns-2 lg:columns-3">
                <label v-for="column in columnGroup.columns" :key="column.key" class="mb-1 flex w-full break-inside-avoid cursor-pointer items-center gap-2 rounded py-1 text-sm text-slate-700">
                  <input type="checkbox" :checked="customColumns.includes(column.key)" class="h-4 w-4 shrink-0 accent-slate-800 focus-visible:ring-2 focus-visible:ring-slate-500" @change="setColumn(column.key, $event.target.checked)" />
                  {{ column.label }}
                </label>
              </div>
            </fieldset>
          </div>
        </fieldset>
        <p v-if="incompleteCount" role="status" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Niepełne dane w {{ incompleteCount }} zgłoszeniach. Brakujące wartości pozostawiono puste.</p>
        <section v-for="item in groups" :key="item.type" :aria-labelledby="`list-${item.type}`" class="min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <h3 :id="`list-${item.type}`" class="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">{{ item.label }} <span class="ml-1 font-normal text-slate-500">{{ item.rows.length }}/{{ item.total }}</span></h3>
          <p v-if="!item.columns.length" class="p-4 text-sm text-slate-500">Wybierz kolumny dla tej grupy, aby wyświetlić listę.</p>
          <p v-else-if="!item.total" class="p-4 text-sm text-slate-500">Brak przyjętych zgłoszeń.</p>
          <p v-else-if="!item.rows.length" class="p-4 text-sm text-slate-500">Brak osób pasujących do filtrów.</p>
          <div v-else class="overflow-x-auto" tabindex="0" :aria-label="`Tabela: ${item.label}`">
            <table class="w-full text-left text-sm">
              <thead class="bg-slate-50 text-slate-600"><tr>
                <th v-for="column in item.columns" :key="column.key" scope="col" class="whitespace-nowrap px-4 py-2" :aria-sort="sortKey === column.key ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'">
                  <button type="button" :disabled="exporting" class="rounded font-medium focus-visible:ring-2 focus-visible:ring-slate-500" @click="sort(column.key)">{{ column.label }}<span v-if="sortKey === column.key" aria-hidden="true" class="ml-1">{{ direction === 'asc' ? '↑' : '↓' }}</span></button>
                </th>
              </tr></thead>
              <tbody class="divide-y divide-slate-100"><tr v-for="entry in item.rows" :key="entry.person.registrationCode">
                <td v-for="column in item.columns" :key="column.key" class="px-4 py-2.5 text-slate-700">{{ listValue(entry, column.key) ?? '—' }}</td>
              </tr></tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
