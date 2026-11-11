<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { createManualStaffRegistration } from '../api/registrationApi.js'
import { fetchStaffCatalogue } from '../api/turnusApi.js'

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
  dataProcessing: false, imageUsage: false, regulations: false, truthDeclaration: false,
  _lastParentNames: '',
})
const healthQuestions = [
  'Czy osoba posiada choroby przewlekłe, o których organizator powinien wiedzieć?',
  'Czy występują alergie lub nietolerancje pokarmowe, które należy uwzględnić przy przygotowywaniu posiłków?',
]

const selectedTurnus = computed(() => props.turnuses.find(item => item.turnusCode === form.turnusCode) || null)
const peselInfo = computed(() => parsePesel(form.pesel))
const eligible = option => (peselInfo.value.isAdult ? !option.minorOnly : !option.adultOnly)
const availableRoles = computed(() => (catalogue.value?.roles || []).filter(role =>
  eligible(role) || role.subroles.some(eligible)))
const selectedRole = computed(() => availableRoles.value.find(role => role.value === form.role) || null)
const availableSubroles = computed(() => (selectedRole.value?.subroles || []).filter(eligible))
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

watch(() => [form.guardianFirstName, form.guardianLastName], ([first, last]) => {
  const names = [first, last].filter(Boolean).join(' ')
  if (!form.parentNames || form.parentNames === form._lastParentNames) form.parentNames = names
  form._lastParentNames = names
})

function parsePesel(value) {
  if (!/^\d{11}$/.test(value)) return { isAdult: false, gender: null, valid: false }
  const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3, 1]
  const valid = value.split('').reduce((sum, digit, index) => sum + Number(digit) * weights[index], 0) % 10 === 0
  const year = Number(value.slice(0, 2)), day = Number(value.slice(4, 6))
  let month = Number(value.slice(2, 4)), century
  if (month >= 1 && month <= 12) century = 1900
  else if (month >= 21 && month <= 32) { month -= 20; century = 2000 }
  else if (month >= 41 && month <= 52) { month -= 40; century = 2100 }
  else if (month >= 61 && month <= 72) { month -= 60; century = 2200 }
  else if (month >= 81 && month <= 92) { month -= 80; century = 1800 }
  else return { isAdult: false, gender: null, valid: false }
  const birth = new Date(century + year, month - 1, day)
  if (birth.getFullYear() !== century + year || birth.getMonth() !== month - 1 || birth.getDate() !== day)
    return { isAdult: false, gender: null, valid: false }
  let age = new Date().getFullYear() - birth.getFullYear()
  const current = new Date()
  if (current.getMonth() < birth.getMonth() || (current.getMonth() === birth.getMonth() && current.getDate() < birth.getDate())) age--
  return { isAdult: age >= 18, gender: Number(value[9]) % 2 === 0 ? 'female' : 'male', valid }
}

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
    consents: { dataProcessing: form.dataProcessing, imageUsage: form.imageUsage,
      regulations: form.regulations, truthDeclaration: form.truthDeclaration },
  }
}

async function submit() {
  if (submitting.value) return
  clearError()
  if (!peselInfo.value.valid) { error.value = 'Podaj prawidłowy numer PESEL.'; return }
  if (!form.role || (selectedRole.value?.subroleRequired && !form.subrole)) { error.value = 'Wybierz prawidłową rolę i funkcję kadry.'; return }
  if (selectedCertificates.value.some(item => item.hasDetails && form.certificates[item.id] && !form.certificateDetails[item.id]?.trim())) {
    error.value = 'Uzupełnij opis wybranego uprawnienia.'; return
  }
  if (!form.dataProcessing || !form.regulations || !form.truthDeclaration) {
    error.value = 'Potwierdź uzyskanie wymaganych zgód i oświadczenia.'; return
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
  <div class="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-950/50 p-3 sm:p-6">
    <section role="dialog" aria-modal="true" aria-labelledby="manual-staff-title" class="my-auto w-full max-w-4xl rounded-2xl bg-slate-50 shadow-2xl">
      <header class="sticky top-0 z-10 flex items-start justify-between gap-4 rounded-t-2xl border-b border-slate-200 bg-white px-5 py-4 sm:px-7">
        <div><h2 id="manual-staff-title" class="text-xl font-semibold text-slate-900">Dodaj kadrę</h2>
          <p class="mt-1 text-sm text-slate-600">Zgłoszenie zostanie zapisane jako zaakceptowane. E-mail osoby jest opcjonalny; zapis nie wysyła automatycznej wiadomości.</p></div>
        <button type="button" aria-label="Zamknij" class="rounded px-2 py-1 text-2xl leading-none text-slate-500 hover:bg-slate-100" @click="requestClose">×</button>
      </header>

      <form class="space-y-4 p-4 sm:p-7" @input="dirty = true" @change="dirty = true" @submit.prevent="submit">
        <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-4 font-semibold text-slate-900">Turnus i dane osobowe</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field sm:col-span-2">Turnus *
              <select v-model="form.turnusCode" required><option value="">Wybierz turnus</option>
                <option v-for="turnus in turnuses" :key="turnus.turnusCode" :value="turnus.turnusCode">{{ turnus.turnusName }} · {{ turnus.turnusCode }}{{ turnus.active ? '' : ' · nieaktywny' }}{{ turnus.staffRegistrationOpen ? '' : ' · zapisy zamknięte' }}</option>
              </select>
              <span v-if="!turnuses.length" class="font-normal text-red-700">Lista turnusów jest niedostępna.</span>
            </label>
            <label class="field">Imię (imiona) *<input v-model.trim="form.firstName" maxlength="60" required /></label>
            <label class="field">Nazwisko *<input v-model.trim="form.lastName" maxlength="80" required />
              <span class="mt-1 flex items-start gap-2 text-xs font-normal text-slate-600"><input v-model="form.birthSurnameSame" type="checkbox" class="mt-0.5" /> Nazwisko rodowe takie samo jak nazwisko</span>
            </label>
            <label class="field">Imię matki *<input v-model.trim="form.motherName" maxlength="60" required /></label>
            <label class="field">Imię ojca *<input v-model.trim="form.fatherName" maxlength="60" required /></label>
            <label v-if="!form.birthSurnameSame" class="field">Nazwisko rodowe *<input v-model.trim="form.birthSurname" maxlength="80" required /></label>
            <label class="field">PESEL *<input v-model.trim="form.pesel" inputmode="numeric" maxlength="11" pattern="[0-9]{11}" required />
              <span v-if="form.pesel.length === 11" class="font-normal text-slate-500">{{ peselInfo.gender === 'female' ? 'Kobieta' : 'Mężczyzna' }} · {{ peselInfo.isAdult ? 'osoba pełnoletnia' : 'osoba niepełnoletnia' }}</span>
            </label>
            <label class="field">Telefon *<input v-model.trim="form.phone" type="tel" maxlength="16" placeholder="+48123456789" required /></label>
            <label class="field">E-mail osoby (opcjonalnie)<input v-model.trim="form.email" type="email" maxlength="254" /></label>
          </div>
        </section>

        <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-4 font-semibold text-slate-900">Adres</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">Ulica *<input v-model.trim="form.street" maxlength="100" required /></label>
            <label class="field">Numer domu / mieszkania *<input v-model.trim="form.houseNumber" maxlength="20" required /></label>
            <label class="field">Kod pocztowy *<input v-model.trim="form.postalCode" maxlength="6" placeholder="00-000" pattern="[0-9]{2}-[0-9]{3}" required /></label>
            <label class="field">Miejscowość *<input v-model.trim="form.city" maxlength="80" required /></label>
          </div>
        </section>

        <section v-if="form.pesel.length === 11 && !peselInfo.isAdult" class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-4 font-semibold text-slate-900">Dane opiekuna</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">Imię opiekuna *<input v-model.trim="form.guardianFirstName" maxlength="60" required /></label>
            <label class="field">Nazwisko opiekuna *<input v-model.trim="form.guardianLastName" maxlength="80" required /></label>
            <label class="field">Relacja *<select v-model="form.guardianRelation" required><option value="">Wybierz</option><option value="matka">Matka</option><option value="ojciec">Ojciec</option><option value="opiekun_prawny">Opiekun prawny</option></select></label>
            <label class="field">Telefon opiekuna *<input v-model.trim="form.guardianPhone" type="tel" maxlength="16" placeholder="+48123456789" required /></label>
            <label class="field">E-mail opiekuna *<input v-model.trim="form.guardianEmail" type="email" maxlength="254" required /></label>
            <label class="field">Imiona i nazwiska rodziców / opiekunów *<input v-model.trim="form.parentNames" maxlength="200" required /></label>
          </div>
        </section>

        <section v-if="form.pesel.length === 11 && peselInfo.isAdult" class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-4 font-semibold text-slate-900">Kontakt awaryjny (ICE)</h3>
          <div class="grid gap-4 sm:grid-cols-2">
            <label class="field">Imię *<input v-model.trim="form.iceFirstName" maxlength="60" required /></label>
            <label class="field">Nazwisko *<input v-model.trim="form.iceLastName" maxlength="80" required /></label>
            <label class="field">Relacja *<select v-model="form.iceRelation" required><option value="">Wybierz</option><option value="matka">Matka</option><option value="ojciec">Ojciec</option><option value="opiekun_prawny">Opiekun prawny</option><option value="inna">Inna</option></select></label>
            <label class="field">Telefon *<input v-model.trim="form.icePhone" type="tel" maxlength="16" placeholder="+48123456789" required /></label>
            <label v-if="form.iceRelation === 'inna'" class="field sm:col-span-2">Doprecyzowanie relacji *<input v-model.trim="form.iceRelationOther" maxlength="80" required /></label>
          </div>
        </section>

        <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-1 font-semibold text-slate-900">Rola i uprawnienia</h3>
          <p v-if="selectedTurnus" class="mb-4 text-sm text-slate-600">Role zgodne z sezonem: {{ selectedTurnus.seasonType === 'WINTER' ? 'Zima' : 'Lato' }}.</p>
          <p v-if="catalogueLoading" role="status" class="text-sm text-slate-500">Wczytywanie ról…</p>
          <p v-else-if="catalogueError" role="alert" class="text-sm text-red-700">{{ catalogueError }}</p>
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

        <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-4 font-semibold text-slate-900">Kwestionariusz</h3>
          <div class="space-y-4">
            <div v-for="(question, index) in healthQuestions" :key="index" class="grid gap-3 sm:grid-cols-[1fr_10rem]">
              <label class="field">{{ question }} *
                <select v-model="form.health[`q${index + 1}`].answer" required><option value="">Wybierz odpowiedź</option><option value="tak">Tak</option><option value="nie">Nie</option></select>
                <textarea v-if="form.health[`q${index + 1}`].answer === 'tak'" v-model.trim="form.health[`q${index + 1}`].detail" maxlength="500" rows="2" class="mt-2" placeholder="Jakie? Proszę krótko opisać" required />
              </label>
            </div>
            <label class="field">Dodatkowe informacje istotne pod kątem organizacji turnusu (opcjonalnie)<textarea v-model.trim="form.health.additionalInfo" maxlength="500" rows="3" /></label>
          </div>
        </section>

        <section class="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
          <h3 class="mb-3 font-semibold text-slate-900">Zgody i oświadczenia</h3>
          <p class="mb-3 text-sm text-slate-600">Zaznacz wymagane pozycje po potwierdzeniu, że osoba udzieliła zgód lub złożyła oświadczenie. Bez tego zgłoszenie nie zostanie zapisane.</p>
          <div class="space-y-3 text-sm text-slate-700">
            <label class="flex gap-2"><input v-model="form.dataProcessing" type="checkbox" required /> Potwierdzam uzyskanie zgody na przetwarzanie danych osobowych *</label>
            <label class="flex gap-2"><input v-model="form.regulations" type="checkbox" required /> Potwierdzam akceptację regulaminu przez osobę zgłaszaną *</label>
            <label class="flex gap-2"><input v-model="form.truthDeclaration" type="checkbox" required /> Potwierdzam złożenie oświadczenia o prawdziwości danych *</label>
            <label class="flex gap-2"><input v-model="form.imageUsage" type="checkbox" /> Zgoda na wykorzystanie wizerunku (opcjonalnie)</label>
          </div>
        </section>

        <p v-if="error" role="alert" class="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{{ error }}</p>
        <footer class="flex flex-col-reverse gap-2 border-t border-slate-200 pt-4 sm:flex-row sm:justify-end">
          <button type="button" class="rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50" :disabled="submitting" @click="requestClose">Anuluj</button>
          <button type="submit" class="rounded-lg bg-slate-800 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-900 disabled:cursor-wait disabled:opacity-60" :disabled="submitting || catalogueLoading || !!catalogueError || !turnuses.length">{{ submitting ? 'Zapisywanie…' : 'Dodaj zaakceptowaną kadrę' }}</button>
        </footer>
      </form>
    </section>
  </div>
</template>

<style scoped>
.field { display: flex; min-width: 0; flex-direction: column; gap: .35rem; color: #334155; font-size: .875rem; font-weight: 500; }
.field input, .field select, .field textarea { width: 100%; min-width: 0; border: 1px solid #cbd5e1; border-radius: .5rem; background: #fff; padding: .6rem .75rem; color: #0f172a; font-weight: 400; outline: none; }
.field input:focus, .field select:focus, .field textarea:focus { border-color: #475569; box-shadow: 0 0 0 2px rgb(71 85 105 / 20%); }
</style>
