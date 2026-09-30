<template>
  <RouterLink
    v-if="!disabled"
    :to="target"
    class="app-product-link"
    :title="title"
  >
    <slot>{{ label }}</slot>
  </RouterLink>
  <span v-else class="app-product-link app-product-link--disabled">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  productId: { type: [Number, String], default: null },
  barcode: { type: String, default: '' },
  name: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const label = computed(() => props.name || props.barcode || 'Product')
const title = computed(() => `Open ${label.value} in Products`)
const target = computed(() => ({
  name: 'products',
  query: {
    ...(props.productId ? { productId: props.productId } : {}),
    ...(props.barcode ? { barcode: props.barcode } : {}),
    ...(props.name ? { product: props.name } : {})
  }
}))
</script>
