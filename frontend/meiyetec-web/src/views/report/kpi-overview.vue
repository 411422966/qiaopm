<template>
  <div class="page">
    <!-- 顶部 hero：标题 + 门店筛选 + 刷新 -->
    <div class="hero">
      <div>
        <h2>高级 KPI 看板</h2>
        <p>毛利率 · 库存周转 · 耗材差异率 · NPS — 4 个独立计算指标</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="storeId" placeholder="选择门店" clearable style="width:160px" @change="load">
          <el-option label="默认门店 1" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- 4 个独立 KPI 卡片 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <div class="kpi-icon"><el-icon><Money /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-label">毛利率（本月）</div>
            <div class="kpi-value">{{ formatPct(data.grossMargin) }}</div>
            <div class="kpi-sub">
              营收 ¥{{ formatMoney(data.grossMarginMonthRevenue) }} ·
              耗材成本 ¥{{ formatMoney(data.grossMarginMonthCogs) }}
            </div>
            <div class="kpi-foot">公式：(营收 − 耗材成本) ÷ 营收</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-teal">
          <div class="kpi-icon"><el-icon><Refresh /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-label">库存周转（年化）</div>
            <div class="kpi-value">{{ data.inventoryTurnover ?? '0.00' }}<span class="unit"> 次/年</span></div>
            <div class="kpi-sub">当前库存货值 ¥{{ formatMoney(data.currentInventoryValue) }}</div>
            <div class="kpi-foot">公式：(月度耗材成本 ÷ 当前库存货值) × 12</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card" :class="diffClass">
          <div class="kpi-icon"><el-icon><WarningFilled /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-label">耗材差异率（近 30 天）</div>
            <div class="kpi-value">{{ formatPct(data.consumablesDiffRate) }}</div>
            <div class="kpi-sub">
              差异 {{ data.consumablesCheckDiffItems || 0 }} 项 ·
              盘点 {{ data.consumablesCheckTotalItems || 0 }} 项
            </div>
            <div class="kpi-foot">公式：差异项数 ÷ 总盘点项数（status=2 已完成）</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card" :class="npsClass">
          <div class="kpi-icon"><el-icon><StarFilled /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-label">NPS（近 30 天）</div>
            <div class="kpi-value">{{ formatNps(data.nps) }}</div>
            <div class="kpi-sub">
              推荐 {{ data.npsPromoters || 0 }} ·
              被动 {{ data.npsPassives || 0 }} ·
              贬损 {{ data.npsDetractors || 0 }}
            </div>
            <div class="kpi-foot">公式：推荐者% − 贬损者%（评分 9–10 推荐 / 7–8 被动 / 0–6 贬损）</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情 Tab 区 -->
    <el-card>
      <template #header>
        <div class="card-head">
          <el-radio-group v-model="detailTab" size="small">
            <el-radio-button value="variance">耗材差异率详情</el-radio-button>
            <el-radio-button value="turnover">库存周转月趋势</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <!-- P1-6 Tab1: 耗材差异率详情 -->
      <div v-if="detailTab === 'variance'" v-loading="tabLoading">
        <!-- 工具栏：导出 -->
        <div class="tab-toolbar">
          <span>POS 实耗 vs 系统出库对比（近 30 天）</span>
          <el-button size="small" :icon="Download" @click="exportVariance">导出 CSV</el-button>
        </div>
        <!-- 汇总指标 -->
        <div class="var-summary">
          <div class="var-card">
            <div class="var-label">POS 实耗（领用）</div>
            <div class="var-value">¥{{ formatMoney(varianceData.posConsumption) }}</div>
            <div class="var-sub">{{ varianceData.posConsumptionCount || 0 }} 条出库记录</div>
          </div>
          <div class="var-arrow">→</div>
          <div class="var-card">
            <div class="var-label">系统出库</div>
            <div class="var-value">¥{{ formatMoney(varianceData.systemOut) }}</div>
            <div class="var-sub">{{ varianceData.systemOutCount || 0 }} 条出库记录</div>
          </div>
          <div class="var-arrow">=</div>
          <div class="var-card" :class="varianceRateClass">
            <div class="var-label">总体差异率</div>
            <div class="var-value">{{ formatPct(varianceData.totalVarianceRate) }}</div>
            <div class="var-sub">差异金额 ¥{{ formatMoney(varianceData.totalDiffAmount) }}</div>
          </div>
        </div>

        <!-- 按商品明细 -->
        <el-table :data="varianceData.byProduct || []" border size="small" style="margin-top: 16px" max-height="400">
          <el-table-column prop="productName" label="商品名称" min-width="160" />
          <el-table-column prop="productCode" label="编码" width="140" />
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column label="POS 实耗数量" width="120" align="right">
            <template #default="{ row }">{{ row.posQty || 0 }}</template>
          </el-table-column>
          <el-table-column label="系统出库数量" width="130" align="right">
            <template #default="{ row }">{{ row.systemOutQty || 0 }}</template>
          </el-table-column>
          <el-table-column label="差异量" width="90" align="right">
            <template #default="{ row }">
              <span :style="{ color: row.diffQty > 0 ? '#f56c6c' : '#67c23a' }">
                {{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="差异率" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.varianceRate > 10 ? 'danger' : row.varianceRate > 5 ? 'warning' : 'success'">
                {{ row.varianceRate }}%
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="POS 金额" width="110" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.posAmount) }}</template>
          </el-table-column>
          <el-table-column label="系统出库金额" width="130" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.systemOutAmount) }}</template>
          </el-table-column>
          <el-table-column label="差异金额" width="110" align="right">
            <template #default="{ row }">
              <span :style="{ color: Number(row.diffAmount) > 0 ? '#f56c6c' : '#67c23a' }">
                {{ Number(row.diffAmount) > 0 ? '+' : '' }}¥{{ formatMoney(row.diffAmount) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!varianceData.byProduct?.length" description="近 30 天暂无出库数据" />
      </div>

      <!-- P1-6 Tab2: 库存周转月趋势 -->
      <div v-if="detailTab === 'turnover'" v-loading="tabLoading">
        <!-- 工具栏：导出 -->
        <div class="tab-toolbar">
          <span>月库存周转次数（年化 = 月度 × 12）</span>
          <el-button size="small" :icon="Download" @click="exportTurnover">导出 CSV</el-button>
        </div>
        <!-- ECharts combo chart -->
        <div ref="turnoverChartRef" class="echart-container" v-if="turnoverTrend.length"></div>
        <el-empty v-if="!turnoverTrend.length" description="暂无周转数据" />
      </div>
    </el-card>

    <!-- NPS 分布可视化（ECharts 饼图） -->
    <el-card v-if="data.npsTotalReviews">
      <template #header>
        <div class="card-head">
          <span>NPS 分布 · 近 30 天评价</span>
          <span class="sub">样本数 {{ data.npsTotalReviews }}</span>
        </div>
      </template>
      <div class="nps-layout">
        <div ref="npsChartRef" class="echart-container-sm"></div>
        <div class="nps-legend">
          <div class="nps-legend-item">
            <span class="dot" style="background:#f56c6c"></span>
            <span class="nps-leg-label">贬损者（0–6 分）</span>
            <span class="nps-leg-count">{{ data.npsDetractors || 0 }}</span>
            <span class="nps-leg-pct">{{ npsPct(data.npsDetractors) }}%</span>
          </div>
          <div class="nps-legend-item">
            <span class="dot" style="background:#e6a23c"></span>
            <span class="nps-leg-label">被动者（7–8 分）</span>
            <span class="nps-leg-count">{{ data.npsPassives || 0 }}</span>
            <span class="nps-leg-pct">{{ npsPct(data.npsPassives) }}%</span>
          </div>
          <div class="nps-legend-item">
            <span class="dot" style="background:#67c23a"></span>
            <span class="nps-leg-label">推荐者（9–10 分）</span>
            <span class="nps-leg-count">{{ data.npsPromoters || 0 }}</span>
            <span class="nps-leg-pct">{{ npsPct(data.npsPromoters) }}%</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 口径说明 -->
    <el-card>
      <template #header><span class="card-head">计算口径与数据源</span></template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="毛利率">公式 (营收 − 耗材成本) / 营收 × 100%</el-descriptions-item>
        <el-descriptions-item label="库存周转">公式 (月度耗材成本 / 当前库存货值) × 12</el-descriptions-item>
        <el-descriptions-item label="耗材差异率">公式 盘点差异项数 / 总盘点项数 × 100%（仅 status=2 已完成）</el-descriptions-item>
        <el-descriptions-item label="NPS">公式 (推荐者% − 贬损者%)，crm_review.status=2 已发布</el-descriptions-item>
        <el-descriptions-item label="营收数据源">pos_order.paidAmount WHERE status=2 AND tenant_id=?</el-descriptions-item>
        <el-descriptions-item label="耗材成本数据源">stock_record.totalAmount WHERE type=4（领用）</el-descriptions-item>
        <el-descriptions-item label="库存货值数据源">SUM(stock_inventory.quantity × stock_product.cost_price)</el-descriptions-item>
        <el-descriptions-item label="盘点数据源">stock_check.total_items / diff_items，check_date 近 30 天</el-descriptions-item>
        <el-descriptions-item label="评价数据源">crm_review.overall_rating，create_time 近 30 天</el-descriptions-item>
        <el-descriptions-item label="租户隔离">所有 SQL 强制 tenant_id 过滤；storeId 可选</el-descriptions-item>
        <el-descriptions-item label="耗材差异率详情">POS领用(type=4) vs 系统出库(type=2) 金额对比，按商品明细展开</el-descriptions-item>
        <el-descriptions-item label="库存周转月趋势">近12月 type=4 月度出库金额 ÷ 月末库存货值，年化 ×12</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Money, Refresh, WarningFilled, StarFilled, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { reportApi } from '@/api/report'

echarts.use([BarChart, LineChart, PieChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const storeId = ref<number | undefined>(undefined)
const loading = ref(false)
const tabLoading = ref(false)
const data = ref<any>({})
const detailTab = ref<'variance' | 'turnover'>('variance')
const varianceData = ref<any>({})
const turnoverTrend = ref<any[]>([])

// ECharts instances
const turnoverChartRef = ref<HTMLDivElement | null>(null)
const npsChartRef = ref<HTMLDivElement | null>(null)
let turnoverChart: echarts.ECharts | null = null
let npsChart: echarts.ECharts | null = null

const formatMoney = (v: any) => {
  const n = Number(v || 0)
  if (isNaN(n)) return '0.00'
  return n.toFixed(2)
}

const formatPct = (v: any) => {
  const n = Number(v || 0)
  if (isNaN(n)) return '0.00%'
  return n.toFixed(2) + '%'
}

const formatNps = (v: any) => {
  const n = Number(v || 0)
  if (isNaN(n)) return '—'
  const sign = n > 0 ? '+' : ''
  return sign + n.toFixed(2)
}

const diffClass = computed(() => {
  const v = Number(data.value.consumablesDiffRate || 0)
  if (v >= 5) return 'kpi-red'
  if (v >= 2) return 'kpi-orange'
  return 'kpi-green'
})

const npsClass = computed(() => {
  const v = Number(data.value.nps || 0)
  if (v >= 30) return 'kpi-green'
  if (v >= 0) return 'kpi-blue'
  return 'kpi-red'
})

const varianceRateClass = computed(() => {
  const v = Number(varianceData.value.totalVarianceRate || 0)
  if (v >= 10) return 'kpi-red'
  if (v >= 5) return 'kpi-orange'
  return 'kpi-green'
})

/** 库存周转条形图宽度：最多占 80% 宽度（已废弃，由 ECharts 替代） */
const barWidth = (val: any) => {
  const n = Number(val || 0)
  if (n <= 0) return 0
  return Math.min((n / 5) * 80, 100)
}

// ========== ECharts 初始化 & 渲染 ==========

function initTurnoverChart() {
  if (!turnoverChartRef.value || !turnoverTrend.value.length) return
  turnoverChart = echarts.init(turnoverChartRef.value)
  updateTurnoverChart()
}

function updateTurnoverChart() {
  if (!turnoverChart) return
  const data = turnoverTrend.value
  const months = data.map((r: any) => r.month)
  const annualized = data.map((r: any) => Number(r.annualizedTurnover) || 0)
  const inventoryVals = data.map((r: any) => Number(r.endInventoryValue) || 0)

  turnoverChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter(params: any) {
        const month = params[0].name
        let html = `<b>${month}</b><br/>`
        params.forEach((p: any) => {
          if (p.seriesName === '年化周转（次/年）') {
            html += `${p.marker} ${p.seriesName}：<b>${p.value}x</b><br/>`
          } else {
            html += `${p.marker} ${p.seriesName}：<b>¥${formatMoney(p.value)}</b><br/>`
          }
        })
        return html
      }
    },
    legend: { data: ['年化周转（次/年）', '月末库存货值'], top: 4 },
    grid: { left: 60, right: 60, top: 48, bottom: 40 },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
    yAxis: [
      {
        type: 'value', name: '周转（次/年）', position: 'left',
        axisLabel: { fontSize: 11, formatter: (v: number) => v + 'x' }
      },
      {
        type: 'value', name: '库存货值', position: 'right',
        axisLabel: { fontSize: 11, formatter: (v: number) => '¥' + (v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v) }
      }
    ],
    series: [
      {
        name: '年化周转（次/年）', type: 'bar',
        data: annualized,
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#67c23a' }
        ]), borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 40
      },
      {
        name: '月末库存货值', type: 'line',
        yAxisIndex: 1, data: inventoryVals,
        itemStyle: { color: '#e6a23c' }, lineStyle: { width: 2 },
        symbol: 'circle', symbolSize: 6
      }
    ]
  })
}

function initNpsChart() {
  if (!npsChartRef.value) return
  npsChart = echarts.init(npsChartRef.value)
  updateNpsChart()
}

function updateNpsChart() {
  if (!npsChart) return
  const total = data.value.npsTotalReviews || 1
  npsChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
    legend: { show: false },
    series: [{
      type: 'pie',
      radius: ['40%', '72%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      label: {
        show: true,
        formatter: '{b}\n{d}%',
        fontSize: 12,
        color: '#606266'
      },
      labelLine: { show: true },
      data: [
        { value: data.value.npsDetractors || 0, name: '贬损', itemStyle: { color: '#f56c6c' } },
        { value: data.value.npsPassives || 0, name: '被动', itemStyle: { color: '#e6a23c' } },
        { value: data.value.npsPromoters || 0, name: '推荐', itemStyle: { color: '#67c23a' } }
      ]
    }]
  })
}

function resizeCharts() {
  turnoverChart?.resize()
  npsChart?.resize()
}

// ========== 导出 CSV ==========

function exportVariance() {
  const rows = varianceData.value.byProduct || []
  const header = ['商品名称', '编码', '分类', '单位', 'POS实耗数量', '系统出库数量', '差异量', '差异率(%)', 'POS金额', '系统出库金额', '差异金额']
  const lines = rows.map((r: any) => [
    r.productName, r.productCode, r.category, r.unit,
    r.posQty || 0, r.systemOutQty || 0, r.diffQty || 0, r.varianceRate || 0,
    r.posAmount || 0, r.systemOutAmount || 0, r.diffAmount || 0
  ])
  downloadCsv('耗材差异率明细', [header, ...lines])
}

function exportTurnover() {
  const header = ['月份', '月度COGS', '月末库存货值', '月度周转', '年化周转（次/年）']
  const lines = turnoverTrend.value.map((r: any) => [
    r.month, r.monthlyCogs, r.endInventoryValue, r.monthlyTurnover, r.annualizedTurnover
  ])
  downloadCsv('库存周转月趋势', [header, ...lines])
}

function downloadCsv(filename: string, rows: any[][]) {
  const BOM = '\uFEFF'
  const content = rows.map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([BOM + content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function npsPct(field: number) {
  const total = data.value.npsTotalReviews || 1
  return ((Number(field || 0) / total) * 100).toFixed(1)
}

async function load() {
  loading.value = true
  tabLoading.value = true
  try {
    const [kpiRes, varianceRes, turnoverRes] = await Promise.all([
      reportApi.kpi(storeId.value),
      reportApi.consumablesVariance(storeId.value, 30),
      reportApi.inventoryTurnoverTrend(storeId.value, 12)
    ])
    const k = kpiRes?.data?.kpiAdvanced || {}
    data.value = k
    varianceData.value = varianceRes?.data || {}
    turnoverTrend.value = turnoverRes?.data || []
    nextTick(() => {
      updateTurnoverChart()
      updateNpsChart()
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '加载 KPI 失败')
    data.value = {}
    varianceData.value = {}
    turnoverTrend.value = []
  } finally {
    loading.value = false
    tabLoading.value = false
  }
}

// Tab 切换时重新渲染图表（解决隐式宽高问题）
watch(detailTab, () => { nextTick(resizeCharts) })

// NPS 数据变化时刷新饼图
watch(() => data.value.npsTotalReviews, () => { nextTick(updateNpsChart) })

// 周转数据变化时刷新柱+线图
watch(turnoverTrend, () => { nextTick(updateTurnoverChart) }, { deep: true })

onBeforeUnmount(() => {
  turnoverChart?.dispose()
  npsChart?.dispose()
})

onMounted(() => {
  load()
  nextTick(() => {
    initTurnoverChart()
    initNpsChart()
    window.addEventListener('resize', resizeCharts)
  })
})
</script>

<style scoped>
.page { display:flex; flex-direction:column; gap:14px }
.hero { display:flex; align-items:center; justify-content:space-between; padding:22px 26px; border-radius:12px; color:#fff; background:linear-gradient(120deg,#5e35b1,#7e57c2); box-shadow:0 8px 24px #5e35b133 }
.hero h2 { margin:0 0 6px }
.hero p { margin:0; opacity:.88 }
.hero-actions { display:flex; gap:10px; align-items:center }

.kpi-card { position:relative; overflow:visible; border:none }
.kpi-card :deep(.el-card__body) { padding:18px }
.kpi-icon { position:absolute; top:16px; right:16px; font-size:32px; opacity:0.18 }
.kpi-body { padding-right:36px }
.kpi-label { font-size:13px; color:#909399; font-weight:500 }
.kpi-value { font-size:28px; font-weight:700; color:#1f2937; line-height:1.3; margin-top:4px }
.kpi-value .unit { font-size:14px; color:#909399; font-weight:400; margin-left:4px }
.kpi-sub { font-size:12px; color:#909399; margin-top:6px }
.kpi-foot { font-size:11px; color:#c0c4cc; margin-top:6px; font-family:Consolas,monospace }

.kpi-blue .kpi-value { color:#409eff }
.kpi-teal .kpi-value { color:#13c2c2 }
.kpi-green .kpi-value { color:#67c23a }
.kpi-orange .kpi-value { color:#e6a23c }
.kpi-red .kpi-value { color:#f56c6c }

.card-head { font-weight:600 }
.sub { color:#909399; font-size:13px; font-weight:400 }

.nps-legend { display:flex; gap:24px; font-size:13px; color:#606266; display:none } /* 保留兼容，已由 ECharts 替代 */

/* P1-6 Tab 工具栏 */
.tab-toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; font-size:13px; color:#606266 }
.tab-toolbar span { font-weight:600; color:#303133 }

/* ECharts 容器 */
.echart-container { width:100%; height:320px }
.echart-container-sm { width:200px; height:200px }

/* NPS 布局：饼图 + 图例并排 */
.nps-layout { display:flex; align-items:center; gap:32px }
.nps-legend { display:flex; flex-direction:column; gap:16px; flex:1 }
.nps-legend-item { display:flex; align-items:center; gap:10px; font-size:13px }
.dot { display:inline-block; width:12px; height:12px; border-radius:50%; flex-shrink:0 }
.nps-leg-label { color:#606266; flex:1 }
.nps-leg-count { font-weight:600; color:#303133; min-width:40px; text-align:right }
.nps-leg-pct { color:#909399; min-width:50px; text-align:right }

/* P1-6 耗材差异率详情 */
.var-summary { display:flex; gap:16px; align-items:center; flex-wrap:wrap }
.var-card { flex:1; min-width:140px; padding:14px 16px; border-radius:8px; background:#f5f7fa; border:1px solid #e4e7ed }
.var-card.kpi-red { background:#fef0f0; border-color:#f56c6c }
.var-card.kpi-orange { background:#fff8f0; border-color:#e6a23c }
.var-card.kpi-green { background:#f0f9f0; border-color:#67c23a }
.var-label { font-size:12px; color:#909399; margin-bottom:6px }
.var-value { font-size:22px; font-weight:700; color:#1f2937; line-height:1.2 }
.var-sub { font-size:11px; color:#c0c4cc; margin-top:4px }
.var-arrow { font-size:20px; color:#909399; flex-shrink:0 }
</style>
