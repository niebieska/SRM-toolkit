<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { createManualStaffRegistration } from '../api/registrationApi.js'
import { fetchStaffCatalogue } from '../api/turnusApi.js'
import {
  filterStaffRolesByAge,
  filterStaffSubrolesByAge,
  isAdultOnDate,
  parseStaffPesel,
} from '../utils/staffRegistrationAge.js'

const props = defineProps({ token: { type: String, required: true }, turnuses: { type: Array, default: () => [] } })
const emit = defineEmits(['close', 'created'])
const submitting = ref(false)
const dirty = ref(false)
const error = ref('')
const catalogue = ref(null)
const catalogueLoading = ref(false)
const catalogueError = ref('')
let catalogueRequest = 0

const form = reactive({
  turnusCode: '', firstName: '', lastName: '', pesel: '', email: '', phone: '', motherName: '', fatherName: '', birthSurname: '', birthSurnameSame: true,
  street: '', houseNumber: '', postalCode: '', city: '', guardianFirstName: '', guardianLastName: '', guardianRelation: '',
  guardianEmail: '', guardianPhone: '', parentNames: '', iceFirstName: '', iceLastName: '', iceRelation: '',
  iceRelationOther: '', icePhone: '', role: '', subrole: '', certificates: {}, certificateDetails: {},
  health: { q1: { answer: '', detail: '' }, q2: { answer: '', detail: '' }, additionalInfo: '' },
  requiredConfirmations: false, imageUsage: false,
  _lastParentNames: '',
})
const healthQuestions = [
  'Czy osoba posiada choroby przewlekłe, o których organizator powinien wiedzieć?',
  'Czy występują alergie lub nietolerancje pokarmowe, które należy uwzględnić przy przygotowywaniu posiłków?',
]

const selectedTurnus = computed(() => props.turnuses.find(item => item.turnusCode === form.turnusCode) || null)
const availableTurnuses = computed(() => props.turnuses.filter(turnus => turnus.active === true))
const peselInfo = computed(() => parseStaffPesel(form.pesel))
const roleEligibilityReady = computed(() => peselInfo.value.valid && !!selectedTurnus.value?.startDate)
const adultAtTurnusStart = computed(() => roleEligibilityReady.value
  && isAdultOnDate(form.pesel, selectedTurnus.value.startDate))
const availableRoles = computed(() => roleEligibilityReady.value
  ? filterStaffRolesByAge(catalogue.value?.roles, adultAtTurnusStart.value)
  : [])
const selectedRole = computed(() => availableRoles.value.find(role => role.value === form.role) || null)
const availableSubroles = computed(() => filterStaffSubrolesByAge(selectedRole.value?.subroles, adultAtTurnusStart.value))
const selectedCertificates = computed(() => {
  const options = catalogue.value?.certificatesByRole || {}
  const key = form.subrole && options[`${form.role}_${form.subrole}`] ? `${form.role}_${form.subrole}`
    : form.subrole && options[form.subrole] ? form.subrole : form.role
  return options[key] || []
})

watch(() => selectedTurnus.value?.seasonType, async season => {
  const requestId = ++catalogueRequest
  catalogue.value = null
  catalogueError.value = ''
  form.role = ''
  form.subrole = ''
  form.certificates = {}
  form.certificateDetails = {}
  if (!season) return
  catalogueLoading.value = true
  try {
    const result = await fetchStaffCatalogue(props.token, season)
    if (requestId === catalogueRequest) catalogue.value = result
  } catch (exception) {
    if (requestId === catalogueRequest) catalogueError.value = exception.message
  } finally {
    if (requestId === catalogueRequest) catalogueLoading.value = false
  }
}, { immediate: true })

watch(() => [form.role, form.subrole], () => {
  form.certificates = {}
  form.certificateDetails = {}
})

watch(availableRoles, roles => {
  if (form.role && !roles.some(role => role.value === form.role)) {
    form.role = ''
    form.subrole = ''
  }
})

watch(availableSubroles, subroles => {
  if (form.subrole && !subroles.some(subrole => subrole.value === form.subrole)) form.subrole = ''
})

watch(() => [form.guardianFirstName, form.guardianLastName], ([first, last]) => {
  const names = [first, last].filter(Boolean).join(' ')
  if (!form.parentNames || form.parentNames === form._lastParentNames) form.parentNames = names
  form._lastParentNames = names
})

function clearError() { error.value = '' }
function requestClose() {
  if (submitting.value) return
  if (dirty.value && !window.confirm('Opuścić formularz bez zapisywania wprowadzonych danych?')) return
  emit('close')
}
function selectRole(value) { form.role = value; form.subrole = '' }
function toggleCertificate(id, checked) {
  const updated = { ...form.certificates }
  if (checked) updated[id] = true
  else delete updated[id]
  form.certificates = updated
  if (!checked) {
    const details = { ...form.certificateDetails }
    delete details[id]
    form.certificateDetails = details
  }
}
function buildPayload() {
  const adult = peselInfo.value.isAdult
  return {
    turnusCode: form.turnusCode,
    person: {
      firstName: form.firstName.trim(), lastName: form.lastName.trim(), pesel: form.pesel,
      gender: peselInfo.value.gender, isAdult: adult,
      motherName: form.motherName.trim(), fatherName: form.fatherName.trim(), birthSurname: form.birthSurnameSame ? form.lastName.trim() : form.birthSurname.trim(),
      contact: { email: form.email.trim(), phone: form.phone.trim() },
    },
    address: { street: form.street.trim(), houseNumber: form.houseNumber.trim(), postalCode: form.postalCode.trim(), city: form.city.trim() },
    guardian: adult ? null : {
      firstName: form.guardianFirstName.trim(), lastName: form.guardianLastName.trim(), relation: form.guardianRelation,
      names: form.parentNames.trim(), contact: { email: form.guardianEmail.trim(), phone: form.guardianPhone.trim() },
    },
    ice: adult ? { firstName: form.iceFirstName.trim(), lastName: form.iceLastName.trim(), relation: form.iceRelation,
      relationOther: form.iceRelation === 'inna' ? form.iceRelationOther.trim() : null, phone: form.icePhone.trim() } : null,
    role: form.role, subrole: form.subrole || null,
    certificates: form.certificates,
    certificateDetails: Object.fromEntries(Object.entries(form.certificateDetails).filter(([key]) => form.certificates[key])),
    health: {
      q1: { question: 'Czy posiadasz choroby przewlekłe, o których organizator powinien wiedzieć?', ...form.health.q1 },
      q2: { question: 'Czy występują u Ciebie alergie lub nietolerancje pokarmowe, które powinny zostać uwzględnione przy przygotowywaniu posiłków?', ...form.health.q2 },
      additionalInfo: { question: 'Dodatkowe informacje istotne pod kątem organizacji turnusu', value: form.health.additionalInfo.trim() },
    },
    consents: {
      dataProcessing: form.requiredConfirmations,
      imageUsage: form.imageUsage,
      regulations: form.requiredConfirmations,
      truthDeclaration: form.requiredConfirmations,
    },
  }
}

async function submit() {
  if (submitting.value) return
  clearError()
  if (!peselInfo.value.valid) { error.value = 'Podaj prawidłowy numer PESEL.'; return }
  if (!selectedRole.value || (selectedRole.value.subroleRequired && !form.subrole)
      || (form.subrole && !availableSubroles.value.some(subrole => subrole.value === form.subrole))) {
    error.value = 'Wybierz prawidłową rolę i funkcję kadry.'; return
  }
  if (selectedCertificates.value.some(item => item.hasDetails && form.certificates[item.id] && !form.certificateDetails[item.id]?.trim())) {
    error.value = 'Uzupełnij opis wybranego uprawnienia.'; return
  }
  if (!form.requiredConfirmations) {
    error.value = 'Potwierdź uzyskanie wymaganych zgód i oświadczeń.'; return
  }
  if (form.health.q1.answer === 'tak' && !form.health.q1.detail.trim()
      || form.health.q2.answer === 'tak' && !form.health.q2.detail.trim()) {
    error.value = 'Uzupełnij szczegóły przy odpowiedzi „Tak”.'; return
  }
  submitting.value = true
  try {
    const result = await createManualStaffRegistration(props.token, buildPayload())
    emit('created', result.registrationCode)
  } catch (exception) { error.value = exception.message }
  finally { submitting.value = false }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 p-3 sm:p-4">
    <section role="dialog" aria-modal="true" aria-labelledby="manual-staff-title" class="my-4 w-full max-w-3xl space-y-5 rounded-xl bg-white px-4 pb-5 pt-4 shadow-xl sm:my-8 sm:px-6 sm:pb-6 sm:pt-5">
      <header class="flex items-center justify-between gap-4">
        <h2 id="manual-staff-title" class="text-lg font-semibold text-slate-900">Dodaj kadrę</h2>
        <button type="button" aria-label="Zamknij" class="text-xl leading-none text-slate-500 hover:text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-700" @click="requestClose">✕</button>
      </header>

      <form class="space-y-5" @input="dirty = true" @change="dirty = true" @submit.prevent="submit">
        <div class="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-950">
          Zgłoszenie zostanie zapisane jako zaakceptowane. E-mail osoby jest opcjonalny; zapis nie wysyła automatycznej wiadomości.
        </div>

        <section class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Turnus i dane osobowe</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field sm:col-span-2">Turnus *
              <select v-model="form.turnusCode" required><option value="">Wybierz turnus</option>
                <option v-for="turnus in availableTurnuses" :key="turnus.turnusCode" :value="turnus.turnusCode">{{ turnus.turnusName }} · {{ turnus.turnusCode }}{{ turnus.staffRegistrationOpen ? '' : ' · zapisy zamknięte' }}</option>
              </select>
              <span v-if="!availableTurnuses.length" class="font-normal text-slate-600">Brak aktywnych turnusów do wyboru.</span>
            </label>
            <label class="field">Imię (imiona) *<input v-model.trim="form.firstName" maxlength="60" required /></label>
            <label class="field">Nazwisko *<input v-model.trim="form.lastName" maxlength="80" required />
              <span class="mt-1 flex items-start gap-2 text-xs font-normal text-slate-600"><input v-model="form.birthSurnameSame" type="checkbox" class="mt-0.5" /> Nazwisko rodowe takie samo jak nazwisko</span>
            </label>
            <label class="field">Imię matki *<input v-model.trim="form.motherName" maxlength="60" required /></label>
            <label class="field">Imię ojca *<input v-model.trim="form.fatherName" maxlength="60" required /></label>
            <label v-if="!form.birthSurnameSame" class="field">Nazwisko rodowe *<input v-model.trim="form.birthSurname" maxlength="80" required /></label>
            <label class="field">PESEL *<input v-model.trim="form.pesel" inputmode="numeric" maxlength="11" pattern="[0-9]{11}" required />
              <span v-if="peselInfo.valid" class="field-hint">{{ peselInfo.gender === 'female' ? 'Kobieta' : 'Mężczyzna' }} · {{ peselInfo.isAdult ? 'osoba pełnoletnia' : 'osoba niepełnoletnia' }}</span>
              <span v-else-if="form.pesel.length === 11" class="field-error">Nieprawidłowy numer PESEL.</span>
            </label>
            <label class="field">Telefon *<input v-model.trim="form.phone" type="tel" maxlength="16" placeholder="+48123456789" required /></label>
            <label class="field">E-mail osoby (opcjonalnie)<input v-model.trim="form.email" type="email" maxlength="254" /></label>
          </div>
        </section>

        <section class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Adres</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">Ulica *<input v-model.trim="form.street" maxlength="100" required /></label>
            <label class="field">Numer domu / mieszkania *<input v-model.trim="form.houseNumber" maxlength="20" required /></label>
            <label class="field">Kod pocztowy *<input v-model.trim="form.postalCode" maxlength="6" placeholder="00-000" pattern="[0-9]{2}-[0-9]{3}" required /></label>
            <label class="field">Miejscowość *<input v-model.trim="form.city" maxlength="80" required /></label>
          </div>
        </section>

        <section v-if="peselInfo.valid && !peselInfo.isAdult" class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Dane opiekuna</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">Imię opiekuna *<input v-model.trim="form.guardianFirstName" maxlength="60" required /></label>
            <label class="field">Nazwisko opiekuna *<input v-model.trim="form.guardianLastName" maxlength="80" required /></label>
            <label class="field">Relacja *<select v-model="form.guardianRelation" required><option value="">Wybierz</option><option value="matka">Matka</option><option value="ojciec">Ojciec</option><option value="opiekun_prawny">Opiekun prawny</option></select></label>
            <label class="field">Telefon opiekuna *<input v-model.trim="form.guardianPhone" type="tel" maxlength="16" placeholder="+48123456789" required /></label>
            <label class="field">E-mail opiekuna *<input v-model.trim="form.guardianEmail" type="email" maxlength="254" required /></label>
            <label class="field">Imiona i nazwiska rodziców / opiekunów *<input v-model.trim="form.parentNames" maxlength="200" required /></label>
          </div>
        </section>

        <section v-if="peselInfo.valid && peselInfo.isAdult" class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Kontakt awaryjny (ICE)</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">Imię *<input v-model.trim="form.iceFirstName" maxlength="60" required /></label>
            <label class="field">Nazwisko *<input v-model.trim="form.iceLastName" maxlength="80" required /></label>
            <label class="field">Relacja *<select v-model="form.iceRelation" required><option value="">Wybierz</option><option value="matka">Matka</option><option value="ojciec">Ojciec</option><option value="opiekun_prawny">Opiekun prawny</option><option value="inna">Inna</option></select></label>
            <label class="field">Telefon *<input v-model.trim="form.icePhone" type="tel" maxlength="16" placeholder="+48123456789" required /></label>
            <label v-if="form.iceRelation === 'inna'" class="field sm:col-span-2">Doprecyzowanie relacji *<input v-model.trim="form.iceRelationOther" maxlength="80" required /></label>
          </div>
        </section>

        <section class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Rola i uprawnienia</h3>
          <p v-if="selectedTurnus" class="mb-4 text-sm text-slate-600">Role zgodne z sezonem: {{ selectedTurnus.seasonType === 'WINTER' ? 'Zima' : 'Lato' }}.</p>
          <p v-if="catalogueLoading" role="status" class="text-sm text-slate-500">Wczytywanie ról…</p>
          <p v-else-if="catalogueError" role="alert" class="text-sm text-red-700">{{ catalogueError }}</p>
          <p v-else-if="!peselInfo.valid" role="status" class="text-sm text-slate-600">Wpisz prawidłowy PESEL, aby dopasować role do wieku w dniu rozpoczęcia turnusu.</p>
          <p v-else-if="!selectedTurnus?.startDate" role="status" class="text-sm text-slate-600">Wybierz turnus, aby dopasować role do wieku w dniu jego rozpoczęcia.</p>
          <p v-else-if="!availableRoles.length" role="status" class="text-sm text-slate-600">Brak ról dostępnych dla wieku osoby w dniu rozpoczęcia turnusu.</p>
          <div v-else class="grid gap-4 sm:grid-cols-2">
            <label class="field">Rola *<select :value="form.role" required @change="selectRole($event.target.value)"><option value="">Wybierz rolę</option><option v-for="role in availableRoles" :key="role.value" :value="role.value">{{ role.label }}</option></select></label>
            <label v-if="selectedRole?.subroles?.length" class="field">Funkcja
              <select v-model="form.subrole" :required="selectedRole.subroleRequired"><option value="">{{ selectedRole.subroleRequired ? 'Wybierz funkcję' : 'Brak funkcji' }}</option><option v-for="item in availableSubroles" :key="item.value" :value="item.value">{{ item.label }}</option></select>
            </label>
            <div v-if="form.role" class="space-y-2 sm:col-span-2">
              <p class="text-sm font-medium text-slate-700">Uprawnienia (opcjonalnie)</p>
              <label v-for="item in selectedCertificates" :key="item.id" class="flex items-start gap-2 text-sm text-slate-700">
                <input type="checkbox" class="mt-1" :checked="!!form.certificates[item.id]" @change="toggleCertificate(item.id, $event.target.checked)" />
                <span class="flex-1">{{ item.label }}
                  <textarea v-if="item.hasDetails && form.certificates[item.id]" v-model.trim="form.certificateDetails[item.id]" maxlength="500" rows="2" class="mt-2 block w-full rounded-lg border border-slate-300 px-3 py-2" placeholder="Opisz uprawnienie" required />
                </span>
              </label>
            </div>
          </div>
        </section>

        <section class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Kwestionariusz</h3>
          <div class="space-y-3">
            <article v-for="(question, index) in healthQuestions" :key="index" class="space-y-2 rounded-lg border border-slate-200 p-3">
              <p class="text-sm font-medium text-slate-800">{{ question }} *</p>
              <label class="field">Odpowiedź
                <select v-model="form.health[`q${index + 1}`].answer" class="question-select" required><option value="">Wybierz odpowiedź</option><option value="tak">Tak</option><option value="nie">Nie</option></select>
              </label>
              <label v-if="form.health[`q${index + 1}`].answer === 'tak'" class="field">Szczegóły
                <textarea v-model.trim="form.health[`q${index + 1}`].detail" maxlength="500" rows="3" placeholder="Jakie? Proszę krótko opisać" required />
              </label>
            </article>
            <label class="field">Dodatkowe informacje istotne pod kątem organizacji turnusu (opcjonalnie)<textarea v-model.trim="form.health.additionalInfo" maxlength="500" rows="3" /></label>
          </div>
        </section>

        <section class="space-y-3">
          <h3 class="border-b border-slate-200 pb-2 text-sm font-semibold text-slate-800">Zgody i oświadczenia</h3>
          <p class="text-sm text-slate-600">Przed zapisaniem zgłoszenia potwierdź, że osoba zgłaszana udzieliła wymaganej zgody na przetwarzanie danych, zaakceptowała regulamin i złożyła oświadczenie o prawdziwości danych.</p>
          <label class="flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 px-3 py-3 text-sm text-slate-700">
            <input v-model="form.requiredConfirmations" type="checkbox" required class="mt-0.5 h-4 w-4 shrink-0 accent-blue-800" />
            <span>Potwierdzam uzyskanie wymaganych zgód i oświadczeń <span class="text-red-600">*</span></span>
          </label>
          <label class="flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 px-3 py-3 text-sm text-slate-700">
            <input v-model="form.imageUsage" type="checkbox" class="mt-0.5 h-4 w-4 shrink-0 accent-blue-800" />
            <span>Osoba zgłaszana udzieliła zgody na wykorzystanie wizerunku <span class="font-normal text-slate-500">(opcjonalnie)</span></span>
          </label>
        </section>

        <p v-if="error" role="alert" class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{{ error }}</p>
        <footer class="flex flex-col-reverse gap-2 border-t border-slate-200 pt-4 sm:flex-row sm:justify-between">
          <button type="button" class="min-h-10 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60" :disabled="submitting" @click="requestClose">Anuluj</button>
          <button type="submit" class="min-h-10 rounded-lg bg-blue-900 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-700 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60" :disabled="submitting || catalogueLoading || !!catalogueError || !availableTurnuses.length">{{ submitting ? 'Zapisywanie…' : 'Dodaj zaakceptowaną kadrę' }}</button>
        </footer>
      </form>
    </section>
  </div>
</template>

<style scoped>
.field { display: block; min-width: 0; color: #334155; font-size: .875rem; font-weight: 500; }
.field input, .field select, .field textarea { display: block; width: 100%; min-width: 0; min-height: 2.5rem; margin-top: .25rem; border: 1px solid #cbd5e1; border-radius: .5rem; padding: .5rem .75rem; color: #0f172a; font-size: .875rem; font-weight: 400; outline: none; }
.field input:focus-visible, .field select:focus-visible, .field textarea:focus-visible { outline: 2px solid #1d4ed8; outline-offset: 1px; }
.field input[type="checkbox"] { display: inline-block; width: 1rem; min-height: 1rem; margin: 0; padding: 0; accent-color: #1e40af; }
.field-hint { display: block; margin-top: .25rem; color: #475569; font-size: .75rem; font-weight: 400; }
.field-error { display: block; margin-top: .25rem; color: #b91c1c; font-size: .75rem; font-weight: 500; }
.question-select { max-width: 8rem; }
</style>
