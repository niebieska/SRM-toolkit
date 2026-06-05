<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 3 – Wywiad zdrowotny</h2>

    <div class="space-y-4">
      <HealthQuestion
        :number="1"
        :question="q1Text"
        detailLabel="Nazwy leków i dawki:"
        :multiline="true"
        :answerError="errors.q1Answer"
        :detailError="errors.q1Detail"
        @update:answer="local.health.q1.answer = $event"
        @update:detail="local.health.q1.detail = $event"
      />
      <HealthQuestion
        :number="2"
        :question="q2Text"
        detailLabel="Jaką chorobą?"
        :multiline="false"
        :answerError="errors.q2Answer"
        :detailError="errors.q2Detail"
        @update:answer="local.health.q2.answer = $event"
        @update:detail="local.health.q2.detail = $event"
      />
      <HealthQuestion
        :number="3"
        :question="q3Text"
        detailLabel="Szczegóły:"
        :multiline="true"
        :answerError="errors.q3Answer"
        :detailError="errors.q3Detail"
        @update:answer="local.health.q3.answer = $event"
        @update:detail="local.health.q3.detail = $event"
      />
      <HealthQuestion
        :number="4"
        :question="q4Text"
        detailLabel="Leki, pokarmy lub inne alergeny:"
        :multiline="true"
        :answerError="errors.q4Answer"
        :detailError="errors.q4Detail"
        @update:answer="local.health.q4.answer = $event"
        @update:detail="local.health.q4.detail = $event"
      />
    </div>

     <div class="flex justify-between">
      <button @click="goPrev" class="border border-gray-300 text-gray-700 px-8 py-3 rounded-xl font-semibold hover:bg-gray-50 transition">
        ← Wstecz
      </button>
      <button @click="goNext" class="bg-gray-800 text-white px-8 py-3 rounded-xl font-semibold hover:bg-gray-700 transition">
        Dalej →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import HealthQuestion from '../../../components/HealthQuestion.vue'

const props = defineProps({
  formData: { type: Object, required: true },
  gender: { type: String, default: null },
})
const emit = defineEmits(['update:formData', 'prev', 'next'])

const local = ref({
  ...props.formData,
  health: {
    q1: { ...props.formData.health?.q1 },
    q2: { ...props.formData.health?.q2 },
    q3: { ...props.formData.health?.q3 },
    q4: { ...props.formData.health?.q4 },
  },
})

const participantSuffix = computed(() => {
  if (props.gender === 'male') return 'uczestnik'
  if (props.gender === 'female') return 'uczestniczka'
  return 'uczestnik/uczestniczka'
})

const leczylSie = computed(() => {
  if (props.gender === 'male') return 'leczył się'
  if (props.gender === 'female') return 'leczyła się'
  return 'leczył się / leczyła się'
})

const uczulony = computed(() => {
  if (props.gender === 'male') return 'uczulony'
  if (props.gender === 'female') return 'uczulona'
  return 'uczulony/uczulona'
})

const q1Text = computed(() => `Czy ${participantSuffix.value} przyjmuje na stałe leki?`)
const q2Text = computed(() => `Czy ${participantSuffix.value} cierpi na przewlekłą chorobę?`)
const q3Text = computed(() => `Czy ${participantSuffix.value} ${leczylSie.value} psychiatrycznie albo cierpi na zaburzenia emocjonalne lub osobowościowe?`)
const q4Text = computed(() => `Czy ${participantSuffix.value} jest ${uczulony.value}?`)

const errors = reactive({})

function validate() {
  Object.keys(errors).forEach(k => delete errors[k])

  const questions = ['q1', 'q2', 'q3', 'q4']
  questions.forEach((q) => {
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
