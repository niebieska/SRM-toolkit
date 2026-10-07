<script setup>
import {onMounted, ref, watch, computed} from 'vue'
import {useAuthStore} from '../stores/auth'
import {fetchRegistrations} from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'
import AcceptRejectModal from './AcceptRejectModal.vue'
import RegistrationDetailModal from './RegistrationDetailModal.vue'
import { formatDateTime } from '../utils/dateUtils'
import { sortRegistrations } from '../utils/registrationSorting'
const props = defineProps({
  filters: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['status-updated'])

const authStore = useAuthStore()
const registrations = ref([])
const loading = ref(false)
const errorMessage = ref('')
const selectedRegistration = ref(null)
const modalAction = ref('ACCEPT')
const detailCode = ref(null)
const sortKey = ref('createdAt')
const sortDirection = ref('asc')
const columns = [
  { key: 'registrationCode', label: 'Kod zgłoszenia' },
  { key: 'registrationType', label: 'Typ' },
  { key: 'turnusCode', label: 'Turnus' },
  { key: 'name', label: 'Imię i nazwisko' },
  { key: 'age', label: 'Wiek' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Data zgłoszenia' },
]

function toggleSort(key) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDirection.value = 'asc'
  }
}

function ariaSort(key) {
  return sortKey.value === key ? (sortDirection.value === 'asc' ? 'ascending' : 'descending') : 'none'
}

const visibleRegistrations = computed(() => {
  const q = props.filters.search?.trim().toLowerCase()

  const filtered = !q ? registrations.value : registrations.value.filter(r =>
      r.registrationCode?.toLowerCase().includes(q) ||
      r.firstName?.toLowerCase().includes(q) ||
      r.lastName?.toLowerCase().includes(q) ||
      `${r.firstName || ''} ${r.lastName || ''}`.toLowerCase().includes(q)
  )
  return sortRegistrations(filtered, sortKey.value, sortDirection.value)
})

async function loadRegistrations() {
  loading.value = true
  errorMessage.value = ''
  try {
    registrations.value = await fetchRegistrations(authStore.token, {
      status: props.filters.status,
      registrationType: props.filters.registrationType,
      turnusCode: props.filters.turnusCode,
    })
  } catch (error) {
    errorMessage.value = 'Nie udało się pobrać listy zgłoszeń'
  } finally {
    loading.value = false
  }
}

function openModal(registration, action) {
  selectedRegistration.value = registration
  modalAction.value = action
}

function closeModal() {
  selectedRegistration.value = null
}

function openDetail(code) {
  detailCode.value = code
}

function closeDetail() {
  detailCode.value = null
}

function handleDetailStatusAction(registration, action) {
  closeDetail()
  openModal(registration, action)
}

async function handleStatusUpdated() {
  await loadRegistrations()
  emit('status-updated')
}

onMounted(loadRegistrations)

watch(
    () => [props.filters.status, props.filters.registrationType, props.filters.turnusCode],
    loadRegistrations,
)
</script>

<template>
  <section class="bg-white rounded-xl shadow p-4 overflow-x-auto">
    <p v-if="loading" class="text-slate-600">Ładowanie...</p>
    <p v-else-if="errorMessage" class="text-red-600">{{ errorMessage }}</p>
    <p v-else-if="visibleRegistrations.length === 0" class="text-slate-600">Brak zgłoszeń</p>

    <table v-else class="w-full min-w-[1050px] text-sm">
      <thead>
      <tr class="text-left border-b border-slate-200">
        <th v-for="column in columns" :key="column.key" scope="col"
            :aria-sort="ariaSort(column.key)" class="py-2 pr-3">
          <button type="button" class="inline-flex items-center gap-1.5 rounded hover:text-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-700"
                  
                  :title="`Sortuj: ${column.label} — ${sortKey === column.key && sortDirection === 'asc' ? 'malejąco' : 'rosnąco'}`"
                  @click="toggleSort(column.key)">
            {{ column.label }}
            <span aria-hidden="true" class="inline-block w-3 text-xs text-slate-500">
              {{ sortKey === column.key ? (sortDirection === 'asc' ? '↑' : '↓') : '' }}
            </span>
          </button>
        </th>
        <th scope="col" class="py-2">Akcje</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="registration in visibleRegistrations" :key="registration.registrationCode"
          class="border-b border-slate-100">
        <td class="py-2 pr-3">{{ registration.registrationCode }}</td>
        <td class="py-2 pr-3">{{ registration.registrationType === 'PARTICIPANT' ? 'Uczestnik' : 'Kadra' }}</td>
        <td class="py-2 pr-3">{{ registration.turnusCode }}</td>
        <td class="py-2 pr-3">{{ registration.firstName || '–' }} {{ registration.lastName || '' }}</td>
        <td class="py-2 pr-3">{{ registration.age != null ? registration.age : '–' }}</td>
        <td class="py-2 pr-3">
          <StatusBadge :status="registration.status"/>
        </td>
        <td class="py-2 pr-3">{{ formatDateTime(registration.createdAt) }}</td>
        <td class="py-2 space-x-2">
          <button
              class="rounded bg-slate-600 text-white px-3 py-1 hover:bg-slate-700"
              @click="openDetail(registration.registrationCode)"
          >
            Szczegóły
          </button>
          <button
              v-if="registration.status !== 'ACCEPTED'"
              class="rounded bg-green-600 text-white px-3 py-1 hover:bg-green-700"
              @click="openModal(registration, 'ACCEPT')"
          >
            Zaakceptuj
          </button>
          <button
              v-if="registration.status !== 'WAITLIST'"
              class="rounded bg-amber-600 text-white px-3 py-1 hover:bg-amber-700"
              @click="openModal(registration, 'WAITLIST')"
          >
            Rezerwa
          </button>
          <button
              v-if="registration.status !== 'REJECTED'"
              class="rounded bg-red-600 text-white px-3 py-1 hover:bg-red-700"
              @click="openModal(registration, 'REJECT')"
          >
            Odrzuć
          </button>
        </td>
      </tr>
      </tbody>
    </table>

    <AcceptRejectModal
        v-if="selectedRegistration"
        :registration="selectedRegistration"
        :action="modalAction"
        @close="closeModal"
        @updated="handleStatusUpdated"
    />

    <RegistrationDetailModal
        v-if="detailCode"
        :code="detailCode"
        @close="closeDetail"
        @status-action="handleDetailStatusAction"
    />
  </section>
</template>
