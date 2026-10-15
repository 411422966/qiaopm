<template>
  <div class="dashboard">
    <!-- 快捷入口 -->
    <el-card shadow="never" class="quick-nav">
      <el-button type="primary" @click="$router.push('/pos/checkout')">收银结算</el-button>
      <el-button type="success" @click="$router.push('/member')">新增会员</el-button>
      <el-button type="warning" @click="$router.push('/booking/booking-form')">新建预约</el-button>
      <el-button @click="$router.push('/report/dashboard')">经营报表</el-button>
      <el-button type="info" @click="$router.push('/market/coupon')">发放优惠券</el-button>
    </el-card>

    <!-- 第一行：核心统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-green" @click="$router.push('/wallet/transactions')">
          <div class="kpi-icon"><el-icon><Money /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥ {{ formatMoney(stats.todayRevenue) }}</div>
            <div class="kpi-label">今日营收</div>
            <div class="kpi-sub">消费 {{ stats.todayOrderCount || 0 }} 笔 · 充值 ¥{{ formatMoney(stats.todayRecharge) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-blue" @click="$router.push('/member')">
          <div class="kpi-icon"><el-icon><UserFilled /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ stats.total || 0 }}</div>
            <div class="kpi-label">会员总数</div>
            <div class="kpi-sub">活跃 {{ stats.active || 0 }} · 冻结 {{ stats.frozen || 0 }} · 活跃率 {{ stats.activeRate || 0 }}%</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-orange" @click="$router.push('/wallet/transactions')">
          <div class="kpi-icon"><el-icon><Wallet /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥ {{ formatMoney(stats.totalBalance) }}</div>
            <div class="kpi-label">储值总额</div>
            <div class="kpi-sub">累计消费 ¥{{ formatMoney(stats.totalConsume) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-red" @click="$router.push('/mkt/points')">
          <div class="kpi-icon"><el-icon><Coin /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ (stats.totalPoints || 0).toLocaleString() }}</div>
            <div class="kpi-label">积分总量</div>
            <div class="kpi-sub">人均 {{ stats.total ? Math.round((stats.totalPoints || 0) / stats.total) : 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：运营指标卡 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-teal" :body-style="{ padding: '0' }" @click="$router.push('/booking/list')">
          <div class="mini-kpi">
            <div class="mini-left">
              <div class="kpi-value">{{ bookingStats.total || 0 }}</div>
              <div class="kpi-label">今日预约</div>
              <div class="kpi-sub">已确认 {{ bookingStats.confirmed || 0 }} · 待完成 {{ bookingStats.pending || 0 }}</div>
            </div>
            <div class="mini-right">
              <div class="checkin-ring" :style="{ '--pct': (bookingStats.checkInRate || 0) + '%' }">
                <span>{{ bookingStats.checkInRate || 0 }}%</span>
                <span class="ring-label">到店率</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-purple" @click="$router.push('/crm/care-follow-task')">
          <div class="kpi-icon"><el-icon><ChatDotRound /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">
              {{ careStats.pending || 0 }}
              <el-badge :value="careStats.urgent || 0" type="danger" :hidden="!careStats.urgent" />
            </div>
            <div class="kpi-label">待回访任务</div>
            <div class="kpi-sub">紧急 {{ careStats.urgent || 0 }} · 进行中 {{ careStats.inProgress || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card" :class="alertStats.total > 0 ? 'kpi-red' : 'kpi-green'" @click="$router.push('/stock/alert')">
          <div class="kpi-icon"><el-icon><WarningFilled /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ alertStats.total || 0 }}</div>
            <div class="kpi-label">库存预警</div>
            <div class="kpi-sub">
              <span v-if="(alertStats.low || 0) > 0" class="alert-chip low">库存不足 {{ alertStats.low }}</span>
              <span v-if="(alertStats.expiring || 0) > 0" class="alert-chip exp">临期 {{ alertStats.expiring }}</span>
              <span v-if="(alertStats.expired || 0) > 0" class="alert-chip expired">已过期 {{ alertStats.expired }}</span>
              <span v-if="!alertStats.low && !alertStats.expiring && !alertStats.expired">暂无预警</span>
            </div>
          </div>
          <div v-if="alertStats.total > 0" class="alert-dot"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card kpi-gold" @click="$router.push('/report/dashboard')">
          <div class="kpi-icon"><el-icon><TrendCharts /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥ {{ formatMoney(weekTotal) }}</div>
            <div class="kpi-label">近7日营收</div>
            <div class="kpi-sub">日均 ¥{{ formatMoney(weekAvg) }} · {{ trendData.length > 0 ? trendData[0].revenue > trendData[trendData.length-1]?.revenue ? '↑' : '↓' : '' }} 趋势</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：流水 + 图表 + 状态 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <!-- 最近流水 -->
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>最近储值流水</span><el-button text type="primary" size="small" @click="$router.push('/wallet/transactions')">查看全部</el-button></div>
          </template>
          <el-table :data="recentTxns" stripe size="small" :max-height="320">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="会员" prop="memberName" min-width="90" show-overflow-tooltip />
            <el-table-column label="类型" width="68">
              <template #default="{ row }">
                <el-tag :type="typeTagType(row.type)" effect="dark" size="small">{{ row.typeLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="金额" align="right" min-width="120">
              <template #default="{ row }">
                <span :class="isIncome(row.type) ? 'text-success' : 'text-danger'">
                  {{ isIncome(row.type) ? '+' : '-' }}¥{{ formatMoney(row.amount) }}
                </span>
                <el-tag v-if="row.giftAmount && Number(row.giftAmount) > 0" size="small" type="warning" effect="plain" style="margin-left:4px">送 ¥{{ formatMoney(row.giftAmount) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!recentTxns.length" class="empty">暂无流水</div>
        </el-card>
      </el-col>

      <!-- 营收趋势图 -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>营收趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                <el-radio-button :value="7">近7天</el-radio-button>
                <el-radio-button :value="30">近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-if="trendData.length" ref="chartRef" class="chart"></div>
          <div v-else class="empty chart-empty">
            <el-empty description="暂无趋势数据" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四行：系统状态 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>系统状态</span></div>
          </template>
          <ul class="status-list">
            <li v-for="m in modules" :key="m.id">
              <span class="dot ok"></span>{{ m.id }} {{ m.name }}<span class="tag ok">已上线</span>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Money, UserFilled, Wallet, Coin, ChatDotRound, WarningFilled, TrendCharts } from '@element-plus/icons-vue'
import { memberApi, bookingApi, careApi, stockApi, reportApi } from '@/api/basic'

const stats = ref<any>({})
const recentTxns = ref<any[]>([])
const bookingStats = ref<any>({})
const careStats = ref<any>({})
const alertStats = ref<any>({})
const trendData = ref<any[]>([])
const trendDays = ref(7)
const chartRef = ref<HTMLDivElement>()
let chartInst: any = null

const modules = [
  { id: 'M01', name: '登录认证' },
  { id: 'M02', name: '基础数据' },
  { id: 'M03', name: '会员管理' },
  { id: 'M04', name: '储值管理' },
  { id: 'M05', name: '套餐次卡' },
  { id: 'M06', name: '预约管理' },
  { id: 'M07', name: '收银结算' },
  { id: 'M08', name: '库存管理' },
  { id: 'M09', name: '员工提成' },
  { id: 'M10', name: '营销工具' },
  { id: 'M11', name: '数据报表' },
  { id: 'M12', name: '系统设置' },
]

const weekTotal = ref(0)
const weekAvg = ref(0)

const formatMoney = (v: any) => Number(v || 0).toFixed(2)
const formatDate = (v: any) => v ? new Date(v).toLocaleString('zh-CN') : '-'

const typeTagType = (t: number) => {
  const m: Record<number, string> = { 1: 'success', 2: 'warning', 3: 'info', 4: 'primary', 5: 'danger', 6: 'success' }
  return m[t] || ''
}
const isIncome = (t: number) => t === 1 || t === 3 || t === 4 || t === 6

const loadStats = async () => {
  try {
    const res: any = await memberApi.stats()
    stats.value = res?.data || {}
    recentTxns.value = res?.data?.recentTxns || []
  } catch {
    stats.value = {}
    recentTxns.value = []
  }
}

const loadBookingStats = async () => {
  try {
    const res: any = await bookingApi.stats()
    bookingStats.value = res?.data || {}
  } catch {
    bookingStats.value = {}
  }
}

const loadCareStats = async () => {
  try {
    const res: any = await careApi.pendingCount()
    careStats.value = res?.data || {}
  } catch {
    careStats.value = {}
  }
}

const loadAlertStats = async () => {
  try {
    const res: any = await stockApi.alertStats()
    alertStats.value = res?.data || {}
  } catch {
    alertStats.value = {}
  }
}

const loadTrend = async () => {
  try {
    const res: any = await reportApi.salesDaily(trendDays.value)
    const data: any[] = res?.data || []
    trendData.value = data.reverse()
    const total = data.reduce((s: number, r: any) => s + Number(r.revenue || 0), 0)
    weekTotal.value = total
    weekAvg.value = data.length ? total / data.length : 0
    await nextTick()
    renderChart()
  } catch {
    trendData.value = []
  }
}

async function renderChart() {
  if (!chartRef.value || !trendData.value.length) return
  try {
    const echarts = await import('echarts/core')
    const [LineChart, GridComponent, TooltipComponent, TitleComponent] = await Promise.all([
      import('echarts/charts').then(m => m.LineChart),
      import('echarts/components').then(m => m.GridComponent),
      import('echarts/components').then(m => m.TooltipComponent),
      import('echarts/components').then(m => m.TitleComponent),
    ])
    const { CanvasRenderer } = await import('echarts/renderers')
    echarts.use([LineChart, GridComponent, TooltipComponent, TitleComponent, CanvasRenderer])

    if (chartInst) { chartInst.dispose(); chartInst = null }
    chartInst = echarts.init(chartRef.value)

    const dates = trendData.value.map((r: any) => r.date?.slice(5) || '')
    const gmvs = trendData.value.map((r: any) => Number(r.revenue || 0))
    const refunds = trendData.value.map((r: any) => Number(r.refundAmount || 0))

    chartInst.setOption({
      tooltip: { trigger: 'axis', formatter: (p: any[]) => {
        let html = `<b>${p[0].name}</b><br/>`
        p.forEach((s: any) => { html += `${s.marker} ${s.seriesName}: ¥${Number(s.value).toFixed(2)}<br/>` })
        return html
      }},
      grid: { left: '3%', right: '4%', bottom: '3%', top: '10px', containLabel: true },
      xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: '#e4e7ed' } }, axisLabel: { color: '#606266', fontSize: 11 } },
      yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { color: '#909399', fontSize: 11 } },
      series: [
        { name: '营收', type: 'line', data: gmvs, smooth: true, lineStyle: { color: '#409eff', width: 2 }, itemStyle: { color: '#409eff' }, areaStyle: { color: 'rgba(64,158,255,0.12)' } },
        { name: '退款', type: 'line', data: refunds, smooth: true, lineStyle: { color: '#f56c6c', width: 1.5 }, itemStyle: { color: '#f56c6c' } },
      ],
    }, true)
  } catch (e) { console.warn('[dashboard chart]', e) }
}

const onResize = () => chartInst?.resize()
window.addEventListener('resize', onResize)

onMounted(() => {
  loadStats()
  loadBookingStats()
  loadCareStats()
  loadAlertStats()
  loadTrend()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chartInst?.dispose()
})
</script>

<style scoped>
.dashboard { padding: 0; }

.quick-nav {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.kpi-card {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  position: relative;
  overflow: visible;
}
.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.mini-kpi { display: flex; align-items: center; padding: 16px; }
.mini-left { flex: 1; }
.mini-right { flex-shrink: 0; }

.kpi-icon {
  position: absolute; top: 16px; right: 16px;
  font-size: 32px; opacity: 0.15;
}
.kpi-body { padding-right: 36px; }
.kpi-value {
  font-size: 26px; font-weight: 700; color: #1f2937; line-height: 1.2;
}
.kpi-label { font-size: 14px; color: #606266; margin-top: 4px; font-weight: 500; }
.kpi-sub { font-size: 12px; color: #909399; margin-top: 4px; }

.kpi-green .kpi-value { color: #67c23a; }
.kpi-blue .kpi-value { color: #409eff; }
.kpi-orange .kpi-value { color: #e6a23c; }
.kpi-red .kpi-value { color: #f56c6c; }
.kpi-teal .kpi-value { color: #13c2c2; }
.kpi-purple .kpi-value { color: #722ed1; }
.kpi-gold .kpi-value { color: #fa8c16; }

.checkin-ring {
  width: 64px; height: 64px; border-radius: 50%;
  background: conic-gradient(#409eff var(--pct, 0%), #f0f0f0 0%);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  position: relative;
}
.checkin-ring::before {
  content: ''; position: absolute; inset: 6px;
  background: #fff; border-radius: 50%;
}
.checkin-ring span { position: relative; z-index: 1; font-size: 13px; font-weight: 700; color: #409eff; line-height: 1.2; }
.ring-label { font-size: 10px !important; font-weight: 400 !important; color: #909399 !important; }

.alert-chip {
  display: inline-block; padding: 1px 6px; border-radius: 8px; font-size: 11px; margin-right: 4px;
}
.alert-chip.low { background: #fef0f0; color: #f56c6c; }
.alert-chip.exp { background: #fff7e6; color: #e6a23c; }
.alert-chip.expired { background: #f56c6c; color: #fff; }

.alert-dot {
  position: absolute; top: 8px; right: 8px;
  width: 10px; height: 10px; border-radius: 50%;
  background: #f56c6c; box-shadow: 0 0 0 3px rgba(245,108,108,0.3);
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(245,108,108,0.3); }
  50% { box-shadow: 0 0 0 6px rgba(245,108,108,0.1); }
}

.card-header {
  font-weight: 600; display: flex; justify-content: space-between; align-items: center;
}

.text-success { color: #67c23a; font-weight: 600; }
.text-danger { color: #f56c6c; font-weight: 600; }

.empty { padding: 30px 0; text-align: center; color: #c0c4cc; font-size: 13px; }
.chart-empty { padding: 0; }

.chart { width: 100%; height: 240px; }

.status-list { list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: 4px 16px; }
.status-list li {
  display: flex; align-items: center; gap: 8px; padding: 6px 0;
  font-size: 13px; color: #4b5563; min-width: 160px;
}
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot.ok { background: #67c23a; box-shadow: 0 0 0 3px rgba(103,194,58,0.2); }
.tag {
  margin-left: auto; padding: 2px 8px; border-radius: 10px;
  font-size: 11px; background: #f0f9eb; color: #67c23a;
}
</style>
