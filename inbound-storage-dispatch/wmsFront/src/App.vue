<script setup>
import { RouterView } from 'vue-router'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { setGlobalToast } from '@/services/notificationService'

const toast = useToast()
setGlobalToast(toast)
</script>

<template>
  <Toast position="top-right" />

  <RouterView />
</template>

<style>
</style>
