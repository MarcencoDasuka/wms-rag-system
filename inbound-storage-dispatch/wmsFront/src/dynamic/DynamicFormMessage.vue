<template>
  <Message v-if="visible" :severity="severity" :icon="icon" size="small" variant="simple">
    <slot>{{ message }}</slot>
  </Message>
</template>

<script setup>
import { isNotEmpty } from '@primeuix/utils'
import { computed, inject } from 'vue'
import Message from 'primevue/message'

const props = defineProps({
  errorType: {
    type: String,
    default: undefined,
  },
  severity: {
    type: String,
    default: 'error',
  },
  icon: {
    type: String,
    default: 'pi pi-key',
  },
})

const $pcForm = inject('$pcForm')
const $fcDynamicFormField = inject('$fcDynamicFormField')

const fieldName = computed(() => $fcDynamicFormField?.name)
const state = computed(() => $pcForm?.states?.[fieldName.value])
const errors = computed(() => state.value?.errors || [])
const invalid = computed(() => state.value?.invalid)

const error = computed(() =>
  errors.value.find(
    (err) =>
      props.errorType === err.errorType || (props.errorType && isNotEmpty(err[props.errorType])),
  ),
)

const message = computed(() => (props.errorType ? error.value?.message : errors.value[0]?.message))

const visible = computed(
  () => invalid.value && (props.errorType ? error.value : errors.value.length > 0),
)
</script>
