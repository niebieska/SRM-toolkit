<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import SignInForm from '../components/SignInForm.vue'
const router = useRouter(), auth = useAuthStore()
</script>
<template>
  <main class="flex min-h-screen items-center justify-center p-4">
    <section class="w-full max-w-md space-y-4 rounded-xl border bg-white p-6 shadow-sm">
      <h1 class="text-2xl font-semibold">Logowanie Biuro</h1>
      <p v-if="auth.sessionMessage" role="status" class="text-sm text-slate-600">{{ auth.sessionMessage }}</p>
      <SignInForm @signed-in="router.replace(auth.home)" />
    </section>
  </main>
</template>
