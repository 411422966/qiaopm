<template>
  <div class="page">
    <!-- 日志保留策略 -->
    <el-card class="group-card">
      <template #header>
        <div class="group-header">
          <el-icon class="group-icon" :size="18"><Timer /></el-icon>
          <span class="group-title">日志保留策略</span>
          <span class="group-desc">定期清理过期操作日志，每天凌晨 3:00 执行</span>
        </div>
      </template>
      <el-form :model="retention" label-width="160px" v-loading="retentionLoading">
        <el-form-item label="保留天数">
          <el-input-number v-model="retention.value" :min="1" :max="3650" :step="1" controls-position="right" style="width: 200px" />
          <span class="key-hint">天 · 范围 1 ~ 3650 · 默认 90</span>
        </el-form-item>
        <el-form-item label="上次清理">
          <span class="last-clean muted">由后端定时任务执行（每天 03:00），可在「操作日志」查看清理结果</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="retentionSaving" :icon="Check" @click="saveRetention">保存设置</el-button>
          <el-button :icon="Refresh" @click="loadRetention" :disabled="retentionSaving">重新加载</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-for="group in orderedGroups" :key="group.key" class="group-card">
      <template #header>
        <div class="group-header">
          <el-icon class="group-icon" :size="18"><component :is="group.icon" /></el-icon>
          <span class="group-title">{{ group.label }}</span>
          <span class="group-desc">{{ group.desc }}</span>
        </div>
      </template>
      <el-form :model="group.items" label-width="140px" v-loading="loading">
        <el-form-item v-for="item in group.items" :key="item.configKey" :label="item.description || item.configKey">
          <!-- Logo 图片上传 -->
          <template v-if="item.configKey === 'shop_logo'">
            <el-upload
              class="logo-uploader"
              action="/api/upload"
              :show-file-list="false"
              :before-upload="(file: File) => beforeLogoUpload(file, item)"
              :http-request="(opts: any) => customLogoUpload(opts, item)"
              accept="image/*"
            >
              <img v-if="item.configValue" :src="item.configValue" class="logo-preview" />
              <el-icon v-else class="logo-placeholder"><Plus /></el-icon>
            </el-upload>
            <span class="key-hint">key: <code>{{ item.configKey }}</code> · 建议 200x80 · ≤500KB</span>
          </template>
          <!-- 多行文本 -->
          <el-input
            v-else-if="isLong(item)"
            type="textarea"
            :rows="2"
            v-model="item.configValue"
            :placeholder="item.description || item.configKey"
            style="max-width: 480px"
          />
          <!-- 普通输入 -->
          <el-input
            v-else
            v-model="item.configValue"
            :placeholder="item.description || item.configKey"
            style="max-width: 480px"
          />
          <span v-if="item.configKey !== 'shop_logo'" class="key-hint">key: <code>{{ item.configKey }}</code></span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveGroup(group.key)">
            <el-icon><Check /></el-icon>&nbsp;保存「{{ group.label }}」
          </el-button>
          <el-button @click="load" :disabled="saving">
            <el-icon><Refresh /></el-icon>&nbsp;重新加载
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage, type UploadFile, type UploadRawFile } from 'element-plus'
import { Shop, Money, Printer, Document, Plus, Check, Refresh, DataAnalysis, Timer } from '@element-plus/icons-vue'
import { sysApi, type SysConfig } from '@/api/sys'

const retention = reactive({ value: 90 })
const retentionLoading = ref(false)
const retentionSaving = ref(false)

async function loadRetention() {
  retentionLoading.value = true
  try {
    const r: any = await sysApi.logRetentionGet()
    retention.value = r?.data?.value ?? 90
  } finally {
    retentionLoading.value = false
  }
}

async function saveRetention() {
  retentionSaving.value = true
  try {
    await sysApi.logRetentionUpdate({ days: retention.value })
    ElMessage.success('已保存，下次清理生效')
  } finally {
    retentionSaving.value = false
  }
}

const raw = ref<Record<string, SysConfig[]>>({})
const loading = ref(false)
const saving = ref(false)

const groupMeta = [
  { key: 'shop', label: '店铺信息', desc: '门店基础信息', icon: Shop },
  { key: 'biz', label: '业务参数', desc: '营业时间与模块开关', icon: DataAnalysis },
  { key: 'payment', label: '支付配置', desc: '收银台展示与启用项', icon: Money },
  { key: 'print', label: '打印设置', desc: '小票打印机', icon: Printer },
  { key: 'default', label: '其他', desc: '杂项配置', icon: Document }
]

const orderedGroups = computed(() =>
  groupMeta
    .map(g => ({ ...g, items: raw.value[g.key] || [] }))
    .filter(g => g.items.length > 0)
)

const isLong = (item: SysConfig) =>
  ['shop_address'].includes(item.configKey || '')

async function load() {
  loading.value = true
  try {
    const r: any = await sysApi.configGrouped()
    raw.value = r.data || {}
  } finally {
    loading.value = false
  }
}

async function saveGroup(gk: string) {
  saving.value = true
  try {
    const items = raw.value[gk] || []
    await sysApi.configBatch(items)
    const label = groupMeta.find(g => g.key === gk)?.label || gk
    ElMessage.success(`已保存「${label}」`)
  } finally {
    saving.value = false
  }
}

// Logo 上传：使用 FileReader 转 dataURL（演示模式，生产应走后端 /upload）
function beforeLogoUpload(file: UploadRawFile, item: SysConfig): boolean {
  const ok = file.type.startsWith('image/')
  const lt500k = file.size / 1024 / 1024 < 0.5
  if (!ok) { ElMessage.warning('请选择图片文件'); return false }
  if (!lt500k) { ElMessage.warning('图片大小需 ≤ 500KB'); return false }
  return true
}

function customLogoUpload(opts: any, item: SysConfig) {
  const reader = new FileReader()
  reader.onload = (e: any) => {
    item.configValue = e.target.result
    ElMessage.success('Logo 已加载，点击「保存」写入数据库')
  }
  reader.readAsDataURL(opts.file)
}

onMounted(() => { load(); loadRetention() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px }
.group-header { display: flex; align-items: center; gap: 8px }
.group-icon { color: var(--el-color-primary) }
.group-title { font-weight: 600; font-size: 15px }
.group-desc { color: #999; font-size: 12px; margin-left: 4px }
.key-hint { margin-left: 12px; color: #999; font-size: 12px }
.key-hint code { background: #f5f7fa; padding: 1px 6px; border-radius: 3px }
.last-clean { font-size: 13px }
.muted { color: #909399 }
.logo-uploader :deep(.el-upload) { border: 1px dashed #d9d9d9; border-radius: 6px; cursor: pointer; position: relative; overflow: hidden; width: 120px; height: 120px; display: flex; align-items: center; justify-content: center; background: #fafafa; transition: border-color .2s }
.logo-uploader :deep(.el-upload:hover) { border-color: var(--el-color-primary) }
.logo-preview { width: 120px; height: 120px; object-fit: contain; background: #fff }
.logo-placeholder { font-size: 28px; color: #8c939d }
</style>