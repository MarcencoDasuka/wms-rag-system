<template>
  <Button
    :icon="isDark ? 'pi pi-sun' : 'pi pi-moon'"
    :label="showLabel ? (isDark ? 'Light' : 'Dark') : undefined"
    severity="secondary"
    outlined
    rounded
    :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
    @click="toggleTheme"
  />
</template>

<script setup>
import Button from 'primevue/button'

import { useTheme } from '@/composables/useTheme'

defineProps({
  showLabel: { type: Boolean, default: false }
})

const { isDark, toggleTheme } = useTheme()
</script>
