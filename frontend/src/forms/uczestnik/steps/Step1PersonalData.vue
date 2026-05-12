<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 1 – Dane osobowe</h2>

    <!-- Turnus selector -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-1">Turnus *</label>
      <div v-if="loadingTurnusy" class="flex items-center gap-2 text-gray-500 text-sm">
        <span class="animate-spin inline-block w-4 h-4 border-2 border-gray-400 border-t-transparent rounded-full"></span>
        Ładowanie…
      </div>
      <div v-else-if="turnusError" class="text-red-600 text-sm">{{ turnusError }}</div>
      <select
        v-else
        v-model="local.turnusCode"
        class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400"
      >
        <option value="">-- Wybierz turnus --</option>
        <option v-for="t in turnusy" :key="t.turnusCode" :value="t.turnusCode">
          {{ t.turnusName }} ({{ t.startDate }} – {{ t.endDate }})
        </option>
      </select>
      <div v-if="selectedTurnus" class="mt-3 border border-gray-200 rounded-lg p-3 bg-gray-50 text-sm space-y-1">
        <div><span class="font-medium">Nazwa:</span> {{ selectedTurnus.turnusName }}</div>
        <div><span class="font-medium">Termin:</span> {{ selectedTurnus.startDate }} – {{ selectedTurnus.endDate }}</div>
        <div><span class="font-medium">Lokalizacja:</span> {{ selectedTurnus.location }}</div>
        <div v-if="selectedTurnus.description"><span class="font-medium">Opis:</span> {{ selectedTurnus.description }}</div>
      </div>
    </div>

    <!-- Name -->
    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Imię (imiona) *</label>
        <input v-model="local.firstName" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Nazwisko *</label>
        <input v-model="local.lastName" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
      </div>
    </div>

    <!-- PESEL -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-1">PESEL *</label>
      <input
        v-model="pesel"
        type="text"
        maxlength="11"
        placeholder="11 cyfr"
        class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400"
        @input="onPeselInput"
      />
      <div v-if="pesel.length === 11" class="mt-1 text-sm text-gray-600">
        Wiek: <span class="font-medium">{{ isAdult ? 'dorosły' : 'niepełnoletni' }}</span> |
        Płeć: <span class="font-medium">{{ gender === 'male' ? 'mężczyzna' : gender === 'female' ? 'kobieta' : '–' }}</span>
      </div>
    </div>

    <!-- Contact -->
    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">E-mail *</label>
        <input v-model="local.email" type="email" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Telefon *</label>
        <input v-model="local.phone" type="tel" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
      </div>
    </div>

    <!-- Guardian (minor) -->
    <div v-if="!isAdult && pesel.length === 11" class="border border-yellow-200 rounded-lg p-4 bg-yellow-50 space-y-4">
      <h3 class="font-semibold text-gray-700">Dane rodzica / opiekuna prawnego</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Imię *</label>
          <input v-model="local.guardianFirstName" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nazwisko *</label>
          <input v-model="local.guardianLastName" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Relacja *</label>
        <select v-model="local.guardianRelation" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400">
          <option value="">-- Wybierz --</option>
          <option value="matka">Matka</option>
          <option value="ojciec">Ojciec</option>
          <option value="opiekun_prawny">Opiekun prawny</option>
        </select>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">E-mail *</label>
          <input v-model="local.guardianEmail" type="email" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Telefon *</label>
          <input v-model="local.guardianPhone" type="tel" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
      </div>
    </div>

    <!-- ICE (adult) -->
    <div v-if="isAdult && pesel.length === 11" class="border border-blue-200 rounded-lg p-4 bg-blue-50 space-y-4">
      <h3 class="font-semibold text-gray-700">Osoba do kontaktu w nagłych przypadkach (ICE)</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Imię *</label>
          <input v-model="local.iceFirstName" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nazwisko *</label>
          <input v-model="local.iceLastName" type="text" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
        </div>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Relacja *</label>
        <select v-model="local.iceRelation" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400">
          <option value="">-- Wybierz --</option>
          <option value="matka">Matka</option>
          <option value="ojciec">Ojciec</option>
          <option value="opiekun_prawny">Opiekun prawny</option>
          <option value="inna">Inna</option>
        </select>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Telefon *</label>
        <input v-model="local.icePhone" type="tel" class="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-gray-400" />
      </div>
    </div>

    <div class="flex justify-end">
      <button @click="goNext" class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition">
        Dalej →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { usePesel } from '../../../composables/usePesel.js'
import { fetchTurnusy } from '../../../api/turnusApi.js'

const props = defineProps({
  formData: { type: Object, required: true },
})
const emit = defineEmits(['update:formData', 'next'])

const local = ref({ ...props.formData })
const { pesel, isAdult, gender } = usePesel()
pesel.value = local.value.pesel || ''

watch(() => local.value.pesel, (val) => {
  pesel.value = val || ''
})

function onPeselInput() {
  local.value.pesel = pesel.value
  local.value.isAdult = isAdult.value
  local.value.gender = gender.value
}

const turnusy = ref([])
const loadingTurnusy = ref(true)
const turnusError = ref(null)

onMounted(async () => {
  try {
    turnusy.value = await fetchTurnusy()
  } catch (e) {
    turnusError.value = 'Nie udało się załadować listy turnusów. Spróbuj odświeżyć stronę.'
  } finally {
    loadingTurnusy.value = false
  }
})

const selectedTurnus = computed(() => {
  return turnusy.value.find(t => t.turnusCode === local.value.turnusCode) || null
})

function goNext() {
  emit('update:formData', { ...local.value, isAdult: isAdult.value, gender: gender.value })
  emit('next')
}
</script>
