<template>
  <div class="bg-white rounded-2xl shadow p-6 space-y-6">
    <h2 class="text-xl font-bold text-gray-800">KROK 3 – Wywiad zdrowotny</h2>

    <div class="space-y-4">
      <HealthQuestion
        :number="1"
        :question="q1Text"
        detailLabel="Nazwy leków i dawki:"
        :multiline="true"
        @update:answer="local.health.q1.answer = $event"
        @update:detail="local.health.q1.detail = $event"
      />
      <HealthQuestion
        :number="2"
        :question="q2Text"
        detailLabel="Jaką chorobą?"
        :multiline="false"
        @update:answer="local.health.q2.answer = $event"
        @update:detail="local.health.q2.detail = $event"
      />
      <HealthQuestion
        :number="3"
        :question="q3Text"
        detailLabel="Szczegóły:"
        :multiline="true"
        @update:answer="local.health.q3.answer = $event"
        @update:detail="local.health.q3.detail = $event"
      />
      <HealthQuestion
        :number="4"
        :question="q4Text"
        detailLabel="Leki, pokarmy lub inne alergeny:"
        :multiline="true"
        @update:answer="local.health.q4.answer = $event"
        @update:detail="local.health.q4.detail = $event"
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
import { ref, computed } from 'vue'
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

function goPrev() {
  emit('update:formData', { ...local.value })
  emit('prev')
}

function goNext() {
  emit('update:formData', { ...local.value })
  emit('next')
}
</script>
