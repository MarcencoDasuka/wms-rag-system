<template>
  <Button type="submit" :severity="severity" :label="label" />
</template>

<script setup>
import Button from 'primevue/button'

defineProps({
  severity: {
    type: String,
    default: 'secondary',
  },
  label: {
    type: String,
    default: 'Submit',
  },
})
</script>
