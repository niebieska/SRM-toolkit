<script setup>
import { onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { fetchRegistrations } from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'
import AcceptRejectModal from './AcceptRejectModal.vue'

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
    <p v-else-if="registrations.length === 0" class="text-slate-600">Brak zgłoszeń</p>

    <table v-else class="w-full min-w-[850px] text-sm">
      <thead>
        <tr class="text-left border-b border-slate-200">
          <th class="py-2">Kod zgłoszenia</th>
          <th class="py-2">Typ</th>
          <th class="py-2">Turnus</th>
          <th class="py-2">Małoletni</th>
          <th class="py-2">Status</th>
          <th class="py-2">Data zgłoszenia</th>
          <th class="py-2">Akcje</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="registration in registrations" :key="registration.registrationCode" class="border-b border-slate-100">
          <td class="py-2">{{ registration.registrationCode }}</td>
          <td class="py-2">{{ registration.registrationType === 'PARTICIPANT' ? 'Uczestnik' : 'Kadra' }}</td>
          <td class="py-2">{{ registration.turnusCode }}</td>
          <td class="py-2">{{ registration.minor ? 'Tak' : 'Nie' }}</td>
          <td class="py-2"><StatusBadge :status="registration.status" /></td>
          <td class="py-2">{{ registration.createdAt }}</td>
          <td class="py-2 space-x-2">
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
  </section>
</template>
