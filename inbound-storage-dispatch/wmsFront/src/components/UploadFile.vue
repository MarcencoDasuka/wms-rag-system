<template>
  <Dialog
    :visible="visible"
    header="Import File"
    modal
    class="w-full max-w-md"
    @update:visible="emit('update:visible', $event)"
    @hide="reset"
    ><div class="flex justify-end w-full pt-2">
      <div class="flex items-center gap-2">
        <span class="font-medium mr-2">Templates</span>

        <Button
          v-if="xlsxTemplatePath"
          as="a"
          :href="xlsxTemplatePath"
          download
          icon="pi pi-file-excel"
          severity="success"
          outlined
          size="small"
          label="XLSX"
        />

        <Button
          v-if="csvTemplatePath"
          as="a"
          :href="csvTemplatePath"
          download
          icon="pi pi-file"
          severity="secondary"
          outlined
          size="small"
          label="CSV"
        />
      </div>
    </div>
    <div class="flex flex-col gap-4">
      <FileUpload
        ref="fileUpload"
        name="file"
        :multiple="false"
        :customUpload="true"
        accept=".xlsx,.xls,.csv"
        :auto="false"
        @select="onSelect"
      >
        <template #content="{ chooseCallback }">
          <div
            class="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center cursor-pointer hover:border-primary transition"
            @click="chooseCallback()"
            @dragover.prevent
            @drop.prevent="chooseCallback()"
          >
            <i class="pi pi-cloud-upload text-3xl mb-2"></i>

            <p class="mb-1">Drag and drop your file here</p>
          </div>
        </template>
      </FileUpload>

      <div v-if="file" class="text-sm text-gray-500">
        Selected: {{ file.name }} ({{ formatSize(file.size) }})
      </div>

      <Message v-if="error" severity="error">{{ error }}</Message>
    </div>

    <template #footer>
      <Button label="Cancel" severity="secondary" text @click="close" />
      <Button
        label="Import"
        icon="pi pi-upload"
        severity="success"
        :loading="loading"
        :disabled="!file"
        @click="upload"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { ref } from 'vue'

import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import Button from 'primevue/button'
import Message from 'primevue/message'

const props = defineProps({
  visible: Boolean,
  apiCall: {
    type: Function,
    required: true,
  },
  xlsxTemplatePath: {
    type: String,
    default: null,
  },
  csvTemplatePath: {
    type: String,
    default: null,
  },
})

const emit = defineEmits(['update:visible', 'success'])

const file = ref(null)
const loading = ref(false)
const error = ref('')

const onSelect = (event) => {
  file.value = event.files?.[0] ?? null
  error.value = ''
}

const upload = async () => {
  if (!file.value) return

  loading.value = true
  error.value = ''

  try {
    const formData = new FormData()
    formData.append('file', file.value)

    await props.apiCall(formData)

    emit('success')
    close()
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || 'Import failed'
  } finally {
    loading.value = false
  }
}

const reset = () => {
  file.value = null
  error.value = ''
  loading.value = false
}

const close = () => {
  emit('update:visible', false)
}

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}
</script>
