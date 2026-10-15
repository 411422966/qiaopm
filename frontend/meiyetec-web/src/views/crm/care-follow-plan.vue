<template>
  <div class="follow-plan-page">
    <!-- ① 顶部 + KPI -->
    <el-card class="header-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon><Tickets /></el-icon>
          <span class="title">回访计划（总览）</span>
          <el-tag effect="plain" type="info" round>只读</el-tag>
        </div>
        <div class="page-tools">
          <el-button :icon="Refresh" plain @click="loadAll">刷新</el-button>
          <el-button type="primary" :icon="Setting" @click="$router.push('/crm/care/follow-rule')">前往完整规则配置</el-button>
        </div>
      </div>

      <div class="kpi-row">
        <div class="kpi" :class="activeKpi === 'total' ? 'kpi-on' : ''" @click="scrollToList">
          <div class="muted">规则总数</div>
          <div class="num">{{ stats.total || 0 }}</div>
        </div>
        <div class="kpi success" :class="activeKpi === 'enabled' ? 'kpi-on' : ''" @click="filterByStatus(1)">
          <div class="muted">已启用</div>
          <div class="num">{{ stats.enabled || 0 }}</div>
        </div>
        <div class="kpi" :class="activeKpi === 'disabled' ? 'kpi-on' : ''" @click="filterByStatus(0)">
          <div class="muted">已停用</div>
          <div class="num">{{ disabledCount }}</div>
        </div>
        <div class="kpi" :class="activeKpi === 'today' ? 'kpi-on' : ''" @click="activeKpi = 'today'">
          <div class="muted">今日自动生成</div>
          <div class="num">{{ stats.todayGenerated || 0 }}</div>
        </div>
        <div class="kpi primary" :class="activeKpi === 'month' ? 'kpi-on' : ''" @click="activeKpi = 'month'">
          <div class="muted">本月自动生成</div>
          <div class="num">{{ stats.monthGenerated || 0 }}</div>
        </div>
      </div>

      <!-- ② 近 7 天触发生成趋势（纯 CSS 柱图） -->
      <div v-if="weekTrend.length" class="trend-section">
        <div class="trend-label"><el-icon><TrendCharts /></el-icon> 近 7 天触发生成趋势</div>
        <div class="trend-bars">
          <div v-for="day in weekTrend" :key="day.date" class="trend-col">
            <div class="trend-bar-wrap">
              <div class="trend-bar" :style="{ height: barHeight(day.count) + 'px' }">
                <span v-if="day.count > 0" class="trend-val">{{ day.count }}</span>
              </div>
            </div>
            <div class="trend-date">{{ day.label }}</div>
          </div>
        </div>
      </div>
      <div v-else class="trend-placeholder">
        <span class="muted">暂无近 7 天触发数据</span>
      </div>
    </el-card>

    <!-- ③ 类型分布 -->
    <el-card class="block-card">
      <template #header>
        <span><el-icon><PieChart /></el-icon> 类型分布</span>
      </template>
      <div v-if="!distribution.length" class="muted empty-mini">暂无数据</div>
      <div v-else class="dist-grid">
        <div v-for="d in distribution" :key="d.type" class="dist-item"
          :class="{ 'dist-clickable': d.total > 0 }"
          @click="d.total > 0 ? filterByType(d.type) : null">
          <div class="dist-head">
            <el-tag size="small" :type="typeTag(d.type)" effect="plain">{{ typeLabel(d.type) }}</el-tag>
            <strong>{{ d.total }}</strong>
          </div>
          <div class="dist-bar">
            <div class="bar" :style="{ width: barWidth(d.enabled, d.total), background: barColor(d.type) }" />
          </div>
          <div class="dist-foot">
            <span class="text-enable">启用 {{ d.enabled }}</span>
            <span class="text-sep">/</span>
            <span class="text-disable">停用 {{ Math.max(0, d.total - d.enabled) }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ④ 计划列表 -->
    <el-card class="block-card">
      <template #header>
        <div class="card-head-row">
          <div class="card-head-left">
            <span><el-icon><List /></el-icon> 计划列表</span>
            <el-tag v-if="activeFilter.label" size="small" closable type="info" effect="plain"
              @close="clearFilters" style="margin-left: 8px">
              {{ activeFilter.label }}
            </el-tag>
          </div>
          <div class="head-tools">
            <el-select v-model="filterType" placeholder="全部类型" clearable size="small" style="width: 120px">
              <el-option label="疗程后" value="POST_TREATMENT" />
              <el-option label="生日" value="BIRTHDAY" />
              <el-option label="到期" value="EXPIRE" />
              <el-option label="自定义" value="CUSTOM" />
            </el-select>
            <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 100px">
              <el-option label="已启用" :value="1" />
              <el-option label="已停用" :value="0" />
            </el-select>
            <el-input v-model="keyword" placeholder="按编码/名称过滤" clearable size="small" style="width: 200px" />
            <el-button type="primary" size="small" @click="openPlan">+ 新建计划</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <!-- 空状态：引导 -->
        <div v-if="!pageData.length && !loading" class="empty-state">
          <div class="empty-icon">
            <svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="8" y="12" width="48" height="40" rx="4" stroke="#dcdfe6" stroke-width="2" fill="#f5f7fa"/>
              <line x1="8" y1="24" x2="56" y2="24" stroke="#dcdfe6" stroke-width="2"/>
              <rect x="16" y="30" width="20" height="4" rx="2" fill="#dcdfe6"/>
              <rect x="16" y="38" width="14" height="4" rx="2" fill="#dcdfe6"/>
              <circle cx="48" cy="48" r="12" fill="#409eff" opacity="0.12"/>
              <line x1="48" y1="43" x2="48" y2="53" stroke="#409eff" stroke-width="2" stroke-linecap="round"/>
              <line x1="43" y1="48" x2="53" y2="48" stroke="#409eff" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="empty-title">暂无回访计划规则</div>
          <div class="empty-desc">创建第一条规则，自动生成客户回访任务，提升服务质量</div>
          <div class="empty-actions">
            <el-button type="primary" @click="$router.push('/crm/care/follow-rule')">立即配置规则</el-button>
            <el-button plain @click="openQuickDemo">插入演示数据</el-button>
          </div>
        </div>

        <el-table v-else :data="pageData" border stripe size="small">
          <el-table-column prop="planCode" label="编码" width="160" />
          <el-table-column label="名称" min-width="200">
            <template #default="{ row }">
              <div class="plan-name">{{ row.planName }}</div>
              <div class="muted plan-sub">{{ row.subjectTemplate || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTag(row.planType)" effect="plain">
                {{ typeLabel(row.planType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="触发" width="120">
            <template #default="{ row }">{{ triggerDesc(row) }}</template>
          </el-table-column>
          <el-table-column label="处理人" width="110">
            <template #default="{ row }">
              <span v-if="row.defaultOwnerName">{{ row.defaultOwnerName }}</span>
              <span v-else class="muted">未指定</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近触发" width="160">
            <template #default="{ row }">
              <div v-if="row.lastRunAt" class="muted">{{ row.lastRunAt }}</div>
              <div v-if="row.lastRunCount != null" class="success">生成 {{ row.lastRunCount }} 条</div>
              <div v-else class="muted">从未触发</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="warning" @click="triggerOne(row)">触发</el-button>
              <el-button link size="small" type="primary" @click="openPlan(row)">编辑</el-button>
              <el-button link size="small" :type="row.enabled === 1 ? 'info' : 'success'" @click="toggleEnabled(row)">
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button link size="small" type="danger" @click="deletePlan(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="pageData.length" class="table-pagination">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="pageSize"
            :total="filteredPlans.length"
            layout="total, prev, pager, next"
            size="small"
            background />
          <span class="pagination-info muted">共 {{ filteredPlans.length }} 条</span>
        </div>
      </div>
    </el-card>

    <!-- ⑤ 触发结果展示 -->
    <el-card v-if="triggerResult" class="block-card">
      <template #header>
        <span><el-icon><CircleCheck /></el-icon> 最近触发结果</span>
        <el-button link size="small" @click="triggerResult = null" style="float:right">关闭</el-button>
      </template>
      <el-alert
        :title="`本次触发生成 ${triggerResult.generatedCount} 个回访任务`"
        :type="triggerResult.generatedCount > 0 ? 'success' : 'info'" show-icon />
      <div v-if="triggerResult.samples?.length" class="samples">
        <div class="muted" style="margin-bottom: 6px">样本（最多 5 条）：</div>
        <el-tag v-for="s in triggerResult.samples" :key="s.id"
          class="sample-tag" size="small" effect="plain">
          {{ s.taskNo }} · {{ s.subject }}
        </el-tag>
      </div>
    </el-card>
    <!-- 创建 / 编辑计划弹窗 -->
    <el-dialog v-model="planDialog.visible" :title="planDialog.isEdit ? '编辑回访计划' : '新建回访计划'" width="680px" @closed="resetPlanForm">
      <el-form :model="planForm" label-width="110px" :rules="planRules" ref="planFormRef">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划编码" prop="planCode">
              <el-input v-model="planForm.planCode" placeholder="如 PLN001" :disabled="planDialog.isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划名称" prop="planName">
              <el-input v-model="planForm.planName" placeholder="如 疗程后 3 天回访" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划类型" prop="planType">
              <el-select v-model="planForm.planType" style="width: 100%" :disabled="planDialog.isEdit">
                <el-option label="疗程后回访" value="POST_TREATMENT" />
                <el-option label="生日关怀" value="BIRTHDAY" />
                <el-option label="套餐到期提醒" value="EXPIRE" />
                <el-option label="自定义" value="CUSTOM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="偏移天数">
              <el-input-number v-model="planForm.triggerOffsetDays" :min="-30" :max="30" style="width: 100%" />
              <div class="form-tip">疗程后/到期：负数提前天数；生日：0 当天</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="默认处理人">
              <el-input v-model="planForm.defaultOwnerName" placeholder="员工姓名（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-radio-group v-model="planForm.priority">
                <el-radio :value="1">低</el-radio>
                <el-radio :value="2">中</el-radio>
                <el-radio :value="3">高</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="SLA 时长（分钟）">
              <el-input-number v-model="planForm.slaMinutes" :min="30" :step="60" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最低消费金额">
              <el-input-number v-model="planForm.minAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务主题模板" prop="subjectTemplate">
          <el-input v-model="planForm.subjectTemplate" placeholder="如 疗程回访：术后恢复情况" />
        </el-form-item>
        <el-form-item label="内容模板">
          <el-input v-model="planForm.contentTemplate" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="话术模板">
          <el-input v-model="planForm.scriptTemplate" type="textarea" :rows="2" placeholder="1. 询问恢复情况 2. 记录反馈" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="planForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="savePlan" :loading="planDialog.saving">保存</el-button>
        <el-button v-if="!planDialog.isEdit" type="warning" plain @click="savePlanAndTrigger" :loading="planDialog.saving">保存并触发</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh, Setting, Tickets, PieChart, List, CircleCheck, TrendCharts
} from '@element-plus/icons-vue'

const loading = ref(false)
const plans = ref<any[]>([])
const keyword = ref('')
const filterType = ref('')
const filterStatus = ref<number | ''>('')
const activeKpi = ref('')
const triggerResult = ref<any>(null)
const stats = reactive({ total: 0, enabled: 0, todayGenerated: 0, monthGenerated: 0 })
const weekTrend = ref<{ date: string; count: number; label: string }[]>([])

// 创建/编辑计划弹窗
const planDialog = reactive({ visible: false, isEdit: false, saving: false, editingId: null as number | null })
const planFormRef = ref<any>()
const planForm = reactive({
  planCode: '', planName: '', planType: 'POST_TREATMENT',
  triggerOffsetDays: 3, priority: 1, slaMinutes: 1440, minAmount: 0,
  subjectTemplate: '', contentTemplate: '', scriptTemplate: '',
  defaultOwnerName: '', enabled: 1
})
const planRules = {
  planCode: [{ required: true, message: '编码必填', trigger: 'blur' }],
  planName: [{ required: true, message: '名称必填', trigger: 'blur' }],
  planType: [{ required: true, message: '类型必填', trigger: 'change' }],
  subjectTemplate: [{ required: true, message: '主题模板必填', trigger: 'blur' }]
}

function openPlan(row?: any) {
  if (row) {
    planDialog.isEdit = true
    planDialog.editingId = row.id
    Object.assign(planForm, {
      planCode: row.planCode, planName: row.planName, planType: row.planType,
      triggerOffsetDays: row.triggerOffsetDays || 3, priority: row.priority || 1,
      slaMinutes: row.slaMinutes || 1440, minAmount: row.minAmount || 0,
      subjectTemplate: row.subjectTemplate || '', contentTemplate: row.contentTemplate || '',
      scriptTemplate: row.scriptTemplate || '', defaultOwnerName: row.defaultOwnerName || '',
      enabled: row.enabled ?? 1
    })
  } else {
    planDialog.isEdit = false
    planDialog.editingId = null
    Object.assign(planForm, {
      planCode: '', planName: '', planType: 'POST_TREATMENT',
      triggerOffsetDays: 3, priority: 1, slaMinutes: 1440, minAmount: 0,
      subjectTemplate: '', contentTemplate: '', scriptTemplate: '',
      defaultOwnerName: '', enabled: 1
    })
  }
  planDialog.visible = true
}
function resetPlanForm() {
  planFormRef.value?.clearValidate()
}
async function savePlan() {
  planFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    planDialog.saving = true
    try {
      if (planDialog.isEdit) {
        await http.put(`/crm/care/follow/plan/${planDialog.editingId}`, planForm)
        ElMessage.success('已保存')
      } else {
        await http.post('/crm/care/follow/plan', planForm)
        ElMessage.success('已创建')
      }
      planDialog.visible = false
      await Promise.all([loadData(), loadStats()])
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
      planDialog.saving = false
    }
  })
}
async function savePlanAndTrigger() {
  await savePlan()
  // 触发最新创建的计划
}

async function toggleEnabled(row: any) {
  const action = row.enabled === 1 ? 'disable' : 'enable'
  try {
    await http.post(`/crm/care/follow/plan/${row.id}/${action}`)
    ElMessage.success(row.enabled === 1 ? '已停用' : '已启用')
    await loadData()
  } catch { ElMessage.error('操作失败') }
}
async function deletePlan(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除计划「${row.planName}」？`, '删除确认', { type: 'warning' })
    await http.delete(`/crm/care/follow/plan/${row.id}`)
    ElMessage.success('已删除')
    await Promise.all([loadData(), loadStats()])
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

// 分页
const pageNum = ref(1)
const pageSize = ref(20)

const disabledCount = computed(() => Math.max(0, (stats.total || 0) - (stats.enabled || 0)))

const activeFilter = computed(() => {
  if (filterStatus.value === 1) return { label: '已启用', clear: () => filterStatus.value = '' }
  if (filterStatus.value === 0) return { label: '已停用', clear: () => filterStatus.value = '' }
  const typeNames: Record<string, string> = { POST_TREATMENT: '疗程后', BIRTHDAY: '生日', EXPIRE: '到期', CUSTOM: '自定义' }
  if (filterType.value) return { label: typeNames[filterType.value] || filterType.value, clear: () => filterType.value = '' }
  return { label: '', clear: () => {} }
})

function clearFilters() {
  keyword.value = ''
  filterType.value = ''
  filterStatus.value = ''
  activeKpi.value = ''
}

function filterByType(type: string) {
  clearFilters()
  filterType.value = type
}

function filterByStatus(status: number) {
  clearFilters()
  filterStatus.value = status
  activeKpi.value = status === 1 ? 'enabled' : 'disabled'
}

function scrollToList() {
  activeKpi.value = 'total'
}

async function loadData() {
  loading.value = true
  try {
    const r: any = await http.get('/crm/care/follow/plan/page', {
      params: { pageNum: 1, pageSize: 500 }
    })
    plans.value = r.data?.records || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
    plans.value = []
  } finally { loading.value = false }
}

async function loadStats() {
  try {
    const r: any = await http.get('/crm/care/follow/plan/stats')
    Object.assign(stats, r.data || {})
  } catch {}
}

async function loadWeekTrend() {
  try {
    const r: any = await http.get('/crm/care/follow/plan/week-trend')
    weekTrend.value = r.data || []
  } catch {
    weekTrend.value = []
  }
}

async function loadAll() {
  await Promise.all([loadData(), loadStats(), loadWeekTrend()])
}

const filteredPlans = computed(() => {
  const kw = (keyword.value || '').trim().toLowerCase()
  let list = plans.value
  if (kw) list = list.filter((p: any) =>
    (p.planCode || '').toLowerCase().includes(kw) ||
    (p.planName || '').toLowerCase().includes(kw))
  if (filterType.value) list = list.filter((p: any) => p.planType === filterType.value)
  if (filterStatus.value !== '') list = list.filter((p: any) => p.enabled === filterStatus.value)
  return list
})

const pageData = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredPlans.value.slice(start, start + pageSize.value)
})

const distribution = computed(() => {
  const types = ['POST_TREATMENT', 'BIRTHDAY', 'EXPIRE']
  return types.map(t => {
    const items = plans.value.filter((p: any) => p.planType === t)
    return {
      type: t,
      total: items.length,
      enabled: items.filter((p: any) => p.enabled === 1).length
    }
  }).filter(d => d.total > 0)
})

// 趋势图高度计算：最大值为 max(5, 最高count)，最低15px，最高80px
const maxTrendCount = computed(() => Math.max(5, ...weekTrend.value.map(d => d.count)))
const barHeight = (count: number) => {
  if (!count) return 4
  return Math.max(4, Math.round((count / maxTrendCount.value) * 80))
}

function typeLabel(t: string) {
  return ({ POST_TREATMENT: '疗程后', BIRTHDAY: '生日', EXPIRE: '到期', CUSTOM: '自定义' } as any)[t] || t || '—'
}
function typeTag(t: string) {
  return ({ POST_TREATMENT: 'primary', BIRTHDAY: 'warning', EXPIRE: 'danger', CUSTOM: 'info' } as any)[t] || 'info'
}
function triggerDesc(row: any) {
  if (row.planType === 'POST_TREATMENT') return `疗程后 ${row.triggerOffsetDays} 天`
  if (row.planType === 'BIRTHDAY') return '会员生日当天'
  if (row.planType === 'EXPIRE') return `到期前 ${Math.abs(row.triggerOffsetDays || 0)} 天`
  return '—'
}
function barWidth(enabled: number, total: number) {
  if (!total) return '0%'
  return `${Math.round((enabled / total) * 100)}%`
}
function barColor(t: string) {
  return ({ POST_TREATMENT: '#409eff', BIRTHDAY: '#e6a23c', EXPIRE: '#f56c6c' } as any)[t] || '#909399'
}

async function triggerOne(row: any) {
  try {
    await ElMessageBox.confirm(
      `立即触发规则「${row.planName}」？将按当前数据生成回访任务。`,
      '手动触发', { type: 'warning' })
    const r: any = await http.post(`/crm/care/follow/plan/${row.id}/trigger`)
    triggerResult.value = r.data
    ElMessage.success(`已生成 ${r.data?.generatedCount ?? 0} 个任务`)
    loadStats()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.message || '触发失败（生产环境通常需要 @Scheduled 调用）')
  }
}

async function openQuickDemo() {
  try {
    await ElMessageBox.confirm(
      '将在数据库插入 3 条演示规则。是否继续？',
      '插入演示数据', { type: 'info', confirmButtonText: '插入', cancelButtonText: '取消' })
    await http.post('/crm/care/follow/plan/demo-seed')
    ElMessage.success('演示数据已插入，请刷新页面查看')
    loadAll()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error('插入失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(() => { loadAll() })
</script>

<style scoped>
.follow-plan-page { padding: 16px; min-height: 100vh; }
.header-card { margin-bottom: 12px; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.page-title { display: flex; align-items: center; gap: 10px; }
.page-title .title { font-size: 20px; font-weight: 700; color: #1e293b; }
.page-tools { display: flex; gap: 8px; }

/* KPI */
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.kpi {
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 10px 14px; cursor: pointer; transition: all 0.2s;
}
.kpi:hover { border-color: #409eff; background: #f0f7ff; }
.kpi-on { border-color: #409eff; background: #ecf5ff; }
.kpi .muted { font-size: 12px; }
.kpi .num { font-size: 22px; font-weight: 700; margin-top: 4px; color: #303133; }
.kpi.success .num { color: #67c23a; }
.kpi.primary .num { color: #409eff; }

/* 趋势图 */
.trend-section { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #ebeef5; }
.trend-label { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #606266; margin-bottom: 10px; }
.trend-bars { display: flex; gap: 8px; align-items: flex-end; }
.trend-col { display: flex; flex-direction: column; align-items: center; flex: 1; }
.trend-bar-wrap { height: 80px; display: flex; align-items: flex-end; width: 100%; }
.trend-bar {
  width: 100%; background: linear-gradient(180deg, #409eff 0%, #66b1ff 100%);
  border-radius: 4px 4px 0 0; min-height: 4px; transition: height 0.4s ease;
  display: flex; align-items: flex-start; justify-content: center; padding-top: 2px;
}
.trend-val { font-size: 11px; font-weight: 600; color: #fff; }
.trend-date { font-size: 11px; color: #909399; margin-top: 4px; }
.trend-placeholder { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #ebeef5; font-size: 13px; }

/* 分布图 */
.block-card { margin-bottom: 12px; }
.card-head-row { display: flex; justify-content: space-between; align-items: center; }
.card-head-left { display: flex; align-items: center; }
.head-tools { display: flex; gap: 8px; align-items: center; }
.muted { color: #909399; }
.empty-mini { padding: 16px 0; text-align: center; }
.dist-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.dist-item { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; }
.dist-clickable { cursor: pointer; transition: all 0.2s; }
.dist-clickable:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,0.12); }
.dist-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.dist-bar { background: #f0f2f5; border-radius: 999px; height: 8px; overflow: hidden; }
.dist-bar .bar { height: 100%; transition: width 0.3s; }
.dist-foot { font-size: 12px; margin-top: 6px; }
.text-enable { color: #67c23a; }
.text-sep { color: #dcdfe6; margin: 0 2px; }
.text-disable { color: #909399; }

/* 空状态 */
.empty-state {
  display: flex; flex-direction: column; align-items: center;
  padding: 40px 20px; text-align: center;
}
.empty-icon { margin-bottom: 16px; }
.empty-title { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: #909399; margin-bottom: 20px; max-width: 360px; }
.empty-actions { display: flex; gap: 10px; }

/* 表格 */
.plan-name { font-weight: 600; color: #1e293b; }
.plan-sub { font-size: 12px; margin-top: 2px; }
.success { color: #67c23a; }
.samples { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 6px; }
.sample-tag { margin-right: 0; }
.hint { font-size: 12px; margin-top: 12px; margin-bottom: 0; color: #909399; }

/* 分页 */
.table-pagination { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.pagination-info { font-size: 13px; }

/* 创建/编辑弹窗 */
.form-tip { font-size: 11px; color: #909399; margin-top: 2px; }

/* 工具栏 */
.head-tools { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
