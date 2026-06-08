<script setup>
import {onMounted, ref, watch, computed} from 'vue'
import {useAuthStore} from '../stores/auth'
import {fetchRegistrations} from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'
import AcceptRejectModal from './AcceptRejectModal.vue'
import RegistrationDetailModal from './RegistrationDetailModal.vue'
import { formatDateTime } from '../utils/dateUtils'
const props = defineProps({
  filters: {
    type: Object,
    required: true,
  },
})

const authStore = useAuthStore()
const registrations = ref([])
const loading = ref(false)
const errorMessage = ref('')
const selectedRegistration = ref(null)
const modalAction = ref('ACCEPT')
const detailCode = ref(null)

const visibleRegistrations = computed(() => {
  const q = props.filters.search?.trim().toLowerCase()

  if (!q) {
    return registrations.value
  }

  return registrations.value.filter(r =>
      r.registrationCode?.toLowerCase().includes(q) ||
      r.firstName?.toLowerCase().includes(q) ||
      r.lastName?.toLowerCase().includes(q) ||
      `${r.firstName || ''} ${r.lastName || ''}`.toLowerCase().includes(q)
  )
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
        <th class="py-2 pr-3">Kod zgłoszenia</th>
        <th class="py-2 pr-3">Typ</th>
        <th class="py-2 pr-3">Turnus</th>
        <th class="py-2 pr-3">Imię i nazwisko</th>
        <th class="py-2 pr-3">Wiek</th>
        <th class="py-2 pr-3">Status</th>
        <th class="py-2 pr-3">Data zgłoszenia</th>
        <th class="py-2">Akcje</th>
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
        @updated="loadRegistrations"
    />

    <RegistrationDetailModal
        v-if="detailCode"
        :code="detailCode"
        @close="closeDetail"
    />
  </section>
</template>
