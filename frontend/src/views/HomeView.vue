<template>
  <div class="min-h-screen bg-gray-100 flex items-center justify-center p-4">
    <div class="bg-white rounded-2xl shadow-lg p-10 max-w-md w-full text-center">
      <h1 class="text-2xl font-bold text-gray-800 mb-2">SRM – Zgłoszenie</h1>
      <p class="text-gray-500 mb-8">Wybierz typ formularza:</p>
      <div class="flex flex-col gap-4">
        <RouterLink
          to="/kadra"
          class="block bg-gray-800 text-white text-lg font-semibold py-4 rounded-xl hover:bg-gray-700 transition"
        >
          Formularz Kadry
        </RouterLink>
        <RouterLink
          to="/uczestnik"
          class="block bg-gray-800 text-white text-lg font-semibold py-4 rounded-xl hover:bg-gray-700 transition"
        >
          Formularz Uczestnika
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
</script>
