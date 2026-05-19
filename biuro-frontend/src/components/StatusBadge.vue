<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    default: '',
  },
})

const view = computed(() => {
  if (props.status === 'ACCEPTED') {
    return { label: 'Zaakceptowane', classes: 'bg-green-100 text-green-700' }
  }
  if (props.status === 'REJECTED') {
    return { label: 'Odrzucone', classes: 'bg-red-100 text-red-700' }
  }
  return { label: 'Nowe', classes: 'bg-blue-100 text-blue-700' }
})
</script>

<template>
  <span class="inline-flex rounded-full px-2 py-1 text-xs font-medium" :class="view.classes">
    {{ view.label }}
  </span>
</template>
