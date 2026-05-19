<script setup>
import { ref } from 'vue'
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
const loading = ref(false)
const errorMessage = ref('')

async function confirm() {
  errorMessage.value = ''
  if (props.action === 'REJECT' && !rejectionReason.value.trim()) {
    errorMessage.value = 'Powód odrzucenia jest wymagany'
    return
  }

  const payload = props.action === 'ACCEPT'
    ? { status: 'ACCEPTED' }
    : { status: 'REJECTED', rejectionReason: rejectionReason.value.trim() }

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
        {{ action === 'ACCEPT' ? 'Potwierdź akceptację' : 'Potwierdź odrzucenie' }}
      </h2>
      <p>
        Zgłoszenie: <strong>{{ registration.registrationCode }}</strong>
      </p>

      <div v-if="action === 'REJECT'" class="space-y-1">
        <label class="block text-sm">Powód odrzucenia</label>
        <textarea
          v-model="rejectionReason"
          rows="4"
          class="w-full rounded border border-slate-300 px-3 py-2"
          placeholder="Podaj powód odrzucenia"
        />
      </div>

      <p v-if="errorMessage" class="text-sm text-red-600">{{ errorMessage }}</p>

      <div class="flex justify-end gap-2">
        <button class="rounded border border-slate-300 px-4 py-2" @click="emit('close')">Anuluj</button>
        <button
          class="rounded text-white px-4 py-2"
          :class="action === 'ACCEPT' ? 'bg-green-600 hover:bg-green-700' : 'bg-red-600 hover:bg-red-700'"
          :disabled="loading"
          @click="confirm"
        >
          {{ loading ? 'Zapisywanie...' : 'Potwierdź' }}
        </button>
      </div>
    </div>
  </div>
</template>
