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
      <select v-else v-model="local.turnusCode" :class="fieldClass('turnusCode')">
        <option value="">-- Wybierz turnus --</option>
        <option v-for="t in turnusy" :key="t.turnusCode" :value="t.turnusCode">
          {{ t.turnusName }} ({{ t.startDate }} – {{ t.endDate }})
        </option>
      </select>
      <p v-if="errors.turnusCode" class="text-red-500 text-xs mt-1">{{ errors.turnusCode }}</p>
      <div v-if="selectedTurnus" class="mt-3 border border-gray-200 rounded-lg p-3 bg-gray-50 text-sm space-y-1">
        <div><span class="font-medium">Nazwa:</span> {{ selectedTurnus.turnusName }}</div>
        <div><span class="font-medium">Termin:</span> {{ selectedTurnus.startDate }} – {{ selectedTurnus.endDate }}</div>
        <div><span class="font-medium">Opis:</span> {{ selectedTurnus.turnusDescription }}</div>
      </div>
    </div>

    <!-- Name -->
    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Imię (imiona) *</label>
        <input v-model="local.firstName" type="text" :class="fieldClass('firstName')" />
        <p v-if="errors.firstName" class="text-red-500 text-xs mt-1">{{ errors.firstName }}</p>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Nazwisko *</label>
        <input v-model="local.lastName" type="text" :class="fieldClass('lastName')" />
        <p v-if="errors.lastName" class="text-red-500 text-xs mt-1">{{ errors.lastName }}</p>
      </div>
    </div>
    <!-- PESEL -->
    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">PESEL *</label>
        <input
            v-model="pesel"
            type="text"
            inputmode="numeric"
            maxlength="11"
            placeholder="11 cyfr"
            :class="fieldClass('pesel')"
            @input="onPeselInput"
        />
        <p v-if="errors.pesel" class="text-red-500 text-xs mt-1">{{ errors.pesel }}</p>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Płeć</label>
        <input
            type="text"
            readonly
            :value="gender === 'male' ? 'Mężczyzna' : gender === 'female' ? 'Kobieta' : ''"
            class="w-full border border-gray-200 rounded px-3 py-2 bg-gray-50 text-gray-600 cursor-default"
        />
      </div>
    </div>

    <!-- Contact -->
    <div class="grid grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">E-mail *</label>
        <input v-model="local.email" type="email" :class="fieldClass('email')" />
        <p v-if="errors.email" class="text-red-500 text-xs mt-1">{{ errors.email }}</p>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Telefon *</label>
        <PhoneInput v-model="local.phone" :error="errors.phone" />
      </div>
    </div>

    <!-- Guardian (minor) -->
    <div v-if="!isAdult && pesel.length === 11" class="border border-yellow-200 rounded-lg p-4 bg-yellow-50 space-y-4">
      <h3 class="font-semibold text-gray-700">Dane rodzica / opiekuna prawnego</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Imię *</label>
          <input v-model="local.guardianFirstName" type="text" :class="fieldClass('guardianFirstName')" />
          <p v-if="errors.guardianFirstName" class="text-red-500 text-xs mt-1">{{ errors.guardianFirstName }}</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nazwisko *</label>
          <input v-model="local.guardianLastName" type="text" :class="fieldClass('guardianLastName')" />
          <p v-if="errors.guardianLastName" class="text-red-500 text-xs mt-1">{{ errors.guardianLastName }}</p>
        </div>
      </div>
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">Relacja *</label>
        <select v-model="local.guardianRelation" :class="fieldClass('guardianRelation')">
          <option value="">-- Wybierz --</option>
          <option value="matka">Matka</option>
          <option value="ojciec">Ojciec</option>
          <option value="opiekun_prawny">Opiekun prawny</option>
        </select>
        <p v-if="errors.guardianRelation" class="text-red-500 text-xs mt-1">{{ errors.guardianRelation }}</p>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">E-mail *</label>
          <input v-model="local.guardianEmail" type="email" :class="fieldClass('guardianEmail')" />
          <p v-if="errors.guardianEmail" class="text-red-500 text-xs mt-1">{{ errors.guardianEmail }}</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Telefon *</label>
          <PhoneInput v-model="local.guardianPhone" :error="errors.guardianPhone" />
        </div>
      </div>
      <!-- Parent names (combined, pre-filled from guardian) -->
      <div>
        <label class="block text-sm font-medium text-gray-700 mb-1">
          Imiona i nazwiska rodziców (opiekunów prawnych) *
        </label>
        <input
          v-model="local.parentNames"
          type="text"
          placeholder="np. Jan Kowalski, Anna Kowalska"
          :class="fieldClass('parentNames')"
          @input="onParentNamesInput"
        />
        <p class="text-xs text-gray-400 mt-1">Wypełnione automatycznie na podstawie danych opiekuna — można edytować.</p>
        <p v-if="errors.parentNames" class="text-red-500 text-xs mt-1">{{ errors.parentNames }}</p>
      </div>
    </div>

    <!-- ICE section (adult) -->
    <div v-if="isAdult && pesel.length === 11" class="border border-blue-200 rounded-lg p-4 bg-blue-50 space-y-4">
      <h3 class="font-semibold text-gray-700">Osoba do kontaktu w nagłych przypadkach (ICE)</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Imię *</label>
          <input v-model="local.iceFirstName" type="text" :class="fieldClass('iceFirstName')" />
          <p v-if="errors.iceFirstName" class="text-red-500 text-xs mt-1">{{ errors.iceFirstName }}</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Nazwisko *</label>
          <input v-model="local.iceLastName" type="text" :class="fieldClass('iceLastName')" />
          <p v-if="errors.iceLastName" class="text-red-500 text-xs mt-1">{{ errors.iceLastName }}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Relacja *</label>
          <select v-model="local.iceRelation" :class="fieldClass('iceRelation')">
            <option value="">-- Wybierz --</option>
            <option value="matka">Matka</option>
            <option value="ojciec">Ojciec</option>
            <option value="opiekun_prawny">Opiekun prawny</option>
            <option value="inna">Inna</option>
          </select>
          <p v-if="errors.iceRelation" class="text-red-500 text-xs mt-1">{{ errors.iceRelation }}</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Telefon *</label>
          <PhoneInput v-model="local.icePhone" :error="errors.icePhone" />
        </div>
      </div>
      <div v-if="local.iceRelation === 'inna'">
        <label class="block text-sm font-medium text-gray-700 mb-1">Podaj relację *</label>
        <input v-model="local.iceRelationOther" type="text" placeholder="np. brat, znajomy" :class="fieldClass('iceRelationOther')" />
        <p v-if="errors.iceRelationOther" class="text-red-500 text-xs mt-1">{{ errors.iceRelationOther }}</p>
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
import { ref, computed, onMounted, watch, reactive } from 'vue'
import { usePesel } from '../../../composables/usePesel.js'
import { fetchTurnusy } from '../../../api/turnusApi.js'
import { validatePesel, validateEmail, validatePhone } from '../../../utils/validators.js'
import PhoneInput from '../../../components/PhoneInput.vue'

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

// Track last auto-generated value so we only overwrite parentNames
// when the user hasn't manually edited it.
let lastAutoParentNames = local.value.parentNames || ''
let parentNamesManuallyEdited = false

function onParentNamesInput() {
  // Mark as manually edited if it diverges from the last auto value
  parentNamesManuallyEdited = local.value.parentNames !== lastAutoParentNames
}

watch(
  [() => local.value.guardianFirstName, () => local.value.guardianLastName],
  ([fn, ln]) => {
    const auto = [fn, ln].filter(Boolean).join(' ')
    if (!parentNamesManuallyEdited) {
      local.value.parentNames = auto
      lastAutoParentNames = auto
    }
  }
)

// Turnus
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

const selectedTurnus = computed(() =>
  turnusy.value.find(t => t.turnusCode === local.value.turnusCode) || null
)

// Validation
const errors = reactive({})

function fieldClass(field) {
  const base = 'w-full border rounded px-3 py-2 focus:outline-none focus:ring-2'
  return errors[field]
    ? `${base} border-red-500 focus:ring-red-400`
    : `${base} border-gray-300 focus:ring-gray-400`
}

function validate() {
  Object.keys(errors).forEach(k => delete errors[k])

  if (!local.value.turnusCode)          errors.turnusCode  = 'Wybierz turnus.'
  if (!local.value.firstName?.trim())   errors.firstName   = 'Podaj imię.'
  if (!local.value.lastName?.trim())    errors.lastName    = 'Podaj nazwisko.'

  if (pesel.value.length !== 11) {
    errors.pesel = 'PESEL musi mieć 11 cyfr.'
  } else if (!validatePesel(pesel.value)) {
    errors.pesel = 'PESEL ma nieprawidłową sumę kontrolną.'
  }

  if (!local.value.email?.trim()) {
    errors.email = 'Podaj adres e-mail.'
  } else if (!validateEmail(local.value.email)) {
    errors.email = 'Podaj prawidłowy adres e-mail.'
  }

  if (!validatePhone(local.value.phone)) {
    errors.phone = 'Podaj prawidłowy numer telefonu (9 cyfr).'
  }

  if (!isAdult.value && pesel.value.length === 11) {
    if (!local.value.guardianFirstName?.trim()) errors.guardianFirstName = 'Podaj imię opiekuna.'
    if (!local.value.guardianLastName?.trim())  errors.guardianLastName  = 'Podaj nazwisko opiekuna.'
    if (!local.value.guardianRelation)          errors.guardianRelation  = 'Wybierz relację.'
    if (!local.value.guardianEmail?.trim()) {
      errors.guardianEmail = 'Podaj e-mail opiekuna.'
    } else if (!validateEmail(local.value.guardianEmail)) {
      errors.guardianEmail = 'Podaj prawidłowy adres e-mail opiekuna.'
    }
    if (!validatePhone(local.value.guardianPhone)) {
      errors.guardianPhone = 'Podaj prawidłowy numer telefonu opiekuna (9 cyfr).'
    }
    if (!local.value.parentNames?.trim())       errors.parentNames       = 'Podaj imiona i nazwiska rodziców.'
  }

  if (isAdult.value && pesel.value.length === 11) {
    if (!local.value.iceFirstName?.trim()) errors.iceFirstName = 'Podaj imię osoby ICE.'
    if (!local.value.iceLastName?.trim())  errors.iceLastName  = 'Podaj nazwisko osoby ICE.'
    if (!local.value.iceRelation)          errors.iceRelation  = 'Wybierz relację.'
    if (local.value.iceRelation === 'inna' && !local.value.iceRelationOther?.trim()) {
      errors.iceRelationOther = 'Podaj relację.'
    }
    if (!validatePhone(local.value.icePhone)) {
      errors.icePhone = 'Podaj prawidłowy numer telefonu osoby ICE (9 cyfr).'
    }
  }

  return Object.keys(errors).length === 0
}

function goNext() {
  if (!validate()) return
  emit('update:formData', { ...local.value, isAdult: isAdult.value, gender: gender.value })
  emit('next')
}
</script>
