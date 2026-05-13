<template>
  <div class="bg-white rounded-2xl shadow p-10 text-center space-y-6">
    <div class="flex justify-center">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" fill="none" />
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01" />
      </svg>
    </div>
    <h2 class="text-2xl font-bold text-gray-800">Wystąpił błąd</h2>
    <p class="text-gray-600">{{ message }}</p>
    <button
      @click="emit('back')"
      class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition"
    >
      ← Wróć i spróbuj ponownie
    </button>
  </div>
</template>

<script setup>
defineProps({ message: { type: String, default: 'Nie udało się wysłać zgłoszenia. Spróbuj ponownie.' } })
const emit = defineEmits(['back'])
</script>
