<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { updateRegistrationStatus } from '../api/registrationApi'

const props = defineProps({
  registration: {
    type: Object,
    required: true,
  },
  action: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['close', 'updated'])

const authStore = useAuthStore()
const rejectionReason = ref('')
const selectedReasons = ref([])
const loading = ref(false)
const errorMessage = ref('')
const personName = computed(() => {
  const person = props.registration.payload?.person || props.registration
  return [person.firstName, person.lastName].filter(Boolean).join(' ') || 'Brak imienia i nazwiska'
})

const otherReason = 'Inny powód'
const predefinedRejectionReasons = computed(() => [
  'Brak wolnych miejsc',
  'Niezgodność z wymaganiami turnusu',
  'Rezygnacja zgłaszającego',
  'Zgłoszenie omyłkowe',
  ...(props.registration.registrationType === 'STAFF' ? ['Brak zapotrzebowania na wybraną rolę'] : []),
  otherReason,
])
const hasOtherReason = computed(() => selectedReasons.value.includes(otherReason))

const actionConfig = computed(() => {
  if (props.action === 'ACCEPT') {
    return {
      title: 'Potwierdź akceptację',
      status: 'ACCEPTED',
      buttonClasses: 'bg-green-600 hover:bg-green-700',
    }
  }
  if (props.action === 'WAITLIST') {
    return {
      title: 'Potwierdź wpisanie na listę rezerwową',
      status: 'WAITLIST',
      buttonClasses: 'bg-amber-600 hover:bg-amber-700',
    }
  }
  return {
    title: 'Potwierdź odrzucenie',
    status: 'REJECTED',
    buttonClasses: 'bg-red-600 hover:bg-red-700',
  }
})

const combinedRejectionReason = computed(() => {
  return [...selectedReasons.value.filter(reason => reason !== otherReason),
    hasOtherReason.value ? rejectionReason.value.trim() : '']
    .filter(Boolean)
    .join('; ')
})

async function confirm() {
  errorMessage.value = ''
  if (props.action === 'REJECT' && hasOtherReason.value && !rejectionReason.value.trim()) {
    errorMessage.value = 'Opisz inny powód odrzucenia'
    return
  }
  if (props.action === 'REJECT' && !combinedRejectionReason.value) {
    errorMessage.value = 'Powód odrzucenia jest wymagany'
    return
  }

  const payload = props.action === 'REJECT'
    ? { status: actionConfig.value.status, rejectionReason: combinedRejectionReason.value }
    : { status: actionConfig.value.status }

  loading.value = true
  try {
    await updateRegistrationStatus(authStore.token, props.registration.registrationCode, payload)
    emit('updated')
    emit('close')
  } catch (error) {
    errorMessage.value = error.message || 'Nie udało się zaktualizować statusu zgłoszenia'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 bg-black/40 flex items-center justify-center p-4">
    <div class="w-full max-w-lg bg-white rounded-xl p-5 space-y-4">
      <h2 class="text-lg font-semibold text-slate-900">{{ actionConfig.title }}</h2>
      <div class="space-y-1">
        <div class="flex flex-wrap items-center gap-2">
          <p class="min-w-0 break-words font-semibold text-slate-900">{{ personName }}</p>
          <span class="rounded-full border border-slate-300 px-2 py-0.5 text-xs font-medium text-slate-600">{{ registration.registrationType === 'STAFF' ? 'Kadra' : 'Uczestnik' }}</span>
        </div>
        <p class="break-all text-sm text-slate-500">Zgłoszenie: {{ registration.registrationCode }}</p>
      </div>

      <div v-if="action === 'REJECT'" class="space-y-3">
        <div class="space-y-2">
          <p class="text-sm font-medium">Powody odrzucenia</p>
          <label
            v-for="reason in predefinedRejectionReasons"
            :key="reason"
            class="flex items-start gap-2 text-sm text-slate-700"
          >
            <input
              v-model="selectedReasons"
              type="checkbox"
              :value="reason"
              class="mt-1"
            />
            <span>{{ reason }}</span>
          </label>
        </div>
        <div v-if="hasOtherReason" class="space-y-1">
          <label for="rejection-other-reason" class="block text-sm">Inny powód *</label>
          <textarea
            id="rejection-other-reason"
            v-model="rejectionReason"
            rows="2"
            class="w-full rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="Krótko opisz powód"
            required
          />
        </div>
      </div>

      <p class="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
        Zmiana statusu spowoduje wysłanie powiadomienia e-mail.
      </p>

      <p v-if="errorMessage" class="text-sm text-red-600">{{ errorMessage }}</p>

      <div class="flex justify-end gap-2">
        <button class="rounded border border-slate-300 px-4 py-2" @click="emit('close')">Anuluj</button>
        <button
          class="rounded text-white px-4 py-2"
          :class="actionConfig.buttonClasses"
          :disabled="loading"
          @click="confirm"
        >
          {{ loading ? 'Zapisywanie...' : 'Potwierdź' }}
        </button>
      </div>
    </div>
  </div>
</template>
