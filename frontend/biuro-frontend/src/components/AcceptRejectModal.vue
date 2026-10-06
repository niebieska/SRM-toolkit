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

const predefinedRejectionReasons = [
  'Brak wolnych miejsc',
  'Nie spełnia kryteriów wieku dla wybranego turnusu',
  'Niekompletne dane w zgłoszeniu',
  'Brak wymaganych zgód',
]

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
  return [...selectedReasons.value, rejectionReason.value.trim()]
    .filter(Boolean)
    .join('; ')
})

async function confirm() {
  errorMessage.value = ''
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
    errorMessage.value = 'Nie udało się zaktualizować statusu zgłoszenia'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 bg-black/40 flex items-center justify-center p-4">
    <div class="w-full max-w-lg bg-white rounded-xl p-5 space-y-4">
      <h2 class="text-lg font-semibold">
        {{ actionConfig.title }}
      </h2>
      <p>
        Zgłoszenie: <strong>{{ registration.registrationCode }}</strong>
      </p>

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
        <label class="block text-sm">Dodatkowy powód</label>
        <textarea
          v-model="rejectionReason"
          rows="4"
          class="w-full rounded border border-slate-300 px-3 py-2"
          placeholder="Opcjonalny komentarz"
        />
      </div>

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
