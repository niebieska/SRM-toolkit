<script setup>
import { computed, reactive, ref } from 'vue'
import { createDemoTurnusy } from '../demo/turnusy'

const turnusy = ref(createDemoTurnusy())
const selectedCode = ref(turnusy.value[0].code)
const tab = ref('people')
const query = ref('')
const group = ref('PARTICIPANT')
const editing = ref(false)
const draft = reactive({})
const message = ref('')
const exportKind = ref('arrival')
const includeStaff = ref(false)
const selected = computed(() => turnusy.value.find(t => t.code === selectedCode.value))
const accepted = t => t.people.filter(p => p.status === 'ACCEPTED' && p.type === 'PARTICIPANT').length
const staff = t => t.people.filter(p => p.status === 'ACCEPTED' && p.type === 'STAFF').length
const waiting = t => t.people.filter(p => p.status === 'WAITLIST').length
const people = computed(() => selected.value.people.filter(p => {
  const inGroup = group.value === 'WAITLIST' ? p.status === 'WAITLIST' : p.status === 'ACCEPTED' && p.type === group.value
  return inGroup && `${p.firstName} ${p.lastName} ${p.code}`.toLocaleLowerCase('pl').includes(query.value.trim().toLocaleLowerCase('pl'))
}))
const exportPeople = computed(() => selected.value.people.filter(p => p.status === 'ACCEPTED' && (includeStaff.value || p.type === 'PARTICIPANT')))
const headers = computed(() => exportKind.value === 'insurance'
  ? ['Kod zgłoszenia', 'Imię', 'Nazwisko', 'Data urodzenia', 'Typ']
  : ['Kod zgłoszenia', 'Imię', 'Nazwisko', 'Typ', 'Przyjazd', 'Podpis / uwagi'])
const rows = computed(() => exportPeople.value.map(p => exportKind.value === 'insurance'
  ? [p.code, p.firstName, p.lastName, p.birthDate, p.role]
  : [p.code, p.firstName, p.lastName, p.role, '', '']))
const date = value => new Intl.DateTimeFormat('pl-PL').format(new Date(`${value}T12:00:00`))
function selectTurnus(code) {
  selectedCode.value = code
  editing.value = false
  message.value = ''
  query.value = ''
}
function edit() {
  Object.assign(draft, { name: selected.value.name, start: selected.value.start, end: selected.value.end, capacity: selected.value.capacity, location: selected.value.location, open: selected.value.open })
  editing.value = true
  message.value = ''
}
function save() {
  if (draft.end < draft.start) { message.value = 'Data końca nie może poprzedzać początku.'; return }
  Object.assign(selected.value, { ...draft, name: draft.name.trim(), location: draft.location.trim() })
  editing.value = false
  message.value = 'Zapisano w podglądzie. Odświeżenie strony przywróci dane przykładowe.'
}
function download() {
  const csvCell = value => `"${String(value).replaceAll('"', '""')}"`
  const csv = '\uFEFF' + [headers.value, ...rows.value].map(row => row.map(csvCell).join(';')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `DEMO-${selected.value.code}-${exportKind.value}.csv`
  a.click()
  URL.revokeObjectURL(url)
  message.value = 'Pobrano CSV z danymi przykładowymi.'
}
function printList() { window.print() }
</script>

<template>
  <main class="turnusy-demo min-h-screen p-4 md:p-8">
    <div class="mx-auto max-w-7xl space-y-6">
      <header class="screen-only flex flex-wrap items-center justify-between gap-4">
        <div><p class="text-xs font-semibold uppercase tracking-widest text-slate-500">Biuro / Organizacja sezonu</p><h1 class="mt-1 text-3xl font-semibold tracking-tight">Turnusy</h1></div>
        <router-link to="/dashboard" class="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50">← Zgłoszenia</router-link>
      </header>
      <div class="screen-only rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"><strong>Podgląd interaktywny</strong> · Wszystkie osoby i turnusy są przykładowe. Zmiany działają tylko do odświeżenia strony.</div>
      <div class="screen-only grid gap-3 md:grid-cols-3">
        <button v-for="turnus in turnusy" :key="turnus.code" type="button" @click="selectTurnus(turnus.code)"
          :aria-pressed="selectedCode === turnus.code"
          class="rounded-xl border bg-white p-5 text-left transition hover:border-teal-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-teal-700"
          :class="selectedCode === turnus.code ? 'border-teal-600 ring-1 ring-teal-600' : 'border-slate-200'">
          <div class="flex items-center justify-between gap-2"><span class="text-xs font-medium text-slate-500">{{ turnus.code }}</span><span class="rounded-full px-2 py-1 text-xs" :class="turnus.open ? 'bg-teal-50 text-teal-800' : 'bg-slate-100 text-slate-600'">{{ turnus.open ? 'Zapisy otwarte' : 'Zapisy zamknięte' }}</span></div>
          <h2 class="mt-3 font-semibold">{{ turnus.name }}</h2><p class="mt-1 text-sm text-slate-500">{{ date(turnus.start) }} – {{ date(turnus.end) }}</p>
          <div class="mt-5 flex items-baseline justify-between"><span class="text-sm text-slate-600">Przyjęci uczestnicy</span><span><strong class="text-xl">{{ accepted(turnus) }}</strong><span class="text-sm text-slate-400"> / {{ turnus.capacity }}</span></span></div>
          <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100"><div class="h-full rounded-full" :class="accepted(turnus) >= turnus.capacity ? 'bg-amber-500' : 'bg-teal-600'" :style="{ width: `${Math.min(100, accepted(turnus) / turnus.capacity * 100)}%` }"></div></div>
          <p class="mt-3 text-xs text-slate-500">Kadra: {{ staff(turnus) }} <span class="mx-2">·</span> Rezerwa: {{ waiting(turnus) }}</p>
        </button>
      </div>
      <section class="rounded-xl border border-slate-200 bg-white">
        <div class="screen-only flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 p-5 md:p-6">
          <div><h2 class="text-xl font-semibold">{{ selected.name }}</h2><p class="mt-1 text-sm text-slate-500">{{ selected.location }}</p></div>
          <div class="text-sm text-slate-500">{{ date(selected.start) }} – {{ date(selected.end) }}</div>
        </div>
        <nav class="screen-only flex gap-5 overflow-x-auto border-b border-slate-200 px-5 md:px-6" aria-label="Widoki turnusu">
          <button v-for="item in [{ key: 'people', label: 'Uczestnicy i kadra' }, { key: 'settings', label: 'Ustawienia turnusu' }, { key: 'exports', label: 'Listy i eksporty' }]" :key="item.key" type="button" @click="tab = item.key; message = ''" :aria-current="tab === item.key ? 'page' : undefined" class="whitespace-nowrap border-b-2 py-4 text-sm font-medium" :class="tab === item.key ? 'border-teal-700 text-teal-800' : 'border-transparent text-slate-500 hover:text-slate-800'">{{ item.label }}</button>
        </nav>
        <p v-if="message" role="status" class="screen-only mx-6 mt-4 rounded-lg bg-slate-100 p-3 text-sm">{{ message }}</p>
        <div v-if="tab === 'people'" class="screen-only space-y-5 p-5 md:p-6">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex flex-wrap gap-2"><button v-for="item in [{ key: 'PARTICIPANT', label: `Przyjęci (${accepted(selected)})` }, { key: 'STAFF', label: `Kadra (${staff(selected)})` }, { key: 'WAITLIST', label: `Rezerwa (${waiting(selected)})` }]" :key="item.key" type="button" @click="group = item.key" :aria-pressed="group === item.key" class="rounded-lg px-3 py-2 text-sm" :class="group === item.key ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600'">{{ item.label }}</button></div>
            <input v-model="query" type="search" aria-label="Szukaj osoby lub kodu" placeholder="Szukaj osoby lub kodu…" class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm sm:w-64" />
          </div>
          <div class="overflow-x-auto"><table class="w-full text-left text-sm"><thead class="border-b text-xs uppercase tracking-wide text-slate-500"><tr><th class="pb-3">Osoba</th><th class="pb-3">Kod zgłoszenia</th><th class="pb-3">Rola</th><th class="pb-3">Przyjazd · demo</th></tr></thead><tbody><tr v-for="person in people" :key="person.code" class="border-b border-slate-100"><td class="py-4 pr-4 font-medium">{{ person.firstName }} {{ person.lastName }}<span class="mt-1 block text-xs font-normal text-slate-500">ur. {{ date(person.birthDate) }}</span></td><td class="pr-4 font-mono text-xs text-slate-500">{{ person.code }}</td><td class="pr-4">{{ person.role }}</td><td><label v-if="person.status === 'ACCEPTED'" class="inline-flex cursor-pointer items-center gap-2"><input v-model="person.arrived" type="checkbox" class="h-4 w-4 accent-teal-700" /><span>{{ person.arrived ? 'Na miejscu' : 'Oczekiwany' }}</span></label><span v-else class="text-slate-400">—</span></td></tr></tbody></table><p v-if="!people.length" class="py-8 text-center text-sm text-slate-500">Brak osób pasujących do wyszukiwania.</p></div>
          <p class="text-xs text-slate-500">Oznaczenie przyjazdu jest demonstracyjne. Lista rezerwowa nie trafia do eksportów.</p>
        </div>
        <div v-if="tab === 'settings'" class="screen-only p-5 md:p-6">
          <div class="mb-5 flex items-center justify-between"><h3 class="font-semibold">Dane organizacyjne</h3><button v-if="!editing" type="button" @click="edit" class="rounded-lg bg-teal-700 px-4 py-2 text-sm text-white hover:bg-teal-800">Edytuj turnus</button></div>
          <form v-if="editing" @submit.prevent="save" class="grid max-w-3xl gap-4 sm:grid-cols-2">
            <label class="text-sm sm:col-span-2">Nazwa<input v-model="draft.name" required pattern=".*\S.*" class="demo-input" /></label>
            <label class="text-sm">Początek<input v-model="draft.start" type="date" required class="demo-input" /></label><label class="text-sm">Koniec<input v-model="draft.end" type="date" required :min="draft.start" class="demo-input" /></label>
            <label class="text-sm">Limit uczestników<input v-model.number="draft.capacity" type="number" min="1" step="1" required class="demo-input" /></label><label class="text-sm">Miejsce<input v-model="draft.location" required pattern=".*\S.*" class="demo-input" /></label>
            <label class="flex items-center gap-2 text-sm sm:col-span-2"><input v-model="draft.open" type="checkbox" class="accent-teal-700" />Zapisy otwarte</label>
            <p v-if="draft.capacity < accepted(selected)" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 sm:col-span-2">Limit jest niższy niż liczba przyjętych osób. Zmiana nie usuwa ani nie odrzuca zgłoszeń.</p>
            <div class="flex gap-2 sm:col-span-2"><button class="rounded-lg bg-teal-700 px-4 py-2 text-sm text-white">Zapisz w podglądzie</button><button type="button" @click="editing = false" class="rounded-lg border px-4 py-2 text-sm">Anuluj</button></div>
          </form>
          <dl v-else class="grid max-w-3xl gap-6 text-sm sm:grid-cols-2"><div><dt class="text-slate-500">Kod turnusu · stały identyfikator</dt><dd class="mt-1 font-mono">{{ selected.code }}</dd></div><div><dt class="text-slate-500">Limit uczestników</dt><dd class="mt-1">{{ selected.capacity }} miejsc <span v-if="accepted(selected) > selected.capacity" class="text-amber-700">· przekroczony</span></dd></div><div><dt class="text-slate-500">Terminy</dt><dd class="mt-1">{{ date(selected.start) }} – {{ date(selected.end) }}</dd></div><div><dt class="text-slate-500">Zapisy</dt><dd class="mt-1">{{ selected.open ? 'Otwarte' : 'Zamknięte' }}</dd></div></dl>
        </div>
        <div v-if="tab === 'exports'" class="space-y-5 p-5 md:p-6">
          <div class="screen-only grid gap-3 sm:grid-cols-2"><button v-for="item in [{ key: 'arrival', title: 'Lista przyjazdowa', description: 'Rejestracja na miejscu · podpisy i uwagi' }, { key: 'insurance', title: 'Lista do ubezpieczenia', description: 'Przykładowy układ · wymaga wzoru ubezpieczyciela' }]" :key="item.key" type="button" @click="exportKind = item.key" :aria-pressed="exportKind === item.key" class="rounded-xl border p-4 text-left" :class="exportKind === item.key ? 'border-teal-600 bg-teal-50' : 'border-slate-200'"><strong class="block text-sm">{{ item.title }}</strong><span class="mt-1 block text-xs text-slate-500">{{ item.description }}</span></button></div>
          <div class="screen-only flex flex-wrap items-center justify-between gap-3"><label class="flex items-center gap-2 text-sm"><input v-model="includeStaff" type="checkbox" class="accent-teal-700" />Uwzględnij przyjętą kadrę</label><div class="flex gap-2"><button type="button" @click="printList" class="rounded-lg border px-4 py-2 text-sm">Drukuj listę</button><button type="button" @click="download" class="rounded-lg bg-teal-700 px-4 py-2 text-sm text-white">Pobierz CSV</button></div></div>
          <div class="export-preview rounded-lg border border-slate-200 p-4"><p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Dane przykładowe · nie do użytku operacyjnego</p><h3 class="mt-2 text-lg font-semibold">{{ exportKind === 'insurance' ? 'Lista do ubezpieczenia' : 'Lista przyjazdowa' }} · {{ selected.name }}</h3><p class="mb-5 mt-1 text-xs text-slate-500">{{ date(selected.start) }} – {{ date(selected.end) }} · Tylko przyjęte osoby · {{ rows.length }} pozycji</p><div class="overflow-x-auto"><table class="w-full text-left text-xs"><thead><tr class="border-b"><th v-for="header in headers" :key="header" class="p-2">{{ header }}</th></tr></thead><tbody><tr v-for="(row, index) in rows" :key="exportPeople[index].code" class="border-b border-slate-100"><td v-for="(cell, cellIndex) in row" :key="cellIndex" class="h-10 p-2">{{ cell }}</td></tr></tbody></table></div></div>
          <p class="screen-only text-xs text-slate-500">PESEL nie jest używany w podglądzie. Docelowe pola listy ubezpieczeniowej ustalimy na podstawie wymagań ubezpieczyciela.</p>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.demo-input { @apply mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2; }
@media print {
  .screen-only { display: none !important; }
  .turnusy-demo { padding: 0; background: white; }
  section, .export-preview { border: 0; padding: 0; }
  .export-preview .overflow-x-auto { overflow: visible; }
  table { font-size: 9px; }
  tr { break-inside: avoid; }
  thead { display: table-header-group; }
}
</style>
