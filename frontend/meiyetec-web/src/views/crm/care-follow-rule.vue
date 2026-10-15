<template>
  <div class="follow-rule-page">
    <!-- ① 页头 + KPI -->
    <el-card class="header-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon><SetUp /></el-icon>
          <span class="title">自动任务规则</span>
          <el-tag effect="plain" type="info" round>{{ total }} 条</el-tag>
        </div>
        <div class="page-tools">
          <el-button :icon="Refresh" plain @click="loadAll">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建规则</el-button>
        </div>
      </div>

      <div class="kpi-mini-row">
        <div class="kpi-mini"><span class="muted">规则总数</span><strong>{{ stats.total || 0 }}</strong></div>
        <div class="kpi-mini"><span class="muted">已启用</span><strong class="success">{{ stats.enabled || 0 }}</strong></div>
        <div class="kpi-mini"><span class="muted">今日自动生成</span><strong>{{ stats.todayGenerated || 0 }}</strong></div>
        <div class="kpi-mini"><span class="muted">本月自动生成</span><strong>{{ stats.monthGenerated || 0 }}</strong></div>
      </div>
    </el-card>

    <!-- ② 筛选 -->
    <el-card class="search-bar">
      <el-form :inline="true">
        <el-form-item label="关键字">
          <el-input v-model="filter.keyword" placeholder="编码 / 名称 / 主题" clearable
            style="width: 220px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filter.planType" clearable placeholder="全部" style="width: 140px" @change="loadData">
            <el-option label="疗程后" value="POST_TREATMENT" />
            <el-option label="生日" value="BIRTHDAY" />
            <el-option label="到期" value="EXPIRE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.enabled" clearable placeholder="全部" style="width: 110px" @change="loadData">
            <el-option label="已启用" :value="1" />
            <el-option label="已停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ③ 表格 -->
    <el-card class="table-card" v-loading="loading">
      <el-table :data="rules" border stripe :row-key="(r: any) => r.id">
        <el-table-column prop="planCode" label="编码" width="150" />
        <el-table-column label="规则名称" min-width="220">
          <template #default="{ row }">
            <div class="rule-name">
              <span>{{ row.planName }}</span>
              <el-tag v-if="!row.enabled" size="small" type="info" effect="plain">已停用</el-tag>
            </div>
            <div class="muted rule-sub">{{ row.subjectTemplate || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.planType)" effect="plain">{{ typeLabel(row.planType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发条件" width="180">
          <template #default="{ row }">
            <div>
              <span>{{ triggerDesc(row) }}</span>
            </div>
            <div class="muted rule-sub">
              {{ frequencyLabel(row.frequency) }} {{ row.triggerTime || '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="限定" width="160">
          <template #default="{ row }">
            <div v-if="row.storeIds" class="muted">门店 {{ row.storeIds }}</div>
            <div v-else class="muted">全部门店</div>
            <div v-if="row.minAmount" class="muted">订单 ≥ ¥{{ row.minAmount }}</div>
            <div v-else-if="row.planType === 'POST_TREATMENT'" class="muted">不限金额</div>
          </template>
        </el-table-column>
        <el-table-column label="默认处理人" width="120" prop="defaultOwnerName">
          <template #default="{ row }">
            <span v-if="row.defaultOwnerName">{{ row.defaultOwnerName }}</span>
            <span v-else class="muted">未指定</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1"
              @change="(v: boolean) => toggleEnabled(row, v)"
              active-color="#67c23a" />
          </template>
        </el-table-column>
        <el-table-column label="最近触发" width="160">
          <template #default="{ row }">
            <div v-if="row.lastRunAt" class="muted">{{ row.lastRunAt }}</div>
            <div v-if="row.lastRunCount != null" class="success">生成 {{ row.lastRunCount }} 条</div>
            <div v-else-if="!row.lastRunAt" class="muted">从未触发</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" @click="openPreview(row)">预览</el-button>
            <el-button link size="small" type="warning" @click="triggerOne(row)">触发</el-button>
            <el-button link size="small" type="danger" @click="deleteOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- ④ 编辑/新建弹窗 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑规则' : '新建规则'" width="720px" @closed="resetForm">
      <el-form :model="form" label-width="110px">
        <el-form-item label="规则编码" required>
          <el-input v-model="form.planCode" :disabled="!!form.id" placeholder="POST_TREATMENT_3D" />
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="form.planName" placeholder="如：3 天疗程后回访" />
        </el-form-item>
        <el-form-item label="触发类型" required>
          <el-select v-model="form.planType" style="width: 100%" @change="onTypeChange">
            <el-option label="疗程后回访" value="POST_TREATMENT" />
            <el-option label="生日关怀" value="BIRTHDAY" />
            <el-option label="到期提醒" value="EXPIRE" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发时机">
          <el-input-number v-model="form.triggerOffsetDays" :min="-365" :max="365" />
          <span class="form-tip">天（负数=前 N 天触发；正数=后 N 天触发）</span>
        </el-form-item>
        <el-form-item label="触发频率">
          <el-radio-group v-model="form.frequency">
            <el-radio value="DAILY">每日</el-radio>
            <el-radio value="WEEKLY">每周</el-radio>
            <el-radio value="MONTHLY">每月</el-radio>
            <el-radio value="ONCE">一次性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="触发时间" v-if="form.frequency !== 'ONCE'">
          <el-time-picker v-model="form.triggerTime" placeholder="如 09:00" format="HH:mm" value-format="HH:mm:ss" />
          <span class="form-tip">每日到点触发</span>
        </el-form-item>
        <el-form-item label="限定门店">
          <el-select v-model="storeIdsArr" multiple filterable collapse-tags
            collapse-tags-tooltip placeholder="不选=全部门店" style="width: 100%"
            @change="onStoreChange">
            <el-option v-for="s in storeOptions" :key="s.id" :label="s.name" :value="String(s.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单金额" v-if="form.planType === 'POST_TREATMENT'">
          <el-input-number v-model="form.minAmount" :min="0" :precision="2" :step="100" placeholder="0 = 不限" />
          <span class="form-tip">元（疗程后回访订单金额 ≥ 该值）</span>
        </el-form-item>
        <el-form-item label="单日上限">
          <el-input-number v-model="form.maxPerDay" :min="1" :max="1000" />
          <span class="form-tip">防止单日生成过多任务</span>
        </el-form-item>
        <el-form-item label="默认主题" required>
          <el-input v-model="form.subjectTemplate" placeholder="如：{memberName} 您的疗程后效果如何？" />
        </el-form-item>
        <el-form-item label="回访要点">
          <el-input v-model="form.contentTemplate" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="建议话术">
          <el-input v-model="form.scriptTemplate" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="默认优先级">
          <el-radio-group v-model="form.priority">
            <el-radio :value="1">低</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">高</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="SLA（分钟）">
          <el-input-number v-model="form.slaMinutes" :min="60" :step="60" />
          <span class="form-tip">默认 1440（24h）</span>
        </el-form-item>
        <el-form-item label="默认处理人">
          <el-select v-model="form.defaultOwnerId" filterable clearable placeholder="选择员工"
            style="width: 100%" @change="onOwnerChange">
            <el-option v-for="u in ownerOptions" :key="u.id"
              :label="u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- ⑤ 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="预览：将触发的会员" width="720px">
      <div v-loading="previewLoading" class="preview-body">
      <el-alert v-if="previewData"
        :title="`${previewData.planName}`"
        type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #default>
          <div class="preview-alert-body">
            <div class="preview-alert-title">{{ previewData.planName }}</div>
            <div class="preview-alert-sub">
              <el-tag size="small" :type="typeTag(previewData.planType)" effect="plain" style="margin-right: 6px">
                {{ typeLabel(previewData.planType) }}
              </el-tag>
              <span class="muted">·</span>
              <span class="muted" style="margin: 0 6px">{{ triggerDesc(previewData) }}</span>
              <span class="muted">·</span>
              <span class="muted" style="margin-left: 6px">
                预计触发 <strong class="success">{{ previewData.totalCount ?? previewData.sample?.length ?? 0 }}</strong> 个会员，
                本次展示样本前 {{ previewData.sampleLimit ?? 50 }} 条<span
                  v-if="previewData.totalCount > (previewData.sampleLimit ?? 50)"
                  style="margin-left: 4px">（已截断）</span>
              </span>
            </div>
          </div>
        </template>
      </el-alert>
      <el-alert v-if="previewData"
        type="warning" :closable="false" show-icon
        title="预览/不写入任务，样本来自现有源数据扫描"
        style="margin-bottom: 12px" />
      <el-table v-if="previewData?.sample?.length" :data="previewData.sample" size="small" border>
        <el-table-column prop="name" label="会员" min-width="100">
          <template #default="{ row }">
            <span v-if="row.name">{{ row.name }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机" width="140">
          <template #default="{ row }">
            <span v-if="row.phone">{{ row.phone }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="store_id" label="门店" width="140">
          <template #default="{ row }">
            <span v-if="storeName(row.store_id)">{{ storeName(row.store_id) }}</span>
            <span v-else-if="row.store_id" class="muted">#{{ row.store_id }}</span>
            <span v-else class="muted">全部门店</span>
          </template>
        </el-table-column>
        <el-table-column prop="source_no" label="来源单号" min-width="160">
          <template #default="{ row }">
            <span v-if="row.source_no">{{ row.source_no }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!previewLoading" description="未匹配到任何会员">
        <template #description>
          <div>未匹配到任何会员</div>
          <div class="muted" style="margin-top: 4px; font-size: 12px">
            可能原因：条件过严 / 当前无即将到期 / 门店 / 套餐类型不符
          </div>
        </template>
        <el-button type="primary" plain size="small" style="margin-top: 8px" @click="closePreviewAndEdit">
          前往编辑规则
        </el-button>
      </el-empty>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, RefreshLeft, Plus,
  SetUp
} from '@element-plus/icons-vue'

// ====== 列表 ======
const loading = ref(false)
const rules = ref<any[]>([])
const total = ref(0)
const filter = reactive<any>({
  pageNum: 1, pageSize: 20,
  keyword: '', planType: undefined, enabled: undefined
})
const stats = reactive({ total: 0, enabled: 0, todayGenerated: 0, monthGenerated: 0 })

async function loadData() {
  loading.value = true
  try {
    const r: any = await http.get('/crm/care/follow/plan/page', { params: filter })
    rules.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载规则失败')
    rules.value = []; total.value = 0
  } finally { loading.value = false }
}
async function loadStats() {
  try {
    const r: any = await http.get('/crm/care/follow/plan/stats')
    Object.assign(stats, r.data || {})
  } catch { /* 静默 */ }
}
async function loadOptions() {
  try {
    const s: any = await http.get('/store/list').catch(() => null)
    storeOptions.value = s?.data || []
  } catch {}
  try {
    const u: any = await http.get('/employee/list').catch(() => null)
    ownerOptions.value = u?.data || []
  } catch {}
}
async function loadAll() { await Promise.all([loadData(), loadStats()]) }

function resetFilter() {
  Object.assign(filter, { keyword: '', planType: undefined, enabled: undefined, pageNum: 1 })
  loadData()
}

// ====== 字典 ======
function typeLabel(t: string) {
  return ({ POST_TREATMENT: '疗程后', BIRTHDAY: '生日', EXPIRE: '到期', CUSTOM: '自定义' } as any)[t] || t || '—'
}
function typeTag(t: string) {
  return ({ POST_TREATMENT: 'primary', BIRTHDAY: 'warning', EXPIRE: 'danger', CUSTOM: 'info' } as any)[t] || ''
}
function frequencyLabel(f: string) {
  return ({ DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月', ONCE: '一次性' } as any)[f] || '每日'
}
function triggerDesc(row: any) {
  const t = row.planType
  if (t === 'POST_TREATMENT') return `疗程后 ${row.triggerOffsetDays} 天`
  if (t === 'BIRTHDAY') return '会员生日当天'
  if (t === 'EXPIRE') return `到期前 ${Math.abs(row.triggerOffsetDays || 0)} 天`
  return '—'
}

// ====== 编辑 ======
const storeOptions = ref<any[]>([])
const ownerOptions = ref<any[]>([])
const editVisible = ref(false)
const editSaving = ref(false)
const form = reactive<any>(defaultForm())
const storeIdsArr = ref<string[]>([])

function defaultForm() {
  return {
    id: undefined,
    planCode: '',
    planName: '',
    planType: 'POST_TREATMENT',
    triggerOffsetDays: 3,
    triggerEvent: '',
    subjectTemplate: '疗程后回访',
    contentTemplate: '',
    scriptTemplate: '',
    priority: 2,
    slaMinutes: 1440,
    defaultOwnerId: undefined,
    defaultOwnerName: '',
    enabled: 1,
    frequency: 'DAILY',
    triggerTime: '09:00:00',
    storeIds: '',
    minAmount: undefined,
    maxPerDay: 100
  }
}
function resetForm() { Object.assign(form, defaultForm()); storeIdsArr.value = [] }

function openCreate() { resetForm(); editVisible.value = true }
async function openEdit(row: any) {
  resetForm()
  try {
    const r: any = await http.get(`/crm/care/follow/plan/${row.id}`)
    Object.assign(form, r.data || {})
    // storeIds 字符串 → 数组
    try {
      const parsed = form.storeIds ? JSON.parse(form.storeIds) : []
      storeIdsArr.value = Array.isArray(parsed) ? parsed.map(String) : []
    } catch { storeIdsArr.value = [] }
  } catch {
    Object.assign(form, row)
  }
  editVisible.value = true
}
function onTypeChange(t: string) {
  if (t === 'BIRTHDAY') form.triggerOffsetDays = 0
  if (t === 'EXPIRE') form.triggerOffsetDays = 7
  if (t === 'POST_TREATMENT') form.triggerOffsetDays = 3
}
function onStoreChange(arr: string[]) {
  form.storeIds = arr && arr.length ? JSON.stringify(arr.map(s => Number(s))) : ''
}
function onOwnerChange(id: any) {
  const u = ownerOptions.value.find((x: any) => x.id === id)
  form.defaultOwnerName = u ? u.name : ''
}
async function save() {
  if (!form.planCode || !form.planName) return ElMessage.warning('编码 / 名称 必填')
  if (!form.subjectTemplate) return ElMessage.warning('主题必填')
  editSaving.value = true
  try {
    const payload = { ...form }
    delete payload.createTime; delete payload.updateTime; delete payload.deleted
    if (form.id) {
      await http.put(`/crm/care/follow/plan/${form.id}`, payload)
      ElMessage.success('已保存')
    } else {
      await http.post('/crm/care/follow/plan', payload)
      ElMessage.success('已创建')
    }
    editVisible.value = false
    loadAll()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally { editSaving.value = false }
}

// ====== 启停 / 触发 / 删除 ======
async function toggleEnabled(row: any, v: boolean) {
  try {
    const url = v
      ? `/crm/care/follow/plan/${row.id}/enable`
      : `/crm/care/follow/plan/${row.id}/disable`
    await http.post(url)
    row.enabled = v ? 1 : 0
    ElMessage.success(v ? '已启用' : '已停用')
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
async function triggerOne(row: any) {
  try {
    await ElMessageBox.confirm(
      `立即触发规则「${row.planName}」？将按当前数据生成回访任务。`,
      '手动触发', { type: 'warning' })
    const r: any = await http.post(`/crm/care/follow/plan/${row.id}/trigger`)
    const n = r.data?.generatedCount ?? 0
    ElMessage.success(`已生成 ${n} 个任务`)
    loadAll()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.message || '触发失败')
  }
}
async function deleteOne(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${row.planName}」？`, '删除', { type: 'warning' })
    await http.delete(`/crm/care/follow/plan/${row.id}`)
    ElMessage.success('已删除')
    loadAll()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

// ====== 预览 ======
const previewVisible = ref(false)
const previewData = ref<any>(null)
const previewLoading = ref(false)

function storeName(id: any) {
  if (id == null) return ''
  const s = storeOptions.value.find((x: any) => String(x.id) === String(id))
  return s ? s.name : ''
}

function closePreviewAndEdit() {
  const planId = previewData.value?.id
  const row = rules.value.find((r: any) => r.id === planId)
  previewVisible.value = false
  if (row) openEdit(row)
}

async function openPreview(row: any) {
  previewVisible.value = true
  previewData.value = null
  previewLoading.value = true
  try {
    const r: any = await http.get(`/crm/care/follow/plan/${row.id}/preview-count`)
    previewData.value = { ...(r.data || {}), id: row.id }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '预览失败')
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

onMounted(() => { loadAll(); loadOptions() })
</script>

<style scoped>
.follow-rule-page { padding: 16px; min-height: 100vh; }
.header-card { margin-bottom: 12px; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.page-title { display: flex; align-items: center; gap: 10px; }
.page-title .title { font-size: 20px; font-weight: 700; color: #1e293b; }
.page-tools { display: flex; gap: 8px; }
.kpi-mini-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.kpi-mini {
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 10px 14px; display: flex; justify-content: space-between; align-items: center;
}
.kpi-mini strong { font-size: 22px; color: #303133; }
.kpi-mini .success { color: #67c23a; }
.search-bar { margin-bottom: 12px; }
.table-card { margin-bottom: 12px; }
.pagination { margin-top: 16px; text-align: right; }
.muted { color: #909399; }
.rule-name { display: flex; align-items: center; gap: 6px; font-weight: 600; color: #1e293b; }
.rule-sub { font-size: 12px; margin-top: 2px; }
.form-tip { color: #909399; font-size: 12px; margin-left: 8px; }
.success { color: #67c23a; }
.preview-alert-body { display: flex; flex-direction: column; gap: 4px; }
.preview-alert-title { font-weight: 600; color: #1e293b; }
.preview-alert-sub { font-size: 12px; }
.preview-alert-sub strong { margin: 0 2px; }
</style>