<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import { useAuthStore } from '../stores/auth'
import { createTurnus, fetchTurnus, updateTurnus } from '../api/turnusApi'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const isNew = computed(() => route.name === 'turnus-create')
const form = reactive({})
const original = ref(''), error = ref(''), success = ref('')
const loading = ref(false), loaded = ref(false), saving = ref(false), conflict = ref(false)
let loadSequence = 0
let createdTurnusCode = null
const dirty = computed(() => loaded.value && JSON.stringify(form) !== original.value)
const status = computed(() => !form.active ? 'Nieaktywny' : form.registrationOpen ? 'Zapisy otwarte' : 'Zapisy zamknięte')
function defaults() {
  return { turnusCode: '', turnusName: '', turnusDescription: '', turnusLocation: '', seasonType: 'SUMMER',
    seasonYear: new Date().getFullYear(), startDate: '', endDate: '', minAge: 14, capacity: 50,
    active: false, registrationOpen: false, version: null }
}
function apply(value) {
  Object.assign(form, defaults(), value, { turnusDescription: value.turnusDescription ?? '' })
  original.value = JSON.stringify(form)
  loaded.value = true
}
async function load() {
  const sequence = ++loadSequence
  loading.value = true
  loaded.value = false
  error.value = ''; conflict.value = false; success.value = ''
  try {
    const value = isNew.value ? defaults() : await fetchTurnus(auth.token, route.params.code)
    if (sequence === loadSequence) apply(value)
  } catch (e) { if (sequence === loadSequence) error.value = e.message }
  finally { if (sequence === loadSequence) loading.value = false }
}
function canLeave(to) {
  if (to.path === '/login' && !auth.isAuthenticated) return true
  return !saving.value && (!dirty.value || window.confirm('Masz niezapisane zmiany. Opuścić stronę bez zapisywania?'))
}
function beforeUnload(event) {
  if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
watch(() => route.fullPath, () => {
  // The POST response already contains the saved definition and its version.
  const reuseCreatedTurnus = createdTurnusCode !== null
    && route.name === 'turnus-detail'
    && route.params.code === createdTurnusCode
  createdTurnusCode = null
  if (!reuseCreatedTurnus) load()
}, { immediate: true })
async function reload() {
  if (saving.value || (dirty.value && !window.confirm('Wczytanie aktualnej wersji usunie niezapisane zmiany. Kontynuować?'))) return
  await load()
}
async function save() {
  if (saving.value) return
  error.value = ''; success.value = ''
  if (form.endDate < form.startDate) { error.value = 'Data końca nie może poprzedzać daty początku.'; return }
  saving.value = true
  try {
    const payload = { ...form, turnusDescription: form.turnusDescription.trim() || null }
    const created = isNew.value
    const value = created ? await createTurnus(auth.token, payload) : await updateTurnus(auth.token, route.params.code, payload)
    apply(value)
    conflict.value = false
    saving.value = false
    if (created) {
      createdTurnusCode = value.turnusCode
      try {
        await router.replace({ name: 'turnus-detail', params: { code: value.turnusCode } })
      } finally {
        createdTurnusCode = null
      }
    }
    success.value = created ? 'Turnus został utworzony.' : 'Zmiany zostały zapisane.'
  } catch (e) { error.value = e.message; conflict.value = e.status === 409 && !isNew.value }
  finally { saving.value = false }
}
</script>

<template>
  <main class="min-h-screen p-4 md:p-8">
    <div class="max-w-6xl mx-auto space-y-6">
      <BiuroHeader :unsaved-changes="dirty" :busy="saving" />
      <RouterLink to="/turnusy" class="inline-block text-sm text-slate-600 hover:text-slate-900">← Wszystkie turnusy</RouterLink>
      <p v-if="loading" role="status" class="rounded-xl bg-white p-8 text-center text-slate-500">Wczytywanie turnusu…</p>
      <div v-if="error" role="alert" class="rounded-lg bg-red-50 border border-red-200 text-red-800 p-4">
        <p>{{ error }}</p><button v-if="conflict || !loaded" type="button" class="mt-2 font-semibold underline" :disabled="loading || saving" @click="reload">Wczytaj aktualną wersję</button>
      </div>
      <template v-if="loaded && !loading">
        <section class="space-y-3">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div><h2 class="text-2xl font-semibold">{{ isNew ? 'Nowy turnus' : form.turnusName }}</h2><p class="text-sm text-slate-500 mt-1">{{ isNew ? 'Uzupełnij definicję i zdecyduj, kiedy otworzyć zapisy.' : `${form.turnusCode} · ${form.seasonType === 'WINTER' ? 'Zima' : 'Lato'} ${form.seasonYear}` }}</p></div>
            <span v-if="!isNew" class="rounded-full bg-slate-200 px-3 py-1 text-sm text-slate-700">{{ status }}</span>
          </div>
          <div class="border-b border-slate-300"><span class="inline-block border-b-2 border-slate-800 py-3 text-sm font-semibold">Definicja</span></div>
        </section>
        <p v-if="success" role="status" class="rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 p-4">{{ success }}</p>
        <form class="space-y-5" @submit.prevent="save">
          <fieldset :disabled="saving" class="grid grid-cols-1 lg:grid-cols-3 gap-5">
            <section class="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-5 md:p-6 space-y-5">
              <div><h3 class="font-semibold text-lg">Informacje o turnusie</h3><p class="text-sm text-slate-500 mt-1">Nazwa, miejsce i termin widoczne w formularzu oraz potwierdzeniach.</p></div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label class="block text-sm font-medium">Kod turnusu<input v-model="form.turnusCode" :readonly="!isNew" required pattern="[A-Z0-9][A-Z0-9_\-]{0,49}" maxlength="50" title="Wielkie litery, cyfry, myślnik lub podkreślenie; maksymalnie 50 znaków." class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 read-only:bg-slate-100" /><span class="block mt-1 text-xs text-slate-500 font-normal">{{ isNew ? 'Unikalny kod, np. NARTY2027T1. Po utworzeniu nie można go zmienić.' : 'Kod istniejącego turnusu jest niezmienny.' }}</span></label>
                <label class="block text-sm font-medium">Nazwa<input v-model="form.turnusName" required maxlength="200" class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
                <label class="block text-sm font-medium md:col-span-2">Miejsce<input v-model="form.turnusLocation" required maxlength="500" class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
                <label class="block text-sm font-medium">Sezon<select v-model="form.seasonType" class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2"><option value="SUMMER">Lato</option><option value="WINTER">Zima</option></select></label>
                <label class="block text-sm font-medium">Rok sezonu<input v-model.number="form.seasonYear" type="number" min="2000" max="9999" required class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
                <label class="block text-sm font-medium">Data początku<input v-model="form.startDate" type="date" required class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
                <label class="block text-sm font-medium">Data końca<input v-model="form.endDate" type="date" :min="form.startDate || undefined" required class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
                <label class="block text-sm font-medium md:col-span-2">Opis <span class="font-normal text-slate-400">(opcjonalny)</span><textarea v-model="form.turnusDescription" rows="4" maxlength="4000" class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2"></textarea></label>
              </div>
            </section>
            <div class="space-y-5">
              <section class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
                <h3 class="font-semibold text-lg">Parametry</h3>
                <label class="block text-sm font-medium">Minimalny wiek<input v-model.number="form.minAge" type="number" min="0" max="120" required class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
                <label class="block text-sm font-medium">Liczba miejsc<input v-model.number="form.capacity" type="number" min="1" max="2147483647" required class="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2" /></label>
              </section>
              <section class="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
                <h3 class="font-semibold text-lg">Dostępność zapisów</h3>
                <label class="flex gap-3 items-start cursor-pointer"><input v-model="form.active" type="checkbox" class="mt-1 h-4 w-4 accent-slate-800" /><span class="text-sm"><strong class="block">Turnus aktywny</strong><span class="text-slate-500">Wyłącz po zakończeniu sezonu. Definicja pozostaje w Biurze.</span></span></label>
                <label class="flex gap-3 items-start cursor-pointer"><input v-model="form.registrationOpen" type="checkbox" class="mt-1 h-4 w-4 accent-slate-800" /><span class="text-sm"><strong class="block">Rejestracja otwarta</strong><span class="text-slate-500">Ręcznie otwórz lub zamknij przyjmowanie zgłoszeń.</span></span></label>
                <p class="text-xs text-slate-500 border-t border-slate-100 pt-3">Turnus pojawi się w formularzu, gdy obie opcje są włączone. Zmiany mogą być widoczne po maksymalnie około godzinie.</p>
              </section>
            </div>
          </fieldset>
          <div class="flex flex-wrap items-center justify-between gap-3 bg-white rounded-xl border border-slate-200 p-4">
            <span class="text-sm text-slate-500">{{ dirty ? 'Masz niezapisane zmiany.' : isNew ? 'Nowy turnus jest domyślnie nieaktywny, z zamkniętymi zapisami.' : 'Wszystkie zmiany zapisane.' }}</span>
            <div class="flex gap-3"><button type="button" class="rounded-lg border border-slate-300 px-4 py-2 disabled:opacity-50" :disabled="saving" @click="router.push('/turnusy')">{{ isNew ? 'Anuluj' : 'Wróć do listy' }}</button><button type="submit" class="rounded-lg bg-slate-800 text-white px-5 py-2 hover:bg-slate-900 disabled:opacity-50" :disabled="saving || (!isNew && !dirty)">{{ saving ? 'Zapisywanie…' : isNew ? 'Utwórz turnus' : 'Zapisz zmiany' }}</button></div>
          </div>
        </form>
      </template>
    </div>
  </main>
</template>
