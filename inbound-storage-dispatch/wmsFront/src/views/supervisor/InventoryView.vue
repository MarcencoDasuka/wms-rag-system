<template>
  <div class="p-6">
    <ConfirmDialog />

    <Card v-if="lastAdjustmentResult" class="mb-4">
      <template #title>Latest stock adjustment</template>
      <template #content>
        <div class="flex flex-col gap-2 text-sm">
          <div>{{ lastAdjustmentResult.message }}</div>
          <div class="flex flex-wrap gap-2">
            <Tag
              :severity="lastAdjustmentResult.reallocationSucceeded ? 'success' : 'warning'"
              :value="
                lastAdjustmentResult.reallocationSucceeded
                  ? 'Reallocation succeeded'
                  : 'Reallocation incomplete'
              "
            />
            <Tag
              v-if="lastAdjustmentResult.partialShortageCreated"
              severity="warning"
              value="Partial completion created"
            />
            <Tag
              v-if="lastAdjustmentResult.orderCancelled"
              severity="danger"
              value="Order cancelled"
            />
          </div>
          <div v-if="lastAdjustmentResult.affectedOrders?.length" class="flex flex-col gap-1">
            <div class="font-semibold">Affected orders</div>
            <div class="flex flex-wrap gap-2">
              <Tag
                v-for="order in lastAdjustmentResult.affectedOrders"
                :key="order.orderId"
                :severity="getOrderSeverity(order.status)"
                :value="`${order.orderNumber} · ${order.status} · ${order.shortageLines}/${order.totalLines} lines`"
              />
            </div>
          </div>
        </div>
      </template>
    </Card>

    <AppDataTable
      v-model:selection="selectedStockItems"
      :value="stockItems"
      :loading="loading"
      :rowClass="stockRowClass"
      :filterFields="inventoryFilterFields"
      :editMode="editMode ? 'cell' : null"
      paginator
      :rows="10"
      stripedRows
      class="p-datatable-sm inventory-table"
      dataKey="id"
      emptyMessage="No inventory stock found."
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
          @click="loadInventoryData"
        />
        <Button
          v-if="canManageStock"
          label="Import"
          icon="pi pi-file-import"
          severity="info"
          @click="importDialogVisible = true"
        />
        <Button
          v-if="canManageStock"
          label="Add Stock"
          icon="pi pi-plus"
          severity="success"
          @click="openAddDialog"
        />
        <Button
          v-if="canManageStock"
          :label="editMode ? 'Exit Edit' : 'Edit'"
          icon="pi pi-pencil"
          severity="warning"
          outlined
          @click="toggleEditMode"
        />
        <Button
          v-if="editMode && canManageStock"
          label="Submit"
          icon="pi pi-check"
          severity="success"
          :disabled="!hasPendingChanges"
          :loading="actionLoading"
          @click="confirmSubmitChanges"
        />
        <Button
          v-if="editMode && canManageStock"
          label="Reset"
          icon="pi pi-refresh"
          severity="secondary"
          outlined
          :disabled="!hasPendingChanges"
          @click="confirmResetChanges"
        />
        <Button
          v-if="editMode && canManageStock"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          outlined
          :disabled="!deletableSelectedStockItems.length || hasPendingChanges"
          @click="confirmDeleteSelected"
        />
        <span v-if="editMode && canManageStock" class="app-muted text-sm"
          >{{ selectedStockItems.length }} selected</span
        >
      </template>
      <Column
        v-if="editMode && canManageStock"
        selectionMode="multiple"
        headerStyle="width: 3rem"
      />
      <Column field="productName" header="Product" sortable filter>
        <template #body="{ data }">
          <ProductLink
            :product-id="data.productId"
            :barcode="data.barcode"
            :name="data.productName"
            :disabled="editMode"
            class="font-semibold"
          />
        </template>
      </Column>

      <Column field="barcode" header="Barcode" sortable filter></Column>
      <Column field="locationBarcode" header="Location" sortable filter></Column>
      <Column field="quantity" header="Total Qty" sortable filter>
        <template #body="{ data }">
          <span class="app-title font-bold text-base">{{ data.quantity }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputNumber v-model="data[field]" class="w-full" :min="0" autofocus />
        </template>
      </Column>

      <Column field="reservedQuantity" header="Reserved" sortable filter>
        <template #body="{ data }">
          <span
            class="font-bold text-base"
            :class="data.reservedQuantity > 0 ? 'text-orange-500' : 'app-muted'"
          >
            {{ data.reservedQuantity }}
          </span>
        </template>
      </Column>

      <Column field="availableQuantity" header="Available" sortable filter>
        <template #body="{ data }">
          <span
            class="font-bold text-base"
            :class="
              data.availableQuantity <= 0
                ? 'text-red-500'
                : data.availableQuantity < 10
                  ? 'text-yellow-500'
                  : 'text-green-500'
            "
          >
            {{ data.availableQuantity }}
          </span>
        </template>
      </Column>

      <Column
        field="manufactureDate"
        filterField="formattedManufactureDate"
        header="Manufactured"
        sortable
        filter
      >
        <template #body="{ data }">
          <span class="app-muted text-sm">{{ data.formattedManufactureDate }}</span>
        </template>
        <template #editor="{ data, field }">
          <DatePicker v-model="data[field]" :maxDate="today" showClear dateFormat="yy-mm-dd" />
        </template>
      </Column>

      <Column
        field="expirationDate"
        filterField="formattedExpirationDate"
        header="Expires"
        sortable
        filter
      >
        <template #body="{ data }">
          <span class="app-muted text-sm">{{ data.formattedExpirationDate }}</span>
        </template>
        <template #editor="{ data, field }">
          <DatePicker v-model="data[field]" :minDate="today" showClear dateFormat="yy-mm-dd" />
        </template>
      </Column>
    </AppDataTable>

    <StockActionDialog
      v-model:visible="dialogVisible"
      :mode="dialogMode"
      :selectedStock="selectedStock"
      :products="products"
      :locations="locations"
      :loading="actionLoading"
      @submit="handleStockAction"
    />
    <UploadFile
      v-model:visible="importDialogVisible"
      :apiCall="handleImport"
      @success="loadInventoryData"
      xlsx-template-path="/templates/stock_template.xlsx"
      csv-template-path="/templates/stock_template.csv"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Column from 'primevue/column'
import ConfirmDialog from 'primevue/confirmdialog'
import Card from 'primevue/card'
import InputNumber from 'primevue/inputnumber'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'

import StockActionDialog from '@/components/inventory/StockActionDialog.vue'
import { inventoryApi } from '@/api/inventoryApi'
import { useAuthStore } from '@/stores/auth'
import UploadFile from '@/components/UploadFile.vue'

const importDialogVisible = ref(false)
const toast = useToast()
const confirm = useConfirm()
const authStore = useAuthStore()

const stockItems = ref([])
const originalStockItems = ref([])
const selectedStockItems = ref([])
const products = ref([])
const locations = ref([])
const loading = ref(false)
const actionLoading = ref(false)
const editMode = ref(false)
const modifiedStockIds = ref(new Set())
const dialogVisible = ref(false)
const dialogMode = ref('add')
const selectedStock = ref(null)
const lastAdjustmentResult = ref(null)
const canManageStock = computed(() => authStore.hasAnyRole(['ROLE_SUPERVISOR', 'ROLE_DEV']))

const today = new Date()
today.setHours(0, 0, 0, 0)

function formatDate(date) {
  return new Intl.DateTimeFormat('en-CA').format(new Date(date))
}

const inventoryFilterFields = [
  { field: 'productName', label: 'Product' },
  { field: 'barcode', label: 'Barcode' },
  { field: 'locationBarcode', label: 'Location' },
  { field: 'quantity', label: 'Total Qty' },
  { field: 'reservedQuantity', label: 'Reserved' },
  { field: 'availableQuantity', label: 'Available' },
  { field: 'formattedManufactureDate', label: 'Manufactured' },
  { field: 'formattedExpirationDate', label: 'Expires' },
]

const cloneRows = (items) => JSON.parse(JSON.stringify(items || []))
const hasPendingChanges = computed(() => modifiedStockIds.value.size > 0)
const deletableSelectedStockItems = computed(() => selectedStockItems.value)

const handleImport = async (formData) => {
  if (!(formData instanceof FormData)) throw new Error('Expected FormData')
  return inventoryApi.importStocks(formData)
}

const getErrorMessage = (error) => {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    'Request failed.'
  )
}

const currentUserId = () => {
  if (authStore.user?.id) return authStore.user.id
  const storedUserId = localStorage.getItem('user_id')
  if (storedUserId) return Number(storedUserId)
  if (authStore.role === 'ROLE_DEV') return 1
  if (authStore.role === 'ROLE_SUPERVISOR') return 2
  if (authStore.role === 'ROLE_OPERATOR') return 3
  return null
}

const loadInventoryData = async () => {
  loading.value = true
  try {
    const [stockResponse, productsResponse, locationsResponse] = await Promise.all([
      inventoryApi.getAllStock(),
      inventoryApi.getProducts(),
      inventoryApi.getLocations(),
    ])

    stockItems.value = stockResponse.data.map((stock) => ({
      ...stock,
      quantity: stock.quantity || 0,
      reservedQuantity: stock.reservedQuantity || 0,
      availableQuantity:
        stock.availableQuantity ?? (stock.quantity || 0) - (stock.reservedQuantity || 0),
      formattedManufactureDate: stock.manufactureDate ? formatDate(stock.manufactureDate) : '',
      formattedExpirationDate: stock.expirationDate ? formatDate(stock.expirationDate) : '',
    }))

    originalStockItems.value = cloneRows(stockItems.value)
    modifiedStockIds.value = new Set()
    products.value = productsResponse.data

    locations.value = locationsResponse.data.filter(
      (location) => location.available !== false && location.zone !== 'DISPATCH',
    )
    selectedStockItems.value = []
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Inventory load failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  selectedStock.value = null
  dialogMode.value = 'add'
  dialogVisible.value = true
}

const toggleEditMode = () => {
  if (!editMode.value) {
    editMode.value = true
    selectedStockItems.value = []
    return
  }

  if (hasPendingChanges.value) {
    confirmResetChanges(() => {
      editMode.value = false
    })
    return
  }

  editMode.value = false
  selectedStockItems.value = []
}

const stockRowClass = (stock) => ({
  'app-row-modified': modifiedStockIds.value.has(stock.id),
})

const normalizeStock = (stock) => ({
  quantity: stock.quantity ?? 0,
  manufactureDate: stock.manufactureDate || null,
  expirationDate: stock.expirationDate || null,
})

const refreshModifiedState = (stock) => {
  const original = originalStockItems.value.find((item) => item.id === stock.id)
  if (!original) return

  const nextIds = new Set(modifiedStockIds.value)
  if (JSON.stringify(normalizeStock(stock)) !== JSON.stringify(normalizeStock(original)))
    nextIds.add(stock.id)
  else nextIds.delete(stock.id)
  modifiedStockIds.value = nextIds
}

const onCellEditComplete = ({ data, newValue, field }) => {
  if (!editMode.value) return
  if (newValue !== undefined) {
    const normalizedValue = typeof newValue === 'string' ? newValue.trim() : newValue
    data[field] = normalizedValue === '' ? null : normalizedValue
  }

  if (field === 'manufactureDate') {
    data.formattedManufactureDate = data.manufactureDate ? formatDate(data.manufactureDate) : '-'
  }
  if (field === 'expirationDate') {
    data.formattedExpirationDate = data.expirationDate ? formatDate(data.expirationDate) : '-'
  }
  if (field === 'quantity') {
    data.availableQuantity = data.quantity - data.reservedQuantity
  }

  refreshModifiedState(data)
}

const confirmSubmitChanges = () => {
  confirm.require({
    message: `Submit ${modifiedStockIds.value.size} changed stock item(s)?`,
    header: 'Submit Inventory Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-success',
    accept: submitQuantityChanges,
  })
}

const submitQuantityChanges = async () => {
  const userId = currentUserId()
  if (!userId) {
    toast.add({
      severity: 'error',
      summary: 'Missing user',
      detail: 'User id is required for stock changes.',
      life: 4000,
    })
    return
  }

  actionLoading.value = true
  try {
    const changedStocks = stockItems.value.filter((stock) => modifiedStockIds.value.has(stock.id))

    const invalidStock = changedStocks.find(
      (stock) => stock.quantity === null || stock.quantity < 0,
    )
    if (invalidStock) {
      toast.add({
        severity: 'error',
        summary: 'Validation failed',
        detail: 'Quantity must be zero or greater.',
        life: 4000,
      })
      actionLoading.value = false
      return
    }

    const invalidDateStock = changedStocks.find((stock) => {
      const today = new Date()
      today.setHours(0, 0, 0, 0)

      const mfg = stock.manufactureDate ? new Date(stock.manufactureDate) : null
      const exp = stock.expirationDate ? new Date(stock.expirationDate) : null

      if (mfg && mfg > today) return true
      if (exp && exp < today) return true
      if (mfg && exp && exp <= mfg) return true

      return false
    })

    if (invalidDateStock) {
      toast.add({
        severity: 'error',
        summary: 'Validation failed',
        detail:
          'Invalid dates detected. Manufacture cannot be in the future, and expiration must be strictly after manufacture.',
        life: 5000,
      })
      actionLoading.value = false
      return
    }

    await Promise.all(
      changedStocks.map((stock) =>
        inventoryApi.adjustStock(stock.id, {
          newQuantity: stock.quantity,
          manufactureDate: stock.manufactureDate || null,
          expirationDate: stock.expirationDate || null,
          userId,
          reason: 'INVENTORY_MISMATCH',
          comment: null,
        }),
      ),
    )

    toast.add({
      severity: 'success',
      summary: 'Inventory updated',
      detail: `${changedStocks.length} stock item(s) updated.`,
      life: 3000,
    })
    editMode.value = false
    selectedStockItems.value = []
    await loadInventoryData()
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
    message: 'Discard all unsaved inventory changes?',
    header: 'Reset Unsaved Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-warning',
    accept: () => {
      stockItems.value = cloneRows(originalStockItems.value)
      modifiedStockIds.value = new Set()
      selectedStockItems.value = []
      toast.add({
        severity: 'info',
        summary: 'Changes reset',
        detail: 'Unsaved inventory changes were discarded.',
        life: 2500,
      })
      if (typeof afterReset === 'function') afterReset()
    },
  })
}

const confirmDeleteSelected = () => {
  confirm.require({
    message: `Delete ${deletableSelectedStockItems.value.length} selected stock item(s)? This action cannot be undone.`,
    header: 'Delete Selected Stock',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: deleteSelectedStocks,
  })
}

const deleteSelectedStocks = async () => {
  actionLoading.value = true
  try {
    await Promise.all(
      deletableSelectedStockItems.value.map((stock) => inventoryApi.deleteStock(stock.id)),
    )
    toast.add({
      severity: 'success',
      summary: 'Stock deleted',
      detail: `${deletableSelectedStockItems.value.length} stock item(s) deleted.`,
      life: 3000,
    })
    selectedStockItems.value = []
    await loadInventoryData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

const handleStockAction = (payload) => {
  submitAction(payload)
}

const submitAction = async (payload) => {
  const userId = currentUserId()
  if (!userId) {
    toast.add({
      severity: 'error',
      summary: 'Missing user',
      detail: 'User id is required for stock changes.',
      life: 4000,
    })
    return
  }

  const invalidDatesStock =
    payload.manufactureDate &&
    payload.expirationDate &&
    new Date(payload.manufactureDate) >= new Date(payload.expirationDate)

  if (invalidDatesStock) {
    toast.add({
      severity: 'error',
      summary: 'Validation failed',
      detail: 'Manufacture date must be before expiration date.',
      life: 4000,
    })
    return
  }

  actionLoading.value = true
  try {
    await inventoryApi.addStock({ ...payload, userId })
    toast.add({
      severity: 'success',
      summary: 'Stock added',
      detail: 'Inventory stock was added.',
      life: 3000,
    })
    dialogVisible.value = false
    selectedStockItems.value = []
    await loadInventoryData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Stock action failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

const getOrderSeverity = (status) => {
  if (status === 'SHORTAGE' || status === 'PARTIALLY_COMPLETED') return 'warning'
  if (status === 'CANCELED' || status === 'CANCELLED') return 'danger'
  if (status === 'IN_PROGRESS' || status === 'ASSIGNED' || status === 'ALLOCATED') return 'info'
  return 'secondary'
}

onMounted(loadInventoryData)
</script>

<style scoped>
.inventory-table :deep(.p-datatable-tbody > tr > td) {
  height: 3.25rem;
}
</style>
