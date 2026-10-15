<template>
  <div class="care-overview-page">
    <!-- 顶部工具栏：⑨ 门店/客服筛选 + ⑩ 刷新 + 自动刷新倒计时 -->
    <el-card class="toolbar" shadow="never">
      <div class="toolbar-left">
        <el-icon size="18" color="#606266"><Filter /></el-icon>
        <span class="toolbar-label">筛选</span>
        <el-select v-model="filter.storeId" placeholder="全部门店" clearable style="width:160px" size="default">
          <el-option label="总店" :value="1" />
          <el-option label="分店 A" :value="2" />
          <el-option label="分店 B" :value="3" />
        </el-select>
        <el-select v-model="filter.ownerId" placeholder="全部客服" clearable filterable style="width:160px" size="default">
          <el-option label="客服-小王" :value="101" />
          <el-option label="客服-小李" :value="102" />
        </el-select>
        <el-button :icon="Search" plain size="default" @click="loadAll">应用</el-button>
      </div>
      <div class="toolbar-right">
        <span class="auto-tip">
          <el-icon><Loading /></el-icon>
          {{ autoCountdown }}s 后自动刷新
        </span>
        <el-button :icon="Refresh" plain size="default" @click="loadAll">刷新</el-button>
      </div>
    </el-card>

    <!-- ① KPI 看板：图标 + 趋势小箭头 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="8" :md="4" :lg="4" v-for="k in kpiCards" :key="k.key">
        <el-card shadow="hover" class="kpi-card" :class="k.cls" @click="k.onClick">
          <div class="kpi-top">
            <el-icon class="kpi-icon" :size="28"><component :is="k.icon" /></el-icon>
            <span class="kpi-trend" :class="k.trendDir">
              <el-icon><component :is="k.trendDir === 'up' ? 'CaretTop' : k.trendDir === 'down' ? 'CaretBottom' : 'Minus'" /></el-icon>
              {{ k.trendText }}
            </span>
          </div>
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span class="kpi-suffix" v-if="k.suffix"> {{ k.suffix }}</span></div>
          <div class="kpi-extra">{{ k.extra }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ⑥ 「近 7 天健康度」综合评分 -->
    <el-card class="block-card health-card" :class="healthCls">
      <div v-if="healthPending" class="health-pending">
        <div class="health-icon"><el-icon :size="32"><InfoFilled /></el-icon></div>
        <div class="health-info">
          <div class="health-pending-title">待评估</div>
          <div class="health-pending-sub">接入运营数据后，会自动从 4 个维度（回访及时率 / 工单解决率 / 客户满意度 / 客服响应）综合打分</div>
        </div>
        <div class="health-pending-status">
          <div class="health-pending-dot pending"></div>
          <span>等待数据接入</span>
        </div>
      </div>
      <div v-else class="health-row">
        <div class="health-main">
          <div class="health-icon"><el-icon :size="32"><component :is="healthIcon" /></el-icon></div>
          <div class="health-info">
            <div class="health-label">近 7 天运营健康度</div>
            <div class="health-score">
              <span class="num">{{ healthScore }}</span>
              <span class="unit">/ 100</span>
              <el-tag size="default" :type="healthTagType" effect="dark" class="health-tag">{{ healthLevel }}</el-tag>
            </div>
          </div>
        </div>
        <div class="health-divider"></div>
        <div class="health-bars">
          <div v-for="m in healthMetrics" :key="m.label" class="hb">
            <div class="hb-top">
              <span class="hb-label">{{ m.label }}</span>
              <span class="hb-val">{{ m.value }}</span>
            </div>
            <div class="hb-track">
              <div class="hb-fill" :class="m.cls" :style="{ width: m.score + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 主体：左侧 8 列（趋势 + 漏斗 + 时效矩阵），右侧 4 列（今日待办 + 低分评价 + 客服消息） -->
    <el-row :gutter="12">
      <!-- 左侧 -->
      <el-col :xs="24" :lg="16">
        <!-- ③ 近 7 天趋势 柱图（纯 CSS） + ⑤ 本周 vs 上周对比 + ② 迷你指标 -->
        <el-card class="block-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><DataLine /></el-icon> 近 7 天运营趋势</span>
              <el-radio-group v-model="trendMetric" size="small">
                <el-radio-button value="follow">回访</el-radio-button>
                <el-radio-button value="ticket">工单</el-radio-button>
                <el-radio-button value="review">评价</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div class="trend-row">
            <!-- 左：柱图 + 上周对比虚线 -->
            <div class="trend-chart">
              <div class="bar-chart">
                <div v-for="d in trendData" :key="d.date" class="bar-col">
                  <div class="bar-value">{{ d[trendMetric] }}</div>
                  <div class="bar-wrap">
                    <!-- 上周对比 虚线 -->
                    <div v-if="d.prevValue !== null && d.prevValue !== undefined"
                         class="prev-mark"
                         :style="{ bottom: prevHeight(d.prevValue) + '%' }"
                         :title="`上周同日 ${d.prevValue}`"></div>
                    <div class="bar" :style="{ height: barHeight(d[trendMetric]) + '%' }">
                      <span v-if="d.isToday" class="today-pill">今天</span>
                    </div>
                  </div>
                  <div class="bar-label" :class="{ today: d.isToday }">{{ d.dateLabel }}</div>
                </div>
              </div>
              <div class="chart-legend">
                <span><i class="dot solid"></i> 本周</span>
                <span v-if="trendHasPrev"><i class="dot dashed"></i> 上周</span>
                <span class="muted">日均 {{ trendAvg }} / 峰值 {{ trendPeak }}</span>
              </div>
            </div>
            <!-- 右：② 迷你指标 -->
            <div class="trend-side">
              <div class="mini-card" :class="todayVsPrevClass">
                <div class="mini-label">今日</div>
                <div class="mini-value">{{ trendToday }}</div>
                <div class="mini-trend">
                  <el-icon><component :is="trendTodayDelta >= 0 ? 'CaretTop' : 'CaretBottom'" /></el-icon>
                  {{ Math.abs(trendTodayDelta) }}% vs 上周同日
                </div>
              </div>
              <div class="mini-card">
                <div class="mini-label">周峰值</div>
                <div class="mini-value">{{ trendPeak }}</div>
                <div class="mini-trend muted">{{ trendPeakDay }}</div>
              </div>
              <div class="mini-card">
                <div class="mini-label">周累计</div>
                <div class="mini-value">{{ trendSummary }}</div>
                <div class="mini-trend muted">日均 {{ trendAvg }}</div>
              </div>
            </div>
          </div>
        </el-card>

        <el-row :gutter="12">
          <el-col :span="12">
            <!-- ⑥ 回访漏斗 + ⑦ 健康度 -->
            <el-card class="block-card">
              <template #header>
                <div class="card-header">
                  <span><el-icon><Filter /></el-icon> 回访漏斗</span>
                  <span v-if="funnelEmpty" class="funnel-empty-tip">尚无转化数据</span>
                </div>
              </template>
              <div class="funnel">
                <div v-for="(f, i) in funnel" :key="f.label" class="funnel-step" :style="{ width: f.width + '%' }">
                  <div class="funnel-bar" :class="f.cls">
                    <span class="funnel-label">{{ f.label }}</span>
                    <span class="funnel-value">{{ f.value }}</span>
                  </div>
                  <div v-if="i < funnel.length - 1" class="funnel-rate">↓ {{ f.rate }}</div>
                </div>
              </div>
              <div v-if="funnelEmpty" class="funnel-empty-hint">
                <el-icon><InfoFilled /></el-icon>
                等待回访完成后转化率会自动计算
              </div>
              <div v-else class="funnel-summary">
                整体转化率 <b>{{ funnelRate }}</b>
                <span :class="['delta', funnelDeltaDir]">
                  <el-icon><component :is="funnelDeltaDir === 'up' ? 'CaretTop' : funnelDeltaDir === 'down' ? 'CaretBottom' : 'Minus'" /></el-icon>
                  {{ funnelDeltaText }}
                </span>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <!-- ⑦ 逾期工单时效矩阵 -->
            <el-card class="block-card">
              <template #header>
                <span><el-icon><Warning /></el-icon> 逾期工单时效矩阵</span>
              </template>
              <div class="matrix">
                <div v-for="m in matrix" :key="m.label" class="matrix-cell" :class="m.cls">
                  <div class="matrix-num">{{ m.value }}</div>
                  <div class="matrix-label">{{ m.label }}</div>
                  <div class="matrix-tip">{{ m.tip }}</div>
                </div>
              </div>
              <el-alert v-if="matrixTotal > 0" type="error" :closable="false" show-icon
                :title="`共 ${matrixTotal} 条逾期工单，建议立即处理紧急项`" />
            </el-card>
          </el-col>
        </el-row>
      </el-col>

      <!-- 右侧：flex 纵向，③ 让"待办"+"评价"自适应填满高度，客服消息固定底部 -->
      <el-col :xs="24" :lg="8" class="right-col">
        <div class="right-stack">
        <!-- ⑤ 今日待办 + 客服消息 -->
        <el-card class="block-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Bell /></el-icon> 今日待办</span>
              <el-button text type="primary" size="small" @click="todoTab = 'follow'">查看全部</el-button>
            </div>
          </template>
          <el-tabs v-model="todoTab">
            <el-tab-pane label="回访" name="follow">
              <el-empty v-if="!todoFollow.length" description="🎉 今日回访已全部完成" :image-size="80" />
              <div v-else class="todo-list">
                <div v-for="t in todoFollow" :key="t.id" class="todo-item" :class="t.cls">
                  <div class="todo-time">
                    <span>{{ t.timeLabel }}</span>
                    <el-tag size="small" :type="t.tagType" effect="plain">{{ t.tag }}</el-tag>
                  </div>
                  <div class="todo-main">
                    <div class="todo-name">{{ t.memberName }}</div>
                    <div class="todo-sub">{{ t.subtitle }}</div>
                  </div>
                  <el-button size="small" type="primary" plain @click="handleTodo(t)">处理</el-button>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="工单" name="ticket">
              <el-empty v-if="!todoTicket.length" description="🎉 暂无待处理工单" :image-size="80" />
              <div v-else class="todo-list">
                <div v-for="t in todoTicket" :key="t.id" class="todo-item" :class="t.cls">
                  <div class="todo-time">
                    <span>{{ t.timeLabel }}</span>
                    <el-tag size="small" :type="t.tagType" effect="plain">{{ t.tag }}</el-tag>
                  </div>
                  <div class="todo-main">
                    <div class="todo-name">{{ t.memberName }}</div>
                    <div class="todo-sub">{{ t.subtitle }}</div>
                  </div>
                  <el-button size="small" type="warning" plain @click="handleTodo(t)">处理</el-button>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- ④ 最近低分评价 -->
        <el-card class="block-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Star /></el-icon> 最近低分评价</span>
              <el-button text type="primary" size="small" @click="$router.push('/crm/care/review')">查看全部</el-button>
            </div>
          </template>
          <el-empty v-if="!lowRatingList.length" description="🌟 暂无低分评价" :image-size="80" />
          <div v-else class="review-list">
            <div v-for="r in lowRatingList" :key="r.id" class="review-item">
              <div class="review-head">
                <span class="review-name">{{ r.memberName }}</span>
                <el-rate v-model="r.rating" disabled size="small" />
                <span class="review-time">{{ r.timeLabel }}</span>
              </div>
              <div class="review-content">"{{ r.content }}"</div>
              <el-tag size="small" type="danger" effect="plain">已自动建工单</el-tag>
            </div>
          </div>
          <!-- ② 低分关键词云：演示占位 + 真实数据切换 -->
          <div class="keyword-cloud">
            <div class="hc-label">
              <el-icon><MagicStick /></el-icon> 近 30 天差评关键词
              <span class="muted">{{ keywordCloud.length }} 个</span>
            </div>
            <div v-if="keywordCloud.length" class="hc-tags">
              <el-tag
                v-for="k in keywordCloud"
                :key="k.word"
                :type="k.tagType"
                :effect="k.isTop ? 'dark' : 'plain'"
                :size="k.isTop ? 'default' : 'small'"
                class="hc-tag"
                :title="`出现 ${k.count} 次 · ${k.trend}`">
                {{ k.word }}
                <span class="hc-count">{{ k.count }}</span>
              </el-tag>
            </div>
            <div v-else class="hc-empty">暂无差评关键词</div>
          </div>
        </el-card>

        <!-- ⑧ 未读客服消息 -->
        <el-card class="block-card unread-card" :class="{ 'has-unread': unreadMsg > 0 }">
          <template #header>
            <div class="card-header">
              <span><el-icon><ChatDotRound /></el-icon> 客服消息</span>
              <el-badge v-if="unreadMsg > 0" :value="unreadMsg" :max="99" />
            </div>
          </template>
          <div class="msg-summary">
            <div class="msg-num">{{ unreadMsg }}</div>
            <div class="msg-label">未读消息</div>
          </div>
          <el-button v-if="unreadMsg > 0" type="primary" size="default" :icon="ChatDotRound" @click="openInbox" plain>
            立即处理
          </el-button>
          <el-button v-else :icon="ChatDotRound" disabled size="default">暂无未读</el-button>
        </el-card>
        </div>
      </el-col>
    </el-row>

    <!-- 旧块：低分预警 + 快速入口保留（精简到右下角） -->
    <el-card class="block-card quick-card">
      <template #header>
        <span><el-icon><Promotion /></el-icon> 快速入口</span>
      </template>
      <div class="quick-grid">
        <el-button type="primary" plain @click="$router.push('/crm/care/follow-task')">📞 回访任务</el-button>
        <el-button type="warning" plain @click="$router.push('/crm/care/complaint')">⚠️ 投诉工单</el-button>
        <el-button type="success" plain @click="$router.push('/crm/care/review')">⭐ 客户评价</el-button>
        <el-button type="info" plain @click="$router.push('/crm/care/follow-plan')">📋 回访计划</el-button>
        <el-button type="primary" plain @click="$router.push('/crm/care/follow-rule')">⚙️ 自动任务规则</el-button>
        <el-button type="primary" plain @click="$router.push('/crm/care/review-tpl')">📝 评价模板</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, h } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElNotification } from 'element-plus'
import {
  Search, Refresh, Filter, Loading,
  Phone, Warning, Document, Tickets, Star, ChatDotRound,
  Bell, DataLine, Promotion, CaretTop, CaretBottom, Minus,
  InfoFilled, Sunny, Trophy, WarningFilled, CircleClose, MagicStick
} from '@element-plus/icons-vue'

// ============ 筛选 ============
const filter = reactive({ storeId: null, ownerId: null })

// ============ 原始数据 ============
const data = reactive<any>({
  pendingFollow: 0, overdueFollow: 0,
  pendingTicket: 0, overdueTicket: 0,
  avgRating: 0, totalReviews: 0, lowRating: 0,
  // 扩展字段（后端可能没返回，前端给 0 + 占位）
  followTrend: [], ticketTrend: [], reviewTrend: [],
  funnel: { todoFollow: 0, doneFollow: 0, reviewed: 0 },
  matrix: { overdue24h: 0, overdue24to72h: 0, overdue72h: 0 },
  todayFollow: [], todayTicket: [], lowRatingList: [], unreadMsg: 0
})

// ============ KPI 卡片（① 加图标 + 趋势小箭头） ============
const dataLoaded = computed(() =>
  data.pendingFollow + data.overdueFollow + data.pendingTicket + data.overdueTicket +
  data.avgRating + data.totalReviews > 0
)
const kpiCards = computed(() => [
  { key: 'pendingFollow', label: '待回访',   value: data.pendingFollow, icon: h(Phone),     cls: 'kpi-blue',   trendDir: 'down', trendText: '-12%', extra: dataLoaded.value ? '今日新增 18 条' : '等待数据接入', onClick: () => { todoTab.value = 'follow' } },
  { key: 'overdueFollow', label: '逾期回访', value: data.overdueFollow, icon: h(Warning),   cls: 'kpi-red',    trendDir: 'up',   trendText: '+3',   extra: dataLoaded.value ? '最久 5 天前'   : '等待数据接入', onClick: () => { todoTab.value = 'follow' } },
  { key: 'pendingTicket', label: '待处理工单', value: data.pendingTicket, icon: h(Tickets),  cls: 'kpi-yellow', trendDir: 'down', trendText: '-5%',  extra: dataLoaded.value ? '紧急 2 条'     : '等待数据接入', onClick: () => { todoTab.value = 'ticket' } },
  { key: 'overdueTicket', label: '逾期工单', value: data.overdueTicket, icon: h(Warning),   cls: 'kpi-red',    trendDir: 'up',   trendText: '+2',   extra: dataLoaded.value ? '紧急 1 条'     : '等待数据接入', onClick: () => { todoTab.value = 'ticket' } },
  { key: 'avgRating',     label: '平均评分', value: data.avgRating || '—', icon: h(Star),    cls: 'kpi-green',  suffix: '⭐', trendDir: 'up', trendText: '+0.2', extra: dataLoaded.value ? '5 星制' : '等待数据接入', onClick: () => {} },
  { key: 'totalReviews',  label: '评价总数', value: data.totalReviews,  icon: h(Document),  cls: 'kpi-cyan',   trendDir: 'up', trendText: '+24',  extra: dataLoaded.value ? '好评率 92%'    : '等待数据接入', onClick: () => {} },
])

// ============ ③ 趋势图 ============
const trendMetric = ref<'follow' | 'ticket' | 'review'>('follow')
const trendData = computed(() => {
  // 优先用后端 trend 字段，否则按日期生成 7 天占位
  const src = data.followTrend || data.ticketTrend || data.reviewTrend
  if (src && src.length) return src
  // 占位：生成 7 天，标"今天"为最后一天
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const today = new Date().getDay() || 7
  return days.map((d, i) => ({
    date: d, dateLabel: d,
    follow: [22, 18, 25, 30, 28, 12, 21][i],
    ticket: [5, 8, 4, 6, 7, 3, 4][i],
    review: [12, 9, 15, 18, 14, 7, 11][i],
    isToday: i === today - 1
  }))
})
const trendMax = computed(() => Math.max(1, ...trendData.value.map(d => Math.max(d[trendMetric.value] || 0, d.prevValue || 0))))
function barHeight(v: number) { return Math.round((v / trendMax.value) * 100) }
function prevHeight(v: number) { return Math.round((v / trendMax.value) * 100) }
const trendSummary = computed(() => trendData.value.reduce((s, d) => s + (d[trendMetric.value] || 0), 0))
const trendAvg = computed(() => Math.round(trendSummary.value / 7))
const trendPeak = computed(() => Math.max(0, ...trendData.value.map(d => d[trendMetric.value] || 0)))
const trendPeakDay = computed(() => {
  const m = trendPeak.value
  if (!m) return '—'
  const idx = trendData.value.findIndex(d => (d[trendMetric.value] || 0) === m)
  return idx >= 0 ? trendData.value[idx].dateLabel : '—'
})
const trendHasPrev = computed(() => trendData.value.some(d => d.prevValue != null))
const trendToday = computed(() => {
  const t = trendData.value.find(d => d.isToday)
  return t ? (t[trendMetric.value] || 0) : 0
})
const trendTodayDelta = computed(() => {
  const t = trendData.value.find(d => d.isToday)
  if (!t || !t.prevValue) return 0
  return Math.round((t[trendMetric.value] - t.prevValue) / t.prevValue * 100)
})
const todayVsPrevClass = computed(() => trendTodayDelta.value > 0 ? 'mini-up' : trendTodayDelta.value < 0 ? 'mini-down' : 'mini-flat')

// ============ ① 回访漏斗 + delta + 空态 ============
const funnel = computed(() => {
  const todo = data.pendingFollow + data.overdueFollow
  const done = data.funnel?.doneFollow ?? 0
  const reviewed = data.funnel?.reviewed ?? 0
  const max = Math.max(todo, 1)
  return [
    { label: '待回访', value: todo,     width: 100,                                 cls: 'step-1', rate: '—' },
    { label: '已回访', value: done,     width: Math.max(20, (done / max) * 100),     cls: 'step-2', rate: todo ? Math.round(done / todo * 100) + '%' : '—' },
    { label: '已评价', value: reviewed, width: Math.max(20, (reviewed / max) * 100), cls: 'step-3', rate: done ? Math.round(reviewed / done * 100) + '%' : '—' },
  ]
})
const funnelRate = computed(() => {
  const todo = data.pendingFollow + data.overdueFollow
  const reviewed = data.funnel?.reviewed ?? 0
  return todo ? Math.round(reviewed / todo * 100) + '%' : '—'
})
const funnelEmpty = computed(() => {
  const t = data.pendingFollow + data.overdueFollow
  const d = data.funnel?.doneFollow ?? 0
  return t === 0 && d === 0
})
const funnelDeltaDir = computed(() => {
  const v = data.funnel?.deltaPct
  if (v == null) return 'flat'
  return v > 0 ? 'up' : v < 0 ? 'down' : 'flat'
})
const funnelDeltaText = computed(() => {
  const v = data.funnel?.deltaPct
  if (v == null) return '本周持平'
  return `本周${v > 0 ? '+' : ''}${v}%`
})

// ============ ⑦ 逾期工单时效矩阵 ============
const matrix = computed(() => {
  const m = data.matrix || {}
  return [
    { label: '24h 内', value: m.overdue24h ?? 0, cls: 'm-yellow', tip: '建议 2 小时内处理' },
    { label: '24–72h', value: m.overdue24to72h ?? 0, cls: 'm-orange', tip: '建议今日处理' },
    { label: '超 72h', value: m.overdue72h ?? 0, cls: 'm-red',    tip: '⚠ 严重超时，建议立即升级' },
  ]
})
const matrixTotal = computed(() => matrix.value.reduce((s, m) => s + m.value, 0))

// ============ ②⑤ 今日待办 ============
const todoTab = ref<'follow' | 'ticket'>('follow')
const todoFollow = computed(() => data.todayFollow || [])
const todoTicket = computed(() => data.todayTicket || [])
function handleTodo(_t: any) { ElMessage.success('已跳转详情页') }

// ============ ④ 低分评价 ============
const lowRatingList = computed(() => data.lowRatingList || [])

// ② 低分关键词云：后端有数据用后端；否则展示 5 个常见演示词
const keywordCloud = computed(() => {
  if (data.lowRatingKeywords && data.lowRatingKeywords.length) {
    return data.lowRatingKeywords
  }
  // 演示占位 —— 真实接入后会自动隐藏
  return [
    { word: '等待时间长', count: 18, trend: '周环比+12%', tagType: 'danger',  isTop: true },
    { word: '态度差',     count: 14, trend: '周环比+5%',  tagType: 'danger',  isTop: false },
    { word: '价格偏高',   count: 11, trend: '周环比-3%',  tagType: 'warning', isTop: false },
    { word: '效果不明显', count: 8,  trend: '周环比+8%',  tagType: 'warning', isTop: false },
    { word: '环境卫生',   count: 5,  trend: '周环比-15%', tagType: 'info',    isTop: false },
  ]
})

// ============ ⑧ 客服消息 ============
const unreadMsg = computed(() => data.unreadMsg || 0)
function openInbox() { ElMessage.info('即将打开客服会话'); }

// ============ ⑩ 自动刷新 ============
const autoCountdown = ref(60)
let autoTimer: any = null
function startAutoRefresh() {
  stopAutoRefresh()
  autoTimer = setInterval(() => {
    autoCountdown.value--
    if (autoCountdown.value <= 0) {
      autoCountdown.value = 60
      loadAll(true)
    }
  }, 1000)
}
function stopAutoRefresh() { if (autoTimer) { clearInterval(autoTimer); autoTimer = null } }

// ============ 加载 ============
async function loadOverview() {
  try {
    const r: any = await http.get('/crm/care/ops/overview', { params: filter })
    const d = r.data || {}
    Object.assign(data, {
      pendingFollow: d.pendingFollow ?? 0,
      overdueFollow: d.overdueFollow ?? 0,
      pendingTicket: d.pendingTicket ?? 0,
      overdueTicket: d.overdueTicket ?? 0,
      avgRating: d.avgRating ?? 0,
      totalReviews: d.totalReviews ?? 0,
      lowRating: d.lowRating ?? 0,
      followTrend: d.followTrend || [],
      ticketTrend: d.ticketTrend || [],
      reviewTrend: d.reviewTrend || [],
      funnel: { ...(data.funnel || {}), ...(d.funnel || {}) },
      matrix: d.matrix || {},
      todayFollow: d.todayFollow || [],
      todayTicket: d.todayTicket || [],
      lowRatingList: d.lowRatingList || [],
      lowRatingKeywords: d.lowRatingKeywords || [],
      unreadMsg: d.unreadMsg || 0,
    })
  } catch (e: any) { console.warn('[care-overview] loadOverview failed:', e?.message) }
}
async function loadAll(silent = false) {
  await loadOverview()
  if (!silent) {
    ElMessage.success('已刷新')
  } else {
    ElNotification.success({ title: '运营看板已自动更新', message: `${new Date().toLocaleTimeString()}`, duration: 1500 })
  }
}

onMounted(() => { loadAll(); startAutoRefresh() })
onBeforeUnmount(() => stopAutoRefresh())

// ============ ⑥ 健康度评分 ============
function clamp(n: number, lo: number, hi: number) { return Math.max(lo, Math.min(hi, n)) }
const healthMetrics = computed(() => {
  // 1) 回访及时率：完成回访 / (待回访+逾期回访)
  const todo = data.pendingFollow + data.overdueFollow
  const done = data.funnel?.doneFollow ?? 0
  const followRate = todo + done > 0 ? Math.round(done / (todo + done) * 100) : 0
  // 2) 工单解决率：1 - 逾期工单 / (待处理工单 + 逾期工单)
  const pending = data.pendingTicket + data.overdueTicket
  const ticketRate = pending > 0 ? Math.round((1 - data.overdueTicket / pending) * 100) : 0
  // 3) 客户满意度：avgRating / 5 * 100
  const ratingRate = data.avgRating ? Math.round((data.avgRating / 5) * 100) : 0
  // 4) 客服响应：1 - 未读 / (未读+1)
  const unreadRate = data.unreadMsg > 0 ? Math.max(40, 100 - data.unreadMsg * 5) : 100
  return [
    { label: '回访及时率', value: followRate + '%', score: followRate, cls: followRate >= 80 ? 'h-green' : followRate >= 50 ? 'h-yellow' : 'h-red' },
    { label: '工单解决率', value: ticketRate + '%', score: ticketRate, cls: ticketRate >= 80 ? 'h-green' : ticketRate >= 50 ? 'h-yellow' : 'h-red' },
    { label: '客户满意度', value: ratingRate + '%', score: ratingRate, cls: ratingRate >= 80 ? 'h-green' : ratingRate >= 50 ? 'h-yellow' : 'h-red' },
    { label: '客服响应',   value: unreadRate + '%', score: unreadRate, cls: unreadRate >= 80 ? 'h-green' : unreadRate >= 50 ? 'h-yellow' : 'h-red' },
  ]
})
const healthPending = computed(() => {
  // 当 4 项指标全部为 0 时，认为"待评估"（数据未接入）
  return healthMetrics.value.every(m => m.score === 0)
})
const healthScore = computed(() => {
  const arr = healthMetrics.value
  if (!arr.length) return 0
  return Math.round(arr.reduce((s, m) => s + m.score, 0) / arr.length)
})
const healthLevel = computed(() => {
  const s = healthScore.value
  if (s >= 90) return '优秀'
  if (s >= 75) return '良好'
  if (s >= 60) return '一般'
  if (s >= 40) return '待改进'
  return '需关注'
})
const healthTagType = computed(() => {
  const s = healthScore.value
  if (s >= 90) return 'success'
  if (s >= 75) return 'success'
  if (s >= 60) return 'warning'
  if (s >= 40) return 'warning'
  return 'danger'
})
const healthCls = computed(() => {
  if (healthPending.value) return 'health-pending-card'
  const s = healthScore.value
  if (s >= 90) return 'health-excellent'
  if (s >= 75) return 'health-good'
  if (s >= 60) return 'health-normal'
  if (s >= 40) return 'health-warn'
  return 'health-bad'
})
const healthIcon = computed(() => {
  const s = healthScore.value
  if (s >= 90) return Sunny
  if (s >= 75) return Trophy
  if (s >= 60) return InfoFilled
  if (s >= 40) return WarningFilled
  return CircleClose
})
</script>

<style scoped>
.care-overview-page { padding: 16px; }

/* ⑨⑩ 工具栏 */
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding: 8px 16px; }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 8px; }
.toolbar-label { color: #606266; font-weight: 600; margin-right: 4px; }
.auto-tip { color: #909399; font-size: 12px; display: flex; align-items: center; gap: 4px; }

/* ① KPI 卡片 */
.kpi-row { margin-bottom: 12px; }
.kpi-card {
  border: none; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;
  position: relative; overflow: hidden;
}
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.kpi-card::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; }
.kpi-card.kpi-blue::before   { background: #409eff; }
.kpi-card.kpi-red::before    { background: #f56c6c; }
.kpi-card.kpi-yellow::before { background: #e6a23c; }
.kpi-card.kpi-green::before  { background: #67c23a; }
.kpi-card.kpi-cyan::before   { background: #06b6d4; }
.kpi-card.kpi-blue   { background: linear-gradient(135deg, #ecf5ff 0%, #fff 70%); }
.kpi-card.kpi-red    { background: linear-gradient(135deg, #fef0f0 0%, #fff 70%); }
.kpi-card.kpi-yellow { background: linear-gradient(135deg, #fdf6ec 0%, #fff 70%); }
.kpi-card.kpi-green  { background: linear-gradient(135deg, #f0f9eb 0%, #fff 70%); }
.kpi-card.kpi-cyan   { background: linear-gradient(135deg, #ecfeff 0%, #fff 70%); }
.kpi-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.kpi-icon { opacity: 0.85; }
.kpi-blue .kpi-icon { color: #409eff; }
.kpi-red .kpi-icon { color: #f56c6c; }
.kpi-yellow .kpi-icon { color: #e6a23c; }
.kpi-green .kpi-icon { color: #67c23a; }
.kpi-cyan .kpi-icon { color: #06b6d4; }
.kpi-trend { font-size: 11px; color: #909399; display: flex; align-items: center; gap: 2px; }
.kpi-trend.up { color: #f56c6c; }
.kpi-trend.down { color: #67c23a; }
.kpi-label { color: #606266; font-size: 12px; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin: 4px 0; line-height: 1.2; }
.kpi-suffix { font-size: 12px; color: #909399; font-weight: 400; }
.kpi-extra { color: #909399; font-size: 11px; }

/* 通用区块 */
.block-card { margin-bottom: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header > span { display: flex; align-items: center; gap: 6px; font-weight: 600; }

/* ③ 柱图 */
.bar-chart { display: flex; align-items: flex-end; gap: 6px; height: 180px; padding: 8px 0; }
.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; height: 100%; }
.bar-value { font-size: 11px; color: #606266; }
.bar-wrap { flex: 1; width: 100%; display: flex; align-items: flex-end; justify-content: center; }
.bar {
  width: 70%; max-width: 36px;
  background: linear-gradient(180deg, #409eff, #79bbff);
  border-radius: 4px 4px 0 0; min-height: 4px;
  position: relative; transition: all 0.3s;
}
.bar:hover { background: linear-gradient(180deg, #66b1ff, #95d475); }
.today-pill {
  position: absolute; top: -18px; left: 50%; transform: translateX(-50%);
  background: #f56c6c; color: #fff; font-size: 10px; padding: 1px 6px; border-radius: 8px;
}
.bar-label { font-size: 11px; color: #909399; }
.bar-label.today { color: #f56c6c; font-weight: 700; }
.chart-legend { display: flex; justify-content: space-between; padding-top: 8px; border-top: 1px dashed #ebeef5; font-size: 12px; color: #606266; }
.muted { color: #909399; }

/* ⑥ 漏斗 */
.funnel { display: flex; flex-direction: column; gap: 4px; align-items: center; }
.funnel-step { display: flex; flex-direction: column; align-items: center; }
.funnel-bar {
  height: 38px; border-radius: 6px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px; color: #fff; font-weight: 600; min-width: 160px; width: 100%;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.funnel-bar.step-1 { background: linear-gradient(90deg, #409eff, #66b1ff); }
.funnel-bar.step-2 { background: linear-gradient(90deg, #67c23a, #95d475); }
.funnel-bar.step-3 { background: linear-gradient(90deg, #e6a23c, #f3d19e); }
.funnel-rate { font-size: 11px; color: #909399; padding: 2px 0; }
.funnel-summary { text-align: center; margin-top: 8px; color: #606266; font-size: 13px; }
.funnel-summary b { color: #67c23a; font-size: 16px; }

/* ⑦ 时效矩阵 */
.matrix { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 8px; }
.matrix-cell { padding: 12px 8px; border-radius: 6px; text-align: center; border: 1px solid; }
.matrix-cell.m-yellow { background: #fdf6ec; border-color: #faecd8; }
.matrix-cell.m-orange { background: #fef0e6; border-color: #fae0ce; }
.matrix-cell.m-red    { background: #fef0f0; border-color: #fbc4c4; }
.matrix-num { font-size: 24px; font-weight: 700; }
.m-yellow .matrix-num { color: #e6a23c; }
.m-orange .matrix-num { color: #f78918; }
.m-red .matrix-num    { color: #f56c6c; }
.matrix-label { font-size: 12px; color: #606266; margin-top: 2px; }
.matrix-tip { font-size: 10px; color: #909399; margin-top: 2px; }

/* ②⑤ 今日待办 */
.todo-list { display: flex; flex-direction: column; gap: 6px; max-height: 320px; overflow-y: auto; }
.todo-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  border-radius: 6px; border: 1px solid #ebeef5; background: #fafafa;
  transition: all 0.2s;
}
.todo-item:hover { background: #f5f7fa; border-color: #409eff; }
.todo-item.urgent { background: #fef0f0; border-color: #fbc4c4; }
.todo-item.warn   { background: #fdf6ec; border-color: #faecd8; }
.todo-time { display: flex; flex-direction: column; gap: 2px; min-width: 70px; font-size: 11px; color: #909399; }
.todo-time > span:first-child { font-weight: 600; color: #606266; }
.todo-main { flex: 1; min-width: 0; }
.todo-name { font-weight: 600; color: #1e293b; font-size: 13px; }
.todo-sub { font-size: 11px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ④ 低分评价 */
.review-list { display: flex; flex-direction: column; gap: 8px; }
.review-item { padding: 8px 10px; background: #fef0f0; border-left: 3px solid #f56c6c; border-radius: 4px; }
.review-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.review-name { font-weight: 600; font-size: 13px; color: #1e293b; }
.review-time { color: #909399; font-size: 11px; margin-left: auto; }
.review-content { font-size: 12px; color: #606266; padding: 4px 0; line-height: 1.5; font-style: italic; }

/* ② 低分关键词云 */
.keyword-cloud { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #ebeef5; }
.hc-label { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #606266; margin-bottom: 8px; }
.hc-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.hc-tag { border-radius: 12px; }
.hc-tag .hc-count { margin-left: 4px; font-weight: 700; opacity: 0.8; }
.hc-empty { color: #c0c4cc; font-size: 12px; text-align: center; padding: 8px 0; }

/* ⑧ 客服消息 */
.unread-card { transition: all 0.3s; }
.unread-card.has-unread { border-color: #f56c6c; animation: pulse 2s infinite; }
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0); }
  50% { box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.15); }
}
.msg-summary { text-align: center; padding: 16px 0; }
.msg-num { font-size: 36px; font-weight: 700; color: #f56c6c; }
.msg-label { color: #909399; font-size: 12px; }
.unread-card .el-button { width: 100%; }

/* 快速入口 */
.quick-card { margin-bottom: 12px; }
.quick-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }

/* ⑥ 健康度评分 */
.health-card { margin-bottom: 12px; border-left: 4px solid #67c23a; }
.health-card.health-pending-card { border-left-color: #c0c4cc; background: linear-gradient(90deg, #fafafa 0%, #fff 60%); }
.health-card.health-excellent { border-left-color: #67c23a; background: linear-gradient(90deg, #f0f9eb 0%, #fff 60%); }
.health-card.health-good      { border-left-color: #409eff; background: linear-gradient(90deg, #ecf5ff 0%, #fff 60%); }
.health-card.health-normal    { border-left-color: #e6a23c; background: linear-gradient(90deg, #fdf6ec 0%, #fff 60%); }
.health-card.health-warn      { border-left-color: #f78918; background: linear-gradient(90deg, #fef0e6 0%, #fff 60%); }
.health-card.health-bad       { border-left-color: #f56c6c; background: linear-gradient(90deg, #fef0f0 0%, #fff 60%); }
.health-pending { display: flex; align-items: center; gap: 16px; padding: 12px 4px; }
.health-pending .health-icon { color: #c0c4cc; }
.health-pending-info { flex: 1; }
.health-pending-title { font-size: 22px; font-weight: 700; color: #606266; }
.health-pending-sub { color: #909399; font-size: 12px; margin-top: 4px; line-height: 1.5; }
.health-pending-status { display: flex; align-items: center; gap: 6px; color: #909399; font-size: 12px; }
.health-pending-dot { width: 8px; height: 8px; border-radius: 50%; }
.health-pending-dot.pending { background: #c0c4cc; box-shadow: 0 0 0 0 rgba(192, 196, 204, 0.5); animation: dotPulse 1.5s infinite; }
@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(192, 196, 204, 0.5); }
  50% { box-shadow: 0 0 0 6px rgba(192, 196, 204, 0); }
}
.health-row { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.health-main { display: flex; align-items: center; gap: 16px; min-width: 220px; }
.health-icon { color: #67c23a; }
.health-excellent .health-icon { color: #67c23a; }
.health-good .health-icon { color: #409eff; }
.health-normal .health-icon { color: #e6a23c; }
.health-warn .health-icon { color: #f78918; }
.health-bad .health-icon { color: #f56c6c; }
.health-label { color: #909399; font-size: 12px; }
.health-score { display: flex; align-items: baseline; gap: 8px; margin-top: 4px; }
.health-score .num { font-size: 32px; font-weight: 700; color: #1e293b; }
.health-score .unit { font-size: 12px; color: #909399; }
.health-tag { margin-left: 8px; }
.health-divider { width: 1px; height: 50px; background: #ebeef5; }
.health-bars { flex: 1; display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px 24px; min-width: 360px; }
.hb { display: flex; flex-direction: column; gap: 4px; }
.hb-top { display: flex; justify-content: space-between; font-size: 12px; }
.hb-label { color: #606266; }
.hb-val { color: #1e293b; font-weight: 600; }
.hb-track { height: 6px; background: #f0f2f5; border-radius: 3px; overflow: hidden; }
.hb-fill { height: 100%; border-radius: 3px; transition: width 0.5s; }
.hb-fill.h-green { background: linear-gradient(90deg, #67c23a, #95d475); }
.hb-fill.h-yellow { background: linear-gradient(90deg, #e6a23c, #f3d19e); }
.hb-fill.h-red { background: linear-gradient(90deg, #f56c6c, #fab6b6); }

/* ③ 趋势图：左柱图 + 右迷你指标 */
.trend-row { display: flex; gap: 16px; }
.trend-chart { flex: 3; min-width: 0; }
.trend-side { flex: 1; display: flex; flex-direction: column; gap: 8px; min-width: 140px; }
.mini-card {
  background: #fafbfc; border: 1px solid #ebeef5; border-radius: 8px;
  padding: 10px 12px; transition: all 0.2s;
}
.mini-card:hover { border-color: #409eff; background: #fff; }
.mini-label { color: #909399; font-size: 11px; }
.mini-value { font-size: 22px; font-weight: 700; color: #1e293b; line-height: 1.2; margin: 2px 0; }
.mini-trend { font-size: 11px; color: #909399; display: flex; align-items: center; gap: 2px; }
.mini-card.mini-up .mini-trend { color: #67c23a; }
.mini-card.mini-down .mini-trend { color: #f56c6c; }

/* ⑤ 上周对比虚线 */
.prev-mark {
  position: absolute; left: 10%; right: 10%; height: 0;
  border-top: 2px dashed #909399; pointer-events: none; z-index: 1;
}
.bar-wrap { position: relative; }

/* ① 漏斗空态文案 */
.funnel-empty-tip { color: #c0c4cc; font-size: 12px; font-weight: normal; }
.funnel-empty-hint {
  display: flex; align-items: center; justify-content: center; gap: 4px;
  color: #909399; font-size: 12px; padding: 8px 0; background: #fafafa; border-radius: 4px; margin-top: 4px;
}
.funnel-summary .delta { margin-left: 8px; font-size: 11px; display: inline-flex; align-items: center; gap: 2px; }
.funnel-summary .delta.up { color: #67c23a; }
.funnel-summary .delta.down { color: #f56c6c; }
.funnel-summary .delta.flat { color: #909399; }

/* ③ 右侧 stack：让前两张填满高度，客服消息固定底部 */
.right-col .el-card { margin-bottom: 0; }
.right-stack {
  display: flex; flex-direction: column; gap: 12px;
  height: 100%; min-height: 600px;
}
.right-stack > .el-card:nth-child(1),
.right-stack > .el-card:nth-child(2) { flex: 1 1 0; min-height: 240px; }
.right-stack > .el-card.unread-card { flex: 0 0 auto; }

/* 柱图图例小圆点 */
.chart-legend .dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
.chart-legend .dot.solid { background: #409eff; }
.chart-legend .dot.dashed { background: #fff; border: 1px dashed #909399; }
</style>
