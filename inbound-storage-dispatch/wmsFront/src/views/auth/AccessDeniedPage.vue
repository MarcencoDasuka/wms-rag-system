<template>
  <div class="app-shell flex items-center justify-center p-6">
    <div class="absolute right-6 top-6">
      <ThemeToggle />
    </div>
    <Card class="app-card w-full max-w-md text-center">
      <template #title>
        <div class="app-title flex flex-col items-center gap-3">
          <i class="pi pi-lock text-4xl app-danger"></i>
          <span>Access Denied</span>
        </div>
      </template>
      <template #content>
        <p class="app-subtitle mb-6">Your current role does not allow access to this page.</p>
        <Button label="Return to Dashboard" icon="pi pi-arrow-left" @click="goToDashboard" />
      </template>
    </Card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

import Button from 'primevue/button'
import Card from 'primevue/card'
import ThemeToggle from '@/components/ThemeToggle.vue'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const goToDashboard = () => {
  router.push(authStore.dashboardPath)
}
</script>
