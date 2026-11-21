<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import StatusPanel from '@/components/StatusPanel.vue'
import ActionPanel from '@/components/ActionPanel.vue'
import LogPanel from '@/components/LogPanel.vue'
import { useNfc } from '@/composables/useNfc'
import { useLightSensor } from '@/composables/useLightSensor'
import { useFileStorage } from '@/composables/useFileStorage'

interface LogEntry {
  id: number
  timestamp: string
  type: 'nfc' | 'lux' | 'file' | 'info' | 'warning' | 'error'
  message: string
}

const logs = ref<LogEntry[]>([])
let entryId = 0

const addLog = (message: string, type: LogEntry['type']) => {
  entryId++
  const time = new Date().toLocaleTimeString() + '.' + String(Date.now() % 1000).padStart(3, '0')
  logs.value.push({ id: entryId, timestamp: time, type, message })
}

const clearLogs = () => {
  logs.value = []
  entryId = 0
  addLog('App started. Waiting for events...', 'info')
}

// Initialize composables
const nfc = useNfc(addLog)
const lux = useLightSensor(addLog)
const files = useFileStorage(addLog)

const createFile = () => files.createFile()
const deleteFile = () => files.deleteFile()

onMounted(() => {
  clearLogs()

  // Check if running in Capacitor
  if (!Capacitor.isNativePlatform()) {
    addLog('Running in browser - Capacitor plugins not available', 'warning')
  }
})
</script>

<template>
  <div class="container">
    <StatusPanel :nfc="nfc.state" :lux="lux.state" :files="files.state" />
    <ActionPanel :onCreate="createFile" :onDelete="deleteFile" />
    <LogPanel :logs="logs" :onClear="clearLogs" />
  </div>
</template>

<style>
/* Global styles */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
body {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  background: #0d1117;
  color: #c9d1d9;
  min-height: 100vh;
  padding: 12px;
  font-size: 13px;
  line-height: 1.5;
}
.container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  gap: 12px;
}

/* PWA Install prompt */
@media (display-mode: standalone) {
  body { padding: 0; }
  .container { height: 100dvh; padding: 12px; gap: 12px; }
}
</style>