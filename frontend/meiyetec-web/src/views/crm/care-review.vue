<template>
  <div class="review-page">

    <!-- KPI -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.total || 0 }}</div>
          <div class="stat-label">评价总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-blue">
          <div class="stat-value">{{ stats.todayReviews || 0 }}</div>
          <div class="stat-label">今日新增</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-orange">
          <div class="stat-value">{{ stats.lowRating || 0 }}</div>
          <div class="stat-label">低分评价</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-green">
          <div class="stat-value">{{ stats.replyRate || '0%' }}</div>
          <div class="stat-label">回复率</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + 维度雷达 + 标签云 -->
    <el-row :gutter="12" class="charts-row">
      <el-col :span="12">
        <el-card class="block-card">
          <template #header>
            <span><el-icon><TrendCharts /></el-icon> 近 7 日评价趋势</span>
          </template>
          <div v-if="!stats.trend || !stats.trend.length" class="muted empty-mini">暂无趋势数据</div>
          <div v-else class="trend-bars">
            <div v-for="d in stats.trend" :key="d.date" class="trend-col">
              <div class="trend-bar-wrap">
                <div class="trend-bar" :style="{ height: barHeight(d.count) + 'px' }">
                  <span v-if="d.count > 0" class="trend-val">{{ d.count }}</span>
                </div>
              </div>
              <div class="trend-date">{{ d.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="block-card radar-card">
          <template #header>
            <span><el-icon><PieChart /></el-icon> 维度均分</span>
          </template>
          <div class="radar-wrap">
            <svg :width="180" :height="180" viewBox="0 0 180 180">
              <g transform="translate(90,90)">
                <polygon v-for="r in [1,0.75,0.5,0.25]" :key="'r'+r"
                  :points="radarPoints(r)"
                  fill="none" stroke="#e6e8eb" stroke-width="1" />
                <line v-for="(axis,i) in radarAxes" :key="'a'+i"
                  x1="0" y1="0" :x2="axis.x" :y2="axis.y" stroke="#e6e8eb" stroke-width="1" />
                <polygon
                  :points="radarPoly()"
                  fill="rgba(64,158,255,0.25)" stroke="#409eff" stroke-width="2" />
                <text v-for="(axis,i) in radarAxes" :key="'t'+i"
                  :x="axis.lx" :y="axis.ly" text-anchor="middle" font-size="11" fill="#666">
                  {{ radarLabels[i] }}
                </text>
              </g>
            </svg>
          </div>
          <div class="radar-stats">
            <span v-for="(v,i) in radarValues" :key="i">
              <em>{{ radarLabels[i] }}</em><strong>{{ v.toFixed(2) }}</strong>
            </span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="block-card">
          <template #header>
            <span><el-icon><CollectionTag /></el-icon> 标签 TOP 10</span>
          </template>
          <div v-if="!stats.topTags || !stats.topTags.length" class="muted empty-mini">暂无标签</div>
          <div v-else>
            <div v-for="(t, i) in stats.topTags" :key="t.name" class="tagline">
              <span class="tag-rank" :class="{ 'top1': i === 0, 'top2': i === 1, 'top3': i === 2 }">{{ i + 1 }}</span>
              <span class="tag-name" :class="{ 'tag-strong': t.count >= 5 }">{{ t.name }}</span>
              <el-progress :percentage="tagBarPct(t.count)" :show-text="false" :stroke-width="8" :color="tagColor(i)" />
              <span class="tag-count">{{ t.count }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 7 日好差评 -->
    <el-card class="block-card" v-if="stats.week7GoodBad">
      <div class="week-summary">
        <span class="chip good">
          <el-icon><CircleCheckFilled /></el-icon> 近 7 日好评 <strong>{{ stats.week7GoodBad.good || 0 }}</strong>
        </span>
        <span class="chip bad">
          <el-icon><WarningFilled /></el-icon> 近 7 日差评 <strong>{{ stats.week7GoodBad.bad || 0 }}</strong>
        </span>
        <span class="chip dim">
          服务 <strong>{{ (stats.dimAvg?.service || 0).toFixed(2) }}</strong> /
          技术 <strong>{{ (stats.dimAvg?.skill || 0).toFixed(2) }}</strong> /
          环境 <strong>{{ (stats.dimAvg?.environment || 0).toFixed(2) }}</strong> /
          性价比 <strong>{{ (stats.dimAvg?.price || 0).toFixed(2) }}</strong>
        </span>
      </div>
    </el-card>

    <!-- 工具栏 -->
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="会员 / 手机 / 内容 / 标签 / 单号" style="width: 220px"
        clearable @keyup.enter="resetPage" />
      <el-select v-model="filter.sourceType" placeholder="来源" clearable style="width: 110px" @change="resetPage">
        <el-option label="订单" value="ORDER" />
        <el-option label="员工" value="STAFF" />
        <el-option label="服务" value="SERVICE" />
        <el-option label="门店" value="STORE" />
      </el-select>
      <el-select v-model="filter.overallRating" placeholder="评分" clearable style="width: 110px" @change="resetPage">
        <el-option v-for="n in 5" :key="n" :label="'★'.repeat(n)" :value="n" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 110px" @change="resetPage">
        <el-option label="待审核" :value="1" />
        <el-option label="已发布" :value="2" />
        <el-option label="已隐藏" :value="3" />
        <el-option label="已申诉" :value="4" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始"
        end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" @change="onDateChange" />
      <el-button type="primary" @click="resetPage">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <div style="flex: 1" />
      <el-button type="warning" @click="openCreate">+ 填写评价</el-button>
      <el-button @click="exportCsv" :loading="exporting">导出</el-button>
      <el-button type="success" plain @click="seedDemo">灌入示例</el-button>
      <el-button @click="refresh" :loading="loading">刷新</el-button>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="sourceNo" label="来源单号" width="170" />
        <el-table-column label="会员" width="160">
          <template #default="{ row }">
            <div>{{ row.memberName || '—' }}</div>
            <div style="font-size: 12px; color: #999">{{ row.memberPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ sourceLabel(row.sourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="120">
          <template #default="{ row }">
            <el-rate v-model="row.overallRating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="维度评分" width="200">
          <template #default="{ row }">
            <span v-if="row.serviceRating" class="dim-score">服 {{ row.serviceRating }}</span>
            <span v-if="row.skillRating" class="dim-score">技 {{ row.skillRating }}</span>
            <span v-if="row.environmentRating" class="dim-score">环 {{ row.environmentRating }}</span>
            <span v-if="row.priceRating" class="dim-score">价 {{ row.priceRating }}</span>
            <span v-if="!hasDim(row)" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="tag in (row.tags || '').split(',').filter(Boolean)" :key="tag"
              size="small" type="info" effect="plain"
              style="margin-right: 4px; margin-bottom: 2px">{{ tag }}</el-tag>
            <span v-if="!row.tags" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.content" class="review-content">"{{ row.content }}"</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="回复" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.replyContent" size="small" type="success">已回复</el-tag>
            <el-tag v-else size="small" type="info">未回复</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 2" size="small" type="success">已发布</el-tag>
            <el-tag v-else-if="row.status === 1" size="small" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.status === 3" size="small" type="info">已隐藏</el-tag>
            <el-tag v-else-if="row.status === 4" size="small" type="danger">已申诉</el-tag>
            <el-tag v-else size="small">—</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="150" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link size="small" type="primary" @click="openReply(row)" :disabled="!!row.replyContent">回复</el-button>
            <el-button link size="small" type="warning" :disabled="row.status === 3" @click="hide(row)">隐藏</el-button>
            <el-button link size="small" type="success" :disabled="row.status !== 3" @click="show(row)">恢复</el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 4" @click="appeal(row)">申诉</el-button>
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

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="`评价详情 ${detail?.sourceNo || ''}`" width="720px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="来源单号">{{ detail.sourceNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(detail.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ detail.memberName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ detail.memberPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="综合评分" :span="2">
            <el-rate v-model="detail.overallRating" disabled />
          </el-descriptions-item>
          <el-descriptions-item label="服务评分">{{ detail.serviceRating || '—' }}</el-descriptions-item>
          <el-descriptions-item label="技术评分">{{ detail.skillRating || '—' }}</el-descriptions-item>
          <el-descriptions-item label="环境评分">{{ detail.environmentRating || '—' }}</el-descriptions-item>
          <el-descriptions-item label="性价比评分">{{ detail.priceRating || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <el-tag v-for="tag in (detail.tags || '').split(',').filter(Boolean)" :key="tag"
              size="small" type="info" style="margin-right: 4px">{{ tag }}</el-tag>
            <span v-if="!detail.tags">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="评价内容" :span="2">"{{ detail.content || '—' }}"</el-descriptions-item>
          <el-descriptions-item label="回复内容" :span="2">{{ detail.replyContent || '—' }}</el-descriptions-item>
          <el-descriptions-item label="回复人">{{ detail.replyBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="回复时间">{{ detail.replyAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detail.status === 2" size="small" type="success">已发布</el-tag>
            <el-tag v-else-if="detail.status === 1" size="small" type="warning">待审核</el-tag>
            <el-tag v-else-if="detail.status === 3" size="small" type="info">已隐藏</el-tag>
            <el-tag v-else-if="detail.status === 4" size="small" type="danger">已申诉</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.createTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail && !detail.replyContent" type="primary" @click="openReply(detail)">回复评价</el-button>
        <el-button v-if="detail && detail.overallRating && detail.overallRating <= 2 && detail.status !== 4"
          type="danger" @click="appeal(detail)">申诉升级</el-button>
      </template>
    </el-dialog>

    <!-- 回复 -->
    <el-dialog v-model="replyDialog.visible" title="回复评价" width="500px">
      <el-form label-width="80px">
        <el-form-item label="回复内容">
          <el-input v-model="replyDialog.content" type="textarea" :rows="4" placeholder="请输入回复" />
        </el-form-item>
        <el-form-item label="回复人">
          <el-input v-model="replyDialog.by" placeholder="admin" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="reply" :loading="replyDialog.loading">提交回复</el-button>
      </template>
    </el-dialog>

    <!-- 申诉 -->
    <el-dialog v-model="appealDialog.visible" title="发起申诉" width="500px">
      <el-form label-width="80px">
        <el-form-item label="申诉备注">
          <el-input v-model="appealDialog.reason" type="textarea" :rows="3" placeholder="如：会员评价与实际不符" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealDialog.visible = false">取消</el-button>
        <el-button type="danger" @click="confirmAppeal">确认申诉</el-button>
      </template>
    </el-dialog>

    <!-- 新建 -->
    <el-dialog v-model="dialog.visible" title="填写评价" width="700px" @closed="resetDialog">
      <el-form :model="form" label-width="100px" :rules="createRules" ref="formRef">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="来源" prop="sourceType">
              <el-select v-model="form.sourceType" style="width: 100%">
                <el-option label="订单" value="ORDER" />
                <el-option label="员工" value="STAFF" />
                <el-option label="服务" value="SERVICE" />
                <el-option label="门店" value="STORE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源ID" prop="sourceId">
              <el-input v-model.number="form.sourceId" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="门店ID">
              <el-input v-model.number="form.storeId" type="number" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="会员ID">
              <el-input v-model.number="form.memberId" type="number" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源单号">
              <el-input v-model="form.sourceNo" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="综合评分" prop="overallRating" required>
          <el-rate v-model="form.overallRating" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="服务"><el-rate v-model="form.serviceRating" :max="5" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="技术"><el-rate v-model="form.skillRating" :max="5" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="环境"><el-rate v-model="form.environmentRating" :max="5" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="性价比"><el-rate v-model="form.priceRating" :max="5" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="标签">
          <el-select v-model="tagList" multiple filterable allow-create placeholder="选择/输入标签" style="width: 100%">
            <el-option label="专业" value="专业" />
            <el-option label="细致" value="细致" />
            <el-option label="热情" value="热情" />
            <el-option label="手法好" value="手法好" />
            <el-option label="环境好" value="环境好" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入评价内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const stats = reactive<any>({
  total: 0, todayReviews: 0, lowRating: 0, replyRate: '0%',
  trend: [], dimAvg: {}, topTags: [], week7GoodBad: { good: 0, bad: 0 }
})
const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', sourceType: undefined as string | undefined,
  overallRating: undefined as number | undefined,
  status: undefined as number | undefined,
  dateFrom: undefined as string | undefined,
  dateTo: undefined as string | undefined
})
const dateRange = ref<[string, string] | null>(null)
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)

// ====== 趋势条高度 ======
const TREND_MAX_H = 120
function barHeight(n: number) {
  const ts = stats.trend || []
  const max = Math.max(1, ...ts.map((x: any) => x.count || 0))
  return Math.max(4, Math.round((n / max) * TREND_MAX_H))
}

// ====== 雷达（4 维度）======
const radarLabels = ['服务', '技术', '环境', '性价比']
const radarValues = computed(() => {
  const d = stats.dimAvg || {}
  return [d.service || 0, d.skill || 0, d.environment || 0, d.price || 0]
})
const radarAxes = computed(() => {
  const r = 70
  return radarLabels.map((_, i) => {
    const a = (-Math.PI / 2) + i * (Math.PI * 2 / 4)
    return { x: Math.cos(a) * r, y: Math.sin(a) * r, lx: Math.cos(a) * (r + 14), ly: Math.sin(a) * (r + 14) + 4 }
  })
})
function radarPoints(scale: number) {
  return radarAxes.value.map(a => `${a.x * scale},${a.y * scale}`).join(' ')
}
function radarPoly() {
  return radarValues.value.map((v, i) => {
    const a = radarAxes.value[i]
    const r = (Math.max(0, Math.min(5, v)) / 5)
    return `${(a.x * r).toFixed(2)},${(a.y * r).toFixed(2)}`
  }).join(' ')
}

// ====== 标签条百分比 ======
function tagBarPct(n: number) {
  const ts = stats.topTags || []
  const max = Math.max(1, ...ts.map((x: any) => x.count || 0))
  return Math.round((n / max) * 100)
}
function tagColor(i: number) {
  return ['#67c23a', '#409eff', '#e6a23c', '#f56c6c', '#909399', '#9c27b0', '#00bcd4', '#795548', '#607d8b', '#3f51b5'][i] || '#909399'
}

// ====== 数据加载 ======
async function loadStats() {
  try {
    const r: any = await http.get('/crm/care/review/stats')
    Object.assign(stats, r.data || {})
  } catch { /* 静默 */ }
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { ...filter }
    if (!params.sourceType) delete params.sourceType
    if (!params.overallRating) delete params.overallRating
    if (!params.status) delete params.status
    if (!params.dateFrom) delete params.dateFrom
    if (!params.dateTo) delete params.dateTo
    const r: any = await http.get('/crm/care/review/page', { params })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([loadStats(), loadData()])
}

function resetPage() { filter.pageNum = 1; loadData() }
function resetFilter() {
  Object.assign(filter, {
    pageNum: 1, pageSize: 20, keyword: '', sourceType: undefined,
    overallRating: undefined, status: undefined, dateFrom: undefined, dateTo: undefined
  })
  dateRange.value = null
  loadData()
}
function onDateChange(v: any) {
  filter.dateFrom = v?.[0]
  filter.dateTo = v?.[1]
  resetPage()
}

function sourceLabel(t: string) {
  return ({ ORDER: '订单', STAFF: '员工', SERVICE: '服务', STORE: '门店' } as any)[t] || t || '—'
}
function hasDim(row: any) {
  return row.serviceRating || row.skillRating || row.environmentRating || row.priceRating
}

// ====== 详情 / 回复 / 隐藏 / 申诉 ======
const detailVisible = ref(false)
const detail = ref<any>(null)
async function openDetail(row: any) {
  const r: any = await http.get(`/crm/care/review/${row.id}`)
  detail.value = r.data || row
  detailVisible.value = true
}

const replyDialog = reactive<any>({ visible: false, review: null as any, content: '', by: 'admin', loading: false })
function openReply(row: any) {
  if (detailVisible.value) detailVisible.value = false
  replyDialog.review = row
  replyDialog.content = ''
  replyDialog.by = 'admin'
  replyDialog.visible = true
}
async function reply() {
  if (!replyDialog.content.trim()) return ElMessage.warning('请输入回复内容')
  replyDialog.loading = true
  try {
    await http.post(`/crm/care/review/${replyDialog.review.id}/reply`, {
      content: replyDialog.content, replyBy: replyDialog.by
    })
    ElMessage.success('已回复')
    replyDialog.visible = false
    await Promise.all([loadStats(), loadData()])
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '回复失败')
  } finally {
    replyDialog.loading = false
  }
}

async function hide(row: any) {
  try {
    await http.post(`/crm/care/review/${row.id}/hide`)
    ElMessage.success('已隐藏')
    await loadData()
  } catch { ElMessage.error('操作失败') }
}
async function show(row: any) {
  try {
    await http.post(`/crm/care/review/${row.id}/show`)
    ElMessage.success('已恢复')
    await loadData()
  } catch { ElMessage.error('操作失败') }
}

const appealDialog = reactive<{ visible: boolean; review: any; reason: string }>({ visible: false, review: null, reason: '' })
function appeal(row: any) {
  if (detailVisible.value) detailVisible.value = false
  appealDialog.review = row
  appealDialog.reason = ''
  appealDialog.visible = true
}
async function confirmAppeal() {
  try {
    await http.post(`/crm/care/review/${appealDialog.review.id}/appeal`)
    ElMessage.warning('已发起申诉，并自动生成投诉工单')
    appealDialog.visible = false
    await Promise.all([loadStats(), loadData()])
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '申诉失败')
  }
}

// ====== 新建 ======
const dialog = reactive({ visible: false, saving: false })
const formRef = ref<any>()
const form = reactive({
  sourceType: 'ORDER', sourceId: undefined as number | undefined,
  sourceNo: '', storeId: 1,
  memberId: undefined as number | undefined,
  overallRating: 5,
  serviceRating: 0, skillRating: 0, environmentRating: 0, priceRating: 0,
  content: '', tags: '', operatorName: 'admin'
})
const tagList = ref<string[]>([])
watch(tagList, (v) => { form.tags = v.join(',') }, { deep: true })
const createRules = {
  sourceId: [{ required: true, message: '来源ID必填', trigger: 'blur' }],
  overallRating: [{ required: true, message: '请评分', trigger: 'change' }]
}
function openCreate() { dialog.visible = true }
function resetDialog() {
  Object.assign(form, {
    sourceType: 'ORDER', sourceId: undefined, sourceNo: '', storeId: 1,
    memberId: undefined, overallRating: 5,
    serviceRating: 0, skillRating: 0, environmentRating: 0, priceRating: 0,
    content: '', tags: '', operatorName: 'admin'
  })
  tagList.value = []
  formRef.value?.clearValidate()
}
async function save() {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    dialog.saving = true
    try {
      const headers = { 'Idempotency-Key': newIdempotencyKey() }
      const r: any = await http.post('/crm/care/review', form, { headers })
      if (r.data?.level && r.data.level >= 2) {
        ElMessage.warning(`低分评价已自动触发投诉工单 #${r.data.ticketId}`)
      } else {
        ElMessage.success('已提交')
      }
      dialog.visible = false
      await Promise.all([loadStats(), loadData()])
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '提交失败')
    } finally {
      dialog.saving = false
    }
  })
}

// ====== 灌入示例 ======
async function seedDemo() {
  try {
    await ElMessageBox.confirm('将灌入 12 条示例评价（含低评自动工单）。继续？', '提示', { type: 'warning' })
  } catch { return }
  await http.post('/crm/care/review/demo-seed')
  ElMessage.success('已灌入')
  await refresh()
}

// ====== 导出 ======
async function exportCsv() {
  exporting.value = true
  try {
    const params: any = { ...filter }
    if (!params.sourceType) delete params.sourceType
    if (!params.overallRating) delete params.overallRating
    if (!params.status) delete params.status
    if (!params.dateFrom) delete params.dateFrom
    if (!params.dateTo) delete params.dateTo
    const r: any = await http.get('/crm/care/review/export', { params })
    const blob = new Blob(['\uFEFF' + (r.data?.content || '')], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = r.data?.filename || 'review.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${r.data?.count || 0} 条`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

// 注意：因 template 内用 watch 语法，script setup 需导入 watch
onMounted(async () => { await refresh() })
</script>

<style scoped>
.review-page { padding: 12px; }
.stat-card { text-align: center; padding: 14px 0; }
.stat-value { font-size: 28px; font-weight: 600; color: #303133; }
.stat-label { color: #909399; font-size: 12px; margin-top: 4px; }
.stat-card.stat-blue .stat-value { color: #409eff; }
.stat-card.stat-orange .stat-value { color: #e6a23c; }
.stat-card.stat-green .stat-value { color: #67c23a; }
.stats-row, .charts-row { margin-bottom: 12px; }
.block-card { margin-bottom: 12px; }
.muted { color: #909399; }
.empty-mini { padding: 24px; text-align: center; }
.trend-bars { display: flex; align-items: flex-end; height: 160px; gap: 12px; padding: 0 12px; }
.trend-col { flex: 1; display: flex; flex-direction: column; align-items: center; }
.trend-bar-wrap { height: 130px; display: flex; align-items: flex-end; }
.trend-bar { width: 28px; background: linear-gradient(180deg, #409eff, #79bbff); border-radius: 4px 4px 0 0; position: relative; }
.trend-val { position: absolute; top: -18px; left: 50%; transform: translateX(-50%); font-size: 11px; color: #303133; }
.trend-date { font-size: 12px; color: #909399; margin-top: 6px; }
.radar-card .radar-wrap { display: flex; justify-content: center; }
.radar-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; padding: 0 12px 8px; font-size: 12px; }
.radar-stats em { font-style: normal; color: #909399; margin-right: 4px; }
.radar-stats strong { color: #303133; font-weight: 600; }
.tagline { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.tag-rank { width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: #f0f2f5; color: #909399; font-size: 11px; }
.tag-rank.top1 { background: #f56c6c; color: #fff; }
.tag-rank.top2 { background: #e6a23c; color: #fff; }
.tag-rank.top3 { background: #67c23a; color: #fff; }
.tag-name { width: 60px; font-size: 13px; color: #303133; }
.tag-name.tag-strong { color: #f56c6c; font-weight: 600; }
.tagline .el-progress { flex: 1; }
.tag-count { font-size: 12px; color: #606266; min-width: 28px; text-align: right; }
.week-summary { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; padding: 4px 0; }
.week-summary .chip { display: inline-flex; align-items: center; gap: 4px; padding: 4px 12px; border-radius: 16px; background: #f0f2f5; color: #606266; font-size: 13px; }
.week-summary .chip strong { color: #303133; margin-left: 4px; }
.week-summary .chip.good { background: #f0f9eb; color: #67c23a; }
.week-summary .chip.bad { background: #fef0f0; color: #f56c6c; }
.week-summary .chip.dim { background: #ecf5ff; color: #409eff; }
.search-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.dim-score { display: inline-block; margin-right: 6px; padding: 0 6px; font-size: 12px; background: #f5f7fa; border-radius: 10px; color: #606266; }
.review-content { color: #303133; font-style: italic; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>