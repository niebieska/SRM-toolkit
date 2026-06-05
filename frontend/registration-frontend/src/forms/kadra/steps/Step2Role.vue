<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 2 – Wybierz rolę w kadrze</h2>

    <!-- Minor info -->
    <div v-if="!isAdult" class="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-sm text-yellow-800">
      Osoby niepełnoletnie mogą pełnić następujące role: <strong>Kandydat na animatora</strong>, <strong>Sternik z
      opiekunem</strong>.
    </div>

    <!-- Role dropdown -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-1">Rola w kadrze *</label>
      <select v-model="local.role"
              class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400">
        <option value="">-- Wybierz rolę --</option>
        <option
            v-for="role in availableRoles"
            :key="role.value"
            :value="role.value"
        >
          {{ role.label }}
        </option>
      </select>
    </div>

    <div v-if="currentRole?.subroles?.length">
      <label class="block text-sm font-medium text-gray-700 mb-1">
        Funkcja *
      </label>

      <select
          v-model="local.subrole"
          class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400"
      >
        <option value="">-- Wybierz funkcję --</option>

        <option
            v-for="subrole in currentRole.subroles"
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
        </div>
      </div>
    </div>

    <div class="flex justify-between">
      <button @click="goPrev"
              class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition">
        ← Wstecz
      </button>
      <button @click="goNext"
              class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition">
        Dalej →
      </button>
    </div>
  </div>
</template>

<script setup>
import {ref, computed, watch} from 'vue'
import {staffRoles as allRoles, certificatesByRole} from '../../../config/staffRoles.js'

const props = defineProps({
  formData: {type: Object, required: true},
  isAdult: {type: Boolean, default: false},
})
const emit = defineEmits(['update:formData', 'prev', 'next'])
const local = ref({
  ...props.formData,
  certificateDetails: {...(props.formData.certificateDetails || {})},
})
const availableRoles = computed(() => {
  return allRoles.filter(role => {
    if (props.isAdult) {
      return !role.minorOnly
    }
    return !role.adultOnly
  })
})
const currentRole = computed(() => {
  return allRoles.find(role => role.value === local.value.role)
})

const selectedCertificates = ref(Object.keys(props.formData.certificates || {}).filter(k => props.formData.certificates[k]))

const currentCertificates = computed(() => {
  return certificatesByRole[local.value.role] || []
})

// Reset certificates when role changes
watch(() => local.value.role, () => {
  selectedCertificates.value = []
  local.value.certificateDetails = {}
  local.value.subrole = ''
})

function goPrev() {
  emit('update:formData', {...local.value})
  emit('prev')
}

function goNext() {
  const certificates = {}
  selectedCertificates.value.forEach(id => {
    certificates[id] = true
  })
  emit('update:formData', {...local.value, certificates})
  emit('next')
}
</script>
