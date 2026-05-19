<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { loginRequest } from '../api/authApi'

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)

const authStore = useAuthStore()
const router = useRouter()

async function submit() {
  errorMessage.value = ''
  loading.value = true

  try {
    const response = await loginRequest(username.value, password.value)
    authStore.login(response.token)
    await router.push('/dashboard')
  } catch (error) {
    errorMessage.value = 'Nieprawidłowy login lub hasło'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="min-h-screen flex items-center justify-center p-4">
    <div class="w-full max-w-md bg-white rounded-xl shadow p-6 space-y-4">
      <h1 class="text-2xl font-semibold">Logowanie Biuro</h1>
      <form class="space-y-3" @submit.prevent="submit">
        <div>
          <label class="block mb-1 text-sm" for="username">Login</label>
          <input
            id="username"
            v-model="username"
            class="w-full rounded border border-slate-300 px-3 py-2"
            required
          />
        </div>
        <div>
          <label class="block mb-1 text-sm" for="password">Hasło</label>
          <input
            id="password"
            v-model="password"
            type="password"
            class="w-full rounded border border-slate-300 px-3 py-2"
            required
          />
        </div>
        <p v-if="errorMessage" class="text-sm text-red-600">{{ errorMessage }}</p>
        <button
          type="submit"
          class="w-full rounded bg-blue-600 text-white py-2 hover:bg-blue-700 disabled:opacity-60"
          :disabled="loading"
        >
          {{ loading ? 'Logowanie...' : 'Zaloguj' }}
        </button>
      </form>
    </div>
  </main>
</template>
