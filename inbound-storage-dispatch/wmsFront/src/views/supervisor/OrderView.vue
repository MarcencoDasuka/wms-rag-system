<template>
  <div class="p-6">
    <ConfirmDialog />

    <Message v-if="loadError" severity="error" class="mb-6" :closable="false">
      {{ loadError }}
    </Message>

    <AppDataTable
      v-model:selection="selectedOrders"
      :value="orders"
      :loading="loading"
      :filterFields="orderFilterFields"
      paginator
      :rows="10"
      :rowsPerPageOptions="[10, 25, 50]"
      v-model:expandedRows="expandedRows"
      stripedRows
      class="p-datatable-sm"
      dataKey="order.id"
      emptyMessage="No orders found."
    >
      <template #toolbar>
        <Button
          icon="pi pi-refresh"
          size="small"
          severity="secondary"
          outlined
          :loading="loading"
          aria-label="Refresh"
          @click="loadOrders"
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
          :disabled="selectedOrders.length !== 1 || !canEditSelected"
          @click="openEditDialog(selectedOrders[0])"
        />

        <Button
          v-if="editMode"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          outlined
          :disabled="!selectedOrders.length"
          @click="confirmDeleteSelectedOrders"
        />
        <span v-if="editMode" class="app-muted text-sm">{{ selectedOrders.length }} selected</span>
      </template>

      <Column v-if="editMode" selectionMode="multiple" headerStyle="width: 3rem" />

      <Column expander style="width: 3rem">
        <template #body="{ data, rowTogglerCallback }">
          <Button
            v-if="hasExpandableLines(data)"
            :icon="isExpanded(data) ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
            text
            size="small"
            class="order-expander-button"
            aria-label="Toggle order lines"
            @click="toggleOrderExpansion(data, rowTogglerCallback, $event)"
          />
        </template>
      </Column>

      <Column field="order.logicId" header="Logic ID" sortable filter />
      <Column field="order.locationLabel" header="Destination" sortable filter>
        <template #body="{ data }">
          {{ data.order.locationLabel }}
        </template>
      </Column>
      <Column header="Lines" style="width: 7rem">
        <template #body="{ data }">
          <Tag
            severity="info"
            :value="`${data.lines?.length || 0} line${(data.lines?.length || 0) === 1 ? '' : 's'}`"
          />
        </template>
      </Column>
      <Column field="order.formattedStatus" header="Status" sortable filter>
        <template #body="{ data }">
          <Tag
            :severity="getStatusSeverity(data.order.status || data.order.Status)"
            :value="data.order.formattedStatus"
          />
        </template>
      </Column>
      <Column field="order.assignedOperatorName" header="Assigned Operator" style="min-width: 14rem" filter>
        <template #body="{ data }">
          <Dropdown
            v-model="assignmentByOrderId[data.order.id]"
            :options="operators"
            optionLabel="username"
            optionValue="id"
            placeholder="Select operator"
            filter
            class="w-full"
            :disabled="isAssignmentLocked(data.order)"
            @change="assignOrderToOperator(data.order.id, assignmentByOrderId[data.order.id])"
          />
        </template>
      </Column>

      <Column field="order.transportUnitBarcode" header="Transport Unit" filter>
        <template #body="{ data }">
          <span class="font-mono font-semibold">{{ data.order.transportUnitBarcode || '-' }}</span>
        </template>
      </Column>

      <Column field="order.formattedCreatedAt" header="Created" sortable filter>
        <template #body="{ data }">
          {{ data.order.formattedCreatedAt }}
        </template>
      </Column>

      <template #expansion="{ data }">
        <div class="order-lines-expansion">
          <AppDataTable
            :value="data.lines"
            :filterFields="orderLineFilterFields"
            :showSearch="false"
            :showPaginator="false"
            class="p-datatable-sm order-lines-table"
            dataKey="orderLineId"
            emptyMessage="No order lines found."
          >
            <Column field="productId" header="Product" filter>
              <template #body="{ data: line }">
                <ProductLink
                  :product-id="line.productId"
                  :barcode="getProduct(line.productId)?.barcode"
                  :name="line.productName || getProduct(line.productId)?.name || '-'"
                  class="font-semibold"
                />
              </template>
            </Column>
            <Column field="requestedQuantity" header="Requested Qty" filter>
              <template #body="{ data: line }">
                <span class="font-semibold">{{ line.requestedQuantity ?? line.quantity ?? 0 }}</span>
              </template>
            </Column>
            <Column field="deliveredQuantity" header="Delivered Qty" filter>
              <template #body="{ data: line }">
                <span class="font-semibold">{{ line.deliveredQuantity ?? line.allocatedQuantity ?? 0 }}</span>
              </template>
            </Column>
            <Column field="formattedStatus" header="Status" filter>
              <template #body="{ data: line }">
                <Tag :severity="getStatusSeverity(line.status)" :value="line.formattedStatus" />
              </template>
            </Column>
          </AppDataTable>
        </div>
      </template>
    </AppDataTable>

    <Dialog
      v-model:visible="formDialogVisible"
      :header="isEditingOrder ? 'Update Order' : 'Create Order'"
      :modal="true"
      class="w-full max-w-5xl"
    >
      <div class="flex flex-col gap-5">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="field">
            <label for="logicId" class="block text-sm font-medium mb-1">Logic ID (Optional)</label>
            <InputText
              id="logicId"
              v-model.trim="formData.logicId"
              class="w-full"
              placeholder="Auto-generated if empty"
              :disabled="isEditingOrder"
            />
          </div>

          <div class="field">
            <label for="location" class="block text-sm font-medium mb-1">Destination Location</label>
            <Select
              id="location"
              v-model="formData.location"
              :options="locations"
              optionLabel="locationCode"
              optionValue="id"
              placeholder="Select a location"
              filter
              class="w-full"
              :invalid="submitted && !formData.location"
            />
            <small v-if="submitted && !formData.location" class="text-red-400">Location is required.</small>
          </div>
        </div>

        <AppDataTable
          :value="formData.lines"
          :filterFields="orderCreateLineFilterFields"
          class="p-datatable-sm"
          dataKey="id"
          :showSearch="false"
          :showPaginator="false"
          responsiveLayout="scroll"
          emptyMessage="Add at least one order line."
        >
          <Column header="Product" style="width: 12rem">
            <template #body="{ data }">
              <Select
                v-model="data.product"
                :options="products"
                optionLabel="name"
                optionValue="id"
                placeholder="Choose product"
                filter
                class="w-full min-w-0"
                :invalid="submitted && !data.product"
              />
              <small v-if="submitted && !data.product" class="text-red-400">Product is required.</small>
            </template>
          </Column>
          <Column header="Quantity" style="min-width: 13rem">
            <template #body="{ data }">
              <InputNumber
                v-model="data.quantity"
                :min="1"
                showButtons
                buttonLayout="horizontal"
                class="w-full min-w-0"
                :invalid="submitted && (!data.quantity || data.quantity < 1)"
              />
              <small v-if="submitted && (!data.quantity || data.quantity < 1)" class="text-red-400"
              >Minimum quantity is 1.</small
              >
            </template>
          </Column>
          <Column header="Actions" style="width: 6rem">
            <template #body="{ index }">
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                rounded
                aria-label="Remove line"
                @click="removeLine(index)"
              />
            </template>
          </Column>
        </AppDataTable>
      </div>

      <template #footer>
        <div class="flex w-full items-center justify-between gap-3">
          <Button label="Add Line" icon="pi pi-plus" outlined @click="addLine" />
          <div class="flex items-center gap-2">
            <Button label="Cancel" icon="pi pi-times" text @click="closeFormDialog" />
            <Button
              :label="isEditingOrder ? 'Save Changes' : 'Submit Order'"
              icon="pi pi-check"
              :severity="isEditingOrder ? 'warning' : 'success'"
              :loading="actionLoading"
              @click="onSubmit"
            />
          </div>
        </div>
      </template>
    </Dialog>

    <UploadFile
      v-model:visible="importDialogVisible"
      :apiCall="handleImport"
      @success="loadOrders"
      xlsx-template-path="/templates/order_template.xlsx"
      csv-template-path="/templates/order_template.csv"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { useRoute } from 'vue-router'

import Button from 'primevue/button'
import Column from 'primevue/column'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import Dropdown from 'primevue/dropdown'
import Tag from 'primevue/tag'
import ConfirmDialog from 'primevue/confirmdialog'

import { orderApi } from '@/api/orderApi.js'
import { inventoryApi } from '@/api/inventoryApi.js'
import { userApi } from '@/api/userApi'
import { productApi } from '@/api/productApi'
import UploadFile from '@/components/UploadFile.vue'

const route = useRoute()
const importDialogVisible = ref(false)
const toast = useToast()
const confirm = useConfirm()

const orders = ref([])
const selectedOrders = ref([])
const expandedRows = ref({})
const editMode = ref(false)

const formDialogVisible = ref(false)
const isEditingOrder = ref(false)
const editingOrderId = ref(null)

const products = ref([])
const locations = ref([])
const operators = ref([])

const loading = ref(false)
const actionLoading = ref(false)
const loadError = ref('')
const submitted = ref(false)

const formatString = (str) => {
  if (!str) return '';
  return String(str).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

const orderFilterFields = [
  { field: 'order.logicId', label: 'Logic ID' },
  { field: 'order.locationLabel', label: 'Destination' },
  { field: 'order.formattedStatus', label: 'Status' },
  { field: 'order.assignedOperatorName', label: 'Assigned Operator' },
  { field: 'order.transportUnitBarcode', label: 'Transport Unit' },
  { field: 'order.formattedCreatedAt', label: 'Created' },
]

const orderLineFilterFields = [
  { field: 'productId', label: 'Product' },
  { field: 'requestedQuantity', label: 'Requested Qty' },
  { field: 'formattedStatus', label: 'Status' },
]

const orderCreateLineFilterFields = [
  { field: 'product', label: 'Product' },
  { field: 'quantity', label: 'Quantity' },
]

const formData = reactive({
  logicId: '',
  location: null,
  lines: [],
})

let nextLineId = 1

const assignmentByOrderId = reactive({})

const handleImport = async (importData) => {
  if (!(importData instanceof FormData)) {
    throw new Error('Wrong Format!')
  }
  return orderApi.importOrders(importData)
}

const getErrorMessage = (error) =>
  error.response?.data?.message || error.response?.data?.error || error.message || 'Request failed.'

const normalizeOrder = (entry) => {
  const order = entry.order || entry;
  const lines = entry.lines || [];

  return {
    order: {
      ...order,
      locationLabel: getLocationLabel(order.destinationLocationId),
      formattedStatus: formatString(order.status || order.Status || 'CREATED'),
      assignedOperatorName: operators.value.find(o => o.id === order.assignedOperatorId)?.username || '',
      formattedCreatedAt: formatDate(order.createdAt)
    },
    lines: lines.map(line => ({
      ...line,
      formattedStatus: formatString(line.status || 'CREATED')
    }))
  }
}

const loadOrders = async () => {
  loading.value = true
  loadError.value = ''

  try {
    const response = await orderApi.getAll()
    let fetchedOrders = (response.data || []).map(normalizeOrder)

    if (route.query.id) {
      const targetId = Number(route.query.id)
      fetchedOrders = fetchedOrders.filter(entry => entry.order.id === targetId)
    }

    orders.value = fetchedOrders

    const currentOrderIds = new Set(orders.value.map((entry) => entry.order?.id).filter(Boolean))
    expandedRows.value = Object.fromEntries(
      Object.entries(expandedRows.value).filter(
        ([orderId, isExpanded]) => isExpanded && currentOrderIds.has(Number(orderId)),
      ),
    )

    orders.value.forEach((entry) => {
      assignmentByOrderId[entry.order.id] = entry.order.assignedOperatorId || null
    })

  } catch (error) {
    orders.value = []
    expandedRows.value = {}
    loadError.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

const toggleEditMode = () => {
  editMode.value = !editMode.value
  selectedOrders.value = []
}

const canEditSelected = computed(() => {
  if (selectedOrders.value.length !== 1) return false
  const orderData = selectedOrders.value[0].order
  return ['CREATED', 'Created'].includes(orderData.status || orderData.Status || 'CREATED')
})

const loadOrderCreateData = async () => {
  const [productsResponse, locationsResponse, usersResponse] = await Promise.all([
    productApi.getAllProducts(),
    inventoryApi.getLocations(),
    userApi.getAll(),
  ])

  products.value = (productsResponse.data || []).map((p) => ({
    id: p.id,
    name: p.name,
    barcode: p.barcode,
  }))

  locations.value = (locationsResponse.data || [])
    .filter((location) => location.zone === 'DISPATCH')
    .map((location) => ({
      ...location,
      locationCode: location.locationCode || location.barcode || location.code || location.location || '',
    }))

  operators.value = (usersResponse.data || []).filter((user) => user.userRole === 'ROLE_OPERATOR')
}

const resetForm = () => {
  formData.logicId = ''
  formData.location = null
  formData.lines = []
  submitted.value = false
  nextLineId = 1
}

const openCreateDialog = async () => {
  resetForm()
  addLine()
  isEditingOrder.value = false
  editingOrderId.value = null
  formDialogVisible.value = true

  try {
    await loadOrderCreateData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Data load failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  }
}

const openEditDialog = async (orderData) => {
  resetForm()

  const order = orderData.order
  const lines = orderData.lines

  formData.logicId = order.logicId
  formData.location = order.destinationLocationId

  lines.forEach((line) => {
    formData.lines.push({
      id: nextLineId++,
      product: line.productId,
      quantity: line.requestedQuantity,
    })
  })

  isEditingOrder.value = true
  editingOrderId.value = order.id
  formDialogVisible.value = true

  try {
    await loadOrderCreateData()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Data load failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  }
}

const closeFormDialog = () => {
  formDialogVisible.value = false
  resetForm()
}

const addLine = () => {
  formData.lines.push({
    id: nextLineId++,
    product: null,
    quantity: 1,
  })
}

const removeLine = (index) => {
  formData.lines.splice(index, 1)
}

const getProduct = (productId) => products.value.find((product) => product.id === productId)

const getOrderQuantity = (order) =>
  (order.lines || []).reduce(
    (total, line) =>
      ['CANCELED', 'CANCELLED', 'Canceled'].includes(line.status || line.Status)
        ? total
        : total + Number(line.requestedQuantity ?? line.quantity ?? 0),
    0,
  )

const hasExpandableLines = (order) => (order.lines || []).length > 0

const isExpanded = (order) => Boolean(expandedRows.value[order.order.id])

const toggleOrderExpansion = (order, rowTogglerCallback, event) => {
  if (!order?.order?.id || !hasExpandableLines(order)) return
  rowTogglerCallback?.(event)
}

const getLocationLabel = (locationId) => {
  const location = locations.value.find((item) => Number(item.id) === Number(locationId))
  return location?.locationCode || location?.barcode || locationId || '-'
}

const isAssignmentLocked = (order) =>
  ['IN_PROGRESS', 'COMPLETED', 'CANCELED', 'CANCELLED', 'PARTIALLY_COMPLETED'].includes(
    String(order?.status || order?.Status).toUpperCase().replace(/ /g, '_'),
  )

const assignOrderToOperator = async (orderId, operatorId) => {
  if (!operatorId) return
  try {
    await orderApi.assign(orderId, operatorId)
    toast.add({
      severity: 'success',
      summary: 'Assigned',
      detail: `Order #${orderId} assigned.`,
      life: 3000,
    })
    await loadOrders()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Assign failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  }
}

const confirmDeleteSelectedOrders = () => {
  confirm.require({
    message: `Delete ${selectedOrders.value.length} selected order(s)?`,
    header: 'Delete Selected Orders',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: deleteSelectedOrders,
  })
}

const deleteSelectedOrders = async () => {
  actionLoading.value = true
  try {
    await Promise.all(selectedOrders.value.map((entry) => orderApi.delete(entry.order.id)))
    toast.add({
      severity: 'success',
      summary: 'Deleted',
      detail: `${selectedOrders.value.length} order(s) deleted.`,
      life: 3000,
    })
    selectedOrders.value = []
    await loadOrders()
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

const getStatusSeverity = (status) =>
  ({
    CREATED: 'info',
    ASSIGNED: 'warning',
    IN_PROGRESS: 'warning',
    ALLOCATED: 'success',
    PARTIALLY_COMPLETED: 'warning',
    SHORTAGE: 'warning',
    COMPLETED: 'success',
    CANCELED: 'danger',
    CANCELLED: 'danger',
  })[String(status).toUpperCase().replace(/ /g, '_')] || 'secondary'

const formatDate = (value) =>
  value
    ? new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value))
    : '-'

const onSubmit = async () => {
  submitted.value = true

  const isTopValid = Boolean(formData.location)
  const areLinesValid = formData.lines.length > 0 && formData.lines.every((line) => line.product && line.quantity >= 1)

  if (!isTopValid || !areLinesValid) {
    toast.add({
      severity: 'error',
      summary: 'Validation Error',
      detail: 'Please fill in all required fields.',
      life: 4000,
    })
    return
  }

  const payload = {
    order: {
      logicId: formData.logicId || null,
      destinationLocationId: formData.location,
    },
    lines: formData.lines.map((line) => ({
      orderId: null,
      productId: line.product,
      requestedQuantity: line.quantity,
    })),
  }

  actionLoading.value = true

  try {
    if (isEditingOrder.value) {
      await orderApi.updateExtended(editingOrderId.value, payload)
      toast.add({ severity: 'success', summary: 'Updated', detail: `Order updated.`, life: 3000 })
    } else {
      await orderApi.create(payload)
      toast.add({
        severity: 'success',
        summary: 'Created',
        detail: `Order created successfully.`,
        life: 3000,
      })
    }

    closeFormDialog()
    await loadOrders()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Action failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

watch(() => route.query.id, () => {
  loadOrders()
})

onMounted(async () => {
  await loadOrderCreateData()
  await loadOrders()
})
</script>

<style scoped>
.order-expander-button {
  flex-shrink: 0;
  height: 2rem;
  justify-content: center;
  min-width: 2rem;
  padding: 0;
  width: 2rem;
}
</style>
