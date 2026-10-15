<template>
  <div class="price-sensitivity-page">
    <!-- 顶部 KPI -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <div class="kpi-label">整体价格弹性</div>
          <div class="kpi-value">
            {{ overview.elasticityCoefficient ?? '-' }}
          </div>
          <div class="kpi-hint">{{ overview.elasticityLabel || '—' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <div class="kpi-label">平均折扣提升</div>
          <div class="kpi-value">{{ overview.avgLiftPercent || '0.00%' }}</div>
          <div class="kpi-hint">基于历史 A/B 实验</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-cyan">
          <div class="kpi-label">高敏感会员数</div>
          <div class="kpi-value">{{ segments.highCount || 0 }}</div>
          <div class="kpi-hint">占 {{ overview.segments?.highSensitivityRate || '0%' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-orange">
          <div class="kpi-label">进行中实验</div>
          <div class="kpi-value">{{ overview.experimentTotal || 0 }}</div>
          <div class="kpi-hint">变异组胜出 {{ overview.variantWin || 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="tab">
      <!-- ========== Tab 1: 总览 ========== -->
      <el-tab-pane label="总览" name="overview">
        <el-row :gutter="12">
          <el-col :span="14">
            <el-card>
              <template #header>
                <div class="card-head">
                  <span>会员敏感度分群</span>
                  <span class="muted">总样本 {{ overview.segments?.totalSampledMembers || 0 }} 人</span>
                </div>
              </template>
              <div ref="segmentEl" class="chart-box"></div>
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card>
              <template #header>
                <div class="card-head">
                  <span>A/B 实验结论分布</span>
                </div>
              </template>
              <div ref="conclusionEl" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-card style="margin-top:12px">
          <template #header>
            <div class="card-head">
              <span>关键洞察（运营建议）</span>
              <el-button text type="primary" @click="loadAll" :loading="loading">刷新</el-button>
            </div>
          </template>
          <el-empty v-if="!overview.insights?.length" description="暂无洞察" />
          <ul v-else class="insight-list">
            <li v-for="(s, i) in overview.insights" :key="i">
              <el-icon class="insight-icon"><Promotion /></el-icon>
              <span>{{ s }}</span>
            </li>
          </ul>
        </el-card>
      </el-tab-pane>

      <!-- ========== Tab 2: 项目价格-转化曲线 ========== -->
      <el-tab-pane label="项目价格曲线" name="curve">
        <el-card class="filter-card">
          <el-input v-model.number="curveProjectId" placeholder="项目 ID（service_id）" style="width:240px" type="number" />
          <el-button type="primary" @click="loadCurve" :loading="curveLoading">查询曲线</el-button>
          <el-button @click="loadAll">刷新总览</el-button>
          <span class="muted" style="margin-left:12px" v-if="curveData">
            数据源：{{ curveData.source === 'PRICING_EXPERIMENT' ? '定价 A/B 实验' : 'POS 历史订单' }}
            · 弹性系数：{{ curveData.priceElasticity ?? 0 }}
          </span>
        </el-card>

        <el-card style="margin-top:12px">
          <template #header>
            <div class="card-head">
              <span>折扣% — 转化率 曲线</span>
              <span class="muted">横轴越大代表折扣越深</span>
            </div>
          </template>
          <div ref="curveEl" class="chart-box"></div>
          <el-table :data="curveData?.points || []" size="small" border style="margin-top:12px">
            <el-table-column prop="variant" label="分组" width="80" />
            <el-table-column label="是否控制组" width="100">
              <template #default="{ row }">
                <el-tag :type="row.isControl ? 'info' : 'primary'" size="small">
                  {{ row.isControl ? '控制组' : '变异组' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="discountPercent" label="平均折扣%" width="110" />
            <el-table-column prop="avgDiscountAmount" label="折扣金额" width="100" />
            <el-table-column prop="originalPrice" label="原价" width="90" />
            <el-table-column prop="finalPrice" label="成交价" width="90" />
            <el-table-column prop="sampleSize" label="样本数" width="90" />
            <el-table-column prop="conversions" label="转化数" width="90" />
            <el-table-column prop="conversionRatePct" label="转化率" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== Tab 3: 会员敏感度分群 ========== -->
      <el-tab-pane label="会员分群" name="segments">
        <el-row :gutter="12">
          <el-col :span="6" v-for="key in (['HIGH','MEDIUM','LOW'] as const)" :key="key">
            <el-card shadow="hover" class="segment-card" :style="{ borderColor: META[key].color }">
              <div class="segment-label" :style="{ background: META[key].bg, color: META[key].color }">
                {{ META[key].label }}
              </div>
              <div class="segment-value" :style="{ color: META[key].color }">
                {{ segments[`${key.toLowerCase()}Count`] || 0 }}
              </div>
              <div class="segment-desc">{{ META[key].description }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-card style="margin-top:12px">
          <template #header>
            <div class="card-head">
              <span>会员样本明细（取前 50）</span>
              <span class="muted">仅展示样本数 ≥ 30 的会员</span>
            </div>
          </template>
          <el-table :data="segments.sampleList || []" size="small" border stripe>
            <el-table-column prop="memberId" label="会员 ID" width="100" />
            <el-table-column label="敏感度">
              <template #default="{ row }">
                <el-tag :style="{ background: META[row.segment].bg, color: META[row.segment].color, borderColor: META[row.segment].color }">
                  {{ META[row.segment].label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="samples" label="样本数" width="100" />
            <el-table-column prop="conversions" label="转化数" width="100" />
            <el-table-column label="转化率" width="120">
              <template #default="{ row }">
                {{ row.samples > 0 ? ((row.conversions / row.samples) * 100).toFixed(1) + '%' : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="avgDiscountAmount" label="平均折扣金额" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== Tab 4: 项目 × 价格档位矩阵 ========== -->
      <el-tab-pane label="价格档位矩阵" name="matrix">
        <el-card>
          <template #header>
            <div class="card-head">
              <span>项目 × 价格档位转化率（Top {{ matrixRows.length }}）</span>
              <el-button text type="primary" @click="loadMatrix" :loading="matrixLoading">刷新</el-button>
            </div>
          </template>
          <el-table :data="matrixRows" size="small" border stripe>
            <el-table-column prop="projectId" label="项目 ID" width="100" />
            <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="价格档" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="tierTag(row.priceTier)">
                  {{ PRICE_TIER_LABEL[row.priceTier] || row.priceTier }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="avgPrice" label="均价" width="100">
              <template #default="{ row }">¥{{ Number(row.avgPrice || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="avgDiscountAmount" label="平均折扣" width="120">
              <template #default="{ row }">¥{{ Number(row.avgDiscountAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="sampleSize" label="样本数" width="90" />
            <el-table-column prop="conversions" label="转化数" width="90" />
            <el-table-column label="转化率" min-width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round(Number(row.conversionRate || 0) * 100)"
                  :stroke-width="14"
                  :status="row.conversionRate >= 0.5 ? 'success' : row.conversionRate >= 0.2 ? '' : 'warning'"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  priceSensitivityApi,
  SENSITIVITY_SEGMENT_META as META,
  PRICE_TIER_LABEL
} from '@/api/price-sensitivity'

echarts.use([BarChart, PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const tab = ref('overview')
const loading = ref(false)

const overview = ref<any>({})
const segments = ref<any>({})
const curveProjectId = ref<number | null>(null)
const curveLoading = ref(false)
const curveData = ref<any>(null)
const matrixRows = ref<any[]>([])
const matrixLoading = ref(false)

// charts
const segmentEl = ref<HTMLDivElement | null>(null)
const conclusionEl = ref<HTMLDivElement | null>(null)
const curveEl = ref<HTMLDivElement | null>(null)
let segmentChart: echarts.ECharts | null = null
let conclusionChart: echarts.ECharts | null = null
let curveChart: echarts.ECharts | null = null

function tierTag(tier: string) {
  if (tier === 'HIGH') return 'danger'
  if (tier === 'MID') return 'warning'
  if (tier === 'LOW') return 'success'
  return 'info'
}

async function loadOverview() {
  const { data } = await priceSensitivityApi.overview()
  overview.value = data || {}
  segments.value = data?.segments || {}
  renderSegmentChart()
  renderConclusionChart()
}

async function loadCurve() {
  if (!curveProjectId.value) {
    ElMessage.warning('请输入项目 ID')
    return
  }
  curveLoading.value = true
  try {
    const { data } = await priceSensitivityApi.curve(curveProjectId.value)
    curveData.value = data
    await nextTick()
    renderCurveChart()
  } finally {
    curveLoading.value = false
  }
}

async function loadMatrix() {
  matrixLoading.value = true
  try {
    const { data } = await priceSensitivityApi.matrix(50)
    matrixRows.value = data?.rows || []
  } finally {
    matrixLoading.value = false
  }
}

function renderSegmentChart() {
  if (!segmentEl.value) return
  if (!segmentChart) segmentChart = echarts.init(segmentEl.value)
  segmentChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12 },
    color: [META.HIGH.color, META.MEDIUM.color, META.LOW.color],
    series: [
      {
        name: '敏感度分群',
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['50%', '45%'],
        label: {
          show: true,
          formatter: (p: any) => `${p.name}\n${p.value} 人`
        },
        data: [
          { name: META.HIGH.label, value: segments.value.highCount || 0 },
          { name: META.MEDIUM.label, value: segments.value.mediumCount || 0 },
          { name: META.LOW.label, value: segments.value.lowCount || 0 }
        ]
      }
    ]
  })
}

function renderConclusionChart() {
  if (!conclusionEl.value) return
  if (!conclusionChart) conclusionChart = echarts.init(conclusionEl.value)
  conclusionChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 70, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'value', name: '实验数' },
    yAxis: {
      type: 'category',
      data: ['变异组胜出', '控制组胜出', '无显著差异', '样本不足'],
      axisLabel: { fontSize: 12 }
    },
    color: ['#67c23a', '#909399', '#e6a23c', '#f56c6c'],
    series: [
      {
        type: 'bar',
        data: [
          overview.value.variantWin || 0,
          overview.value.controlWin || 0,
          overview.value.noSignificance || 0,
          overview.value.pending || 0
        ],
        barWidth: 22,
        label: { show: true, position: 'right', color: '#303133' }
      }
    ]
  })
}

function renderCurveChart() {
  if (!curveEl.value) return
  if (!curveChart) curveChart = echarts.init(curveEl.value)
  const points = (curveData.value?.points || []) as any[]
  const xs = points.map((p: any) => Number(p.discountPercent || 0))
  const rates = points.map((p: any) => Number((Number(p.conversionRate || 0) * 100).toFixed(2)))
  curveChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        const pt = points[p.dataIndex]
        if (!pt) return ''
        return `<b>分组 ${pt.variant}</b><br/>折扣%：${p.axisValue}<br/>转化率：${p.value}%<br/>样本：${pt.sampleSize}<br/>成交价：¥${Number(pt.finalPrice || 0).toFixed(2)}`
      }
    },
    legend: { top: 0, data: ['转化率%'] },
    grid: { left: 60, right: 30, top: 36, bottom: 40 },
    xAxis: {
      type: 'category',
      name: '折扣%',
      data: xs,
      axisLabel: { formatter: (v: any) => `${v}%` }
    },
    yAxis: {
      type: 'value',
      name: '转化率%',
      axisLabel: { formatter: (v: any) => `${v}%` }
    },
    series: [
      {
        name: '转化率%',
        type: 'line',
        smooth: true,
        data: rates,
        lineStyle: { width: 3, color: '#409eff' },
        itemStyle: { color: '#409eff' },
        areaStyle: { color: 'rgba(64,158,255,0.18)' },
        symbol: 'circle',
        symbolSize: 10,
        label: {
          show: true,
          position: 'top',
          formatter: (p: any) => `${p.value}%`
        }
      }
    ]
  })
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadMatrix()])
  } finally {
    loading.value = false
  }
}

function resize() {
  segmentChart?.resize()
  conclusionChart?.resize()
  curveChart?.resize()
}

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  segmentChart?.dispose(); segmentChart = null
  conclusionChart?.dispose(); conclusionChart = null
  curveChart?.dispose(); curveChart = null
})

watch(tab, async (v) => {
  if (v === 'segments' || v === 'overview') await nextTick(); resize()
})
</script>

<style scoped>
.price-sensitivity-page { padding: 16px; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { color: #fff; }
.kpi-card .kpi-label { color: rgba(255,255,255,0.85); font-size: 12px; }
.kpi-card .kpi-value { color: #fff; font-size: 24px; font-weight: 600; margin-top: 6px; }
.kpi-card .kpi-hint { color: rgba(255,255,255,0.7); font-size: 12px; margin-top: 4px; }
.kpi-blue { background: linear-gradient(135deg, #409eff, #66b1ff); }
.kpi-purple { background: linear-gradient(135deg, #7a5af8, #b794f4); }
.kpi-cyan { background: linear-gradient(135deg, #36cbcb, #5cdbd3); }
.kpi-orange { background: linear-gradient(135deg, #e6a23c, #ebb563); }

.chart-box { width: 100%; height: 320px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 12px; }
.filter-card :deep(.el-input),
.filter-card :deep(.el-button) { margin-right: 8px; }

.insight-list { list-style: none; padding: 0; margin: 0; }
.insight-list li {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 10px 12px; margin-bottom: 8px;
  background: #f7f9fc; border-radius: 6px;
  color: #303133;
}
.insight-icon { color: #409eff; margin-top: 2px; }

.segment-card { border-top: 3px solid; }
.segment-card .segment-label {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
}
.segment-card .segment-value { font-size: 28px; font-weight: 700; margin: 12px 0 6px; }
.segment-card .segment-desc { color: #606266; font-size: 12px; line-height: 1.5; }
</style>
