<template>
  <div class="app-table-shell">
    <div v-if="showSearch || $slots.toolbar" class="app-table-toolbar">
      <div v-if="$slots.toolbar" class="app-table-toolbar__actions">
        <slot name="toolbar" />
      </div>
      <div v-if="showSearch" class="app-table-toolbar__search">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="filters.global.value" placeholder="Search table" size="small" />
        </IconField>
      </div>
    </div>

    <DataTable
      v-model:filters="filters"
      :value="value"
      :loading="loading"
      :dataKey="dataKey"
      :emptyMessage="emptyMessage"
      :rows="rows"
      :rowsPerPageOptions="rowsPerPageOptions"
      :globalFilterFields="globalFilterFields"
      :filterDisplay="showSearch ? 'menu' : undefined"
      :paginator="showPaginator"
      stripedRows
      responsiveLayout="scroll"
      class="app-data-table p-datatable-sm"
      :paginatorTemplate="showPaginator ? 'FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport' : undefined"
      :currentPageReportTemplate="showPaginator ? '{first}-{last} of {totalRecords}' : undefined"
      v-bind="$attrs"
    >
      <template v-for="(_, slotName) in dataTableSlots" #[slotName]="slotProps">
        <slot :name="slotName" v-bind="slotProps || {}" />
      </template>
    </DataTable>
  </div>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { FilterMatchMode, FilterOperator } from '@primevue/core/api'
import DataTable from 'primevue/datatable'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  value: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  dataKey: { type: String, default: 'id' },
  emptyMessage: { type: String, default: 'No records found.' },
  rows: { type: Number, default: 10 },
  rowsPerPageOptions: { type: Array, default: () => [10, 20, 50] },
  filterFields: { type: Array, default: () => [] },
  showSearch: { type: Boolean, default: true },
  showPaginator: { type: Boolean, default: true }
})

const slots = defineSlots()
const filters = reactive({})

const dataTableSlots = computed(() => {
  const { toolbar: _toolbar, ...rest } = slots
  return rest
})

const normalizedFilterFields = computed(() => props.filterFields.map((field) => {
  if (typeof field === 'string') return { field, label: field }
  return field
}).filter((field) => field?.field))

const globalFilterFields = computed(() => normalizedFilterFields.value.map(({ field }) => field))

const buildFilters = () => {
  const nextFilters = {
    global: { value: null, matchMode: FilterMatchMode.CONTAINS }
  }

  for (const { field } of normalizedFilterFields.value) {
    nextFilters[field] = {
      operator: FilterOperator.AND,
      constraints: [{ value: null, matchMode: FilterMatchMode.CONTAINS }]
    }
  }

  return nextFilters
}

const resetFilters = () => {
  const nextFilters = buildFilters()
  for (const key of Object.keys(filters)) delete filters[key]
  Object.assign(filters, nextFilters)
}

watch(normalizedFilterFields, resetFilters, { immediate: true })
</script>
