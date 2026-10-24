<script setup>
import { computed, ref, toRaw } from 'vue'
import { useAuthStore } from '../stores/auth'
import { updateRegistrationData } from '../api/registrationApi'

const props = defineProps({ detail: { type: Object, required: true }, reloadError: { type: String, default: '' } })
const emit = defineEmits(['saved', 'cancel', 'dirty-change'])
const auth = useAuthStore()
const original = structuredClone(toRaw(props.detail.payload || {}))
const draft = ref(structuredClone(original))
const saving = ref(false)
const errorMessage = ref('')
const staleVersion = ref(false)

const isStaff = computed(() => props.detail.registrationType === 'STAFF')
const isMinor = computed(() => props.detail.minor === true)
const questionEntries = computed(() => Object.entries(draft.value.health || {})
  .filter(([key, value]) => (isStaff.value ? ['q1', 'q2'] : ['q1', 'q2', 'q3']).includes(key) && value && typeof value === 'object')
  .map(([key, value]) => ({
    key,
    question: typeof value.question === 'string' && value.question.trim() ? value.question : `Pytanie ${key.slice(1)}`,
    answerPath: `health.${key}.answer`,
    detailPath: `health.${key}.detail`,
  })))
const additionalInfoPath = computed(() => {
  const value = draft.value.health?.additionalInfo
  if (value && typeof value === 'object' && !Array.isArray(value)) return 'health.additionalInfo.value'
  if (typeof value === 'string') return 'health.additionalInfo'
  return null
})

function editablePaths() {
  const paths = [
    'person.firstName', 'person.lastName', 'person.contact.email', 'person.contact.phone',
    'address.street', 'address.houseNumber', 'address.postalCode', 'address.city',
  ]
  if (isStaff.value) paths.push('person.motherName', 'person.fatherName', 'person.birthSurname')
  if (isMinor.value) {
    paths.push('guardian.firstName', 'guardian.lastName', 'guardian.relation', 'guardian.names',
      'guardian.contact.email', 'guardian.contact.phone')
    if (!isStaff.value) paths.push('address.sameAddress', 'address.guardianAddress.street',
      'address.guardianAddress.houseNumber', 'address.guardianAddress.postalCode', 'address.guardianAddress.city')
  } else {
    paths.push('ice.firstName', 'ice.lastName', 'ice.relation', 'ice.relationOther', 'ice.phone')
  }
  for (const question of questionEntries.value) paths.push(question.answerPath, question.detailPath)
  if (additionalInfoPath.value) paths.push(additionalInfoPath.value)
  return paths
}

function read(path) {
  return path.split('.').reduce((value, key) => value == null ? undefined : value[key], draft.value)
}

function write(path, value) {
  if (path === 'address.sameAddress' && value === true) {
    draft.value.address ||= {}
    if (original.address?.guardianAddress && typeof original.address.guardianAddress === 'object') {
      draft.value.address.guardianAddress = structuredClone(original.address.guardianAddress)
    } else {
      delete draft.value.address.guardianAddress
    }
  }
  const parts = path.split('.')
  let target = draft.value
  for (const part of parts.slice(0, -1)) {
    if (!target[part] || typeof target[part] !== 'object') target[part] = {}
    target = target[part]
  }
  target[parts.at(-1)] = value
  errorMessage.value = ''
  emit('dirty-change', isDirty.value)
}

function sameValue(left, right) {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null)
}

const changes = computed(() => Object.fromEntries(editablePaths()
  .filter(path => !sameValue(readFrom(original, path), read(path)))
  .map(path => [path, read(path) ?? null])))
const isDirty = computed(() => Object.keys(changes.value).length > 0)

function readFrom(root, path) {
  return path.split('.').reduce((value, key) => value == null ? undefined : value[key], root)
}

const requiredPaths = computed(() => [
  'person.firstName', 'person.lastName', 'person.contact.email', 'person.contact.phone',
  'address.street', 'address.houseNumber', 'address.postalCode', 'address.city',
  ...(isStaff.value ? ['person.motherName', 'person.fatherName', 'person.birthSurname'] : []),
  ...(isMinor.value ? ['guardian.firstName', 'guardian.lastName', 'guardian.relation', 'guardian.names', 'guardian.contact.email', 'guardian.contact.phone'] : ['ice.firstName', 'ice.lastName', 'ice.relation', 'ice.phone']),
])

const fieldErrors = ref({})
function validate() {
  const errors = {}
  for (const path of requiredPaths.value) {
    const value = read(path)
    if (typeof value !== 'string' || !value.trim()) errors[path] = 'To pole jest wymagane.'
  }
  for (const path of ['person.contact.email', ...(isMinor.value ? ['guardian.contact.email'] : [])]) {
    const value = read(path)
    if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) errors[path] = 'Podaj prawidłowy adres e-mail.'
  }
  for (const path of ['person.contact.phone', ...(isMinor.value ? ['guardian.contact.phone'] : ['ice.phone'])]) {
    const value = read(path)
    if (value && !/^\+[1-9][0-9]{9,14}$/.test(value)) errors[path] = 'Podaj numer z prefiksem kraju, np. +48123456789.'
  }
  if (!/^\d{2}-\d{3}$/.test(read('address.postalCode') || '')) errors['address.postalCode'] = 'Użyj formatu XX-XXX.'
  if (isMinor.value && !isStaff.value && read('address.sameAddress') === false) {
    for (const part of ['street', 'houseNumber', 'postalCode', 'city']) {
      const path = `address.guardianAddress.${part}`
      if (typeof read(path) !== 'string' || !read(path).trim()) errors[path] = 'Uzupełnij adres opiekuna.'
    }
    if (!/^\d{2}-\d{3}$/.test(read('address.guardianAddress.postalCode') || '')) errors['address.guardianAddress.postalCode'] = 'Użyj formatu XX-XXX.'
  }
  if (!isMinor.value && read('ice.relation') === 'inna' && !(read('ice.relationOther') || '').trim()) {
    errors['ice.relationOther'] = 'Doprecyzuj relację kontaktu awaryjnego.'
  }
  for (const question of questionEntries.value) {
    if (!['tak', 'nie'].includes(read(question.answerPath))) errors[question.answerPath] = 'Wybierz odpowiedź.'
    if (read(question.answerPath) === 'tak' && !(read(question.detailPath) || '').trim()) errors[question.detailPath] = 'Uzupełnij szczegóły odpowiedzi.'
  }
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

function labelFor(path) {
  return ({
    'person.firstName': 'Imię', 'person.lastName': 'Nazwisko',
    'person.contact.email': 'E-mail', 'person.contact.phone': 'Telefon',
    'person.motherName': 'Imię matki', 'person.fatherName': 'Imię ojca', 'person.birthSurname': 'Nazwisko rodowe',
    'address.street': 'Ulica', 'address.houseNumber': 'Nr domu', 'address.postalCode': 'Kod pocztowy', 'address.city': 'Miasto',
    'guardian.firstName': 'Imię', 'guardian.lastName': 'Nazwisko', 'guardian.relation': 'Relacja',
    'guardian.names': 'Imiona i nazwiska rodziców/opiekunów', 'guardian.contact.email': 'E-mail', 'guardian.contact.phone': 'Telefon',
    'ice.firstName': 'Imię', 'ice.lastName': 'Nazwisko', 'ice.relation': 'Relacja', 'ice.relationOther': 'Doprecyzowanie relacji', 'ice.phone': 'Telefon',
    'health.additionalInfo.value': 'Dodatkowe informacje',
    'address.guardianAddress.street': 'Ulica', 'address.guardianAddress.houseNumber': 'Nr domu',
    'address.guardianAddress.postalCode': 'Kod pocztowy', 'address.guardianAddress.city': 'Miasto',
  })[path] || path
}

const inputClass = 'mt-1 min-h-10 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-700 focus-visible:ring-offset-1'
const compactSelectClass = 'mt-1 min-h-10 w-32 rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-700 focus-visible:ring-offset-1'

async function save() {
  errorMessage.value = ''
  staleVersion.value = false
  if (!validate()) {
    errorMessage.value = 'Sprawdź pola oznaczone poniżej.'
    return
  }
  if (!isDirty.value) {
    emit('cancel')
    return
  }
  saving.value = true
  try {
    const updated = await updateRegistrationData(auth.token, props.detail.registrationCode, {
      expectedVersion: props.detail.version,
      changes: changes.value,
    })
    emit('saved', updated)
  } catch (error) {
    errorMessage.value = error.message || 'Nie udało się zapisać zmian.'
    staleVersion.value = error.status === 409
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form class="space-y-5" @submit.prevent="save">
    <div class="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-950">
      PESEL, turnus, status, zgody oraz rola i uprawnienia kadry pozostają bez zmian. Zapisane poprawki nie wysyłają e-maila.
    </div>

    <section class="space-y-3">
      <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Dane osobowe i kontakt</h3>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label v-for="path in ['person.firstName', 'person.lastName', 'person.contact.email', 'person.contact.phone', ...(isStaff ? ['person.motherName', 'person.fatherName', 'person.birthSurname'] : [])]" :key="path" class="block text-sm font-medium text-slate-700">
          {{ labelFor(path) }}<span v-if="requiredPaths.includes(path)" class="text-red-600"> *</span>
          <input :value="read(path) ?? ''" :type="path.endsWith('.email') ? 'email' : path.endsWith('.phone') ? 'tel' : 'text'" :maxlength="path.endsWith('.phone') ? 16 : path === 'person.firstName' || path === 'person.motherName' || path === 'person.fatherName' ? 100 : path === 'person.lastName' ? 150 : 254" :class="inputClass" @input="write(path, $event.target.value)" />
          <span v-if="fieldErrors[path]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[path] }}</span>
        </label>
      </div>
    </section>

    <section class="space-y-3">
      <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Adres</h3>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label v-for="path in ['address.street', 'address.houseNumber', 'address.postalCode', 'address.city']" :key="path" class="block text-sm font-medium text-slate-700">
          {{ labelFor(path) }} *
          <input :value="read(path) ?? ''" :class="inputClass" @input="write(path, $event.target.value)" />
          <span v-if="fieldErrors[path]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[path] }}</span>
        </label>
      </div>
      <template v-if="isMinor && !isStaff">
        <label class="flex min-h-10 cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700">
          <input type="checkbox" :checked="read('address.sameAddress') !== false" class="h-4 w-4 accent-blue-800" @change="write('address.sameAddress', $event.target.checked)" />
          Adres opiekuna taki sam jak uczestnika
        </label>
        <div v-if="read('address.sameAddress') === false" class="grid grid-cols-1 gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 sm:grid-cols-2">
          <label v-for="path in ['address.guardianAddress.street', 'address.guardianAddress.houseNumber', 'address.guardianAddress.postalCode', 'address.guardianAddress.city']" :key="path" class="block text-sm font-medium text-slate-700">
            {{ labelFor(path) }} *
            <input :value="read(path) ?? ''" :class="inputClass" @input="write(path, $event.target.value)" />
            <span v-if="fieldErrors[path]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[path] }}</span>
          </label>
        </div>
      </template>
    </section>

    <section v-if="isMinor" class="space-y-3">
      <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Dane opiekuna</h3>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label v-for="path in ['guardian.firstName', 'guardian.lastName', 'guardian.relation', 'guardian.names', 'guardian.contact.email', 'guardian.contact.phone']" :key="path" class="block text-sm font-medium text-slate-700">
          {{ labelFor(path) }}<span v-if="requiredPaths.includes(path)" class="text-red-600"> *</span>
          <select v-if="path === 'guardian.relation'" :value="read(path) ?? ''" :class="inputClass" @change="write(path, $event.target.value)">
            <option value="matka">Matka</option><option value="ojciec">Ojciec</option><option value="opiekun_prawny">Opiekun prawny</option>
          </select>
          <input v-else :value="read(path) ?? ''" :type="path.endsWith('.email') ? 'email' : 'text'" :class="inputClass" @input="write(path, $event.target.value)" />
          <span v-if="fieldErrors[path]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[path] }}</span>
        </label>
      </div>
    </section>

    <section v-else class="space-y-3">
      <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Kontakt awaryjny</h3>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label v-for="path in ['ice.firstName', 'ice.lastName', 'ice.relation', 'ice.relationOther', 'ice.phone']" :key="path" v-show="path !== 'ice.relationOther' || read('ice.relation') === 'inna'" class="block text-sm font-medium text-slate-700">
          {{ labelFor(path) }}<span v-if="requiredPaths.includes(path)" class="text-red-600"> *</span>
          <select v-if="path === 'ice.relation'" :value="read(path) ?? ''" :class="inputClass" @change="write(path, $event.target.value)">
            <option value="matka">Matka</option><option value="ojciec">Ojciec</option><option value="opiekun_prawny">Opiekun prawny</option><option value="inna">Inna</option>
          </select>
          <input v-else :value="read(path) ?? ''" :class="inputClass" @input="write(path, $event.target.value)" />
          <span v-if="fieldErrors[path]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[path] }}</span>
        </label>
      </div>
    </section>

    <section class="space-y-3">
      <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Kwestionariusz</h3>
      <article v-for="question in questionEntries" :key="question.key" class="space-y-2 rounded-lg border border-slate-200 p-3">
        <p class="text-sm font-medium text-slate-800">{{ question.question }}</p>
        <label class="block text-sm font-medium text-slate-700">Odpowiedź
          <select :value="read(question.answerPath) ?? ''" :class="compactSelectClass" @change="write(question.answerPath, $event.target.value)">
            <option value="">Wybierz odpowiedź</option><option value="tak">Tak</option><option value="nie">Nie</option>
          </select>
          <span v-if="fieldErrors[question.answerPath]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[question.answerPath] }}</span>
        </label>
        <label v-if="read(question.answerPath) === 'tak'" class="block text-sm font-medium text-slate-700">Szczegóły
          <textarea :value="read(question.detailPath) ?? ''" rows="3" maxlength="4000" :class="inputClass" @input="write(question.detailPath, $event.target.value)" />
          <span v-if="fieldErrors[question.detailPath]" class="mt-1 block text-xs font-normal text-red-700">{{ fieldErrors[question.detailPath] }}</span>
        </label>
      </article>
      <label v-if="additionalInfoPath" class="block text-sm font-medium text-slate-700">Dodatkowe informacje
        <textarea :value="read(additionalInfoPath) ?? ''" rows="3" maxlength="4000" :class="inputClass" @input="write(additionalInfoPath, $event.target.value)" />
      </label>
    </section>

    <p v-if="errorMessage" role="alert" class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{{ errorMessage }}</p>
    <p v-if="reloadError" role="alert" class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{{ reloadError }}</p>
    <div class="flex flex-col-reverse gap-2 border-t border-slate-200 pt-4 sm:flex-row sm:justify-between">
      <button type="button" class="min-h-10 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" :disabled="saving" @click="emit('cancel')">Anuluj</button>
      <div class="flex flex-col-reverse gap-2 sm:flex-row">
        <button v-if="staleVersion" type="button" class="min-h-10 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50" @click="emit('reload')">Wczytaj aktualne dane</button>
        <button type="submit" class="min-h-10 rounded-lg bg-blue-900 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-700 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60" :disabled="saving || !isDirty">
          {{ saving ? 'Zapisywanie…' : 'Zapisz zmiany' }}
        </button>
      </div>
    </div>
  </form>
</template>
