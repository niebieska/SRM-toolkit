<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
const props = defineProps({ kind: { type: String, default: 'leave' } })
const emit = defineEmits(['stay', 'discard'])
const dialog = ref(null)
const message = computed(() => ({
  leave: 'Opuszczenie strony spowoduje utratę wprowadzonych zmian.',
  reload: 'Wczytanie zapisanej wersji zastąpi Twoje niezapisane zmiany.',
  logout: 'Wylogowanie spowoduje utratę wprowadzonych zmian.',
}[props.kind] || 'Opuszczenie strony spowoduje utratę wprowadzonych zmian.'))
const discardLabel = computed(() => ({
  reload: 'Odrzuć i wczytaj', logout: 'Odrzuć i wyloguj',
}[props.kind] || 'Odrzuć zmiany'))
onMounted(() => dialog.value.showModal())
onBeforeUnmount(() => dialog.value?.close())
</script>

<template>
  <dialog ref="dialog" aria-labelledby="discard-title" aria-describedby="discard-message" class="discard-dialog m-auto rounded-2xl border border-slate-200 bg-white p-0 text-slate-900 shadow-xl" @cancel.prevent="emit('stay')">
    <div class="p-5 sm:p-6">
      <h2 id="discard-title" class="text-lg font-semibold">Masz niezapisane zmiany</h2>
      <p id="discard-message" class="mt-2 text-sm leading-relaxed text-slate-600">{{ message }}</p>
      <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end sm:gap-3">
        <button type="button" class="min-h-10 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2" @click="emit('discard')">{{ discardLabel }}</button>
        <button type="button" autofocus class="min-h-10 rounded-lg bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2" @click="emit('stay')">Zostań</button>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.discard-dialog { width: min(28rem, calc(100% - 2rem)); }
.discard-dialog::backdrop { background: rgb(15 23 42 / 45%); }
</style>
