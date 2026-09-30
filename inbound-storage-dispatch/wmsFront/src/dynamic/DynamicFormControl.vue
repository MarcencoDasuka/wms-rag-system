<template>
  <component
    :is="component"
    :id="groupId"
    :name="name"
    :class="{ 'w-full': fluid }"
    v-bind="$attrs"
  />
</template>

<script setup>
import * as PrimeVue from 'primevue'
import { computed, inject, onMounted } from 'vue'

const props = defineProps({
  as: {
    type: String,
    default: 'InputText',
  },
  schema: {
    type: Object,
    required: true,
  },
  defaultValue: {
    default: '',
  },
  fluid: {
    type: Boolean,
    default: false,
  },
})

const $fcDynamicForm = inject('$fcDynamicForm')
const $fcDynamicFormField = inject('$fcDynamicFormField')

const name = computed(() => $fcDynamicFormField?.name)
const groupId = computed(() => $fcDynamicFormField?.groupId)

const component = computed(() => PrimeVue[props.as] ?? props.as)

onMounted(() => {
  if ($fcDynamicForm && name.value) {
    $fcDynamicForm.addField(name.value, props.schema, props.defaultValue)
  }
})
</script>
