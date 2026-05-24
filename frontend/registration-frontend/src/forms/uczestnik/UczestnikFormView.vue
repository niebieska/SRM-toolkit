<template>
  <div class="min-h-screen bg-gray-100 py-8 px-4">
    <div class="max-w-2xl mx-auto">
      <div class="text-center mb-6">
        <h1 class="text-2xl font-bold text-gray-800">Formularz zgłoszeniowy – Uczestnik</h1>
      </div>

      <!-- Stepper -->
      <div class="flex justify-between mb-8" v-if="currentStep <= 4">
        <div v-for="step in 4" :key="step" class="flex-1 text-center">
          <div
            class="w-8 h-8 rounded-full mx-auto flex items-center justify-center text-sm font-bold"
            :class="currentStep >= step ? 'bg-gray-800 text-white' : 'bg-gray-200 text-gray-500'"
          >
            {{ step }}
          </div>
          <div class="text-xs mt-1 text-gray-500">{{ stepLabels[step - 1] }}</div>
        </div>
      </div>

      <Step1PersonalData
        v-if="currentStep === 1"
        v-model:formData="formData"
        @next="currentStep = 2"
      />
      <Step2Address
        v-else-if="currentStep === 2"
        v-model:formData="formData"
        :isAdult="formData.isAdult"
        @prev="currentStep = 1"
        @next="currentStep = 3"
      />
      <Step3Health
        v-else-if="currentStep === 3"
        v-model:formData="formData"
        :gender="formData.gender"
        @prev="currentStep = 2"
        @next="currentStep = 4"
      />
      <Step4Consents
        v-else-if="currentStep === 4"
        v-model:formData="formData"
        :isAdult="formData.isAdult"
        @prev="currentStep = 3"
        @submit="handleSubmit"
      />
      <SuccessPage
        v-else-if="currentStep === 5"
        :registrationCode="registrationCode"
        @reset="resetForm"
      />
      <ErrorPage
        v-else-if="currentStep === 6"
        :message="errorMessage"
        @back="currentStep = 4"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import Step1PersonalData from './steps/Step1PersonalData.vue'
import Step2Address from './steps/Step2Address.vue'
import Step3Health from './steps/Step3Health.vue'
import Step4Consents from './steps/Step4Consents.vue'
import SuccessPage from './steps/SuccessPage.vue'
import ErrorPage from '../../components/ErrorPage.vue'
import { submitParticipantRegistration } from '../../api/registrationApi.js'

const currentStep = ref(1)
const stepLabels = ['Dane osobowe', 'Adres', 'Zdrowie', 'Zgody']
const registrationCode = ref('')
const errorMessage = ref('')

const emptyForm = () => ({
  turnusCode: '',
  firstName: '',
  lastName: '',
  pesel: '',
  isAdult: false,
  gender: null,
  email: '',
  phone: '',
  guardianFirstName: '',
  guardianLastName: '',
  guardianRelation: '',
  guardianEmail: '',
  guardianPhone: '',
  parentNames: '',
  iceFirstName: '',
  iceLastName: '',
  iceRelation: '',
  icePhone: '',
  street: '',
  houseNumber: '',
  postalCode: '',
  city: '',
  sameAddress: true,
  parentStreet: '',
  parentHouseNumber: '',
  parentPostalCode: '',
  parentCity: '',
  health: {
    q1: { answer: '', detail: '' },
    q2: { answer: '', detail: '' },
    q3: { answer: '', detail: '' },
    q4: { answer: '', detail: '' },
    q5: { answer: '', detail: '' },
    q6: { answer: '', detail: '' },
  },
  healthDeclaration: false,
  consent1: false,
  consent2: false,
  consent3: false,
})

const formData = ref(emptyForm())

async function handleSubmit() {
  try {
    const result = await submitParticipantRegistration(formData.value)
    registrationCode.value = result.registrationCode
    currentStep.value = 5
  } catch (e) {
    errorMessage.value = friendlyError(e.message)
    currentStep.value = 6
  }
}

function friendlyError(msg) {
  if (msg.includes('409') || msg.includes('ALREADY_REGISTERED')) return 'Ta osoba jest już zarejestrowana na ten turnus.'
  if (msg.includes('AGE_TOO_LOW'))         return 'Uczestnik nie spełnia wymogu minimalnego wieku dla tego turnusu.'
  if (msg.includes('TURNUS_INACTIVE'))     return 'Wybrany turnus nie jest aktywny.'
  if (msg.includes('REGISTRATION_CLOSED')) return 'Rejestracja na ten turnus jest zamknięta.'
  if (msg.includes('404'))                 return 'Wybrany turnus nie istnieje.'
  return 'Nie udało się wysłać zgłoszenia. Spróbuj ponownie lub skontaktuj się z organizatorem.'
}

function resetForm() {
  currentStep.value = 1
  registrationCode.value = ''
  errorMessage.value = ''
  formData.value = emptyForm()
}
</script>
