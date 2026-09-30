<template>
  <div class="p-6">
    <ConfirmDialog />

    <AppDataTable
      v-model:selection="selectedLocations"
      :value="locations"
      :loading="loading"
      :rowClass="locationRowClass"
      :filterFields="locationFilterFields"
      :editMode="editMode ? 'cell' : null"
      paginator
      :rows="10"
      stripedRows
      class="p-datatable-sm"
      dataKey="id"
      emptyMessage="No locations found."
      @cell-edit-complete="onCellEditComplete"
    >
      <template #toolbar>
        <Button
          icon="pi pi-refresh"
          size="small"
          severity="secondary"
          outlined
          :loading="loading"
          aria-label="Refresh"
          @click="loadLocations"
        />
        <Button
          label="Import"
          icon="pi pi-file-import"
          severity="info"
          @click="importDialogVisible = true"
        />
        <Button label="Create" icon="pi pi-plus" severity="success" @click="openCreateDialog" />
        <Button
          :label="editMode ? 'Exit Edit' : 'Edit'"
          icon="pi pi-pencil"
          severity="warning"
          outlined
          @click="toggleEditMode"
        />
        <Button
          v-if="editMode"
          label="Submit"
          icon="pi pi-check"
          severity="success"
          :disabled="!hasPendingChanges"
          :loading="actionLoading"
          @click="confirmSubmitChanges"
        />
        <Button
          v-if="editMode"
          label="Reset"
          icon="pi pi-refresh"
          severity="secondary"
          outlined
          :disabled="!hasPendingChanges"
          @click="confirmResetChanges"
        />
        <Button
          v-if="editMode"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          outlined
          :disabled="!selectedLocations.length"
          @click="confirmDeleteSelected"
        />
        <span v-if="editMode" class="app-muted text-sm"
        >{{ selectedLocations.length }} selected</span
        >
      </template>
      <Column v-if="editMode" selectionMode="multiple" headerStyle="width: 3rem" />
      <Column field="name" header="Name" sortable filter>
        <template #body="{ data }">
          <span class="app-title font-bold text-primary">{{ data.name }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputText v-model="data[field]" class="w-full" autofocus />
        </template>
      </Column>
      <Column field="barcode" header="Barcode" sortable filter>
        <template #body="{ data }">
          <span class="app-title font-bold text-primary">{{ data.barcode }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputText v-model="data[field]" class="w-full" autofocus />
        </template>
      </Column>

      <Column field="zone" filterField="formattedZone" header="Zone" sortable filter>
        <template #body="{ data }">
          <span class="app-subtitle">{{ data.formattedZone }}</span>
        </template>
        <template #editor="{ data, field }">
          <Dropdown v-model="data[field]" :options="zones" filter class="w-full" />
        </template>
      </Column>

      <Column field="description" header="Description" filter>
        <template #body="{ data }">
          <span class="app-subtitle">{{ data.description || 'No description' }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputText v-model="data[field]" class="w-full" />
        </template>
      </Column>

      <Column field="available" filterField="formattedStatus" header="Status" sortable filter>
        <template #body="{ data }">
          <Tag
            :severity="data.available ? 'success' : 'danger'"
            :value="data.formattedStatus"
          />
        </template>
        <template #editor="{ data, field }">
          <Dropdown
            v-model="data[field]"
            :options="availabilityOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </template>
      </Column>
    </AppDataTable>

    <Dialog
      v-model:visible="dialogVisible"
      :header="isEditing ? 'Edit Location' : 'Create Location'"
      :modal="true"
      class="w-full max-w-md"
    >
      <div class="flex flex-col gap-4 mt-2">
        <div class="flex flex-col gap-2">
          <label for="name" class="app-subtitle font-medium"
          >Name <span class="text-red-500">*</span></label
          >
          <InputText
            id="name"
            v-model="formData.name"
            placeholder="e.g., PICK-A-01"
            required
            autofocus
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="barcode" class="app-subtitle font-medium"
          >Barcode <span class="text-red-500">*</span></label
          >
          <InputText
            id="barcode"
            v-model="formData.barcode"
            placeholder="e.g., PICK-A-01"
            required
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="zone" class="app-subtitle font-medium"
          >Zone <span class="text-red-500">*</span></label
          >
          <Dropdown
            id="zone"
            v-model="formData.zone"
            :options="zones"
            placeholder="Select Zone"
            filter
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="description" class="app-subtitle font-medium">Description</label>
          <Textarea
            id="description"
            v-model="formData.description"
            rows="3"
            placeholder="Optional description..."
            class="w-full"
          />
        </div>

        <div class="flex items-center gap-3 mt-2" v-if="isEditing">
          <InputSwitch inputId="available" v-model="formData.available" />
          <label for="available" class="app-title font-medium cursor-pointer"
          >Location is Available</label
          >
        </div>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          text
          severity="secondary"
          @click="dialogVisible = false"
        />
        <Button
          :label="isEditing ? 'Save' : 'Create'"
          icon="pi pi-check"
          severity="success"
          :loading="actionLoading"
          @click="saveLocation"
          :disabled="!isFormValid"
        />
      </template>
    </Dialog>
    <UploadFile
      v-model:visible="importDialogVisible"
      :apiCall="handleImport"
      @success="loadLocations"
      xlsx-template-path="/templates/location_template.xlsx"
      csv-template-path="/templates/location_template.csv"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Dropdown from 'primevue/dropdown'
import InputSwitch from 'primevue/inputswitch'
import ConfirmDialog from 'primevue/confirmdialog'

import { locationApi } from '@/api/locationApi'
import UploadFile from '@/components/UploadFile.vue'

const toast = useToast()
const confirm = useConfirm()

const importDialogVisible = ref(false)
const locations = ref([])
const originalLocations = ref([])
const selectedLocations = ref([])
const loading = ref(false)
const actionLoading = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editMode = ref(false)
const modifiedLocationIds = ref(new Set())

const formatString = (str) => {
  if (!str) return '';
  return String(str).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

const formData = ref({
  id: null,
  name: '',
  barcode: '',
  zone: null,
  description: '',
  available: true,
})

const zones = ref(['PICKING', 'REPLENISHMENT', 'DISPATCH'])
const availabilityOptions = ref([
  { label: 'AVAILABLE', value: true },
  { label: 'DISABLED', value: false },
])
const cloneRows = (items) => JSON.parse(JSON.stringify(items || []))

const locationFilterFields = [
  { field: 'barcode', label: 'Barcode' },
  { field: 'name', label: 'Name' },
  { field: 'formattedZone', label: 'Zone' },
  { field: 'description', label: 'Description' },
  { field: 'formattedStatus', label: 'Status' },
]

const handleImport = async (formData) => {
  if (!(formData instanceof FormData)) {
    throw new Error('Expected FormData')
  }
  return locationApi.importLocations(formData)
}

const isFormValid = computed(() => {
  return formData.value.barcode?.trim() && formData.value.zone
})

const loadLocations = async () => {
  loading.value = true
  try {
    const res = await locationApi.getAll()
    locations.value = res.data.map(loc => ({
      ...loc,
      formattedZone: formatString(loc.zone),
      formattedStatus: loc.available ? 'Available' : 'Disabled'
    }))
    originalLocations.value = cloneRows(res.data)
    modifiedLocationIds.value = new Set()
    selectedLocations.value = []
  } catch (error) {
    showError('Failed to load locations', error)
  } finally {
    loading.value = false
  }
}

const hasPendingChanges = computed(() => modifiedLocationIds.value.size > 0)

const locationRowClass = (location) => ({
  'app-row-modified': modifiedLocationIds.value.has(location.id),
})

const getOriginalLocation = (id) => originalLocations.value.find((location) => location.id === id)

const normalizeLocation = (location) => ({
  name: location.name?.trim() || '',
  barcode: location.barcode?.trim() || '',
  zone: location.zone || null,
  description: location.description || '',
  available: location.available !== false,
})

const refreshModifiedState = (location) => {
  const original = getOriginalLocation(location.id)
  if (!original) return

  const currentValue = JSON.stringify(normalizeLocation(location))
  const originalValue = JSON.stringify(normalizeLocation(original))
  const nextIds = new Set(modifiedLocationIds.value)

  if (currentValue !== originalValue) nextIds.add(location.id)
  else nextIds.delete(location.id)

  modifiedLocationIds.value = nextIds
}

const onCellEditComplete = ({ data, newValue, field }) => {
  if (!editMode.value) return
  data[field] = typeof newValue === 'string' ? newValue.trim() : newValue

  if (field === 'zone') data.formattedZone = formatString(data.zone)
  if (field === 'available') data.formattedStatus = data.available ? 'Available' : 'Disabled'

  refreshModifiedState(data)
}

const toggleEditMode = () => {
  if (!editMode.value) {
    editMode.value = true
    selectedLocations.value = []
    return
  }

  if (hasPendingChanges.value) {
    confirmResetChanges(() => {
      editMode.value = false
    })
    return
  }

  editMode.value = false
  selectedLocations.value = []
}

const confirmSubmitChanges = () => {
  confirm.require({
    message: `Submit ${modifiedLocationIds.value.size} changed location(s)?`,
    header: 'Submit Location Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-success',
    accept: submitLocationChanges,
  })
}

const submitLocationChanges = async () => {
  actionLoading.value = true
  try {
    const changedLocations = locations.value.filter((location) =>
      modifiedLocationIds.value.has(location.id),
    )
    await Promise.all(
      changedLocations.map((location) =>
        locationApi.update(location.id, {
          name: location.name,
          barcode: location.barcode,
          zone: location.zone,
          description: location.description,
          available: location.available,
        }),
      ),
    )
    toast.add({ severity: 'success', summary: 'Changes saved', detail: `${changedLocations.length} location(s) updated.`, life: 3000 })
    editMode.value = false
    await loadLocations()
  } catch (error) {
    showError('Submit failed', error)
  } finally {
    actionLoading.value = false
  }
}

const confirmResetChanges = (afterReset) => {
  confirm.require({
    message: 'Discard all unsaved location changes?',
    header: 'Reset Unsaved Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-warning',
    accept: () => {
      locations.value = cloneRows(originalLocations.value)
      // re-map formatted fields after revert
      locations.value.forEach(loc => {
        loc.formattedZone = formatString(loc.zone)
        loc.formattedStatus = loc.available ? 'Available' : 'Disabled'
      })
      modifiedLocationIds.value = new Set()
      toast.add({ severity: 'info', summary: 'Changes reset', detail: 'Unsaved location changes were discarded.', life: 2500 })
      if (typeof afterReset === 'function') afterReset()
    },
  })
}

const openCreateDialog = () => {
  isEditing.value = false
  formData.value = { id: null, name: '', barcode: '', zone: null, description: '', available: true }
  dialogVisible.value = true
}

const openEditDialog = (data) => {
  isEditing.value = true
  formData.value = { ...data }
  dialogVisible.value = true
}

const saveLocation = async () => {
  actionLoading.value = true
  try {
    const payload = {
      name: formData.value.name,
      barcode: formData.value.barcode,
      zone: formData.value.zone,
      description: formData.value.description,
      available: formData.value.available,
    }

    if (isEditing.value) {
      await locationApi.update(formData.value.id, payload)
      toast.add({ severity: 'success', summary: 'Success', detail: 'Location updated', life: 3000 })
    } else {
      await locationApi.create(payload)
      toast.add({ severity: 'success', summary: 'Success', detail: 'Location created', life: 3000 })
    }

    dialogVisible.value = false
    await loadLocations()
  } catch (error) {
    showError(isEditing.value ? 'Update failed' : 'Creation failed', error)
  } finally {
    actionLoading.value = false
  }
}

const confirmDeleteSelected = () => {
  confirm.require({
    message: `Delete ${selectedLocations.value.length} selected location(s)?`,
    header: 'Delete Selected Locations',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: deleteSelectedLocations,
  })
}

const deleteSelectedLocations = async () => {
  actionLoading.value = true
  try {
    await Promise.all(selectedLocations.value.map((location) => locationApi.delete(location.id)))
    toast.add({ severity: 'success', summary: 'Deleted', detail: `${selectedLocations.value.length} location(s) deleted.`, life: 3000 })
    selectedLocations.value = []
    await loadLocations()
  } catch (error) {
    showError('Deletion failed', error)
  } finally {
    actionLoading.value = false
  }
}

const showError = (summary, error) => {
  const detail = error.response?.data?.message || error.response?.data?.error || error.message || 'An error occurred'
  toast.add({ severity: 'error', summary, detail, life: 5000 })
}

onMounted(() => {
  loadLocations()
})
</script>
