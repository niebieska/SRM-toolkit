<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  required: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const wrapper = ref(null)
const inputText = ref(toPolishDate(props.modelValue))
const calendarOpen = ref(false)
const visibleMonth = ref(monthFromValue(props.modelValue))
const weekdays = ['Pn', 'Wt', 'Śr', 'Cz', 'Pt', 'So', 'Nd']
const monthLabel = computed(() => new Intl.DateTimeFormat('pl-PL', { month: 'long', year: 'numeric' }).format(visibleMonth.value))
const calendarDays = computed(() => {
  const year = visibleMonth.value.getFullYear()
  const month = visibleMonth.value.getMonth()
  const firstDay = new Date(year, month, 1)
  const mondayOffset = (firstDay.getDay() + 6) % 7
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  return [
    ...Array.from({ length: mondayOffset }, () => null),
    ...Array.from({ length: daysInMonth }, (_, index) => index + 1),
  ]
})

watch(() => props.modelValue, value => {
  inputText.value = toPolishDate(value)
  if (value) visibleMonth.value = monthFromValue(value)
})

function toPolishDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || '') ? value.split('-').reverse().join('.') : ''
}
function monthFromValue(value) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value || '')) {
    const [year, month] = value.split('-').map(Number)
    return new Date(year, month - 1, 1)
  }
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1)
}
function parseDate(value) {
  const match = /^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/.exec(value.trim())
  if (!match) return ''
  const [, dayText, monthText, yearText] = match
  const day = Number(dayText)
  const month = Number(monthText)
  const year = Number(yearText)
  const candidate = new Date(year, month - 1, day)
  if (candidate.getFullYear() !== year || candidate.getMonth() !== month - 1 || candidate.getDate() !== day) return ''
  return `${yearText}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
function updateFromInput(event) {
  const parsed = parseDate(inputText.value)
  if (parsed) {
    event.target.setCustomValidity('')
    emit('update:modelValue', parsed)
  } else if (!inputText.value.trim()) {
    event.target.setCustomValidity('')
    emit('update:modelValue', '')
  } else {
    event.target.setCustomValidity('Wpisz prawidłową datę w formacie dd.mm.rrrr.')
  }
}
function changeMonth(offset) {
  visibleMonth.value = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + offset, 1)
}
function selectDay(day) {
  if (!day) return
  const year = visibleMonth.value.getFullYear()
  const month = visibleMonth.value.getMonth() + 1
  const isoDate = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  inputText.value = toPolishDate(isoDate)
  wrapper.value?.querySelector('input')?.setCustomValidity('')
  emit('update:modelValue', isoDate)
  calendarOpen.value = false
}
function isSelected(day) {
  return Boolean(day && props.modelValue === `${visibleMonth.value.getFullYear()}-${String(visibleMonth.value.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`)
}
function onDocumentClick(event) {
  if (!wrapper.value?.contains(event.target)) calendarOpen.value = false
}
function onKeydown(event) {
  if (event.key === 'Escape') calendarOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="wrapper" class="relative mt-1">
    <div class="flex">
      <input
        v-model="inputText"
        type="text"
        inputmode="numeric"
        autocomplete="off"
        :required="required"
        :pattern="required ? '\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}' : undefined"
        placeholder="dd.mm.rrrr"
        aria-label="Data wpłaty w formacie dzień, miesiąc, rok"
        class="h-10 min-w-0 flex-1 rounded-l-lg border border-slate-300 px-3 text-sm focus:z-10 focus:border-slate-500 focus:outline-none focus:ring-2 focus:ring-slate-500"
        @input="updateFromInput"
        @focus="calendarOpen = false"
      />
      <button
        type="button"
        class="flex h-10 w-11 items-center justify-center rounded-r-lg border border-l-0 border-slate-300 bg-white text-slate-600 hover:bg-slate-50 focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500"
        aria-label="Otwórz kalendarz"
        :aria-expanded="calendarOpen"
        @click="calendarOpen = !calendarOpen"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="h-5 w-5" aria-hidden="true">
          <rect x="3.5" y="5" width="17" height="16" rx="2" />
          <path d="M7.5 3v4M16.5 3v4M3.5 9h17" />
        </svg>
      </button>
    </div>
    <p class="mt-1 text-xs text-slate-500">Wpisz datę jako dd.mm.rrrr lub wybierz ją z kalendarza.</p>
    <div v-if="calendarOpen" class="absolute bottom-full right-0 z-30 mb-2 w-72 rounded-xl border border-slate-200 bg-white p-3 shadow-xl" role="dialog" aria-label="Kalendarz">
      <div class="mb-3 flex items-center justify-between">
        <button type="button" class="rounded-lg px-2.5 py-1.5 text-slate-600 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" aria-label="Poprzedni miesiąc" @click="changeMonth(-1)">‹</button>
        <p class="text-sm font-semibold capitalize text-slate-900">{{ monthLabel }}</p>
        <button type="button" class="rounded-lg px-2.5 py-1.5 text-slate-600 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500" aria-label="Następny miesiąc" @click="changeMonth(1)">›</button>
      </div>
      <div class="grid grid-cols-7 gap-1 text-center">
        <span v-for="weekday in weekdays" :key="weekday" class="py-1 text-xs font-medium text-slate-500">{{ weekday }}</span>
        <span v-for="(day, index) in calendarDays" :key="`day-${index}`" class="flex h-8 items-center justify-center">
          <button
            v-if="day"
            type="button"
            class="h-8 w-8 rounded-full text-sm hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500"
            :class="isSelected(day) ? 'bg-slate-800 font-semibold text-white hover:bg-slate-700' : 'text-slate-800'"
            :aria-label="`${day} ${monthLabel}`"
            :aria-pressed="isSelected(day)"
            @click="selectDay(day)"
          >{{ day }}</button>
        </span>
      </div>
    </div>
  </div>
</template>
