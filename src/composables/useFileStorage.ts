import { ref, onMounted, onUnmounted, type Ref } from 'vue'
import { Capacitor } from '@capacitor/core'
import type { EvilSpinsFileStoragePlugin, FileStorageEvent, PermissionStatus, WriteFileOptions, WriteFileResult, DeleteFileOptions, DeleteFileResult } from '@/types/capacitor'

export interface FileStorageState {
  listening: boolean
  permissionGranted: boolean
  lastCreatedFile: string | null
}

export interface UseFileStorageReturn {
  state: Ref<FileStorageState>
  createFile: () => Promise<void>
  deleteFile: () => Promise<void>
  checkPermissions: () => Promise<void>
  requestPermissions: () => Promise<void>
  addLog: (message: string, type: 'file' | 'info' | 'warning' | 'error') => void
}

export function useFileStorage(logFn: (msg: string, type: 'file' | 'info' | 'warning' | 'error') => void): UseFileStorageReturn {
  const state = ref<FileStorageState>({
    listening: false,
    permissionGranted: false,
    lastCreatedFile: null
  })

  let plugin: EvilSpinsFileStoragePlugin | null = null
  let listenerRemover: (() => void) | null = null

  const initializePlugin = async () => {
    if (!Capacitor.isPluginAvailable('EvilSpinsFileStorage')) {
      logFn('EvilSpinsFileStorage plugin not available', 'error')
      return
    }

    plugin = (Capacitor as any).Plugins.EvilSpinsFileStorage as EvilSpinsFileStoragePlugin
  }

  const setupListener = async () => {
    if (!plugin) return

    try {
      const { remove } = await plugin.addListener('fileStorageEvent', (event: FileStorageEvent) => {
        handleFileStorageEvent(event)
      })
      listenerRemover = remove
      state.value.listening = true
      logFn('FileStorage listener ready', 'file')
    } catch (e) {
      logFn(`FileStorage listener error: ${(e as Error).message}`, 'error')
    }
  }

  const handleFileStorageEvent = (event: FileStorageEvent) => {
    const eventType = event.event || 'unknown'

    if (eventType === 'log') {
      const type = event.type === 'info' ? 'file' : (event.type || 'file')
      logFn(event.message || '', type)

      // Check for permission status in log messages
      const msg = event.message || ''
      if (msg.includes('MANAGE_EXTERNAL_STORAGE: GRANTED') ||
          msg.includes('WRITE_EXTERNAL_STORAGE: GRANTED') ||
          msg.includes('hasManageStoragePermission: true')) {
        state.value.permissionGranted = true
      } else if (msg.includes('MANAGE_EXTERNAL_STORAGE: NOT GRANTED') ||
                 msg.includes('WRITE_EXTERNAL_STORAGE: NOT GRANTED') ||
                 msg.includes('hasManageStoragePermission: false') ||
                 msg.includes('hasWriteExternalStoragePermission: false')) {
        state.value.permissionGranted = false
      }
    }
  }

  const createFile = async () => {
    if (!plugin) {
      logFn('Plugin not available', 'error')
      return
    }

    const fileName = `evilspins_${Date.now()}.txt`
    const content = `Created: ${new Date().toISOString()}\nFrom evilspins app`
    const options: WriteFileOptions = { fileName, content, directory: '' }

    logFn(`Creating file: ${fileName}`, 'file')
    try {
      const result: WriteFileResult = await plugin.writeFile(options)
      logFn(`Created: ${result.path} (${result.size} bytes)`, 'file')
      state.value.lastCreatedFile = fileName
    } catch (e) {
      logFn(`Create error: ${(e as Error).message}`, 'error')
    }
  }

  const deleteFile = async () => {
    if (!plugin) {
      logFn('Plugin not available', 'error')
      return
    }

    if (!state.value.lastCreatedFile) {
      logFn('No file to delete (create one first)', 'warning')
      return
    }

    const fileName = state.value.lastCreatedFile
    const options: DeleteFileOptions = { fileName, directory: '' }

    logFn(`Deleting file: ${fileName}`, 'file')
    try {
      const result: DeleteFileResult = await plugin.deleteFile(options)
      logFn(`Deleted: ${result.path}`, 'file')
      state.value.lastCreatedFile = null
    } catch (e) {
      logFn(`Delete error: ${(e as Error).message}`, 'error')
    }
  }

  const checkPermissions = async () => {
    if (!plugin) return
    try {
      const status: PermissionStatus = await plugin.checkPermissions()
      state.value.permissionGranted = status.hasManageStoragePermission || status.hasWriteExternalStoragePermission
      logFn(`Permissions: manage=${status.manageExternalStorage}, write=${status.writeExternalStorage}`, 'file')
    } catch (e) {
      logFn(`Check permissions error: ${(e as Error).message}`, 'error')
    }
  }

  const requestPermissions = async () => {
    if (!plugin) return
    try {
      const status: PermissionStatus = await plugin.requestPermissions()
      state.value.permissionGranted = status.hasManageStoragePermission || status.hasWriteExternalStoragePermission
      logFn(`Requested permissions: manage=${status.manageExternalStorage}, write=${status.writeExternalStorage}`, 'file')
    } catch (e) {
      logFn(`Request permissions error: ${(e as Error).message}`, 'error')
    }
  }

  onMounted(async () => {
    await initializePlugin()
    await setupListener()
    await checkPermissions()
  })

  onUnmounted(() => {
    if (listenerRemover) listenerRemover()
    state.value.listening = false
  })

  return {
    state: state as Readonly<Ref<FileStorageState>>,
    createFile,
    deleteFile,
    checkPermissions,
    requestPermissions,
    addLog: logFn
  }
}