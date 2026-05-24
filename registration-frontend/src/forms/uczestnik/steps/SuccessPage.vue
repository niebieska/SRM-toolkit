<template>
  <div class="bg-white rounded-2xl shadow p-10 text-center space-y-6">
    <div class="flex justify-center">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-20 h-20 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" fill="none" />
        <path stroke-linecap="round" stroke-linejoin="round" d="M7 13l3 3 7-7" />
      </svg>
    </div>
    <h2 class="text-2xl font-bold text-gray-800">Dziękujemy za zgłoszenie!</h2>
    <p class="text-gray-600">Potwierdzenie zgłoszenia zostało wysłane na podany adres e‑mail.</p>
    <div class="inline-block bg-gray-100 border border-gray-300 rounded-xl px-6 py-4">
      <p class="text-sm text-gray-500 mb-1">Twój numer zgłoszenia</p>
      <p class="text-2xl font-mono font-bold text-gray-800 tracking-widest">{{ registrationCode }}</p>
    </div>
    <p class="text-sm text-gray-500">Zachowaj ten numer — będzie potrzebny do weryfikacji.</p>
    <button
      @click="emit('reset')"
      class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition"
    >
      Wypełnij kolejne zgłoszenie
    </button>
  </div>
</template>

<script setup>
defineProps({ registrationCode: { type: String, required: true } })
const emit = defineEmits(['reset'])
</script>
