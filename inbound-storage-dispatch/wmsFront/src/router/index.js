import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

import LoginView from '../views/auth/LoginView.vue'
import AccessDeniedPage from '../views/auth/AccessDeniedPage.vue'
import SupervisorLayout from '../layouts/SupervisorLayout.vue'
import SuperDashboard from '../views/supervisor/SuperDashboard.vue'
import ProductsView from '../views/supervisor/ProductsView.vue'
import LocationsView from '../views/supervisor/LocationsView.vue'
import InventoryView from '../views/supervisor/InventoryView.vue'
import TasksView from '../views/supervisor/TasksView.vue'
import HistoryView from '../views/supervisor/HistoryView.vue'
import UsersView from '../views/supervisor/UsersView.vue'
import OperatorConsole from '../views/operator/OperatorConsole.vue'
import VerifyEmailView from '../views/auth/VerifyEmailView.vue'
import OrderForm from '../views/supervisor/OrderView.vue'
import ReplenishmentsView from '../views/supervisor/ReplenishmentsView.vue'
import AllocationsView from '@/views/supervisor/AllocationsView.vue'

const DEV = 'ROLE_DEV'
const SUPERVISOR = 'ROLE_SUPERVISOR'
const OPERATOR = 'ROLE_OPERATOR'
const SUPERVISOR_OR_DEV = [SUPERVISOR, DEV]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
    {
      path: '/access-denied',
      component: SupervisorLayout,
      meta: { requiresAuth: true },
      children: [{ path: '', name: 'access-denied', component: AccessDeniedPage }],
    },
    {
      path: '/supervisor',
      alias: '/dev',
      component: SupervisorLayout,
      meta: { requiresAuth: true, roles: SUPERVISOR_OR_DEV },
      children: [
        {
          path: '',
          alias: ['/dashboard', '/dev'],
          name: 'supervisor-dashboard',
          component: SuperDashboard,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        { path: 'dashboard', component: SuperDashboard, meta: { roles: SUPERVISOR_OR_DEV } },
        {
          path: 'inventory',
          alias: '/inventory',
          name: 'inventory',
          component: InventoryView,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        {
          path: 'allocations',
          name: 'allocations',
          component: AllocationsView,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        {
          path: 'replenishments',
          name: 'replenishments',
          component: ReplenishmentsView,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        {
          path: 'products',
          name: 'products',
          component: ProductsView,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        {
          path: 'locations',
          name: 'locations',
          component: LocationsView,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        {
          path: 'history',
          alias: '/inventory/history',
          name: 'history',
          component: HistoryView,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
        { path: 'users', name: 'users', component: UsersView, meta: { roles: SUPERVISOR_OR_DEV } },
        {
          path: 'orders',
          name: 'orders',
          component: OrderForm,
          meta: { roles: SUPERVISOR_OR_DEV },
        },
      ],
    },
    {
      path: '/operator',
      name: 'operator',
      component: OperatorConsole,
      meta: { requiresAuth: true, roles: [OPERATOR] },
    },
    {
      path: '/verify',
      name: 'verify',
      component: VerifyEmailView,
      meta: { guestOnly: true },
    },
  ],
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return next(authStore.dashboardPath)
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }

  const allowedRoles = to.meta.roles || []
  if (to.meta.requiresAuth && allowedRoles.length && !authStore.hasAnyRole(allowedRoles)) {
    return next({ name: 'access-denied' })
  }

  next()
})

export default router
