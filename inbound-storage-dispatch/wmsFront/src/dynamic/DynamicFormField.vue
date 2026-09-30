<template>
  <FormField class="flex flex-col gap-2">
    <slot />
  </FormField>
</template>

<script setup>
import { FormField } from '@primevue/forms'
import { provide } from 'vue'

const props = defineProps({
  groupId: {
    type: String,
    default: undefined,
  },
  name: {
    type: String,
    required: true,
  },
})

provide('$fcDynamicFormField', {
  groupId: props.groupId,
  name: props.name,
})
</script>
