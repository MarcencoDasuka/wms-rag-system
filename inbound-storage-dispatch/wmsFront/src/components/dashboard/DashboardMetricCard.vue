<template>
  <Card class="app-card dashboard-metric-card border-none shadow-lg" :class="{ 'dashboard-metric-card--warning': warning }">
    <template #content>
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0">
          <p class="app-subtitle text-sm font-medium mb-2">{{ title }}</p>
          <Skeleton v-if="loading" width="6rem" height="2.5rem" class="mb-3" />
          <div v-else class="app-title text-4xl font-bold">{{ formattedValue }}</div>
          <p class="app-muted text-sm mt-3">{{ description }}</p>
        </div>
        <div class="dashboard-metric-card__icon rounded-xl p-3" :class="iconTone">
          <i :class="[icon, 'text-2xl']"></i>
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue'

import Card from 'primevue/card'
import Skeleton from 'primevue/skeleton'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  value: {
    type: Number,
    default: 0
  },
  icon: {
    type: String,
    required: true
  },
  iconTone: {
    type: String,
    default: 'dashboard-metric-card__icon--primary'
  },
  description: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  warning: {
    type: Boolean,
    default: false
  }
})

const formattedValue = computed(() => new Intl.NumberFormat().format(props.value ?? 0))
</script>

<style scoped>
.dashboard-metric-card--warning {
  box-shadow:
    var(--shadow-card),
    0 0 0 1px color-mix(in srgb, var(--status-warning) 40%, transparent) !important;
}

.dashboard-metric-card__icon {
  background: color-mix(in srgb, currentColor 12%, transparent);
}

.dashboard-metric-card__icon--primary {
  color: var(--brand-accent);
}

.dashboard-metric-card__icon--warning {
  color: var(--status-warning);
}

.dashboard-metric-card__icon--success {
  color: var(--status-success);
}

.dashboard-metric-card__icon--danger {
  color: var(--status-danger);
}
</style>
