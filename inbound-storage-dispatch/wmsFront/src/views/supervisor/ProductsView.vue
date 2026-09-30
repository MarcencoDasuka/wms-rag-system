<template>
  <div class="p-6">
    <ConfirmDialog />

    <AppDataTable
      v-model:selection="selectedProducts"
      :value="products"
      :loading="loading"
      :rowClass="productRowClass"
      :filterFields="productFilterFields"
      :editMode="editMode ? 'cell' : null"
      paginator
      :rows="10"
      stripedRows
      class="p-datatable-sm"
      dataKey="id"
      emptyMessage="No products found."
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
          @click="loadProducts"
        />
        <Button
          v-if="canManageProducts"
          label="Import"
          icon="pi pi-file-import"
          severity="info"
          @click="importDialogVisible = true"
        />
        <Button
          v-if="canManageProducts"
          label="Add Category"
          icon="pi pi-folder-plus"
          severity="secondary"
          outlined
          @click="openCategoryDialog"
        />
        <Button
          v-if="canManageProducts"
          label="Create"
          icon="pi pi-plus"
          severity="success"
          @click="openCreateDialog"
        />
        <Button
          v-if="canManageProducts"
          :label="editMode ? 'Exit Edit' : 'Edit'"
          icon="pi pi-pencil"
          severity="warning"
          outlined
          @click="toggleEditMode"
        />
        <Button
          v-if="canManageProducts && editMode"
          label="Edit Details"
          icon="pi pi-list"
          severity="warning"
          outlined
          :disabled="selectedProducts.length !== 1 || hasPendingChanges"
          @click="openEditDialog(selectedProducts[0])"
        />
        <Button
          v-if="canManageProducts && editMode"
          label="Submit"
          icon="pi pi-check"
          severity="success"
          :disabled="!hasPendingChanges"
          :loading="actionLoading"
          @click="confirmSubmitChanges"
        />
        <Button
          v-if="canManageProducts && editMode"
          label="Reset"
          icon="pi pi-refresh"
          severity="secondary"
          outlined
          :disabled="!hasPendingChanges"
          @click="confirmResetChanges"
        />
        <Button
          v-if="canManageProducts && editMode"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          outlined
          :disabled="!selectedProducts.length"
          @click="confirmDeleteSelected"
        />
        <span v-if="canManageProducts && editMode" class="app-muted text-sm"
        >{{ selectedProducts.length }} selected</span
        >
      </template>
      <Column
        v-if="canManageProducts && editMode"
        selectionMode="multiple"
        headerStyle="width: 3rem"
      />

      <Column field="name" header="Product" sortable filter>
        <template #body="slotProps">
          <span v-if="editMode" class="font-semibold text-primary">{{ slotProps.data.name }}</span>
          <ProductLink
            v-else
            :product-id="slotProps.data.id"
            :barcode="slotProps.data.barcode"
            :name="slotProps.data.name"
            class="font-semibold"
          />
        </template>
        <template #editor="{ data }">
          <InputText v-model="data.name" class="w-full" autofocus />
        </template>
      </Column>

      <Column field="categoryName" header="Category" sortable filter>
        <template #body="slotProps">
          <Tag severity="info" :value="slotProps.data.categoryName" />
        </template>
        <template #editor="{ data }">
          <Dropdown
            v-model="data.categoryId"
            :options="categories"
            optionLabel="name"
            optionValue="id"
            filter
            class="w-full"
          />
        </template>
      </Column>

      <Column field="barcode" header="Barcode" sortable filter>
        <template #editor="{ data }">
          <InputText v-model="data.barcode" class="w-full" />
        </template>
      </Column>

      <Column field="autoReplenish" filterField="formattedAutoReplenish" header="Auto Replenish" sortable filter>
        <template #body="slotProps">
          <Tag
            :severity="slotProps.data.autoReplenish ? 'success' : 'secondary'"
            :value="slotProps.data.formattedAutoReplenish"
          />
        </template>
        <template #editor="{ data, field }">
          <Dropdown
            v-model="data[field]"
            :options="autoReplenishOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </template>
      </Column>

      <Column field="minThreshold" filterField="formattedMinThreshold" header="Min Threshold" sortable filter>
        <template #body="slotProps">
          <span class="app-subtitle">{{ slotProps.data.formattedMinThreshold }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputNumber v-model="data[field]" :min="0" class="w-full" :disabled="!data.autoReplenish" />
        </template>
      </Column>

      <Column field="replenishQty" filterField="formattedReplenishQty" header="Replenish Qty" sortable filter>
        <template #body="slotProps">
          <span class="app-subtitle">{{ slotProps.data.formattedReplenishQty }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputNumber v-model="data[field]" :min="1" class="w-full" :disabled="!data.autoReplenish" />
        </template>
      </Column>

      <Column field="createdAt" filterField="formattedCreatedAt" header="Created" sortable filter>
        <template #body="slotProps">
          <span class="app-muted text-sm">{{ slotProps.data.formattedCreatedAt }}</span>
        </template>
      </Column>
    </AppDataTable>

    <Dialog
      v-model:visible="dialogVisible"
      :header="dialogMode === 'create' ? 'Add Product' : 'Edit Product'"
      modal
      class="w-full max-w-2xl"
      @hide="resetForm"
    >
      <form class="flex flex-col gap-4 mt-2" @submit.prevent="submitProduct">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="flex flex-col gap-2">
            <label for="productName" class="app-subtitle font-medium">Product Name *</label>
            <InputText
              id="productName"
              v-model.trim="form.name"
              placeholder="Product name"
              class="w-full"
              autofocus
            />
            <small v-if="submitted && !form.name" class="text-red-500"
            >Product name is required.</small
            >
          </div>

          <div class="flex flex-col gap-2">
            <label for="productBarcode" class="app-subtitle font-medium">Barcode *</label>
            <div class="flex gap-2">
              <InputText
                id="productBarcode"
                v-model.trim="form.barcode"
                placeholder="Product Barcode"
                class="w-full"
              />
              <Button
                icon="pi pi-camera"
                severity="secondary"
                outlined
                aria-label="Scan barcode"
                type="button"
                @click="scannerVisible = true"
              />
            </div>
            <small v-if="submitted && !form.barcode" class="text-red-500"
            >Barcode is required.</small
            >
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <label for="productCategory" class="app-subtitle font-medium">Category *</label>
          <div class="flex gap-2">
            <Dropdown
              id="productCategory"
              v-model="form.categoryId"
              :options="categories"
              optionLabel="name"
              optionValue="id"
              placeholder="Select category"
              class="w-full"
              filter
            />
            <Button
              icon="pi pi-plus"
              severity="secondary"
              outlined
              aria-label="Create category"
              type="button"
              @click="openCategoryDialog"
            />
          </div>
          <small v-if="submitted && !form.categoryId" class="text-red-500"
          >Category is required.</small
          >
        </div>

        <div
          class="p-4 border border-surface-200 dark:border-surface-700 rounded-lg bg-surface-50 dark:bg-surface-900 mt-2"
        >
          <div class="flex items-center gap-3 mb-4">
            <InputSwitch inputId="autoReplenish" v-model="form.autoReplenish" />
            <label for="autoReplenish" class="font-semibold cursor-pointer"
            >Enable Auto-Replenishment</label
            >
          </div>

          <div v-if="form.autoReplenish" class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="flex flex-col gap-2">
              <label class="app-subtitle text-sm font-medium">Min Threshold (Trigger)</label>
              <InputNumber
                v-model="form.minThreshold"
                :min="0"
                placeholder="e.g. 5"
                class="w-full"
              />
              <small class="app-muted text-xs"
              >When total stock falls below this, a task is created.</small
              >
              <small
                v-if="submitted && form.autoReplenish && form.minThreshold === null"
                class="text-red-500"
              >Required</small
              >
            </div>
            <div class="flex flex-col gap-2">
              <label class="app-subtitle text-sm font-medium">Replenishment Quantity</label>
              <InputNumber
                v-model="form.replenishQty"
                :min="1"
                placeholder="e.g. 20"
                class="w-full"
              />
              <small class="app-muted text-xs">How many items to bring from storage.</small>
              <small
                v-if="submitted && form.autoReplenish && !form.replenishQty"
                class="text-red-500"
              >Required (>0)</small
              >
            </div>
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <label for="productDescription" class="app-subtitle font-medium">Description</label>
          <Textarea
            id="productDescription"
            v-model.trim="form.description"
            placeholder="Short operational description"
            rows="2"
            class="w-full"
          />
        </div>
      </form>

      <template #footer>
        <Button
          label="Cancel"
          severity="secondary"
          text
          :disabled="actionLoading"
          @click="closeDialog"
        />
        <Button
          :label="dialogMode === 'create' ? 'Create Product' : 'Save Changes'"
          :loading="actionLoading"
          @click="submitProduct"
        />
      </template>
    </Dialog>

    <Dialog v-model:visible="scannerVisible" header="Scan Barcode" modal class="w-full max-w-lg">
      <BarcodeScanner @detected="handleBarcodeDetected" />
      <p class="app-muted text-sm mt-3">
        Point the camera at a barcode. The barcode field will be filled automatically after
        detection.
      </p>
    </Dialog>

    <Dialog
      v-model:visible="categoryDialogVisible"
      header="Create Category"
      modal
      class="w-full max-w-md"
      @hide="resetCategoryForm"
    >
      <form class="flex flex-col gap-2" @submit.prevent="submitCategory">
        <label for="categoryName" class="app-subtitle font-medium">Category Name</label>
        <InputText
          id="categoryName"
          v-model.trim="categoryForm.name"
          placeholder="e.g. Packaging"
          class="w-full"
          autofocus
        />
        <small v-if="categorySubmitted && !categoryForm.name" class="app-danger"
        >Category name is required.</small
        >
      </form>
      <template #footer>
        <Button
          label="Cancel"
          severity="secondary"
          text
          :disabled="categoryLoading"
          @click="categoryDialogVisible = false"
        />
        <Button
          label="Create Category"
          icon="pi pi-check"
          :loading="categoryLoading"
          @click="submitCategory"
        />
      </template>
    </Dialog>
    <UploadFile
      v-model:visible="importDialogVisible"
      :apiCall="handleImport"
      @success="loadProducts"
      xlsx-template-path="/templates/product_template.xlsx"
      csv-template-path="/templates/product_template.csv"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

import Button from 'primevue/button'
import Column from 'primevue/column'
import ConfirmDialog from 'primevue/confirmdialog'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import Toast from 'primevue/toast'

import BarcodeScanner from '@/components/BarcodeScanner.vue'
import { productApi } from '@/api/productApi'
import { useAuthStore } from '@/stores/auth'
import UploadFile from '@/components/UploadFile.vue'

const authStore = useAuthStore()
const route = useRoute()
const toast = useToast()
const confirm = useConfirm()

const importDialogVisible = ref(false)
const products = ref([])
const originalProducts = ref([])
const categories = ref([])
const selectedProducts = ref([])
const loading = ref(false)
const actionLoading = ref(false)
const editMode = ref(false)
const modifiedProductIds = ref(new Set())
const dialogVisible = ref(false)
const scannerVisible = ref(false)
const categoryDialogVisible = ref(false)
const categoryLoading = ref(false)
const dialogMode = ref('create')
const selectedProductId = ref(null)
const submitted = ref(false)
const categorySubmitted = ref(false)

const filters = reactive({
  name: '',
  categoryId: null,
})

const form = reactive({
  name: '',
  barcode: '',
  description: '',
  categoryId: null,
  autoReplenish: false,
  minThreshold: null,
  replenishQty: null,
})

const categoryForm = reactive({ name: '' })
const autoReplenishOptions = [
  { label: 'Active', value: true },
  { label: 'Disabled', value: false },
]

const canManageProducts = computed(() => authStore.hasAnyRole(['ROLE_SUPERVISOR', 'ROLE_DEV']))
const highlightedProductId = computed(() =>
  route.query.productId ? Number(route.query.productId) : null,
)

const productFilterFields = [
  { field: 'name', label: 'Product' },
  { field: 'barcode', label: 'Barcode' },
  { field: 'categoryName', label: 'Category' },
  { field: 'formattedAutoReplenish', label: 'Auto Replenish' },
  { field: 'formattedMinThreshold', label: 'Min Threshold' },
  { field: 'formattedReplenishQty', label: 'Replenish Qty' },
  { field: 'formattedCreatedAt', label: 'Created' },
]

const handleImport = async (formData) => {
  if (!(formData instanceof FormData)) {
    throw new Error('Expected FormData')
  }
  return productApi.importProducts(formData)
}

const getErrorMessage = (error) =>
  error.response?.data?.message || error.response?.data?.error || error.message || 'Request failed.'

const categoryName = (categoryId) =>
  categories.value.find((c) => c.id === categoryId)?.name || `Category #${categoryId}`

const cloneRows = (items) => JSON.parse(JSON.stringify(items || []))

const enrichProducts = (items) =>
  (items || []).map((product) => {
    const isAuto = product.autoReplenish === true;
    return {
      ...product,
      categoryName: categoryName(product.categoryId),
      formattedAutoReplenish: isAuto ? 'Active' : 'Disabled',
      formattedMinThreshold: isAuto ? (product.minThreshold ?? '-') : '-',
      formattedReplenishQty: isAuto ? (product.replenishQty ?? '-') : '-',
      formattedCreatedAt: formatDate(product.createdAt)
    }
  })

const snapshotProducts = () => {
  originalProducts.value = cloneRows(products.value)
  modifiedProductIds.value = new Set()
  selectedProducts.value = []
}

const formatDate = (dateValue) => {
  if (!dateValue) return '-'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(dateValue))
}

const loadCategories = async () => {
  const response = await productApi.getCategories()
  categories.value = response.data
}

const loadProducts = async () => {
  loading.value = true
  try {
    const response = await productApi.getAllProducts()
    products.value = enrichProducts(response.data)
    snapshotProducts()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Products load failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
}

const applyRouteProductFilter = async () => {
  const queryName = route.query.product
  const queryBarcode = route.query.barcode
  const queryProductId = route.query.productId ? Number(route.query.productId) : null

  if (queryName) {
    filters.name = String(queryName)
    await applySearch()
    return
  }

  await loadProducts()

  if (queryBarcode) {
    products.value = products.value.filter((product) => product.barcode === queryBarcode)
  } else if (queryProductId) {
    products.value = products.value.filter((product) => Number(product.id) === queryProductId)
  }
  snapshotProducts()
}

const productRowClass = (product) => ({
  'app-row-highlight':
    highlightedProductId.value && Number(product.id) === highlightedProductId.value,
  'app-row-modified': modifiedProductIds.value.has(product.id),
})

const hasPendingChanges = computed(() => modifiedProductIds.value.size > 0)

const normalizeProduct = (product) => ({
  name: product.name?.trim() || '',
  barcode: product.barcode?.trim() || '',
  categoryId: product.categoryId || null,
  autoReplenish: product.autoReplenish === true,
  minThreshold: product.autoReplenish ? (product.minThreshold ?? null) : null,
  replenishQty: product.autoReplenish ? (product.replenishQty ?? null) : null,
})

const refreshModifiedState = (product) => {
  const original = originalProducts.value.find((item) => item.id === product.id)
  if (!original) return

  const nextIds = new Set(modifiedProductIds.value)
  if (JSON.stringify(normalizeProduct(product)) !== JSON.stringify(normalizeProduct(original)))
    nextIds.add(product.id)
  else nextIds.delete(product.id)
  modifiedProductIds.value = nextIds
}

const onCellEditComplete = ({ data, newValue, field }) => {
  if (!editMode.value) return

  if (newValue !== undefined) {
    data[field] = typeof newValue === 'string' ? newValue.trim() : newValue
  }

  if (field === 'categoryId') data.categoryName = categoryName(data.categoryId)

  if (field === 'autoReplenish' || field === 'minThreshold' || field === 'replenishQty') {
    if (!data.autoReplenish) {
      data.minThreshold = null
      data.replenishQty = null
    }
    data.formattedAutoReplenish = data.autoReplenish ? 'Active' : 'Disabled';
    data.formattedMinThreshold = data.autoReplenish ? (data.minThreshold ?? '-') : '-';
    data.formattedReplenishQty = data.autoReplenish ? (data.replenishQty ?? '-') : '-';
  }

  refreshModifiedState(data)
}

const toggleEditMode = () => {
  if (!editMode.value) {
    editMode.value = true
    selectedProducts.value = []
    return
  }

  if (hasPendingChanges.value) {
    confirmResetChanges(() => {
      editMode.value = false
    })
    return
  }

  editMode.value = false
  selectedProducts.value = []
}

const confirmSubmitChanges = () => {
  confirm.require({
    message: `Submit ${modifiedProductIds.value.size} changed product(s)?`,
    header: 'Submit Product Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-success',
    accept: submitProductChanges,
  })
}

const submitProductChanges = async () => {
  actionLoading.value = true
  try {
    const changedProducts = products.value.filter((product) =>
      modifiedProductIds.value.has(product.id),
    )
    const invalidProduct = changedProducts.find(
      (product) =>
        !product.name?.trim() ||
        !product.barcode?.trim() ||
        !product.categoryId ||
        (product.autoReplenish &&
          (product.minThreshold === null || !product.replenishQty || product.replenishQty < 1)),
    )
    if (invalidProduct) {
      toast.add({
        severity: 'error',
        summary: 'Validation failed',
        detail: `Check required fields for ${invalidProduct.name || 'selected product'}.`,
        life: 5000,
      })
      return
    }
    await Promise.all(
      changedProducts.map((product) =>
        productApi.updateProduct(product.id, {
          name: product.name,
          barcode: product.barcode,
          description: product.description || null,
          categoryId: product.categoryId,
          autoReplenish: product.autoReplenish,
          minThreshold: product.autoReplenish ? product.minThreshold : null,
          replenishQty: product.autoReplenish ? product.replenishQty : null,
        }),
      ),
    )
    toast.add({
      severity: 'success',
      summary: 'Changes saved',
      detail: `${changedProducts.length} product(s) updated.`,
      life: 3000,
    })
    editMode.value = false
    await applySearch()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Submit failed',
      detail: getErrorMessage(error),
      life: 5000,
    })
  } finally {
    actionLoading.value = false
  }
}

const confirmResetChanges = (afterReset) => {
  confirm.require({
    message: 'Discard all unsaved product changes?',
    header: 'Reset Unsaved Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-warning',
    accept: () => {
      products.value = cloneRows(originalProducts.value)
      modifiedProductIds.value = new Set()
      toast.add({
        severity: 'info',
        summary: 'Changes reset',
        detail: 'Unsaved product changes were discarded.',
        life: 2500,
      })
      if (typeof afterReset === 'function') afterReset()
    },
  })
}

const applySearch = async () => {
  const name = filters.name?.trim()
  const categoryId = filters.categoryId

  if (!name && !categoryId) {
    await loadProducts()
    return
  }

  loading.value = true
  try {
    const response = await productApi.searchProducts({
      ...(name ? { name } : {}),
      ...(categoryId ? { categoryId } : {}),
    })
    products.value = enrichProducts(response.data)
    snapshotProducts()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Search failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
}

const clearSearch = async () => {
  filters.name = ''
  filters.categoryId = null
  await loadProducts()
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  selectedProductId.value = null
  resetForm()
  dialogVisible.value = true
}

const openCategoryDialog = () => {
  resetCategoryForm()
  categoryDialogVisible.value = true
}

const openEditDialog = (product) => {
  dialogMode.value = 'edit'
  selectedProductId.value = product.id
  form.name = product.name || ''
  form.barcode = product.barcode || ''
  form.description = product.description || ''
  form.categoryId = product.categoryId || null
  form.autoReplenish = product.autoReplenish || false
  form.minThreshold = product.minThreshold ?? null
  form.replenishQty = product.replenishQty ?? null
  submitted.value = false
  dialogVisible.value = true
}

const closeDialog = () => {
  scannerVisible.value = false
  dialogVisible.value = false
}

const resetForm = () => {
  form.name = ''
  form.barcode = ''
  form.description = ''
  form.categoryId = null
  form.autoReplenish = false
  form.minThreshold = null
  form.replenishQty = null
  submitted.value = false
}

const resetCategoryForm = () => {
  categoryForm.name = ''
  categorySubmitted.value = false
}

const handleBarcodeDetected = (barcode) => {
  form.barcode = barcode
  scannerVisible.value = false
  toast.add({
    severity: 'success',
    summary: 'Barcode scanned',
    detail: `Barcode set to ${barcode}`,
    life: 2500,
  })
}

const submitCategory = async () => {
  categorySubmitted.value = true
  if (!categoryForm.name) return

  categoryLoading.value = true
  try {
    const response = await productApi.createCategory({ name: categoryForm.name })
    await loadCategories()
    products.value = enrichProducts(products.value)
    snapshotProducts()
    form.categoryId = response.data?.id ?? form.categoryId
    categoryDialogVisible.value = false
    toast.add({
      severity: 'success',
      summary: 'Category created',
      detail: response.data?.name || categoryForm.name,
      life: 3000,
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Category creation failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    categoryLoading.value = false
  }
}

const submitProduct = async () => {
  submitted.value = true

  const isAutoValid =
    !form.autoReplenish ||
    (form.minThreshold !== null && form.replenishQty !== null && form.replenishQty > 0)
  if (!form.name || !form.barcode || !form.categoryId || !isAutoValid) return

  actionLoading.value = true
  const payload = {
    name: form.name,
    barcode: form.barcode,
    description: form.description || null,
    categoryId: form.categoryId,
    autoReplenish: form.autoReplenish,
    minThreshold: form.autoReplenish ? form.minThreshold : null,
    replenishQty: form.autoReplenish ? form.replenishQty : null,
  }

  try {
    if (dialogMode.value === 'create') {
      await productApi.createProduct(payload)
      toast.add({ severity: 'success', summary: 'Product created', detail: form.name, life: 3000 })
    } else {
      await productApi.updateProduct(selectedProductId.value, payload)
      toast.add({ severity: 'success', summary: 'Product updated', detail: form.name, life: 3000 })
    }
    dialogVisible.value = false
    await applySearch()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    actionLoading.value = false
  }
}

const confirmDeleteSelected = () => {
  confirm.require({
    message: `Delete ${selectedProducts.value.length} selected product(s)? This action cannot be undone.`,
    header: 'Delete Selected Products',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: deleteSelectedProducts,
  })
}

const deleteSelectedProducts = async () => {
  actionLoading.value = true
  try {
    await Promise.all(selectedProducts.value.map((product) => productApi.deleteProduct(product.id)))
    toast.add({
      severity: 'success',
      summary: 'Products deleted',
      detail: `${selectedProducts.value.length} product(s) deleted.`,
      life: 3000,
    })
    selectedProducts.value = []
    await applySearch()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    actionLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadCategories()
    await applyRouteProductFilter()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Products setup failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
})

watch(() => route.query, applyRouteProductFilter)
</script>
