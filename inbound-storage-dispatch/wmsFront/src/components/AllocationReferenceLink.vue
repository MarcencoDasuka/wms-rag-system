<template>
  <RouterLink v-if="target" :to="target" class="app-product-link text-primary font-semibold hover:underline" :title="title">
    {{ label }}
  </RouterLink>

  <span v-else class="app-product-link app-muted" :title="title">
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  type: { type: String, default: '' },
  orderId: { type: [Number, String], default: null },
  replenishmentId: { type: [Number, String], default: null },
})

const label = computed(() => {
  if ((props.type === 'PICKING_ORDER' || props.type === 'ORDER') && props.orderId) {
    return `ORD-${props.orderId}` // Сделал короче и красивее
  }

  if (props.type === 'REPLENISHMENT' && props.replenishmentId) {
    return `REPL-${props.replenishmentId}` // Сделал короче и красивее
  }

  return 'Reference'
})

const title = computed(() => `Open ${label.value}`)

const target = computed(() => {
  if ((props.type === 'PICKING_ORDER' || props.type === 'ORDER') && props.orderId) {
    return {
      path: '/supervisor/orders',
      query: { id: props.orderId },
    }
  }

  if (props.type === 'REPLENISHMENT' && props.replenishmentId) {
    return {
      path: '/supervisor/replenishments',
      query: { id: props.replenishmentId }, // Этот query подхватит твой ReplenishmentsView
    }
  }

  return null
})
</script>
