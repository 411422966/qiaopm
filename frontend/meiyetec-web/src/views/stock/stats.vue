<template>
  <div class="stats-page">

    <!-- 功能未启用 -->
    <template v-if="!featureEnabled">
      <el-row justify="center" style="margin-top: 80px">
        <el-col :span="12">
          <el-card shadow="never" class="empty-card">
            <div style="text-align: center; padding: 32px 0">
              <svg width="64" height="64" viewBox="0 0 64 64" fill="none" style="margin-bottom: 16px">
                <rect x="8" y="32" width="12" height="20" rx="2" fill="#dcdfe6"/>
                <rect x="26" y="20" width="12" height="32" rx="2" fill="#c0c4cc"/>
                <rect x="44" y="12" width="12" height="40" rx="2" fill="#909399"/>
              </svg>
              <h3 style="color: #303133; margin-bottom: 8px">库存统计功能</h3>
              <p style="color: #909399; margin-bottom: 16px">
                库存总览、消耗趋势、分类占比、TOP 排行将在数据积累后自动生成
              </p>
              <el-divider>
                <span style="color: #c0c4cc; font-size: 12px">数据来源</span>
              </el-divider>
              <div class="flow-steps">
                <el-tag type="info">商品档案</el-tag>
                <el-icon style="color: #c0c4cc"><ArrowRight /></el-icon>
                <el-tag type="info">出入库记录</el-tag>
                <el-icon style="color: #c0c4cc"><ArrowRight /></el-icon>
                <el-tag type="info">消耗记录</el-tag>
                <el-icon style="color: #c0c4cc"><ArrowRight /></el-icon>
                <el-tag type="success">统计报表</el-tag>
              </div>
              <el-divider />
              <p style="color: #c0c4cc; font-size: 12px">
                完成库存消耗闭环后即可使用完整统计功能
              </p>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 功能已启用 -->
    <template v-else>
      <!-- 概览统计卡 -->
      <el-row :gutter="12" class="stats-row">
        <el-col :span="6">
          <el-card>
            <div class="stat">
              <div class="label">商品种类</div>
              <div class="value">{{ overview.totalProducts || 0 }}</div>
              <div class="sub">当前在库</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card>
            <div class="stat">
              <div class="label">库存总价值</div>
              <div class="value primary">¥{{ formatAmount(overview.totalValue || 0) }}</div>
              <div class="sub">成本口径</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card>
            <div class="stat">
              <div class="label">本月消耗</div>
              <div class="value danger">¥{{ formatAmount(overview.monthCost || 0) }}</div>
              <div class="sub">耗材成本</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card>
            <div class="stat">
              <div class="label">本月出库</div>
              <div class="value warn">{{ overview.monthOutCount || 0 }} 次</div>
              <div class="sub">含报损</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 趋势图表 + 分类占比 -->
      <el-row :gutter="12" style="margin-bottom: 12px">
        <el-col :span="12">
          <el-card>
            <template #header><span>月度消耗趋势（近6月）</span></template>
            <div ref="trendChartRef" style="height: 260px">
              <div v-if="!trendData.length" class="chart-placeholder">暂无趋势数据</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header><span>分类库存占比</span></template>
            <div ref="pieChartRef" style="height: 260px">
              <div v-if="!categoryData.length" class="chart-placeholder">暂无分类数据</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 消耗排行 + 预警快览 -->
      <el-row :gutter="12">
        <el-col :span="14">
          <el-card>
            <template #header>
              <span>消耗排行 TOP10</span>
              <el-radio-group v-model="rankType" size="small" style="float: right" @change="loadRanking">
                <el-radio-button value="amount">按金额</el-radio-button>
                <el-radio-button value="count">按数量</el-radio-button>
              </el-radio-group>
            </template>
            <el-table :data="ranking" border stripe size="small">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="productName" label="商品" width="180" />
              <el-table-column prop="category" label="分类" width="80" />
              <el-table-column label="消耗数量" width="100" align="right">
                <template #default="{ row }">{{ row.totalQty }} {{ row.unit }}</template>
              </el-table-column>
              <el-table-column label="消耗金额" width="100" align="right">
                <template #default="{ row }">
                  <span class="neg">¥{{ formatAmount(row.totalAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="占比" width="120">
                <template #default="{ row }">
                  <el-progress :percentage="row.pct || 0" :color="(row.pct || 0) > 20 ? '#f56c6c' : '#409eff'" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card>
            <template #header><span>库存预警快览</span></template>
            <el-table :data="quickAlerts" border stripe size="small">
              <el-table-column label="预警" width="80">
                <template #default="{ row }">
                  <el-tag :type="(ALERT_TAG[row.alertType] || 'primary') as any" size="small">{{ ALERT_LABEL[row.alertType] || '其他' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="productName" label="商品" />
              <el-table-column label="当前" width="80" align="right">
                <template #default="{ row }">
                  <span :class="row.alertType === 'OUT' ? 'danger' : row.alertType === 'LOW' ? 'warn' : ''">{{ row.quantity }}</span>
                </template>
              </el-table-column>
              <el-table-column label="最低" width="60" align="right">
                <template #default="{ row }">{{ row.minStock }}</template>
              </el-table-column>
            </el-table>
            <div style="margin-top: 8px; text-align: right">
              <el-button link type="primary" @click="$router.push('/stock/alert')">查看全部预警 →</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import stockApi from '@/api/stock'

const featureEnabled = ref(true)

const trendChartRef = ref<HTMLElement>()
const pieChartRef = ref<HTMLElement>()
let trendChart: any = null
let pieChart: any = null

const overview = ref<any>({})
const trendData = ref<any[]>([])
const categoryData = ref<any[]>([])
const ranking = ref<any[]>([])
const quickAlerts = ref<any[]>([])
const rankType = ref('amount')

const ALERT_LABEL: Record<string, string> = { OUT: '缺货', LOW: '低于', OVER: '超储', EXPIRING: '临期' }
const ALERT_TAG: Record<string, string> = { OUT: 'danger', LOW: 'warning', OVER: 'info', EXPIRING: 'primary' }

const formatAmount = (v: number) => (v || 0).toFixed(2)

const loadOverview = async (): Promise<boolean> => {
  try {
    const res: any = await stockApi.stats({})
    overview.value = res.data || {}
    featureEnabled.value = true
    return true
  } catch (e: any) {
    if (e?.response?.status === 404) {
      featureEnabled.value = false
      return false
    }
    return false
  }
}

const loadTrend = async () => {
  if (!featureEnabled.value) return
  try {
    const res: any = await stockApi.statsTrend({ months: 6 })
    console.log('[stats] trend raw', res)
    trendData.value = res.data || []
    console.log('[stats] trend set', trendData.value.length, trendData.value)
    await nextTick()
    renderTrendChart()
  } catch (e) {
    console.warn('[stats] trend error', e)
  }
}

const loadCategory = async () => {
  if (!featureEnabled.value) return
  try {
    const res: any = await stockApi.stats({})
    console.log('[stats] category raw', res)
    const records = res.data?.categoryStats || []
    categoryData.value = records
    console.log('[stats] category set', categoryData.value.length, categoryData.value)
    await nextTick()
    renderPieChart()
  } catch (e) {
    console.warn('[stats] category error', e)
  }
}

const loadRanking = async () => {
  if (!featureEnabled.value) return
  try {
    const res: any = await stockApi.statsRanking({ type: rankType.value, limit: 10 })
    ranking.value = res.data || []
  } catch {}
}

const loadAlerts = async () => {
  if (!featureEnabled.value) return
  try {
    const res: any = await stockApi.alertPage({ pageNum: 1, pageSize: 10, alertType: 'OUT,LOW' })
    quickAlerts.value = res.data?.records || []
  } catch {}
}

const renderTrendChart = () => {
  if (!trendChartRef.value) return
  if (!trendData.value.length) return
  if (!(window as any).echarts) {
    console.warn('[stats] echarts 尚未就绪，放弃渲染')
    return
  }
  trendChart?.dispose()
  trendChart = null
  trendChart = (window as any).echarts.init(trendChartRef.value)
  const months = trendData.value.map((r: any) => r.month)
  const amounts = trendData.value.map((r: any) => r.amount || 0)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', name: '金额(元)' },
    series: [{ name: '消耗金额', type: 'bar', data: amounts, itemStyle: { color: '#f56c6c' }, barWidth: '50%' }]
  })
}

const renderPieChart = () => {
  if (!pieChartRef.value) return
  if (!categoryData.value.length) return
  if (!(window as any).echarts) {
    console.warn('[stats] echarts 尚未就绪，放弃渲染')
    return
  }
  pieChart?.dispose()
  pieChart = null
  pieChart = (window as any).echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0, left: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data: categoryData.value.map((r: any) => ({ name: r.category, value: r.value })),
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 }
    }]
  })
}

const loadAll = async () => {
  // 第一个请求作为"探针"：决定是否启用功能
  const ok = await loadOverview()
  if (!ok) return  // 404 后续不再调用
  await Promise.all([
    loadTrend(),
    loadCategory(),
    loadRanking(),
    loadAlerts()
  ])
}

onMounted(async () => {
  // 先加载 echarts，再触发数据请求（图表渲染依赖 window.echarts）
  try {
    const echarts: any = await import('echarts')
    // echarts v6 ESM 直接导出 init（无 default 整体对象）
    const initFn = echarts.init || echarts.default?.init
    // 用对象包装，保留 init 等 API；同时把 use/registerComponent 等也挂上
    ;(window as any).echarts = {
      init: initFn,
      use: echarts.use,
      registerComponent: echarts.registerComponent,
      registerLayout: echarts.registerLayout,
      registerProcessor: echarts.registerProcessor,
      registerMap: echarts.registerMap,
      registerTheme: echarts.registerTheme
    }
    console.log('[stats] echarts loaded, init type:', typeof initFn)
  } catch (e) {
    console.error('[stats] echarts 加载失败', e)
  }

  await loadAll()
})

onUnmounted(() => {
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<style scoped>
.stats-page { padding: 12px; }
.stats-row { margin-bottom: 12px; }
.stat { text-align: center; }
.stat .label { color: #999; font-size: 12px; }
.stat .value { font-size: 28px; font-weight: bold; margin-top: 4px; }
.stat .value.primary { color: #409eff; }
.stat .value.danger { color: #f56c6c; }
.stat .value.warn { color: #e6a23c; }
.stat .sub { color: #bbb; font-size: 11px; margin-top: 2px; }
.danger { color: #f56c6c; font-weight: bold; }
.warn { color: #e6a23c; font-weight: bold; }
.neg { color: #f56c6c; }
.empty-card { border: 1px dashed #e4e7ed; }
.flow-steps { display: flex; align-items: center; justify-content: center; gap: 8px; flex-wrap: wrap; }
.chart-placeholder { height: 260px; display: flex; align-items: center; justify-content: center; color: #c0c4cc; }
</style>
