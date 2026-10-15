<template>
  <el-button
    :type="type"
    :size="size"
    :loading="loading"
    :disabled="disabled || loading"
    @click="handleClick"
  >
    <slot></slot>
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { newIdempotencyKey, tryLock } from '@/utils/idempotency'

const props = defineProps<{
  /** 业务名称（用于去重锁） */
  name: string
  /** 按钮类型 */
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
  /** 尺寸 */
  size?: 'default' | 'small' | 'large'
  /** 是否禁用 */
  disabled?: boolean
  /** 锁定时长（毫秒） */
  lockMs?: number
  /** 是否生成 Idempotency-Key（不通过 emit，而是 export 给父组件） */
  withIdempotencyKey?: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: { key: string }): void
  (e: 'duplicate'): void
}>()

const loading = ref(false)

async function handleClick() {
  if (!tryLock(props.name, props.lockMs ?? 500)) {
    emit('duplicate')
    return
  }
  loading.value = true
  try {
    const key = newIdempotencyKey()
    emit('submit', { key })
  } finally {
    // 500ms 后再解锁，避免按钮连点
    setTimeout(() => {
      loading.value = false
    }, props.lockMs ?? 500)
  }
}


defineExpose({
  setLoading: (v: boolean) => { loading.value = v }
})
</script>
