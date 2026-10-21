<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import DiscardChangesDialog from '../components/DiscardChangesDialog.vue'
import { createDiscardConfirmation } from '../utils/discardConfirmation.js'
import { useAuthStore } from '../stores/auth.js'
import { fetchProfile } from '../api/authApi.js'
import { fetchAccounts, fetchAccountTurnuses, createAccount, updateAccount, resetAccountPassword, fetchAccountAudit } from '../api/accountApi.js'
import { roleLabels, passwordError } from '../utils/accountAccess.js'
import { eventLabels, auditChanges } from '../utils/accountAudit.js'
const auth = useAuthStore(), router = useRouter()
const accounts = ref([]), turnuses = ref([]), loading = ref(false), busy = ref(false)
const error = ref(''), success = ref(''), search = ref(''), selected = ref(null), original = ref('')
const password = ref(''), confirmation = ref(''), resetPassword = ref(''), resetConfirmation = ref('')
const resetOpen = ref(false), audit = ref([]), auditError = ref(''), auditLoading = ref(false)
const form = reactive({ username: '', displayName: '', active: true, roles: [], turnusCodes: [], version: 0 })
const { prompt, confirm, decide } = createDiscardConfirmation()
let historySequence = 0
const filtered = computed(() => accounts.value.filter(a => `${a.username} ${a.displayName}`.toLocaleLowerCase('pl').includes(search.value.toLocaleLowerCase('pl'))))
const dirty = computed(() => selected.value !== null && (JSON.stringify(form) !== original.value || password.value || resetPassword.value))
const isNew = computed(() => selected.value === 'new')
function apply(account) {
  Object.assign(form, { username: account?.username || '', displayName: account?.displayName || '',
    active: account?.active ?? true, roles: [...(account?.roles || [])], turnusCodes: [...(account?.turnusCodes || [])], version: account?.version ?? 0 })
  original.value = JSON.stringify(form)
  password.value = ''; confirmation.value = ''; resetPassword.value = ''; resetConfirmation.value = ''; resetOpen.value = false
}
async function history() {
  const sequence = ++historySequence
  audit.value = []; auditError.value = ''
  if (!selected.value || isNew.value) { auditLoading.value = false; return }
  auditLoading.value = true
  try { const result = await fetchAccountAudit(auth.token, selected.value); if (sequence === historySequence) audit.value = result }
  catch (e) { if (sequence === historySequence) auditError.value = e.message }
  finally { if (sequence === historySequence) auditLoading.value = false }
}
async function select(account) {
  if (busy.value || loading.value || (dirty.value && !await confirm())) return
  selected.value = account?.id || 'new'; apply(account); error.value = ''; success.value = ''
  await history()
}
async function load() {
  if (loading.value || busy.value || (dirty.value && !await confirm('reload'))) return
  loading.value = true; error.value = ''
  try {
    const [rows, options] = await Promise.all([fetchAccounts(auth.token), fetchAccountTurnuses(auth.token)])
    accounts.value = rows; turnuses.value = options
    if (isNew.value) apply(null)
    else if (selected.value) {
      const row = rows.find(a => a.id === selected.value)
      if (row) apply(row)
      else selected.value = null
    }
    await history()
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function refreshOwnProfile(account) {
  if (account.username !== auth.user?.username) return
  // Reset/disable of the current account revoked this very session.
  if (!account.active || account.passwordChangeRequired) { auth.expire(auth.token); return }
  const token = auth.token, user = await fetchProfile(token)
  if (token === auth.token) auth.user = user
  if (!auth.isAdmin) await router.replace(auth.home)
}
async function save() {
  if (busy.value || loading.value) return
  error.value = ''; success.value = ''
  if (!form.roles.length) { error.value = 'Wybierz co najmniej jedną rolę.'; return }
  if (isNew.value) {
    error.value = passwordError(password.value) || (password.value !== confirmation.value ? 'Hasła muszą być takie same.' : '')
    if (error.value) return
  }
  busy.value = true
  try {
    const row = isNew.value
      ? await createAccount(auth.token, { username: form.username, displayName: form.displayName, password: password.value, roles: form.roles, turnusCodes: form.turnusCodes })
      : await updateAccount(auth.token, selected.value, { version: form.version, displayName: form.displayName, active: form.active, roles: form.roles, turnusCodes: form.turnusCodes })
    const index = accounts.value.findIndex(a => a.id === row.id)
    if (index < 0) accounts.value.push(row)
    else accounts.value[index] = row
    selected.value = row.id; apply(row)
    success.value = 'Konto zostało zapisane.'
    await refreshOwnProfile(row)
    if (!auth.expired && auth.isAdmin) await history()
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function reset() {
  if (busy.value || loading.value) return
  error.value = ''; success.value = ''
  error.value = passwordError(resetPassword.value) || (resetPassword.value !== resetConfirmation.value ? 'Hasła muszą być takie same.' : '')
  if (error.value) return
  if (JSON.stringify(form) !== original.value) { error.value = 'Najpierw zapisz lub odrzuć zmiany konta.'; return }
  busy.value = true
  try {
    const row = await resetAccountPassword(auth.token, selected.value, form.version, resetPassword.value)
    accounts.value = accounts.value.map(a => a.id === row.id ? row : a)
    apply(row); success.value = 'Hasło zresetowane. Przekaż hasło tymczasowe użytkownikowi bezpiecznym kanałem.'
    await refreshOwnProfile(row)
    if (!auth.expired && auth.isAdmin) await history()
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
function canLeave() { return !auth.isAdmin || !auth.isAuthenticated || (!busy.value && (!dirty.value || confirm())) }
function beforeUnload(event) { if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(canLeave)
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); load() })
onBeforeUnmount(() => { historySequence++; decide(false); window.removeEventListener('beforeunload', beforeUnload) })
</script>
<template>
  <main class="min-h-screen p-4 md:p-8"><div class="mx-auto max-w-6xl space-y-6">
    <BiuroHeader :unsaved-changes="Boolean(dirty)" :busy="busy" :confirm-discard="confirm" />
    <header class="flex flex-wrap items-center justify-between gap-3"><h2 class="text-2xl font-semibold">Konta użytkowników</h2>
      <div class="flex gap-2"><button :disabled="loading || busy" class="rounded border px-3 py-2 disabled:opacity-50" @click="load">Wczytaj aktualne dane</button><button :disabled="loading || busy" class="rounded bg-slate-800 px-4 py-2 text-white disabled:opacity-50" @click="select(null)">Dodaj konto</button></div>
    </header>
    <p v-if="error" role="alert" class="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-800">{{ error }}</p>
    <p v-if="success" role="status" class="rounded border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{{ success }}</p>
    <p v-if="loading" role="status">Wczytywanie kont…</p>
    <div class="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <section class="overflow-hidden rounded-xl border bg-white">
        <div class="border-b p-4"><input v-model="search" aria-label="Wyszukaj konto" placeholder="Wyszukaj" class="w-full rounded border px-3 py-2" /></div>
        <ul class="divide-y"><li v-for="account in filtered" :key="account.id">
          <button :disabled="busy || loading" :aria-pressed="selected === account.id" class="w-full border-l-4 p-4 text-left hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-slate-500 disabled:opacity-50" :class="selected === account.id ? 'border-slate-800 bg-slate-100' : 'border-transparent'" @click="select(account)">
            <span class="flex flex-wrap items-center gap-2">
              <span class="min-w-0 break-words font-medium">{{ account.displayName }}</span>
              <span class="shrink-0 rounded-full border px-2 py-0.5 text-xs font-medium" :class="account.active ? 'border-slate-400 bg-slate-200 text-slate-900' : 'border-slate-200 bg-white text-slate-600'">{{ account.active ? 'Aktywne' : 'Wyłączone' }}</span>
            </span>
            <span class="mt-1 block break-all text-xs text-slate-500">Login: {{ account.username }}</span>
            <span class="mt-2 flex flex-wrap gap-1">
              <span v-for="role in account.roles" :key="role" class="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{{ roleLabels[role] || role }}</span>
            </span>
            <span v-if="account.roles.includes('OPERATOR')" class="mt-2 block text-xs text-slate-600">Dostęp operacyjny: wszystkie turnusy</span>
            <span v-else-if="account.roles.includes('VIEWER')" class="mt-2 block text-xs text-slate-600">Przypisane turnusy: {{ account.turnusCodes?.length || 0 }}</span>
            <span v-if="account.passwordChangeRequired" class="mt-1 block text-xs text-amber-800">Wymagana zmiana hasła</span>
          </button>
        </li></ul><p v-if="!filtered.length && !loading" class="p-4 text-sm text-slate-600">Brak kont pasujących do wyszukiwania.</p>
      </section>
      <div v-if="selected" class="space-y-5">
        <section class="space-y-4 rounded-xl border bg-white p-5">
          <h3 class="text-xl font-semibold">{{ isNew ? 'Nowe konto' : 'Ustawienia konta' }}</h3>
          <form class="space-y-4" @submit.prevent="save"><fieldset :disabled="busy || loading" class="min-w-0 space-y-4">
            <label class="block text-sm">Login<input v-model="form.username" :disabled="!isNew" autocomplete="off" required minlength="3" maxlength="64" pattern="[A-Za-z0-9][A-Za-z0-9._\-]{2,63}" class="mt-1 w-full rounded border px-3 py-2 disabled:bg-slate-100" /></label>
            <label class="block text-sm">Nazwa użytkownika<input v-model="form.displayName" required maxlength="150" class="mt-1 w-full rounded border px-3 py-2" /></label>
            <label v-if="!isNew" class="flex items-center gap-2 text-sm"><input v-model="form.active" type="checkbox" />Konto aktywne</label>
            <fieldset class="space-y-2 rounded border p-3"><legend class="px-1 text-sm font-semibold">Role</legend>
              <label v-for="(label, role) in roleLabels" :key="role" class="flex items-center gap-2 text-sm"><input v-model="form.roles" :value="role" type="checkbox" />{{ label }}</label>
              <p class="text-xs text-slate-600">Administrator zarządza kontami. Operator ma pełny dostęp do obsługi turnusów i zgłoszeń. Operator-Podgląd list obejmuje przypisane turnusy.</p>
            </fieldset>
            <fieldset class="space-y-2 rounded border p-3"><legend class="px-1 text-sm font-semibold">Przypisane turnusy</legend>
              <p v-if="form.roles.includes('OPERATOR')" class="text-xs text-slate-600">Operator widzi wszystkie turnusy niezależnie od przypisań.</p>
              <p v-else-if="!form.roles.includes('VIEWER')" class="text-xs text-slate-600">Przypisania dają dostęp wyłącznie z rolą Operator-Podgląd list.</p>
              <div class="max-h-48 space-y-2 overflow-auto"><label v-for="turnus in turnuses" :key="turnus.turnusCode" class="flex items-start gap-2 text-sm"><input v-model="form.turnusCodes" :value="turnus.turnusCode" type="checkbox" class="mt-1" /><span>{{ turnus.turnusName }} <span class="text-xs text-slate-500">({{ turnus.turnusCode }})</span></span></label></div>
              <p v-if="!turnuses.length" class="text-sm text-slate-600">Brak zdefiniowanych turnusów.</p>
            </fieldset>
            <template v-if="isNew">
              <label class="block text-sm">Hasło tymczasowe<input v-model="password" type="password" autocomplete="new-password" required class="mt-1 w-full rounded border px-3 py-2" /></label>
              <label class="block text-sm">Powtórz hasło<input v-model="confirmation" type="password" autocomplete="new-password" required class="mt-1 w-full rounded border px-3 py-2" /></label>
              <p class="text-xs text-slate-600">Minimum 12 znaków, maksimum 72 bajty UTF-8. Użytkownik zmieni hasło przy pierwszym logowaniu. Przekaż je bezpiecznym kanałem.</p>
            </template>
            <button class="rounded bg-slate-800 px-4 py-2 text-white disabled:opacity-50" :disabled="busy || loading">{{ busy ? 'Zapisywanie…' : isNew ? 'Utwórz konto' : 'Zapisz zmiany' }}</button>
          </fieldset></form>
        </section>
        <section v-if="!isNew" class="space-y-4 rounded-xl border bg-white p-5">
          <button type="button" :disabled="busy || loading" :aria-expanded="resetOpen" class="rounded text-sm font-semibold underline" @click="resetOpen = !resetOpen">Reset hasła</button>
          <form v-if="resetOpen" class="space-y-3" @submit.prevent="reset">
            <p class="text-sm text-slate-600">Reset zakończy wszystkie sesje użytkownika i wymusi zmianę hasła. Reset własnego konta wymaga ponownego logowania.</p>
            <label class="block text-sm">Nowe hasło tymczasowe<input v-model="resetPassword" type="password" autocomplete="new-password" required :disabled="busy" class="mt-1 w-full rounded border px-3 py-2" /></label>
            <label class="block text-sm">Powtórz hasło<input v-model="resetConfirmation" type="password" autocomplete="new-password" required :disabled="busy" class="mt-1 w-full rounded border px-3 py-2" /></label>
            <button :disabled="busy" class="rounded border border-red-300 px-4 py-2 text-red-800 disabled:opacity-50">{{ busy ? 'Resetowanie…' : 'Zresetuj hasło i zakończ sesje' }}</button>
          </form>
        </section>
        <section v-if="!isNew" class="space-y-3 rounded-xl border bg-white p-5">
          <h3 class="text-lg font-semibold">Historia konta</h3><p class="text-xs text-slate-500">Ostatnie 100 zdarzeń</p>
          <p v-if="auditLoading" role="status">Wczytywanie historii…</p>
          <p v-else-if="auditError" role="alert" class="text-sm text-red-700">{{ auditError }} <button class="underline" @click="history">Spróbuj ponownie</button></p>
          <ul v-else class="max-h-96 space-y-3 overflow-auto text-sm"><li v-for="entry in audit" :key="entry.id" class="border-t pt-3">
            <p class="font-medium">{{ eventLabels[entry.event] || 'Zdarzenie konta' }}</p>
            <p class="text-xs text-slate-600">{{ new Date(entry.occurredAt).toLocaleString('pl-PL') }} · {{ entry.actorUsername || 'System / próba logowania' }}</p>
            <p v-if="auditChanges(entry)" class="mt-1 break-words text-slate-600">{{ auditChanges(entry) }}</p>
          </li></ul><p v-if="!auditLoading && !auditError && !audit.length" class="text-sm text-slate-600">Brak zdarzeń.</p>
        </section>
      </div>
      <p v-else class="rounded-xl border bg-white p-5 text-slate-600">Wybierz konto lub dodaj nowe.</p>
    </div>
    <DiscardChangesDialog v-if="prompt" :kind="prompt.kind" @stay="decide(false)" @discard="decide(true)" />
  </div></main>
</template>
