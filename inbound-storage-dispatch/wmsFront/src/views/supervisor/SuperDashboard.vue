<template>
  <div class="supervisor-dashboard p-4 md:p-6">
    <div class="flex justify-start mb-4 pb-2">
      <Button
        icon="pi pi-refresh"
        severity="secondary"
        outlined
        :loading="loading"
        aria-label="Refresh"
        @click="loadDashboard"
      />
    </div>

    <Message v-if="errorMessage" severity="error" :closable="false" class="mb-6">
      <div class="flex items-center justify-between gap-3 flex-wrap">
        <span>{{ errorMessage }}</span>
        <Button
          label="Retry"
          icon="pi pi-refresh"
          severity="danger"
          outlined
          size="small"
          :loading="loading"
          @click="loadDashboard"
        />
      </div>
    </Message>

    <div class="dashboard-kpis mb-6">
      <template v-if="loading">
        <Card v-for="index in 8" :key="index" class="app-card border-none shadow-lg kpi-card">
          <template #content>
            <Skeleton height="4.5rem" />
          </template>
        </Card>
      </template>
      <template v-else>
        <Card
          v-for="card in kpiCards"
          :key="card.key"
          class="app-card border-none shadow-lg kpi-card"
        >
          <template #content>
            <div class="flex items-center justify-between gap-4">
              <div class="app-subtitle text-sm font-semibold">{{ card.title }}</div>
              <div class="app-title text-2xl font-bold shrink-0">
                {{ formatNumber(card.value) }}
              </div>
            </div>
          </template>
        </Card>
      </template>
    </div>

    <Panel class="notification app-card border-none m-10 shadow-lg">
      <template #header>
        <div class="flex items-center gap-2">
          <i class="pi pi-bell app-danger"></i>
          <span class="font-semibold">Needs Attention</span>
        </div>
      </template>
      <template #icons>
        <Badge :value="needsAttentionCountLabel" severity="danger" />
      </template>

      <div v-if="loading" class="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <Skeleton v-for="index in 4" :key="index" height="5.5rem" />
      </div>
      <div
        v-else-if="dashboard.needsAttention.length"
        class="grid grid-cols-1 xl:grid-cols-2 gap-4"
      >
        <Card
          v-for="item in dashboard.needsAttention"
          :key="attentionKey(item)"
          class="attention-card"
        >
          <template #content>
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2 mb-2 flex-wrap">
                  <Tag :severity="severityToPrime(item.severity)" :value="item.severity" />
                  <span class="text-sm app-muted">{{ formatDateTime(item.createdAt) }}</span>
                </div>
                <div class="font-semibold mb-1">{{ item.title }}</div>
                <div class="app-muted text-sm">{{ item.description }}</div>
              </div>
              <Badge
                v-if="item.relatedEntityId"
                :value="`#${item.relatedEntityId}`"
                severity="contrast"
              />
            </div>
          </template>
        </Card>
      </div>
      <Message v-else severity="success" :closable="false">No urgent issues right now.</Message>
    </Panel>

    <div class="dashboard-charts mb-6">
      <Card class="app-card border-none shadow-lg">
        <template #title>Orders by Status</template>
        <template #content>
          <Chart
            v-if="!loading && ordersByStatusChartData.labels.length"
            type="doughnut"
            :data="ordersByStatusChartData"
            :options="doughnutChartOptions"
          />
          <Skeleton v-else-if="loading" height="18rem" />
          <Message v-else severity="info" :closable="false"
            >No order status data available.</Message
          >
        </template>
      </Card>

      <Card class="app-card border-none shadow-lg">
        <template #title>Completed Orders Trend</template>
        <template #content>
          <Chart
            v-if="!loading && completedOrdersTrendChartData.labels.length"
            type="line"
            :data="completedOrdersTrendChartData"
            :options="lineChartOptions"
          />
          <Skeleton v-else-if="loading" height="18rem" />
          <Message v-else severity="info" :closable="false"
            >No completion trend data available.</Message
          >
        </template>
      </Card>

      <Card class="app-card border-none shadow-lg">
        <template #title>Operator Performance</template>
        <template #content>
          <Chart
            v-if="!loading && operatorPerformanceChartData.labels.length"
            type="bar"
            :data="operatorPerformanceChartData"
            :options="barChartOptions"
          />
          <Skeleton v-else-if="loading" height="18rem" />
          <Message v-else severity="info" :closable="false"
            >No operator performance data available.</Message
          >
        </template>
      </Card>
    </div>

    <div class="dashboard-secondary mb-6 pt-6">
      <Card class="app-card border-none shadow-lg activity-card">
        <template #title>Live Activity Feed</template>
        <template #content>
          <div v-if="dashboard.activityFeed.length" class="activity-feed-shell">
            <Timeline :value="dashboard.activityFeed" align="left" class="activity-timeline">
              <template #content="{ item }">
                <div class="mb-4">
                  <div class="flex items-center gap-2 flex-wrap mb-1">
                    <Tag :value="prettyType(item.type)" severity="info" />
                    <span class="app-muted text-xs">{{ formatDateTime(item.createdAt) }}</span>
                  </div>
                  <div class="font-medium">{{ item.message }}</div>
                  <div v-if="item.actorName" class="app-muted text-sm mt-1">
                    {{ item.actorName }}
                  </div>
                </div>
              </template>
            </Timeline>
          </div>
          <Message v-else severity="info" :closable="false">No recent activity available.</Message>
        </template>
      </Card>

      <Card class="app-card border-none shadow-lg workload-card">
        <template #title>Operator Workload</template>
        <template #content>
          <DataTable
            v-if="dashboard.operatorPerformance.length"
            :value="dashboard.operatorPerformance"
            responsiveLayout="scroll"
            stripedRows
            scrollable
            scrollHeight="22rem"
          >
            <Column field="operatorName" header="Operator" />
            <Column field="status" header="Status">
              <template #body="{ data }">
                <Tag :severity="operatorStatusSeverity(data.status)" :value="data.status" />
              </template>
            </Column>
            <Column field="activeTasks" header="Active Tasks" />
            <Column field="completedOrdersToday" header="Completed Today" />
            <Column field="averageCompletionTimeMinutes" header="Avg Completion">
              <template #body="{ data }">
                {{ formatMinutes(data.averageCompletionTimeMinutes) }}
              </template>
            </Column>
          </DataTable>
          <Message v-else severity="info" :closable="false">
            No operator workload data available.
          </Message>
        </template>
      </Card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

import Badge from 'primevue/badge'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Chart from 'primevue/chart'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Message from 'primevue/message'
import Panel from 'primevue/panel'
import Skeleton from 'primevue/skeleton'
import Tag from 'primevue/tag'
import Timeline from 'primevue/timeline'

import { dashboardService } from '@/services/dashboardService'

const emptyDashboard = () => ({
  summary: {
    ordersToday: 0,
    completedOrdersToday: 0,
    inProgressOrders: 0,
    shortageOrders: 0,
    canceledOrdersToday: 0,
    activeOperators: 0,
    totalOperators: 0,
    averageCompletionTimeMinutes: 0,
    lowStockProducts: 0,
    ordersWaitingForDispatch: 0,
    stockMovementsToday: 0,
    inventoryAdjustmentsToday: 0,
    failedScanAttemptsToday: 0,
  },
  ordersByStatus: [],
  completedOrdersTrend: [],
  operatorPerformance: [],
  topPickedProducts: [],
  lowStockItems: [],
  mostActiveLocations: [],
  needsAttention: [],
  activityFeed: [],
  generatedAt: null,
})

const dashboard = ref(emptyDashboard())
const loading = ref(false)
const errorMessage = ref('')

const chartPalette = () => {
  const style = getComputedStyle(document.documentElement)
  return {
    primary: style.getPropertyValue('--brand-primary').trim() || '#0e7490',
    accent: style.getPropertyValue('--brand-accent').trim() || '#2563eb',
    success: style.getPropertyValue('--status-success').trim() || '#059669',
    warning: style.getPropertyValue('--status-warning').trim() || '#d97706',
    danger: style.getPropertyValue('--status-danger').trim() || '#dc2626',
    text: style.getPropertyValue('--text-secondary').trim() || '#52657a',
    border: style.getPropertyValue('--border-subtle').trim() || '#d8e3ee',
  }
}

const formatNumber = (value) => new Intl.NumberFormat().format(Number(value ?? 0))

const formatDateTime = (value) => {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

const formatMinutes = (value) => `${formatNumber(value)} min`

const prettyType = (value) =>
  String(value || '')
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase())

const getErrorMessage = (error) =>
  error.response?.data?.message ||
  error.response?.data?.error ||
  'Unable to load supervisor dashboard.'

const loadDashboard = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    dashboard.value = await dashboardService.getSupervisorDashboard()
  } catch (error) {
    dashboard.value = emptyDashboard()
    errorMessage.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

const needsAttentionCountLabel = computed(() => String(dashboard.value.needsAttention.length))

const severityToPrime = (value) => {
  if (value === 'CRITICAL') return 'danger'
  if (value === 'WARNING') return 'warn'
  return 'info'
}

const operatorStatusSeverity = (value) => {
  if (value === 'DELAYED') return 'danger'
  if (value === 'IDLE') return 'warn'
  return 'success'
}

const attentionKey = (item) =>
  `${item.type}-${item.relatedEntityId || item.title}-${item.createdAt || ''}`

const buildOrdersByStatusChart = () => {
  const palette = chartPalette()

  const colorMap = {
    CREATED: '#cbd5e1',
    ASSIGNED: '#93c5fd',
    IN_PROGRESS: palette.accent,
    PROCESSING: palette.primary,
    PICKED: '#818cf8',
    PARTIALLY_COMPLETED: '#fbbf24',
    SHORTAGE: palette.warning,
    COMPLETED: palette.success,
    DELIVERED: palette.success,
    CANCELLED: palette.danger,
    CANCELED: palette.danger,
    FAILED: palette.danger,
  }

  return {
    labels: dashboard.value.ordersByStatus.map((item) => prettyType(item.status)),
    datasets: [
      {
        data: dashboard.value.ordersByStatus.map((item) => item.count),
        backgroundColor: dashboard.value.ordersByStatus.map((item) => {
          const statusKey = String(item.status || '').toUpperCase()
          return colorMap[statusKey] || '#94a3b8'
        }),
      },
    ],
  }
}

const buildCompletedOrdersTrendChart = () => {
  const palette = chartPalette()
  return {
    labels: dashboard.value.completedOrdersTrend.map((item) =>
      new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(
        new Date(item.date),
      ),
    ),
    datasets: [
      {
        label: 'Completed Orders',
        data: dashboard.value.completedOrdersTrend.map((item) => item.count),
        borderColor: palette.primary,
        backgroundColor: `${palette.primary}22`,
        tension: 0.35,
        fill: true,
      },
    ],
  }
}

const buildOperatorPerformanceChart = () => {
  const palette = chartPalette()
  return {
    labels: dashboard.value.operatorPerformance.map((item) => item.operatorName),
    datasets: [
      {
        label: 'Completed Today',
        data: dashboard.value.operatorPerformance.map((item) => item.completedOrdersToday),
        backgroundColor: palette.success,
      },
    ],
  }
}

const kpiCards = computed(() => {
  const summary = dashboard.value.summary

  return [
    { key: 'ordersToday', title: 'Orders Created Today', value: summary.ordersToday },
    {
      key: 'completedOrdersToday',
      title: 'Orders Completed Today',
      value: summary.completedOrdersToday,
    },
    { key: 'inProgressOrders', title: 'Orders In Progress', value: summary.inProgressOrders },
    { key: 'shortageOrders', title: 'Orders With Shortage', value: summary.shortageOrders },
    {
      key: 'canceledOrdersToday',
      title: 'Orders Canceled Today',
      value: summary.canceledOrdersToday,
    },
    { key: 'activeOperators', title: 'Active Operators', value: summary.activeOperators },
  ]
})

const ordersByStatusChartData = computed(() => buildOrdersByStatusChart())
const completedOrdersTrendChartData = computed(() => buildCompletedOrdersTrendChart())
const operatorPerformanceChartData = computed(() => buildOperatorPerformanceChart())
const sharedScales = () => {
  const palette = chartPalette()
  return {
    x: {
      ticks: { color: palette.text },
      grid: { color: `${palette.border}66` },
    },
    y: {
      ticks: { color: palette.text, precision: 0 },
      grid: { color: `${palette.border}66` },
    },
  }
}

const lineChartOptions = computed(() => ({
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: chartPalette().text } } },
  scales: {
    x: sharedScales().x,
    y: {
      ...sharedScales().y,
      min: 0,
      beginAtZero: true,
    },
  },
}))

const barChartOptions = computed(() => ({
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: chartPalette().text } } },
  scales: sharedScales(),
}))

const doughnutChartOptions = computed(() => ({
  maintainAspectRatio: false,
  plugins: { legend: { position: 'bottom', labels: { color: chartPalette().text } } },
}))

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.supervisor-dashboard {
  min-height: 100%;
}

.dashboard-kpis {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.dashboard-charts {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.dashboard-charts :deep(.p-card-body),
.dashboard-charts :deep(.p-card-content) {
  height: 100%;
}

.dashboard-charts :deep(canvas) {
  min-height: 18rem;
}

.dashboard-secondary {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  align-items: start;
}

.activity-card,
.workload-card {
  min-height: 31rem;
}

.activity-card :deep(.p-card-body),
.workload-card :deep(.p-card-body) {
  height: 100%;
}

.kpi-card {
  min-height: 0;
}

.kpi-card :deep(.p-card-body) {
  padding: 0.9rem 1rem;
}

.kpi-card :deep(.p-card-content) {
  padding: 0;
}

.attention-card {
  background: color-mix(in srgb, var(--surface-card) 92%, var(--status-danger) 8%);
  border: 1px solid color-mix(in srgb, var(--status-danger) 18%, var(--border-subtle));
  box-shadow: none;
}

.activity-timeline :deep(.p-timeline-event-opposite) {
  display: none;
}

.activity-feed-shell {
  max-height: 24rem;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.activity-card :deep(.p-card-body) {
  min-height: 0;
}

.workload-card :deep(.p-card-body) {
  min-height: 0;
}

.workload-card :deep(.p-datatable-table-container) {
  border-radius: 6px;
}

.activity-card :deep(.p-card-content),
.workload-card :deep(.p-card-content) {
  overflow: hidden;
}

@media (max-width: 1199px) {
  .dashboard-secondary {
    grid-template-columns: minmax(0, 1fr);
  }

  .activity-feed-shell {
    max-height: 22rem;
  }
}
</style>
