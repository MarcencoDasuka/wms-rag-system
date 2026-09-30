<template>
  <div class="p-6">

    <AppDataTable
      :value="allocations"
      :loading="loading"
      :filterFields="allocationFilterFields"
      paginator
      :rows="10"
      dataKey="allocationId"
      stripedRows
      emptyMessage="No active allocations found."
    >
      <template #toolbar>
        <Button
          icon="pi pi-refresh"
          size="small"
          severity="secondary"
          outlined
          :loading="loading"
          @click="loadAllocations"
        />
      </template>

      <Column field="formattedType" header="Task Type" sortable filter>
        <template #body="{ data }">
          <Tag :value="data.formattedType" :severity="data.type === 'REPLENISHMENT' ? 'info' : 'success'" />
        </template>
      </Column>

      <Column header="Reference" field="reference" sortable filter>
        <template #body="{ data }">
          <AllocationReferenceLink
            :type="data.type"
            :order-id="data.orderId"
            :replenishment-id="data.replenishmentId"
          />
        </template>
      </Column>

      <Column field="productName" header="Product" sortable filter>
        <template #body="{ data }">
          {{ data.productName }}
        </template>
      </Column>

      <Column field="locationName" header="Location" sortable filter />

      <Column field="requestedQuantity" header="Requested Qty" sortable filter>
        <template #body="{ data }">
          <span class="font-bold">
            {{ data.requestedQuantity }}
          </span>
        </template>
      </Column>

      <Column field="deliveredQuantity" header="Delivered Qty" sortable filter>
        <template #body="{ data }">
          <span class="font-bold">
            {{ data.deliveredQuantity ?? 0 }}
          </span>
        </template>
      </Column>

      <Column field="sourceLocationScanned" header="Location Scan">
        <template #body="{ data }">
          <i
            class="pi"
            :class="
              data.sourceLocationScanned
                ? 'pi-check-circle text-green-500'
                : 'pi-times-circle text-red-500'
            "
          />
        </template>
      </Column>

      <Column field="productScanned" header="Product Scan">
        <template #body="{ data }">
          <i
            class="pi"
            :class="
              data.productScanned
                ? 'pi-check-circle text-green-500'
                : 'pi-times-circle text-red-500'
            "
          />
        </template>
      </Column>

      <Column field="formattedStatus" header="Status" sortable filter>
        <template #body="{ data }">
          <Tag :value="data.formattedStatus" :severity="statusSeverity(data.status)" />
        </template>
      </Column>
    </AppDataTable>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'

import Button from 'primevue/button'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Toast from 'primevue/toast'

import { allocationApi } from '@/api/allocationApi.js'
import AllocationReferenceLink from '@/components/AllocationReferenceLink.vue'

const toast = useToast()

const allocations = ref([])
const loading = ref(false)

const formatString = (str) => {
  if (!str) return '';
  return String(str).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

const allocationFilterFields = [
  { field: 'formattedType', label: 'Task Type' },
  { field: 'reference', label: 'Reference' },
  { field: 'productName', label: 'Product' },
  { field: 'locationName', label: 'Location' },
  { field: 'requestedQuantity', label: 'Requested Qty' },
  { field: 'deliveredQuantity', label: 'Delivered Qty' },
  { field: 'formattedStatus', label: 'Status' }
]

const getErrorMessage = (error) => {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    'Request failed.'
  )
}

const statusSeverity = (status) => {
  switch (status?.toUpperCase()?.replace(/ /g, '_')) {
    case 'COMPLETED':
      return 'success'
    case 'PARTIALLY_COMPLETED':
      return 'warning'
    case 'CANCELED':
    case 'CANCELLED':
      return 'danger'
    case 'IN_PROGRESS':
      return 'warn'
    case 'ASSIGNED':
      return 'info'
    case 'CREATED':
      return 'secondary'
    default:
      return 'contrast'
  }
}

const loadAllocations = async () => {
  loading.value = true

  try {
    const response = await allocationApi.getSupervisorAllocations()
    allocations.value = response.data.map(item => ({
      ...item,
      formattedType: formatString(item.type),
      formattedStatus: formatString(item.status),
      reference: item.type === 'REPLENISHMENT' ? `REPL-${item.replenishmentId}` : `ORD-${item.orderId}`
    }))

  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Allocation load failed',
      detail: getErrorMessage(error),
      life: 4000,
    })
  } finally {
    loading.value = false
  }
}

onMounted(loadAllocations)
</script>
