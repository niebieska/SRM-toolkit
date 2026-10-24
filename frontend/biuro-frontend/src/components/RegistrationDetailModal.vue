<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { fetchRegistrationDetail } from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'
import RegistrationEditForm from './RegistrationEditForm.vue'
import DiscardChangesDialog from './DiscardChangesDialog.vue'
import { formatDateTime } from '../utils/dateUtils'
import { registrationSections, combineQualificationSections, formatFieldValue } from '../utils/registrationDetails'
import { createDiscardConfirmation } from '../utils/discardConfirmation.js'

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
const editing = ref(false)
const editDirty = ref(false)
const editSuccess = ref('')
const editReloadError = ref('')
const { prompt, confirm, decide } = createDiscardConfirmation()
const personName = computed(() => {
  const person = detail.value?.payload?.person
  return [detail.value?.firstName || person?.firstName, detail.value?.lastName || person?.lastName]
    .filter(Boolean).join(' ')
})
const statusHistoryTimeline = computed(() => {
  if (!detail.value) return []

  const changes = (detail.value.statusHistory || []).map(entry => ({
    id: `change-${entry.id}`,
    status: entry.newStatus,
    changedAt: entry.changedAt,
    actorUsername: entry.actorUsername,
    rejectionReason: entry.rejectionReason,
  }))

  return [
    ...changes,
    {
      id: 'initial',
      status: 'NEW',
      changedAt: detail.value.createdAt,
      actorUsername: null,
      rejectionReason: null,
    },
  ]
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

async function requestCancelEdit() {
  if (editDirty.value && !(await confirm('leave'))) return
  editing.value = false
  editDirty.value = false
}

async function requestClose() {
  if (editing.value && editDirty.value && !(await confirm('leave'))) return
  emit('close')
}

async function requestReload() {
  editReloadError.value = ''
  let latest
  try {
    latest = await fetchRegistrationDetail(authStore.token, props.code)
  } catch (error) {
    editReloadError.value = error.message || 'Nie udało się wczytać aktualnych danych.'
    return
  }
  if (editDirty.value && !(await confirm('reload'))) return
  detail.value = latest
  editing.value = false
  editDirty.value = false
  editSuccess.value = ''
}

function handleEditSaved(updated) {
  detail.value = updated
  editing.value = false
  editDirty.value = false
  editSuccess.value = 'Zmiany zostały zapisane.'
}

function editFieldLabel(path) {
  return ({
    'person.firstName': 'Imię', 'person.lastName': 'Nazwisko',
    'person.contact.email': 'E-mail', 'person.contact.phone': 'Telefon',
    'person.motherName': 'Imię matki', 'person.fatherName': 'Imię ojca', 'person.birthSurname': 'Nazwisko rodowe',
    'address.street': 'Ulica', 'address.houseNumber': 'Nr domu', 'address.postalCode': 'Kod pocztowy', 'address.city': 'Miasto',
    'guardian.firstName': 'Imię opiekuna', 'guardian.lastName': 'Nazwisko opiekuna', 'guardian.names': 'Imiona i nazwiska rodziców/opiekunów',
    'guardian.relation': 'Relacja opiekuna', 'guardian.contact.email': 'E-mail opiekuna', 'guardian.contact.phone': 'Telefon opiekuna',
    'address.sameAddress': 'Adres opiekuna taki sam', 'address.guardianAddress.street': 'Ulica opiekuna',
    'address.guardianAddress.houseNumber': 'Nr domu opiekuna', 'address.guardianAddress.postalCode': 'Kod pocztowy opiekuna',
    'address.guardianAddress.city': 'Miasto opiekuna',
    'ice.firstName': 'Imię kontaktu awaryjnego', 'ice.lastName': 'Nazwisko kontaktu awaryjnego',
    'ice.relation': 'Relacja kontaktu awaryjnego', 'ice.relationOther': 'Doprecyzowanie relacji', 'ice.phone': 'Telefon kontaktu awaryjnego',
    'health.q1.answer': 'Choroby przewlekłe — odpowiedź', 'health.q1.detail': 'Choroby przewlekłe — szczegóły',
    'health.q2.answer': 'Alergie — odpowiedź', 'health.q2.detail': 'Alergie — szczegóły',
    'health.q3.answer': 'Dodatkowe potrzeby — odpowiedź', 'health.q3.detail': 'Dodatkowe potrzeby — szczegóły',
    'health.additionalInfo.value': 'Dodatkowe informacje', 'health.additionalInfo': 'Dodatkowe informacje',
  })[path] || path
}

function addressValue(fields, paths) {
  const parts = paths
    .map(path => fields.find(field => field.path === path)?.value)
    .filter(value => value !== null && value !== undefined && String(value).trim() !== '')
    .map(value => String(value).trim())
  return parts.join(' ') || 'Brak danych'
}

function addressFieldsToKeep(fields) {
  const addressPaths = new Set([
    'address.street', 'address.houseNumber', 'address.postalCode', 'address.city',
    'address.sameAddress', 'address.guardianAddress',
    'address.guardianAddress.street', 'address.guardianAddress.houseNumber',
    'address.guardianAddress.postalCode', 'address.guardianAddress.city',
  ])
  return fields.filter(field => !addressPaths.has(field.path)
    && !field.path.startsWith('address.guardianAddress.'))
}

function guardianAddressFields(fields) {
  const labels = {
    'guardianAddress.street': 'Ulica',
    'guardianAddress.houseNumber': 'Nr domu',
    'guardianAddress.postalCode': 'Kod pocztowy',
    'guardianAddress.city': 'Miasto',
  }
  return fields
    .filter(field => field.path === 'address.guardianAddress' || field.path.startsWith('address.guardianAddress.'))
    .filter(field => field.value !== null && field.value !== undefined && String(field.value).trim() !== '')
    .map(field => {
      const path = field.path.replace('address.', '')
      return { ...field, path, label: labels[path] || field.label }
    })
}

function guardianAddressOtherFields(fields) {
  const addressPaths = new Set([
    'guardianAddress.street', 'guardianAddress.houseNumber',
    'guardianAddress.postalCode', 'guardianAddress.city',
  ])
  return fields.filter(field => !addressPaths.has(field.path))
}

function iceFields(fields) {
  const relation = fields.find(field => field.path === 'ice.relation')
  const relationDetail = fields.find(field => field.path === 'ice.relationOther')
  const customRelation = relation?.value === 'inna'
    && relationDetail?.value !== null
    && relationDetail?.value !== undefined
    && String(relationDetail.value).trim() !== ''

  return fields
    .filter(field => field.path !== 'ice.relationOther')
    .map(field => field.path === 'ice.relation' && customRelation
      ? { ...field, value: relationDetail.value }
      : field)
}

function visibleRegistrationSections(registration) {
  const sections = registrationSections(registration)
    .filter(section => registration.registrationType !== 'STAFF' || section.key !== 'role')
  const addressSection = sections.find(section => section.key === 'address')
  if (addressSection) {
    const sameAddress = addressSection.fields.find(field => field.path === 'address.sameAddress')?.value === true
    const guardianFields = guardianAddressFields(addressSection.fields)
    addressSection.fields = addressSection.fields.filter(field => field.path !== 'address.guardianAddress'
      && !field.path.startsWith('address.guardianAddress.'))
    if (!sameAddress && guardianFields.length) {
      const guardianSection = sections.find(section => section.key === 'guardian')
      const insertAfter = guardianSection || addressSection
      sections.splice(sections.indexOf(insertAfter) + 1, 0, {
        key: 'guardianAddress',
        label: 'Adres opiekuna',
        fields: guardianFields,
      })
    }
  }
  return combineQualificationSections(sections)
}
</script>

<template>
  <div class="fixed inset-0 bg-black/40 flex items-start justify-center p-4 overflow-y-auto z-50">
    <div :class="['w-full rounded-xl bg-white px-6 pb-6 pt-5 my-8 space-y-5', editing ? 'max-w-3xl' : 'max-w-2xl']">

      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">{{ editing ? 'Edytuj dane zgłoszenia' : 'Szczegóły zgłoszenia' }}</h2>
        <button class="text-slate-500 hover:text-slate-800 text-xl leading-none" aria-label="Zamknij szczegóły zgłoszenia" @click="requestClose">✕</button>
      </div>

      <p v-if="loading" class="text-slate-600">Ładowanie...</p>
      <p v-else-if="errorMessage" class="text-red-600">{{ errorMessage }}</p>

      <template v-else-if="detail">

        <RegistrationEditForm
          v-if="editing"
          :detail="detail"
          :reload-error="editReloadError"
          @saved="handleEditSaved"
          @reload="requestReload"
          @cancel="requestCancelEdit"
          @dirty-change="editDirty = $event"
        />

        <template v-else>

        <p v-if="editSuccess" role="status" class="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">{{ editSuccess }}</p>

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
          <div
            :class="detail.registrationType === 'PARTICIPANT'
              ? 'columns-1 gap-x-5 rounded-lg border border-slate-200 bg-slate-50/70 p-4 sm:columns-2'
              : 'grid grid-cols-1 gap-x-5 gap-y-4 rounded-lg border border-slate-200 bg-slate-50/70 p-4 sm:grid-cols-2'"
          >
            <div
              v-for="section in visibleRegistrationSections(detail)"
              :key="section.key"
              class="min-w-0"
              :class="detail.registrationType === 'PARTICIPANT'
                ? 'break-inside-avoid pb-4'
                : section.questions || section.key === 'consents' ? 'sm:col-span-2' : ''"
              :style="detail.registrationType === 'PARTICIPANT' && (section.questions || section.key === 'consents')
                ? { columnSpan: 'all' }
                : undefined"
            >
              <div
                class="min-w-0 rounded-md border border-slate-200 bg-white p-3"
                :class="detail.registrationType === 'STAFF' ? 'h-full' : ''"
              >
                <h4 class="mb-2 border-b border-slate-200 pb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                  {{ section.label }}
                </h4>
              <div v-if="section.questions" class="space-y-3">
                <article v-for="question in section.questions" :key="question.key" class="border-b border-slate-100 pb-3 last:border-0 last:pb-0">
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
              <dl v-else-if="section.key === 'person'" class="grid grid-cols-1 gap-y-2 text-sm">
                <div v-for="field in section.fields" :key="field.path" class="flex min-w-0 items-baseline gap-2">
                  <dt class="shrink-0 text-slate-500">{{ field.label }}:</dt>
                  <dd class="min-w-0 break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                </div>
              </dl>
              <div v-else-if="section.key === 'address'" class="space-y-3 text-sm">
                <address class="space-y-1 not-italic text-slate-700">
                  <p>{{ addressValue(section.fields, ['address.street', 'address.houseNumber']) }}</p>
                  <p>{{ addressValue(section.fields, ['address.postalCode', 'address.city']) }}</p>
                </address>
                <dl v-if="addressFieldsToKeep(section.fields).length" class="grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1">
                  <template v-for="field in addressFieldsToKeep(section.fields)" :key="field.path">
                    <dt class="min-w-0 break-words text-slate-500">{{ field.label }}</dt>
                    <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                  </template>
                </dl>
              </div>
              <div v-else-if="section.key === 'guardianAddress'" class="space-y-3 text-sm">
                <address class="space-y-1 not-italic text-slate-700">
                  <p>{{ addressValue(section.fields, ['guardianAddress.street', 'guardianAddress.houseNumber']) }}</p>
                  <p>{{ addressValue(section.fields, ['guardianAddress.postalCode', 'guardianAddress.city']) }}</p>
                </address>
                <dl v-if="guardianAddressOtherFields(section.fields).length" class="grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1">
                  <template v-for="field in guardianAddressOtherFields(section.fields)" :key="field.path">
                    <dt class="min-w-0 break-words text-slate-500">{{ field.label }}</dt>
                    <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                  </template>
                </dl>
              </div>
              <dl v-else-if="section.key === 'ice'" class="grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1 text-sm">
                <template v-for="field in iceFields(section.fields)" :key="field.path">
                  <dt class="min-w-0 break-words text-slate-500">{{ field.label }}</dt>
                  <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                </template>
              </dl>
              <dl v-else class="grid grid-cols-[minmax(0,0.4fr)_minmax(0,0.6fr)] gap-x-3 gap-y-1 text-sm">
                <template v-for="field in section.fields" :key="field.path">
                  <dt class="text-slate-500">{{ field.label }}</dt>
                  <dd class="min-w-0 whitespace-pre-wrap break-words">{{ formatFieldValue(field.value, field.path) }}</dd>
                </template>
              </dl>
              </div>
            </div>
          </div>
        </section>
        <p v-else class="text-sm text-slate-500">Brak danych zgłoszenia</p>

        <section aria-labelledby="status-history-heading" class="rounded-lg border border-slate-200 p-4">
          <h3 id="status-history-heading" class="text-sm font-semibold text-slate-700">Historia statusów</h3>
          <ol class="relative mt-3 border-l border-slate-200">
            <li v-for="entry in statusHistoryTimeline" :key="entry.id" class="relative ml-4 pb-4 last:pb-0">
              <span class="absolute -left-[21px] top-1 h-2.5 w-2.5 rounded-full border-2 border-white bg-slate-400" aria-hidden="true"></span>
              <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                <StatusBadge :status="entry.status" />
                <span class="text-xs text-slate-600">{{ formatDateTime(entry.changedAt) }}</span>
                <span v-if="entry.actorUsername" class="text-xs text-slate-500">· {{ entry.actorUsername }}</span>
              </div>
              <p v-if="entry.rejectionReason" class="mt-1 whitespace-pre-wrap break-words text-sm text-slate-700">
                Powód odrzucenia: {{ entry.rejectionReason }}
              </p>
            </li>
          </ol>
        </section>

        <section v-if="detail.editHistory?.length" aria-labelledby="data-history-heading" class="rounded-lg border border-slate-200 p-4">
          <h3 id="data-history-heading" class="text-sm font-semibold text-slate-700">Historia zmian danych</h3>
          <ol class="relative mt-3 border-l border-slate-200">
            <li v-for="entry in detail.editHistory" :key="entry.id" class="relative ml-4 pb-4 last:pb-0">
              <span class="absolute -left-[21px] top-1 h-2.5 w-2.5 rounded-full border-2 border-white bg-blue-700" aria-hidden="true"></span>
              <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-600">
                <span>{{ formatDateTime(entry.changedAt) }}</span><span>· {{ entry.actorUsername }}</span>
              </div>
              <p class="mt-1 text-sm text-slate-700">Zmieniono: {{ entry.changedFields.map(editFieldLabel).join(', ') }}</p>
            </li>
          </ol>
        </section>

        </template>

      </template>

      <div v-if="detail && !loading && !errorMessage && !editing" class="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 pt-4">
        <button class="rounded border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50" @click="editing = true; editSuccess = ''; editReloadError = ''">
          Edytuj
        </button>
        <div class="flex flex-wrap justify-end gap-2">
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
        <button class="rounded border border-slate-300 px-4 py-2 hover:bg-slate-50" @click="requestClose">
          Zamknij
        </button>
        </div>
      </div>
    </div>
    <DiscardChangesDialog
      v-if="prompt"
      :kind="prompt.kind"
      @stay="decide(false)"
      @discard="decide(true)"
    />
  </div>
</template>
