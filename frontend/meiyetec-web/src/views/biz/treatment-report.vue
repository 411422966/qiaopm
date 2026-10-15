<template>
  <div class="tr-page">
    <!-- 顶部：4 KPI 卡 -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="6">
        <el-card class="kpi-card kpi-blue">
          <div class="kpi-inner">
            <div class="kpi-icon"><el-icon :size="28"><Document /></el-icon></div>
            <div>
              <div class="kpi-value">{{ kpi.totalReports }}</div>
              <div class="kpi-label">累计报告数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="kpi-card kpi-green">
          <div class="kpi-inner">
            <div class="kpi-icon"><el-icon :size="28"><Calendar /></el-icon></div>
            <div>
              <div class="kpi-value">{{ kpi.todayReports }}</div>
              <div class="kpi-label">今日生成</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="kpi-card kpi-orange">
          <div class="kpi-inner">
            <div class="kpi-icon"><el-icon :size="28"><DataLine /></el-icon></div>
            <div>
              <div class="kpi-value">{{ formatPct(kpi.avgCompliance) }}</div>
              <div class="kpi-label">平均遵守率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="kpi-card kpi-purple">
          <div class="kpi-inner">
            <div class="kpi-icon"><el-icon :size="28"><TrendCharts /></el-icon></div>
            <div>
              <div class="kpi-value">
                <span :style="{ color: deltaMeta(kpi.avgDelta).color }">
                  {{ deltaMeta(kpi.avgDelta).arrow }}
                  {{ kpi.avgDelta != null ? Math.abs(kpi.avgDelta).toFixed(2) : '—' }}
                </span>
              </div>
              <div class="kpi-label">平均效果分差</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体 -->
    <el-tabs v-model="activeTab" class="tr-tabs">

      <!-- ============ Tab 1: 最新报告 ============ -->
      <el-tab-pane label="最新报告" name="latest">
        <el-card class="filter-bar">
          <el-form :inline="true" :model="genForm">
            <el-form-item label="会员">
              <el-input
                v-model="genForm.memberId"
                placeholder="会员 ID"
                style="width: 140px"
                clearable
                @keyup.enter="onGenerate" />
            </el-form-item>
            <el-form-item label="疗程方案">
              <el-select
                v-model="genForm.planId"
                placeholder="选择方案"
                filterable
                style="width: 220px"
                @change="onPlanChange">
                <el-option
                  v-for="p in planOptions"
                  :key="p.id"
                  :label="p.planName"
                  :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="生成者">
              <el-input v-model="genForm.generatedBy" placeholder="SYSTEM" style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="generateLoading" @click="onGenerate">
                <el-icon><MagicStick /></el-icon>生成报告
              </el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="filter-hint">
            报告生成后会追加一条历史快照，可在「历史快照」Tab 查看历史趋势。
          </div>
        </el-card>

        <div v-if="!currentReport" class="empty-tip">
          <el-empty
            description="尚无报告。请先填写会员 ID + 方案 ID，然后点击「生成报告」。"
            :image-size="120" />
        </div>

        <div v-else>
          <!-- ✅ 新增：报告操作工具栏（导出 PDF + 扫码分享 + 重新生成） -->
          <div class="report-actions no-print">
            <el-button :icon="Share" plain size="default" @click="onShareQr">
              扫码查看 H5 客户端版本
            </el-button>
            <el-button :icon="Printer" plain size="default" @click="onExportPdf">
              导出 PDF / 打印
            </el-button>
            <el-button :icon="Refresh" plain size="default" @click="onGenerate">
              重新生成
            </el-button>
          </div>

          <!-- 4 个二级 KPI：方案完成进度 -->
          <el-row :gutter="16" class="mb16">
            <el-col :span="6">
              <el-statistic title="方案总次数" :value="currentReport.totalSessions ?? 0" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="已完成" :value="currentReport.completedSessions ?? 0" />
            </el-col>
            <el-col :span="6">
              <el-statistic
                title="遵守率"
                :value="formatPct(currentReport.careComplianceRate)" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="效果分差值">
                <template #default>
                  <span :style="{ color: deltaMeta(currentReport.effectScoreDelta).color, fontWeight: 700 }">
                    {{ deltaMeta(currentReport.effectScoreDelta).arrow }}
                    {{ currentReport.effectScoreDelta != null ? formatNum(currentReport.effectScoreDelta) : '—' }}
                  </span>
                </template>
              </el-statistic>
            </el-col>
          </el-row>

          <!-- ✅ 按 phaseNo 分组：每个 phase 是一组多序列柱（target/completed/abandoned/ongoing） -->
          <el-row :gutter="16">
            <el-col :span="14">
              <el-card>
                <template #header>
                  <span><el-icon><Histogram /></el-icon> 各阶段 session 分布（按 phaseNo 拆分）</span>
                </template>
                <div ref="sessionsChartEl" class="chart-area" />
              </el-card>
            </el-col>
            <el-col :span="10">
              <el-card>
                <template #header>
                  <span><el-icon><PieChart /></el-icon> 各阶段完成度玫瑰图</span>
                </template>
                <div ref="phasePieEl" class="chart-area" />
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="16" class="mt12">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span><el-icon><DataLine /></el-icon> 皮肤评分变化</span>
                </template>
                <div ref="scoreLineEl" class="chart-area" />
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span><el-icon><MagicStick /></el-icon> 文本建议（启发式）</span>
                </template>
                <div class="recommendation-text">
                  <el-tag
                    :type="deltaMeta(currentReport.effectScoreDelta).color === '#67C23A' ? 'success' :
                          deltaMeta(currentReport.effectScoreDelta).color === '#F56C6C' ? 'danger' : 'info'"
                    effect="plain"
                    class="mr8">
                    {{ deltaMeta(currentReport.effectScoreDelta).tag }}
                  </el-tag>
                  <el-tag
                    :type="complianceColorTag(currentReport.careComplianceRate)"
                    effect="plain">
                    遵守率 {{ formatPct(currentReport.careComplianceRate) }}
                  </el-tag>
                  <el-divider />
                  <p class="reco-line">{{ currentReport.recommendation || '暂无建议' }}</p>
                  <el-descriptions :column="2" border size="small" class="mt12">
                    <el-descriptions-item label="会员">{{ currentReport.memberId }}</el-descriptions-item>
                    <el-descriptions-item label="方案">{{ currentReport.planName || currentReport.treatmentPlanId }}</el-descriptions-item>
                    <el-descriptions-item label="已完成 session">{{ currentReport.completedSessions ?? 0 }}</el-descriptions-item>
                    <el-descriptions-item label="进行中">{{ currentReport.ongoingSessions ?? 0 }}</el-descriptions-item>
                    <el-descriptions-item label="废弃">{{ currentReport.abandonedSessions ?? 0 }}</el-descriptions-item>
                    <el-descriptions-item label="护理建议 (采纳/总数)">
                      {{ currentReport.careSuggestionsFollowed ?? 0 }} / {{ currentReport.careSuggestionsTotal ?? 0 }}
                    </el-descriptions-item>
                    <el-descriptions-item label="效果分（前）">{{ formatNum(currentReport.effectScoreBefore) }}</el-descriptions-item>
                    <el-descriptions-item label="效果分（后）">{{ formatNum(currentReport.effectScoreAfter) }}</el-descriptions-item>
                    <el-descriptions-item label="生成者">{{ currentReport.generatedBy || 'SYSTEM' }}</el-descriptions-item>
                    <el-descriptions-item label="生成时间">{{ formatTime(currentReport.generatedAt) }}</el-descriptions-item>
                  </el-descriptions>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- ============ Tab 2: 历史快照 ============ -->
      <el-tab-pane label="历史快照" name="history">
        <el-card class="filter-bar">
          <el-form :inline="true">
            <el-form-item label="会员 ID">
              <el-input
                v-model="historyMemberId"
                placeholder="会员 ID"
                style="width: 200px"
                clearable
                @keyup.enter="loadHistory" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadHistory">
                <el-icon><Search /></el-icon>查询
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table :data="historyList" border v-loading="historyLoading">
          <el-table-column prop="id" label="报告 ID" width="80" />
          <el-table-column prop="memberId" label="会员" width="80" />
          <el-table-column prop="planName" label="方案">
            <template #default="{ row }">
              {{ row.planName || row.treatmentPlanId }}
            </template>
          </el-table-column>
          <el-table-column label="完成进度" width="120">
            <template #default="{ row }">
              {{ row.completedSessions ?? 0 }} / {{ row.totalSessions ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="遵守率" width="100">
            <template #default="{ row }">
              <el-tag :type="complianceColorTag(row.careComplianceRate)" effect="plain">
                {{ formatPct(row.careComplianceRate) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="效果分差" width="140">
            <template #default="{ row }">
              <span :style="{ color: deltaMeta(row.effectScoreDelta).color, fontWeight: 600 }">
                {{ deltaMeta(row.effectScoreDelta).arrow }}
                {{ row.effectScoreDelta != null ? formatNum(row.effectScoreDelta) : '—' }}
              </span>
              <el-tag size="small" effect="plain" class="ml4">
                {{ deltaMeta(row.effectScoreDelta).tag }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="generatedBy" label="生成者" width="100" />
          <el-table-column label="生成时间" width="170">
            <template #default="{ row }">{{ formatTime(row.generatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="viewReport(row.id)">
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!historyLoading && !historyList.length" description="暂无历史报告" />
      </el-tab-pane>

      <!-- ============ Tab 3: 阈值规则预览 ============ -->
      <el-tab-pane label="阈值规则预览" name="rules">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-card>
              <template #header>
                <span><el-icon><SetUp /></el-icon> 规则说明</span>
              </template>
              <el-table :data="ruleTable" border>
                <el-table-column label="优先级" prop="priority" width="80" />
                <el-table-column label="触发条件" prop="condition" min-width="220" />
                <el-table-column label="文本结论" prop="text" min-width="320" />
                <el-table-column label="典型场景" prop="scenario" min-width="160" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card>
              <template #header>
                <span><el-icon><DataAnalysis /></el-icon> 模拟当前数据</span>
              </template>
              <el-form :model="simForm" label-width="100px">
                <el-form-item label="总次数">
                  <el-input-number v-model="simForm.totalSessions" :min="0" :max="100" />
                </el-form-item>
                <el-form-item label="已完成">
                  <el-input-number v-model="simForm.completedSessions" :min="0" :max="100" />
                </el-form-item>
                <el-form-item label="遵守率">
                  <el-input-number v-model="simForm.compliance" :min="0" :max="1" :step="0.05" :precision="2" />
                </el-form-item>
                <el-form-item label="效果分差">
                  <el-input-number v-model="simForm.delta" :min="-50" :max="50" :step="0.5" :precision="2" />
                </el-form-item>
                <el-form-item>
                  <div class="sim-result">
                    <el-tag :type="deltaMeta(simForm.delta).color === '#67C23A' ? 'success' :
                              deltaMeta(simForm.delta).color === '#F56C6C' ? 'danger' : 'info'" class="mr8">
                      Δ {{ deltaMeta(simForm.delta).tag }}
                    </el-tag>
                    <el-tag :type="complianceColorTag(simForm.compliance)" class="mr8">
                      遵守率 {{ formatPct(simForm.compliance) }}
                    </el-tag>
                  </div>
                </el-form-item>
                <el-form-item label="预览结论">
                  <div class="preview-text">
                    {{ recommendText({
                      hasData: simForm.totalSessions > 0,
                      complianceRate: simForm.compliance,
                      delta: simForm.delta,
                      completed: simForm.completedSessions,
                      total: simForm.totalSessions
                    }) }}
                  </div>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

    </el-tabs>

    <!-- ✅ 扫码分享 Dialog（H5 客户端可扫码打开当前报告） -->
    <el-dialog
      v-model="shareOpen"
      title="扫码查看 H5 客户端版本"
      width="92%"
      :style="{ maxWidth: '380px' }"
      align-center>
      <div v-if="currentReport" class="tr-share">
        <div class="tr-share__qr">
          <img :src="qrUrl" :alt="`QR code for ${shareUrl}`" />
        </div>
        <div class="tr-share__url">
          <el-input :model-value="shareUrl" readonly>
            <template #append>
              <el-button :icon="CopyDocument" @click="onCopyShareLink">复制</el-button>
            </template>
          </el-input>
        </div>
        <p class="tr-share__hint">
          📱 <b>顾客 / 技师</b> 扫码即可在 H5 端看到本份报告（只读）。<br>
          链接携带唯一 report id，过期需重新扫码。
        </p>
        <el-divider />
        <p class="tr-share__meta">
          报告 ID：<b>#{{ currentReport.id }}</b><br>
          会员 ID：<b>{{ currentReport.memberId }}</b><br>
          生成时间：{{ formatTime(currentReport.generatedAt) }}
        </p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Document, Calendar, DataLine, TrendCharts, Histogram, PieChart, MagicStick,
  Search, DataAnalysis, SetUp, Share, Printer, Refresh, CopyDocument
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, PieChart as ECPie, LineChart } from 'echarts/charts'
import {
  TooltipComponent, LegendComponent, GridComponent, TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

import { treatmentReportApi, recommendText, deltaMeta, computeEffectScore } from '@/api/treatment-report'
import { treatmentApi } from '@/api/treatment'
import request from '@/utils/http'

echarts.use([
  BarChart, ECPie, LineChart,
  TooltipComponent, LegendComponent, GridComponent, TitleComponent,
  CanvasRenderer
])

const activeTab = ref('latest')

// ========== KPI 卡 ==========
const kpi = reactive({
  totalReports: 0,
  todayReports: 0,
  avgCompliance: 0 as number | null,
  avgDelta: 0 as number | null
})

const todayStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const loadKpi = async () => {
  try {
    const res: any = await treatmentReportApi.stats()
    const data = res.data || res || {}
    kpi.totalReports = data.totalReports ?? data.total ?? 0
    kpi.todayReports = data.todayReports ?? data.todayCount ?? 0
    kpi.avgCompliance = data.avgCompliance ?? null
    kpi.avgDelta = data.avgDelta ?? null
  } catch (e) {
    // 静默：KPI 不阻塞主流程
  }
}

// ========== Tab 1: 最新报告 ==========
const genForm = reactive({
  memberId: '' as string | number,
  planId: undefined as number | undefined,
  generatedBy: ''
})
const planOptions = ref<any[]>([])
const generateLoading = ref(false)
const currentReport = ref<any>(null)

const loadPlanOptions = async () => {
  try {
    const res: any = await treatmentApi.planPage({ pageSize: 200, status: 1 })
    const list = res.data?.records || res.data?.list || res.data || []
    planOptions.value = Array.isArray(list) ? list : []
  } catch {
    planOptions.value = []
  }
}

const resetForm = () => {
  genForm.memberId = ''
  genForm.planId = undefined
  genForm.generatedBy = ''
  currentReport.value = null
}

const onPlanChange = () => {
  // 切换方案时尝试预填 memberId（如果之前历史有）
  // 当前省略，避免误填
}

const onGenerate = async () => {
  if (!genForm.memberId) {
    ElMessage.warning('请填写会员 ID')
    return
  }
  if (!genForm.planId) {
    ElMessage.warning('请选择疗程方案')
    return
  }
  generateLoading.value = true
  try {
    const res: any = await treatmentReportApi.generate({
      memberId: genForm.memberId,
      planId: genForm.planId,
      generatedBy: genForm.generatedBy || 'SYSTEM'
    })
    currentReport.value = res.data || res
    ElMessage.success('报告已生成')
    await nextTick()
    renderAllCharts()
    loadKpi()
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    generateLoading.value = false
  }
}

// ========== ECharts ==========
const sessionsChartEl = ref<HTMLDivElement | null>(null)
const phasePieEl = ref<HTMLDivElement | null>(null)
const scoreLineEl = ref<HTMLDivElement | null>(null)
let sessionsChart: echarts.ECharts | null = null
let phasePieChart: echarts.ECharts | null = null
let scoreLineChart: echarts.ECharts | null = null

const resizeAll = () => {
  sessionsChart?.resize()
  phasePieChart?.resize()
  scoreLineChart?.resize()
}

const disposeCharts = () => {
  sessionsChart?.dispose()
  phasePieChart?.dispose()
  scoreLineChart?.dispose()
  sessionsChart = phasePieChart = scoreLineChart = null
}

const parsePhasesJson = (raw: string | null | undefined) => {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

// ====================================================================
// ✅ 按 phaseNo 分组的多系列柱状图：每个 phase 一组，4 序列（目标/完成/废弃/进行）
// ====================================================================
const renderSessionsBar = () => {
  if (!sessionsChartEl.value) return
  const phases = parsePhasesJson(currentReport.value?.phasesSummaryJson)
  if (!sessionsChart) {
    sessionsChart = echarts.init(sessionsChartEl.value)
  }
  const names = phases.map((p: any) => p.phaseName || `阶段 ${p.phaseNo}` || '阶段')

  // 单条 phase：4 个 metric（target / completed / abandoned / ongoing）
  const target    = phases.map((p: any) => Number(p.targetSessions ?? p.totalSessions ?? 0))
  const completed = phases.map((p: any) => Number(p.completedSessions ?? 0))
  const abandoned = phases.map((p: any) => Number(p.abandonedSessions ?? 0))
  const ongoing   = phases.map((p: any, i: number) =>
    Math.max(target[i] - completed[i] - abandoned[i], 0)
  )

  sessionsChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any[]) => {
        const lines = params.map((p: any) =>
          `${p.marker}${p.seriesName}：<b>${p.value}</b>`
        ).join('<br/>')
        const total = target[params[0].dataIndex]
        const done  = completed[params[0].dataIndex]
        return `<b>${params[0].axisValue}</b><br/>${lines}<br/><span style="color:#909399">目标 ${total} / 已完成 ${done}（${total ? (done*100/total).toFixed(1) : 0}%）</span>`
      }
    },
    legend: { data: ['目标', '已完成', '进行中', '已废弃'], top: 0 },
    grid: { left: 40, right: 16, top: 36, bottom: 32, containLabel: true },
    xAxis: {
      type: 'category',
      data: names.length ? names : ['（暂无阶段）'],
      axisLabel: { fontSize: 11, rotate: names.length > 4 ? 25 : 0 }
    },
    yAxis: { type: 'value', name: 'session 数', minInterval: 1 },
    series: [
      {
        name: '目标', type: 'bar', stack: 'total', data: target,
        itemStyle: { color: '#dcdfe6' }, barWidth: 28,
        emphasis: { focus: 'series' }
      },
      {
        name: '已完成', type: 'bar', stack: 'total', data: completed,
        itemStyle: { color: '#67c23a' },
        emphasis: { focus: 'series' }
      },
      {
        name: '已废弃', type: 'bar', stack: 'total', data: abandoned,
        itemStyle: { color: '#f56c6c' },
        emphasis: { focus: 'series' }
      },
      {
        name: '进行中', type: 'bar', stack: 'total', data: ongoing,
        itemStyle: { color: '#409eff', opacity: 0.7 },
        emphasis: { focus: 'series' }
      }
    ]
  }, true)
}

// ====================================================================
// ✅ 按 phaseNo 拆分的玫瑰图：每 phase 一片花瓣，半径 = 该 phase 的完成率
// ====================================================================
const renderPhasePie = () => {
  if (!phasePieEl.value) return
  const phases = parsePhasesJson(currentReport.value?.phasesSummaryJson)

  if (!phasePieChart) phasePieChart = echarts.init(phasePieEl.value)

  // 没有 phases 时回落到汇总环图（保持视觉一致）
  if (!phases.length) {
    const total = currentReport.value?.totalSessions ?? 0
    const completed = currentReport.value?.completedSessions ?? 0
    const abandoned = currentReport.value?.abandonedSessions ?? 0
    const ongoing = Math.max(total - completed - abandoned, 0)
    phasePieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 次 ({d}%)' },
      legend: { orient: 'vertical', right: 8, top: 'middle' },
      series: [{
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['38%', '50%'],
        data: [
          { name: '已完成', value: completed, itemStyle: { color: '#67c23a' } },
          { name: '进行中', value: ongoing, itemStyle: { color: '#409eff' } },
          { name: '已废弃', value: abandoned, itemStyle: { color: '#f56c6c' } }
        ]
      }]
    }, true)
    return
  }

  // ✅ 玫瑰图 / 南丁格尔图：每 phase 一片，半径 = 完成率
  const data = phases.map((p: any, idx: number) => {
    const tgt = Number(p.targetSessions ?? p.totalSessions ?? 0)
    const done = Number(p.completedSessions ?? 0)
    const rate = tgt > 0 ? Math.round((done / tgt) * 1000) / 10 : 0
    return {
      name: p.phaseName || `阶段 ${p.phaseNo || idx + 1}`,
      value: rate,
      rawCompleted: done,
      rawTarget: tgt,
      itemStyle: { color: ['#67c23a', '#409eff', '#9c27b0', '#e6a23c', '#ff7eb3', '#9aa9c0'][idx % 6] }
    }
  })

  phasePieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const raw = p.data
        return `<b>${p.name}</b><br/>完成率：<b>${p.value}%</b><br/>${raw.rawCompleted} / ${raw.rawTarget} 次`
      }
    },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'middle',
      textStyle: { fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['24%', '74%'],
      center: ['38%', '50%'],
      roseType: 'radius',           // ← 半径映射 = 完成率
      avoidLabelOverlap: true,
      label: {
        show: true,
        formatter: '{b}\n{c}%',
        fontSize: 11,
        color: '#303133'
      },
      labelLine: { length: 6, length2: 4 },
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      data
    }]
  }, true)
}

const renderScoreLine = () => {
  if (!scoreLineEl.value) return
  if (!scoreLineChart) scoreLineChart = echarts.init(scoreLineEl.value)

  const before = currentReport.value?.effectScoreBefore
  const after = currentReport.value?.effectScoreAfter
  const delta = currentReport.value?.effectScoreDelta

  // 单一报告时只有两点；历史 >1 时可叠曲线，这里前端只画当前一次
  const xData = ['疗程前', '疗程后']
  const yData = [
    before != null ? Number(before) : null,
    after != null ? Number(after) : null
  ]

  scoreLineChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const arr = params.map(p => `${p.marker} ${p.seriesName}：${p.value != null ? Number(p.value).toFixed(2) : '—'}`).join('<br/>')
        return `${params[0].axisValue}<br/>${arr}${delta != null ? `<br/><b>差值：${Number(delta).toFixed(2)}</b>` : ''}`
      }
    },
    legend: { data: ['综合分'], top: 0 },
    grid: { left: 40, right: 16, top: 36, bottom: 28, containLabel: true },
    xAxis: { type: 'category', data: xData, boundaryGap: false },
    yAxis: { type: 'value', name: '分', minInterval: 5 },
    series: [
      {
        name: '综合分', type: 'line', data: yData, smooth: true,
        lineStyle: { color: '#9c27b0', width: 3 },
        itemStyle: { color: '#9c27b0' },
        symbolSize: 12,
        connectNulls: true,
        label: { show: true, position: 'top', formatter: (p: any) => p.value != null ? Number(p.value).toFixed(1) : '' }
      }
    ]
  }, true)
}

const renderAllCharts = () => {
  renderSessionsBar()
  renderPhasePie()
  renderScoreLine()
}

watch(activeTab, async (t) => {
  if (t === 'history') loadHistory()
  if (t === 'rules') return
})

// ========== Tab 2: 历史快照 ==========
const historyMemberId = ref<any>('')
const historyList = ref<any[]>([])
const historyLoading = ref(false)

const loadHistory = async () => {
  if (!historyMemberId.value) {
    ElMessage.warning('请填写会员 ID')
    return
  }
  historyLoading.value = true
  try {
    const res: any = await treatmentReportApi.listByMember(historyMemberId.value)
    historyList.value = (res.data && (Array.isArray(res.data) ? res.data : res.data.list)) || []
  } catch (e) {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

const viewReport = async (id: number | string) => {
  try {
    const res: any = await treatmentReportApi.getById(id)
    currentReport.value = res.data || res
    activeTab.value = 'latest'
    await nextTick()
    renderAllCharts()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

// ========== Tab 3: 规则预览 ==========
const simForm = reactive({
  totalSessions: 12,
  completedSessions: 8,
  compliance: 0.6,
  delta: 3.5
})

const ruleTable = [
  {
    priority: 1,
    condition: 'totalSessions === 0 或 hasData=false',
    text: recommendText({ hasData: false, complianceRate: null, delta: null, completed: 0, total: 0 }),
    scenario: '新客户、尚无 session'
  },
  {
    priority: 2,
    condition: '遵守率 ≥ 80%',
    text: '客户依从性良好，已坚持完成大部分护理建议，建议继续保持当前节奏。',
    scenario: '复购意愿强'
  },
  {
    priority: 3,
    condition: '遵守率 < 50%',
    text: '护理依从性偏低，建议运营人员加强触达与提醒，必要时重新评估方案可行性。',
    scenario: '有放弃风险'
  },
  {
    priority: 4,
    condition: '效果分差 Δ ≥ +5',
    text: '效果分显著提升（≥ 5 分），可建议客户续单或转介绍。',
    scenario: '促成续单 / 升单'
  },
  {
    priority: 5,
    condition: '效果分差 Δ ≤ -5',
    text: '效果分出现下降趋势（≤ -5 分），建议复盘方案 / 调整技师 / 复核肤质档案。',
    scenario: '投诉预警'
  },
  {
    priority: 6,
    condition: '其余（默认）',
    text: '效果分小幅波动，仍在正常区间内，建议持续观察 2~3 个 session 后再生成下份报告。',
    scenario: '日常随访'
  }
]

// ========== 工具 ==========
const formatPct = (rate: number | null | undefined) => {
  if (rate == null) return '—'
  return `${(Number(rate) * 100).toFixed(1)}%`
}

const formatNum = (n: number | null | undefined, digits = 2) => {
  if (n == null) return '—'
  return Number(n).toFixed(digits)
}

const formatTime = (t: string | null | undefined) => {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN', { hour12: false })
}

const complianceColorTag = (rate: number | null | undefined): 'success' | 'warning' | 'danger' | 'info' => {
  if (rate == null) return 'info'
  if (rate >= 0.8) return 'success'
  if (rate >= 0.5) return 'warning'
  return 'danger'
}

// ====================================================================
// ✅ 扫码分享（H5 客户端可扫码查看当前报告，只读）
// ====================================================================
const shareOpen = ref(false)
const shareUrl  = ref('')
const qrUrl     = ref('')

/**
 * 与 style-gallery 共用同款 QR API；
 * 此处构造 `${origin}/biz/treatment-report?share=${id}` 让 H5 端读 query 后渲染只读视图
 */
const buildReportShareUrl = (id: number | string): string => {
  if (typeof window === 'undefined') return `/biz/treatment-report?share=${id}`
  return `${window.location.origin}/biz/treatment-report?share=${id}`
}
const buildReportQrUrl = (text: string, size = 220): string => {
  const params = new URLSearchParams({ data: text, size: `${size}x${size}`, format: 'png', margin: '8', ecc: 'M' })
  return `https://api.qrserver.com/v1/create-qr-code/?${params.toString()}`
}

const onShareQr = () => {
  if (!currentReport.value?.id) {
    ElMessage.warning('请先生成报告后再分享')
    return
  }
  shareUrl.value = buildReportShareUrl(currentReport.value.id)
  qrUrl.value    = buildReportQrUrl(shareUrl.value)
  shareOpen.value = true
}

const onCopyShareLink = async () => {
  if (!shareUrl.value) return
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('链接已复制 ✅')
  } catch {
    const input = document.createElement('input')
    input.value = shareUrl.value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('链接已复制 ✅')
  }
}

// ====================================================================
// ✅ 导出 PDF（通过 window.print + @media print CSS，无新依赖）
// ====================================================================
/**
 * 触发浏览器原生打印对话框。
 * 用户可另存为 PDF / 直发邮件 / 选物理打印机。
 * @media print 样式已隐藏非报告区（KPI 总览 / Tab 栏 / 操作按钮 / 其他 tab）。
 */
const onExportPdf = () => {
  if (!currentReport.value) {
    ElMessage.warning('请先生成报告')
    return
  }
  // 渲染一帧让 chart 已 ready；再触发打印
  nextTick(() => {
    renderAllCharts()
    setTimeout(() => {
      try {
        window.print()
      } catch (e: any) {
        ElMessage.error('浏览器不支持打印：' + (e?.message || ''))
      }
    }, 250)
  })
}

onMounted(async () => {
  loadKpi()
  loadPlanOptions()
  await nextTick()
  window.addEventListener('resize', resizeAll)

  // 处理从分享链接进来的场景：?share=ID → 自动切到「最新报告」Tab 并拉详情
  try {
    const params = new URLSearchParams(window.location.search)
    const shareId = params.get('share')
    if (shareId) {
      try {
        const res: any = await treatmentReportApi.getById(shareId)
        currentReport.value = res?.data || res
        activeTab.value = 'latest'
        await nextTick()
        renderAllCharts()
      } catch {
        ElMessage.info(`报告 #${shareId} 不存在或无权访问`)
      }
    }
  } catch { /* 忽略 */ }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAll)
  disposeCharts()
})
</script>

<style scoped>
.tr-page {
  padding: 16px;
}

.mb16 { margin-bottom: 16px; }
.mt12 { margin-top: 12px; }
.mr8 { margin-right: 8px; }
.ml4 { margin-left: 4px; }

.kpi-card {
  border-radius: 10px;
  overflow: hidden;
}

.kpi-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.kpi-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.kpi-blue .kpi-icon    { background: linear-gradient(135deg, #409eff, #79bbff); }
.kpi-green .kpi-icon   { background: linear-gradient(135deg, #67c23a, #95d475); }
.kpi-orange .kpi-icon  { background: linear-gradient(135deg, #e6a23c, #f3d19e); }
.kpi-purple .kpi-icon  { background: linear-gradient(135deg, #9c27b0, #ce93d8); }

.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.1;
}
.kpi-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.filter-bar {
  margin-bottom: 12px;
}

.filter-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  margin-left: 4px;
}

.empty-tip {
  margin: 24px 0;
}

.chart-area {
  width: 100%;
  height: 320px;
}

.recommendation-text {
  min-height: 200px;
}

.reco-line {
  line-height: 1.7;
  color: #303133;
  font-size: 13px;
  margin: 0;
}

.sim-result {
  margin-top: 6px;
}

.preview-text {
  background: #f5f7fa;
  border-left: 3px solid #409eff;
  padding: 12px;
  border-radius: 4px;
  color: #303133;
  font-size: 13px;
  line-height: 1.7;
}

/* ============ ✅ 报告操作工具栏 ============ */
.report-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 0 0 12px;
}

/* ============ ✅ 扫码分享 Dialog ============ */
.tr-share {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 0;
}
.tr-share__qr {
  width: 220px;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  padding: 8px;
  border: 1px solid #f0f0f3;
  margin-bottom: 14px;
}
.tr-share__qr img {
  width: 100%;
  height: 100%;
  display: block;
}
.tr-share__url {
  width: 100%;
  margin-bottom: 10px;
}
.tr-share__hint,
.tr-share__meta {
  text-align: center;
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
  margin: 0;
  width: 100%;
}
.tr-share__meta {
  text-align: left;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
}

/* ============ ✅ 打印 / PDF 样式 ============ */
@media print {
  /* 隐藏非报告区元素 */
  .no-print,
  .el-tabs__nav,
  .el-tabs__header,
  .kpi-card,
  .filter-bar,
  .report-actions,
  .tr-page > .tr-tabs > .el-tabs__content-wrap > .el-tab-pane[name="history"],
  .tr-page > .tr-tabs > .el-tabs__content-wrap > .el-tab-pane[name="rules"] {
    display: none !important;
  }

  /* 强制全部 tab-pane 内容可见（不受 active 控制） */
  .tr-page .el-tabs__content {
    display: block !important;
  }
  .tr-page .el-tab-pane {
    display: block !important;
    opacity: 1 !important;
    visibility: visible !important;
  }

  .tr-page {
    padding: 0;
    background: #fff;
  }
  .el-card {
    box-shadow: none !important;
    border: 1px solid #e0e0e0 !important;
    page-break-inside: avoid;
  }
  .chart-area {
    height: 240px !important;
    page-break-inside: avoid;
  }
  .el-statistic__content {
    font-size: 18px !important;
  }

  /* PDF 在 A4 上尽量铺满 */
  @page {
    size: A4;
    margin: 14mm;
  }
}
</style>
