<template>
  <div class="material-uploader">
    <el-upload
      v-if="!displayUrl"
      :show-file-list="false"
      :auto-upload="false"
      accept="image/*,.pdf"
      :on-change="handleChange"
      :disabled="disabled"
      class="uploader-trigger"
    >
      <div class="trigger">
        <el-icon :size="32"><Plus /></el-icon>
        <div class="trigger-text">点击上传 / 拖入文件</div>
        <div class="trigger-hint">{{ hint }}</div>
      </div>
    </el-upload>

    <div v-else class="preview">
      <el-image
        v-if="isImage"
        :src="displayUrl"
        :preview-src-list="[displayUrl]"
        fit="cover"
        class="preview-img"
        :preview-teleported="true"
      />
      <div v-else class="file-card">
        <el-icon :size="40" color="#909399"><Document /></el-icon>
        <div class="file-name">{{ existing?.fileName || '文件' }}</div>
        <div class="file-size">{{ formatSize(existing?.fileSize) }}</div>
      </div>
      <div class="preview-actions">
        <el-link type="primary" underline="never" @click="view">查看</el-link>
        <el-link v-if="!disabled" type="danger" underline="never" @click="remove" :loading="removing">删除</el-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Document } from '@element-plus/icons-vue'
import { sysApi, type DcepQualificationAttachment } from '@/api/sys'

const props = defineProps<{
  materialType: string
  qualificationId?: number
  existing?: DcepQualificationAttachment
  modelValue?: string
  hint?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'uploaded', att: DcepQualificationAttachment): void
  (e: 'removed', id: number): void
}>()

const uploading = ref(false)
const removing = ref(false)

const displayUrl = computed(() => props.modelValue || props.existing?.url || '')
const isImage = computed(() => {
  const t = props.existing?.contentType || ''
  return t.startsWith('image/') || /\.(jpe?g|png|gif|webp|bmp)$/i.test(displayUrl.value)
})

function handleChange(file: any) {
  if (!file?.raw) return
  upload(file.raw)
}

async function upload(file: File) {
  if (props.disabled) return
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('单文件最大 10MB')
    return
  }
  uploading.value = true
  try {
    const r: any = await sysApi.dcepQualUpload(props.qualificationId || null, props.materialType, file)
    const d = r.data
    emit('update:modelValue', d.url)
    emit('uploaded', d)
    ElMessage.success('上传成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function remove() {
  if (!props.existing?.id) return
  try {
    await ElMessageBox.confirm('确定删除该附件？删除后无法恢复。', '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  removing.value = true
  try {
    await sysApi.dcepQualDeleteAttachment(props.existing.id)
    emit('update:modelValue', '')
    emit('removed', props.existing.id)
    ElMessage.success('已删除')
  } finally {
    removing.value = false
  }
}

function view() {
  if (!displayUrl.value) return
  window.open(displayUrl.value, '_blank')
}

function formatSize(n?: number) {
  if (!n) return ''
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}
</script>

<style scoped>
.material-uploader { width: 100% }
.uploader-trigger { display: block }
.trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  background: #fafafa;
  color: #909399;
  cursor: pointer;
  transition: all .15s;
}
.trigger:hover { border-color: #409eff; color: #409eff }
.trigger-text { margin-top: 6px; font-size: 13px }
.trigger-hint { font-size: 11px; margin-top: 2px; color: #c0c4cc }

.preview { display: flex; align-items: center; gap: 12px; padding: 8px; border: 1px solid #ebeef5; border-radius: 6px; background: #fff }
.preview-img { width: 96px; height: 96px; border-radius: 4px; border: 1px solid #ebeef5 }
.file-card { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 8px 16px; min-width: 200px }
.file-name { font-size: 13px; color: #303133 }
.file-size { font-size: 12px; color: #909399 }
.preview-actions { display: flex; flex-direction: column; gap: 4px; font-size: 12px }
</style>