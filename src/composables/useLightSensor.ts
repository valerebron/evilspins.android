import { ref, onMounted, onUnmounted, type Ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import type { EvilSpinsLightSensorPlugin, LightSensorEvent } from '@/types/capacitor'

export interface LightSensorState {
  listening: boolean
  lastLux: number | null
}

export interface UseLightSensorReturn {
  state: Ref<LightSensorState>
  addLog: (message: string, type: 'lux' | 'info' | 'warning' | 'error') => void
}

export function useLightSensor(logFn: (msg: string, type: 'lux' | 'info' | 'warning' | 'error') => void): UseLightSensorReturn {
  const state = ref<LightSensorState>({
    listening: false,
    lastLux: null
  })

  let plugin: EvilSpinsLightSensorPlugin | null = null
  let listenerRemover: (() => void) | null = null

  const initializePlugin = async () => {
    if (!Capacitor.isPluginAvailable('EvilSpinsLightSensor')) {
      logFn('EvilSpinsLightSensor plugin not available', 'error')
      return
    }

    plugin = (Capacitor as any).Plugins.EvilSpinsLightSensor as EvilSpinsLightSensorPlugin
  }

  const setupListener = async () => {
    if (!plugin) return

    try {
      const { remove } = await plugin.addListener('lightSensorEvent', (event: LightSensorEvent) => {
        handleLightSensorEvent(event)
      })
      listenerRemover = remove
      state.value.listening = true
    } catch (e) {
      logFn(`Light sensor listener error: ${(e as Error).message}`, 'error')
    }
  }

  const handleLightSensorEvent = (event: LightSensorEvent) => {
    const eventType = event.event || 'unknown'

    if (eventType === 'log') {
      logFn(event.message || '', event.type || 'info')
      if (event.type === 'lux') {
        const match = event.message?.match(/LUX:\s*([\d.]+)/)
        if (match) {
          state.value.lastLux = parseFloat(match[1])
        }
      }
    }
  }

  onMounted(async () => {
    await initializePlugin()
    await setupListener()
  })

  onUnmounted(() => {
    if (listenerRemover) listenerRemover()
    state.value.listening = false
  })

  return {
    state: state as Readonly<Ref<LightSensorState>>,
    addLog: logFn
  }
}