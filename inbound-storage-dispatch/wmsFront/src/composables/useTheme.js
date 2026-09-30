import { computed, ref } from 'vue'

const STORAGE_KEY = 'wms_theme'
const DARK_CLASS = 'app-dark'

const getInitialTheme = () => {
  const storedTheme = localStorage.getItem(STORAGE_KEY)
  if (storedTheme === 'dark' || storedTheme === 'light') return storedTheme
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

const theme = ref(getInitialTheme())

const applyTheme = (nextTheme) => {
  document.documentElement.classList.toggle(DARK_CLASS, nextTheme === 'dark')
  document.documentElement.dataset.theme = nextTheme
  localStorage.setItem(STORAGE_KEY, nextTheme)
}

applyTheme(theme.value)

export const useTheme = () => {
  const isDark = computed(() => theme.value === 'dark')

  const setTheme = (nextTheme) => {
    theme.value = nextTheme
    applyTheme(nextTheme)
  }

  const toggleTheme = () => {
    setTheme(isDark.value ? 'light' : 'dark')
  }

  return {
    theme,
    isDark,
    setTheme,
    toggleTheme
  }
}
