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

const FIELD_LABELS = {
  turnusCode: 'Turnus',
  'person.firstName': 'Imię',
  'person.lastName': 'Nazwisko',
  'person.pesel': 'PESEL',
  'person.gender': 'Płeć',
  'person.isAdult': 'Osoba pełnoletnia',
  'person.contact.email': 'E-mail',
  'person.contact.phone': 'Telefon',
  'address.street': 'Ulica',
  'address.houseNumber': 'Nr domu',
  'address.postalCode': 'Kod pocztowy',
  'address.city': 'Miasto',
  'address.sameAddress': 'Adres opiekuna taki sam',
  'address.guardianAddress.street': 'Ulica (opiekun)',
  'address.guardianAddress.houseNumber': 'Nr domu (opiekun)',
  'address.guardianAddress.postalCode': 'Kod pocztowy (opiekun)',
  'address.guardianAddress.city': 'Miasto (opiekun)',
  'guardian.firstName': 'Imię opiekuna',
  'guardian.lastName': 'Nazwisko opiekuna',
  'guardian.relation': 'Relacja',
  'guardian.names': 'Imiona rodziców',
  'guardian.contact.email': 'E-mail opiekuna',
  'guardian.contact.phone': 'Telefon opiekuna',
  'ice.firstName': 'Imię osoby kontaktowej',
  'ice.lastName': 'Nazwisko osoby kontaktowej',
  'ice.relation': 'Relacja (kontakt awaryjny)',
  'ice.phone': 'Telefon (kontakt awaryjny)',
  role: 'Rola',
  'consents.dataProcessing': 'Zgoda na przetwarzanie danych',
  'consents.imageUsage': 'Zgoda na wykorzystanie wizerunku',
  'consents.regulations': 'Akceptacja regulaminu',
  'health.declaration': 'Oświadczenie zdrowotne',
}

const SECTION_LABELS = {
  person: 'Dane osobowe',
  address: 'Adres',
  guardian: 'Dane opiekuna',
  ice: 'Kontakt awaryjny',
  role: 'Rola',
  certificates: 'Uprawnienia',
  certificateDetails: 'Szczegóły uprawnień',
  health: 'Zdrowie',
  consents: 'Zgody',
  turnusCode: 'Turnus',
}

onMounted(async () => {
  try {
    detail.value = await fetchRegistrationDetail(authStore.token, props.code)
  } catch {
    errorMessage.value = 'Nie udało się pobrać szczegółów zgłoszenia'
  } finally {
    loading.value = false
  }
})

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function toLabel(path, key) {
  const pathKey = path.join('.')
  if (FIELD_LABELS[pathKey]) return FIELD_LABELS[pathKey]
  if (SECTION_LABELS[key]) return SECTION_LABELS[key]
  return key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/^./, (char) => char.toUpperCase())
}

function flattenFields(node, path = []) {
  if (Array.isArray(node)) {
    return [{
      path: path.join('.'),
      label: toLabel(path, path[path.length - 1] || 'value'),
      value: node.length === 0 ? null : node,
    }]
  }

  if (isPlainObject(node)) {
    const entries = Object.entries(node)
    if (entries.length === 0) {
      return [{
        path: path.join('.'),
        label: toLabel(path, path[path.length - 1] || 'value'),
        value: null,
      }]
    }
    return entries.flatMap(([key, value]) => flattenFields(value, [...path, key]))
  }

  return [{
    path: path.join('.'),
    label: toLabel(path, path[path.length - 1] || 'value'),
    value: node,
  }]
}

function payloadSections(payload) {
  if (!isPlainObject(payload)) {
    return [{
      key: 'payload',
      label: 'Dane zgłoszenia',
      fields: [{
        path: 'payload',
        label: 'Dane',
        value: payload,
      }],
    }]
  }

  const topLevel = Object.entries(payload)
  const scalarFields = []
  const sections = []

  for (const [key, value] of topLevel) {
    if (isPlainObject(value) || Array.isArray(value)) {
      sections.push({
        key,
        label: SECTION_LABELS[key] || toLabel([key], key),
        fields: flattenFields(value, [key]),
      })
    } else {
      scalarFields.push({
        path: key,
        label: toLabel([key], key),
        value,
      })
    }
  }

  if (scalarFields.length) {
    sections.unshift({
      key: 'basic',
      label: 'Podstawowe informacje',
      fields: scalarFields,
    })
  }

  return sections
}

function formatFieldValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? 'Tak' : 'Nie'
  if (Array.isArray(value)) {
    if (value.every((entry) => ['string', 'number', 'boolean'].includes(typeof entry))) {
      return value.join(', ')
    }
    return JSON.stringify(value)
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
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

        <!-- Payload as dedicated fields -->
        <section v-if="detail.payload">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Dane zgłoszenia</h3>
          <div class="space-y-3">
            <div
              v-for="section in payloadSections(detail.payload)"
              :key="section.key"
              class="bg-slate-50 border border-slate-200 rounded-lg p-3"
            >
              <h4 class="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-2">
                {{ section.label }}
              </h4>
              <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                <template v-for="field in section.fields" :key="field.path">
                  <dt class="text-slate-500">{{ field.label }}</dt>
                  <dd>{{ formatFieldValue(field.value) }}</dd>
                </template>
              </dl>
            </div>
          </div>
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
