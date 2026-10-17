<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { loginRequest } from '../api/authApi.js'
import { useAuthStore } from '../stores/auth.js'
const emit = defineEmits(['signed-in'])
const auth = useAuthStore()
const username = ref(''), password = ref(''), error = ref(''), loading = ref(false)
let attempt = 0
onBeforeUnmount(() => { attempt++ })
async function submit() {
  if (loading.value) return
  const currentAttempt = ++attempt
  loading.value = true; error.value = ''
  try {
    const response = await loginRequest(username.value, password.value)
    if (currentAttempt !== attempt) return
    password.value = ''
    emit('signed-in', auth.login(response))
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
</script>
<template>
  <form class="space-y-4" @submit.prevent="submit">
    <label class="block text-sm">Login<input v-model="username" name="username" autocomplete="username" required autofocus class="mt-1 w-full rounded border border-slate-300 px-3 py-2" /></label>
    <label class="block text-sm">Hasło<input v-model="password" name="password" type="password" autocomplete="current-password" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2" /></label>
    <p v-if="error" role="alert" class="text-sm text-red-700">{{ error }}</p>
    <button :disabled="loading" class="w-full rounded bg-slate-800 py-2 text-white disabled:opacity-50">{{ loading ? 'Logowanie…' : 'Zaloguj' }}</button>
  </form>
</template>
