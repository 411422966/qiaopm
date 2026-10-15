<template>
  <div class="waitlist-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">等待中</div>
        <div class="kpi-value">{{ countByStatus(1) }}</div>
      </div>
      <div class="kpi-item yellow">
        <div class="kpi-label">已通知</div>
        <div class="kpi-value">{{ countByStatus(2) }}</div>
      </div>
      <div class="kpi-item green">
        <div class="kpi-label">已转预约</div>
        <div class="kpi-value">{{ countByStatus(3) }}</div>
      </div>
      <div class="kpi-item gray">
        <div class="kpi-label">已取消</div>
        <div class="kpi-value">{{ countByStatus(4) }}</div>
      </div>
      <div class="kpi-item red">
        <div class="kpi-label">已过期</div>
        <div class="kpi-value">{{ countByStatus(5) }}</div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="候补号 / 会员 / 项目" style="width: 220px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="等待" :value="1" />
        <el-option label="已通知" :value="2" />
        <el-option label="已转预约" :value="3" />
        <el-option label="已取消" :value="4" />
        <el-option label="已过期" :value="5" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openCreate">+ 新增候补</el-button>
      <el-button type="warning" @click="openAutoFill">⚡ 自动补位</el-button>
      <el-button type="success" @click="openSmartFill" data-testid="btn-m13-smart-fill">🤖 AI 智能补位</el-button>
      <el-button type="info" @click="openScoreConfig" plain>⚙️ 评分配置</el-button>
      <el-button @click="exportCsv">导出 CSV</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="waitlistNo" label="候补号" width="170" />
        <el-table-column prop="memberName" label="会员" width="100" />
        <el-table-column prop="memberPhone" label="电话" width="130" />
        <el-table-column prop="serviceItemName" label="项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="employeeName" label="技师" width="100" />
        <el-table-column prop="preferredDate" label="期望日期" width="120" />
        <el-table-column label="时段" width="140">
          <template #default="{ row }">
            {{ formatTime(row.preferredTimeFrom) }} ~ {{ formatTime(row.preferredTimeTo) }}
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="AI 评分" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lastScore != null" :type="scoreTag(row.lastScore)" size="small">
              {{ row.lastScore?.toFixed?.(1) ?? row.lastScore }}
            </el-tag>
            <span v-else style="color:#94a3b8;font-size:11px">未评</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notifyCount" label="通知" width="80" align="center" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="danger" :disabled="row.status !== 1" @click="cancel(row)">取消</el-button>
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

    <!-- 新增候补 -->
    <el-dialog v-model="dialog.visible" title="新增候补" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="会员" required>
              <el-input v-model="form.memberName" placeholder="会员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话" required>
              <el-input v-model="form.memberPhone" placeholder="13800138000" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="项目" required>
          <el-select v-model="form.serviceItemId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="s in serviceList" :key="s.id" :label="`${s.serviceCode} - ${s.serviceName}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="技师">
          <el-input v-model="form.employeeName" placeholder="可空" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="期望日期" required>
              <el-date-picker v-model="form.preferredDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时段起">
              <el-time-picker v-model="form.preferredTimeFrom" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时段止">
              <el-time-picker v-model="form.preferredTimeTo" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" style="width: 100%" />
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

    <!-- 自动补位 -->
    <el-dialog v-model="fillDialog.visible" title="自动补位" width="500px" @closed="resetFill">
      <el-form :model="fillForm" label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="fillForm.serviceItemId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="s in serviceList" :key="s.id" :label="`${s.serviceCode} - ${s.serviceName}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="技师">
          <el-input v-model="fillForm.employeeName" placeholder="可空" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="日期" required>
              <el-date-picker v-model="fillForm.preferredDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时段起">
              <el-time-picker v-model="fillForm.startTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时段止">
              <el-time-picker v-model="fillForm.endTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="fillDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="runAutoFill" :loading="fillDialog.running">执行</el-button>
      </template>
    </el-dialog>

    <!-- M13 · AI 智能补位（评分 + 预览 + 一键执行） -->
    <el-dialog v-model="smartFillDialog.visible" title="🤖 AI 智能补位 · 评分驱动" width="980px" @closed="resetSmartFill" top="5vh">
      <el-form :model="smartFillForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="项目" required>
              <el-select v-model="smartFillForm.serviceItemId" placeholder="选择项目" filterable style="width: 100%">
                <el-option v-for="s in serviceList" :key="s.id" :label="`${s.serviceCode} - ${s.serviceName}`" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="日期" required>
              <el-date-picker v-model="smartFillForm.preferredDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="严格日期">
              <el-switch v-model="smartFillForm.strictMatch" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="时段起">
              <el-time-picker v-model="smartFillForm.startTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时段止">
              <el-time-picker v-model="smartFillForm.endTime" format="HH:mm" value-format="HH:mm" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="执行模式">
              <el-radio-group v-model="smartFillDialog.mode">
                <el-radio-button label="preview">评分预览</el-radio-button>
                <el-radio-button label="execute">一键执行</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 评分维度配置（只读，从 DB 加载） -->
      <el-card v-if="smartFillDialog.config" shadow="never" class="config-card">
        <div class="config-title">
          <el-icon><DataAnalysis /></el-icon>
          评分权重配置
          <el-tag size="small" :type="smartFillDialog.config.enabled ? 'success' : 'info'" style="margin-left:8px">
            {{ smartFillDialog.config.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </div>
        <el-row :gutter="8">
          <el-col :span="4"><el-statistic title="期望匹配" :value="(smartFillDialog.config.wPreference*100).toFixed(0) + '%'" /></el-col>
          <el-col :span="4"><el-statistic title="会员价值" :value="(smartFillDialog.config.wValue*100).toFixed(0) + '%'" /></el-col>
          <el-col :span="4"><el-statistic title="爽约风险" :value="(smartFillDialog.config.wNoShow*100).toFixed(0) + '%'" /></el-col>
          <el-col :span="4"><el-statistic title="等待时长" :value="(smartFillDialog.config.wWaitTime*100).toFixed(0) + '%'" /></el-col>
          <el-col :span="4"><el-statistic title="响应率" :value="(smartFillDialog.config.wResponse*100).toFixed(0) + '%'" /></el-col>
          <el-col :span="4"><el-statistic title="通知阈值" :value="smartFillDialog.config.minScoreToNotify" /></el-col>
        </el-row>
        <div class="config-meta">
          单轮最多 {{ smartFillDialog.config.maxNotifyPerRound }} 人 · 冷却 {{ smartFillDialog.config.cooldownMinutes }} 分钟 · 等待封顶 {{ smartFillDialog.config.waitTimeCapHours }}h · 锁定会员 {{ smartFillDialog.config.lockedMemberAction }}
        </div>
      </el-card>

      <!-- 评分结果 -->
      <el-card v-if="smartFillDialog.scored.length > 0" shadow="never" class="result-card">
        <div class="result-title">
          <el-icon><Trophy /></el-icon>
          候选候补评分（已按总分排序）
          <el-tag type="success" size="small" style="margin-left:8px">
            {{ smartFillDialog.scored.filter((s:any) => s.eligible).length }} / {{ smartFillDialog.scored.length }} 可通知
          </el-tag>
        </div>
        <el-table :data="smartFillDialog.scored" v-loading="smartFillDialog.loading" border stripe max-height="380">
          <el-table-column prop="ranking" label="排名" width="60" align="center">
            <template #default="{ row }">
              <el-tag :type="row.ranking === 1 ? 'danger' : (row.ranking <= 3 ? 'warning' : 'info')" size="small">
                #{{ row.ranking }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="memberName" label="会员" width="100" />
          <el-table-column prop="memberPhone" label="电话" width="120" />
          <el-table-column prop="preferredDate" label="期望日期" width="110" />
          <el-table-column label="期望时段" width="120">
            <template #default="{ row }">
              {{ formatTime(row.preferredTimeFrom) }} ~ {{ formatTime(row.preferredTimeTo) }}
            </template>
          </el-table-column>
          <el-table-column label="综合分" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="scoreTag(row.totalScore)" size="default" effect="dark">
                {{ row.totalScore?.toFixed?.(1) ?? row.totalScore }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="5 维度" min-width="280">
            <template #default="{ row }">
              <div class="dim-bars">
                <div class="dim-bar" v-for="d in scoreDims(row)" :key="d.key">
                  <span class="dim-label">{{ d.label }}</span>
                  <el-progress :percentage="d.value" :stroke-width="10" :show-text="false" :color="dimColor(d.value)" />
                  <span class="dim-val">{{ d.value?.toFixed?.(1) ?? d.value }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="通知" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.eligible ? 'success' : 'danger'" size="small">
                {{ row.eligible ? '可通知' : '跳过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rejectReason" label="原因" min-width="160" show-overflow-tooltip />
        </el-table>
      </el-card>

      <template #footer>
        <el-button @click="smartFillDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="runScorePreview" :loading="smartFillDialog.loading">
          🔍 评分预览
        </el-button>
        <el-button type="success" @click="runSmartFill" :loading="smartFillDialog.running" v-if="smartFillDialog.mode === 'execute'">
          ✅ 一键执行通知
        </el-button>
      </template>
    </el-dialog>

    <!-- M13 · 评分配置（读写） -->
    <el-dialog v-model="configDialog.visible" title="⚙️ 评分权重配置" width="720px" @closed="resetConfig">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        5 个维度权重之和必须为 1.0（±0.01）。保存后立即生效，影响后续评分与自动补位。
      </el-alert>
      <el-form :model="configForm" label-width="180px">
        <el-form-item label="期望匹配（preference）">
          <el-slider v-model="configForm.wPreference" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" input-size="small" />
        </el-form-item>
        <el-form-item label="会员价值（value）">
          <el-slider v-model="configForm.wValue" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" input-size="small" />
        </el-form-item>
        <el-form-item label="爽约风险（noShow）">
          <el-slider v-model="configForm.wNoShow" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" input-size="small" />
        </el-form-item>
        <el-form-item label="等待时长（waitTime）">
          <el-slider v-model="configForm.wWaitTime" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" input-size="small" />
        </el-form-item>
        <el-form-item label="响应率（response）">
          <el-slider v-model="configForm.wResponse" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" input-size="small" />
        </el-form-item>
        <el-divider>策略阈值</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="通知最低分">
              <el-input-number v-model="configForm.minScoreToNotify" :min="0" :max="100" :step="5" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单轮最多通知">
              <el-input-number v-model="configForm.maxNotifyPerRound" :min="1" :max="20" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="通知冷却(分钟)">
              <el-input-number v-model="configForm.cooldownMinutes" :min="0" :max="1440" :step="15" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="等待封顶(h小时)">
              <el-input-number v-model="configForm.waitTimeCapHours" :min="1" :max="48" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="锁定会员处置">
              <el-radio-group v-model="configForm.lockedMemberAction">
                <el-radio-button label="SKIP">跳过</el-radio-button>
                <el-radio-button label="NOTIFY">仍通知</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="评分启用">
              <el-switch v-model="configForm.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="configDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveScoreConfig" :loading="configDialog.saving">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Trophy } from '@element-plus/icons-vue'
import { newIdempotencyKey } from '@/utils/idempotency'

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', status: undefined as number | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const serviceList = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/biz/booking/waitlist/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadServiceList() {
  const r = await http.get('/biz/service/item/page', { params: { pageNum: 1, pageSize: 200, status: 1 } })
  serviceList.value = r.data?.records || []
}

function countByStatus(s: number) { return rows.value.filter((r: any) => r.status === s).length }

function statusLabel(s: number) {
  return { 1: '等待', 2: '已通知', 3: '已转预约', 4: '已取消', 5: '已过期' }[s] || '未知'
}
function statusTag(s: number) {
  return ({ 1: 'primary', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' } as any)[s] || 'info'
}

function formatTime(t: string) {
  if (!t) return ''
  return t.length > 5 ? t.substring(0, 5) : t
}

function resetFilter() {
  filter.keyword = ''
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

const dialog = reactive({ visible: false, saving: false })
const form = reactive<any>({
  memberName: '', memberPhone: '',
  serviceItemId: undefined, employeeId: undefined, employeeName: '',
  preferredDate: undefined, preferredTimeFrom: undefined, preferredTimeTo: undefined,
  priority: 0, remark: ''
})

function openCreate() {
  dialog.visible = true
}

function resetDialog() {
  Object.assign(form, {
    memberName: '', memberPhone: '',
    serviceItemId: undefined, employeeId: undefined, employeeName: '',
    preferredDate: undefined, preferredTimeFrom: undefined, preferredTimeTo: undefined,
    priority: 0, remark: ''
  })
}

async function save() {
  if (!form.memberName || !form.memberPhone) return ElMessage.warning('会员和电话必填')
  if (!form.serviceItemId) return ElMessage.warning('项目必填')
  if (!form.preferredDate) return ElMessage.warning('期望日期必填')
  dialog.saving = true
  try {
    const itm = serviceList.value.find((s: any) => s.id === form.serviceItemId)
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    await http.post('/biz/booking/waitlist', {
      ...form,
      serviceItemName: itm?.serviceName,
      operatorName: 'admin'
    }, { headers })
    ElMessage.success('已添加')
    dialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    dialog.saving = false
  }
}

async function cancel(row: any) {
  await http.post(`/biz/booking/waitlist/${row.id}/cancel`)
  ElMessage.success('已取消')
  loadData()
}

const fillDialog = reactive({ visible: false, running: false })
const fillForm = reactive<any>({
  serviceItemId: undefined, employeeName: '',
  preferredDate: undefined, startTime: undefined, endTime: undefined
})

function openAutoFill() {
  fillDialog.visible = true
}

function resetFill() {
  Object.assign(fillForm, {
    serviceItemId: undefined, employeeName: '',
    preferredDate: undefined, startTime: undefined, endTime: undefined
  })
}

async function runAutoFill() {
  if (!fillForm.serviceItemId) return ElMessage.warning('项目必填')
  if (!fillForm.preferredDate) return ElMessage.warning('日期必填')
  fillDialog.running = true
  try {
    const itm = serviceList.value.find((s: any) => s.id === fillForm.serviceItemId)
    const r = await http.post('/biz/booking/waitlist/auto-fill', {
      serviceItemId: fillForm.serviceItemId,
      serviceItemName: itm?.serviceName,
      employeeId: null,
      preferredDate: fillForm.preferredDate,
      startTime: fillForm.startTime || '00:00',
      endTime: fillForm.endTime || '23:59'
    })
    ElMessage.success(`已通知 ${r.data.notifiedCount} 位候补`)
    fillDialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '执行失败')
  } finally {
    fillDialog.running = false
  }
}

async function exportCsv() {
  try {
    const res = await http.get('/biz/booking/waitlist/export', {
      params: { status: filter.status },
      responseType: 'blob'
    })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '候补队列.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

// ============ M13 · AI 智能补位（评分驱动） ============

const smartFillDialog = reactive<any>({
  visible: false, mode: 'preview',
  loading: false, running: false,
  config: null as any,
  scored: [] as any[]
})

const smartFillForm = reactive<any>({
  serviceItemId: undefined, employeeName: '',
  preferredDate: undefined, startTime: undefined, endTime: undefined,
  strictMatch: false
})

async function openSmartFill() {
  smartFillDialog.visible = true
  smartFillDialog.mode = 'preview'
  smartFillDialog.scored = []
  await loadScoreConfig()
}

function resetSmartFill() {
  Object.assign(smartFillForm, {
    serviceItemId: undefined, employeeName: '',
    preferredDate: undefined, startTime: undefined, endTime: undefined,
    strictMatch: false
  })
  smartFillDialog.scored = []
  smartFillDialog.config = null
}

async function loadScoreConfig() {
  try {
    const r = await http.get('/biz/booking/waitlist/score-config')
    smartFillDialog.config = r.data
    // 同步到配置对话框表单（保留小数精度）
    configForm.wPreference = Number(r.data.wPreference) || 0.30
    configForm.wValue = Number(r.data.wValue) || 0.25
    configForm.wNoShow = Number(r.data.wNoShow) || 0.20
    configForm.wWaitTime = Number(r.data.wWaitTime) || 0.15
    configForm.wResponse = Number(r.data.wResponse) || 0.10
    configForm.minScoreToNotify = Number(r.data.minScoreToNotify) || 30
    configForm.maxNotifyPerRound = Number(r.data.maxNotifyPerRound) || 5
    configForm.cooldownMinutes = Number(r.data.cooldownMinutes) || 60
    configForm.waitTimeCapHours = Number(r.data.waitTimeCapHours) || 8
    configForm.lockedMemberAction = r.data.lockedMemberAction || 'SKIP'
    configForm.enabled = r.data.enabled ? 1 : 0
  } catch (e) {
    ElMessage.warning('读取评分配置失败，已使用默认权重')
  }
}

async function runScorePreview() {
  if (!smartFillForm.serviceItemId) return ElMessage.warning('项目必填')
  if (!smartFillForm.preferredDate) return ElMessage.warning('日期必填')
  smartFillDialog.loading = true
  try {
    const itm = serviceList.value.find((s: any) => s.id === smartFillForm.serviceItemId)
    const r = await http.post('/biz/booking/waitlist/score-preview', {
      serviceItemId: smartFillForm.serviceItemId,
      serviceItemName: itm?.serviceName,
      employeeId: null,
      preferredDate: smartFillForm.preferredDate,
      startTime: smartFillForm.startTime || '00:00',
      endTime: smartFillForm.endTime || '23:59',
      strictMatch: smartFillForm.strictMatch
    })
    smartFillDialog.config = r.data.config
    smartFillDialog.scored = r.data.scored || []
    if (smartFillDialog.scored.length === 0) {
      ElMessage.info('无候选候补')
    } else {
      const eligible = smartFillDialog.scored.filter((s: any) => s.eligible).length
      ElMessage.success(`已评分 ${smartFillDialog.scored.length} 位候补，${eligible} 位可通知`)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '评分预览失败')
  } finally {
    smartFillDialog.loading = false
  }
}

async function runSmartFill() {
  if (!smartFillForm.serviceItemId) return ElMessage.warning('项目必填')
  if (!smartFillForm.preferredDate) return ElMessage.warning('日期必填')
  // 先跑一次预览，确保 scored 不为空（用户可观察）
  if (smartFillDialog.scored.length === 0) {
    await runScorePreview()
    if (smartFillDialog.scored.length === 0) return
  }
  smartFillDialog.running = true
  try {
    const itm = serviceList.value.find((s: any) => s.id === smartFillForm.serviceItemId)
    const r = await http.post('/biz/booking/waitlist/smart-fill', {
      serviceItemId: smartFillForm.serviceItemId,
      serviceItemName: itm?.serviceName,
      employeeId: null,
      preferredDate: smartFillForm.preferredDate,
      startTime: smartFillForm.startTime || '00:00',
      endTime: smartFillForm.endTime || '23:59',
      strictMatch: smartFillForm.strictMatch
    })
    ElMessage.success(`AI 评分通知：共 ${r.data.scoredCount} 位候选 → 实际通知 ${r.data.notifiedCount} 位`)
    smartFillDialog.scored = [...(r.data.ranked || []), ...(r.data.skipped || [])]
      .sort((a: any, b: any) => (a.ranking ?? 99) - (b.ranking ?? 99))
    smartFillDialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '执行失败')
  } finally {
    smartFillDialog.running = false
  }
}

// 评分可视化辅助
function scoreTag(s: number | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (s == null) return 'info'
  if (s >= 80) return 'success'
  if (s >= 60) return 'warning'
  return 'danger'
}
function dimColor(v: number): string {
  if (v >= 80) return '#10b981'
  if (v >= 60) return '#f59e0b'
  return '#ef4444'
}
function scoreDims(row: any): Array<{ key: string; label: string; value: number }> {
  return [
    { key: 'preference', label: '期望', value: Number(row.preferenceScore ?? 0) },
    { key: 'value', label: '价值', value: Number(row.valueScore ?? 0) },
    { key: 'noShow', label: '风险', value: Number(row.noShowScore ?? 0) },
    { key: 'waitTime', label: '等待', value: Number(row.waitTimeScore ?? 0) },
    { key: 'response', label: '响应', value: Number(row.responseScore ?? 0) }
  ]
}

// ============ M13 · 评分配置 ============

const configDialog = reactive({ visible: false, saving: false })
const configForm = reactive<any>({
  wPreference: 0.30, wValue: 0.25, wNoShow: 0.20, wWaitTime: 0.15, wResponse: 0.10,
  minScoreToNotify: 30, maxNotifyPerRound: 5, cooldownMinutes: 60, waitTimeCapHours: 8,
  lockedMemberAction: 'SKIP', enabled: 1
})

async function openScoreConfig() {
  configDialog.visible = true
  await loadScoreConfig()
}

function resetConfig() {
  Object.assign(configForm, {
    wPreference: 0.30, wValue: 0.25, wNoShow: 0.20, wWaitTime: 0.15, wResponse: 0.10,
    minScoreToNotify: 30, maxNotifyPerRound: 5, cooldownMinutes: 60, waitTimeCapHours: 8,
    lockedMemberAction: 'SKIP', enabled: 1
  })
}

async function saveScoreConfig() {
  // 前端预校验
  const sum = (configForm.wPreference + configForm.wValue + configForm.wNoShow +
    configForm.wWaitTime + configForm.wResponse).toFixed(2)
  if (Math.abs(Number(sum) - 1.0) > 0.01) {
    return ElMessage.error(`5 个权重之和必须为 1.0（当前=${sum}）`)
  }
  configDialog.saving = true
  try {
    await http.put('/biz/booking/waitlist/score-config', { ...configForm })
    ElMessage.success('评分配置已保存')
    configDialog.visible = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    configDialog.saving = false
  }
}

onMounted(async () => {
  await loadServiceList()
  await loadData()
})
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.gray { background: linear-gradient(135deg, #f3f4f6, #e5e7eb); }
.kpi-item.red { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }

/* M13 评分 */
.config-card { margin-top: 12px; }
.config-card .config-title { font-weight: 600; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }
.config-card .config-meta { font-size: 12px; color: #64748b; margin-top: 8px; }
.result-card { margin-top: 12px; }
.result-card .result-title { font-weight: 600; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }
.dim-bars { display: flex; flex-direction: column; gap: 4px; min-width: 260px; }
.dim-bar { display: grid; grid-template-columns: 38px 1fr 38px; gap: 6px; align-items: center; font-size: 11px; }
.dim-bar .dim-label { color: #475569; }
.dim-bar .dim-val { color: #1e293b; text-align: right; font-weight: 600; }
</style>
