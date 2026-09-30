<template>
  <Dialog
    :visible="visible"
    :header="dialogTitle"
    modal
    class="w-full max-w-lg"
    @update:visible="emit('update:visible', $event)"
    @hide="resetForm"
  >
    <form class="flex flex-col gap-4" @submit.prevent="submitForm">
      <div v-if="mode === 'add'" class="flex flex-col gap-2">
        <label for="productId" class="app-subtitle font-medium">Product</label>
        <Dropdown
          id="productId"
          v-model="form.productId"
          :options="products"
          optionLabel="name"
          optionValue="id"
          placeholder="Select product"
          class="w-full"
          filter
        />
        <small v-if="submitted && !form.productId" class="app-danger">Product is required.</small>
      </div>

      <div v-if="mode === 'add'" class="flex flex-col gap-2">
        <label for="locationId" class="app-subtitle font-medium">Location</label>
        <Dropdown
          id="locationId"
          v-model="form.locationId"
          :options="locations"
          optionLabel="barcode"
          optionValue="id"
          placeholder="Select location"
          class="w-full"
          filter
        />
        <small v-if="submitted && !form.locationId" class="app-danger">Location is required.</small>
      </div>

      <div v-if="selectedStock && mode !== 'add'" class="app-muted-panel rounded-lg p-3 text-sm">
        <div class="app-title font-semibold">{{ selectedStock.productName }}</div>
        <div>
          {{ selectedStock.barcode }} · {{ selectedStock.locationBarcode }} · Current qty:
          {{ selectedStock.quantity }}
        </div>
        <div v-if="reservedQuantity > 0" class="app-muted text-xs mt-1">
          {{ reservedQuantity }} reserved quantity is protected and cannot be removed manually.
        </div>
      </div>

      <div v-if="mode === 'adjust'" class="flex flex-col gap-2">
        <label for="reason" class="app-subtitle font-medium">Adjustment Reason</label>
        <Dropdown
          id="reason"
          v-model="form.reason"
          :options="reasonOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="Select reason"
          class="w-full"
        />
        <small v-if="submitted && !form.reason" class="app-danger">Reason is required.</small>
      </div>

      <div v-if="mode === 'adjust'" class="flex flex-col gap-2">
        <label for="comment" class="app-subtitle font-medium">Comment</label>
        <Textarea
          id="comment"
          v-model="form.comment"
          rows="3"
          autoResize
          class="w-full"
          placeholder="Optional comment"
        />
      </div>

      <div class="flex flex-col gap-2">
        <label :for="quantityField" class="app-subtitle font-medium">{{ quantityLabel }}</label>
        <InputNumber
          :id="quantityField"
          v-model="form[quantityField]"
          :min="quantityMin"
          :max="quantityMax"
          showButtons
          class="w-full"
          inputClass="w-full"
        />
        <small v-if="submitted && !isQuantityValid" class="app-danger">{{
          quantityValidationMessage
        }}</small>
      </div>

      <div v-if="mode === 'add'" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="flex flex-col gap-2">
          <label for="manufactureDate" class="app-subtitle font-medium"> Manufacture Date </label>

          <DatePicker
            id="manufactureDate"
            v-model="form.manufactureDate"
            :maxDate="today"
            showClear
            dateFormat="yy-mm-dd"
            class="w-full"
          />
        </div>

        <div class="flex flex-col gap-2">
          <label for="expirationDate" class="app-subtitle font-medium"> Expiration Date </label>

          <DatePicker
            id="expirationDate"
            v-model="form.expirationDate"
            :minDate="today"
            showClear
            dateFormat="yy-mm-dd"
            class="w-full"
          />
        </div>
      </div>
    </form>

    <template #footer>
      <Button label="Cancel" severity="secondary" text :disabled="loading" @click="closeDialog" />
      <Button
        :label="submitLabel"
        :severity="submitSeverity"
        :loading="loading"
        @click="submitForm"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import InputNumber from 'primevue/inputnumber'
import DatePicker from 'primevue/datepicker'
import Textarea from 'primevue/textarea'

const today = new Date()
today.setHours(0, 0, 0, 0)
const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, required: true },
  selectedStock: { type: Object, default: null },
  products: { type: Array, default: () => [] },
  locations: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible', 'submit'])

const submitted = ref(false)
const form = reactive({
  productId: null,
  locationId: null,
  quantity: null,
  newQuantity: null,
  reason: null,
  comment: '',
  manufactureDate: '',
  expirationDate: '',
})

const reasonOptions = [
  { label: 'Stolen', value: 'STOLEN' },
  { label: 'Damaged', value: 'DAMAGED' },
  { label: 'Lost', value: 'LOST' },
  { label: 'Inventory mismatch', value: 'INVENTORY_MISMATCH' },
]

const dialogTitle = computed(() => {
  if (props.mode === 'add') return 'Add Stock'
  if (props.mode === 'remove') return 'Remove Stock'
  return 'Adjust Stock'
})

const quantityField = computed(() => (props.mode === 'adjust' ? 'newQuantity' : 'quantity'))
const quantityLabel = computed(() => {
  if (props.mode === 'adjust') return 'New Physical Quantity'
  if (props.mode === 'remove') return 'Available Quantity to Remove'
  return 'Quantity'
})
const quantityMin = computed(() => (props.mode === 'adjust' ? 0 : 1))
const reservedQuantity = computed(() => Number(props.selectedStock?.reservedQuantity ?? 0))
const availableQuantity = computed(() =>
  Number(props.selectedStock?.availableQuantity ?? props.selectedStock?.quantity ?? 0),
)
const quantityMax = computed(() => {
  if (props.mode === 'remove') return availableQuantity.value
  return undefined
})
const submitLabel = computed(() => {
  if (props.mode === 'add') return 'Add Stock'
  if (props.mode === 'remove') return 'Remove Stock'
  return 'Adjust Stock'
})
const submitSeverity = computed(() => {
  if (props.mode === 'add') return 'success'
  if (props.mode === 'remove') return 'danger'
  return 'warning'
})
const isQuantityValid = computed(() => {
  const value = form[quantityField.value]
  if (value === null || value === undefined || value < quantityMin.value) return false
  if (props.mode === 'remove' && value > availableQuantity.value) return false
  return true
})
const quantityValidationMessage = computed(() => {
  if (props.mode === 'adjust') return 'New available quantity must be greater than or equal to 0.'
  if (props.mode === 'remove')
    return `Quantity must be between 1 and available quantity (${availableQuantity.value}).`
  return 'Quantity must be greater than 0.'
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      submitted.value = false
      if (props.mode === 'adjust' && props.selectedStock) {
        form.newQuantity = props.selectedStock.quantity ?? 0
        form.reason = null
        form.comment = ''
      }
    }
  },
)

const resetForm = () => {
  submitted.value = false
  form.productId = null
  form.locationId = null
  form.quantity = null
  form.newQuantity = null
  form.reason = null
  form.comment = ''
  form.manufactureDate = ''
  form.expirationDate = ''
}

const closeDialog = () => {
  emit('update:visible', false)
}

const isFormValid = () => {
  if (!isQuantityValid.value) return false
  if (props.mode === 'add') {
    return !!form.productId && !!form.locationId
  }
  if (props.mode === 'adjust') {
    return !!props.selectedStock?.id && !!form.reason
  }
  return !!props.selectedStock?.id
}

const submitForm = () => {
  submitted.value = true
  if (!isFormValid()) return

  if (props.mode === 'add') {
    emit('submit', {
      productId: form.productId,
      locationId: form.locationId,
      quantity: form.quantity,
      manufactureDate: form.manufactureDate || null,
      expirationDate: form.expirationDate || null,
    })
    return
  }

  if (props.mode === 'remove') {
    emit('submit', {
      stockId: props.selectedStock.id,
      quantity: form.quantity,
    })
    return
  }

  emit('submit', {
    stockId: props.selectedStock.id,
    newQuantity: form.newQuantity,
    reason: form.reason,
    comment: form.comment || null,
  })
}
</script>
