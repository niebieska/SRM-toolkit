<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import BiuroHeader from '../components/BiuroHeader.vue'
import { useAuthStore } from '../stores/auth.js'
import { changePassword } from '../api/authApi.js'
import { passwordError } from '../utils/accountAccess.js'
const auth = useAuthStore(), router = useRouter()
const current = ref(''), password = ref(''), confirmation = ref(''), error = ref(''), busy = ref(false)
let active = true, requestToken = null
onBeforeUnmount(() => { active = false })
onBeforeRouteLeave(() => !busy.value || auth.token !== requestToken)
async function submit() {
  if (busy.value) return
  error.value = passwordError(password.value) || (password.value !== confirmation.value ? 'Nowe hasła muszą być takie same.' : '')
  if (error.value) return
  busy.value = true
  requestToken = auth.token
  try {
    const token = requestToken, sessionVersion = auth.sessionVersion
    const response = await changePassword(token, current.value, password.value)
    // Expiry clears the token; only a newer login/clear supersedes this response.
    if (auth.sessionVersion !== sessionVersion) return
    auth.login(response)
    current.value = ''; password.value = ''; confirmation.value = ''
    busy.value = false
    if (active) await router.replace(auth.home)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <main class="min-h-screen p-4 md:p-8"><div class="mx-auto max-w-6xl space-y-6">
    <BiuroHeader :busy="busy" />
    <section class="mx-auto max-w-lg space-y-4 rounded-xl border bg-white p-6">
      <h2 class="text-xl font-semibold">Zmiana hasła</h2>
      <p v-if="auth.user?.passwordChangeRequired" class="text-sm text-slate-600">Przed rozpoczęciem pracy ustaw własne hasło.</p>
      <form class="space-y-4" @submit.prevent="submit">
        <fieldset :disabled="busy" class="space-y-4">
        <label class="block text-sm">Obecne hasło<input v-model="current" type="password" autocomplete="current-password" required class="mt-1 w-full rounded border px-3 py-2" /></label>
        <label class="block text-sm">Nowe hasło<input v-model="password" type="password" autocomplete="new-password" required aria-describedby="password-help" class="mt-1 w-full rounded border px-3 py-2" /></label>
        <p id="password-help" class="text-sm text-slate-600">Co najmniej 12 znaków, najwyżej 72 bajty UTF-8.</p>
        <label class="block text-sm">Powtórz nowe hasło<input v-model="confirmation" type="password" autocomplete="new-password" required class="mt-1 w-full rounded border px-3 py-2" /></label>
        <p v-if="error" role="alert" class="text-sm text-red-700">{{ error }}</p>
        <button :disabled="busy" class="rounded bg-slate-800 px-4 py-2 text-white disabled:opacity-50">{{ busy ? 'Zapisywanie…' : 'Zmień hasło' }}</button>
        </fieldset>
      </form>
    </section>
  </div></main>
</template>
