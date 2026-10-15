<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import { useAuthStore } from '../stores/auth'
import { fetchTurnusLists } from '../api/turnusApi'
import { availableListColumns, columnsForList, filterAndSortList, listColumns, listValue, listViews, validateTurnusLists } from '../utils/turnusLists.js'

const route = useRoute(), auth = useAuthStore()
const data = ref(null), error = ref(''), loading = ref(false)
const search = ref(''), group = ref(''), role = ref('')
const sortKey = ref('lastName'), direction = ref('asc')
const selectedView = ref('general')
const customColumns = ref(listColumns.map(column => column.key))
let sequence = 0
const entries = computed(() => data.value?.entries || [])
const roles = computed(() => [...new Map(entries.value.filter(e => e.person.registrationType === 'STAFF' && e.person.role)
  .map(e => [e.person.role, { code: e.person.role, label: e.roleLabel || e.person.role }])).values()]
  .sort((a, b) => a.label.localeCompare(b.label, 'pl')))
const groups = computed(() => [
  { type: 'PARTICIPANT', label: 'Uczestnicy' }, { type: 'STAFF', label: 'Kadra' },
].filter(item => !group.value || item.type === group.value).map(item => ({
  ...item,
  total: entries.value.filter(e => e.person.registrationType === item.type).length,
  rows: filterAndSortList(entries.value, { type: item.type, search: search.value, role: role.value,
    sortKey: sortKey.value, direction: direction.value }),
  columns: columnsForList(selectedView.value, customColumns.value, item.type),
})))
const incompleteCount = computed(() => entries.value.filter(e => e.person.incompleteData).length)
const filtered = computed(() => Boolean(search.value || group.value || role.value))
function clearFilters() { search.value = ''; group.value = ''; role.value = '' }
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
async function load() {
  const request = ++sequence, code = String(route.params.code)
  data.value = null; error.value = ''; loading.value = true
  try {
    const result = await fetchTurnusLists(auth.token, code)
    if (!validateTurnusLists(result, code)) throw new Error('Biuro zwróciło nieprawidłową listę turnusu.')
    if (request === sequence) data.value = result
  } catch (exception) {
    if (request === sequence) error.value = exception.message
  } finally {
    if (request === sequence) loading.value = false
  }
}
watch(() => route.params.code, () => {
  clearFilters()
  selectedView.value = 'general'
  customColumns.value = listColumns.map(column => column.key)
  sortKey.value = 'lastName'; direction.value = 'asc'
  load()
}, { immediate: true })
watch(group, value => { if (value === 'PARTICIPANT') role.value = '' })
onBeforeUnmount(() => { sequence++ })
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
        <RouterLink to="/turnusy" class="rounded hover:text-slate-900 focus-visible:ring-2 focus-visible:ring-slate-500">Turnusy</RouterLink>
        <span aria-hidden="true" class="mx-2">/</span><span aria-current="page">Listy</span>
      </nav>
      <header>
        <h2 class="text-2xl font-semibold text-slate-900">{{ data?.turnus.turnusName || 'Listy turnusu' }}</h2>
        <p v-if="data" class="mt-1 text-sm text-slate-600">{{ data.turnus.turnusCode }} · {{ displayDate(data.turnus.startDate) }} – {{ displayDate(data.turnus.endDate) }}</p>
      </header>
      <nav aria-label="Sekcje turnusu" class="flex gap-1 border-b border-slate-200 text-sm">
        <RouterLink :to="{ name: 'turnus-detail', params: { code: route.params.code } }" class="rounded-t px-4 py-2 text-slate-600 hover:bg-white focus-visible:ring-2 focus-visible:ring-slate-500">Definicja</RouterLink>
        <RouterLink :to="{ name: 'turnus-lists', params: { code: route.params.code } }" aria-current="page" class="border-b-2 border-slate-800 px-4 py-2 font-semibold text-slate-900">Listy</RouterLink>
      </nav>
      <p v-if="loading" role="status" class="text-sm text-slate-600">Wczytywanie list…</p>
      <div v-else-if="error" role="alert" class="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
        {{ error }} <button type="button" class="ml-2 rounded underline focus-visible:ring-2 focus-visible:ring-red-600" @click="load">Spróbuj ponownie</button>
      </div>
      <template v-else-if="data">
        <section aria-label="Filtry list" class="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-3">
          <label class="flex w-full items-center gap-2 text-sm text-slate-600 sm:w-auto">
            <span class="shrink-0">Typ listy</span>
            <select :value="selectedView" class="min-w-0 flex-1 rounded border border-slate-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-slate-500 sm:flex-none" @change="selectView($event.target.value)">
              <option v-for="view in listViews" :key="view.key" :value="view.key">{{ view.label }}</option>
            </select>
          </label>
          <select v-model="group" aria-label="Osoby" class="rounded border border-slate-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-slate-500">
            <option value="">Wszyscy</option><option value="PARTICIPANT">Uczestnicy</option><option value="STAFF">Kadra</option>
          </select>
          <select v-if="group !== 'PARTICIPANT'" v-model="role" aria-label="Rola kadry" class="rounded border border-slate-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-slate-500" :disabled="!roles.length">
            <option value="">Wszystkie role kadry</option><option v-for="item in roles" :key="item.code" :value="item.code">{{ item.label }}</option>
          </select>
          <div class="relative w-full sm:w-64">
            <input v-model="search" aria-label="Wyszukaj osobę" placeholder="Wyszukaj" class="w-full rounded border border-slate-300 px-3 py-2 pr-9 text-sm focus-visible:ring-2 focus-visible:ring-slate-500" />
            <button v-if="search" type="button" aria-label="Wyczyść wyszukiwanie" class="absolute inset-y-0 right-2 my-auto h-6 w-6 rounded text-slate-500 hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-slate-500" @click="search = ''">×</button>
          </div>
          <button type="button" class="rounded border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-slate-500" :disabled="!filtered" @click="clearFilters">Wyczyść filtry</button>
        </section>
        <fieldset v-if="selectedView === 'custom'" class="rounded-xl border border-slate-200 bg-white px-3 pb-3">
          <legend class="px-1 text-sm font-semibold text-slate-800">Kolumny</legend>
          <div class="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-3">
            <label v-for="column in availableListColumns" :key="column.key" class="flex cursor-pointer items-center gap-2 rounded py-1 text-sm text-slate-700">
              <input type="checkbox" :checked="customColumns.includes(column.key)" class="h-4 w-4 accent-slate-800 focus-visible:ring-2 focus-visible:ring-slate-500" @change="setColumn(column.key, $event.target.checked)" />
              {{ column.label }}
            </label>
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
                  <button type="button" class="rounded font-medium focus-visible:ring-2 focus-visible:ring-slate-500" @click="sort(column.key)">{{ column.label }}<span v-if="sortKey === column.key" aria-hidden="true" class="ml-1">{{ direction === 'asc' ? '↑' : '↓' }}</span></button>
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
