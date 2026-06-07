<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 3 – Kwestionariusz</h2>

    <div class="space-y-4">
      <HealthQuestion
          :number="1"
          question="Czy posiadasz choroby przewlekłe, o których organizator powinien wiedzieć? *"
          placeholder="Jakie? Prosze krótko opisać"
          :multiline="true"
          :answerError="errors.q1Answer"
          :detailError="errors.q1Detail"
          @update:answer="local.health.q1.answer = $event"
          @update:detail="local.health.q1.detail = $event"
      />

      <HealthQuestion
          :number="2"
          question="Czy występują u Ciebie alergie lub nietolerancje pokarmowe, które powinny zostać uwzględnione przy przygotowywaniu posiłków? *"
          placeholder="Jakie? Prosze krótko opisać"
          :multiline="true"
          :answerError="errors.q2Answer"
          :detailError="errors.q2Detail"
          @update:answer="local.health.q2.answer = $event"
          @update:detail="local.health.q2.detail = $event"
      />

      <div class="border border-gray-200 rounded-lg p-4 bg-white">
        <p class="font-medium text-gray-800 mb-3">
          3. Dodatkowe informacje istotne pod kątem organizacji turnusu
        </p>

        <textarea
            v-model="local.health.additionalInfo"
            rows="4"
            maxlength="1000"
            placeholder="Istotne kwestie organizacyjne, np. inny termin przyjazdu, wyjazdu"
            class="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-400"
        />
      </div>
    </div>

    <div class="flex justify-between">
      <button
          @click="goPrev"
          class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition"
      >
        ← Wstecz
      </button>

      <button
          @click="goNext"
          class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition"
      >
        Dalej →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import HealthQuestion from '../../../components/HealthQuestion.vue'

const props = defineProps({
  formData: { type: Object, required: true },
  gender: { type: String, default: null },
})

const emit = defineEmits(['update:formData', 'prev', 'next'])

const emptyHealthQuestion = {
  answer: '',
  detail: '',
}

const local = ref({
  ...props.formData,
  health: {
    q1: { ...emptyHealthQuestion, ...(props.formData.health?.q1 || {}) },
    q2: { ...emptyHealthQuestion, ...(props.formData.health?.q2 || {}) },
    additionalInfo: props.formData.health?.additionalInfo || '',
  },
})

const errors = reactive({})

function validate() {
  Object.keys(errors).forEach(key => delete errors[key])

  const questions = ['q1', 'q2']

  questions.forEach(q => {
    const item = local.value.health[q]

    if (!item?.answer) {
      errors[`${q}Answer`] = 'To pole jest wymagane.'
    } else if (item.answer === 'tak' && !item.detail?.trim()) {
      errors[`${q}Detail`] = 'Proszę podać szczegóły.'
    }
  })

  return Object.keys(errors).length === 0
}

function goPrev() {
  emit('update:formData', { ...local.value })
  emit('prev')
}

function goNext() {
  if (!validate()) return

  emit('update:formData', { ...local.value })
  emit('next')
}
</script>