<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import TurnusFormField from '../components/TurnusFormField.vue'
import DiscardChangesDialog from '../components/DiscardChangesDialog.vue'
import { createDiscardConfirmation } from '../utils/discardConfirmation'
import { validateTurnusForm } from '../utils/turnusFormValidation'
import { useAuthStore } from '../stores/auth'
import { createTurnus, fetchTurnus, updateTurnus, fetchStaffingCategories, fetchStaffFeeOptions } from '../api/turnusApi'
import { requirementCount, withRequirement } from '../utils/staffRequirements'
import { hasStaffFee, staffFeeAmount, withStaffFee, withoutStaffFee } from '../utils/staffFees'
const { prompt: discardPrompt, confirm: confirmDiscard, decide: decideDiscard } = createDiscardConfirmation()
const formElement = ref(null), fieldErrors = ref({})
const categories = ref([]), categoriesLoading = ref(false), categoriesError = ref('')
const feeOptions = ref([]), feeOptionsLoading = ref(false), feeOptionsError = ref('')
let categorySequence = 0, feeOptionSequence = 0
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const isNew = computed(() => route.name === 'turnus-create')
const form = reactive({})
const original = ref(''), error = ref(''), validationError = ref(''), success = ref('')
const loading = ref(false), loaded = ref(false), saving = ref(false), conflict = ref(false)
let loadSequence = 0
let createdTurnusCode = null
const dirty = computed(() => loaded.value && JSON.stringify(form) !== original.value)
const feesReadOnly = computed(() => !isNew.value && form.feesConfigured)
const plannedStaff = computed(() => (form.staffRequirements || []).reduce((total, item) =>
  total + (Number.isInteger(item.requiredCount) && item.requiredCount > 0 ? item.requiredCount : 0), 0))
const status = computed(() => !form.active ? 'Nieaktywny' : 'Aktywny')
function defaults() {
  return { turnusCode: '', turnusName: '', turnusDescription: '', turnusLocation: '', seasonType: 'SUMMER',
    seasonYear: new Date().getFullYear(), startDate: '', endDate: '', minAge: 14, participantCapacity: 50, staffCapacity: 10,
    active: false, participantRegistrationOpen: false, staffRegistrationOpen: false, version: null, staffRequirements: [], participantFee: 0, staffFees: [], feesConfigured: false }
}
function apply(value) {
  Object.assign(form, defaults(), value, {
    turnusDescription: value.turnusDescription ?? '',
    staffRequirements: (value.staffRequirements || []).map(item => ({ ...item })),
    staffFees: (value.staffFees || []).map(item => ({ ...item })),
  })
  fieldErrors.value = {}
  validationError.value = ''
  original.value = JSON.stringify(form)
  loaded.value = true
}
async function load() {
  const sequence = ++loadSequence
  loading.value = true
  loaded.value = false
  error.value = ''; validationError.value = ''; conflict.value = false; success.value = ''
  try {
    const value = isNew.value ? defaults() : await fetchTurnus(auth.token, route.params.code)
    if (sequence === loadSequence) apply(value)
  } catch (e) { if (sequence === loadSequence) error.value = e.message }
  finally { if (sequence === loadSequence) loading.value = false }
}
function isCreatedDetail(to) {
  return createdTurnusCode !== null && to.name === 'turnus-detail' && to.params.code === createdTurnusCode
}
function canLeave(to) {
  if (isCreatedDetail(to)) return true
  if (!auth.isOperator || (to.path === '/login' && !auth.isAuthenticated)) return true
  if (saving.value) return false
  return !dirty.value || confirmDiscard('leave')
}
function beforeUnload(event) {
  if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); categorySequence++; feeOptionSequence++; loadSequence++; decideDiscard(false) })
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
watch(() => route.fullPath, () => {
  // The POST response already contains the saved definition and its version.
  const reuseCreatedTurnus = isCreatedDetail(route)
  createdTurnusCode = null
  if (!reuseCreatedTurnus) load()
}, { immediate: true })
async function loadCategories() {
  if (!form.seasonType) return
  const sequence = ++categorySequence
  categoriesLoading.value = true
  categoriesError.value = ''
  categories.value = []
  try {
    const value = await fetchStaffingCategories(auth.token, form.seasonType)
    if (!Array.isArray(value)) throw new Error('Nieprawidłowa lista kategorii kadry.')
    if (sequence === categorySequence) categories.value = value
  } catch (e) { if (sequence === categorySequence) categoriesError.value = e.message }
  finally { if (sequence === categorySequence) categoriesLoading.value = false }
}
watch(() => form.seasonType, loadCategories, { immediate: true })
async function loadFeeOptions() {
  if (!form.seasonType) return
  const sequence = ++feeOptionSequence
  feeOptionsLoading.value = true
  feeOptionsError.value = ''
  feeOptions.value = []
  try {
    const value = await fetchStaffFeeOptions(auth.token, form.seasonType)
    if (!Array.isArray(value)) throw new Error('Nieprawidłowa lista opcji opłat kadry.')
    if (sequence === feeOptionSequence) feeOptions.value = value
  } catch (e) { if (sequence === feeOptionSequence) feeOptionsError.value = e.message }
  finally { if (sequence === feeOptionSequence) feeOptionsLoading.value = false }
}
watch(() => form.seasonType, loadFeeOptions, { immediate: true })
function setRequirement(code, value) {
  form.staffRequirements = withRequirement(form.staffRequirements, code, value)
  if (fieldErrors.value.staffRequirements) validateField('staffRequirements')
}
function staffFeeLabel(option) {
  return option.subroleLabel ? `${option.roleLabel} — ${option.subroleLabel}` : option.roleLabel
}
function setStaffFeeEnabled(option, enabled) {
  form.staffFees = enabled
    ? withStaffFee(form.staffFees, option, '')
    : withoutStaffFee(form.staffFees, option)
  validateField('staffFees')
}
function updateStaffFee(option, value) {
  form.staffFees = withStaffFee(form.staffFees, option, value)
  if (fieldErrors.value.staffFees) validateField('staffFees')
}
async function reload() {
  if (saving.value || (dirty.value && !await confirmDiscard('reload'))) return
  await load()
}
function validateField(name) {
  const errors = validateTurnusForm(form, categories.value, feeOptions.value)
  if (errors[name]) fieldErrors.value[name] = errors[name]
  else delete fieldErrors.value[name]
  if (!Object.keys(errors).length) validationError.value = ''
  if (name === 'startDate' && fieldErrors.value.endDate) validateField('endDate')
}
function onFieldInput(event) {
  const name = event.target.name
  if (name && fieldErrors.value[name]) validateField(name)
}
function descriptionId(name, help = false) {
  return fieldErrors.value[name] ? `${name}-error` : help ? `${name}-help` : undefined
}
async function save() {
  if (saving.value || categoriesLoading.value || categoriesError.value || feeOptionsLoading.value || feeOptionsError.value) return
  error.value = ''; validationError.value = ''; success.value = ''
  fieldErrors.value = validateTurnusForm(form, categories.value, feeOptions.value)
  if (Object.keys(fieldErrors.value).length) {
    validationError.value = 'Sprawdź zaznaczone pola przed zapisaniem turnusu.'
    const name = Object.keys(fieldErrors.value)[0]
    formElement.value?.querySelector(`[name="${name}"]`)?.focus()
    return
  }
  saving.value = true
  try {
    const created = isNew.value
    const payload = {
      ...form,
      participantFee: Number(form.participantFee),
      staffFees: form.staffFees.map(item => ({ ...item, amount: Number(item.amount) })),
      turnusDescription: form.turnusDescription.trim() || null,
    }
    if (!created && !form.feesConfigured) {
      const originalForm = JSON.parse(original.value)
      const participantFeeChanged = Number(form.participantFee) !== Number(originalForm.participantFee ?? 0)
      const staffFeesChanged = JSON.stringify(form.staffFees) !== JSON.stringify(originalForm.staffFees || [])
      if (!participantFeeChanged && !staffFeesChanged) {
        delete payload.participantFee
        delete payload.staffFees
      }
    }
    delete payload.feesConfigured
    const value = created ? await createTurnus(auth.token, payload) : await updateTurnus(auth.token, route.params.code, payload)
    apply(value)
    conflict.value = false
    if (created) {
      createdTurnusCode = value.turnusCode
      try {
        await router.replace({ name: 'turnus-detail', params: { code: value.turnusCode } })
      } finally {
        createdTurnusCode = null
      }
    }
    success.value = created ? 'Turnus został utworzony.' : 'Zmiany zostały zapisane.'
  } catch (e) {
    error.value = e.message
    conflict.value = e.status === 409 && !isNew.value
    if (e.status === 409 && isNew.value) fieldErrors.value.turnusCode = e.message
  }
  finally { saving.value = false }
}
</script>

<template>
  <main class="min-h-screen bg-slate-100 px-4 py-5 sm:px-6 lg:px-8">
    <div class="mx-auto max-w-6xl space-y-5">
      <BiuroHeader :unsaved-changes="dirty" :busy="saving" :confirm-discard="confirmDiscard" />
      <DiscardChangesDialog v-if="discardPrompt" :kind="discardPrompt.kind" @stay="decideDiscard(false)" @discard="decideDiscard(true)" />
      <nav aria-label="Ścieżka nawigacji" class="text-sm text-slate-600">
        <ol class="flex flex-wrap items-center gap-2">
          <li><RouterLink to="/turnusy" class="rounded hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500">Turnusy</RouterLink></li>
          <li aria-hidden="true" class="text-slate-400">/</li>
          <li aria-current="page" class="font-medium text-slate-800">{{ isNew ? 'Nowy turnus' : 'Szczegóły turnusu' }}</li>
        </ol>
      </nav>
      <header class="space-y-1">
        <div class="flex flex-wrap items-center gap-2">
          <h2 class="min-w-0 break-words text-xl font-semibold tracking-tight text-slate-900">{{ isNew ? 'Nowy turnus' : form.turnusName || 'Szczegóły turnusu' }}</h2>
          <span v-if="loaded" class="shrink-0 rounded-full border px-2 py-0.5 text-xs font-medium" :class="form.active ? 'border-slate-400 bg-slate-200 text-slate-900' : 'border-slate-200 bg-white text-slate-600'">{{ status }}</span>
        </div>
        <p v-if="isNew" class="text-sm text-slate-600">Uzupełnij informacje i skonfiguruj nowy turnus.</p>
      </header>
      <nav v-if="!isNew && loaded" aria-label="Sekcje turnusu" class="flex gap-1 border-b border-slate-200 text-sm">
        <RouterLink :to="{ name: 'turnus-detail', params: { code: form.turnusCode } }" aria-current="page" class="border-b-2 border-slate-800 px-4 py-2 font-semibold text-slate-900">Szczegóły</RouterLink>
        <RouterLink :to="{ name: 'turnus-lists', params: { code: form.turnusCode } }" class="rounded-t px-4 py-2 text-slate-600 hover:bg-white focus-visible:ring-2 focus-visible:ring-slate-500">Listy</RouterLink>
        <RouterLink :to="{ name: 'turnus-payments', params: { code: form.turnusCode } }" class="rounded-t px-4 py-2 text-slate-600 hover:bg-white focus-visible:ring-2 focus-visible:ring-slate-500">Wpłaty</RouterLink>
      </nav>
      <p v-if="loading" role="status" class="turnus-card text-center text-sm text-slate-600">Wczytywanie turnusu…</p>
      <div v-if="error || validationError" role="alert" class="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
        <p>{{ error || validationError }}</p>
        <button v-if="conflict || !loaded" type="button" class="mt-2 rounded font-semibold underline focus-visible:ring-2 focus-visible:ring-red-600" :disabled="loading || saving" @click="reload">Wczytaj aktualną wersję</button>
      </div>
      <p v-if="success" role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{{ success }}</p>
      <form v-if="loaded && !loading" ref="formElement" novalidate class="space-y-5" :aria-busy="saving" @submit.prevent="save" @input="onFieldInput" @focusout="event => event.target.name && validateField(event.target.name)">
        <fieldset :disabled="saving" class="grid min-w-0 grid-cols-1 items-start gap-5 lg:grid-cols-3">
          <legend class="sr-only">Szczegóły turnusu</legend>
          <div class="min-w-0 space-y-4 lg:col-span-2">
            <section aria-labelledby="turnus-information-heading" class="turnus-card min-w-0">
              <div class="mb-5 flex flex-wrap items-baseline justify-between gap-2">
                <h3 id="turnus-information-heading" class="text-base font-semibold text-slate-900">Informacje o turnusie</h3>
                <span class="text-xs text-slate-600">* Pola wymagane</span>
              </div>
              <div class="grid grid-cols-1 gap-x-4 gap-y-5 sm:grid-cols-2">
                  <TurnusFormField id="turnusCode" label="Kod turnusu" required help="Unikalny kod. Po utworzeniu nie można go zmienić." :error="fieldErrors.turnusCode">
                    <input id="turnusCode" name="turnusCode" v-model="form.turnusCode" type="text" :readonly="!isNew" pattern="[A-Z0-9][A-Z0-9_\-]{0,49}" maxlength="50" placeholder="Np. NARTY2027T1" class="turnus-control" required :aria-invalid="!!fieldErrors.turnusCode" :aria-describedby="descriptionId('turnusCode', true)" />
                  </TurnusFormField>
                  <TurnusFormField id="turnusName" label="Nazwa" required :error="fieldErrors.turnusName">
                    <input id="turnusName" name="turnusName" v-model="form.turnusName" type="text" maxlength="200" placeholder="Np. Narty z Jezusem" class="turnus-control" required :aria-invalid="!!fieldErrors.turnusName" :aria-describedby="descriptionId('turnusName', false)" />
                  </TurnusFormField>
                  <TurnusFormField id="turnusLocation" label="Miejsce" required :error="fieldErrors.turnusLocation" class="sm:col-span-2">
                    <input id="turnusLocation" name="turnusLocation" v-model="form.turnusLocation" type="text" maxlength="500" placeholder="Miejscowość i ośrodek" class="turnus-control" required :aria-invalid="!!fieldErrors.turnusLocation" :aria-describedby="descriptionId('turnusLocation', false)" />
                  </TurnusFormField>
                  <TurnusFormField id="seasonType" label="Sezon" required :error="fieldErrors.seasonType" :help="feesReadOnly ? 'Sezonu nie można zmienić po ustaleniu opłat.' : undefined">
                    <select id="seasonType" name="seasonType" v-model="form.seasonType" class="turnus-control" required :disabled="feesReadOnly" :aria-invalid="!!fieldErrors.seasonType" :aria-describedby="descriptionId('seasonType', feesReadOnly)"><option value="SUMMER">Lato</option><option value="WINTER">Zima</option></select>
                  </TurnusFormField>
                  <TurnusFormField id="seasonYear" label="Rok sezonu" required :error="fieldErrors.seasonYear">
                    <input id="seasonYear" name="seasonYear" v-model.number="form.seasonYear" type="number" min="2000" max="9999" step="1" class="turnus-control" required :aria-invalid="!!fieldErrors.seasonYear" :aria-describedby="descriptionId('seasonYear', false)" />
                  </TurnusFormField>
                  <TurnusFormField id="startDate" label="Data początku" required :error="fieldErrors.startDate">
                    <input id="startDate" name="startDate" v-model="form.startDate" type="date" class="turnus-control" required :aria-invalid="!!fieldErrors.startDate" :aria-describedby="descriptionId('startDate', false)" />
                  </TurnusFormField>
                  <TurnusFormField id="endDate" label="Data końca" required :error="fieldErrors.endDate">
                    <input id="endDate" name="endDate" v-model="form.endDate" type="date" :min="form.startDate || undefined" class="turnus-control" required :aria-invalid="!!fieldErrors.endDate" :aria-describedby="descriptionId('endDate', false)" />
                  </TurnusFormField>
                  <TurnusFormField id="turnusDescription" label="Opis (opcjonalnie)" :error="fieldErrors.turnusDescription" class="sm:col-span-2">
                    <textarea id="turnusDescription" name="turnusDescription" v-model="form.turnusDescription" rows="4" maxlength="4000" placeholder="Dodatkowe informacje o turnusie" class="turnus-control resize-y" :aria-invalid="!!fieldErrors.turnusDescription" :aria-describedby="descriptionId('turnusDescription')"></textarea>
                  </TurnusFormField>
              </div>
            </section>
            <section aria-labelledby="turnus-settings-heading" class="turnus-card">
              <h3 id="turnus-settings-heading" class="mb-3 text-base font-semibold text-slate-900">Ustawienia zapisów</h3>
              <div class="grid grid-cols-1 items-stretch gap-2 sm:grid-cols-3">
                <label class="flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-3 text-sm font-medium text-slate-800 focus-within:ring-2 focus-within:ring-slate-500 focus-within:ring-offset-2" :class="form.active ? 'border-slate-500 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'">
                  <input v-model="form.active" type="checkbox" class="mt-0.5 h-5 w-5 shrink-0 rounded accent-slate-800" />
                  <span class="min-w-0">
                    <span class="block">Turnus aktywny</span>
                    <span aria-hidden="true" class="mt-1.5 inline-flex rounded-full px-2 py-0.5 text-xs font-medium" :class="form.active ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600'">{{ form.active ? 'Aktywny' : 'Nieaktywny' }}</span>
                  </span>
                </label>
                <label class="flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-3 text-sm font-medium text-slate-800 focus-within:ring-2 focus-within:ring-slate-500 focus-within:ring-offset-2" :class="form.participantRegistrationOpen ? 'border-slate-500 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'">
                  <input v-model="form.participantRegistrationOpen" type="checkbox" class="mt-0.5 h-5 w-5 shrink-0 rounded accent-slate-800" />
                  <span class="min-w-0">
                    <span class="block">Zapisy uczestników</span>
                    <span aria-hidden="true" class="mt-1.5 inline-flex rounded-full px-2 py-0.5 text-xs font-medium" :class="form.participantRegistrationOpen ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600'">{{ form.participantRegistrationOpen ? 'Otwarte' : 'Zamknięte' }}</span>
                  </span>
                </label>
                <label class="flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-3 text-sm font-medium text-slate-800 focus-within:ring-2 focus-within:ring-slate-500 focus-within:ring-offset-2" :class="form.staffRegistrationOpen ? 'border-slate-500 bg-slate-50' : 'border-slate-200 hover:bg-slate-50'">
                  <input v-model="form.staffRegistrationOpen" type="checkbox" class="mt-0.5 h-5 w-5 shrink-0 rounded accent-slate-800" />
                  <span class="min-w-0">
                    <span class="block">Zapisy kadry</span>
                    <span aria-hidden="true" class="mt-1.5 inline-flex rounded-full px-2 py-0.5 text-xs font-medium" :class="form.staffRegistrationOpen ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600'">{{ form.staffRegistrationOpen ? 'Otwarte' : 'Zamknięte' }}</span>
                  </span>
                </label>
              </div>
            </section>
          </div>
          <div class="min-w-0 space-y-4">
            <section aria-labelledby="turnus-limits-heading" class="turnus-card">
              <h3 id="turnus-limits-heading" class="mb-4 text-base font-semibold text-slate-900">Limity uczestników</h3>
              <div class="space-y-3">
                <div>
                  <div class="flex items-center justify-between gap-3">
                    <label for="minAge" class="text-sm font-medium text-slate-700">Minimalny wiek <span aria-hidden="true" class="text-slate-500">*</span></label>
                    <div class="flex shrink-0 items-center gap-2">
                      <input id="minAge" name="minAge" v-model.number="form.minAge" type="number" min="0" max="120" step="1" required class="turnus-number" :aria-invalid="!!fieldErrors.minAge" :aria-describedby="descriptionId('minAge')" />
                      <span class="w-7 text-xs text-slate-600">lat</span>
                    </div>
                  </div>
                  <p v-if="fieldErrors.minAge" id="minAge-error" class="mt-1.5 text-sm text-red-700">{{ fieldErrors.minAge }}</p>
                </div>
                <div>
                  <div class="flex items-center justify-between gap-3">
                    <label for="participantCapacity" class="text-sm font-medium text-slate-700">Miejsca dla uczestników <span aria-hidden="true" class="text-slate-500">*</span></label>
                    <div class="flex shrink-0 items-center gap-2">
                      <input id="participantCapacity" name="participantCapacity" v-model.number="form.participantCapacity" type="number" min="1" max="2147483647" step="1" required class="turnus-number" :aria-invalid="!!fieldErrors.participantCapacity" :aria-describedby="descriptionId('participantCapacity')" />
                      <span class="w-7 text-xs text-slate-600">osób</span>
                    </div>
                  </div>
                  <p v-if="fieldErrors.participantCapacity" id="participantCapacity-error" class="mt-1.5 text-sm text-red-700">{{ fieldErrors.participantCapacity }}</p>
                </div>
                <div>
                  <div class="flex items-center justify-between gap-3">
                    <label for="staffCapacity" class="text-sm font-medium text-slate-700">Miejsca dla kadry <span aria-hidden="true" class="text-slate-500">*</span></label>
                    <div class="flex shrink-0 items-center gap-2">
                      <input id="staffCapacity" name="staffCapacity" v-model.number="form.staffCapacity" type="number" min="1" max="2147483647" step="1" required class="turnus-number" :aria-invalid="!!fieldErrors.staffCapacity" :aria-describedby="descriptionId('staffCapacity')" />
                      <span class="w-7 text-xs text-slate-600">osób</span>
                    </div>
                  </div>
                  <p v-if="fieldErrors.staffCapacity" id="staffCapacity-error" class="mt-1.5 text-sm text-red-700">{{ fieldErrors.staffCapacity }}</p>
                </div>
              </div>
            </section>
            <section aria-labelledby="turnus-staffing-heading" class="turnus-card">
              <h3 id="turnus-staffing-heading" class="text-base font-semibold text-slate-900">Wymagana kadra</h3>
              <p v-if="categories.length && !categoriesLoading && !categoriesError" class="mt-1 text-sm text-slate-600">Łącznie wymagana kadra: <output class="font-semibold text-slate-900" aria-live="polite">{{ plannedStaff }}</output></p>
              <p v-if="categoriesLoading" role="status" class="mt-3 text-sm text-slate-600">Wczytywanie kategorii kadry…</p>
              <div v-else-if="categoriesError" role="alert" class="mt-3 text-sm text-red-700">{{ categoriesError }} <button type="button" class="mt-1 block rounded font-medium underline focus-visible:ring-2 focus-visible:ring-slate-500" @click="loadCategories">Spróbuj ponownie</button></div>
              <p v-else-if="!categories.length" class="mt-3 text-sm leading-relaxed text-slate-600">Brak zdefiniowanych kategorii kadry dla wybranego sezonu.</p>
              <template v-else>
                <div class="mt-3 divide-y divide-slate-100">
                  <div v-for="category in categories" :key="category.code" class="flex items-center justify-between gap-3 py-2 first:pt-0 last:pb-0">
                    <label :for="`staff-count-${category.code}`" class="min-w-0 text-sm text-slate-700">{{ category.label }}</label>
                    <input :id="`staff-count-${category.code}`" name="staffRequirements" :value="requirementCount(form.staffRequirements, category.code)" @input="setRequirement(category.code, $event.target.value)" type="number" min="0" max="2147483647" step="1" required class="turnus-number" :aria-invalid="!!fieldErrors.staffRequirements" :aria-describedby="fieldErrors.staffRequirements ? 'staffRequirements-error' : 'staffRequirements-help'" />
                  </div>
                </div>
                <p v-if="fieldErrors.staffRequirements" id="staffRequirements-error" class="mt-2 text-sm text-red-700">{{ fieldErrors.staffRequirements }}</p>
                <p id="staffRequirements-help" class="mt-3 text-xs text-slate-600">0 oznacza brak wymagania. To plan składu zespołu.</p>
              </template>
            </section>
            <section aria-labelledby="turnus-fees-heading" class="turnus-card">
              <h3 id="turnus-fees-heading" class="text-base font-semibold text-slate-900">Opłaty turnusu</h3>
              <p class="mt-1 text-xs leading-relaxed text-slate-600">Opłaty ustala się przy tworzeniu turnusu lub przy pierwszym uzupełnieniu stawek. Opłata kadry dotyczy wybranej roli lub funkcji — kwoty nie sumują się. Późniejszą obniżkę rozlicz rabatem dla zgłoszenia.</p>
              <div class="mt-3">
                <label for="participantFee" class="text-sm font-medium text-slate-700">Opłata uczestnika (zł)</label>
                <input id="participantFee" name="participantFee" v-model.number="form.participantFee" type="number" min="0" max="99999999.99" step="0.01" :disabled="feesReadOnly" class="turnus-control mt-1" :aria-invalid="!!fieldErrors.participantFee" :aria-describedby="descriptionId('participantFee', true)" />
                <p v-if="!fieldErrors.participantFee" id="participantFee-help" class="mt-1 text-xs text-slate-600">{{ feesReadOnly ? 'Opłata została ustalona i nie można jej zmienić.' : 'Wpisz 0, jeśli uczestnik nie wnosi opłaty.' }}</p>
                <p v-if="fieldErrors.participantFee" id="participantFee-error" class="mt-1.5 text-sm text-red-700">{{ fieldErrors.participantFee }}</p>
              </div>
              <p v-if="feeOptionsLoading" role="status" class="mt-4 text-sm text-slate-600">Wczytywanie ról i funkcji…</p>
              <div v-else-if="feeOptionsError" role="alert" class="mt-4 text-sm text-red-700">{{ feeOptionsError }} <button type="button" class="rounded font-medium underline focus-visible:ring-2 focus-visible:ring-slate-500" @click="loadFeeOptions">Spróbuj ponownie</button></div>
              <template v-else>
                <h4 class="mb-1 mt-4 text-sm font-semibold text-slate-800">Opłaty kadry</h4>
                <div class="divide-y divide-slate-100">
                  <div v-for="option in feeOptions" :key="`${option.role}:${option.subrole || ''}`" class="flex min-w-0 items-center gap-2 py-2 first:pt-1">
                    <label class="flex min-w-0 flex-1 cursor-pointer items-center gap-2 text-sm text-slate-700">
                      <input type="checkbox" class="h-4 w-4 shrink-0 accent-slate-800" :disabled="feesReadOnly" :checked="hasStaffFee(form.staffFees, option)" :aria-label="`Przypisz opłatę: ${staffFeeLabel(option)}`" @change="setStaffFeeEnabled(option, $event.target.checked)" />
                      <span class="min-w-0">{{ staffFeeLabel(option) }}</span>
                    </label>
                    <label v-if="hasStaffFee(form.staffFees, option)" class="flex shrink-0 items-center gap-1.5 text-xs text-slate-600">
                      <span class="sr-only">Opłata dla {{ staffFeeLabel(option) }}</span>
                      <input :value="staffFeeAmount(form.staffFees, option)" type="number" min="0.01" max="99999999.99" step="0.01" inputmode="decimal" :disabled="feesReadOnly" class="turnus-number w-24" :aria-invalid="!!fieldErrors.staffFees" :aria-describedby="fieldErrors.staffFees ? 'staffFees-error' : 'staffFees-help'" @input="updateStaffFee(option, $event.target.value)" />
                      <span>zł</span>
                    </label>
                  </div>
                </div>
                <p v-if="fieldErrors.staffFees" id="staffFees-error" class="mt-2 text-sm text-red-700">{{ fieldErrors.staffFees }}</p>
                <p id="staffFees-help" class="mt-2 text-xs text-slate-600">{{ feesReadOnly ? 'Opłaty zostały ustalone i nie można ich zmienić.' : 'Niezaznaczona rola oznacza brak opłaty. Ustaw opłatę dla roli albo jej funkcji.' }}</p>
              </template>
            </section>

          </div>
        </fieldset>
        <div class="turnus-card flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <p class="text-sm text-slate-600" role="status">{{ saving ? 'Zapisywanie turnusu…' : dirty ? 'Masz niezapisane zmiany.' : isNew ? 'Nowy turnus zostanie zapisany z wybranymi ustawieniami.' : 'Wszystkie zmiany zapisane.' }}</p>
          <div class="flex flex-col-reverse gap-2 sm:flex-row sm:gap-3">
            <button type="button" class="turnus-button border border-slate-300 bg-white text-slate-700 hover:bg-slate-50" :disabled="saving" @click="router.push('/turnusy')">{{ isNew ? 'Anuluj' : 'Wróć do listy' }}</button>
            <button type="submit" class="turnus-button bg-slate-800 text-white hover:bg-slate-900" :disabled="saving || categoriesLoading || !!categoriesError || feeOptionsLoading || !!feeOptionsError || (!isNew && !dirty)">{{ saving ? 'Zapisywanie…' : isNew ? 'Utwórz turnus' : 'Zapisz zmiany' }}</button>
          </div>
        </div>
      </form>
    </div>
  </main>
</template>

<style scoped>
.turnus-card { @apply rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5; }
.turnus-control { @apply block min-h-10 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-slate-500 focus:outline-none focus:ring-2 focus:ring-slate-500 focus:ring-offset-1 read-only:bg-slate-50 disabled:cursor-not-allowed disabled:bg-slate-100; }
.turnus-number { @apply h-9 w-20 shrink-0 rounded-lg border border-slate-300 bg-white px-2 text-center text-sm text-slate-900 focus:border-slate-500 focus:outline-none focus:ring-2 focus:ring-slate-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:bg-slate-100; }
.turnus-control[aria-invalid="true"], .turnus-number[aria-invalid="true"] { @apply border-red-400 focus:border-red-500 focus:ring-red-500; }
.turnus-button { @apply inline-flex min-h-10 items-center justify-center rounded-lg px-4 py-2 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50; }
</style>
