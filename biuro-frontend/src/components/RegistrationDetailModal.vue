<script setup>
import { onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { fetchRegistrationDetail } from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({
  code: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['close'])

const authStore = useAuthStore()
const detail = ref(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    detail.value = await fetchRegistrationDetail(authStore.token, props.code)
  } catch {
    errorMessage.value = 'Nie udało się pobrać szczegółów zgłoszenia'
  } finally {
    loading.value = false
  }
})

function formattedPayload(payload) {
  return JSON.stringify(payload, null, 2)
}
</script>

<template>
  <div class="fixed inset-0 bg-black/40 flex items-start justify-center p-4 overflow-y-auto z-50">
    <div class="w-full max-w-2xl bg-white rounded-xl p-6 space-y-5 my-8">

      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">Szczegóły zgłoszenia</h2>
        <button class="text-slate-500 hover:text-slate-800 text-xl leading-none" @click="emit('close')">✕</button>
      </div>

      <p v-if="loading" class="text-slate-600">Ładowanie...</p>
      <p v-else-if="errorMessage" class="text-red-600">{{ errorMessage }}</p>

      <template v-else-if="detail">

        <!-- Header info -->
        <section>
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Zgłoszenie</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Kod</dt>
            <dd>{{ detail.registrationCode }}</dd>
            <dt class="text-slate-500">Typ</dt>
            <dd>{{ detail.registrationType === 'PARTICIPANT' ? 'Uczestnik' : 'Kadra' }}</dd>
            <dt class="text-slate-500">Turnus</dt>
            <dd>{{ detail.turnusCode }}</dd>
            <dt class="text-slate-500">Status</dt>
            <dd><StatusBadge :status="detail.status" /></dd>
            <template v-if="detail.rejectionReason">
              <dt class="text-slate-500">Powód odrzucenia</dt>
              <dd>{{ detail.rejectionReason }}</dd>
            </template>
            <dt class="text-slate-500">Data zgłoszenia</dt>
            <dd>{{ detail.createdAt }}</dd>
          </dl>
        </section>

        <!-- Full payload as JSON -->
        <section v-if="detail.payload">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Dane zgłoszenia (JSON)</h3>
          <pre class="bg-slate-50 border border-slate-200 rounded-lg p-4 text-xs overflow-x-auto whitespace-pre-wrap break-words">{{ formattedPayload(detail.payload) }}</pre>
        </section>
        <p v-else class="text-sm text-slate-500">Brak danych zgłoszenia</p>

      </template>

      <div class="flex justify-end pt-2">
        <button class="rounded border border-slate-300 px-4 py-2 hover:bg-slate-50" @click="emit('close')">
          Zamknij
        </button>
      </div>
    </div>
  </div>
</template>
