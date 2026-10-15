<template>
  <div class="channel-page">
    <el-card class="search-bar">
      <el-button type="primary" @click="loadData">刷新</el-button>
      <el-button type="success" @click="healthCheck">🩺 健康检查</el-button>
      <el-button type="primary" @click="openCreate">+ 新增渠道</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" border stripe>
        <el-table-column label="渠道" width="120">
          <template #default="{ row }">
            <el-tag :type="channelTag(row.channelType)" size="small">{{ channelLabel(row.channelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="channelName" label="名称" min-width="160" />
        <el-table-column prop="provider" label="提供商" width="120" />
        <el-table-column prop="signature" label="签名/前缀" width="120" />
        <el-table-column label="限流" width="160">
          <template #default="{ row }">
            <span>{{ row.rateLimitPerMin }}/min · {{ row.rateLimitPerDay }}/day</span>
          </template>
        </el-table-column>
        <el-table-column label="重试" width="120">
          <template #default="{ row }">
            <span>{{ row.retryMax }}次 / {{ row.retryIntervalSec }}s</span>
          </template>
        </el-table-column>
        <el-table-column label="健康" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.lastHealthMsg === 'OK' ? 'success' : 'warning'">
              {{ row.lastHealthMsg || '未检查' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑渠道' : '新增渠道'" width="600px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="渠道" required>
              <el-select v-model="form.channelType" style="width: 100%" :disabled="!!dialog.id">
                <el-option label="短信" value="SMS" />
                <el-option label="小程序订阅消息" value="MINI_PROGRAM" />
                <el-option label="公众号" value="MP" />
                <el-option label="企业微信" value="WECHAT_WORK" />
                <el-option label="站内信" value="IN_APP" />
                <el-option label="邮件" value="EMAIL" />
                <el-option label="APP推送" value="PUSH" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.channelName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="提供商"><el-input v-model="form.provider" placeholder="阿里云/微信/腾讯云..." /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签名"><el-input v-model="form.signature" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="每分钟限额"><el-input-number v-model="form.rateLimitPerMin" :min="1" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每日限额"><el-input-number v-model="form.rateLimitPerDay" :min="1" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="重试次数"><el-input-number v-model="form.retryMax" :min="0" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重试间隔(s)"><el-input-number v-model="form.retryIntervalSec" :min="1" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="凭证(JSON)">
          <el-input v-model="form.configJson" type="textarea" :rows="3" placeholder='{"accessKey":"...", "secret":"..."}' />
        </el-form-item>
        <el-form-item label="启用">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'

const rows = ref<any[]>([])

async function loadData() {
  const r = await http.get('/mkt/channel/list')
  rows.value = r.data || []
}

function channelLabel(t: string) {
  return ({ SMS: '短信', MINI_PROGRAM: '小程序', MP: '公众号', WECHAT_WORK: '企业微信', IN_APP: '站内信', EMAIL: '邮件', PUSH: 'APP推送' } as any)[t] || t
}
function channelTag(t: string) {
  return ({ SMS: 'primary', MINI_PROGRAM: 'success', MP: 'warning', WECHAT_WORK: 'info', IN_APP: '', EMAIL: 'danger', PUSH: '' } as any)[t] || ''
}

async function healthCheck() {
  const r = await http.post('/mkt/channel/health-check')
  const results = r.data?.results || []
  ElMessage.success(`检查完成：${results.length} 个渠道`)
  loadData()
}

const dialog = reactive({ visible: false, id: undefined as number | undefined })
const form = reactive<any>({
  channelType: 'SMS', channelName: '', provider: '', signature: '',
  rateLimitPerMin: 60, rateLimitPerDay: 5000, retryMax: 3, retryIntervalSec: 60,
  configJson: '', status: 1
})

function openCreate() { dialog.id = undefined; dialog.visible = true }
function openEdit(row: any) { dialog.id = row.id; Object.assign(form, row); dialog.visible = true }
function resetDialog() {
  Object.assign(form, { channelType: 'SMS', channelName: '', provider: '', signature: '', rateLimitPerMin: 60, rateLimitPerDay: 5000, retryMax: 3, retryIntervalSec: 60, configJson: '', status: 1 })
}

async function save() {
  if (!form.channelName || !form.channelType) return ElMessage.warning('名称 / 渠道 必填')
  if (dialog.id) {
    await http.put(`/mkt/channel/${dialog.id}`, form)
    ElMessage.success('已更新')
  } else {
    await http.post('/mkt/channel', form)
    ElMessage.success('已创建')
  }
  dialog.visible = false
  loadData()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除渠道「${row.channelName}」？`, '提示', { type: 'warning' })
  await http.delete(`/mkt/channel/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
</style>
