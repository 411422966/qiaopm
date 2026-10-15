<template>
  <div class="hq-page">

    <!-- P0-1: KPI 指标卡（可点击跳转） -->
    <el-card>
      <div class="kpi-row">
      <div class="kpi-item" :class="{ clickable: true }" @click="router.push('/report/biz')">
        <div class="kpi-label">数据范围</div>
        <div class="kpi-value small">{{ scope.dataScope || '—' }}</div>
        <div class="kpi-sub">点击查看明细</div>
      </div>
      <div class="kpi-item green clickable" @click="router.push('/report/biz')">
        <div class="kpi-label">总净营收</div>
        <div class="kpi-value">¥{{ formatAmount(data.totalNetRevenue) }}</div>
        <!-- P0-3: 同比箭头 -->
        <div class="kpi-trend" v-if="data.compare">
          <span :class="trendClass(data.compare.revenueGrowth)">
            {{ trendIcon(data.compare.revenueGrowth) }}
            {{ Math.abs(data.compare.revenueGrowth).toFixed(1) }}%
          </span>
          <span class="kpi-sub">较上期</span>
        </div>
      </div>
      <div class="kpi-item yellow clickable" @click="router.push('/pos/orders')">
        <div class="kpi-label">总订单数</div>
        <div class="kpi-value">{{ data.totalOrderCount ?? '—' }}</div>
        <div class="kpi-trend" v-if="data.compare">
          <span :class="trendClass(data.compare.ordersGrowth)">
            {{ trendIcon(data.compare.ordersGrowth) }}
            {{ Math.abs(data.compare.ordersGrowth).toFixed(1) }}%
          </span>
          <span class="kpi-sub">较上期</span>
        </div>
      </div>
      <div class="kpi-item blue clickable" @click="router.push('/org/brand')">
        <div class="kpi-label">品牌数</div>
        <div class="kpi-value">{{ data.brandSummary?.length ?? 0 }}</div>
        <div class="kpi-sub">点击管理品牌</div>
      </div>
      </div>
    </el-card>

    <!-- P0-2: 趋势图 -->
    <el-card class="chart-card" v-loading="chartLoading">
      <div class="chart-header">
        <span class="chart-title">📈 营收趋势（近 {{ trendDays() }} 天）</span>
        <el-radio-group v-model="chartMode" size="small">
          <el-radio-button value="revenue">营收</el-radio-button>
          <el-radio-button value="orders">订单数</el-radio-button>
          <el-radio-button value="both">双轴</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="chartRef" class="chart-container" v-show="data.trend?.length"></div>
      <div v-if="!data.trend?.length && !chartLoading" class="chart-empty">
        暂无趋势数据，请确认有订单记录
      </div>
    </el-card>

    <!-- P0-5: 增强筛选器 -->
    <el-card class="search-bar">
      <div class="search-body">
        <el-date-picker
          v-model="period"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="~"
          start-placeholder="开始"
          end-placeholder="结束"
          style="width: 260px"
        />
        <el-select v-model="compareMode" placeholder="对比周期" style="width: 150px" clearable>
          <el-option label="无对比" value="none" />
          <el-option label="环比上周" value="prev_week" />
          <el-option label="环比上月" value="prev_month" />
        </el-select>
        <el-select v-model="dimension" placeholder="维度" style="width: 130px">
          <el-option label="全部门店" value="ALL" />
          <el-option label="按品牌" value="BRAND" />
          <el-option label="按门店" value="STORE" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
        <el-button @click="resetPeriod">重置</el-button>
      </div>
    </el-card>

    <!-- 品牌 + Top 门店并排 -->
    <el-row :gutter="12">
      <el-col :span="12">
        <el-card>
          <div class="table-header">
            <h4 style="margin:0">品牌级汇总</h4>
            <span class="table-hint">点击行跳转品牌管理</span>
          </div>
          <el-table
            :data="data.brandSummary || []"
            border size="small"
            :empty-text="' '"
            @row-click="row => router.push('/org/brand')"
            :row-class-name="() => 'clickable-row'"
          >
            <el-table-column prop="brandName" label="品牌" min-width="180" />
            <el-table-column prop="storeCount" label="门店数" width="100" align="center" />
            <el-table-column prop="memberCount" label="会员数" width="100" align="center" />
            <el-table-column prop="orderCount" label="订单数" width="100" align="center" />
            <el-table-column label="净营收" width="140" align="right">
              <template #default="{ row }">¥{{ formatAmount(row.netRevenue) }}</template>
            </el-table-column>
          </el-table>
          <!-- P0-4: 空状态 -->
          <div v-if="!data.brandSummary?.length" class="empty-state">
            <span>暂无品牌数据</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <div class="table-header">
            <h4 style="margin:0">Top 10 门店</h4>
            <span class="table-hint">点击行跳转门店管理</span>
          </div>
          <el-table
            :data="data.topStores || []"
            border size="small"
            :empty-text="' '"
            @row-click="row => router.push('/org/store')"
            :row-class-name="() => 'clickable-row'"
          >
            <el-table-column type="index" label="排名" width="60" />
            <el-table-column prop="storeName" label="门店" min-width="160" />
            <el-table-column prop="orderCount" label="订单数" width="100" align="center" />
            <el-table-column label="净营收" width="130" align="right">
              <template #default="{ row }">¥{{ formatAmount(row.netRevenue) }}</template>
            </el-table-column>
          </el-table>
          <!-- P0-4: 空状态 -->
          <div v-if="!data.topStores?.length" class="empty-state">
            <span>暂无门店数据</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const router = useRouter()
const scope = reactive<any>({})
const data = reactive<any>({})
const period = ref<any>([])
const loading = ref(false)
const chartLoading = ref(false)
const chartRef = ref<HTMLElement>()
const chartMode = ref<'revenue' | 'orders' | 'both'>('revenue')
const compareMode = ref('none')
const dimension = ref('ALL')

let chartInst: any = null

function trendDays(): number {
  if (!period.value || period.value.length !== 2) return 30
  const [s, e] = period.value
  if (!s || !e) return 30
  const ms = new Date(s).getTime(), me = new Date(e).getTime()
  return Math.max(1, Math.round((me - ms) / 86400000) + 1)
}

function trendClass(val: number | null | undefined) {
  if (val == null || val === 0) return 'trend-neutral'
  return val > 0 ? 'trend-up' : 'trend-down'
}

function trendIcon(val: number | null | undefined) {
  if (val == null || val === 0) return '—'
  return val > 0 ? '▲' : '▼'
}

async function loadScope() {
  try {
    const r = await http.get('/org/scope/me')
    Object.assign(scope, r.data || {})
  } catch {}
}

async function loadData() {
  loading.value = true
  chartLoading.value = true
  try {
    const params: any = {}
    if (period.value && period.value.length === 2) {
      params.startDate = period.value[0]
      params.endDate = period.value[1]
    }
    // 维度：ALL=汇总；BRAND=按品牌分组；STORE=按门店分组（后端可扩展）
    if (dimension.value && dimension.value !== 'ALL') {
      params.dimension = dimension.value
    }
    // 对比周期：none=不对比；prev_week=环比上周；prev_month=环比上月
    if (compareMode.value && compareMode.value !== 'none') {
      params.compareMode = compareMode.value
    }
    const r = await http.get('/org/hq/dashboard', { params })
    Object.assign(data, r.data || {})
    await nextTick()
    await renderChart()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
    chartLoading.value = false
  }
}

function resetPeriod() {
  const now = new Date()
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  period.value = [fmt(new Date(now.getTime() - 29 * 86400000)), fmt(now)]
  loadData()
}

function formatAmount(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

// P0-2: ECharts 趋势图
async function renderChart() {
  const trend = data.trend
  if (!chartRef.value || !trend?.length) {
    chartInst?.dispose()
    chartInst = null
    return
  }
  try {
    const echarts = await import('echarts/core')
    const [LineChart, BarChart, GridComponent, TooltipComponent,
           LegendComponent, DataZoomComponent] = await Promise.all([
      import('echarts/charts').then(m => m.LineChart),
      import('echarts/charts').then(m => m.BarChart),
      import('echarts/components').then(m => m.GridComponent),
      import('echarts/components').then(m => m.TooltipComponent),
      import('echarts/components').then(m => m.LegendComponent),
      import('echarts/components').then(m => m.DataZoomComponent),
    ])
    const { CanvasRenderer } = await import('echarts/renderers')
    echarts.use([LineChart, BarChart, GridComponent, TooltipComponent,
                 LegendComponent, DataZoomComponent, CanvasRenderer])

    if (chartInst) { chartInst.dispose(); chartInst = null }
    chartInst = echarts.init(chartRef.value)

    const dates = trend.map((r: any) => {
      const d = r.statDate || r.date
      return d ? d.slice(5) : ''
    })
    const revenues = trend.map((r: any) => Number(r.netRevenue || 0))
    const orders = trend.map((r: any) => Number(r.orderCount || 0))

    const isBoth = chartMode.value === 'both'

    chartInst.setOption({
      tooltip: isBoth
        ? { trigger: 'axis', axisPointer: { type: 'cross' } }
        : { trigger: 'axis' },
      legend: isBoth ? { data: ['营收(元)', '订单数'], top: 0 } : undefined,
      grid: { left: '3%', right: '4%', bottom: '12%', top: isBoth ? '35px' : '15px', containLabel: true },
      xAxis: {
        type: 'category', data: dates,
        axisLine: { lineStyle: { color: '#e4e7ed' } },
        axisLabel: { color: '#606266', fontSize: 11 },
      },
      yAxis: isBoth ? [
        { type: 'value', name: '营收', axisLine: { show: false }, axisTick: { show: false },
          splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#909399' } },
        { type: 'value', name: '订单', axisLine: { show: false }, axisTick: { show: false },
          splitLine: { show: false }, axisLabel: { color: '#909399' } },
      ] : { type: 'value', axisLine: { show: false }, axisTick: { show: false },
          splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#909399', fontSize: 11 } },
      series: isBoth ? [
        { name: '营收(元)', type: 'line', yAxisIndex: 0, data: revenues,
          smooth: true, lineStyle: { color: '#409eff', width: 2 },
          itemStyle: { color: '#409eff' },
          areaStyle: { color: 'rgba(64,158,255,0.10)' } },
        { name: '订单数', type: 'bar', yAxisIndex: 1, data: orders,
          itemStyle: { color: 'rgba(64,158,255,0.30)', borderRadius: [3,3,0,0] } },
      ] : chartMode.value === 'orders' ? [
        { name: '订单数', type: 'bar',
          data: orders,
          itemStyle: { color: 'rgba(64,158,255,0.70)', borderRadius: [4,4,0,0] },
          barMaxWidth: 40 },
      ] : [
        { name: '营收(元)', type: 'line', data: revenues,
          smooth: true, lineStyle: { color: '#409eff', width: 2 },
          itemStyle: { color: '#409eff' },
          areaStyle: { color: 'rgba(64,158,255,0.12)' },
          showSymbol: false },
      ],
      dataZoom: [{
        type: 'inside', start: 0, end: 100,
        zoomLock: false,
      }],
    }, true)
  } catch (e) { console.warn('[hq chart]', e) }
}

const onResize = () => chartInst?.resize()
window.addEventListener('resize', onResize)

watch(chartMode, () => renderChart())

onMounted(async () => {
  // 默认近 30 天
  const now = new Date()
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  period.value = [fmt(new Date(now.getTime() - 29 * 86400000)), fmt(now)]
  await loadScope()
  await loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chartInst?.dispose()
})
</script>

<style scoped>
.hq-page { padding: 0; }

/* KPI 卡 */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.kpi-item {
  background: linear-gradient(135deg, #f0f9ff, #e0f2fe);
  border-radius: 10px;
  padding: 16px 12px;
  text-align: center;
  transition: box-shadow 0.2s, transform 0.2s;
  cursor: default;
}
.kpi-item.clickable { cursor: pointer; }
.kpi-item.clickable:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  transform: translateY(-2px);
}
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-item.blue { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.kpi-label { font-size: 12px; color: #475569; margin-bottom: 4px; }
.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}
.kpi-value.small { font-size: 14px; font-weight: 600; }
.kpi-sub { font-size: 11px; color: #94a3b8; margin-top: 4px; }
.kpi-trend { margin-top: 4px; }
.trend-up { color: #16a34a; font-weight: 600; font-size: 13px; }
.trend-down { color: #dc2626; font-weight: 600; font-size: 13px; }
.trend-neutral { color: #64748b; font-size: 13px; }

/* 趋势图 */
.chart-card { margin-bottom: 12px; }
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.chart-title { font-size: 15px; font-weight: 600; color: #1e293b; }
.chart-container { height: 280px; min-height: 120px; }
.chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  color: #94a3b8;
  font-size: 13px;
  border-radius: 6px;
  background: linear-gradient(180deg, #fafbfc, #f5f7fa);
}

/* 搜索栏 */
.search-bar { margin-bottom: 12px; }
.search-bar :deep(.el-card__body) { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* 表格 */
.table-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.table-hint { font-size: 11px; color: #94a3b8; }
.clickable-row { cursor: pointer; }
.clickable-row:hover { background-color: #f5f7fa; }
.empty-state {
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
}
</style>
