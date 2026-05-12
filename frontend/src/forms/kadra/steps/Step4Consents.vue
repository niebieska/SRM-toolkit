<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 4 – Zgody i oświadczenia</h2>

    <!-- Consent 1 -->
    <div class="border border-gray-200 rounded-lg p-4 space-y-2">
      <h3 class="font-semibold text-gray-700">1. Zgoda na przetwarzanie danych osobowych *</h3>
      <p class="text-sm text-gray-600" v-if="isAdult">
        Wyrażam zgodę na przetwarzanie moich danych osobowych przez organizatora w celu realizacji turnusu, zgodnie z RODO.
      </p>
      <p class="text-sm text-gray-600" v-else>
        Wyrażam zgodę na przetwarzanie danych osobowych mojego dziecka / podopiecznego przez organizatora w celu realizacji turnusu, zgodnie z RODO.
      </p>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.consent1" class="w-4 h-4" />
        <span class="text-sm font-medium text-gray-700">Akceptuję</span>
      </label>
    </div>

    <!-- Consent 2 -->
    <div class="border border-gray-200 rounded-lg p-4 space-y-2">
      <h3 class="font-semibold text-gray-700">2. Zgoda na wykorzystanie wizerunku</h3>
      <p class="text-sm text-gray-600" v-if="isAdult">
        Wyrażam zgodę na fotografowanie i filmowanie mojej osoby podczas turnusu oraz publikację zdjęć i nagrań w materiałach organizatora.
      </p>
      <p class="text-sm text-gray-600" v-else>
        Wyrażam zgodę na fotografowanie i filmowanie mojego dziecka / podopiecznego podczas turnusu oraz publikację zdjęć i nagrań w materiałach organizatora.
      </p>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.consent2" class="w-4 h-4" />
        <span class="text-sm font-medium text-gray-700">Akceptuję</span>
      </label>
    </div>

    <!-- Consent 3 -->
    <div class="border border-gray-200 rounded-lg p-4 space-y-2">
      <h3 class="font-semibold text-gray-700">3. Akceptacja regulaminu *</h3>
      <p class="text-sm text-gray-600" v-if="isAdult">
        Zapoznałem/am się z regulaminem turnusu i akceptuję jego warunki.
      </p>
      <p class="text-sm text-gray-600" v-else>
        Zapoznałem/am się z regulaminem turnusu i akceptuję jego warunki w imieniu swojego dziecka / podopiecznego.
      </p>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.consent3" class="w-4 h-4" />
        <span class="text-sm font-medium text-gray-700">Akceptuję</span>
      </label>
    </div>

    <div class="flex justify-between">
      <button @click="goPrev" class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition">
        ← Wstecz
      </button>
      <button @click="goSubmit" class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition">
        WYŚLIJ ZGŁOSZENIE
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
const emit = defineEmits(['update:formData', 'prev', 'submit'])

const local = ref({ ...props.formData })

function goPrev() {
  emit('update:formData', { ...local.value })
  emit('prev')
}

function goSubmit() {
  emit('update:formData', { ...local.value })
  emit('submit')
}
</script>
