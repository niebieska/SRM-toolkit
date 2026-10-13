<script setup>
defineProps({
  id: { type: String, required: true },
  label: { type: String, required: true },
  required: Boolean,
  help: { type: String, default: '' },
  error: { type: String, default: '' },
})
</script>

<template>
  <div class="min-w-0">
    <label :for="id" class="mb-1.5 block text-sm font-medium text-slate-800">
      {{ label }} <span v-if="required" aria-hidden="true" class="text-slate-500">*</span>
    </label>
    <slot />
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-sm text-red-700">{{ error }}</p>
    <p v-else-if="help" :id="`${id}-help`" class="mt-1.5 text-xs leading-relaxed text-slate-600">{{ help }}</p>
  </div>
</template>
