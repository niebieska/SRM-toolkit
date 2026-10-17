<script setup>
import { computed, onMounted, ref } from 'vue'
import BiuroHeader from '../components/BiuroHeader.vue'
import { useAuthStore } from '../stores/auth.js'
import { fetchViewerTurnuses } from '../api/turnusApi.js'
import { matchesTurnusSearch, formatTurnusDate } from '../utils/turnusSearch.js'
const auth = useAuthStore(), rows = ref([]), search = ref(''), loading = ref(false), error = ref('')
const filtered = computed(() => rows.value.filter(t => matchesTurnusSearch(t, search.value)))
async function load() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try { rows.value = await fetchViewerTurnuses(auth.token) } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <main class="min-h-screen p-4 md:p-8"><div class="mx-auto max-w-6xl space-y-6">
    <BiuroHeader />
    <section class="overflow-hidden rounded-xl border bg-white">
      <div class="border-b p-4"><h2 class="mb-3 text-xl font-semibold">Listy turnusów</h2>
        <input v-model="search" aria-label="Wyszukaj turnus" placeholder="Wyszukaj" class="w-full rounded border px-3 py-2 sm:w-56" />
      </div>
      <p v-if="error" role="alert" class="p-4 text-red-700">{{ error }} <button class="underline" @click="load">Spróbuj ponownie</button></p>
      <p v-else-if="loading" role="status" class="p-4">Wczytywanie…</p>
      <div v-else class="overflow-x-auto"><table class="w-full text-left text-sm">
        <thead class="bg-slate-50"><tr><th class="p-4">Turnus</th><th class="p-4">Termin</th><th class="p-4">Miejsce</th><th class="p-4"><span class="sr-only">Listy</span></th></tr></thead>
        <tbody><tr v-for="turnus in filtered" :key="turnus.turnusCode" class="border-t">
          <td class="p-4 font-medium">{{ turnus.turnusName }}</td><td class="whitespace-nowrap p-4">{{ formatTurnusDate(turnus.startDate) }} – {{ formatTurnusDate(turnus.endDate) }}</td><td class="p-4">{{ turnus.turnusLocation }}</td>
          <td class="p-4"><RouterLink :to="{ name: 'turnus-lists', params: { code: turnus.turnusCode } }" class="rounded bg-slate-700 px-3 py-2 text-white">Listy</RouterLink></td>
        </tr></tbody>
      </table><p v-if="!filtered.length" class="p-6 text-slate-600">{{ rows.length ? 'Brak wyników.' : 'Nie przypisano Ci żadnych turnusów.' }}</p></div>
    </section>
  </div></main>
</template>
