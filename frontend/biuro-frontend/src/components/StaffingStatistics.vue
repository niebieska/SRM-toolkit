<script setup>
defineProps({
  roles: { type: Array, required: true },
  unmapped: { type: Number, default: 0 },
})
</script>

<template>
  <section v-if="roles.length" aria-labelledby="staffing-statistics-heading" class="mt-3 border-t border-slate-100 pt-3">
    <h3 id="staffing-statistics-heading" class="sr-only">Kadra według ról — przyjęci / wymagani</h3>
    <ul class="flex flex-wrap items-baseline gap-x-6 gap-y-2 text-sm">
      <li v-for="role in roles" :key="role.categoryCode">
        <span class="text-slate-500">{{ role.label }}:</span>
        <span class="ml-1 font-medium tabular-nums">{{ role.accepted }}/{{ role.required }}</span>
      </li>
    </ul>
    <p v-if="unmapped" role="status" class="mt-3 rounded-lg bg-amber-50 p-2 text-sm text-amber-900">Nie można przypisać {{ unmapped }} przyjętych zgłoszeń kadry do ról. Sprawdź ich dane; są uwzględnione w ogólnej liczbie kadry.</p>
  </section>
</template>
