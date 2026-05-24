<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 4 – Zgody i oświadczenia</h2>

    <!-- Consent 1 (required) -->
    <div class="border rounded-lg p-4 space-y-2" :class="errors.consent1 ? 'border-red-400 bg-red-50' : 'border-gray-200'">
      <h3 class="font-semibold text-gray-700">1. Zgoda na przetwarzanie danych osobowych *</h3>
      <p class="text-sm text-gray-600" v-if="isAdult">
        Wyrażam zgodę na przetwarzanie moich danych osobowych przez Duszpasterstwo Młodzieży Księży Sercanów,
        ul. Główna 45, 33‑111 Koszyce Wielkie, w celu rejestracji uczestników Sercańskich Rekolekcji Młodzieżowych,
        zapewnienia bezpieczeństwa podczas turnusu, wykupienia polisy ubezpieczeniowej oraz otrzymywania informacji
        o kolejnych turnusach.
      </p>
      <p class="text-sm text-gray-600" v-else>
        Wyrażam zgodę na przetwarzanie wpisanych danych osobowych mojego dziecka przez Duszpasterstwo Młodzieży
        Księży Sercanów, ul. Główna 45, 33‑111 Koszyce Wielkie, w celu rejestracji uczestników Sercańskich
        Rekolekcji Młodzieżowych, zapewnienia bezpieczeństwa podczas turnusu, kontaktu z rodzicami, wykupienia
        polisy ubezpieczeniowej oraz otrzymywania informacji o kolejnych turnusach.
      </p>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.consent1" class="w-4 h-4" />
        <span class="text-sm font-medium text-gray-700">Akceptuję</span>
      </label>
      <p v-if="errors.consent1" class="text-red-500 text-xs">{{ errors.consent1 }}</p>
    </div>

    <!-- Consent 2 (optional) -->
    <div class="border border-gray-200 rounded-lg p-4 space-y-2">
      <h3 class="font-semibold text-gray-700">2. Zgoda na wykorzystanie wizerunku</h3>
      <p class="text-sm text-gray-600" v-if="isAdult">
        Wyrażam zgodę na umieszczanie zdjęć i filmów zawierających mój wizerunek, wykonanych podczas SRM,
        na stronach internetowych i profilach społecznościowych związanych z tym wydarzeniem.
      </p>
      <p class="text-sm text-gray-600" v-else>
        Wyrażam zgodę na umieszczanie zdjęć i filmów zawierających wizerunek mojego dziecka, wykonanych podczas SRM,
        na stronach internetowych i profilach społecznościowych związanych z tym wydarzeniem.
      </p>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.consent2" class="w-4 h-4" />
        <span class="text-sm font-medium text-gray-700">Akceptuję</span>
      </label>
    </div>

    <!-- Consent 3 (required) -->
    <div class="border rounded-lg p-4 space-y-2" :class="errors.consent3 ? 'border-red-400 bg-red-50' : 'border-gray-200'">
      <h3 class="font-semibold text-gray-700">3. Akceptacja regulaminu *</h3>
      <p class="text-sm text-gray-600" v-if="isAdult">
        Rozumiem charakter rekolekcji oraz wiem, jakich zasad będę musiał(-a) przestrzegać.
        Potwierdzam zapoznanie się z regulaminem i zobowiązuję się do jego przestrzegania.
      </p>
      <p class="text-sm text-gray-600" v-else>
        Rozumiem charakter rekolekcji oraz wiem, jakich zasad będzie musiało przestrzegać moje dziecko.
        Potwierdzam zapoznanie się z regulaminem i biorę odpowiedzialność za podporządkowanie się jego zasadom
        przez moje dziecko.
      </p>
      <label class="flex items-center gap-2 cursor-pointer">
        <input type="checkbox" v-model="local.consent3" class="w-4 h-4" />
        <span class="text-sm font-medium text-gray-700">Akceptuję</span>
      </label>
      <p v-if="errors.consent3" class="text-red-500 text-xs">{{ errors.consent3 }}</p>
    </div>

    <p v-if="submitError" class="text-red-600 text-sm text-center">{{ submitError }}</p>

    <div class="flex justify-between">
      <button @click="goPrev" class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition">
        ← Wstecz
      </button>
      <button
        @click="goSubmit"
        :disabled="submitting"
        class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition disabled:opacity-50"
      >
        {{ submitting ? 'Wysyłanie…' : 'WYŚLIJ ZGŁOSZENIE' }}
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
const emit = defineEmits(['update:formData', 'prev', 'submit'])

const local = ref({ ...props.formData })
const errors = reactive({})
const submitting = ref(false)
const submitError = ref(null)

function validate() {
  Object.keys(errors).forEach(k => delete errors[k])
  if (!local.value.consent1) errors.consent1 = 'Ta zgoda jest wymagana.'
  if (!local.value.consent3) errors.consent3 = 'Akceptacja regulaminu jest wymagana.'
  return Object.keys(errors).length === 0
}

function goPrev() {
  emit('update:formData', { ...local.value })
  emit('prev')
}

async function goSubmit() {
  if (!validate()) return
  submitting.value = true
  submitError.value = null
  try {
    emit('update:formData', { ...local.value })
    emit('submit')
  } finally {
    submitting.value = false
  }
}
</script>
