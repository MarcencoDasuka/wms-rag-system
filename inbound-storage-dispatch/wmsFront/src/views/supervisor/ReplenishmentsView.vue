<template>
  <div class="p-6">
    <ConfirmDialog />

    <AppDataTable
      v-model:selection="selectedReplenishments"
      :value="replenishments"
      :loading="loading"
      :filterFields="replenishmentFilterFields"
      :editMode="editMode ? 'cell' : null"
      paginator
      :rows="10"
      stripedRows
      class="p-datatable-sm"
      dataKey="id"
      emptyMessage="No replenishment tasks found."
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
          @click="loadData"
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
          label="Edit Selected"
          icon="pi pi-pencil"
          severity="warning"
          outlined
          :disabled="selectedReplenishments.length !== 1"
          @click="openEditDialog(selectedReplenishments[0])"
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
          label="Cancel Selected"
          icon="pi pi-ban"
          severity="secondary"
          outlined
          :disabled="!cancelableSelectedReplenishments.length || hasPendingChanges"
          @click="confirmCancelSelected"
        />

        <Button
          v-if="editMode"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          outlined
          :disabled="!deletableSelectedReplenishments.length || hasPendingChanges"
          @click="confirmDeleteSelected"
        />

        <span v-if="editMode" class="app-muted text-sm">
          {{ selectedReplenishments.length }} selected
        </span>
      </template>

      <Column v-if="editMode" selectionMode="multiple" headerStyle="width: 3rem" />

      <Column field="logicId" header="Logic ID" sortable filter>
        <template #body="slotProps">
          <span>{{ slotProps.data.logicId || `REPL-${slotProps.data.id}` }}</span>
        </template>
      </Column>

      <Column field="productName" header="Product" sortable filter>
        <template #body="slotProps">
          <ProductLink
            :product-id="slotProps.data.productId"
            :name="slotProps.data.productName"
            class="font-semibold"
          />
        </template>
      </Column>

      <Column field="requestedQuantity" header="Requested Qty" sortable filter>
        <template #body="slotProps">
          <span class="text-primary font-bold text-base">{{
              slotProps.data.requestedQuantity
            }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputNumber v-model="data[field]" :min="1" autofocus class="w-full" />
        </template>
      </Column>

      <Column field="destinationLocationId" filterField="destinationLocationLabel" header="Destination" sortable filter>
        <template #body="slotProps">
          <span class="app-subtitle font-semibold">{{
              slotProps.data.destinationLocationLabel
            }}</span>
        </template>
        <template #editor="{ data, field }">
          <Dropdown
            v-model="data[field]"
            :options="locations"
            optionLabel="barcode"
            optionValue="id"
            filter
            autofocus
            class="w-full"
          />
        </template>
      </Column>

      <Column field="status" filterField="formattedStatus" header="Status" sortable filter>
        <template #body="slotProps">
          <Tag
            :severity="getStatusSeverity(slotProps.data.status)"
            :value="slotProps.data.formattedStatus"
          />
        </template>
      </Column>

      <Column field="assignedOperatorName" header="Assigned Operator" filter>
        <template #body="slotProps">
          <Dropdown
            v-model="assignmentByTaskId[slotProps.data.taskId]"
            :options="operators"
            optionLabel="username"
            optionValue="id"
            placeholder="Select operator"
            filter
            class="w-full"
            :disabled="isAssignmentLocked(slotProps.data) || editMode"
            @change="
              assignReplenishment(slotProps.data.id, assignmentByTaskId[slotProps.data.taskId])
            "
          />
        </template>
      </Column>

      <Column header="Transport Unit">
        <template #body="{ data }">
          <span class="font-mono">{{ data.transportUnitBarcode || '-' }}</span>
        </template>
      </Column>

      <Column field="formattedCreatedAt" header="Created" sortable filter>
        <template #body="slotProps">
          <span class="app-muted text-sm">{{ slotProps.data.formattedCreatedAt }}</span>
        </template>
      </Column>
    </AppDataTable>

    <Dialog
      v-model:visible="createDialogVisible"
      header="Create Replenishment Task"
      :modal="true"
      class="w-full max-w-md"
    >
      <div class="flex flex-col gap-4 mt-2">
        <div class="flex flex-col gap-2">
          <label for="product" class="app-subtitle font-medium">Product</label>
          <Dropdown
            id="product"
            v-model="newReplenishment.productId"
            :options="products"
            optionLabel="name"
            optionValue="id"
            placeholder="Select a Product"
            filter
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="quantity" class="app-subtitle font-medium">Requested Quantity</label>
          <InputNumber
            id="quantity"
            v-model="newReplenishment.requestedQuantity"
            :min="1"
            showButtons
            placeholder="Enter quantity"
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="location" class="app-subtitle font-medium">Destination Location</label>
          <Dropdown
            id="location"
            v-model="newReplenishment.destinationLocationId"
            :options="locations"
            optionLabel="barcode"
            optionValue="id"
            placeholder="Select location"
            filter
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          text
          severity="secondary"
          @click="createDialogVisible = false"
        />
        <Button
          label="Create"
          icon="pi pi-check"
          severity="success"
          :loading="actionLoading"
          @click="handleCreate"
          :disabled="!isCreateFormValid"
        />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="editDialogVisible"
      header="Update Replenishment Task"
      :modal="true"
      class="w-full max-w-md"
    >
      <div class="flex flex-col gap-4 mt-2">
        <div class="flex flex-col gap-2">
          <label class="app-muted font-medium">Product (Read Only)</label>
          <InputText
            :value="getProductName(editingReplenishment.productId)"
            disabled
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="editQuantity" class="app-subtitle font-medium">Requested Quantity</label>
          <InputNumber
            id="editQuantity"
            v-model="editingReplenishment.requestedQuantity"
            :min="1"
            showButtons
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="editLocation" class="app-subtitle font-medium">Destination Location</label>
          <Dropdown
            id="editLocation"
            v-model="editingReplenishment.destinationLocationId"
            :options="locations"
            optionLabel="barcode"
            optionValue="id"
            filter
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          text
          severity="secondary"
          @click="editDialogVisible = false"
        />
        <Button
          label="Save Changes"
          icon="pi pi-check"
          severity="warning"
          :loading="actionLoading"
          @click="handleUpdate"
        />
      </template>
    </Dialog>

    <UploadFile
      v-model:visible="importDialogVisible"
      :apiCall="handleImport"
      @success="loadData"
      xlsx-template-path="/templates/replenishment_template.xlsx"
      csv-template-path="/templates/replenishment_template.csv"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useRoute } from 'vue-router'

import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import ConfirmDialog from 'primevue/confirmdialog'

import { replenishmentApi } from '@/api/replenishmentApi'
import { inventoryApi } from '@/api/inventoryApi'
import { userApi } from '@/api/userApi'
import { productApi } from '@/api/productApi'
import UploadFile from '@/components/UploadFile.vue'

const route = useRoute()
const importDialogVisible = ref(false)
const toast = useToast()
const confirm = useConfirm()

const replenishments = ref([])
const originalReplenishments = ref([])
const selectedReplenishments = ref([])
const modifiedReplenishmentIds = ref(new Set())

const products = ref([])
const locations = ref([])
const operators = ref([])

const loading = ref(false)
const actionLoading = ref(false)
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const editMode = ref(false)

const filters = ref({ productId: null, destinationLocationId: null, status: null })

const hasSelection = computed(() => selectedReplenishments.value.length > 0)

const isUniformSelection = computed(() => {
  if (!hasSelection.value) return false
  const firstStatus = selectedReplenishments.value[0].status
  return selectedReplenishments.value.every((task) => task.status === firstStatus)
})

const formatString = (str) => {
  if (!str) return '';
  return String(str).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

const cloneRows = (items) => JSON.parse(JSON.stringify(items || []))
const hasPendingChanges = computed(() => modifiedReplenishmentIds.value.size > 0)

const deletableSelectedReplenishments = computed(() =>
  selectedReplenishments.value.filter((task) => task.status === 'CREATED'),
)

const cancelableSelectedReplenishments = computed(() =>
  selectedReplenishments.value.filter((task) => !['COMPLETED', 'CANCELED'].includes(task.status)),
)

const handleImport = async (formData) => {
  if (!(formData instanceof FormData)) {
    throw new Error('Expected FormData')
  }
  return replenishmentApi.importReplenishments(formData)
}

const assignmentByTaskId = ref({})

const replenishmentFilterFields = [
  { field: 'logicId', label: 'Logic ID' },
  { field: 'productName', label: 'Product' },
  { field: 'requestedQuantity', label: 'Requested Qty' },
  { field: 'destinationLocationLabel', label: 'Destination' },
  { field: 'formattedStatus', label: 'Status' },
  { field: 'assignedOperatorName', label: 'Operator' },
  { field: 'formattedCreatedAt', label: 'Created' },
]

const newReplenishment = ref({
  productId: null,
  requestedQuantity: null,
  destinationLocationId: null,
})

const editingReplenishment = ref({
  id: null,
  taskId: null,
  productId: null,
  requestedQuantity: null,
  destinationLocationId: null,
})

const isCreateFormValid = computed(
  () =>
    newReplenishment.value.productId &&
    newReplenishment.value.requestedQuantity > 0 &&
    newReplenishment.value.destinationLocationId,
)

const getErrorMessage = (error) =>
  error.response?.data?.message || error.response?.data?.error || error.message || 'Request failed.'

const getProductName = (id) => products.value.find((p) => p.id === id)?.name || `Product #${id}`

const getLocationName = (id) =>
  locations.value.find((l) => l.id === id)?.barcode || `Location #${id}`

const getOperatorName = (id) => operators.value.find((o) => o.id === id)?.username || ''

const formatDate = (ts) => {
  if (!ts) return '-'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(ts))
}

const getStatusSeverity = (status) => {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'PARTIALLY_COMPLETED':
      return 'warning'
    case 'CANCELED':
      return 'secondary'
    case 'IN_PROGRESS':
    case 'ASSIGNED':
      return 'warning'
    case 'CREATED':
      return 'info'
    default:
      return 'danger'
  }
}

const isAssignmentLocked = (task) =>
  ['ASSIGNED', 'IN_PROGRESS', 'PARTIALLY_COMPLETED', 'COMPLETED', 'CANCELED', 'CANCELLED'].includes(
    task.status,
  )

const loadData = async () => {
  loading.value = true
  try {
    const [productsRes, locsRes, usersRes] = await Promise.all([
      productApi.getAllProducts(),
      inventoryApi.getLocations(),
      userApi.getAll(),
    ])

    products.value = productsRes.data
    locations.value = locsRes.data.filter((l) => l.available !== false && l.zone === 'PICKING')
    operators.value = (usersRes.data || []).filter((u) => u.userRole === 'ROLE_OPERATOR')

    await applyFilters()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Load Failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
}

const applyFilters = async () => {
  loading.value = true
  try {
    const cleanFilters = Object.fromEntries(
      Object.entries(filters.value).filter(([, v]) => v !== null && v !== ''),
    )

    if (route.query.id) {
      cleanFilters.id = Number(route.query.id)
    }

    const res = await replenishmentApi.filter(cleanFilters)

    replenishments.value = res.data.map((task) => ({
      ...task,
      productName: getProductName(task.productId),
      assignedOperatorName: getOperatorName(task.assignedOperatorId),
      destinationLocationLabel: getLocationName(task.destinationLocationId),
      formattedStatus: formatString(task.status),
      formattedCreatedAt: formatDate(task.createdAt)
    }))

    originalReplenishments.value = cloneRows(replenishments.value)
    modifiedReplenishmentIds.value = new Set()

    assignmentByTaskId.value = Object.fromEntries(
      replenishments.value.map((task) => [task.taskId, task.assignedOperatorId || null]),
    )
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Filtering Failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  newReplenishment.value = {
    productId: null,
    requestedQuantity: null,
    destinationLocationId: null,
  }
  createDialogVisible.value = true
}

const toggleEditMode = () => {
  if (!editMode.value) {
    editMode.value = true
    selectedReplenishments.value = []
    return
  }

  if (hasPendingChanges.value) {
    confirmResetChanges(() => {
      editMode.value = false
    })
    return
  }

  editMode.value = false
  selectedReplenishments.value = []
}

const openEditDialog = (data) => {
  editingReplenishment.value = { ...data }
  editDialogVisible.value = true
}

const handleUpdate = async () => {
  actionLoading.value = true
  try {
    const payload = {
      productId: editingReplenishment.value.productId,
      requestedQuantity: editingReplenishment.value.requestedQuantity,
      destinationLocationId: editingReplenishment.value.destinationLocationId,
    }

    await replenishmentApi.update(editingReplenishment.value.id, payload)

    toast.add({
      severity: 'success',
      summary: 'Updated',
      detail: 'Replenishment updated successfully.',
      life: 3000,
    })

    editDialogVisible.value = false
    await loadData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

const normalizeRepl = (repl) => ({
  requestedQuantity: repl.requestedQuantity,
  destinationLocationId: repl.destinationLocationId,
})

const onCellEditComplete = ({ data, newValue, field }) => {
  if (!editMode.value) return
  if (newValue !== undefined) {
    data[field] = newValue
  }

  if (field === 'destinationLocationId') {
    data.destinationLocationLabel = getLocationName(data.destinationLocationId)
  }

  const original = originalReplenishments.value.find((item) => item.id === data.id)
  if (!original) return

  const nextIds = new Set(modifiedReplenishmentIds.value)
  if (JSON.stringify(normalizeRepl(data)) !== JSON.stringify(normalizeRepl(original))) {
    nextIds.add(data.id)
  } else {
    nextIds.delete(data.id)
  }
  modifiedReplenishmentIds.value = nextIds
}

const confirmSubmitChanges = () => {
  confirm.require({
    message: `Submit ${modifiedReplenishmentIds.value.size} changed task(s)?`,
    header: 'Submit Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-success',
    accept: submitQuantityChanges,
  })
}

const submitQuantityChanges = async () => {
  actionLoading.value = true
  try {
    const changedTasks = replenishments.value.filter((repl) =>
      modifiedReplenishmentIds.value.has(repl.id),
    )

    await Promise.all(
      changedTasks.map((repl) =>
        replenishmentApi.update(repl.id, {
          taskId: repl.taskId,
          productId: repl.productId,
          requestedQuantity: repl.requestedQuantity,
          status: repl.status,
          destinationLocationId: repl.destinationLocationId,
        }),
      ),
    )

    toast.add({
      severity: 'success',
      summary: 'Tasks updated',
      detail: `${changedTasks.length} task(s) updated.`,
      life: 3000,
    })
    editMode.value = false
    selectedReplenishments.value = []
    await loadData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

const confirmResetChanges = (afterReset) => {
  confirm.require({
    message: 'Discard all unsaved changes?',
    header: 'Reset Unsaved Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-warning',
    accept: () => {
      replenishments.value = cloneRows(originalReplenishments.value)

      replenishments.value.forEach(repl => {
        repl.destinationLocationLabel = getLocationName(repl.destinationLocationId)
        repl.formattedStatus = formatString(repl.status)
      })

      modifiedReplenishmentIds.value = new Set()
      selectedReplenishments.value = []
      toast.add({
        severity: 'info',
        summary: 'Changes reset',
        detail: 'Unsaved changes were discarded.',
        life: 2500,
      })
      if (typeof afterReset === 'function') afterReset()
    },
  })
}

const handleCreate = async () => {
  actionLoading.value = true
  try {
    await replenishmentApi.create(newReplenishment.value)
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Replenishment task created.',
      life: 3000,
    })
    createDialogVisible.value = false
    await loadData()
  } finally {
    actionLoading.value = false
  }
}

const assignReplenishment = async (id, operatorId) => {
  if (!operatorId) return
  actionLoading.value = true
  try {
    await replenishmentApi.assign(id, operatorId)
    toast.add({
      severity: 'success',
      summary: 'Assigned',
      detail: `Replenishment #${id} assigned to operator.`,
      life: 3000,
    })
    await loadData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Assign failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

const confirmDeleteSelected = () => {
  confirm.require({
    message: `Permanently delete ${deletableSelectedReplenishments.value.length} selected task(s)?`,
    header: 'Delete Selected',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: deleteSelectedReplenishments,
  })
}

const deleteSelectedReplenishments = async () => {
  loading.value = true
  try {
    await Promise.allSettled(
      deletableSelectedReplenishments.value.map((t) => replenishmentApi.delete(t.id)),
    )
    selectedReplenishments.value = []
    await loadData()
  } finally {
    loading.value = false
  }
}

const confirmCancelSelected = () => {
  confirm.require({
    message: `Cancel ${cancelableSelectedReplenishments.value.length} selected task(s)?`,
    header: 'Cancel Selected',
    icon: 'pi pi-info-circle',
    acceptClass: 'p-button-secondary',
    accept: cancelSelectedReplenishments,
  })
}

const cancelSelectedReplenishments = async () => {
  loading.value = true
  try {
    await Promise.allSettled(
      cancelableSelectedReplenishments.value.map((t) => replenishmentApi.cancel(t.id)),
    )
    selectedReplenishments.value = []
    await loadData()
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query.id,
  () => {
    applyFilters()
  },
)

onMounted(loadData)
</script>
