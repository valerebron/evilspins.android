import { ref, onMounted, onUnmounted, type Ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import type { EvilSpinsNfcPlugin, NfcEvent } from '@/types/capacitor'

export interface NfcState {
  supported: boolean
  enabled: boolean
  listening: boolean
  lastTag: string | null
  lastPayload: string | null
}

export interface UseNfcReturn {
  state: Ref<NfcState>
  startListening: () => Promise<void>
  stopListening: () => Promise<void>
  addLog: (message: string, type: 'nfc' | 'info' | 'warning' | 'error') => void
}

export function useNfc(logFn: (msg: string, type: 'nfc' | 'info' | 'warning' | 'error') => void): UseNfcReturn {
  const state = ref<NfcState>({
    supported: false,
    enabled: false,
    listening: false,
    lastTag: null,
    lastPayload: null
  })

  let plugin: EvilSpinsNfcPlugin | null = null
  let listenerRemover: (() => void) | null = null

  const initializePlugin = async () => {
    if (!Capacitor.isPluginAvailable('EvilSpinsNfc')) {
      logFn('EvilSpinsNfc plugin not available', 'error')
      return
    }

    plugin = (Capacitor as any).Plugins.EvilSpinsNfc as EvilSpinsNfcPlugin

    try {
      const [supported, enabled] = await Promise.all([
        plugin.isSupported(),
        plugin.isEnabled()
      ])
      state.value.supported = supported.supported
      state.value.enabled = enabled.enabled
      logFn(`NFC Supported: ${supported.supported}`, 'info')
      logFn(`NFC Enabled: ${enabled.enabled}`, 'info')
    } catch (e) {
      logFn(`NFC init error: ${(e as Error).message}`, 'error')
    }
  }

  const setupListener = async () => {
    if (!plugin) return

    try {
      const { remove } = await plugin.addListener('nfcEvent', (event: NfcEvent) => {
        handleNfcEvent(event)
      })
      listenerRemover = remove
    } catch (e) {
      logFn(`NFC listener error: ${(e as Error).message}`, 'error')
    }
  }

  const handleNfcEvent = (event: NfcEvent) => {
    const eventType = event.event || 'unknown'

    if (eventType === 'log') {
      logFn(event.message || '', event.type || 'info')
    } else if (eventType === 'tag_detected') {
      logFn(`Tag detected: ${event.tagId}`, 'nfc')
      state.value.lastTag = event.tagId || null
      if (event.ndefRecords && event.ndefRecords.length > 0) {
        const payloads = event.ndefRecords
          .map(r => r.payload)
          .filter(p => p && p !== '')
        if (payloads.length > 0) {
          state.value.lastPayload = payloads.join('\n---\n')
          logFn(`NDEF: ${JSON.stringify(event.ndefRecords)}`, 'nfc')
        }
      }
    } else if (eventType === 'tag_lost' || eventType === 'tag_removed') {
      logFn(`Tag removed: ${event.tagId}`, 'nfc')
      state.value.lastTag = null
      state.value.lastPayload = null
    } else {
      logFn(`NFC event: ${JSON.stringify(event)}`, 'nfc')
    }
  }

  const startListening = async () => {
    if (!plugin) return
    try {
      await plugin.startListening()
      state.value.listening = true
      logFn('NFC listening started', 'info')
    } catch (e) {
      logFn(`NFC start error: ${(e as Error).message}`, 'error')
    }
  }

  const stopListening = async () => {
    if (!plugin) return
    try {
      await plugin.stopListening()
      state.value.listening = false
      logFn('NFC listening stopped', 'info')
    } catch (e) {
      logFn(`NFC stop error: ${(e as Error).message}`, 'error')
    }
  }

  onMounted(async () => {
    await initializePlugin()
    await setupListener()
    await startListening()
  })

  onUnmounted(async () => {
    if (listenerRemover) listenerRemover()
    await stopListening()
  })

  return {
    state: state as Readonly<Ref<NfcState>>,
    startListening,
    stopListening,
    addLog: logFn
  }
}