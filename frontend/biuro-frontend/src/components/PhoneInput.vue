<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  modelValue: { type: String, default: '' },
  error: { type: String, default: '' },
  required: Boolean,
})
const emit = defineEmits(['update:modelValue'])

const prefix = ref('48')
const digits = ref('')

function parseValue(value) {
  if (!value) {
    prefix.value = '48'
    digits.value = ''
    return
  }
  const normalized = value.startsWith('+') ? value.slice(1) : value
  const numeric = normalized.replace(/[\s-]/g, '')
  if (numeric.length > 9) {
    digits.value = numeric.slice(-9)
    prefix.value = numeric.slice(0, -9)
  } else {
    digits.value = numeric
    prefix.value = '48'
  }
}

function emitValue() {
  emit('update:modelValue', `+${prefix.value}${digits.value}`)
}

function onPrefixInput() {
  prefix.value = prefix.value.replace(/\D/g, '').slice(0, 4)
  emitValue()
}

function onDigitsInput() {
  digits.value = digits.value.replace(/\D/g, '').slice(0, 9)
  emitValue()
}

watch(() => props.modelValue, value => {
  if (value !== `+${prefix.value}${digits.value}`) parseValue(value)
}, { immediate: true })
</script>

<template>
  <div class="min-w-0">
    <div class="flex w-full min-w-0 items-start gap-1">
      <div class="flex shrink-0 items-center rounded border focus-within:ring-2" :class="error ? 'border-red-500 focus-within:ring-red-400' : 'border-slate-300 focus-within:ring-blue-700'">
        <span class="select-none px-2 py-2 text-slate-500">+</span>
        <input
          :id="`${id}-prefix`"
          v-model="prefix"
          type="text"
          inputmode="numeric"
          maxlength="4"
          aria-label="Prefiks kraju"
          :aria-invalid="error ? 'true' : undefined"
          :aria-describedby="error ? `${id}-error` : undefined"
          class="w-14 bg-transparent py-2 pr-2 text-center text-sm text-slate-900 focus:outline-none"
          @input="onPrefixInput"
        />
      </div>
      <input
        :id="`${id}-number`"
        v-model="digits"
        type="text"
        inputmode="numeric"
        maxlength="9"
        :required="required"
        placeholder="123456789"
        aria-label="Numer telefonu"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="error ? `${id}-error` : undefined"
        class="w-full min-w-0 rounded border px-3 py-2 text-sm text-slate-900 outline-none focus:ring-2"
        :class="error ? 'border-red-500 focus:ring-red-400' : 'border-slate-300 focus:ring-blue-700'"
        @input="onDigitsInput"
      />
    </div>
    <p v-if="error" :id="`${id}-error`" class="mt-1 text-xs font-normal text-red-700">{{ error }}</p>
  </div>
</template>
