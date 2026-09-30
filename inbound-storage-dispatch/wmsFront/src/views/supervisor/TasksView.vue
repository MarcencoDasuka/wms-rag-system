<template>
  <div class="p-6">
    <Toast />
    <ConfirmDialog />

    <AppDataTable
      v-model:selection="selectedTasks"
      :value="tasks"
      :loading="loading"
      :filterFields="taskFilterFields"
      paginator
      :rows="10"
      stripedRows
      class="p-datatable-sm"
      dataKey="id"
      emptyMessage="No replenishment tasks found."
    >
      <template #toolbar>
        <Button icon="pi pi-refresh" size="small" severity="secondary" outlined :loading="loading" aria-label="Refresh" @click="loadData" />
        <Button label="Create" icon="pi pi-plus" severity="success" @click="openCreateDialog" />
        <Button :label="editMode ? 'Exit Edit' : 'Edit'" icon="pi pi-pencil" severity="warning" outlined @click="toggleEditMode" />
        <Button v-if="editMode" label="Delete Selected" icon="pi pi-trash" severity="danger" outlined :disabled="!deletableSelectedTasks.length" @click="confirmDeleteSelected" />
        <span v-if="editMode" class="app-muted text-sm">{{ selectedTasks.length }} selected</span>
      </template>

          <Column v-if="editMode" selectionMode="multiple" headerStyle="width: 3rem" />
          <Column header="Product" sortable>
            <template #body="slotProps">
              <div class="flex flex-col">
                <ProductLink
                  :product-id="slotProps.data.productId"
                  :barcode="getProductBarcode(slotProps.data.productId)"
                  :name="getProductName(slotProps.data.productId)"
                  class="font-semibold"
                />
                <span class="text-xs text-gray-400 font-mono">Barcode: {{ getProductBarcode(slotProps.data.productId) }}</span>
              </div>
            </template>
          </Column>

          <Column field="requestedQuantity" header="Requested Qty" sortable filter>
            <template #body="slotProps">
              <span class="text-blue-400 font-bold">{{ slotProps.data.requestedQuantity }}</span>
            </template>
          </Column>

          <Column header="Destination" sortable>
            <template #body="slotProps">
              <div class="flex flex-col">
                <span>{{ getLocationName(slotProps.data.destinationLocationId) }}</span>
                <span class="text-xs text-gray-400 font-mono">{{ getLocationCode(slotProps.data.destinationLocationId) }}</span>
              </div>
            </template>
          </Column>

          <Column field="status" header="Status" sortable filter>
            <template #body="slotProps">
              <Tag :severity="getStatusSeverity(slotProps.data.status)" :value="slotProps.data.status" />
            </template>
          </Column>

          <!-- Inline editing is intentionally not enabled here because replenishment tasks are backed by generated allocation allocations. -->
    </AppDataTable>

    <Dialog v-model:visible="dialogVisible" header="Create Replenishment Task" :modal="true" class="p-fluid w-full max-w-md">
      <div class="field mb-4">
        <label for="product" class="block text-sm font-medium mb-1">Product</label>
          <Dropdown
            id="product"
            v-model="newTask.productId"
            :options="products"
            optionLabel="name"
            optionValue="id"
            filter
            placeholder="Select a Product"
        />
      </div>

      <div class="field mb-4">
        <label for="quantity" class="block text-sm font-medium mb-1">Requested Quantity</label>
        <InputNumber
          id="quantity"
          v-model="newTask.requestedQuantity"
          :min="1"
          showButtons
          placeholder="Enter quantity"
        />
      </div>

      <div class="field mb-4">
        <label for="location" class="block text-sm font-medium mb-1">Destination Location</label>
          <Dropdown
          id="location"
          v-model="newTask.destinationLocationId"
          :options="locations"
          optionLabel="locationCode"
          optionValue="id"
          filter
          placeholder="Select Destination Zone"
        />
      </div>

      <template #footer>
        <Button label="Cancel" icon="pi pi-times" text @click="dialogVisible = false" />
        <Button label="Create Task" icon="pi pi-check" severity="success" :loading="actionLoading" @click="createTask" :disabled="!isFormValid" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import InputNumber from 'primevue/inputnumber'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'


import { replenishmentApi } from '@/api/replenishmentApi'
import { inventoryApi } from '@/api/inventoryApi'

const toast = useToast()
const confirm = useConfirm()

const tasks = ref([])
const selectedTasks = ref([])
const products = ref([])
const locations = ref([])

const loading = ref(false)
const actionLoading = ref(false)
const dialogVisible = ref(false)
const editMode = ref(false)
const taskFilterFields = [
  { field: 'productId', label: 'Product' },
  { field: 'requestedQuantity', label: 'Requested Qty' },
  { field: 'destinationLocationId', label: 'Destination' },
  { field: 'status', label: 'Status' }
]

const newTask = ref({
  productId: null,
  requestedQuantity: null,
  destinationLocationId: null
})

const isFormValid = computed(() => {
  return newTask.value.productId &&
    newTask.value.requestedQuantity > 0 &&
    newTask.value.destinationLocationId
})

const deletableSelectedTasks = computed(() => selectedTasks.value.filter((task) => task.status === 'CREATED'))

const getErrorMessage = (error) => {
  return error.response?.data?.message || error.response?.data?.error || error.message || 'Request failed.'
}

const getStatusSeverity = (status) => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'IN_PROGRESS' || status === 'ASSIGNED' || status === 'REQUIRES_REVALIDATION') return 'warning'
  if (status === 'CREATED') return 'info'
  return 'danger'
}

const getProductName = (id) => {
  const product = products.value.find(p => p.id === id)
  return product ? product.name : `Product #${id}`
}

const getProductBarcode = (id) => {
  const product = products.value.find((p) => p.id === id)
  return product ? product.barcode || product.sku || product.code || product.productCode || '-' : '-'
}

const getLocationName = (id) => {
  const loc = locations.value.find(l => l.id === id)
  return loc ? loc.name || loc.locationCode || loc.barcode : `Location #${id}`
}

const getLocationCode = (id) => {
  const loc = locations.value.find((l) => l.id === id)
  return loc ? loc.locationCode || loc.barcode || loc.code || loc.location || '-' : '-'
}

const loadData = async () => {
  loading.value = true
  try {
    const [tasksRes, productsRes, locsRes] = await Promise.all([
      replenishmentApi.getAll(),
      inventoryApi.getProducts(),
      inventoryApi.getLocations()
    ])

    tasks.value = tasksRes.data
    products.value = productsRes.data.map((product) => ({
      ...product,
      name: product.name || product.productName || '',
      sku: product.sku || product.barcode || product.code || product.productCode || ''
    }))
    locations.value = locsRes.data
      .filter(l => l.available !== false) // Only show available locations
      .map((location) => ({
        ...location,
        locationCode: location.locationCode || location.barcode || location.code || location.location || ''
      }))
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Load Failed', detail: getErrorMessage(error), life: 4000 })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  newTask.value = { productId: null, requestedQuantity: null, destinationLocationId: null }
  dialogVisible.value = true
}

const toggleEditMode = () => {
  editMode.value = !editMode.value
  selectedTasks.value = []
}

const createTask = async () => {
  actionLoading.value = true
  try {
    await replenishmentApi.create(newTask.value)
    toast.add({ severity: 'success', summary: 'Task Created', detail: 'Replenishment task has been created successfully.', life: 3000 })
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Creation Failed', detail: getErrorMessage(error), life: 5000 })
  } finally {
    actionLoading.value = false
  }
}

const confirmDeleteSelected = () => {
  confirm.require({
    message: `Delete ${deletableSelectedTasks.value.length} selected task(s)? Only CREATED tasks can be deleted.`,
    header: 'Delete Selected Tasks',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: deleteSelectedTasks
  })
}

const deleteSelectedTasks = async () => {
  loading.value = true
  try {
    await Promise.all(deletableSelectedTasks.value.map((task) => replenishmentApi.delete(task.id)))
    toast.add({ severity: 'success', summary: 'Deleted', detail: `${deletableSelectedTasks.value.length} task(s) deleted.`, life: 3000 })
    selectedTasks.value = []
    await loadData()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Deletion Failed', detail: getErrorMessage(error), life: 4000 })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>
