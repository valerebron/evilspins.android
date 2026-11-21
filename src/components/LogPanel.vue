<script setup lang="ts">
import { ref, computed } from 'vue'

interface LogEntry {
  id: number
  timestamp: string
  type: 'nfc' | 'lux' | 'file' | 'info' | 'warning' | 'error'
  message: string
}

interface Props {
  logs: LogEntry[]
  onClear: () => void
}

const props = defineProps<Props>()

const showAllLogs = ref(true)
const filterText = computed(() => showAllLogs.value ? 'Show: All' : 'Show: Files Only')

const filteredLogs = computed(() => {
  if (showAllLogs.value) return props.logs
  return props.logs.filter(log =>
    log.type === 'file' || log.type === 'info' || log.type === 'warning' || log.type === 'error'
  )
})

const typeColors: Record<string, string> = {
  nfc: '#3fb950',
  lux: '#d29922',
  file: '#a371f7',
  info: '#58a6ff',
  warning: '#d29922',
  error: '#f85149'
}

const typeBg: Record<string, string> = {
  nfc: '#1a3a2a',
  lux: '#1a3a1a',
  file: '#1a1a3a',
  info: '#1a2a3a',
  warning: '#3a2a1a',
  error: '#3a1a1a'
}
</script>

<template>
  <div class="log-panel">
    <div class="log-header">
      <span class="log-title">Logs</span>
      <div class="log-controls">
        <button class="log-btn" @click="props.onClear">Clear</button>
        <button class="log-btn" @click="showAllLogs = !showAllLogs" v-text="filterText"></button>
      </div>
    </div>
    <div class="log-area" ref="logArea">
      <div
        v-for="log in filteredLogs"
        :key="log.id"
        class="log-entry"
        :style="{
          background: typeBg[log.type],
          borderLeftColor: typeColors[log.type],
          color: typeColors[log.type]
        }"
      >
        <span class="timestamp">[{{ log.timestamp }}]</span>
        <span class="tag">[{{ log.type.toUpperCase() }}]</span>
        {{ log.message }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.log-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #161b22;
  border-radius: 8px;
  border: 1px solid #30363d;
  overflow: hidden;
}
.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #0d1117;
  border-bottom: 1px solid #30363d;
}
.log-title {
  font-size: 11px;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.log-controls {
  display: flex;
  gap: 8px;
}
.log-btn {
  padding: 6px 12px;
  border: 1px solid #30363d;
  border-radius: 4px;
  background: #21262d;
  color: #c9d1d9;
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
}
.log-btn:hover { background: #30363d; }
.log-area {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  font-size: 11px;
  line-height: 1.6;
}
.log-entry {
  margin: 2px 0;
  padding: 4px 8px;
  border-radius: 3px;
  border-left-width: 3px;
  border-left-style: solid;
  word-break: break-word;
  white-space: pre-wrap;
  font-family: inherit;
}
.timestamp { color: #8b949e; margin-right: 8px; font-variant-numeric: tabular-nums; }
.tag { font-weight: bold; color: #ffa657; margin-right: 4px; }
</style>