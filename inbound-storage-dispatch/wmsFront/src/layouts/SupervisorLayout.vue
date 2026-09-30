<template>
  <div class="app-shell font-sans flex">
    <aside
      class="app-sidebar app-sidebar-desktop w-64 shadow-xl hidden md:flex md:flex-col md:shrink-0"
    >
      <div class="app-sidebar-scroll min-h-0 flex-1">
        <SidebarBrand />
        <div class="px-4 pb-4 flex flex-col gap-2">
          <SidebarNavigation :items="menuItems" />
          <div class="app-sidebar-divider"></div>
          <ThemeToggle show-label class="w-full justify-start sidebar-action-btn" />
          <Button
            icon="pi pi-sign-out"
            label="Logout"
            severity="secondary"
            text
            class="w-full justify-start sidebar-action-btn"
            @click="handleLogout"
          />
        </div>
      </div>
    </aside>

    <div
      v-if="mobileMenuOpen"
      class="mobile-sidebar-backdrop md:hidden"
      @click="closeMobileMenu"
    ></div>

    <aside
      class="app-sidebar mobile-sidebar shadow-xl flex flex-col md:hidden"
      :class="{ 'mobile-sidebar--open': mobileMenuOpen }"
    >
      <div class="app-sidebar-scroll min-h-0 flex-1">
        <div class="flex items-center justify-between border-b border-white/10">
          <SidebarBrand />
          <Button
            icon="pi pi-times"
            text
            rounded
            severity="secondary"
            aria-label="Close menu"
            class="mr-3"
            @click="closeMobileMenu"
          />
        </div>
        <div class="px-4 pb-4 flex flex-col gap-2">
          <SidebarNavigation :items="menuItems" @navigate="closeMobileMenu" />
          <div class="app-sidebar-divider"></div>
          <ThemeToggle show-label class="w-full justify-start sidebar-action-btn" />
          <Button
            icon="pi pi-sign-out"
            label="Logout"
            severity="secondary"
            text
            class="w-full justify-start sidebar-action-btn"
            @click="handleLogout"
          />
        </div>
      </div>
    </aside>

    <main class="flex-1 min-w-0 overflow-y-auto">
      <header
        class="app-header sticky top-0 z-30 flex items-center justify-between gap-3 p-4 md:hidden"
      >
        <div class="flex items-center gap-3">
          <img
            :src="isDark ? '/white_logo.png' : '/color_white_logo.png'"
            alt="Inbound Storage Dispatch logo"
            class="h-8 w-auto object-contain"
          />
          <span class="app-title text-lg font-bold tracking-wide">ISD WMS</span>
        </div>
        <Button
          icon="pi pi-bars"
          text
          rounded
          severity="secondary"
          aria-label="Open menu"
          @click="openMobileMenu"
        />
      </header>
      <router-view />
    </main>

    <AiChatWidget v-if="['ROLE_SUPERVISOR', 'ROLE_DEV'].includes(authStore.role)" />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import Button from 'primevue/button'
import 'primeicons/primeicons.css'
import ThemeToggle from '@/components/ThemeToggle.vue'
import AiChatWidget from '@/components/AiChatWidget.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const mobileMenuOpen = ref(false)
const { isDark } = useTheme()

const SidebarBrand = defineComponent({
  name: 'SidebarBrand',
  setup() {
    return () =>
      h('div', { class: 'pt-5 pb-4 px-6 flex items-center gap-3' }, [
        h('img', {
          src: isDark.value ? '/white_logo.png' : '/color_white_logo.png',
          alt: 'Inbound Storage Dispatch logo',
          class: 'h-9 w-auto object-contain',
        }),
        h('h1', { class: 'app-title text-xl font-bold tracking-wide' }, 'ISD WMS'),
      ])
  },
})

const SidebarNavigation = defineComponent({
  name: 'SidebarNavigation',
  props: {
    items: {
      type: Array,
      required: true,
    },
  },
  emits: ['navigate'],
  setup(props, { emit }) {
    return () =>
      h(
        'nav',
        { class: 'flex flex-col gap-2' },
        props.items.map((item) =>
          h(
            RouterLink,
            {
              key: item.to,
              to: item.to,
              exactActiveClass: 'app-nav-link-active',
              activeClass: 'app-nav-link-active',
              class: 'app-nav-link py-[10px] px-3 rounded-lg transition flex items-center gap-3',
              onClick: () => emit('navigate'),
            },
            () => [h('i', { class: item.icon }), item.label],
          ),
        ),
      )
  },
})

const menuItems = computed(() => {
  const isOperator = authStore.role === 'ROLE_OPERATOR'
  if (isOperator) {
    return [{ to: '/operator', label: 'Operator Console', icon: 'pi pi-box' }]
  }

  const items = [
    { to: '/supervisor/dashboard', label: 'Dashboard', icon: 'pi pi-chart-bar', exact: true },
    { to: '/supervisor/orders', label: 'Order', icon: 'pi pi-cart-arrow-down' },
    { to: '/supervisor/replenishments', label: 'Replenishments', icon: 'pi pi-sync' },
    { to: '/supervisor/allocations', label: 'Allocations', icon: 'pi pi-list' },
    { to: '/supervisor/inventory', label: 'Inventory', icon: 'pi pi-table' },
    { to: '/supervisor/products', label: 'Products', icon: 'pi pi-tags' },
    { to: '/supervisor/locations', label: 'Locations', icon: 'pi pi-map-marker' },
    { to: '/supervisor/users', label: 'Users', icon: 'pi pi-users' },
    { to: '/supervisor/history', label: 'History', icon: 'pi pi-history' },
  ]

  return items
})

const openMobileMenu = () => {
  mobileMenuOpen.value = true
}

const closeMobileMenu = () => {
  mobileMenuOpen.value = false
}

const handleLogout = () => {
  closeMobileMenu()
  authStore.logout()
  router.push({ name: 'login', query: { loggedOut: '1' } })
}

watch(
  () => route.fullPath,
  () => closeMobileMenu(),
)
</script>

<style scoped>
.app-sidebar-desktop {
  height: 100vh;
  position: sticky;
  top: 0;
}

.app-sidebar-scroll {
  overflow-y: auto;
}

.app-sidebar-divider {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  margin: 0.4rem 0; /* Redus nesemnificativ de la 0.5rem */
}

.sidebar-action-btn:deep(.p-button) {
  justify-content: flex-start;
  width: 100%;
}

.mobile-sidebar {
  bottom: 0;
  left: 0;
  max-width: 20rem;
  position: fixed;
  top: 0;
  transform: translateX(-100%);
  transition: transform 0.2s ease;
  width: min(82vw, 20rem);
  z-index: 50;
}

.mobile-sidebar--open {
  transform: translateX(0);
}

.mobile-sidebar-backdrop {
  background: rgba(0, 0, 0, 0.48);
  inset: 0;
  position: fixed;
  z-index: 40;
}
</style>
