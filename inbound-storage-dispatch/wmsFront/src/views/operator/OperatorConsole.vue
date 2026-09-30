<template>
  <div class="app-shell font-sans min-h-screen flex flex-col items-center w-full overflow-x-hidden">
    <ConfirmDialog />
    <header class="app-header w-full flex justify-between items-center px-4 py-2">
      <div class="flex items-center gap-2">
        <img
          :src="isDark ? '/white_logo.png' : '/color_logo.png'"
          alt="Inbound Storage Dispatch logo"
          class="h-14 w-auto object-contain"
        />
        <h1 class="text-base font-bold tracking-wide app-title">Operator Console</h1>
      </div>
      <div class="flex items-center gap-2">
        <ThemeToggle />
        <Button icon="pi pi-sign-out" severity="danger" text rounded size="small" @click="handleLogout" />
      </div>
    </header>

    <main class="w-full max-w-xl p-4 sm:p-6 flex flex-col gap-6 items-center justify-center box-border">

      <div v-if="loading" class="app-card w-full rounded-2xl p-10 text-center flex flex-col items-center justify-center">
        <ProgressSpinner strokeWidth="4" style="width: 2.5rem; height: 2.5rem" />
        <p class="app-muted mt-4 text-sm">Loading assigned task...</p>
      </div>

      <Message v-else-if="loadError" class="w-full text-xs" severity="error" :closable="false">
        {{ loadError }}
      </Message>

      <div v-else-if="showTuScan" class="app-card rounded-2xl w-full text-left p-5 sm:p-6 flex flex-col gap-5 box-border">
        <div class="text-center flex flex-col items-center gap-2">
          <span class="text-xs font-bold px-3 py-1.5 rounded-xl tracking-wide app-pill" :class="taskBadgeClass">
            {{ isReplenishmentTask ? 'Replenishment' : 'Order' }}
          </span>
          <h2 class="text-base font-bold mt-2 app-title">Step 1: Scan Transport Unit</h2>
          <p class="app-muted text-xs">Required prefix: 'TU' followed by 6 digits (e.g., TU100001)</p>
        </div>

        <div v-if="currentAllocation" class="app-muted-panel rounded-xl p-4 flex flex-col gap-2 text-xs">
          <div class="text-[10px] font-bold uppercase tracking-wider app-muted">Current task</div>
          <div class="font-bold app-title text-sm">{{ currentAllocation.productName }}</div>
          <div class="flex flex-wrap gap-x-5 gap-y-2 app-subtitle font-medium">
            <span>Required Qty: <strong class="app-warm font-bold text-sm">{{ currentAllocation.requiredQuantity }} u.</strong></span>
            <span>Location: <strong class="font-mono app-accent font-bold text-sm tracking-wide">{{ currentAllocation.sourceLocationBarcode }}</strong></span>
          </div>
        </div>

        <ScanSection
          v-model="tuInput"
          :loading="actionLoading"
          :error-message="actionError"
          submit-label="Confirm"
          placeholder="Scan TU barcode"
          @submit="submitTuScan"
          class="mt-1"
        />
      </div>

      <div v-else-if="showFinalSummary" class="app-card rounded-2xl w-full text-left p-5 sm:p-6 flex flex-col gap-5 box-border">
        <div class="text-center px-3 py-2.5 rounded-xl app-type-banner" :class="lastTaskType === 'REPLENISHMENT' ? 'app-type-banner--repl' : 'app-type-banner--pick'">
          <div class="text-base font-bold tracking-wide flex items-center justify-center gap-2.5">
            <i class="pi pi-shopping-bag"></i>
            {{ lastTaskType === 'REPLENISHMENT' ? 'Replenishment Finished' : 'Picking Order Lines Finished' }}
          </div>
        </div>

        <div class="app-muted-panel w-full rounded-xl p-4 flex flex-col gap-4">
          <div class="flex justify-between items-center app-divider pb-3">
            <div class="text-[11px] font-bold uppercase tracking-wider app-subtitle flex items-center gap-1.5">
              <i class="pi pi-check-circle text-sm app-success"></i> Please Verify Gathered Items
            </div>
            <span class="text-[10px] px-2.5 py-0.5 rounded-full font-bold app-pill app-pill--review">
              SUMMARY
            </span>
          </div>

          <div class="flex flex-col gap-3 max-h-[280px] overflow-y-auto pr-1">
            <div
              v-for="(proc, index) in finalSummaryEntries"
              :key="proc.allocationId || index"
              class="app-card flex flex-col gap-2 p-3 rounded-xl text-xs"
            >
              <div class="flex justify-between items-start gap-3">
                <span class="font-bold app-title line-clamp-2 flex-1">
                  {{ proc.productName || 'Unknown Product' }}
                </span>
                <span class="font-mono text-[10px] app-barcode whitespace-nowrap mt-0.5">
                  #{{ proc.productBarcode || 'No EAN' }}
                </span>
              </div>

              <div class="flex justify-between items-center mt-1 pt-2 app-divider-dashed">
                <div class="flex items-center gap-1.5 font-mono text-[11px]">
                  <span class="app-chip px-2 py-0.5 rounded font-semibold">
                    {{ proc.sourceLocationBarcode || '???' }}
                  </span>
                </div>

                <div class="text-right whitespace-nowrap">
                  <span class="app-muted text-[11px]">Processed: </span>
                  <strong class="text-sm app-success font-extrabold">
                    {{ proc.pickedQuantity ?? proc.requiredQuantity }}
                  </strong>
                  <span class="text-[10px] app-muted font-normal"> / {{ proc.requiredQuantity }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-between text-[11px] app-muted pt-1 app-divider">
            <span>Total lines processed:</span>
            <span class="font-bold app-subtitle">{{ finalSummaryEntries.length || 0 }}</span>
          </div>
        </div>

        <Button
          :label="lastTaskType === 'REPLENISHMENT' ? 'Scan Destination' : 'Dispatch'"
          icon="pi pi-arrow-right"
          severity="primary"
          class="p-button-lg py-3.5 px-6 text-base font-bold tracking-wider w-full mt-2"
          @click="goToDestinationScanScreen"
        />
      </div>

      <div v-else-if="showDestinationScan" class="app-card rounded-2xl w-full text-left p-5 sm:p-6 flex flex-col gap-5 box-border">
        <div class="text-center flex flex-col items-center gap-2">
          <span class="text-xs font-bold px-3 py-1.5 rounded-xl tracking-wide app-pill" :class="taskBadgeClass">
            {{ lastTaskType === 'REPLENISHMENT' ? 'Replenishment' : 'Order' }}
          </span>
          <h2 class="text-base font-bold mt-2 app-title">Final Step: Scan Destination Location</h2>
          <p class="app-muted text-xs">Please drop off items and scan the target zone to complete task</p>
        </div>

        <div class="app-dispatch-banner w-full rounded-xl p-3 flex items-center justify-center gap-2 text-xs font-bold tracking-wide uppercase">
          <i class="pi pi-info-circle text-sm"></i>
          Drop TU at dispatch
        </div>

        <div class="app-muted-panel rounded-xl p-5 text-center my-1">
          <div class="text-[10px] font-bold uppercase tracking-wider app-muted">Target Destination Location</div>
          <div class="font-mono font-bold app-success text-2xl mt-2 tracking-wide">
            {{ savedDestinationBarcode || 'DEST-ZONE' }}
          </div>
        </div>

        <ScanSection
          v-model="destinationInput"
          :loading="actionLoading"
          :error-message="actionError"
          submit-label="Confirm"
          placeholder="Destination barcode"
          @submit="submitDestinationScan"
          class="mt-1"
        />
      </div>

      <div v-else-if="isEmpty" class="app-card w-full rounded-2xl p-10 text-center flex flex-col items-center justify-center gap-3">
        <i class="pi pi-inbox text-4xl app-muted"></i>
        <h2 class="text-lg font-semibold app-title">No assigned tasks</h2>
        <p class="app-muted text-xs">You do not have active tasks right now.</p>
        <Button class="mt-3 p-button-sm px-4" icon="pi pi-refresh" label="Refresh" @click="loadCurrentTask()" />
      </div>

      <template v-else-if="summary">
        <div class="app-card rounded-2xl w-full text-left p-4 sm:p-6 flex flex-col gap-5 box-border">

          <div v-if="!isAwaitingStart" class="app-muted-panel flex justify-between items-center px-4 py-3.5 rounded-xl">
            <div class="flex flex-col gap-1">
              <div class="text-base font-bold tracking-tight app-title">
                Lines Processing Flow
              </div>
              <span class="text-[11px] font-medium app-muted">
                {{ orderedAllocations.length }} lines combined together
              </span>
            </div>
            <span class="text-xs font-bold px-3 py-1.5 rounded-xl tracking-wide app-pill" :class="taskBadgeClass">
              {{ isReplenishmentTask ? 'Replenishment' : 'Order' }}
            </span>
          </div>

          <div v-if="!isAwaitingStart" class="app-muted-panel rounded-xl p-3.5 flex justify-between items-center text-xs">
            <span class="app-subtitle font-semibold">Processing line {{ currentLineIndex + 1 }} of {{ orderedAllocations.length }}</span>
            <div class="flex gap-2">
              <span
                v-for="(_, idx) in orderedAllocations"
                :key="idx"
                class="w-2.5 h-2.5 rounded-full app-progress-dot"
                :class="idx === currentLineIndex ? 'app-progress-dot--active' : (idx < currentLineIndex ? 'app-progress-dot--done' : 'app-progress-dot--pending')"
              ></span>
            </div>
          </div>

          <div v-if="!isAwaitingStart" class="app-card rounded-xl p-4 text-xs flex flex-col gap-2">
            <div class="text-[10px] font-bold uppercase tracking-wider app-muted">Current Product to Pick</div>
            <div class="font-bold app-title text-sm app-divider pb-2 mb-1">{{ currentAllocation?.productName }}</div>
            <div class="flex flex-wrap items-center gap-x-6 gap-y-2 app-subtitle font-medium pt-1">
              <span>Required Qty: <strong class="app-warm font-bold text-sm">{{ currentAllocation?.requiredQuantity }} u.</strong></span>
              <span>Location: <strong class="font-mono app-accent font-bold text-sm tracking-wide">{{ currentAllocation?.sourceLocationBarcode }}</strong></span>
            </div>
          </div>

          <div v-if="isAwaitingStart" class="w-full flex flex-col gap-5 py-1">
            <div class="app-muted-panel w-full rounded-xl p-4 flex flex-col gap-4">
              <div class="flex justify-between items-center app-divider pb-3">
                <div class="text-[11px] font-bold uppercase tracking-wider app-subtitle flex items-center gap-1.5">
                  <i class="pi pi-map-marker text-sm app-warm"></i> Preview
                </div>
                <span class="text-xs font-bold px-3 py-1.5 rounded-xl tracking-wide app-pill" :class="taskBadgeClass">
                  {{ isReplenishmentTask ? 'Replenishment' : 'Order' }}
                </span>
              </div>

              <div class="flex flex-col gap-3 max-h-[260px] overflow-y-auto pr-1">
                <div
                  v-for="(proc, index) in orderedAllocations"
                  :key="proc.allocationId || index"
                  class="app-card flex flex-col gap-2 p-3 rounded-xl text-xs"
                >
                  <div class="flex justify-between items-start gap-3">
                    <span class="font-bold app-title line-clamp-2 flex-1">
                      {{ proc.productName || 'Unknown Product' }}
                    </span>
                    <span class="font-mono text-[10px] app-barcode whitespace-nowrap mt-0.5">
                      #{{ proc.productBarcode || 'No EAN' }}
                    </span>
                  </div>

                  <div class="flex justify-between items-center mt-1 pt-2 app-divider-dashed">
                    <div class="flex items-center gap-1.5 font-mono text-[11px]">
                      <span class="app-chip px-2 py-0.5 rounded font-semibold">
                        {{ proc.sourceLocationBarcode || '???' }}
                      </span>
                    </div>
                    <div class="text-right whitespace-nowrap">
                      <span class="app-muted text-[11px]">Qty: </span>
                      <strong class="text-sm app-warm font-bold">{{ proc.requiredQuantity }}</strong>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <Button
              label="Start Task"
              icon="pi pi-play"
              class="p-button-lg py-3.5 px-6 text-base font-bold tracking-wider w-full mt-1"
              :severity="isReplenishmentTask ? 'primary' : 'warn'"
              :loading="actionLoading"
              @click="startTask"
            />
          </div>

          <template v-else-if="currentAllocation">
            <div class="w-full flex flex-col gap-4">
              <div class="w-full pt-2 mb-6 px-4">
                <div class="flex items-center justify-center w-full max-w-md mx-auto">
                  <div class="flex flex-col items-center flex-initial relative">
                    <div
                      class="app-step-circle w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold border-2 flex-shrink-0"
                      :class="activeStep === 1 ? 'app-step-circle--active' : (activeStep > 1 ? 'app-step-circle--done' : 'app-step-circle--pending')"
                    >
                      <span>1</span>
                    </div>
                    <div class="w-0 min-w-[90px] text-center mt-3.5">
                      <span class="text-[10px] font-bold tracking-wider uppercase block leading-tight" :class="activeStep === 1 ? 'app-warm font-extrabold' : 'app-muted'">
                        Source Loc.
                      </span>
                    </div>
                  </div>

                  <div class="flex-1 h-[2px] mx-2 mb-3.5 app-step-line" :class="activeStep > 1 ? 'app-step-line--done' : ''"></div>

                  <div class="flex flex-col items-center flex-initial relative">
                    <div
                      class="app-step-circle w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold border-2 flex-shrink-0"
                      :class="activeStep === 2 ? 'app-step-circle--active' : (activeStep > 2 ? 'app-step-circle--done' : 'app-step-circle--pending')"
                    >
                      <span>2</span>
                    </div>
                    <div class="w-0 min-w-[90px] text-center mt-3.5">
                      <span class="text-[10px] font-bold tracking-wider uppercase block leading-tight" :class="activeStep === 2 ? 'app-warm font-extrabold' : 'app-muted'">
                        Product Code
                      </span>
                    </div>
                  </div>

                  <div class="flex-1 h-[2px] mx-2 mb-3.5 app-step-line" :class="activeStep > 2 ? 'app-step-line--done' : ''"></div>

                  <div class="flex flex-col items-center flex-initial relative">
                    <div
                      class="app-step-circle w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold border-2 flex-shrink-0"
                      :class="activeStep === 3 ? 'app-step-circle--active' : 'app-step-circle--pending'"
                    >
                      <span>3</span>
                    </div>
                    <div class="w-0 min-w-[90px] text-center mt-3.5">
                      <span class="text-[10px] font-bold tracking-wider uppercase block leading-tight" :class="activeStep === 3 ? 'app-warm font-extrabold' : 'app-muted'">
                        Quantity
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="w-full">
                <div v-if="activeStep === 1" class="app-card rounded-xl p-5 flex flex-col gap-4 text-center items-center w-full box-border">
                  <div>
                    <div class="text-xs app-muted uppercase font-bold tracking-wider">Go to location & scan:</div>
                    <div class="font-mono font-bold app-accent text-2xl mt-2 tracking-wide">
                      {{ currentAllocation.sourceLocationBarcode }}
                    </div>
                  </div>
                  <ScanSection
                    v-model="barcodeInput"
                    :loading="actionLoading"
                    :error-message="actionError"
                    submit-label="Confirm"
                    placeholder="Source barcode"
                    @submit="submitBarcodeStep"
                    class="w-full mt-2"
                  />
                </div>

                <div v-if="activeStep === 2" class="app-card rounded-xl p-5 flex flex-col gap-4 text-center items-center w-full box-border">
                  <div>
                    <div class="text-xs app-muted uppercase font-bold tracking-wider">Scan product barcode</div>
                    <div class="font-mono font-bold app-warm text-2xl mt-2 tracking-wide">
                      {{ currentAllocation.productBarcode || 'No barcode' }}
                    </div>
                  </div>
                  <ScanSection
                    v-model="barcodeInput"
                    :loading="actionLoading"
                    :error-message="actionError"
                    submit-label="Confirm"
                    placeholder="Product barcode"
                    @submit="submitBarcodeStep"
                    class="w-full mt-2"
                  />
                </div>

                <div v-if="activeStep === 3" class="app-card rounded-xl p-5 flex flex-col gap-5 text-center items-center w-full box-border">
                  <div>
                    <div class="text-xs app-muted uppercase font-bold tracking-wider">Confirm picked quantity</div>
                    <div class="text-xs font-normal app-muted mt-2.5">Required: <strong class="app-warm text-sm font-bold">{{ currentAllocation.requiredQuantity }} u.</strong></div>
                  </div>

                  <div class="w-full max-w-xs flex flex-col gap-4 items-center mt-1">
                    <InputNumber
                      v-model="pickedQuantity"
                      :min="0"
                      :max="currentAllocation.requiredQuantity"
                      fluid
                      inputClass="app-qty-input text-center text-xl font-extrabold border-none py-3 w-full rounded-xl"
                    />
                    <div class="flex gap-5 w-full justify-center mt-1 touch-none select-none">
                      <Button
                        icon="pi pi-minus"
                        severity="danger"
                        class="w-12 h-12 rounded-full flex-shrink-0"
                        @pointerdown="startLongPress(decrementQuantity)"
                        @pointerup="stopLongPress"
                        @pointerleave="stopLongPress"
                        :disabled="pickedQuantity <= 0"
                      />
                      <Button
                        icon="pi pi-plus"
                        severity="success"
                        class="w-12 h-12 rounded-full flex-shrink-0"
                        @pointerdown="startLongPress(() => incrementQuantity(currentAllocation.requiredQuantity))"
                        @pointerup="stopLongPress"
                        @pointerleave="stopLongPress"
                        :disabled="pickedQuantity >= currentAllocation.requiredQuantity"
                      />
                    </div>
                  </div>

                  <Message v-if="actionError" severity="error" :closable="false" class="w-full text-xs mt-2">
                    {{ actionError }}
                  </Message>

                  <Button
                    label="Confirm"
                    icon="pi pi-check-circle"
                    class="mt-4 w-full font-bold py-3 text-sm tracking-wide"
                    :loading="actionLoading"
                    @click="confirmQuantity"
                  />
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { allocationApi } from '@/api/allocationApi'
import { useTheme } from '@/composables/useTheme'
import BarcodeScanner from '@/components/BarcodeScanner.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import ConfirmDialog from 'primevue/confirmdialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'

const ScanSection = defineComponent({
  name: 'ScanSection',
  props: {
    modelValue: { type: String, default: '' },
    loading: { type: Boolean, default: false },
    errorMessage: { type: String, default: '' },
    submitLabel: { type: String, required: true },
    placeholder: { type: String, required: true }
  },
  emits: ['update:modelValue', 'submit'],
  setup(props, { emit }) {
    const isScannerOpen = ref(false)
    const toggleScanner = () => { isScannerOpen.value = !isScannerOpen.value }
    const handleDetected = (value) => {
      emit('update:modelValue', value ? value.toUpperCase() : '')
      isScannerOpen.value = false
    }

    return () =>
      h('div', { class: 'flex flex-col gap-3 w-full items-center' }, [
        h('div', { class: 'flex flex-col gap-2 w-full text-center' }, [
          h(InputText, {
            modelValue: props.modelValue,
            'onUpdate:modelValue': (value) => emit('update:modelValue', value ? value.toUpperCase() : ''),
            placeholder: props.placeholder,
            class: 'app-scan-input text-center w-full p-inputtext-sm py-2.5 rounded-xl',
            onKeyup: (event) => { if (event.key === 'Enter') emit('submit') }
          }),
          h(Button, {
            label: props.submitLabel,
            icon: 'pi pi-check',
            severity: 'success',
            class: 'w-full p-button-sm rounded-xl py-2.5 font-bold tracking-wide',
            loading: props.loading,
            onClick: () => emit('submit')
          })
        ]),
        h(Button, {
          label: isScannerOpen.value ? 'Close Camera' : 'Scan Barcode',
          icon: isScannerOpen.value ? 'pi pi-times' : 'pi pi-camera',
          severity: 'info',
          class: 'w-full text-xs font-semibold py-2 rounded-xl border',
          onClick: toggleScanner
        }),
        isScannerOpen.value ? h('div', { class: 'app-scanner-frame w-full rounded-xl overflow-hidden p-1' }, [h(BarcodeScanner, { onDetected: handleDetected })]) : null,
        props.errorMessage ? h(Message, { severity: 'error', closable: false, class: 'w-full text-xs text-left mt-1' }, () => props.errorMessage) : null
      ])
  }
})

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { isDark } = useTheme()
const confirm = useConfirm()
const toast = useToast()

const loading = ref(true)
const actionLoading = ref(false)
const loadError = ref('')
const actionError = ref('')
const summary = ref(null)
const barcodeInput = ref('')
const destinationInput = ref('')
const pickedQuantity = ref(0)
const shortageComment = ref('')
const tuInput = ref('')

const finalAllocationsSummary = ref([])
const showTuScan = ref(false)
const showDestinationScan = ref(false)
const showFinalSummary = ref(false)

const lastTaskType = ref('')
const savedDestinationBarcode = ref('')
const activeTuBarcode = ref(localStorage.getItem('active_tu_barcode') || '')

let longPressTimer = null
let longPressInterval = null

const resetState = () => {
  summary.value = null
  showTuScan.value = false
  showDestinationScan.value = false
  showFinalSummary.value = false
  lastTaskType.value = ''
  savedDestinationBarcode.value = ''
  activeTuBarcode.value = ''
  tuInput.value = ''
  barcodeInput.value = ''
  destinationInput.value = ''
  pickedQuantity.value = 0
  shortageComment.value = ''
  finalAllocationsSummary.value = []
}

const incrementQuantity = (max) => { if (pickedQuantity.value < max) pickedQuantity.value++ }
const decrementQuantity = () => { if (pickedQuantity.value > 0) pickedQuantity.value-- }

const startLongPress = (actionFn) => {
  actionFn()
  longPressTimer = setTimeout(() => {
    longPressInterval = setInterval(() => {
      actionFn()
    }, 50)
  }, 300)
}

const stopLongPress = () => {
  if (longPressTimer) clearTimeout(longPressTimer)
  if (longPressInterval) clearInterval(longPressInterval)
}

onBeforeUnmount(() => {
  stopLongPress()
})

const handleLogout = async () => {
  await authStore.logout()
  router.push('/login')
}

const isEmpty = computed(() => !loading.value && !loadError.value && !summary.value && !showFinalSummary.value && !showDestinationScan.value && !showTuScan.value)

const currentAllocation = computed(() => {
  if (!summary.value || !summary.value.currentAllocation) return null;

  return {
    ...summary.value.currentAllocation,
    requiredQuantity: summary.value.currentAllocation.quantity ?? summary.value.currentAllocation.requiredQuantity
  }
})

const orderedAllocations = computed(() => normalizeSummaryEntries(summary.value))
const isReplenishmentTask = computed(() => summary.value?.taskType === 'REPLENISHMENT' || lastTaskType.value === 'REPLENISHMENT')

const taskBadgeClass = computed(() => {
  return isReplenishmentTask.value ? 'app-pill--repl' : 'app-pill--pick'
})
const isAwaitingStart = computed(() => currentAllocation.value?.status === 'ASSIGNED')

const currentLineIndex = computed(() => {
  if (!currentAllocation.value || !orderedAllocations.value.length) return 0
  const idx = orderedAllocations.value.findIndex(a => a.allocationId === currentAllocation.value.allocationId)
  return idx !== -1 ? idx : 0
})

const activeStep = computed(() => {
  if (!currentAllocation.value) return 1
  if (!currentAllocation.value.sourceLocationScanned) return 1
  if (!currentAllocation.value.productScanned) return 2
  return 3
})

const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback

const normalizeSummaryEntries = (payload) => {
  if (!payload) return []

  if (payload.allocations?.length) {
    return payload.allocations
  }

  return (payload.orderLines ?? []).map((line) => ({
    allocationId: line.taskId || line.orderLineId,
    taskId: line.taskId,
    orderLineId: line.orderLineId,
    productId: line.productId,
    productName: line.productName,
    productBarcode: line.productBarcode,
    sourceLocationBarcode: line.sourceLocationBarcodes?.[0] || null,
    destinationLocationBarcode: line.destinationLocationBarcode,
    requiredQuantity: line.requestedQuantity ?? line.quantity,
    pickedQuantity: line.pickedQuantity,
    status: line.status,
  }))
}

const finalSummaryEntries = computed(() => {
  if (!showFinalSummary.value) {
    return finalAllocationsSummary.value
  }

  const normalized = normalizeSummaryEntries(summary.value)
  return normalized.length ? normalized : finalAllocationsSummary.value
})

const hydrateState = (payload) => {
  summary.value = payload
  actionError.value = ''
  barcodeInput.value = ''
  destinationInput.value = ''
  shortageComment.value = ''

  const alloc = payload?.currentAllocation
  if (alloc) {
    pickedQuantity.value = alloc.quantity ?? alloc.requiredQuantity ?? alloc.requestedQuantity ?? 0
  } else {
    pickedQuantity.value = 0
  }

  if (payload?.taskType) {
    lastTaskType.value = payload.taskType
  }
  if (payload?.destinationLocationBarcode) {
    savedDestinationBarcode.value = payload.destinationLocationBarcode
  }
  if (payload && payload.allocations) {
    finalAllocationsSummary.value = JSON.parse(JSON.stringify(payload.allocations))
  }
}

const hydrateCompletionSummary = (completion) => {
  if (!completion?.summary) return

  summary.value = completion.summary
  finalAllocationsSummary.value = JSON.parse(JSON.stringify(normalizeSummaryEntries(completion.summary)))
  lastTaskType.value = completion.summary.taskType || lastTaskType.value
  if (completion.summary.destinationLocationBarcode) {
    savedDestinationBarcode.value = completion.summary.destinationLocationBarcode
  }
  showFinalSummary.value = !completion.summary.currentAllocation
  showDestinationScan.value = false
}

const loadCurrentTask = async () => {
  if (showFinalSummary.value || showDestinationScan.value || showTuScan.value) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const response = await allocationApi.getCurrentTaskSummary()
    if (response.status === 204 || !response.data) {
      if (savedDestinationBarcode.value) {
        showFinalSummary.value = true
        summary.value = null
      } else {
        resetState()
      }
      return
    }

    hydrateState(response.data)

    if (response.data.status === 'STARTED' || (response.data.currentAllocation && response.data.currentAllocation.status === 'IN_PROGRESS')) {
      const isTuScannedOnBackend = response.data.currentAllocation?.tuScanned

      if (!isTuScannedOnBackend && !activeTuBarcode.value) {
        showTuScan.value = true
      } else {
        showTuScan.value = false
      }
    }
  } catch (error) {
    if (error?.response?.status === 404 && savedDestinationBarcode.value) {
      showFinalSummary.value = true
      summary.value = null
      return
    }
    if (error?.response?.status === 204) {
      if (savedDestinationBarcode.value) {
        showFinalSummary.value = true
        summary.value = null
      } else {
        resetState()
      }
      return
    }
    loadError.value = getErrorMessage(error, 'Failed to load operator task.')
    summary.value = null
  } finally {
    loading.value = false
  }
}

const forceRefreshTask = () => {
  localStorage.removeItem('active_tu_barcode')
  resetState()
  loadCurrentTask()
}

const goToDestinationScanScreen = () => {
  showFinalSummary.value = false
  showDestinationScan.value = true
}

const startTask = async () => {
  if (actionLoading.value) return
  actionLoading.value = true
  actionError.value = ''
  try {
    const response = await allocationApi.startCurrentTask()
    if (response?.data) {
      hydrateState(response.data)
    }
    showTuScan.value = true
  } catch (error) {
    actionError.value = getErrorMessage(error, 'Failed to start task.')
  } finally {
    actionLoading.value = false
  }
}

const submitTuScan = async () => {
  if (actionLoading.value) return

  if (!tuInput.value?.trim()) {
    actionError.value = 'Transport Unit barcode is required.'
    return
  }

  const cleanTu = tuInput.value.trim().toUpperCase()
  const tuRegex = /^TU\d{6}$/

  if (!tuRegex.test(cleanTu)) {
    actionError.value = "Invalid format! Must start with 'TU' followed by 6 digits."
    return
  }

  actionLoading.value = true
  actionError.value = ''

  try {
    const isOrderParam = !isReplenishmentTask.value
    const allocationId = summary.value?.allocations[0]?.allocationId

    if (!allocationId) {
      throw new Error('No active allocation instance found to attach TU.')
    }

    const response = await allocationApi.scanTransportUnit(allocationId, cleanTu, isOrderParam)

    activeTuBarcode.value = cleanTu
    localStorage.setItem('active_tu_barcode', cleanTu)

    if (response && response.data) {
      hydrateState(response.data)
    }

    showTuScan.value = false
    await loadCurrentTask()

  } catch (error) {
    if (error?.response?.status === 404) {
      actionError.value = `Scanned barcode does not exist in database: ${cleanTu}`
    } else {
      actionError.value = getErrorMessage(error, 'Transport Unit validation failed.')
    }
  } finally {
    actionLoading.value = false
  }
}

const submitBarcodeStep = async () => {
  if (actionLoading.value) return
  if (!currentAllocation.value || !barcodeInput.value?.trim()) {
    actionError.value = 'Barcode is required.'
    return
  }
  actionLoading.value = true
  actionError.value = ''
  const cleanBarcode = barcodeInput.value.trim().toUpperCase()

  try {
    if (!currentAllocation.value.sourceLocationScanned) {
      await allocationApi.scanSourceLocation(currentAllocation.value.allocationId, cleanBarcode)
    } else if (!currentAllocation.value.productScanned) {
      await allocationApi.scanProduct(currentAllocation.value.allocationId, cleanBarcode)
    }
    barcodeInput.value = ''
    await loadCurrentTask()
  } catch (error) {
    actionError.value = getErrorMessage(error, 'Barcode validation failed.')
  } finally {
    actionLoading.value = false
  }
}

const confirmQuantity = async () => {
  if (actionLoading.value) return
  if (!currentAllocation.value) return

  if (pickedQuantity.value < 0 || pickedQuantity.value > currentAllocation.value.requiredQuantity) return

  if (pickedQuantity.value < currentAllocation.value.requiredQuantity) {
    confirm.require({
      message: 'Confirm the lower quantity?',
      header: 'Confirm quantity',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Confirm',
      rejectLabel: 'Cancel',
      accept: submitPickedQuantity,
    })
    return
  }

  await submitPickedQuantity()
}

const submitPickedQuantity = async () => {
  if (!currentAllocation.value) return

  actionLoading.value = true
  actionError.value = ''

  try {
    const allocationId = currentAllocation.value.allocationId || currentAllocation.value.id

    await allocationApi.confirmPickedQuantity(allocationId, {
      pickedQuantity: pickedQuantity.value,
      shortageReason: pickedQuantity.value < currentAllocation.value.requiredQuantity ? 'SHORTAGE' : null,
      comment: null,
    })

    const response = await allocationApi.completeAssignedAllocation(allocationId)
    const completion = response.data

    const hasActiveAllocations = completion.summary?.currentAllocation != null;
    const allCanceled = completion.summary?.allocations?.length > 0 &&
      completion.summary.allocations.every(a => a.status === 'CANCELED');

    if (completion.orderStatus === 'CANCELED' || allCanceled) {
      localStorage.removeItem('active_tu_barcode')
      resetState()
      await loadCurrentTask()
      toast.add({
        severity: 'warn',
        summary: 'Task Cancelled',
        detail: 'No stock found. Task was cancelled and TU released.',
        life: 4000,
      })
      return
    }

    if (!hasActiveAllocations) {
      hydrateCompletionSummary(completion)
      showFinalSummary.value = true
      showDestinationScan.value = false
    } else {
      hydrateCompletionSummary(completion)
      showFinalSummary.value = false
      showDestinationScan.value = false
    }

    toast.add({
      severity: completion.newProcessCreated ? 'warn' : 'success',
      summary: 'Picking updated',
      detail: completion.message || 'Allocation completed.',
      life: 4000,
    })

    if (completion.newProcessCreated) {
      toast.add({
        severity: 'info',
        summary: 'Reallocation',
        detail: 'Alternative stock found. New task created.',
        life: 5000,
      })
    }
  } catch (error) {
    actionError.value = getErrorMessage(error, 'Wrong quantity.')
  } finally {
    actionLoading.value = false
  }
}

const submitDestinationScan = async () => {
  if (actionLoading.value) return
  if (!destinationInput.value?.trim()) {
    actionError.value = 'Destination barcode is required.'
    return
  }
  actionLoading.value = true
  actionError.value = ''

  const cleanDest = destinationInput.value.trim().toUpperCase()
  if (savedDestinationBarcode.value && cleanDest !== savedDestinationBarcode.value.toUpperCase()) {
    actionLoading.value = false
    actionError.value = `Location mismatch! Expected: ${savedDestinationBarcode.value}`
    return
  }

  try {
    const allocationId = finalAllocationsSummary.value[0]?.allocationId
    if (allocationId && activeTuBarcode.value) {
      await allocationApi.dispatchAllocation(allocationId, activeTuBarcode.value)
    }

    localStorage.removeItem('active_tu_barcode')

    if (!isReplenishmentTask.value) {
      await allocationApi.completeCurrentOrder()
    }

    resetState()
    await loadCurrentTask()
  } catch (error) {
    actionError.value = getErrorMessage(error, 'Destination validation failed.')
  } finally {
    actionLoading.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    forceRefreshTask()
  }
)

onMounted(() => {
  loadCurrentTask()
})
</script>

<style scoped>
.app-type-banner {
  border: 1px solid transparent;
}

.app-type-banner--repl {
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  border-color: rgba(37, 99, 235, 0.25);
}

.app-type-banner--pick {
  background: rgba(217, 119, 6, 0.1);
  color: #d97706;
  border-color: rgba(217, 119, 6, 0.25);
}

html.app-dark .app-type-banner--repl {
  background: rgba(96, 165, 250, 0.15);
  color: #60a5fa;
  border-color: rgba(96, 165, 250, 0.3);
}

html.app-dark .app-type-banner--pick {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
  border-color: rgba(251, 191, 36, 0.3);
}

.app-pill {
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.app-pill--repl {
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  border: 1px solid rgba(37, 99, 235, 0.25);
}

.app-pill--pick {
  background: rgba(217, 119, 6, 0.1);
  color: #d97706;
  border: 1px solid rgba(217, 119, 6, 0.25);
}

.app-pill--review {
  background: color-mix(in srgb, var(--status-warning) 14%, transparent);
  color: var(--status-warning);
  border: 1px solid color-mix(in srgb, var(--status-warning) 25%, transparent);
}

html.app-dark .app-pill--repl {
  background: rgba(96, 165, 250, 0.15);
  color: #60a5fa;
  border-color: rgba(96, 165, 250, 0.3);
}

html.app-dark .app-pill--pick {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
  border-color: rgba(251, 191, 36, 0.3);
}

.app-dispatch-banner {
  background: color-mix(in srgb, var(--status-info) 12%, var(--surface-card));
  color: var(--status-info);
  border: 1px solid color-mix(in srgb, var(--status-info) 22%, transparent);
}

html.app-dark .app-dispatch-banner {
  background: color-mix(in srgb, var(--status-info) 18%, var(--surface-card));
  color: color-mix(in srgb, var(--status-info) 85%, white);
  border-color: color-mix(in srgb, var(--status-info) 28%, transparent);
}

.app-accent {
  color: #2563eb;
}

html.app-dark .app-accent {
  color: #60a5fa;
}

.app-barcode {
  color: #7c3aed;
}

html.app-dark .app-barcode {
  color: #c4b5fd;
}

.app-divider {
  border-bottom: 1px solid var(--border-subtle);
}

.app-divider-dashed {
  border-top: 1px dashed var(--border-subtle);
}

.app-chip {
  background: var(--surface-card-muted);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
}

.app-progress-dot {
  transition: all 0.2s;
  background: var(--border-subtle);
}

.app-progress-dot--active {
  background: var(--brand-warm);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--brand-warm) 18%, transparent);
  transform: scale(1.1);
}

.app-progress-dot--done {
  background: var(--status-success);
}

.app-progress-dot--pending {
  background: var(--border-subtle);
}

.app-step-circle {
  background: var(--surface-card);
  border-color: var(--border-subtle);
  color: var(--text-muted);
  transition: all 0.3s;
}

.app-step-circle--active {
  border-color: var(--brand-warm);
  color: var(--brand-warm);
  font-weight: 800;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--brand-warm) 10%, transparent);
  transform: scale(1.05);
}

.app-step-circle--done {
  background: var(--status-success);
  border-color: var(--status-success);
  color: var(--text-inverse);
  font-weight: 700;
}

.app-step-circle--pending {
  background: var(--surface-card);
  border-color: var(--border-subtle);
  color: var(--text-muted);
}

.app-step-line {
  background: var(--border-subtle);
  transition: background 0.3s;
}

.app-step-line--done {
  background: var(--status-success);
}

.app-qty-input {
  background: var(--surface-card-muted) !important;
  color: var(--text-primary) !important;
}

.app-scan-input {
  background: var(--surface-card) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-subtle) !important;
}

.app-scan-input:focus {
  border-color: var(--brand-warm) !important;
}

.app-scanner-frame {
  border: 2px dashed var(--brand-accent);
  background: color-mix(in srgb, var(--brand-accent) 6%, transparent);
}
</style>
