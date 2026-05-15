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
      <HealthQuestion
        :number="5"
        :question="q5Text"
        detailLabel="Proszę wpisać szczepienia (nazwa, rok):"
        :multiline="true"
        :answerError="errors.q5Answer"
        :detailError="errors.q5Detail"
        @update:answer="local.health.q5.answer = $event"
        @update:detail="local.health.q5.detail = $event"
      />
      <HealthQuestion
        :number="6"
        :question="q6Text"
        detailLabel="Proszę opisać:"
        :multiline="true"
        :answerError="errors.q6Answer"
        :detailError="errors.q6Detail"
        @update:answer="local.health.q6.answer = $event"
        @update:detail="local.health.q6.detail = $event"
      />
    </div>

    <div class="border border-gray-200 rounded-lg p-4 bg-gray-50">
      <label class="flex items-start gap-3 cursor-pointer">
        <input type="checkbox" v-model="local.healthDeclaration" class="w-4 h-4 mt-1" />
        <span class="text-sm text-gray-700">
          Oświadczam, że podane informacje są zgodne z prawdą i zobowiązuję się do niezwłocznego powiadomienia organizatora o wszelkich zmianach stanu zdrowia uczestnika.
        </span>
      </label>
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
    q5: { ...props.formData.health?.q5 },
    q6: { ...props.formData.health?.q6 },
  },
})

const he    = computed(() => props.gender === 'female' ? 'uczestniczka' : 'uczestnik')
const leczyl = computed(() => props.gender === 'female' ? 'uczestniczka leczyła się' : 'uczestnik leczył się')
const uczulony = computed(() => props.gender === 'female' ? 'uczulona' : 'uczulony')
const zaszczepiony = computed(() => props.gender === 'female' ? 'zaszczepiona' : 'zaszczepiony')

const q1Text = computed(() => `Czy ${he.value} przyjmuje na stałe leki?`)
const q2Text = computed(() => `Czy ${he.value} cierpi na przewlekłą chorobę?`)
const q3Text = computed(() => `Czy ${leczyl.value} psychiatrycznie albo cierpi na zaburzenia emocjonalne lub osobowościowe?`)
const q4Text = computed(() => `Czy ${he.value} jest ${uczulony.value}?`)
const q5Text = computed(() => `Czy ${he.value} jest ${zaszczepiony.value} (tężec, błonica lub inne)?`)
const q6Text = computed(() => `Czy ${he.value} ma specjalne potrzeby edukacyjne lub inne ważne informacje, o których organizator powinien wiedzieć?`)

const errors = reactive({})

function validate() {
  Object.keys(errors).forEach(k => delete errors[k])

  const questions = ['q1', 'q2', 'q3', 'q4', 'q5', 'q6']
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
