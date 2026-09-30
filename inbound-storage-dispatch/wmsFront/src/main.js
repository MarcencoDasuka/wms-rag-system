import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useTheme } from './composables/useTheme'

import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import ConfirmationService from 'primevue/confirmationservice'
import ToastService from 'primevue/toastservice'
import AppDataTable from '@/components/AppDataTable.vue'
import ProductLink from '@/components/ProductLink.vue'

const app = createApp(App)

useTheme()

app.use(createPinia())
app.use(router)

app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      darkModeSelector: '.app-dark',
      cssLayer: false
    }
  }
})
app.use(ToastService)
app.use(ConfirmationService)
app.component('AppDataTable', AppDataTable)
app.component('ProductLink', ProductLink)

app.mount('#app')
