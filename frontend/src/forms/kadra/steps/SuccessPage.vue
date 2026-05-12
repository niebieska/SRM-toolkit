<template>
  <div class="bg-white rounded-2xl shadow p-10 text-center space-y-6">
    <!-- Green checkmark SVG -->
    <div class="flex justify-center">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" fill="none" />
        <path stroke-linecap="round" stroke-linejoin="round" d="M7 13l3 3 7-7" />
      </svg>
    </div>
    <h2 class="text-2xl font-bold text-gray-800">Dziękujemy za zgłoszenie!</h2>
    <p class="text-gray-600">Twoje zgłoszenie do kadry zostało przyjęte. Skontaktujemy się z Tobą wkrótce.</p>
    <button
      @click="emit('reset')"
      class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition"
    >
      Wypełnij kolejne zgłoszenie
    </button>
  </div>
</template>

<script setup>
const emit = defineEmits(['reset'])
</script>
