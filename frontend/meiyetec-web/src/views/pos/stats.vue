<template>
  <div class="stats-page">
    <!-- 顶部 Banner -->
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><DataAnalysis /></el-icon>
        <div>
          <h2>
            收银统计
            <el-tag v-if="isDemo" type="warning" size="small" effect="dark" style="margin-left: 8px; vertical-align: middle">
              演示数据
            </el-tag>
          </h2>
          <p>
            实时查看营收数据 · 支持按时间维度筛选
            <span v-if="loading" class="loading-tag">加载中...</span>
            <span v-else-if="isDemo" class="loading-tag">数据库暂无订单，已注入演示数据</span>
          </p>
        </div>
      </div>
      <div class="banner-right">
        <el-radio-group v-model="period" @change="onPeriodChange">
          <el-radio-button :value="'today'">今日</el-radio-button>
          <el-radio-button :value="'7d'">近7天</el-radio-button>
          <el-radio-button :value="'30d'">近30天</el-radio-button>
          <el-radio-button :value="'custom'">自定义</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="period === 'custom'"
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          size="default"
          style="width: 260px"
          @change="loadAll"
        />
        <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 核心 KPI -->
    <div class="kpi-row">
      <div class="kpi-card kpi-revenue">
        <div class="kpi-icon"><el-icon :size="32"><Money /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">
            {{ periodLabel }}营收
            <span class="trend-chip" v-if="kpi.trend.revenue !== null"
                  :class="kpi.trend.revenue > 0 ? 'up' : kpi.trend.revenue < 0 ? 'down' : 'flat'">
              {{ kpi.trend.revenue > 0 ? '↑' : kpi.trend.revenue < 0 ? '↓' : '−' }}
              {{ Math.abs(kpi.trend.revenue) }}%
              <small>vs {{ kpi.prevLabel }}</small>
            </span>
          </div>
          <div class="kpi-num">¥{{ kpi.revenue.toFixed(2) }}</div>
          <div class="kpi-sub">
            订单数 {{ kpi.paidCount }} · 客单价 ¥{{ kpi.avgPrice.toFixed(2) }}
          </div>
        </div>
      </div>
      <div class="kpi-card kpi-count">
        <div class="kpi-icon"><el-icon :size="32"><Document /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">
            {{ periodLabel }}订单
            <span class="trend-chip" v-if="kpi.trend.paidCount !== null"
                  :class="kpi.trend.paidCount > 0 ? 'up' : kpi.trend.paidCount < 0 ? 'down' : 'flat'">
              {{ kpi.trend.paidCount > 0 ? '↑' : kpi.trend.paidCount < 0 ? '↓' : '−' }}
              {{ Math.abs(kpi.trend.paidCount) }}%
              <small>vs {{ kpi.prevLabel }}</small>
            </span>
          </div>
          <div class="kpi-num">{{ kpi.totalCount }}</div>
          <div class="kpi-sub">
            已支付 {{ kpi.paidCount }} · 进行中 {{ kpi.pendingCount }} · 已退款 {{ kpi.refundedCount }}
          </div>
        </div>
      </div>
      <div class="kpi-card kpi-avg">
        <div class="kpi-icon"><el-icon :size="32"><Wallet /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">
            客单价
            <span class="trend-chip" v-if="kpi.trend.avgPrice !== null"
                  :class="kpi.trend.avgPrice > 0 ? 'up' : kpi.trend.avgPrice < 0 ? 'down' : 'flat'">
              {{ kpi.trend.avgPrice > 0 ? '↑' : kpi.trend.avgPrice < 0 ? '↓' : '−' }}
              {{ Math.abs(kpi.trend.avgPrice) }}%
              <small>vs {{ kpi.prevLabel }}</small>
            </span>
          </div>
          <div class="kpi-num">¥{{ kpi.avgPrice.toFixed(2) }}</div>
          <div class="kpi-sub">
            单笔最高 ¥{{ kpi.maxOrder.toFixed(2) }} · 最低 ¥{{ kpi.minOrder.toFixed(2) }}
          </div>
        </div>
      </div>
      <div class="kpi-card kpi-refund">
        <div class="kpi-icon"><el-icon :size="32"><RefreshLeft /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">
            退款
            <span class="trend-chip" v-if="kpi.trend.refundAmount !== null"
                  :class="kpi.trend.refundAmount > 0 ? 'down-warn' : kpi.trend.refundAmount < 0 ? 'up-good' : 'flat'">
              {{ kpi.trend.refundAmount > 0 ? '↑' : kpi.trend.refundAmount < 0 ? '↓' : '−' }}
              {{ Math.abs(kpi.trend.refundAmount) }}%
              <small>vs {{ kpi.prevLabel }}</small>
            </span>
          </div>
          <div class="kpi-num">¥{{ kpi.refundAmount.toFixed(2) }}</div>
          <div class="kpi-sub">
            退款率 {{ kpi.refundRate }}% · 共 {{ kpi.refundedCount }} 单
          </div>
        </div>
      </div>
    </div>

    <div class="grid-row">
      <!-- 左：每日趋势 + 支付方式 -->
      <div class="left-col">
        <!-- 趋势图（简易柱状图）-->
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              <el-icon><TrendCharts /></el-icon>
              <span>{{ periodLabel }}每日营收趋势</span>
              <div class="chart-legend">
                <span class="legend-item"><span class="dot dot-rev"></span>营收</span>
                <span class="legend-item"><span class="dot dot-cnt"></span>订单数</span>
              </div>
            </div>
          </template>
          <div v-if="trend.length" class="bar-chart">
            <div v-for="(d, idx) in trend" :key="idx" class="bar-item">
              <div class="bar-value">¥{{ d.amount.toFixed(0) }}</div>
              <div class="bar-count">{{ d.count }}单</div>
              <div class="bar-wrap">
                <div class="bar-sub" :style="{ height: maxTrendCount ? (d.count / maxTrendCount * 100 + '%') : '0%' }"></div>
                <div class="bar" :style="{ height: maxTrend ? (d.amount / maxTrend * 100 + '%') : '0%' }"></div>
              </div>
              <div class="bar-label">{{ d.date }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>

        <!-- 支付方式分布 -->
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-title">
              <el-icon><CreditCard /></el-icon>
              <span>支付方式分布</span>
            </div>
          </template>
          <div v-if="paymentMethods.length" class="method-list">
            <div v-for="(m, idx) in paymentMethods" :key="idx" class="method-item">
              <div class="method-info">
                <span class="method-icon" :style="{ background: m.color }">{{ m.icon }}</span>
                <div>
                  <div class="method-name">{{ m.label }}</div>
                  <small>{{ m.count }} 笔</small>
                </div>
              </div>
              <div class="method-right">
                <div class="method-amount">¥{{ m.amount.toFixed(2) }}</div>
                <div class="method-bar-wrap">
                  <div class="method-bar" :style="{ width: m.percent + '%', background: m.color }"></div>
                </div>
                <small class="method-percent">{{ m.percent }}%</small>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无支付记录" />
        </el-card>
      </div>

      <!-- 右：热门服务 + 状态饼图 -->
      <div class="right-col">
        <!-- 状态分布 -->
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              <el-icon><PieChart /></el-icon>
              <span>订单状态分布</span>
            </div>
          </template>
          <div v-if="statusDist.length" class="status-list">
            <div v-for="(s, idx) in statusDist" :key="idx" class="status-item">
              <div class="status-bar-wrap">
                <div class="status-bar" :style="{ width: s.percent + '%', background: s.color }"></div>
              </div>
              <div class="status-info">
                <el-tag :type="statusType(s.value)" size="small">{{ s.label }}</el-tag>
                <b>{{ s.count }} 笔</b>
              </div>
              <small class="status-percent">{{ s.percent }}%</small>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>

        <!-- 热门服务 Top5 -->
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-title">
              <el-icon><Trophy /></el-icon>
              <span>热门服务 Top5</span>
            </div>
          </template>
          <div v-if="hotServices.length" class="rank-list">
            <div v-for="(s, idx) in hotServices" :key="idx" class="rank-item">
              <span class="rank-no" :class="`r${idx + 1}`">{{ idx + 1 }}</span>
              <div class="rank-info">
                <div class="rank-name">{{ s.name }}</div>
                <small>{{ s.count }} 次 · 营收 ¥{{ s.amount.toFixed(2) }}</small>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </div>
    </div>

    <!-- 时间筛选明细表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-title">
          <el-icon><DataLine /></el-icon>
          <span>每日明细（{{ periodLabel }} · 共 {{ trend.length }} 天）</span>
          <el-button type="primary" link size="small" @click="exportCSV" :icon="Download" style="margin-left: auto">导出 CSV</el-button>
        </div>
      </template>
      <el-table :data="trend" border size="default" empty-text="暂无数据">
        <el-table-column type="index" label="#" width="55" align="center" />
        <el-table-column prop="date" label="日期" min-width="140" />
        <el-table-column label="订单数" width="100" align="center">
          <template #default="{ row }">{{ row.count }}</template>
        </el-table-column>
        <el-table-column label="营收" min-width="140" align="right">
          <template #default="{ row }">¥{{ row.amount.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="退款" min-width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.refund > 0" class="refund">¥{{ row.refund.toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="客单价" min-width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.count > 0">¥{{ (row.amount / row.count).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" min-width="200">
          <template #default="{ row }">
            <span v-for="(m, i) in row.methods" :key="i" class="method-tag">
              {{ m.label }} {{ m.count }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis, Refresh, Money, Document, Wallet, RefreshLeft,
  TrendCharts, CreditCard, PieChart, Trophy, DataLine, Download
} from '@element-plus/icons-vue'
import { orderApi } from '@/api/pos'

// ========== 状态 ==========
const period = ref<'today' | '7d' | '30d' | 'custom'>('today')
const dateRange = ref<[string, string] | null>(null)  // 自定义时段
const allOrders = ref<any[]>([])
const loading = ref(false)

const STATUS: any = { 1: '进行中', 2: '已支付', 3: '已退款', 4: '已撤销' }
const STATUS_COLORS: any = { 1: '#f59e0b', 2: '#10b981', 3: '#909399', 4: '#ef4444' }
const METHOD: any = {
  cash: { label: '现金', color: '#10b981', icon: '现' },
  wallet: { label: '储值', color: '#3b82f6', icon: '储' },
  wechat: { label: '微信', color: '#22c55e', icon: '微' },
  alipay: { label: '支付宝', color: '#06b6d4', icon: '宝' },
  unionpay: { label: '银联', color: '#6366f1', icon: '银' },
  e_cny_soft: { label: '数字人民币', color: '#a855f7', icon: '数' },
  e_cny_hard: { label: '硬件钱包', color: '#a855f7', icon: '硬' },
  points: { label: '积分', color: '#f97316', icon: '分' },
  coupon: { label: '优惠券', color: '#ec4899', icon: '券' }
}
const statusLabel = (s: number) => STATUS[s] || '未知'
const statusType = (s: number) => ({ 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }[s] || '')

const periodLabel = computed(() => ({ today: '今日', '7d': '近7天', '30d': '近30天', custom: '自定义时段' }[period.value]))

// ========== 日期范围（当前 + 上周期对比） ==========
// 返回 { curStart, curEnd, prevStart, prevEnd, days }
const dateRanges = computed<{ curStart: Date; curEnd: Date; prevStart: Date; prevEnd: Date; days: number }>(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const tomorrow = new Date(today.getTime() + 86400000)
  let curStart = today, curEnd = tomorrow, days = 1

  if (period.value === '7d') {
    curStart = new Date(today.getTime() - 6 * 86400000); days = 7
  } else if (period.value === '30d') {
    curStart = new Date(today.getTime() - 29 * 86400000); days = 30
  } else if (period.value === 'custom' && dateRange.value && dateRange.value.length === 2) {
    curStart = new Date(dateRange.value[0])
    curEnd = new Date(new Date(dateRange.value[1]).getTime() + 86400000)
    days = Math.max(1, Math.round((curEnd.getTime() - curStart.getTime()) / 86400000))
  }
  // 上周期 = 同长度，前推 days 天
  const prevEnd = new Date(curStart.getTime())
  const prevStart = new Date(curStart.getTime() - days * 86400000)
  return { curStart, curEnd, prevStart, prevEnd, days }
})

// 切换时段时：若切到非 custom 清空自定义日期
const onPeriodChange = () => {
  if (period.value !== 'custom') {
    dateRange.value = null
    loadAll()
  }
}

// ========== 数据加载 ==========
const loadAll = async () => {
  loading.value = true
  try {
    // 后端 /orders/page 已批量补全 items + payments（IN 查询），无需前端并发拉详情
    const res: any = await orderApi.page({ pageNum: 1, pageSize: 200 })
    const real = res.data?.records || res.data?.list || res.records || res.list || []
    if (real.length === 0) {
      // 数据库暂无订单 → 注入演示数据（不污染后端）
      allOrders.value = generateMockOrders()
      isDemo.value = true
    } else {
      allOrders.value = real
      isDemo.value = false
    }
  } catch (e: any) {
    // 接口失败也走演示数据，避免页面空白
    allOrders.value = generateMockOrders()
    isDemo.value = true
    ElMessage.warning('后端数据加载失败，已显示演示数据：' + (e.message || ''))
  } finally {
    loading.value = false
  }
}

// ========== 演示数据模式 ==========
const isDemo = ref(false)
const DEMO_METHODS = ['wechat', 'alipay', 'cash', 'wallet', 'unionpay', 'points']
const DEMO_METHOD_WEIGHTS = [40, 35, 8, 7, 5, 5]   // 微信 40% 支付宝 35%...
const DEMO_SERVICES = [
  { name: '经典剪发',      price: 80,   weight: 25 },
  { name: '精剪造型',      price: 158,  weight: 18 },
  { name: '染发护理',      price: 480,  weight: 15 },
  { name: '烫发护理',      price: 580,  weight: 10 },
  { name: '深层护理 SPA',  price: 298,  weight: 12 },
  { name: '面部护理',      price: 380,  weight: 8 },
  { name: '美甲手护',      price: 128,  weight: 7 },
  { name: '肩颈按摩 60min',price: 168,  weight: 5 }
]
const DEMO_MEMBERS = [
  { name: '李小姐', phone: '138****1234' },
  { name: '王女士', phone: '139****5678' },
  { name: '张总',   phone: '186****9012' },
  { name: '陈先生', phone: '137****3456' },
  { name: '刘女士', phone: '135****7890' },
  { name: '黄小姐', phone: '188****2345' },
  { name: '赵先生', phone: '189****6789' },
  { name: '吴女士', phone: '186****0123' },
  { name: '周小姐', phone: '137****4567' },
  { name: '散客',   phone: '' }
]
const pickWeighted = <T extends { weight: number }>(arr: T[]): T => {
  const total = arr.reduce((s, x) => s + x.weight, 0)
  let r = Math.random() * total
  for (const x of arr) { r -= x.weight; if (r <= 0) return x }
  return arr[0]
}
// 把加权数组转为累计权重后二分查找
const pickFromWeighted = (weights: number[]): number => {
  const total = weights.reduce((s, w) => s + w, 0)
  let r = Math.random() * total
  for (let i = 0; i < weights.length; i++) { r -= weights[i]; if (r <= 0) return i }
  return weights.length - 1
}

const fmt = (d: Date) => d.toISOString().slice(0, 19).replace('T', ' ')
const dateKey = (d: Date) => d.toISOString().slice(0, 10)
const today0 = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d }

/**
 * 生成最近 30 天的演示订单
 * - 周末高峰 +30%
 * - 晚高峰 18-21 +50%
 * - 工作日 9-11 / 18-21 双峰
 * - 退款率 ~5%
 */
const generateMockOrders = (): any[] => {
  const orders: any[] = []
  const startDay = today0()
  let idCounter = 100000

  for (let dayOffset = 29; dayOffset >= 0; dayOffset--) {
    const day = new Date(startDay.getTime() - dayOffset * 86400000)
    const dow = day.getDay()          // 0=Sun 6=Sat
    const isWeekend = dow === 0 || dow === 6
    const baseDaily = isWeekend ? 16 : 10  // 周末 16 单 / 工作日 10 单
    const dailyCount = baseDaily + Math.floor(Math.random() * 5) - 2

    for (let i = 0; i < dailyCount; i++) {
      // 随机生成时间（加权：晚高峰密集）
      const hourRand = Math.random()
      let hour: number
      if (hourRand < 0.45) hour = 9 + Math.floor(Math.random() * 3)             // 9-11
      else if (hourRand < 0.75) hour = 14 + Math.floor(Math.random() * 4)        // 14-17
      else hour = 18 + Math.floor(Math.random() * 4)                             // 18-21
      const minute = Math.floor(Math.random() * 60)
      const second = Math.floor(Math.random() * 60)
      const createTime = new Date(day)
      createTime.setHours(hour, minute, second)

      // 支付时间：5 秒 ~ 8 分钟后
      const payDelaySec = 5 + Math.floor(Math.random() * 480)
      const payTime = new Date(createTime.getTime() + payDelaySec * 1000)

      // 选 1~3 个服务
      const svcCount = Math.random() < 0.65 ? 1 : (Math.random() < 0.85 ? 2 : 3)
      const items: any[] = []
      let totalAmount = 0
      const usedSvc = new Set<string>()
      for (let k = 0; k < svcCount; k++) {
        let svc = pickWeighted(DEMO_SERVICES)
        let safety = 0
        while (usedSvc.has(svc.name) && safety < 5) { svc = pickWeighted(DEMO_SERVICES); safety++ }
        usedSvc.add(svc.name)
        const qty = 1
        const unitPrice = svc.price
        const itemTotal = unitPrice * qty
        items.push({
          serviceName: svc.name,
          quantity: qty,
          unitPrice,
          totalPrice: itemTotal,
          finalPrice: itemTotal,
          discountAmount: 0
        })
        totalAmount += itemTotal
      }

      // 5% 概率退款，2% 概率撤销
      const roll = Math.random()
      const status = roll < 0.05 ? 3 : roll < 0.07 ? 4 : 2
      const isWalkin = Math.random() < 0.15
      const member = isWalkin ? DEMO_MEMBERS[9] : DEMO_MEMBERS[Math.floor(Math.random() * 9)]

      // 折扣：10% 概率打 9 折
      const hasDiscount = Math.random() < 0.1
      const discountAmount = hasDiscount ? Math.round(totalAmount * 0.1) : 0
      const finalAmount = totalAmount - discountAmount
      const paidAmount = status === 2 ? finalAmount : (status === 3 ? finalAmount : 0)
      const refundAmount = status === 3 ? Math.round(finalAmount * (0.5 + Math.random() * 0.5)) : 0

      // 支付方式
      const methodIdx = pickFromWeighted(DEMO_METHOD_WEIGHTS)
      const method = DEMO_METHODS[methodIdx]

      const orderNo = `PO${dateKey(day).replace(/-/g, '')}${String(++idCounter).padStart(6, '0').slice(-6)}`
      orders.push({
        id: idCounter,
        orderNo,
        memberName: member.name,
        memberPhone: member.phone,
        totalAmount,
        discountAmount,
        finalAmount,
        paidAmount,
        refundAmount,
        status,
        createTime: fmt(createTime),
        payTime: status >= 2 ? fmt(payTime) : null,
        refundTime: status === 3 ? fmt(new Date(payTime.getTime() + 1800 * 1000)) : null,
        refundReason: status === 3 ? '客户原因' : null,
        cashierName: ['小王', '小李', '小张'][Math.floor(Math.random() * 3)],
        items,
        payments: status >= 2 ? [{ method, amount: paidAmount, status: 1, payTime: fmt(payTime) }] : []
      })
    }
  }
  return orders
}

// ========== 周期筛选（使用 dateRanges） ==========
const filterByRange = (start: Date, end: Date) => (o: any) => {
  const t = o.createTime || o.payTime
  if (!t) return false
  const d = new Date(t)
  return d >= start && d < end
}

const filteredOrders = computed(() => {
  const { curStart, curEnd } = dateRanges.value
  return allOrders.value.filter(filterByRange(curStart, curEnd))
})

const prevOrders = computed(() => {
  const { prevStart, prevEnd } = dateRanges.value
  return allOrders.value.filter(filterByRange(prevStart, prevEnd))
})

// ========== 计算某个范围的 KPI（复用） ==========
const computeKpi = (arr: any[]) => {
  const paid = arr.filter(o => o.status === 2)
  const refunded = arr.filter(o => o.status === 3)
  const revenue = paid.reduce((s, o) => s + Number(o.paidAmount || 0), 0)
  const refundAmount = refunded.reduce((s, o) => s + Number(o.refundAmount || 0), 0)
  return {
    revenue,
    paidCount: paid.length,
    totalCount: arr.length,
    pendingCount: arr.filter(o => o.status === 1).length,
    refundedCount: refunded.length,
    refundAmount,
    refundRate: (paid.length + refunded.length) > 0
      ? (refunded.length / (paid.length + refunded.length) * 100)
      : 0,
    avgPrice: paid.length ? revenue / paid.length : 0,
    maxOrder: paid.length ? Math.max(...paid.map(o => Number(o.paidAmount || 0))) : 0,
    minOrder: paid.length ? Math.min(...paid.map(o => Number(o.paidAmount || 0))) : 0
  }
}

// 计算环比 pct（百分比，保留 1 位小数；返回 number|null）
const trendPct = (cur: number, prev: number): number | null => {
  if (prev === 0) return cur > 0 ? 100 : null
  return Number(((cur - prev) / prev * 100).toFixed(1))
}

// ========== KPI（当前 + 上周期） ==========
const kpi = computed(() => {
  const cur = computeKpi(filteredOrders.value)
  const prev = computeKpi(prevOrders.value)
  return {
    ...cur,
    refundRate: cur.refundRate.toFixed(1),
    // 环比
    trend: {
      revenue: trendPct(cur.revenue, prev.revenue),
      paidCount: trendPct(cur.paidCount, prev.paidCount),
      avgPrice: trendPct(cur.avgPrice, prev.avgPrice),
      refundAmount: trendPct(cur.refundAmount, prev.refundAmount)
    },
    prevLabel: period.value === 'today' ? '昨日'
             : period.value === 'custom' ? '上同期'
             : '上周期'
  }
})

// ========== 每日趋势 ==========
const trend = computed(() => {
  const map = new Map<string, any>()
  filteredOrders.value.forEach(o => {
    const date = (o.createTime || '').slice(0, 10)
    if (!date) return
    if (!map.has(date)) {
      map.set(date, { date, count: 0, amount: 0, refund: 0, methods: {} as any })
    }
    const d = map.get(date)
    d.count++
    // 已支付：累计营收；已退款：累计退款额（不算入营收避免重复）
    if (o.status === 2) d.amount += Number(o.paidAmount || 0)
    if (o.status === 3) d.refund += Number(o.refundAmount || 0)
  })
  return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date))
})

const maxTrend = computed(() => Math.max(1, ...trend.value.map(t => t.amount)))
const maxTrendCount = computed(() => Math.max(1, ...trend.value.map(t => t.count)))

// ========== 支付方式分布 ==========
const paymentMethods = computed(() => {
  const map = new Map<string, { amount: number; count: number }>()
  filteredOrders.value.forEach(o => {
    if (o.status === 2 || o.status === 3) {
      const method = o.payments?.[0]?.method || 'cash'
      if (!map.has(method)) map.set(method, { amount: 0, count: 0 })
      const m = map.get(method)!
      m.amount += Number(o.paidAmount || 0)
      m.count++
    }
  })
  const total = Array.from(map.values()).reduce((s, m) => s + m.amount, 0)
  return Array.from(map.entries())
    .map(([k, v]) => ({
      method: k,
      label: METHOD[k]?.label || k,
      color: METHOD[k]?.color || '#999',
      icon: METHOD[k]?.icon || '?',
      amount: v.amount,
      count: v.count,
      percent: total > 0 ? (v.amount / total * 100).toFixed(1) : '0.0'
    }))
    .sort((a, b) => b.amount - a.amount)
})

// ========== 状态分布 ==========
const statusDist = computed(() => {
  const map = new Map<number, number>()
  filteredOrders.value.forEach(o => {
    const s = o.status || 0
    map.set(s, (map.get(s) || 0) + 1)
  })
  const total = filteredOrders.value.length
  return Array.from(map.entries())
    .map(([k, v]) => ({
      value: k,
      label: STATUS[k] || '未知',
      color: STATUS_COLORS[k] || '#999',
      count: v,
      percent: total > 0 ? (v / total * 100).toFixed(1) : '0.0'
    }))
    .sort((a, b) => b.count - a.count)
})

// ========== 热门服务 Top5 ==========
const hotServices = computed(() => {
  const map = new Map<string, { count: number; amount: number }>()
  filteredOrders.value.forEach(o => {
    if (!o.items) return
    o.items.forEach((i: any) => {
      const name = i.serviceName || `服务${i.serviceId}`
      if (!map.has(name)) map.set(name, { count: 0, amount: 0 })
      const m = map.get(name)!
      m.count += Number(i.quantity || 1)
      m.amount += Number(i.finalPrice || i.totalPrice || 0)
    })
  })
  return Array.from(map.entries())
    .map(([k, v]) => ({ name: k, ...v }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 5)
})

// ========== 导出 ==========
const exportCSV = () => {
  const headers = ['日期', '订单数', '营收(元)', '退款(元)', '客单价(元)']
  const rows = trend.value.map(d => [
    d.date, d.count, d.amount.toFixed(2), d.refund.toFixed(2),
    d.count > 0 ? (d.amount / d.count).toFixed(2) : '0.00'
  ])
  const csv = [headers, ...rows].map(r =>
    r.map(v => {
      const s = String(v ?? '')
      return s.includes(',') ? `"${s}"` : s
    }).join(',')
  ).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `收银统计_${period.value}_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('CSV 已导出')
}

onMounted(loadAll)
</script>

<style scoped>
.stats-page { padding: 16px; }

.banner {
  background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%);
  color: #fff; padding: 24px; border-radius: 10px;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.banner h2 { margin: 0; font-size: 22px; }
.banner p { margin: 4px 0 0; opacity: 0.85; font-size: 13px; }
.banner-right { display: flex; gap: 8px; align-items: center; }
:deep(.banner-right .el-radio-button__inner) { background: rgba(255,255,255,0.15); border-color: rgba(255,255,255,0.3); color: #fff; }
:deep(.banner-right .el-radio-button__inner:hover) { background: rgba(255,255,255,0.25); }
:deep(.banner-right .el-radio-button__original-radio:checked + .el-radio-button__inner) { background: #fff; color: #3b82f6; }

.loading-tag {
  display: inline-block; margin-left: 10px; padding: 2px 8px;
  background: rgba(255,255,255,0.2); border-radius: 10px; font-size: 12px;
}

/* KPI 卡片 */
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.kpi-card {
  background: #fff; border-radius: 10px; padding: 18px 20px;
  display: flex; gap: 16px; align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: transform 0.2s;
}
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.kpi-icon { width: 56px; height: 56px; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #fff; }
.kpi-revenue .kpi-icon { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
.kpi-count .kpi-icon   { background: linear-gradient(135deg, #3b82f6 0%, #06b6d4 100%); }
.kpi-avg .kpi-icon     { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.kpi-refund .kpi-icon  { background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%); }

.kpi-label { font-size: 13px; color: #909399; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.kpi-num   { font-size: 28px; font-weight: bold; margin: 4px 0; line-height: 1.2; color: #303133; }
.kpi-sub   { font-size: 12px; color: #909399; }

/* 环比 chip */
.trend-chip {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 1px 8px; border-radius: 10px;
  font-size: 11px; font-weight: 600;
  line-height: 1.4;
}
.trend-chip small { font-weight: 400; opacity: 0.75; margin-left: 3px; font-size: 10px; }
.trend-chip.up       { background: #ecfdf5; color: #059669; }
.trend-chip.down     { background: #fef2f2; color: #dc2626; }
.trend-chip.down-warn{ background: #fef2f2; color: #dc2626; }   /* 退款涨 = 坏 */
.trend-chip.up-good  { background: #ecfdf5; color: #059669; }   /* 退款降 = 好 */
.trend-chip.flat     { background: #f3f4f6; color: #6b7280; }

.grid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }

.card-title { display: flex; align-items: center; gap: 6px; font-weight: 600; }

/* 柱状图 */
.bar-chart { display: flex; gap: 8px; align-items: flex-end; height: 240px; padding: 0 8px; }
.bar-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 0; }
.bar-value { font-size: 11px; color: #1e40af; height: 18px; font-weight: 600; }
.bar-count { font-size: 10px; color: #6b7280; height: 14px; }
.bar-wrap { width: 100%; height: 160px; display: flex; align-items: flex-end; gap: 2px; justify-content: center; }
.bar {
  flex: 1; max-width: 18px;
  background: linear-gradient(180deg, #06b6d4 0%, #3b82f6 100%);
  border-radius: 4px 4px 0 0; transition: height 0.3s;
  min-height: 2px;
}
.bar-sub {
  flex: 1; max-width: 18px;
  background: linear-gradient(180deg, #cbd5e1 0%, #94a3b8 100%);
  border-radius: 4px 4px 0 0; transition: height 0.3s;
  min-height: 2px; opacity: 0.85;
}
.bar-label { font-size: 11px; color: #909399; transform: rotate(-30deg); white-space: nowrap; }

/* 图表图例 */
.chart-legend { display: flex; gap: 12px; margin-left: auto; font-size: 12px; color: #606266; font-weight: normal; }
.legend-item { display: inline-flex; align-items: center; gap: 4px; }
.legend-item .dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; }
.legend-item .dot-rev { background: linear-gradient(180deg, #06b6d4 0%, #3b82f6 100%); }
.legend-item .dot-cnt { background: linear-gradient(180deg, #cbd5e1 0%, #94a3b8 100%); }

/* 支付方式 */
.method-list { display: flex; flex-direction: column; gap: 14px; }
.method-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.method-info { display: flex; align-items: center; gap: 10px; min-width: 120px; }
.method-icon {
  width: 36px; height: 36px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: bold; font-size: 14px;
}
.method-name { font-size: 14px; font-weight: 500; }
.method-right { flex: 1; display: flex; align-items: center; gap: 8px; }
.method-amount { font-weight: bold; min-width: 90px; text-align: right; font-size: 14px; }
.method-bar-wrap { flex: 1; height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
.method-bar { height: 100%; transition: width 0.3s; border-radius: 3px; }
.method-percent { color: #909399; min-width: 40px; text-align: right; font-size: 12px; }

/* 状态分布 */
.status-list { display: flex; flex-direction: column; gap: 12px; }
.status-item { display: flex; align-items: center; gap: 12px; }
.status-bar-wrap { flex: 1; height: 10px; background: #f0f0f0; border-radius: 5px; overflow: hidden; }
.status-bar { height: 100%; transition: width 0.3s; }
.status-info { display: flex; align-items: center; gap: 8px; min-width: 110px; }
.status-percent { color: #909399; min-width: 40px; text-align: right; }

/* 热门服务 */
.rank-list { display: flex; flex-direction: column; gap: 10px; }
.rank-item { display: flex; align-items: center; gap: 12px; padding: 8px; border-radius: 8px; background: #fafbfc; }
.rank-no {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: #e0e0e0; color: #666; font-weight: bold; font-size: 13px;
}
.rank-no.r1 { background: linear-gradient(135deg, #ffd700, #ffaa00); color: #fff; }
.rank-no.r2 { background: linear-gradient(135deg, #c0c0c0, #888); color: #fff; }
.rank-no.r3 { background: linear-gradient(135deg, #cd7f32, #8b4513); color: #fff; }
.rank-info { flex: 1; }
.rank-name { font-size: 14px; font-weight: 500; }
.rank-info small { color: #909399; font-size: 12px; }

.method-tag { display: inline-block; padding: 2px 8px; background: #f0f0f0; border-radius: 4px; font-size: 12px; margin-right: 4px; margin-bottom: 2px; }
.refund { color: #ef4444; }

/* 响应式 */
@media (max-width: 1200px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .grid-row { grid-template-columns: 1fr; }
}
</style>
