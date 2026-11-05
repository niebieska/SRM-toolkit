<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import PolishDatePicker from '../components/PolishDatePicker.vue'
import { useAuthStore } from '../stores/auth'
import { fetchTurnuses } from '../api/turnusApi'
import { createDemoPaymentRegistrations, paymentBalance, paymentStatus, paymentTotal } from '../utils/demoPayments'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const turnuses = ref([])
const loading = ref(false)
const error = ref('')
const search = ref('')
const statusFilter = ref('')
const registrationTypeFilter = ref('')
const turnusFilter = computed({
  get: () => typeof route.query.turnus === 'string' ? route.query.turnus : '',
  set: value => router.replace({ query: { ...route.query, turnus: value || undefined } }),
})
const selectedId = ref('')
const lookupOpen = ref(false)
const registrationLookup = ref('')
const registrationLookupError = ref('')
const registrationLookupResults = ref([])
const formMode = ref('')
const editingPaymentId = ref('')
const formError = ref('')
const demoMessage = ref('')
let requestSequence = 0

const registrations = ref([])
const selectedRegistration = computed(() => registrations.value.find(item => item.id === selectedId.value) || null)
const latestDiscountHistoryEntry = computed(() => {
  const history = selectedRegistration.value?.discountHistory || []
  return history.length ? history[history.length - 1] : null
})
const scopedRegistrations = computed(() => registrations.value.filter(item => !turnusFilter.value || item.turnusCode === turnusFilter.value))
const visibleRegistrations = computed(() => scopedRegistrations.value.filter(item => {
  const statusMatches = !statusFilter.value || paymentStatus(item) === statusFilter.value
  const typeMatches = !registrationTypeFilter.value || item.registrationType === registrationTypeFilter.value
  const normalizedSearch = search.value.trim().toLocaleLowerCase('pl-PL')
  const searchable = `${item.registrationCode} ${item.firstName} ${item.lastName} ${item.role || ''} ${item.turnusName} ${item.turnusCode}`.toLocaleLowerCase('pl-PL')
  return statusMatches && typeMatches && (!normalizedSearch || searchable.includes(normalizedSearch))
}).sort((left, right) => left.lastName.localeCompare(right.lastName, 'pl') || left.firstName.localeCompare(right.firstName, 'pl')))
const hasActiveFilters = computed(() => Boolean(statusFilter.value || registrationTypeFilter.value || turnusFilter.value || search.value))
const editingPayment = computed(() => selectedRegistration.value?.payments.find(item => item.id === editingPaymentId.value) || null)
const paymentForm = reactive({ date: today(), amount: '', method: 'Przelew', label: '', customLabel: '', note: '', reason: '' })
const discountForm = reactive({ amount: '', description: '' })
const predefinedPaymentLabels = ['Zaliczka', 'Dopłata', 'Wpłata całej kwoty']

function clearFilters() {
  statusFilter.value = ''
  registrationTypeFilter.value = ''
  turnusFilter.value = ''
  search.value = ''
}

function today() { return new Date().toISOString().slice(0, 10) }
function formatMoney(value) {
  return new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN', maximumFractionDigits: 2 }).format(value)
}
function formatDate(value) {
  if (!value) return '—'
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}
function statusLabel(registration) {
  return ({
    NO_PAYMENT: 'Brak wpłaty',
    OWED: 'Do dopłaty',
    PAID: 'Opłacone',
    OVERPAID: 'Nadpłata',
    DISCOUNT_SETTLED: 'Rozliczono rabatem',
  })[paymentStatus(registration)]
}
function paymentStatusSummary(registration) {
  const status = paymentStatus(registration)
  if (status === 'NO_PAYMENT') return `Brak wpłaty: ${formatMoney(paymentBalance(registration))}`
  if (status === 'OWED') return `Do dopłaty: ${formatMoney(paymentBalance(registration))}`
  if (status === 'OVERPAID') return `Nadpłata: ${formatMoney(Math.abs(paymentBalance(registration)))}`
  if (status === 'DISCOUNT_SETTLED') return 'Rozliczono rabatem'
  return 'Opłacono'
}
function statusClass(registration) {
  return ({
    NO_PAYMENT: 'border-red-200 bg-red-50 text-red-800',
    OWED: 'border-amber-200 bg-amber-50 text-amber-900',
    PAID: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    OVERPAID: 'border-violet-200 bg-violet-50 text-violet-800',
    DISCOUNT_SETTLED: 'border-slate-200 bg-slate-100 text-slate-700',
  })[paymentStatus(registration)]
}
function openRegistration(registration) {
  selectedId.value = registration.id
  formMode.value = ''
  formError.value = ''
  demoMessage.value = ''
}
function openRegistrationLookup() {
  registrationLookup.value = ''
  registrationLookupError.value = ''
  registrationLookupResults.value = []
  lookupOpen.value = true
}
function findRegistrationForPayment() {
  const query = registrationLookup.value.trim()
  if (!query) {
    registrationLookupError.value = 'Wpisz kod zgłoszenia lub nazwisko.'
    registrationLookupResults.value = []
    return
  }
  const normalizedQuery = query.toLocaleLowerCase('pl-PL')
  const normalizedCode = query.toLocaleUpperCase('pl-PL')
  const matches = registrations.value.filter(item =>
    item.registrationCode.toLocaleUpperCase('pl-PL').includes(normalizedCode)
      || item.lastName.toLocaleLowerCase('pl-PL').includes(normalizedQuery)
  ).sort((left, right) => left.lastName.localeCompare(right.lastName, 'pl') || left.firstName.localeCompare(right.firstName, 'pl'))
  registrationLookupResults.value = matches
  if (!matches.length) {
    registrationLookupError.value = 'Nie znaleziono zgłoszenia z przypisaną opłatą. Sprawdź numer i spróbuj ponownie.'
    return
  }
  registrationLookupError.value = ''
  if (matches.length === 1) selectRegistrationForPayment(matches[0])
}
function selectRegistrationForPayment(registration) {
  lookupOpen.value = false
  openRegistration(registration)
  openPaymentForm()
}
function closeDialog() {
  selectedId.value = ''
  formMode.value = ''
  formError.value = ''
}
function openPaymentForm(payment = null) {
  formMode.value = payment ? 'edit-payment' : 'add-payment'
  editingPaymentId.value = payment?.id || ''
  const savedLabel = payment?.label || ''
  const isPredefinedLabel = predefinedPaymentLabels.includes(savedLabel)
  Object.assign(paymentForm, payment
    ? { date: payment.date, amount: payment.amount, method: payment.method, label: isPredefinedLabel ? savedLabel : savedLabel ? 'CUSTOM' : '', customLabel: isPredefinedLabel ? '' : savedLabel, note: payment.note || '', reason: '' }
    : { date: today(), amount: '', method: 'Przelew', label: '', customLabel: '', note: '', reason: '' })
  formError.value = ''
}
function openDiscountForm() {
  formMode.value = 'discount'
  Object.assign(discountForm, { amount: selectedRegistration.value.discount, description: '' })
  formError.value = ''
}
function savePayment() {
  if (!selectedRegistration.value) return
  const amount = Number(paymentForm.amount)
  const paymentLabel = paymentForm.label === 'CUSTOM' ? paymentForm.customLabel.trim() : paymentForm.label
  if (!paymentForm.date || !Number.isFinite(amount) || amount <= 0) {
    formError.value = 'Podaj datę i kwotę większą od 0 zł.'
    return
  }
  if (!paymentLabel) {
    formError.value = 'Wybierz opis wpłaty lub wpisz własny opis.'
    return
  }
  if (formMode.value === 'edit-payment' && !paymentForm.reason.trim()) {
    formError.value = 'Podaj powód korekty wpłaty.'
    return
  }
  if (editingPayment.value) {
    const previous = { date: editingPayment.value.date, amount: editingPayment.value.amount, method: editingPayment.value.method, label: editingPayment.value.label, note: editingPayment.value.note }
    Object.assign(editingPayment.value, { date: paymentForm.date, amount, method: paymentForm.method, label: paymentLabel, note: paymentForm.note.trim() })
    editingPayment.value.history ||= []
    editingPayment.value.history.push({ previous, changedAt: new Date().toLocaleString('pl-PL'), actor: 'operator.demo', reason: paymentForm.reason.trim() })
    demoMessage.value = 'Zmieniono przykładową wpłatę. Zmiana nie została zapisana.'
  } else {
    selectedRegistration.value.payments.push({ id: `demo-pay-${Date.now()}`, date: paymentForm.date, amount, method: paymentForm.method, label: paymentLabel, note: paymentForm.note.trim(), history: [] })
    demoMessage.value = 'Dodano przykładową wpłatę. Zmiana nie została zapisana.'
  }
  formMode.value = ''
  formError.value = ''
}
function saveDiscount() {
  const amount = Number(discountForm.amount)
  if (!Number.isFinite(amount) || amount < 0 || amount > selectedRegistration.value.fee) {
    formError.value = `Rabat musi mieścić się w zakresie od 0 do ${formatMoney(selectedRegistration.value.fee)}.`
    return
  }
  if (amount !== selectedRegistration.value.discount && !discountForm.description.trim()) {
    formError.value = 'Podaj opis rabatu.'
    return
  }
  if (amount !== selectedRegistration.value.discount) {
    selectedRegistration.value.discountHistory.push({
      previousAmount: selectedRegistration.value.discount,
      amount,
      changedAt: new Date().toLocaleString('pl-PL'),
      actor: 'operator.demo',
      description: discountForm.description.trim(),
    })
    selectedRegistration.value.discount = amount
    demoMessage.value = 'Zmieniono przykładowy rabat. Zmiana nie została zapisana.'
  }
  formMode.value = ''
  formError.value = ''
}

async function loadPayments() {
  const sequence = ++requestSequence
  turnuses.value = []
  registrations.value = []
  error.value = ''
  loading.value = true
  try {
    const result = await fetchTurnuses(auth.token)
    if (sequence === requestSequence) {
      turnuses.value = result
      registrations.value = result.flatMap(turnus => createDemoPaymentRegistrations(turnus.turnusCode).map(item => ({
        ...item,
        turnusName: turnus.turnusName,
        turnusCode: turnus.turnusCode,
        payments: item.payments.map(payment => ({ ...payment, history: [] })),
        discountHistory: item.discountHistory.map(entry => ({ ...entry })),
      })))
    }
  } catch (exception) {
    if (sequence === requestSequence) error.value = exception.message || 'Nie udało się wczytać wpłat.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
watch(() => auth.token, loadPayments, { immediate: true })
onBeforeUnmount(() => { requestSequence++ })
</script>

<template>
  <main class="min-h-screen p-4 md:p-8">
    <div class="mx-auto max-w-6xl space-y-4">
      <BiuroHeader />
      <h2 class="text-xl font-semibold text-slate-900">Wpłaty</h2>

      <p v-if="loading" role="status" class="text-sm text-slate-600">Wczytywanie wpłat…</p>
      <div v-else-if="error" role="alert" class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
        {{ error }} <button type="button" class="ml-2 rounded underline focus-visible:ring-2 focus-visible:ring-red-600" @click="loadPayments">Spróbuj ponownie</button>
      </div>
      <template v-else>
        <aside class="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950" role="note">
          <span aria-hidden="true" class="mt-px font-bold">ⓘ</span>
          <p><strong>Widok demonstracyjny.</strong> Dane osób i wpłat są przykładowe. Wprowadzone tu zmiany istnieją tylko do odświeżenia strony.</p>
        </aside>

        <div class="flex justify-end">
          <button type="button" class="whitespace-nowrap rounded bg-slate-600 px-3 py-1 text-sm text-white hover:bg-slate-700" @click="openRegistrationLookup">Dodaj wpłatę</button>
        </div>

        <section class="grid grid-cols-1 gap-3 rounded-xl bg-white p-4 shadow md:grid-cols-2 lg:grid-cols-[repeat(4,minmax(0,1fr))_auto]">
          <select v-model="statusFilter" aria-label="Status opłaty" class="min-w-0 rounded border border-slate-300 px-3 py-2">
            <option value="">Wszystkie statusy</option>
            <option value="NO_PAYMENT">Brak wpłaty</option>
            <option value="OWED">Do dopłaty</option>
            <option value="PAID">Opłacone</option>
            <option value="OVERPAID">Nadpłata</option>
            <option value="DISCOUNT_SETTLED">Rozliczono rabatem</option>
          </select>
          <select v-model="registrationTypeFilter" aria-label="Typ zgłoszenia" class="min-w-0 rounded border border-slate-300 px-3 py-2">
            <option value="">Wszystkie typy</option>
            <option value="PARTICIPANT">Uczestnik</option>
            <option value="STAFF">Kadra</option>
          </select>
          <select v-model="turnusFilter" aria-label="Turnus" class="min-w-0 rounded border border-slate-300 px-3 py-2">
            <option value="">Wszystkie turnusy</option>
            <option v-for="item in turnuses" :key="item.turnusCode" :value="item.turnusCode">{{ item.turnusName }}</option>
          </select>
          <div class="relative min-w-0">
            <input v-model="search" aria-label="Wyszukaj wpłaty" placeholder="Wyszukaj" class="w-full rounded border border-slate-300 px-3 py-2 pr-9" />
            <button v-if="search" type="button" class="absolute inset-y-0 right-2 my-auto h-6 w-6 rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-800" aria-label="Wyczyść wyszukiwanie" @click="search = ''">×</button>
          </div>
          <div class="flex justify-end md:col-span-2 lg:col-span-1">
            <button type="button" class="whitespace-nowrap rounded border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50" :disabled="!hasActiveFilters" @click="clearFilters">Wyczyść filtry</button>
          </div>
        </section>

        <section class="overflow-x-auto rounded-xl bg-white p-4 shadow">
          <div v-if="!visibleRegistrations.length" class="p-8 text-center text-sm text-slate-600">Brak zgłoszeń pasujących do wyszukiwania i filtra.</div>
          <table v-else class="w-full min-w-[1050px] whitespace-nowrap text-left text-sm">
            <thead>
              <tr class="border-b border-slate-200 text-left">
                <th scope="col" class="py-2 pr-3">Kod zgłoszenia</th>
                <th scope="col" class="py-2 pr-3">Typ</th>
                <th scope="col" class="py-2 pr-3">Turnus</th>
                <th scope="col" class="py-2 pr-3">Imię i nazwisko</th>
                <th scope="col" class="py-2 pr-3 text-right">Rabat</th>
                <th scope="col" class="py-2 pr-3 text-right">Wpłacono</th>
                <th scope="col" class="py-2 pr-3 text-right">Pozostało</th>
                <th scope="col" class="py-2 pr-3">Status opłaty</th>
                <th scope="col" class="py-2">Akcje</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="registration in visibleRegistrations" :key="registration.id" class="border-b border-slate-100">
                <td class="py-2 pr-3">{{ registration.registrationCode }}</td>
                <td class="py-2 pr-3">{{ registration.registrationType === 'PARTICIPANT' ? 'Uczestnik' : 'Kadra' }}</td>
                <td class="py-2 pr-3">{{ registration.turnusCode }}</td>
                <td class="py-2 pr-3">{{ registration.firstName || '–' }} {{ registration.lastName || '' }}</td>
                <td class="py-2 pr-3 text-right">{{ registration.discount ? `−${formatMoney(registration.discount)}` : '–' }}</td>
                <td class="py-2 pr-3 text-right">{{ formatMoney(paymentTotal(registration)) }}</td>
                <td class="py-2 pr-3 text-right">{{ formatMoney(paymentBalance(registration)) }}</td>
                <td class="py-2 pr-3"><span class="inline-flex whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium" :class="statusClass(registration)">{{ statusLabel(registration) }}</span></td>
                <td class="py-2"><button type="button" class="rounded bg-slate-600 px-3 py-1 text-white hover:bg-slate-700" @click="openRegistration(registration)">Szczegóły</button></td>
              </tr>
            </tbody>
          </table>
          <p class="border-t border-slate-100 px-4 py-3 text-xs text-slate-500">{{ visibleRegistrations.length }} z {{ scopedRegistrations.length }} przykładowych zgłoszeń</p>
        </section>
      </template>

      <div v-if="lookupOpen" class="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/40 p-0 sm:items-center sm:p-4" @click.self="lookupOpen = false" @keydown.esc="lookupOpen = false">
        <section role="dialog" aria-modal="true" aria-labelledby="payment-lookup-title" class="w-full rounded-t-2xl bg-white shadow-xl sm:max-w-md sm:rounded-2xl">
          <form class="space-y-4 p-5 sm:p-6" @submit.prevent="findRegistrationForPayment">
            <header class="flex items-start justify-between gap-4">
              <div>
                <h3 id="payment-lookup-title" class="text-lg font-semibold text-slate-900">Dodaj wpłatę</h3>
                <p class="mt-1 text-sm text-slate-600">Wyszukaj zgłoszenie, do którego chcesz odnotować wpłatę.</p>
              </div>
              <button type="button" aria-label="Zamknij" class="rounded-lg px-2 py-1 text-xl leading-none text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" @click="lookupOpen = false">×</button>
            </header>
            <label for="registration-payment-search" class="block text-sm font-medium text-slate-700">Kod zgłoszenia lub nazwisko
              <input id="registration-payment-search" v-model="registrationLookup" type="search" required autocomplete="off" placeholder="Np. REG-P-ZAGLE26T1-18 lub Kowalska" class="mt-1 h-11 w-full rounded-lg border border-slate-300 px-3 text-sm focus:border-slate-500 focus:outline-none focus:ring-2 focus:ring-slate-500" @input="registrationLookupResults = []; registrationLookupError = ''" />
            </label>
            <p v-if="registrationLookupError" role="alert" class="text-sm text-red-700">{{ registrationLookupError }}</p>
            <ul v-if="registrationLookupResults.length > 1" class="max-h-64 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200" aria-label="Wyniki wyszukiwania zgłoszeń">
              <li v-for="registration in registrationLookupResults" :key="registration.id" class="flex items-center justify-between gap-3 p-3">
                <div class="min-w-0">
                  <p class="font-medium text-slate-900">{{ registration.firstName }} {{ registration.lastName }}</p>
                  <p class="mt-0.5 truncate font-mono text-xs text-slate-500">{{ registration.registrationCode }}</p>
                  <p class="mt-0.5 truncate text-xs text-slate-500">{{ registration.turnusName }}</p>
                </div>
                <button type="button" class="shrink-0 rounded bg-slate-600 px-3 py-1.5 text-xs text-white hover:bg-slate-700" @click="selectRegistrationForPayment(registration)">Wybierz</button>
              </li>
            </ul>
            <div class="flex justify-end gap-2 border-t border-slate-100 pt-4">
              <button type="button" class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" @click="lookupOpen = false">Anuluj</button>
              <button type="submit" class="rounded-lg bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2">Szukaj</button>
            </div>
          </form>
        </section>
      </div>

      <div v-if="selectedRegistration" class="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/40 p-0 sm:items-center sm:p-4" @click.self="closeDialog" @keydown.esc="closeDialog">
        <section role="dialog" aria-modal="true" :aria-labelledby="'payment-dialog-title'" class="max-h-[92vh] w-full overflow-y-auto rounded-t-2xl bg-white shadow-xl sm:max-w-2xl sm:rounded-2xl">
          <header class="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 sm:px-6">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h3 id="payment-dialog-title" class="text-lg font-semibold text-slate-900">{{ selectedRegistration.lastName }}, {{ selectedRegistration.firstName }}</h3>
                <span class="inline-flex whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium" :class="statusClass(selectedRegistration)">{{ paymentStatusSummary(selectedRegistration) }}</span>
              </div>
              <p class="mt-1 font-mono text-xs text-slate-500">{{ selectedRegistration.registrationCode }}</p>
            </div>
            <button type="button" aria-label="Zamknij" class="rounded-lg px-2 py-1 text-xl leading-none text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" @click="closeDialog">×</button>
          </header>
          <div class="space-y-5 px-5 py-5 sm:px-6">
            <section aria-labelledby="payment-history-heading">
              <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                <h4 id="payment-history-heading" class="text-sm font-semibold text-slate-900">Zarejestrowane wpłaty</h4>
                <button type="button" class="rounded-lg bg-slate-800 px-3 py-2 text-xs font-semibold text-white hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2" @click="openPaymentForm()">Dodaj wpłatę</button>
              </div>
              <p v-if="!selectedRegistration.payments.length" class="rounded-lg border border-dashed border-slate-300 px-4 py-5 text-center text-sm text-slate-500">Brak zarejestrowanych wpłat.</p>
              <div v-else class="overflow-x-auto rounded-lg border border-slate-200">
                <table class="w-full min-w-[480px] text-left text-sm">
                  <thead class="bg-slate-50 text-xs text-slate-500"><tr><th class="px-3 py-2">Data</th><th class="px-3 py-2">Opis</th><th class="px-3 py-2">Metoda</th><th class="px-3 py-2 text-right">Kwota</th><th class="px-3 py-2"><span class="sr-only">Akcje</span></th></tr></thead>
                  <tbody class="divide-y divide-slate-100">
                    <tr v-for="payment in selectedRegistration.payments" :key="payment.id">
                      <td class="whitespace-nowrap px-3 py-2.5 text-slate-700">{{ formatDate(payment.date) }}</td>
                      <td class="px-3 py-2.5 text-slate-700">{{ payment.label || payment.note || 'Wpłata' }}</td>
                      <td class="px-3 py-2.5 text-slate-700">{{ payment.method }}</td>
                      <td class="whitespace-nowrap px-3 py-2.5 text-right font-medium text-slate-900">{{ formatMoney(payment.amount) }}</td>
                      <td class="px-3 py-2.5 text-right"><button type="button" class="rounded px-2 py-1 text-xs font-medium text-slate-700 underline hover:bg-slate-50 focus-visible:ring-2 focus-visible:ring-slate-500" @click="openPaymentForm(payment)">Edytuj</button></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section v-if="selectedRegistration.discount > 0" aria-label="Przyznany rabat" class="space-y-2">
              <h4 class="text-sm font-semibold text-slate-900">Przyznany rabat</h4>
              <article class="rounded-lg border border-slate-200 bg-white p-3">
                <p class="text-sm font-semibold text-violet-800">Rabat: {{ formatMoney(selectedRegistration.discount) }}</p>
                <p v-if="latestDiscountHistoryEntry?.description" class="mt-2 text-sm text-slate-700">{{ latestDiscountHistoryEntry.description }}</p>
                <p v-if="latestDiscountHistoryEntry" class="mt-1 text-xs text-slate-500">{{ latestDiscountHistoryEntry.actor }} · {{ latestDiscountHistoryEntry.changedAt }}</p>
              </article>
            </section>
            <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
              <button type="button" class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" @click="openDiscountForm">Edytuj rabat</button>
              <button type="button" class="rounded-lg px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" @click="closeDialog">Zamknij</button>
            </div>

            <form v-if="formMode" class="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4" @submit.prevent="formMode === 'discount' ? saveDiscount() : savePayment()">
              <div class="flex items-center justify-between gap-2">
                <h4 class="font-semibold text-slate-900">{{ formMode === 'discount' ? 'Rabat dla zgłoszenia' : formMode === 'edit-payment' ? 'Edycja wpłaty' : 'Nowa wpłata' }}</h4>
                <button type="button" class="rounded px-2 py-1 text-sm text-slate-600 hover:bg-slate-200 focus-visible:ring-2 focus-visible:ring-slate-500" @click="formMode = ''; formError = ''">Anuluj</button>
              </div>
              <div v-if="formMode === 'discount'" class="grid gap-3 sm:grid-cols-2">
                <label class="text-sm font-medium text-slate-700">Kwota rabatu (zł)
                  <input v-model.number="discountForm.amount" type="number" min="0" :max="selectedRegistration.fee" step="0.01" required class="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm focus:ring-2 focus:ring-slate-500" />
                </label>
                <label class="text-sm font-medium text-slate-700">Opis rabatu <span v-if="Number(discountForm.amount) !== selectedRegistration.discount" class="text-red-700">*</span>
                  <input v-model="discountForm.description" type="text" maxlength="300" :required="Number(discountForm.amount) !== selectedRegistration.discount" placeholder="Krótki opis rabatu" class="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm focus:ring-2 focus:ring-slate-500" />
                </label>
              </div>
              <div v-else class="grid gap-3 sm:grid-cols-2">
                <label class="text-sm font-medium text-slate-700">Data wpłaty
                  <PolishDatePicker v-model="paymentForm.date" required />
                </label>
                <label class="text-sm font-medium text-slate-700">Kwota (zł)
                  <input v-model.number="paymentForm.amount" type="number" min="0.01" step="0.01" required class="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm focus:ring-2 focus:ring-slate-500" />
                </label>
                <label class="text-sm font-medium text-slate-700">Metoda
                  <select v-model="paymentForm.method" class="mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm focus:ring-2 focus:ring-slate-500"><option>Przelew</option><option>Gotówka</option></select>
                </label>
                <label class="text-sm font-medium text-slate-700">Opis wpłaty
                  <select v-model="paymentForm.label" required class="mt-1 h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm focus:ring-2 focus:ring-slate-500">
                    <option value="" disabled>Wybierz opis</option>
                    <option v-for="label in predefinedPaymentLabels" :key="label" :value="label">{{ label }}</option>
                    <option value="CUSTOM">Własny opis</option>
                  </select>
                </label>
                <label v-if="paymentForm.label === 'CUSTOM'" class="text-sm font-medium text-slate-700">Własny opis
                  <input v-model="paymentForm.customLabel" type="text" maxlength="100" required placeholder="Wpisz opis wpłaty" class="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm focus:ring-2 focus:ring-slate-500" />
                </label>
                <label class="text-sm font-medium text-slate-700 sm:col-span-2">Uwagi <span class="font-normal text-slate-500">(opcjonalnie)</span>
                  <input v-model="paymentForm.note" type="text" maxlength="300" class="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm focus:ring-2 focus:ring-slate-500" />
                </label>
                <label v-if="formMode === 'edit-payment'" class="text-sm font-medium text-slate-700 sm:col-span-2">Powód korekty <span class="text-red-700">*</span>
                  <input v-model="paymentForm.reason" type="text" maxlength="300" required placeholder="Krótki powód korekty" class="mt-1 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm focus:ring-2 focus:ring-slate-500" />
                </label>
              </div>
              <p v-if="formError" role="alert" class="text-sm text-red-700">{{ formError }}</p>
              <div class="flex justify-end">
                <button type="submit" class="rounded-lg bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2">{{ formMode === 'discount' ? 'Zapisz rabat' : formMode === 'edit-payment' ? 'Zapisz korektę' : 'Dodaj wpłatę' }}</button>
              </div>
            </form>
            <p v-if="demoMessage" role="status" class="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{{ demoMessage }}</p>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>
