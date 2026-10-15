<template>
  <div class="experiment-page">
    <!-- 顶部 KPI -->
    <div class="kpi-row">
      <el-card shadow="never" class="kpi-card">
        <div class="kpi-num">{{ stats.running }}</div>
        <div class="kpi-label">进行中</div>
      </el-card>
      <el-card shadow="never" class="kpi-card">
        <div class="kpi-num">{{ stats.completed }}</div>
        <div class="kpi-label">已完成</div>
      </el-card>
      <el-card shadow="never" class="kpi-card">
        <div class="kpi-num">{{ stats.totalSamples }}</div>
        <div class="kpi-label">总样本量</div>
      </el-card>
      <el-card shadow="never" class="kpi-card">
        <div class="kpi-num success">{{ stats.winning }}</div>
        <div class="kpi-label">变异组胜出</div>
      </el-card>
    </div>

    <el-tabs v-model="tab">
      <!-- ===== 实验列表 ===== -->
      <el-tab-pane label="实验管理" name="list">
        <el-form :inline="true" class="toolbar">
          <el-form-item label="状态">
            <el-select v-model="query.status" style="width:130px" clearable placeholder="全部">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="进行中" value="RUNNING" />
              <el-option label="已暂停" value="PAUSED" />
              <el-option label="已完成" value="COMPLETED" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="名称/KEY" clearable style="width:160px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadExps">查询</el-button>
            <el-button type="success" @click="openCreate">+ 新建实验</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="experiments" stripe>
          <el-table-column prop="expKey" label="实验 KEY" width="160">
            <template #default="{ row }">
              <code style="font-size:12px">{{ row.expKey }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="expName" label="实验名称" min-width="160" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="A组（控制）" width="130" align="center">
            <template #default="{ row }">
              <span class="group-tag">{{ row.groupAName || 'A' }}:</span>
              {{ formatMode(row.groupAMode, row.groupAValue) }}
            </template>
          </el-table-column>
          <el-table-column label="B组" width="130" align="center">
            <template #default="{ row }">
              <span class="group-tag variant">{{ row.groupBName || 'B' }}:</span>
              {{ formatMode(row.groupBMode, row.groupBValue) }}
            </template>
          </el-table-column>
          <el-table-column label="样本/转化" width="130" align="center">
            <template #default="{ row }">
              <span style="color:#409eff">A {{ row.controlSampleSize || 0 }}</span>
              <span style="margin:0 4px">/</span>
              <span style="color:#67c23a">B {{ row.variantSampleSize || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.confidenceLevel" :class="row.confidenceLevel >= 0.95 ? 'text-success' : 'text-muted'">
                {{ (row.confidenceLevel * 100).toFixed(1) }}%
              </span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="结论" width="140" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.conclusion === 'WIN_VARIANT'" type="success" size="small">变异组胜出</el-tag>
              <el-tag v-else-if="row.conclusion === 'WIN_CONTROL'" type="warning" size="small">对照组胜出</el-tag>
              <el-tag v-else-if="row.conclusion === 'NO_SIGNIFICANCE'" type="info" size="small">无显著差异</el-tag>
              <span v-else class="text-muted">待分析</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="viewStats(row)">统计</el-button>
              <el-button v-if="row.status === 'DRAFT' || row.status === 'PAUSED'" size="small" link type="success"
                @click="startExp(row)">启动</el-button>
              <el-button v-if="row.status === 'RUNNING'" size="small" link type="warning"
                @click="pauseExp(row)">暂停</el-button>
              <el-button v-if="row.status === 'RUNNING'" size="small" link type="primary"
                @click="completeExp(row)">结束</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          @current-change="loadExps"
        />
      </el-tab-pane>

      <!-- ===== 统计详情 ===== -->
      <el-tab-pane label="实验统计" name="stats">
        <div v-if="!selectedExp" class="empty-tip">
          请在「实验管理」点击「统计」查看详情
        </div>
        <div v-else>
          <div class="stats-header">
            <h3>{{ selectedExp.expName }}</h3>
            <div style="display:flex; gap:8px">
              <el-button size="small" @click="recomputeExp(selectedExp.id)">重新计算</el-button>
              <el-button size="small" link type="primary" @click="tab = 'list'">返回列表</el-button>
            </div>
          </div>

          <!-- 显著性结论 -->
          <el-card shadow="never" class="conclusion-card">
            <div class="conclusion-label">统计结论</div>
            <div class="conclusion-value" :class="conclusionClass(expStats.conclusion)">
              {{ conclusionLabel(expStats.conclusion) }}
            </div>
            <div class="confidence-row">
              <span>置信度</span>
              <el-progress :percentage="Math.round((expStats.confidenceLevel || 0) * 100)" :color="confidenceColor(expStats.confidenceLevel)" style="width:200px" />
              <span>{{ ((expStats.confidenceLevel || 0) * 100).toFixed(2) }}%</span>
            </div>
          </el-card>

          <!-- 两组对比 -->
          <el-row :gutter="16" style="margin:16px 0">
            <el-col :span="12">
              <el-card shadow="never" class="group-card control">
                <template #header>
                  <span>{{ selectedExp.groupAName || 'A' }}组（控制）</span>
                </template>
                <div class="group-value">{{ formatMode(selectedExp.groupAMode, selectedExp.groupAValue) }}</div>
                <el-divider />
                <div class="metric-row">
                  <span>样本量</span><b>{{ expStats.controlSampleSize || 0 }}</b>
                </div>
                <div class="metric-row">
                  <span>转化数</span><b>{{ expStats.controlConversions || 0 }}</b>
                </div>
                <div class="metric-row">
                  <span>转化率</span><b class="success">{{ expStats.controlRate || '0.00%' }}</b>
                </div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" class="group-card variant">
                <template #header>
                  <span>{{ selectedExp.groupBName || 'B' }}组（变异）</span>
                </template>
                <div class="group-value">{{ formatMode(selectedExp.groupBMode, selectedExp.groupBValue) }}</div>
                <el-divider />
                <div class="metric-row">
                  <span>样本量</span><b>{{ expStats.variantSampleSize || 0 }}</b>
                </div>
                <div class="metric-row">
                  <span>转化数</span><b>{{ expStats.variantConversions || 0 }}</b>
                </div>
                <div class="metric-row">
                  <span>转化率</span><b class="success">{{ expStats.variantRate || '0.00%' }}</b>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 提升幅度 -->
          <el-card shadow="never" class="lift-card">
            <span>相对提升：</span>
            <b :class="liftClass(expStats.liftPercent)">
              {{ formatLift(expStats.liftPercent) }}
            </b>
            <span style="margin-left:16px;color:#909399">显著：{{ expStats.significant ? '✅ 是' : '❌ 否' }}</span>
          </el-card>

          <!-- 按组分布 -->
          <el-table :data="distribution" stripe style="margin-top:16px">
            <el-table-column prop="variant" label="分组" width="80" />
            <el-table-column prop="sample_size" label="样本量" align="center" />
            <el-table-column prop="conversions" label="转化数" align="center" />
            <el-table-column label="转化率" align="center">
              <template #default="{ row }">
                <span v-if="row.sample_size > 0">
                  {{ ((row.conversions / row.sample_size) * 100).toFixed(2) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column label="均价差" align="right">
              <template #default="{ row }">
                <span v-if="row.avg_original_price && row.avg_final_price">
                  原 ¥{{ Number(row.avg_original_price).toFixed(2) }} →
                  实 ¥{{ Number(row.avg_final_price).toFixed(2) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建实验弹窗 -->
    <el-dialog v-model="createVisible" title="新建定价实验" width="680px" :close-on-click-modal="false">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="实验 KEY" prop="expKey">
              <el-input v-model="form.expKey" placeholder="如: summer-discount-v1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实验名称" prop="expName">
              <el-input v-model="form.expName" placeholder="如: 夏季折扣测试" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">A组（控制组，原价）</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="折扣模式">
              <el-select v-model="form.groupAMode" style="width:100%">
                <el-option label="原价（无折扣）" value="ORIGINAL" />
                <el-option label="百分比折扣" value="PERCENT" />
                <el-option label="固定金额减免" value="FIXED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="折扣值">
              <el-input-number v-if="form.groupAMode === 'PERCENT'" v-model="form.groupAValue" :min="0" :max="100" style="width:100%" />
              <el-input-number v-else-if="form.groupAMode === 'FIXED'" v-model="form.groupAValue" :min="0" :precision="2" style="width:100%" />
              <span v-else class="text-muted">原价（无折扣）</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">B组（变异组，测试折扣）</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="折扣模式">
              <el-select v-model="form.groupBMode" style="width:100%">
                <el-option label="百分比折扣" value="PERCENT" />
                <el-option label="固定金额减免" value="FIXED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣值">
              <el-input-number v-model="form.groupBValue" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="控制组占比">
              <el-input-number v-model="form.controlTrafficRatio" :min="0.05" :max="0.95" :step="0.05" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">适用条件</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="最低订单金额">
              <el-input-number v-model="form.minOrderAmount" :min="0" :precision="2" style="width:100%" placeholder="不限制" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最少样本量">
              <el-input-number v-model="form.minSampleSize" :min="10" :max="10000" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建并保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pricingExperimentApi as expApi } from '@/api/pricing-experiment'

const tab = ref('list')
const experiments = ref<any[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 20, status: '', keyword: '' })
const stats = reactive({ running: 0, completed: 0, totalSamples: 0, winning: 0 })
const selectedExp = ref<any>(null)
const statsData = ref<any>({})
const distribution = ref<any[]>([])
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref()

const defaultForm = () => ({
  expKey: '',
  expName: '',
  groupAMode: 'ORIGINAL',
  groupAValue: 0,
  groupBMode: 'PERCENT',
  groupBValue: 10,
  controlTrafficRatio: 0.5,
  minOrderAmount: 0,
  minSampleSize: 100,
  description: ''
})

const form = reactive(defaultForm())

const rules = {
  expKey: [{ required: true, message: '请填写实验 KEY', trigger: 'blur' }],
  expName: [{ required: true, message: '请填写实验名称', trigger: 'blur' }]
}

const expStats = computed(() => statsData.value)

async function loadExps() {
  try {
    const res: any = await expApi.page(query)
    experiments.value = res.data?.records || []
    total.value = res.data?.total || 0
    // 计算 KPI
    const all: any[] = experiments.value
    stats.running = all.filter(e => e.status === 'RUNNING').length
    stats.completed = all.filter(e => e.status === 'COMPLETED').length
    stats.totalSamples = all.reduce((s, e) => s + (e.controlSampleSize || 0) + (e.variantSampleSize || 0), 0)
    stats.winning = all.filter(e => e.conclusion === 'WIN_VARIANT').length
  } catch (e) { console.error(e) }
}

function openCreate() {
  Object.assign(form, defaultForm())
  createVisible.value = true
}

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await expApi.create({ ...form })
    ElMessage.success('实验已创建')
    createVisible.value = false
    loadExps()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '创建失败')
  } finally { saving.value = false }
}

async function viewStats(row: any) {
  selectedExp.value = row
  try {
    const [st, dist]: any = await Promise.all([
      expApi.stats(row.id),
      expApi.distribution(row.id)
    ])
    statsData.value = st.data || {}
    distribution.value = dist.data || []
    tab.value = 'stats'
  } catch (e) { ElMessage.error('加载统计失败') }
}

async function recomputeExp(id: number) {
  try {
    await expApi.recompute(id)
    ElMessage.success('重新计算完成')
    if (selectedExp.value?.id === id) viewStats(selectedExp.value)
  } catch (e) { ElMessage.error('计算失败') }
}

async function startExp(row: any) {
  try { await expApi.start(row.id); ElMessage.success('已启动'); loadExps() }
  catch (e: any) { ElMessage.error(e?.response?.data?.msg || '启动失败') }
}
async function pauseExp(row: any) {
  try { await expApi.pause(row.id); ElMessage.success('已暂停'); loadExps() }
  catch (e: any) { ElMessage.error(e?.response?.data?.msg || '暂停失败') }
}
async function completeExp(row: any) {
  try { await expApi.complete(row.id); ElMessage.success('已结束'); loadExps() }
  catch (e: any) { ElMessage.error(e?.response?.data?.msg || '结束失败') }
}

// ---- 格式化辅助 ----
function statusTag(s: string) {
  return { DRAFT: 'info', RUNNING: 'success', PAUSED: 'warning', COMPLETED: '' }[s] || 'info'
}
function statusLabel(s: string) {
  return { DRAFT: '草稿', RUNNING: '进行中', PAUSED: '已暂停', COMPLETED: '已完成' }[s] || s
}
function formatMode(mode: string, val: number) {
  if (mode === 'ORIGINAL' || !mode) return '原价'
  if (mode === 'PERCENT') return `打 ${(100 - (val || 0)).toFixed(0)} 折`
  if (mode === 'FIXED') return `减 ¥${val}`
  return `${val}`
}
function conclusionLabel(c: string) {
  return { WIN_VARIANT: '🎉 变异组胜出', WIN_CONTROL: '对照组胜出', NO_SIGNIFICANCE: '无显著差异', PENDING: '待分析' }[c || ''] || '待分析'
}
function conclusionClass(c: string) {
  return { WIN_VARIANT: 'text-success', WIN_CONTROL: 'text-warning', NO_SIGNIFICANCE: 'text-muted' }[c || ''] || 'text-muted'
}
function confidenceColor(lv: number) {
  if (lv === undefined || lv === null) return '#909399'
  if (lv >= 0.95) return '#67c23a'
  if (lv >= 0.8) return '#e6a23c'
  return '#909399'
}
function liftClass(lv: string) {
  if (!lv) return 'text-muted'
  const v = parseFloat(lv)
  if (v > 0) return 'text-success'
  if (v < 0) return 'text-danger'
  return ''
}
function formatLift(lv: string) {
  if (!lv) return '—'
  const v = parseFloat(lv)
  if (v > 0) return `+${v.toFixed(2)}%`
  return `${v.toFixed(2)}%`
}

onMounted(loadExps)
</script>

<style scoped>
.experiment-page { padding: 16px; }
.kpi-row { display: flex; gap: 12px; margin-bottom: 16px; }
.kpi-card { flex: 1; text-align: center; background: #f5f7fa; border: none; }
.kpi-num { font-size: 28px; font-weight: 700; color: #303133; line-height: 1.2; }
.kpi-num.success { color: #67c23a; }
.kpi-label { font-size: 13px; color: #909399; margin-top: 4px; }
.toolbar { margin-bottom: 12px; }
.empty-tip { text-align: center; padding: 60px; color: #909399; }
.group-tag { display: inline-block; padding: 1px 6px; border-radius: 4px; background: #ecf5ff; color: #409eff; font-size: 11px; margin-right: 4px; }
.group-tag.variant { background: #f0f9eb; color: #67c23a; }
.text-success { color: #67c23a; font-weight: 600; }
.text-warning { color: #e6a23c; font-weight: 600; }
.text-danger { color: #f56c6c; font-weight: 600; }
.text-muted { color: #909399; }
.stats-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.conclusion-card { text-align: center; padding: 16px; }
.conclusion-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.conclusion-value { font-size: 22px; font-weight: 700; margin-bottom: 12px; }
.confidence-row { display: flex; align-items: center; gap: 12px; font-size: 14px; color: #606266; }
.group-card { text-align: center; }
.group-value { font-size: 20px; font-weight: 600; color: #409eff; margin-bottom: 8px; }
.metric-row { display: flex; justify-content: space-between; padding: 4px 0; color: #606266; font-size: 14px; }
.metric-row .success { color: #67c23a; font-weight: 600; }
.lift-card { padding: 12px 16px; font-size: 16px; }
</style>
