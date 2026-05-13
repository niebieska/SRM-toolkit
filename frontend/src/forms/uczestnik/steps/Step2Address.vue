<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 2 – Dane adresowe</h2>

    <!-- Participant address -->
    <div class="space-y-4">
      <h3 class="font-semibold text-gray-700">Adres zamieszkania uczestnika</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Ulica *</label>
          <input v-model="local.street" type="text" :class="fieldClass('street')" />
          <p v-if="errors.street" class="text-red-500 text-xs mt-1">{{ errors.street }}</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nr domu/mieszkania *</label>
          <input v-model="local.houseNumber" type="text" :class="fieldClass('houseNumber')" />
          <p v-if="errors.houseNumber" class="text-red-500 text-xs mt-1">{{ errors.houseNumber }}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Kod pocztowy *</label>
          <input v-model="local.postalCode" type="text" placeholder="00-000" :class="fieldClass('postalCode')" />
          <p v-if="errors.postalCode" class="text-red-500 text-xs mt-1">{{ errors.postalCode }}</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Miejscowość *</label>
          <input v-model="local.city" type="text" :class="fieldClass('city')" />
          <p v-if="errors.city" class="text-red-500 text-xs mt-1">{{ errors.city }}</p>
        </div>
      </div>
    </div>

    <!-- Parent address (for minors) -->
    <div v-if="!isAdult" class="space-y-4">
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.sameAddress" class="w-4 h-4" />
        <span class="text-sm text-gray-700">Adres zamieszkania lub pobytu rodziców jest taki sam jak uczestnika</span>
      </label>

      <div v-if="!local.sameAddress" class="border border-gray-200 rounded-lg p-4 space-y-4">
        <h3 class="font-semibold text-gray-700">Adres rodziców / opiekunów</h3>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Ulica *</label>
            <input v-model="local.parentStreet" type="text" :class="fieldClass('parentStreet')" />
            <p v-if="errors.parentStreet" class="text-red-500 text-xs mt-1">{{ errors.parentStreet }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Nr domu/mieszkania *</label>
            <input v-model="local.parentHouseNumber" type="text" :class="fieldClass('parentHouseNumber')" />
            <p v-if="errors.parentHouseNumber" class="text-red-500 text-xs mt-1">{{ errors.parentHouseNumber }}</p>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Kod pocztowy *</label>
            <input v-model="local.parentPostalCode" type="text" placeholder="00-000" :class="fieldClass('parentPostalCode')" />
            <p v-if="errors.parentPostalCode" class="text-red-500 text-xs mt-1">{{ errors.parentPostalCode }}</p>
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Miejscowość *</label>
            <input v-model="local.parentCity" type="text" :class="fieldClass('parentCity')" />
            <p v-if="errors.parentCity" class="text-red-500 text-xs mt-1">{{ errors.parentCity }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="flex justify-between">
      <button @click="goPrev" class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition">
        ← Wstecz
      </button>
      <button @click="goNext" class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition">
        Dalej →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  formData: { type: Object, required: true },
  isAdult: { type: Boolean, default: false },
})
const emit = defineEmits(['update:formData', 'prev', 'next'])

const local = ref({ ...props.formData })
const errors = reactive({})

function fieldClass(field) {
  const base = 'w-full border rounded px-3 py-2 focus:outline-none focus:ring-2'
  return errors[field]
    ? `${base} border-red-500 focus:ring-red-400`
    : `${base} border-gray-300 focus:ring-gray-400`
}

function validate() {
  Object.keys(errors).forEach(k => delete errors[k])

  if (!local.value.street?.trim())      errors.street      = 'Podaj ulicę.'
  if (!local.value.houseNumber?.trim()) errors.houseNumber = 'Podaj numer domu.'
  if (!local.value.postalCode?.trim())  errors.postalCode  = 'Podaj kod pocztowy.'
  if (!local.value.city?.trim())        errors.city        = 'Podaj miejscowość.'

  if (!props.isAdult && !local.value.sameAddress) {
    if (!local.value.parentStreet?.trim())      errors.parentStreet      = 'Podaj ulicę rodziców.'
    if (!local.value.parentHouseNumber?.trim()) errors.parentHouseNumber = 'Podaj numer domu rodziców.'
    if (!local.value.parentPostalCode?.trim())  errors.parentPostalCode  = 'Podaj kod pocztowy rodziców.'
    if (!local.value.parentCity?.trim())        errors.parentCity        = 'Podaj miejscowość rodziców.'
  }

  return Object.keys(errors).length === 0
}

function goPrev() {
  emit('update:formData', { ...local.value })
  emit('prev')
}

function goNext() {
  if (!validate()) return
  emit('update:formData', { ...local.value })
  emit('next')
}
</script>
