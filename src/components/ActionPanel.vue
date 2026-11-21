<script setup lang="ts">
interface Props {
  onCreate: () => void
  onDelete: () => void
  disabled?: boolean
}

const props = defineProps<Props>()
</script>

<template>
  <div class="action-panel">
    <button class="action-btn create" @click="props.onCreate" :disabled="props.disabled">
      Create File
    </button>
    <button class="action-btn delete" @click="props.onDelete" :disabled="props.disabled">
      Delete File
    </button>
  </div>
</template>

<style scoped>
.action-panel {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #161b22;
  border-radius: 8px;
  border: 1px solid #30363d;
}
.action-btn {
  flex: 1;
  padding: 14px 24px;
  border: 1px solid #30363d;
  border-radius: 6px;
  background: #21262d;
  color: #c9d1d9;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.action-btn:hover:not(:disabled) { background: #30363d; border-color: #484f58; }
.action-btn:active:not(:disabled) { background: #161b22; }
.action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.action-btn.create { border-color: #3fb950; }
.action-btn.create:hover:not(:disabled) { background: #1a3a2a; }
.action-btn.delete { border-color: #f85149; }
.action-btn.delete:hover:not(:disabled) { background: #3a1a1a; }
</style>