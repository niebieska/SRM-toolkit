<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { fetchRegistrationDetail } from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'
import { formatDateTime } from '../utils/dateUtils'
import { registrationSections, formatFieldValue } from '../utils/registrationDetails'

const props = defineProps({
  code: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['close', 'status-action'])

const authStore = useAuthStore()
const detail = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const personName = computed(() => {
  const person = detail.value?.payload?.person
  return [detail.value?.firstName || person?.firstName, detail.value?.lastName || person?.lastName]
    .filter(Boolean).join(' ')
})

onMounted(async () => {
  try {
    detail.value = await fetchRegistrationDetail(authStore.token, props.code)
  } catch (error) {
    errorMessage.value = error.message || 'Nie udało się pobrać szczegółów zgłoszenia'
  } finally {
    loading.value = false
  }
})

function requestStatusAction(action) {
  emit('status-action', detail.value, action)
}
</script>

<template>
  <div class="fixed inset-0 bg-black/40 flex items-start justify-center p-4 overflow-y-auto z-50">
    <div class="w-full max-w-2xl bg-white rounded-xl px-6 pt-5 pb-6 space-y-5 my-8">

      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">Szczegóły zgłoszenia</h2>
        <button class="text-slate-500 hover:text-slate-800 text-xl leading-none" @click="emit('close')">✕</button>
      </div>

      <p v-if="loading" class="text-slate-600">Ładowanie...</p>
      <p v-else-if="errorMessage" class="text-red-600">{{ errorMessage }}</p>

      <template v-else-if="detail">

        <!-- Header info -->
        <section aria-labelledby="registration-summary-heading" class="space-y-3">
          <div class="flex flex-wrap items-start gap-x-6 gap-y-3 sm:pr-4">
          <div class="min-w-0 flex-1 basis-60">
            <div class="flex min-h-6 flex-wrap items-center gap-2">
              <h3 id="registration-summary-heading" class="min-w-0 break-words text-base font-semibold text-slate-900">{{ personName || 'Zgłoszenie' }}</h3>
              <span class="rounded-full border border-slate-300 px-2 py-0.5 text-xs font-medium text-slate-600">{{ detail.registrationType === 'PARTICIPANT' ? 'Uczestnik' : 'Kadra' }}</span>
              <StatusBadge :status="detail.status" />
            </div>
            <p class="mt-1 break-all text-xs leading-4 text-slate-500">{{ detail.registrationCode }}</p>
          </div>
          <dl class="flex flex-wrap gap-x-6 gap-y-3 text-sm">
            <div class="min-w-0">
              <dt class="flex min-h-6 items-center text-xs text-slate-500">Turnus</dt>
              <dd class="mt-1 break-words leading-4">{{ detail.turnusCode }}</dd>
            </div>
            <div>
              <dt class="flex min-h-6 items-center text-xs text-slate-500">Data zgłoszenia</dt>
              <dd class="mt-1 leading-4">{{ formatDateTime(detail.createdAt) }}</dd>
            </div>
          </dl>
          </div>
          <dl v-if="detail.registrationType === 'STAFF' && (detail.payload?.role || detail.payload?.subrole)" class="flex flex-wrap gap-x-6 gap-y-2 text-sm">
            <div v-if="detail.payload?.role" class="flex min-w-0 items-baseline gap-2">
              <dt class="text-slate-500">Rola</dt>
              <dd class="min-w-0 break-words">{{ formatFieldValue(detail.payload.role) }}</dd>
            </div>
            <div v-if="detail.payload?.subrole" class="flex min-w-0 items-baseline gap-2">
              <dt class="text-slate-500">Funkcja</dt>
              <dd class="min-w-0 break-words">{{ formatFieldValue(detail.payload.subrole) }}</dd>
            </div>
          </dl>
          <div v-if="detail.rejectionReason" class="border-t border-slate-200 pt-3 text-sm">
            <p class="font-medium text-slate-700">Powód odrzucenia</p>
            <p class="mt-1 whitespace-pre-wrap break-words text-slate-600">{{ detail.rejectionReason }}</p>
          </div>
        </section>

        <!-- Payload as dedicated fields -->
        <section v-if="detail.payload">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Dane zgłoszenia</h3>
          <div class="space-y-3">
            <div
              v-for="section in registrationSections(detail).filter(section => detail.registrationType !== 'STAFF' || section.key !== 'role')"
              :key="section.key"
              class="bg-slate-50 border border-slate-200 rounded-lg p-3"
            >
              <h4 class="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-2">
                {{ section.label }}
              </h4>
              <div v-if="section.questions" class="space-y-3">
                <article v-for="question in section.questions" :key="question.key" class="rounded-md border border-slate-200 bg-white px-3 py-3">
                  <p class="whitespace-pre-wrap break-words text-sm font-medium leading-5 text-slate-800">{{ question.question }}</p>

                  <template v-if="question.key === 'additionalInfo'">
                    <p
                      v-for="field in question.fields.filter(field => field.path.endsWith('.value') || field.path === 'health.additionalInfo')"
                      :key="field.path"
                      class="mt-2 whitespace-pre-wrap break-words text-sm leading-5 text-slate-700"
                    >{{ formatFieldValue(field.value, field.path) }}</p>
                    <dl v-if="question.fields.some(field => !field.path.endsWith('.value') && field.path !== 'health.additionalInfo')" class="mt-2 grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1 text-sm">
                      <template v-for="field in question.fields.filter(field => !field.path.endsWith('.value') && field.path !== 'health.additionalInfo')" :key="field.path">
                        <dt class="min-w-0 break-words text-slate-500">{{ field.label }}</dt>
                        <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                      </template>
                    </dl>
                  </template>

                  <template v-else>
                    <div class="mt-2 flex flex-wrap items-center gap-2 text-sm">
                      <span class="text-slate-500">Odpowiedź</span>
                      <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                        {{ formatFieldValue(question.fields.find(field => field.path === `health.${question.key}.answer`)?.value, `health.${question.key}.answer`) }}
                      </span>
                    </div>
                    <div v-for="field in question.fields.filter(field => field.path === `health.${question.key}.detail`)" :key="field.path" class="mt-2 border-l-2 border-slate-200 pl-3">
                      <p class="text-xs font-medium text-slate-500">Szczegóły</p>
                      <p class="mt-0.5 whitespace-pre-wrap break-words text-sm leading-5 text-slate-700">{{ formatFieldValue(field.value, field.path) }}</p>
                    </div>
                    <dl v-if="question.fields.some(field => ![`health.${question.key}.answer`, `health.${question.key}.detail`].includes(field.path))" class="mt-2 grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1 text-sm">
                      <template v-for="field in question.fields.filter(field => ![`health.${question.key}.answer`, `health.${question.key}.detail`].includes(field.path))" :key="field.path">
                        <dt class="min-w-0 break-words text-slate-500">{{ field.label }}</dt>
                        <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                      </template>
                    </dl>
                  </template>
                </article>
              </div>
              <dl v-else class="grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1 text-sm">
                <template v-for="field in section.fields" :key="field.path">
                  <dt class="text-slate-500">{{ field.label }}</dt>
                  <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                </template>
              </dl>
            </div>
          </div>
        </section>
        <p v-else class="text-sm text-slate-500">Brak danych zgłoszenia</p>

      </template>

      <div class="flex flex-wrap justify-end gap-2 pt-2">
        <button
          v-if="detail && detail.status !== 'ACCEPTED'"
          class="rounded bg-green-600 text-white px-4 py-2 hover:bg-green-700"
          @click="requestStatusAction('ACCEPT')"
        >
          Zaakceptuj
        </button>
        <button
          v-if="detail && detail.status !== 'WAITLIST'"
          class="rounded bg-amber-600 text-white px-4 py-2 hover:bg-amber-700"
          @click="requestStatusAction('WAITLIST')"
        >
          Lista rezerwowa
        </button>
        <button
          v-if="detail && detail.status !== 'REJECTED'"
          class="rounded bg-red-600 text-white px-4 py-2 hover:bg-red-700"
          @click="requestStatusAction('REJECT')"
        >
          Odrzuć
        </button>
        <button class="rounded border border-slate-300 px-4 py-2 hover:bg-slate-50" @click="emit('close')">
          Zamknij
        </button>
      </div>
    </div>
  </div>
</template>
