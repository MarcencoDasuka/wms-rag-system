<template>
  <div
    class="app-shell relative flex flex-col items-center justify-center px-6 py-16 sm:px-8 lg:px-12 min-h-screen"
  >
    <Toast />
    <div class="absolute right-6 top-6">
      <ThemeToggle />
    </div>

    <div class="w-full max-w-md mb-12 flex flex-col items-center justify-center text-center">
      <img
        :src="isDark ? '/white_logo.png' : '/color_logo.png'"
        alt="Inbound Storage Dispatch logo"
        class="mx-auto block h-32 w-auto max-w-[85vw] object-contain sm:h-40 transition-all duration-300 mb-2"
      />
      <h2 class="app-brand text-3xl font-extrabold tracking-tight sm:text-4xl pb-4">
        Inbound Storage Dispatch
      </h2>
    </div>

    <Card class="app-card w-full max-w-md shadow-lg rounded-xl">
      <template #content>
        <form @submit.prevent="handleLogin" class="flex flex-col gap-5 py-2">
          <div class="flex flex-col gap-2">
            <label for="username" class="app-subtitle font-medium text-sm sm:text-base"
              >Username or Email</label
            >
            <InputText id="username" v-model.trim="username" type="text" class="w-full p-3" />
            <small v-if="submitted && !username" class="app-danger mt-1 text-xs"
              >Username or email is required.</small
            >
          </div>

          <div class="flex flex-col gap-2">
            <label for="password" class="app-subtitle font-medium text-sm sm:text-base"
              >Password</label
            >
            <Password
              id="password"
              v-model="password"
              :feedback="false"
              toggleMask
              inputClass="w-full p-3"
              class="w-full [&>input]:w-full"
            />
            <small v-if="submitted && !password" class="app-danger mt-1 text-xs"
              >Password is required.</small
            >
          </div>

          <Message v-if="errorMessage" severity="error" :closable="false" class="mt-2 text-sm">
            {{ errorMessage }}
          </Message>

          <Button
            type="submit"
            :label="isLoading ? 'Logging In...' : 'Log In'"
            :loading="isLoading"
            class="w-full mt-4 p-3 font-semibold text-base"
          />
        </form>
      </template>
    </Card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'

import { useTheme } from '@/composables/useTheme'

import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Toast from 'primevue/toast'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()

const { isDark } = useTheme()

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const isLoading = ref(false)
const submitted = ref(false)

const getErrorMessage = (error) => {
  return (
    error.response?.data?.error ||
    error.response?.data?.message ||
    error.message ||
    'Wrong Username or Password'
  )
}

const handleLogin = async () => {
  submitted.value = true
  errorMessage.value = ''

  if (!username.value || !password.value) {
    errorMessage.value = 'Username and password are required.'
    return
  }

  isLoading.value = true

  try {
    await authStore.login(username.value, password.value)
    router.push(route.query.redirect || authStore.dashboardPath)
  } catch (error) {
    if (error.response?.status === 401) {
      errorMessage.value = 'Incorrect username or password.'
    } else {
      errorMessage.value = getErrorMessage(error)
    }

    toast.add({
      severity: 'error',
      summary: 'Login failed',
      detail: errorMessage.value,
      life: 4000,
    })
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  if (route.query.loggedOut) {
    toast.add({
      severity: 'success',
      summary: 'Logged out',
      detail: 'You have been logged out successfully.',
      life: 3000,
    })
  }
})
</script>
