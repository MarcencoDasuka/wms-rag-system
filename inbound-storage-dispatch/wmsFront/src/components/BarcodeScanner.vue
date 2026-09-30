<template>
  <div class="flex flex-col gap-3">
    <div class="relative w-full rounded-xl overflow-hidden bg-gray-900 aspect-video">
      <video ref="videoRef" class="w-full h-full object-cover" playsinline />

      <div
        v-if="scanning"
        class="absolute inset-0 flex items-center justify-center pointer-events-none"
      >
        <div class="relative w-56 h-36">
          <span
            class="absolute top-0 left-0 w-6 h-6 border-t-2 border-l-2 border-blue-400 rounded-tl"
          />
          <span
            class="absolute top-0 right-0 w-6 h-6 border-t-2 border-r-2 border-blue-400 rounded-tr"
          />
          <span
            class="absolute bottom-0 left-0 w-6 h-6 border-b-2 border-l-2 border-blue-400 rounded-bl"
          />
          <span
            class="absolute bottom-0 right-0 w-6 h-6 border-b-2 border-r-2 border-blue-400 rounded-br"
          />
          <div class="absolute top-0 left-0 w-full h-0.5 bg-blue-400 opacity-80 animate-scan" />
        </div>
        <span class="absolute bottom-3 text-xs text-blue-300 tracking-wider">SCANNING...</span>
      </div>

      <div
        v-if="!scanning"
        class="absolute inset-0 flex flex-col items-center justify-center gap-2"
      >
        <svg class="w-10 h-10 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="1.5"
            d="M3 4h2v2H3V4zm4 0h1v2H7V4zm3 0h1v2h-1V4zm3 0h1v2h-1V4zm3 0h2v2h-2V4zM3 8h2v1H3V8zm16 0h2v1h-2V8zM3 11h2v2H3v-2zm16 0h2v2h-2v-2zM3 15h2v2H3v-2zm16 0h2v2h-2v-2zM3 19h2v1H3v-1zm4 0h1v1H7v-1zm3 0h1v1h-1v-1zm3 0h1v1h-1v-1zm3 0h2v1h-2v-1z"
          />
        </svg>
        <p class="text-gray-500 text-sm">Camera inactive</p>
      </div>
    </div>

    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

    <div class="flex gap-2 w-full">
      <Button
        :label="scanning ? 'Stop Scanner' : 'Start Scanner'"
        :icon="scanning ? 'pi pi-times' : 'pi pi-camera'"
        :severity="scanning ? 'danger' : 'primary'"
        class="flex-1"
        @click="scanning ? stopScanning() : startScanning()"
      />

      <Button
        v-if="scanning && torchSupported"
        :icon="torchActive ? 'pi pi-lightbulb' : 'pi pi-lightbulb'"
        :severity="torchActive ? 'warn' : 'secondary'"
        @click="toggleTorch"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onUnmounted, nextTick } from 'vue'
import { BrowserMultiFormatReader } from '@zxing/browser'
import Button from 'primevue/button'
import Message from 'primevue/message'

const emit = defineEmits(['detected'])

const videoRef = ref(null)
const scanning = ref(false)
const error = ref('')
const torchSupported = ref(false)
const torchActive = ref(false)

let controls = null
let videoTrack = null

const startScanning = async () => {
  error.value = ''
  const codeReader = new BrowserMultiFormatReader()

  try {
    controls = await codeReader.decodeFromVideoDevice(undefined, videoRef.value, (result) => {
      if (result) {
        emit('detected', result.getText())
        stopScanning()
      }
    })

    scanning.value = true

    await nextTick()
    checkTorchSupport()
  } catch (err) {
    console.error(err)
    error.value = 'Could not access camera. Please allow camera permissions.'
  }
}

const checkTorchSupport = () => {
  if (!videoRef.value || !videoRef.value.srcObject) return

  const stream = videoRef.value.srcObject
  videoTrack = stream.getVideoTracks()[0]

  if (videoTrack) {
    const capabilities = videoTrack.getCapabilities?.()
    if (capabilities && 'torch' in capabilities) {
      torchSupported.value = true
    }
  }
}

const toggleTorch = async () => {
  if (!videoTrack || !torchSupported.value) return

  try {
    torchActive.value = !torchActive.value
    await videoTrack.applyConstraints({
      advanced: [{ torch: torchActive.value }],
    })
  } catch (err) {
    console.error('Nu s-a putut schimba starea blițului:', err)
  }
}

const stopScanning = () => {
  if (torchActive.value) {
    toggleTorch()
  }

  controls?.stop()
  controls = null
  videoTrack = null
  scanning.value = false
  torchSupported.value = false
  torchActive.value = false
}

onUnmounted(() => stopScanning())
</script>

<style scoped>
@keyframes scan {
  0% {
    top: 0;
  }
  100% {
    top: calc(100% - 2px);
  }
}
.animate-scan {
  animation: scan 1.5s linear infinite;
}
</style>
