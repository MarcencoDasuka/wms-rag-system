<template>
  <Form @submit="onSubmit" class="flex flex-col gap-4">
    <template v-if="!fields">
      <slot />
    </template>
    <template v-else>
      <DynamicFormField
        v-for="(config, name) in fields"
        :key="name"
        :name="name"
        :groupId="config.groupId"
      >
        <DynamicFormLabel>{{ config.label }}</DynamicFormLabel>
        <DynamicFormControl
          :as="config.as"
          :defaultValue="config.defaultValue"
          :fluid="config.fluid"
          :schema="config.schema"
          v-bind="config"
        />
        <template v-if="config.messages">
          <DynamicFormMessage
            v-for="msg in config.messages"
            :key="msg.errorType"
            :errorType="msg.errorType"
            :severity="msg.severity"
          />
        </template>
        <DynamicFormMessage v-else />
      </DynamicFormField>
      <DynamicFormSubmit />
    </template>
  </Form>
</template>

<script setup>
import { provide, reactive } from 'vue'
import { Form } from '@primevue/forms'
import DynamicFormField from './DynamicFormField.vue'
import DynamicFormLabel from './DynamicFormLabel.vue'
import DynamicFormControl from './DynamicFormControl.vue'
import DynamicFormMessage from './DynamicFormMessage.vue'
import DynamicFormSubmit from './DynamicFormSubmit.vue'

const props = defineProps({
  fields: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['submit'])

// Registry for fields (name, schema, defaultValue)
const fieldsRegistry = reactive(new Map())

const addField = (name, schema, defaultValue) => {
  if (!fieldsRegistry.has(name)) {
    fieldsRegistry.set(name, { name, schema, defaultValue })
  }
}

provide('$fcDynamicForm', {
  addField,
  getFields: () => fieldsRegistry,
})

const onSubmit = (event) => {
  emit('submit', event)
}
</script>
