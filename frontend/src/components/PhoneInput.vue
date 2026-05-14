<template>
  <div class="flex items-start gap-1">
    <div class="flex items-center border rounded focus-within:ring-2 shrink-0"
         :class="error ? 'border-red-500 focus-within:ring-red-400' : 'border-gray-300 focus-within:ring-gray-400'">
      <span class="px-2 py-2 text-gray-500 select-none">+</span>
      <input
        v-model="prefix"
        type="text"
        inputmode="numeric"
        maxlength="4"
        class="w-14 py-2 pr-2 focus:outline-none bg-transparent text-center"
        @input="onPrefixInput"
        @change="emitValue"
      />
    </div>
    <div class="flex-1">
      <input
        v-model="digits"
        type="text"
        inputmode="numeric"
        maxlength="9"
        placeholder="123456789"
        class="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2"
        :class="error ? 'border-red-500 focus:ring-red-400' : 'border-gray-300 focus:ring-gray-400'"
        @input="onDigitsInput"
        @change="emitValue"
      />
    </div>
  </div>
  <p v-if="error" class="text-red-500 text-xs mt-1">{{ error }}</p>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  error: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const prefix = ref('48')
const digits = ref('')

// Parse initial modelValue: +{prefix}{9digits}
function parseValue(val) {
  if (!val) return
  let v = val.startsWith('+') ? val.slice(1) : val
  if (v.length > 9) {
    digits.value = v.slice(-9)
    prefix.value = v.slice(0, v.length - 9) || '48'
  } else {
    digits.value = v
    prefix.value = '48'
  }
}

parseValue(props.modelValue)

watch(() => props.modelValue, (val) => {
  const combined = `+${prefix.value}${digits.value}`
  if (val !== combined) {
    parseValue(val)
  }
})

function onPrefixInput() {
  // Allow digits only
  prefix.value = prefix.value.replace(/\D/g, '')
  emitValue()
}

function onDigitsInput() {
  // Allow digits only
  digits.value = digits.value.replace(/\D/g, '')
  emitValue()
}

function emitValue() {
  emit('update:modelValue', `+${prefix.value}${digits.value}`)
}
</script>
