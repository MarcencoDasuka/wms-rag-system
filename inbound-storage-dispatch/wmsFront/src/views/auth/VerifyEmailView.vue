<template>
  <div
    class="app-shell relative flex flex-col items-center justify-center px-6 py-16 sm:px-8 lg:px-12 min-h-screen"
  >
    <Toast />
    <div class="absolute right-6 top-6">
      <ThemeToggle />
    </div>

    <Card class="app-card w-full max-w-md shadow-lg rounded-xl">
      <template #content>
        <div v-if="!success && !fatalError" class="flex flex-col gap-4 py-2">
          <div class="w-full flex justify-center mb-4">
            <img
              :src="isDark ? '/white_logo.png' : '/color_logo.png'"
              alt="Inbound Storage Dispatch logo"
              class="block h-24 w-auto object-contain transition-all duration-300"
            />
          </div>

          <div class="text-center mb-2">
            <h3 class="app-brand text-xl font-bold">Activate Your Account</h3>
            <p class="app-subtitle text-sm mt-1">Please set a password for your new account.</p>
          </div>

          <form @submit.prevent="submitVerification" class="flex flex-col gap-5">
            <div class="flex flex-col gap-2">
              <label for="password" class="app-subtitle font-medium text-sm sm:text-base">
                New Password <span class="app-danger">*</span>
              </label>

              <Password
                id="password"
                v-model="password"
                toggleMask
                required
                promptLabel="Choose a password"
                weakLabel="Weak password"
                mediumLabel="Medium strength"
                strongLabel="Strong password"
                :mediumRegex="passwordMediumRegex"
                :strongRegex="passwordStrongRegex"
                inputClass="w-full p-3"
                class="w-full [&>input]:w-full"
                :pt="{
                  panel: {
                    class:
                      'app-card border border-gray-200 dark:border-gray-700 shadow-2xl p-4 rounded-xl',
                  },
                  meter: { class: 'bg-gray-100 dark:bg-gray-800 rounded-sm' },
                  info: { class: 'app-subtitle text-xs font-semibold mt-1.5' },
                }"
              >
                <template #footer>
                  <div
                    class="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700 flex flex-col gap-2 min-w-[240px]"
                  >
                    <p
                      class="text-[10px] font-bold app-subtitle opacity-70 uppercase tracking-wider"
                    >
                      Requirements:
                    </p>

                    <div
                      v-for="(req, index) in passwordRequirements"
                      :key="index"
                      class="flex items-center gap-2 text-xs transition-colors duration-200"
                      :class="
                        req.valid
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : 'app-subtitle opacity-60'
                      "
                    >
                      <i
                        :class="[
                          req.valid
                            ? 'pi pi-check-circle text-emerald-500 dark:text-emerald-400'
                            : 'pi pi-circle',
                          'text-[10px]',
                        ]"
                      ></i>
                      <span :class="{ 'line-through opacity-50': req.valid }">{{ req.label }}</span>
                    </div>
                  </div>
                </template>
              </Password>
            </div>

            <div class="flex flex-col gap-2">
              <label for="confirmPassword" class="app-subtitle font-medium text-sm sm:text-base">
                Confirm Password <span class="app-danger">*</span>
              </label>
              <Password
                id="confirmPassword"
                v-model="confirmPassword"
                :feedback="false"
                toggleMask
                required
                inputClass="w-full p-3"
                class="w-full [&>input]:w-full"
              />
              <small
                v-if="confirmPassword && !isPasswordMatching"
                class="app-danger text-xs flex items-center gap-1 mt-1"
              >
                <i class="pi pi-times-circle"></i> Passwords do not match.
              </small>
            </div>

            <Button
              type="submit"
              label="Set Password & Activate"
              :loading="loading"
              :disabled="!isFormValid"
              class="w-full mt-4 p-3 font-semibold text-base"
            />
          </form>
        </div>

        <div v-else-if="success" class="flex flex-col items-center gap-4 py-6 text-center">
          <div
            class="w-20 h-20 bg-green-50 dark:bg-green-950/30 rounded-full flex items-center justify-center border border-green-100 dark:border-green-900 shadow-inner"
          >
            <i class="pi pi-check text-5xl text-green-600 dark:text-green-400"></i>
          </div>
          <h2 class="app-brand text-2xl font-bold">Account Activated!</h2>
          <p class="app-subtitle text-sm">Your password has been set successfully.</p>
          <Button label="Go to Login" class="w-full mt-4 p-3 font-semibold" @click="goToLogin" />
        </div>

        <div v-else class="flex flex-col items-center gap-4 py-6 text-center">
          <div
            class="w-20 h-20 bg-red-50 dark:bg-red-950/30 rounded-full flex items-center justify-center border border-red-100 dark:border-red-900 shadow-inner"
          >
            <i class="pi pi-times text-5xl text-red-600 dark:text-red-400"></i>
          </div>
          <h2 class="app-brand text-2xl font-bold">Activation Failed</h2>
          <p class="app-danger font-medium text-sm">{{ errorMessage }}</p>
          <p class="app-subtitle text-xs mt-1">The link might be expired or invalid.</p>
          <Button
            label="Back to Login"
            severity="secondary"
            class="w-full mt-4 p-3 font-semibold"
            @click="goToLogin"
          />
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { authApi } from '@/api/authApi'
import { useTheme } from '@/composables/useTheme'

import Card from 'primevue/card'
import Button from 'primevue/button'
import Password from 'primevue/password'
import Toast from 'primevue/toast'
import ThemeToggle from '@/components/ThemeToggle.vue'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const { isDark } = useTheme()

const token = ref('')
const password = ref('')
const confirmPassword = ref('')

const loading = ref(false)
const success = ref(false)
const fatalError = ref(false)
const errorMessage = ref('')

const passwordMediumRegex = '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d]{8,64}$'
const passwordStrongRegex =
  '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#])[A-Za-z\\d@$!%*?&_#]{8,64}$'

const hasUppercase = computed(() => /[A-Z]/.test(password.value))
const hasLowercase = computed(() => /[a-z]/.test(password.value))
const hasDigit = computed(() => /\d/.test(password.value))
const hasSpecialChar = computed(() => /[@$!%*?&_#]/.test(password.value))
const hasValidLength = computed(() => password.value.length >= 8 && password.value.length <= 64)

const passwordRequirements = computed(() => [
  { label: '8-64 characters long', valid: hasValidLength.value },
  { label: 'At least one uppercase letter (A-Z)', valid: hasUppercase.value },
  { label: 'At least one lowercase letter (a-z)', valid: hasLowercase.value },
  { label: 'At least one number (0-9)', valid: hasDigit.value },
  { label: 'At least one special character (@$!%*?&_#)', valid: hasSpecialChar.value },
])

const isPasswordMatching = computed(() => {
  return password.value === confirmPassword.value
})

const isFormValid = computed(() => {
  return (
    hasValidLength.value &&
    hasUppercase.value &&
    hasLowercase.value &&
    hasDigit.value &&
    hasSpecialChar.value &&
    isPasswordMatching.value
  )
})

const goToLogin = () => {
  router.push('/login')
}

onMounted(() => {
  token.value = route.query.token
  if (!token.value) {
    fatalError.value = true
    errorMessage.value = 'No verification token provided in the URL.'
  }
})

const submitVerification = async () => {
  if (!isFormValid.value) return
  loading.value = true
  try {
    await authApi.verify({ token: token.value, password: password.value })
    success.value = true
  } catch (error) {
    fatalError.value = true
    errorMessage.value = error.response?.data?.error || 'Failed to verify email.'
    toast.add({ severity: 'error', summary: 'Error', detail: errorMessage.value, life: 5000 })
  } finally {
    loading.value = false
  }
}
</script>
