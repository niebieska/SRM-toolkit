<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 2 – Dane adresowe</h2>

    <!-- Participant address -->
    <div class="space-y-4">
      <h3 class="font-semibold text-gray-700">Adres zamieszkania uczestnika</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Ulica *</label>
          <input v-model="local.street" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nr domu/mieszkania *</label>
          <input v-model="local.houseNumber" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Kod pocztowy *</label>
          <input v-model="local.postalCode" type="text" placeholder="00-000" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Miejscowość *</label>
          <input v-model="local.city" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
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
            <input v-model="local.parentStreet" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Nr domu/mieszkania *</label>
            <input v-model="local.parentHouseNumber" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Kod pocztowy *</label>
            <input v-model="local.parentPostalCode" type="text" placeholder="00-000" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Miejscowość *</label>
            <input v-model="local.parentCity" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
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
import { ref } from 'vue'

const props = defineProps({
  formData: { type: Object, required: true },
  isAdult: { type: Boolean, default: false },
})
const emit = defineEmits(['update:formData', 'prev', 'next'])

const local = ref({ ...props.formData })

function goPrev() {
  emit('update:formData', { ...local.value })
  emit('prev')
}

function goNext() {
  emit('update:formData', { ...local.value })
  emit('next')
}
</script>
