<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth.js'
import SignInForm from './components/SignInForm.vue'
import { tokenExpiry, canVisit } from './utils/accountAccess.js'
const auth = useAuthStore(), router = useRouter(), dialog = ref(null)
const blocked = computed(() => auth.expired && router.currentRoute.value.path !== '/login' && router.currentRoute.value.path !== '/sesja')
let timer
async function signedIn(differentUser) {
  if (differentUser || auth.user.passwordChangeRequired || !canVisit(auth.user, router.currentRoute.value)) await router.replace(auth.home)
}
function checkExpiry() {
  if (auth.token && tokenExpiry(auth.token) <= Date.now()) auth.expire(auth.token)
}
async function syncDialog() {
  await nextTick()
  if (blocked.value && !dialog.value?.open) dialog.value?.showModal()
  if (!blocked.value && dialog.value?.open) dialog.value.close()
}
watch(blocked, syncDialog, { flush: 'post' })
onMounted(() => { timer = setInterval(checkExpiry, 1000); checkExpiry(); syncDialog() })
onBeforeUnmount(() => clearInterval(timer))
async function abandon() { auth.clear(); await router.replace('/login') }
</script>
<template>
  <div :inert="blocked || undefined" :aria-hidden="blocked || undefined"><router-view :key="auth.screenVersion" /></div>
  <dialog ref="dialog" aria-labelledby="session-title" class="w-[calc(100%_-_2rem)] max-w-md rounded-xl border bg-white p-6 shadow-xl backdrop:bg-slate-900/60" @cancel.prevent>
    <h2 id="session-title" class="mb-3 text-xl font-semibold">Zaloguj się ponownie</h2>
    <p class="mb-4 text-sm text-slate-600">{{ auth.sessionMessage }}</p>
    <SignInForm v-if="blocked" @signed-in="signedIn" />
    <button class="mt-4 rounded text-sm underline" @click="abandon">Opuść ekran i odrzuć niezapisane zmiany</button>
  </dialog>
</template>
