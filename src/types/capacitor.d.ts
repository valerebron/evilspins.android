import '@capacitor/core'

declare module '@capacitor/core' {
  interface PluginRegistry {
    EvilSpinsNfc: EvilSpinsNfcPlugin
    EvilSpinsLightSensor: EvilSpinsLightSensorPlugin
    EvilSpinsFileStorage: EvilSpinsFileStoragePlugin
    EvilSpinsPermissions: EvilSpinsPermissionsPlugin
  }
}

export interface EvilSpinsNfcPlugin {
  isSupported(): Promise<{ supported: boolean }>
  isEnabled(): Promise<{ enabled: boolean }>
  startListening(): Promise<void>
  stopListening(): Promise<void>
  addListener(eventName: 'nfcEvent', listener: (event: NfcEvent) => void): Promise<{ remove: () => void }>
  removeAllListeners(): Promise<void>
}

export interface NfcEvent {
  event: 'log' | 'tag_detected' | 'tag_lost' | 'tag_removed' | 'unknown'
  type?: 'nfc' | 'info' | 'warning' | 'error'
  message?: string
  tagId?: string
  ndefRecords?: NdefRecord[]
}

export interface NdefRecord {
  payload: string
  tnf: number
  type: number[]
  id: number[]
}

export interface EvilSpinsLightSensorPlugin {
  addListener(eventName: 'lightSensorEvent', listener: (event: LightSensorEvent) => void): Promise<{ remove: () => void }>
  removeAllListeners(): Promise<void>
}

export interface LightSensorEvent {
  event: 'log' | 'unknown'
  type?: 'lux' | 'info' | 'warning' | 'error'
  message?: string
}

export interface EvilSpinsFileStoragePlugin {
  writeFile(options: WriteFileOptions): Promise<WriteFileResult>
  readFile(options: ReadFileOptions): Promise<ReadFileResult>
  deleteFile(options: DeleteFileOptions): Promise<DeleteFileResult>
  listFiles(options: ListFilesOptions): Promise<ListFilesResult>
  checkPermissions(): Promise<PermissionStatus>
  requestPermissions(): Promise<PermissionStatus>
  addListener(eventName: 'fileStorageEvent', listener: (event: FileStorageEvent) => void): Promise<{ remove: () => void }>
  removeAllListeners(): Promise<void>
}

export interface WriteFileOptions {
  fileName: string
  content: string
  directory: string
}

export interface WriteFileResult {
  path: string
  size: number
}

export interface ReadFileOptions {
  fileName: string
  directory: string
}

export interface ReadFileResult {
  content: string
  size: number
}

export interface DeleteFileOptions {
  fileName: string
  directory: string
}

export interface DeleteFileResult {
  path: string
}

export interface ListFilesOptions {
  directory: string
}

export interface ListFilesResult {
  files: string[]
}

export interface PermissionStatus {
  manageExternalStorage: 'granted' | 'denied' | 'unknown'
  writeExternalStorage: 'granted' | 'denied' | 'unknown'
  hasManageStoragePermission: boolean
  hasWriteExternalStoragePermission: boolean
}

export interface FileStorageEvent {
  event: 'log' | 'unknown'
  type?: 'file' | 'info' | 'warning' | 'error'
  message?: string
}

export interface EvilSpinsPermissionsPlugin {
  checkPermission(options: { permission: string }): Promise<{ granted: boolean }>
  requestPermission(options: { permission: string }): Promise<{ granted: boolean }>
  checkPermissions(options: { permissions: string[] }): Promise<{ permissions: { name: string; granted: boolean }[] }>
  requestPermissions(options: { permissions: string[] }): Promise<{ permissions: { name: string; granted: boolean }[] }>
}