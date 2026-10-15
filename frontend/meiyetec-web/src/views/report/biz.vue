<template>
  <div class="page">
    <div class="hero">
      <div>
        <h2>营业报表</h2>
        <p>按天/月查看营收走势、订单量与客单价，并一键导出 Excel。</p>
      </div>
      <div class="hero-actions">
        <el-radio-group v-model="viewMode" size="default" @change="load">
          <el-radio-button value="daily">日报</el-radio-button>
          <el-radio-button value="monthly">月报</el-radio-button>
        </el-radio-group>
        <el-select v-model="range" style="width:140px" @change="load">
          <el-option label="近7天" value="last_7_days" />
          <el-option label="近30天" value="last_30_days" />
          <el-option label="近90天" value="last_quarter" />
        </el-select>
        <el-button type="primary" :loading="exporting" @click="downloadExcel">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
      </div>
    </div>

    <!-- 汇总 -->
    <div class="kpi" v-loading="loading">
      <div class="kpi-card">
        <div class="stat-icon" style="background:#fff1f5;color:#e91e63"><el-icon><Money /></el-icon></div>
        <div><div class="stat-label">总营收</div><div class="stat-value">¥{{ formatMoney(totalRevenue) }}</div></div>
      </div>
      <div class="kpi-card">
        <div class="stat-icon" style="background:#e8f5e9;color:#4caf50"><el-icon><Document /></el-icon></div>
        <div><div class="stat-label">总订单数</div><div class="stat-value">{{ totalCount }}</div></div>
      </div>
      <div class="kpi-card">
        <div class="stat-icon" style="background:#e3f2fd;color:#1976d2"><el-icon><DataAnalysis /></el-icon></div>
        <div><div class="stat-label">平均客单价</div><div class="stat-value">¥{{ formatMoney(avgOrder) }}</div></div>
      </div>
      <div class="kpi-card">
        <div class="stat-icon" style="background:#fff8e1;color:#ff9800"><el-icon><TrendCharts /></el-icon></div>
        <div><div class="stat-label">峰值单日营收</div><div class="stat-value">¥{{ formatMoney(peakRevenue) }}</div></div>
      </div>
    </div>

    <!-- 趋势图（有数据才显示） -->
    <el-card v-if="rows.length > 0">
      <template #header>
        <div class="card-head">
          <span>{{ viewMode === 'daily' ? '每日营业数据' : '每月营业数据' }} · 趋势图</span>
          <span class="sub">时间范围：{{ rangeLabel }}</span>
        </div>
      </template>
      <div ref="chartRef" class="chart-container"></div>
    </el-card>

    <!-- 空数据提示 -->
    <el-card v-if="!loading && rows.length === 0">
      <div class="empty-state">
        <el-icon class="empty-icon"><TrendCharts /></el-icon>
        <p>暂无营业数据</p>
        <span>切换时间范围或日期后重试</span>
      </div>
    </el-card>

    <!-- 数据表（有数据才显示） -->
    <el-card v-if="rows.length > 0">
      <template #header>
        <div class="card-head">
          <span>{{ viewMode === 'daily' ? '每日营业数据' : '每月营业数据' }}</span>
          <span class="sub">时间范围：{{ rangeLabel }}</span>
        </div>
      </template>
      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column
          v-if="viewMode === 'daily'"
          prop="date" label="日期" width="140"
        />
        <el-table-column
          v-else
          prop="month" label="月份" width="140"
        />
        <el-table-column prop="orderCount" label="订单数" width="100" align="right" sortable />
        <el-table-column label="营收" width="180" align="right" sortable :sort-by="(r:any) => Number(r.revenue)">
          <template #default="{ row }">¥{{ formatMoney(row.revenue) }}</template>
        </el-table-column>
        <el-table-column
          v-if="viewMode === 'daily'"
          label="客单价" width="140" align="right"
        >
          <template #default="{ row }">¥{{ formatMoney(row.avgOrder) }}</template>
        </el-table-column>
        <el-table-column
          v-if="viewMode === 'daily'"
          label="去年同期" width="160" align="right"
        >
          <template #default="{ row }">
            <span v-if="row.lastYearRevenue != null && Number(row.lastYearRevenue) > 0">¥{{ formatMoney(row.lastYearRevenue) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="viewMode === 'daily'"
          label="同比" width="120" align="right"
        >
          <template #default="{ row }">
            <!-- vsLastYear = null 表示无去年数据，显示 — -->
            <span v-if="row.vsLastYear == null" class="muted">—</span>
            <!-- 0 表示同比为0 -->
            <span v-else-if="row.vsLastYear === 0" class="muted">0.0%</span>
            <!-- 正常非零值 -->
            <span v-else :class="vsClass(row.vsLastYear)">
              {{ vsArrow(row.vsLastYear) }} {{ formatPct(row.vsLastYear) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="占比" min-width="160">
          <template #default="{ row }">
            <el-progress :percentage="pct(row.revenue, totalRevenue)" :show-text="false" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Money, Document, DataAnalysis, TrendCharts, Download } from '@element-plus/icons-vue'
import { reportApi } from '@/api/report'
import * as echarts from 'echarts'

const viewMode = ref<'daily' | 'monthly'>('daily')
const range = ref('last_30_days')
const loading = ref(false)
const exporting = ref(false)
const rows = ref<any[]>([])

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const rangeLabel = computed(() => {
  switch (range.value) {
    case 'last_7_days': return '最近 7 天'
    case 'last_30_days': return '最近 30 天'
    case 'last_quarter': return '最近 90 天'
    default: return ''
  }
})

const totalRevenue = computed(() => rows.value.reduce((s, r) => s + Number(r.revenue || 0), 0))
const totalCount = computed(() => rows.value.reduce((s, r) => s + Number(r.orderCount || 0), 0))
const avgOrder = computed(() => totalCount.value === 0 ? 0 : totalRevenue.value / totalCount.value)
const peakRevenue = computed(() => rows.value.reduce((m, r) => Math.max(m, Number(r.revenue || 0)), 0))

function formatMoney(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}
function pct(v: number, total: number) {
  if (!total) return 0
  return Math.round((Number(v || 0) / total) * 100)
}
function formatPct(v: any) {
  const n = Number(v)
  if (isNaN(n)) return '—'
  return Math.abs(n).toFixed(1) + '%'
}
function vsArrow(v: any) {
  const n = Number(v)
  if (isNaN(n) || n === 0) return '—'
  return n >= 0 ? '↑' : '↓'
}
function vsClass(v: any) {
  const n = Number(v)
  if (isNaN(n) || n === 0) return 'vs-neutral'
  return n > 0 ? 'vs-up' : 'vs-down'
}
function numFmt(v: number) {
  if (v === 0) return '0'
  if (v >= 10000) return `${(v / 10000).toFixed(1)}w`
  if (v >= 1000) return `${(v / 1000).toFixed(0)}k`
  return String(v)
}

async function load() {
  loading.value = true
  try {
    if (viewMode.value === 'daily') {
      const days = range.value === 'last_7_days' ? 7 : range.value === 'last_quarter' ? 90 : 30
      const res = await reportApi.bizDaily(days)
      rows.value = res.data || []
    } else {
      const months = range.value === 'last_7_days' ? 1 : range.value === 'last_quarter' ? 3 : 12
      const res = await reportApi.bizMonthly(months)
      rows.value = res.data || []
    }
    await nextTick()
    renderChart()
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// ===== 图表 =====
function renderChart() {
  if (!chartRef.value || rows.value.length === 0) return
  if (chart) { chart.dispose(); chart = null }
  chart = echarts.init(chartRef.value)

  const isDaily = viewMode.value === 'daily'
  const xData = rows.value.map((r: any) => isDaily ? r.date : r.month)
  const revenues = rows.value.map((r: any) => Number(r.revenue) || 0)
  const lastYear = isDaily ? rows.value.map((r: any) => Number(r.lastYearRevenue) || 0) : []

  const showRotate = xData.length > 60
  const fontSize = xData.length > 30 ? 10 : 11
  const maxRev = Math.max(...revenues.filter(v => v > 0), 1)
  const hasLyData = isDaily && lastYear.some(v => v > 0)
  const maxLy = hasLyData ? Math.max(...lastYear.filter(v => v > 0), 1) : 0

  const series: any[] = [
    {
      name: isDaily ? '本期营收' : '本月营收',
      type: 'bar',
      yAxisIndex: 0,
      data: revenues,
      barMaxWidth: 22,
      barMinHeight: 2,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#42a5f5' },
          { offset: 1, color: '#1565c0' }
        ]),
        borderRadius: [3, 3, 0, 0]
      },
      label: {
        show: revenues.some(v => v > 0),
        position: 'top',
        fontSize: 10,
        color: '#42a5f5',
        formatter: (p: any) => {
          const v = Number(p.value) || 0
          return v === 0 ? '' : numFmt(v)
        }
      }
    }
  ]

  const yAxis: any[] = [
    {
      type: 'value',
      name: '本期',
      nameTextStyle: { fontSize: 11, color: '#42a5f5' },
      position: 'left',
      min: 0,
      max: Math.ceil(maxRev * 1.15),
      axisLabel: { fontSize: 11, color: '#42a5f5', formatter: numFmt },
      splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
      axisLine: { show: false }
    }
  ]

  // 仅当有去年数据时才显示对比线 + 右侧Y轴
  if (isDaily && hasLyData) {
    series.push({
      name: '去年同期',
      type: 'line',
      yAxisIndex: 1,
      data: lastYear,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#e0a0a0', width: 2 },
      itemStyle: { color: '#e0a0a0' },
      emphasis: { focus: 'series' }
    })
    yAxis.push({
      type: 'value',
      name: '去年',
      nameTextStyle: { fontSize: 11, color: '#e0a0a0' },
      position: 'right',
      min: 0,
      max: Math.ceil(maxLy * 1.15),
      axisLabel: { fontSize: 11, color: '#e0a0a0', formatter: numFmt },
      splitLine: { show: false },
      axisLine: { show: false },
      splitArea: {
        show: true,
        areaStyle: { color: ['rgba(224,160,160,0.04)', 'rgba(224,160,160,0)'] }
      }
    })
  }

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        const date = params[0].axisValue
        let html = `<b>${date}</b><br/>`
        let rev = 0
        let ly = 0
        params.forEach((p: any) => {
          const v = Number(p.value) || 0
          if (p.seriesName && p.seriesName.includes('去年同期')) ly = v
          else if (p.seriesName) { rev = v; html += `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:4px"></span>${p.seriesName}: ¥${v.toFixed(2)}<br/>` }
        })
        if (isDaily && ly > 0) {
          const rate = ((rev - ly) / ly * 100)
          const sign = rate >= 0 ? '↑' : '↓'
          html += `<span style="color:${rate >= 0 ? '#67c23a' : '#f56c6c'};font-weight:600">同比 ${sign}${Math.abs(rate).toFixed(1)}%</span>`
        }
        return html
      }
    },
    legend: { show: hasLyData, top: 4, textStyle: { fontSize: 12 } },
    grid: { left: 65, right: hasLyData ? 60 : 24, top: hasLyData ? 38 : 24, bottom: showRotate ? 70 : 46 },
    xAxis: {
      type: 'category',
      data: xData,
      boundaryGap: true,
      axisLabel: {
        fontSize,
        rotate: showRotate ? 30 : 0,
        interval: 'auto',
        formatter: (v: string) => isDaily ? v.slice(5) : v
      },
      axisTick: { alignWithLabel: false },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    yAxis,
    series
  }
  chart.setOption(option, true)
}

function handleResize() {
  chart?.resize()
}

watch([viewMode, range], () => { load() })

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})

// ===== 导出（Excel 含同比/去年数据） =====
async function downloadExcel() {
  exporting.value = true
  const isDaily = viewMode.value === 'daily'
  const token = localStorage.getItem('token') || ''

  try {
    // 用前端已有 rows 数据直接生成 blob，避免多一次请求
    const headers = isDaily
      ? ['日期', '订单数', '营收(元)', '客单价(元)', '去年同期(元)', '同比(%)']
      : ['月份', '订单数', '营收(元)']

    const csvRows = rows.value.map(r => {
      if (isDaily) {
        const ly = r.lastYearRevenue != null ? Number(r.lastYearRevenue) : 0
        const lyStr = ly > 0 ? ly.toFixed(2) : ''
        const vsStr = r.vsLastYear != null ? r.vsLastYear.toFixed(1) + '%' : '—'
        return [
          r.date,
          r.orderCount,
          Number(r.revenue || 0).toFixed(2),
          Number(r.avgOrder || 0).toFixed(2),
          lyStr,
          vsStr
        ].join(',')
      } else {
        return [
          r.month,
          r.orderCount,
          Number(r.revenue || 0).toFixed(2)
        ].join(',')
      }
    })

    const BOM = '\uFEFF'
    const csv = BOM + [headers.join(','), ...csvRows].join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const objectUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = (isDaily ? '营业日报_' : '营业月报_') + new Date().toISOString().slice(0, 10) + '.csv'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(objectUrl)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.page { display:flex; flex-direction:column; gap:14px }
.hero { display:flex; align-items:center; justify-content:space-between; padding:22px 26px; border-radius:12px; color:#fff; background:linear-gradient(120deg,#00acc1,#26c6da); box-shadow:0 8px 24px #00acc133 }
.hero h2 { margin:0 0 6px }
.hero p { margin:0; opacity:.88 }
.hero-actions { display:flex; gap:10px; align-items:center }
.kpi { display:grid; grid-template-columns:repeat(4,1fr); gap:14px }
.kpi-card { background:#fff; padding:18px; border-radius:12px; display:flex; align-items:center; gap:14px; box-shadow:0 4px 16px #00000010 }
.stat-icon { width:48px; height:48px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:22px }
.stat-label { color:#909399; font-size:13px; margin-bottom:4px }
.stat-value { font-size:22px; font-weight:600; color:#303133 }
.card-head { display:flex; justify-content:space-between; align-items:center }
.sub { color:#909399; font-size:13px }
.chart-container { width:100%; height:320px }

/* 空状态 */
.empty-state { display:flex; flex-direction:column; align-items:center; justify-content:center; padding:60px 0; color:#909399 }
.empty-icon { font-size:48px; color:#dcdfe6; margin-bottom:12px }
.empty-state p { margin:0 0 4px; font-size:15px; color:#606266 }
.empty-state span { font-size:13px; color:#c0c4cc }

/* 涨跌色 */
.muted { color:#c0c4cc }
.vs-up { color:#67c23a; font-weight:600 }
.vs-down { color:#f56c6c; font-weight:600 }
.vs-neutral { color:#909399 }
</style>