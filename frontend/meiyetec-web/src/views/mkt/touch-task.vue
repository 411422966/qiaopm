<template>
  <div class="touch-task-page">
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="任务名 / 编号" style="width: 200px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.channelType" placeholder="渠道" clearable style="width: 120px" @change="loadData">
        <el-option label="短信" value="SMS" />
        <el-option label="小程序" value="MINI_PROGRAM" />
        <el-option label="公众号" value="MP" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="待执行" :value="1" />
        <el-option label="执行中" :value="2" />
        <el-option label="已完成" :value="3" />
        <el-option label="已暂停" :value="4" />
        <el-option label="已失败" :value="5" />
        <el-option label="已取消" :value="6" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openCreate">+ 新建任务</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="taskNo" label="任务号" width="170" />
        <el-table-column prop="campaignName" label="活动名称" min-width="180" />
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.channelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerEvent" label="触发事件" width="160" />
        <el-table-column label="目标/成功/失败" width="160">
          <template #default="{ row }">
            <span>{{ row.targetCount }} / </span>
            <span style="color: #67c23a">{{ row.successCount }}</span>
            <span> / </span>
            <span style="color: #f56c6c">{{ row.failCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已读/转化" width="120">
          <template #default="{ row }">
            <span style="color: #409eff">{{ row.readCount }}</span>
            <span> / </span>
            <span style="color: #e6a23c">{{ row.convertCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="scheduledAt" label="计划时间" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openLog(row)">明细</el-button>
            <el-button link size="small" type="primary" :disabled="row.status !== 1" @click="start(row)">启动</el-button>
            <el-button link size="small" type="warning" :disabled="row.status !== 2" @click="pause(row)">暂停</el-button>
            <el-button link size="small" type="success" :disabled="row.status !== 4" @click="resume(row)">恢复</el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 3 || row.status === 6" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新建 -->
    <el-dialog v-model="dialog.visible" title="新建触达任务" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.campaignName" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="渠道" required>
              <el-select v-model="form.channelType" style="width: 100%" @change="onChannelChange">
                <el-option label="短信" value="SMS" />
                <el-option label="小程序订阅消息" value="MINI_PROGRAM" />
                <el-option label="公众号" value="MP" />
                <el-option label="站内信" value="IN_APP" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板">
              <el-select v-model="form.templateId" style="width: 100%">
                <el-option v-for="t in templates" :key="t.id" :label="`${t.templateCode} - ${t.templateName}`" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="触发事件">
          <el-select v-model="form.triggerEvent" style="width: 100%">
            <el-option label="手动" value="MANUAL" />
            <el-option label="订单完成" value="ORDER_COMPLETED" />
            <el-option label="套餐交付" value="CARD_DELIVERED" />
            <el-option label="会员生日" value="BIRTHDAY" />
            <el-option label="到期前30天" value="EXPIRE_30D" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间">
          <el-date-picker v-model="form.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="立即执行" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细 -->
    <el-dialog v-model="logDialog.visible" :title="`触达明细 ${logDialog.task?.taskNo || ''}`" width="900px">
      <el-table :data="logDialog.logs || []" v-loading="logDialog.loading" border size="small">
        <el-table-column prop="memberName" label="会员" width="100" />
        <el-table-column prop="memberPhone" label="手机" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="logStatusTag(row.status)">{{ logStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="retryCount" label="重试次数" width="80" align="center" />
        <el-table-column prop="externalMsgId" label="外部消息ID" width="180" show-overflow-tooltip />
        <el-table-column prop="sentAt" label="发送时间" width="170" />
        <el-table-column prop="readAt" label="已读时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const filter = reactive({
  pageNum: 1, pageSize: 20, keyword: '',
  channelType: undefined as string | undefined, status: undefined as number | undefined
})
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const templates = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/mkt/task/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.keyword = ''; filter.channelType = undefined
  filter.status = undefined; filter.pageNum = 1
  loadData()
}

function statusLabel(s: number) {
  return ({ 1: '待执行', 2: '执行中', 3: '已完成', 4: '已暂停', 5: '已失败', 6: '已取消' } as any)[s] || '—'
}
function statusTag(s: number) {
  return ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'primary', 5: 'danger', 6: 'info' } as any)[s] || 'info'
}
function logStatusLabel(s: number) {
  return ({ 1: '待发送', 2: '已发送', 3: '失败', 4: '已读', 5: '已转化', 6: '已取消' } as any)[s] || '—'
}
function logStatusTag(s: number) {
  return ({ 1: 'info', 2: 'primary', 3: 'danger', 4: 'warning', 5: 'success', 6: 'info' } as any)[s] || 'info'
}

const dialog = reactive({ visible: false, saving: false })
const form = reactive<any>({
  campaignName: '', channelType: 'SMS', templateId: undefined,
  triggerEvent: 'MANUAL', scheduledAt: null, remark: ''
})

async function onChannelChange() {
  await loadTemplates()
}
async function loadTemplates() {
  const r = await http.get('/mkt/template/list', { params: { channelType: form.channelType } })
  templates.value = r.data || []
}

function openCreate() { dialog.visible = true; loadTemplates() }
function resetDialog() {
  Object.assign(form, { campaignName: '', channelType: 'SMS', templateId: undefined, triggerEvent: 'MANUAL', scheduledAt: null, remark: '' })
}

async function save() {
  if (!form.campaignName || !form.channelType) return ElMessage.warning('活动名称 / 渠道 必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    await http.post('/mkt/task', form, { headers })
    ElMessage.success('已创建')
    dialog.visible = false
    loadData()
  } finally {
    dialog.saving = false
  }
}

async function start(row: any) {
  const r = await http.post(`/mkt/task/${row.id}/start`)
  ElMessage.success(`成功 ${r.data?.successCount} 失败 ${r.data?.failCount}`)
  loadData()
}

async function pause(row: any) {
  await http.post(`/mkt/task/${row.id}/pause`)
  ElMessage.success('已暂停')
  loadData()
}
async function resume(row: any) {
  await http.post(`/mkt/task/${row.id}/resume`)
  ElMessage.success('已恢复')
  loadData()
}
async function cancel(row: any) {
  await ElMessageBox.confirm(`取消任务「${row.campaignName}」？`, '提示', { type: 'warning' })
  await http.post(`/mkt/task/${row.id}/cancel`)
  ElMessage.success('已取消')
  loadData()
}

const logDialog = reactive<any>({ visible: false, task: null, logs: null, loading: false })
async function openLog(row: any) {
  logDialog.task = row
  logDialog.visible = true
  logDialog.loading = true
  try {
    const r = await http.get(`/mkt/task/${row.id}/log?pageSize=200`)
    logDialog.logs = r.data?.records || []
  } finally {
    logDialog.loading = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-input, .search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
</style>
