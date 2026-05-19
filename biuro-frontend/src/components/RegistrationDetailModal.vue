<script setup>
import { onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { fetchRegistrationDetail } from '../api/registrationApi'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({
  code: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['close'])

const authStore = useAuthStore()
const detail = ref(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    detail.value = await fetchRegistrationDetail(authStore.token, props.code)
  } catch {
    errorMessage.value = 'Nie udało się pobrać szczegółów zgłoszenia'
  } finally {
    loading.value = false
  }
})

function yesNo(val) {
  if (val === true) return 'Tak'
  if (val === false) return 'Nie'
  return '–'
}

function healthAnswer(q) {
  if (!q) return '–'
  const answer = q.answer === 'tak' ? 'Tak' : q.answer === 'nie' ? 'Nie' : (q.answer || '–')
  return q.detail ? `${answer} (${q.detail})` : answer
}
</script>

<template>
  <div class="fixed inset-0 bg-black/40 flex items-start justify-center p-4 overflow-y-auto z-50">
    <div class="w-full max-w-2xl bg-white rounded-xl p-6 space-y-5 my-8">

      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold">Szczegóły zgłoszenia</h2>
        <button class="text-slate-500 hover:text-slate-800 text-xl leading-none" @click="emit('close')">✕</button>
      </div>

      <p v-if="loading" class="text-slate-600">Ładowanie...</p>
      <p v-else-if="errorMessage" class="text-red-600">{{ errorMessage }}</p>

      <template v-else-if="detail">

        <!-- Header info -->
        <section>
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Zgłoszenie</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Kod</dt>
            <dd>{{ detail.registrationCode }}</dd>
            <dt class="text-slate-500">Typ</dt>
            <dd>{{ detail.registrationType === 'PARTICIPANT' ? 'Uczestnik' : 'Kadra' }}</dd>
            <dt class="text-slate-500">Turnus</dt>
            <dd>{{ detail.turnusCode }}</dd>
            <dt class="text-slate-500">Status</dt>
            <dd><StatusBadge :status="detail.status" /></dd>
            <template v-if="detail.rejectionReason">
              <dt class="text-slate-500">Powód odrzucenia</dt>
              <dd>{{ detail.rejectionReason }}</dd>
            </template>
            <dt class="text-slate-500">Data zgłoszenia</dt>
            <dd>{{ detail.createdAt }}</dd>
          </dl>
        </section>

        <!-- Person -->
        <section v-if="detail.payload?.person">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Dane osobowe</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Imię i nazwisko</dt>
            <dd>{{ detail.payload.person.firstName }} {{ detail.payload.person.lastName }}</dd>
            <dt class="text-slate-500">Wiek</dt>
            <dd>{{ detail.age != null ? detail.age + ' lat' : '–' }}</dd>
            <dt class="text-slate-500">Małoletni</dt>
            <dd>{{ yesNo(detail.minor) }}</dd>
            <dt class="text-slate-500">Płeć</dt>
            <dd>{{ detail.payload.person.gender === 'male' ? 'Mężczyzna' : detail.payload.person.gender === 'female' ? 'Kobieta' : '–' }}</dd>
            <dt class="text-slate-500">E-mail</dt>
            <dd>{{ detail.payload.person.contact?.email || '–' }}</dd>
            <dt class="text-slate-500">Telefon</dt>
            <dd>{{ detail.payload.person.contact?.phone || '–' }}</dd>
          </dl>
        </section>

        <!-- Address -->
        <section v-if="detail.payload?.address">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Adres</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Ulica i numer</dt>
            <dd>{{ detail.payload.address.street }} {{ detail.payload.address.houseNumber }}</dd>
            <dt class="text-slate-500">Kod pocztowy</dt>
            <dd>{{ detail.payload.address.postalCode }}</dd>
            <dt class="text-slate-500">Miejscowość</dt>
            <dd>{{ detail.payload.address.city }}</dd>
          </dl>
        </section>

        <!-- Guardian (minor) -->
        <section v-if="detail.payload?.guardian">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Opiekun prawny</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Imię i nazwisko</dt>
            <dd>{{ detail.payload.guardian.firstName }} {{ detail.payload.guardian.lastName }}</dd>
            <dt class="text-slate-500">Relacja</dt>
            <dd>{{ detail.payload.guardian.relation || '–' }}</dd>
            <dt class="text-slate-500">E-mail</dt>
            <dd>{{ detail.payload.guardian.contact?.email || '–' }}</dd>
            <dt class="text-slate-500">Telefon</dt>
            <dd>{{ detail.payload.guardian.contact?.phone || '–' }}</dd>
            <dt class="text-slate-500">Rodzice / opiekunowie</dt>
            <dd>{{ detail.payload.guardian.names || '–' }}</dd>
          </dl>
        </section>

        <!-- ICE (adult) -->
        <section v-if="detail.payload?.ice">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Kontakt alarmowy (ICE)</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Imię i nazwisko</dt>
            <dd>{{ detail.payload.ice.firstName }} {{ detail.payload.ice.lastName }}</dd>
            <dt class="text-slate-500">Relacja</dt>
            <dd>{{ detail.payload.ice.relation || '–' }}</dd>
            <dt class="text-slate-500">Telefon</dt>
            <dd>{{ detail.payload.ice.phone || '–' }}</dd>
          </dl>
        </section>

        <!-- Staff role -->
        <section v-if="detail.payload?.role">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Rola</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Funkcja</dt>
            <dd>{{ detail.payload.role }}</dd>
          </dl>
        </section>

        <!-- Health -->
        <section v-if="detail.payload?.health">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Zdrowie</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Choroby przewlekłe</dt>
            <dd>{{ healthAnswer(detail.payload.health.q1) }}</dd>
            <dt class="text-slate-500">Alergie</dt>
            <dd>{{ healthAnswer(detail.payload.health.q2) }}</dd>
            <dt class="text-slate-500">Leki</dt>
            <dd>{{ healthAnswer(detail.payload.health.q3) }}</dd>
            <dt class="text-slate-500">Dieta</dt>
            <dd>{{ healthAnswer(detail.payload.health.q4) }}</dd>
            <dt class="text-slate-500">Inne informacje zdrowotne</dt>
            <dd>{{ healthAnswer(detail.payload.health.q5) }}</dd>
            <dt class="text-slate-500">Deklaracja zdrowotna</dt>
            <dd>{{ yesNo(detail.payload.health.declaration) }}</dd>
          </dl>
        </section>

        <!-- Consents -->
        <section v-if="detail.payload?.consents">
          <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-2">Zgody</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt class="text-slate-500">Przetwarzanie danych</dt>
            <dd>{{ yesNo(detail.payload.consents.dataProcessing) }}</dd>
            <dt class="text-slate-500">Wizerunek</dt>
            <dd>{{ yesNo(detail.payload.consents.imageUsage) }}</dd>
            <dt class="text-slate-500">Regulamin</dt>
            <dd>{{ yesNo(detail.payload.consents.regulations) }}</dd>
          </dl>
        </section>

      </template>

      <div class="flex justify-end pt-2">
        <button class="rounded border border-slate-300 px-4 py-2 hover:bg-slate-50" @click="emit('close')">
          Zamknij
        </button>
      </div>
    </div>
  </div>
</template>
