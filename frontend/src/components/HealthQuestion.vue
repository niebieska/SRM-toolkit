<template>
  <div class="border border-gray-200 rounded-lg p-4 bg-white">
    <p class="font-medium text-gray-800 mb-3">
      <span class="font-bold">{{ number }}.</span> {{ question }}
    </p>
    <div class="flex gap-6 mb-3">
      <label class="flex items-center gap-2 cursor-pointer">
        <input
          type="radio"
          :name="`question-${number}`"
          value="tak"
          v-model="answer"
          class="w-4 h-4"
        />
        <span>Tak</span>
      </label>
      <label class="flex items-center gap-2 cursor-pointer">
        <input
          type="radio"
          :name="`question-${number}`"
          value="nie"
          v-model="answer"
          class="w-4 h-4"
        />
        <span>Nie</span>
      </label>
    </div>
    <div v-if="answer === 'tak'" class="mt-2">
      <label class="block text-sm font-medium text-gray-700 mb-1">{{ detailLabel }}</label>
      <textarea
        v-if="multiline"
        v-model="detail"
        rows="3"
        class="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-400"
      ></textarea>
      <input
        v-else
        type="text"
        v-model="detail"
        class="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-400"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  number: { type: Number, required: true },
  question: { type: String, required: true },
  detailLabel: { type: String, default: 'Szczegóły' },
  multiline: { type: Boolean, default: true },
})

const emit = defineEmits(['update:answer', 'update:detail'])

const answer = ref('')
const detail = ref('')

watch(answer, (val) => {
  emit('update:answer', val)
  if (val !== 'tak') {
    detail.value = ''
    emit('update:detail', '')
  }
})

watch(detail, (val) => {
  emit('update:detail', val)
})
</script>
