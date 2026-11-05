<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 2 – Wybierz rolę w kadrze</h2>

    <p v-if="loading" role="status" class="text-sm text-gray-600">Wczytywanie opcji kadry…</p>
    <div v-if="loadError" role="alert" class="rounded-lg bg-red-50 p-3 text-sm text-red-700">
      {{ loadError }}
      <button type="button" class="ml-2 underline" :disabled="loading" @click="loadCatalogue">Spróbuj ponownie</button>
    </div>
    <div v-if="!isAdult && catalogue" class="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-sm text-yellow-800">
      Dostępne role i funkcje uwzględniają niepełnoletność. Wybierz jedną z przedstawionych opcji.
    </div>

    <!-- Role dropdown -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-1">Rola w kadrze *</label>
      <select v-model="local.role"
              :class="fieldClass('role')">
        <option value="">-- Wybierz rolę --</option>
        <option
            v-for="role in roleOptions"
            :key="role.value"
            :value="role.value"
        >
          {{ role.label }}
        </option>
      </select>
    </div>

    <div v-if="currentRole?.subroles?.length">
      <label class="block text-sm font-medium text-gray-700 mb-1">
        Funkcja {{ requiresFunction ? '*' : '(opcjonalna)' }}
      </label>

      <select
          v-model="local.subrole"
          :class="fieldClass('subrole')"
      >
        <option value="">{{ requiresFunction ? '-- Wybierz funkcję --' : 'Bez dodatkowej funkcji' }}</option>

        <option
            v-for="subrole in functionOptions"
            :key="subrole.value"
            :value="subrole.value"
        >
          {{ subrole.label }}
        </option>
      </select>
    </div>

    <!-- Certificates -->
    <div v-if="local.role && currentCertificates.length > 0" class="space-y-3">
      <h3 class="font-semibold text-gray-700">Posiadane certyfikaty / uprawnienia</h3>
      <div v-for="cert in currentCertificates" :key="cert.id" class="space-y-2">
        <label class="flex items-center gap-2 cursor-pointer">
          <input
              type="checkbox"
              :value="cert.id"
              v-model="selectedCertificates"
              class="w-4 h-4"
          />
          <span class="text-sm text-gray-700">{{ cert.label }}</span>
        </label>
        <div v-if="cert.hasDetails && selectedCertificates.includes(cert.id)" class="ml-6">
          <input
              type="text"
              v-model="local.certificateDetails[cert.id]"
              placeholder="Proszę wpisać jakie"
              class="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-400"
          />
          <p class="mt-1 text-right text-xs" :class="(local.certificateDetails[cert.id] || '').length > FORM_FIELD_LIMITS.certificateDetails ? 'text-red-600' : 'text-gray-500'">
            {{ (local.certificateDetails[cert.id] || '').length }} / {{ FORM_FIELD_LIMITS.certificateDetails }} znaków
          </p>
          <p v-if="certificateDetailErrors[cert.id]" class="text-red-500 text-xs mt-1">{{ certificateDetailErrors[cert.id] }}</p>
        </div>
      </div>
    </div>

    <p v-if="roleError" role="alert" class="text-sm text-red-600">{{ roleError }}</p>
    <div class="flex justify-between">
      <button @click="goPrev"
              class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition">
        ← Wstecz
      </button>
      <button @click="goNext"
              class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition disabled:opacity-50"
              :disabled="loading || !catalogue">
        Dalej →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount, reactive } from 'vue'
import { availableRoles, availableFunctions, functionRequired, selectionError, certificatesFor } from '../../../config/staffRoles.js'
import { fetchStaffCatalogue } from '../../../api/staffCatalogueApi.js'
import {applyLengthErrors, FORM_FIELD_LIMITS} from '../../../utils/formFieldLimits.js'

const props = defineProps({
  formData: { type: Object, required: true },
  isAdult: { type: Boolean, default: false },
})
const emit = defineEmits(['update:formData', 'prev', 'next'])
const local = ref({ ...props.formData, subrole: props.formData.subrole || '',
  certificateDetails: { ...(props.formData.certificateDetails || {}) } })
const selectedCertificates = ref(Object.keys(props.formData.certificates || {}).filter(key => props.formData.certificates[key]))
const catalogue = ref(null)
const loading = ref(false)
const loadError = ref('')
const roleError = ref('')
const certificateDetailErrors = reactive({})
let requestSequence = 0
const roleOptions = computed(() => availableRoles(catalogue.value, props.isAdult))
const currentRole = computed(() => roleOptions.value.find(role => role.value === local.value.role))
const functionOptions = computed(() => availableFunctions(currentRole.value, props.isAdult))
const requiresFunction = computed(() => functionRequired(currentRole.value, props.isAdult))
const currentCertificates = computed(() => currentRole.value
  ? certificatesFor(catalogue.value, local.value.role, local.value.subrole) : [])

function clearCertificates() {
  selectedCertificates.value = []
  local.value.certificateDetails = {}
  Object.keys(certificateDetailErrors).forEach(key => delete certificateDetailErrors[key])
}
function normalizeSelection() {
  if (!catalogue.value) return
  if (!currentRole.value) {
    local.value.role = ''
    local.value.subrole = ''
    clearCertificates()
    return
  }
  if (local.value.subrole && !functionOptions.value.some(option => option.value === local.value.subrole)) {
    local.value.subrole = ''
    clearCertificates()
  }
  const allowed = new Set(currentCertificates.value.map(option => option.id))
  selectedCertificates.value = selectedCertificates.value.filter(id => allowed.has(id))
}
async function loadCatalogue() {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  catalogue.value = null
  local.value.turnusCode = props.formData.turnusCode
  try {
    if (!props.formData.turnusCode) throw new Error('Wróć do poprzedniego kroku i wybierz turnus.')
    const value = await fetchStaffCatalogue(props.formData.turnusCode)
    if (sequence !== requestSequence) return
    catalogue.value = value
    normalizeSelection()
  } catch (error) {
    if (sequence === requestSequence) loadError.value = error.message
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
watch(() => props.formData.turnusCode, loadCatalogue, { immediate: true })
watch(() => props.isAdult, normalizeSelection)
watch(() => local.value.role, () => {
  if (local.value.subrole && !functionOptions.value.some(option => option.value === local.value.subrole)) local.value.subrole = ''
})
watch(() => [local.value.role, local.value.subrole], clearCertificates)
onBeforeUnmount(() => { requestSequence++ })
function fieldClass() {
  return 'w-full border border-gray-300 rounded px-3 py-2 bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-gray-400'
}
function formValue() {
  const certificates = Object.fromEntries(selectedCertificates.value.map(id => [id, true]))
  return { ...local.value, certificates }
}
function goPrev() {
  emit('update:formData', formValue())
  emit('prev')
}
function goNext() {
  if (loading.value || !catalogue.value) return
  Object.keys(certificateDetailErrors).forEach(key => delete certificateDetailErrors[key])
  roleError.value = selectionError(catalogue.value, local.value.role, local.value.subrole, props.isAdult)
  if (roleError.value) return
  const certificatesWithDetails = currentCertificates.value.filter(option =>
    option.hasDetails && selectedCertificates.value.includes(option.id))
  applyLengthErrors(certificateDetailErrors,
    Object.fromEntries(certificatesWithDetails.map(option => [
      option.id, local.value.certificateDetails[option.id] || '',
    ])),
    Object.fromEntries(certificatesWithDetails.map(option => [option.id, {
      max: FORM_FIELD_LIMITS.certificateDetails,
      label: option.label,
    }])))
  if (Object.keys(certificateDetailErrors).length) return
  const missingDetails = currentCertificates.value.some(option => option.hasDetails
    && selectedCertificates.value.includes(option.id) && !local.value.certificateDetails[option.id]?.trim())
  if (missingDetails) {
    roleError.value = 'Opisz wybrane uprawnienia.'
    return
  }
  emit('update:formData', formValue())
  emit('next')
}
</script>
