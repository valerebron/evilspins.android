<script setup lang="ts">
import { computed, type Ref } from 'vue'
import type { NfcState } from '@/composables/useNfc'
import type { LightSensorState } from '@/composables/useLightSensor'
import type { FileStorageState } from '@/composables/useFileStorage'

interface Props {
  nfc: NfcState | Ref<NfcState>
  lux: LightSensorState | Ref<LightSensorState>
  files: FileStorageState | Ref<FileStorageState>
}

const props = defineProps<Props>()

// Auto-unwrap refs for Vue 3.3+ compatibility
const nfc = computed(() => 'value' in props.nfc ? props.nfc.value : props.nfc)
const lux = computed(() => 'value' in props.lux ? props.lux.value : props.lux)
const files = computed(() => 'value' in props.files ? props.files.value : props.files)

const nfcClass = computed(() => {
  if (nfc.value.lastPayload) return 'status-value active'
  if (nfc.value.lastTag) return 'status-value active'
  return 'status-value empty'
})

const nfcText = computed(() => {
  if (nfc.value.lastPayload) return nfc.value.lastPayload
  if (nfc.value.lastTag) return `Tag ID: ${nfc.value.lastTag}`
  return 'No tag detected'
})

const luxText = computed(() => {
  return lux.value.lastLux !== null ? `${lux.value.lastLux.toFixed(1)} lx` : '-- lx'
})

const fileClass = computed(() => {
  return files.value.permissionGranted ? 'status-value granted' : 'status-value denied'
})

const fileText = computed(() => {
  return files.value.permissionGranted ? 'Permission granted' : 'Permission denied'
})
</script>

<template>
  <div class="status-panel">
    <div class="status-item">
      <div class="status-label">NFC YEAH !</div>
      <div :class="nfcClass" v-text="nfcText"></div>
    </div>
    <div class="status-item">
      <div class="status-label">LUX</div>
      <div class="status-value" v-text="luxText"></div>
    </div>
    <div class="status-item">
      <div class="status-label">FILES</div>
      <div :class="fileClass" v-text="fileText"></div>
    </div>
  </div>
</template>

<style scoped>
.status-panel {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #161b22;
  border-radius: 8px;
  border: 1px solid #30363d;
  flex-wrap: wrap;
}
.status-item {
  flex: 1;
  min-width: 180px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  background: #0d1117;
  border-radius: 6px;
  border: 1px solid #21262d;
}
.status-label {
  font-size: 11px;
  color: #8b949e;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.status-value {
  font-size: 15px;
  font-weight: 600;
  color: #c9d1d9;
  word-break: break-word;
  white-space: pre-wrap;
  font-family: inherit;
}
.status-value.active { color: #3fb950; }
.status-value.empty { color: #8b949e; font-style: italic; }
.status-value.denied { color: #f85149; }
.status-value.granted { color: #3fb950; }
</style>