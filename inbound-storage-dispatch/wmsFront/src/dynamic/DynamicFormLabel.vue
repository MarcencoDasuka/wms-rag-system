<template>
  <label :for="htmlFor">
    <slot />
  </label>
</template>

<script setup>
import { computed, inject } from 'vue'

const $fcDynamicFormField = inject('$fcDynamicFormField')

const htmlFor = computed(() => $fcDynamicFormField?.groupId)
</script>
