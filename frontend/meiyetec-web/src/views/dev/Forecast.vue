<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">客流预测与智能排班</span>
          <el-space>
            <el-button type="primary" @click="runEtl" :loading="etlLoading">
              {{ etlLoading ? 'ETL 运行中...' : '运行 ETL + 预测' }}
            </el-button>
            <el-button @click="loadCurve">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 预测曲线 -->
        <el-tab-pane label="预测曲线" name="curve">
          <div class="chart-wrapper">
            <div ref="curveChart" style="width:100%;height:360px"></div>
          </div>

          <el-descriptions :column="4" border style="margin-top:16px" v-if="accuracy">
            <el-descriptions-item label="算法">
              <el-tag type="info">{{ accuracy.algorithm || 'EWMA_V2' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="MAE">
              <el-tag :type="accuracy.conclusion === 'GOOD' ? 'success' : accuracy.conclusion === 'FAIR' ? 'warning' : 'danger'">
                {{ accuracy.mae || '—' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="MAPE">
              <span>{{ accuracy.mape || '—' }}%</span>
            </el-descriptions-item>
            <el-descriptions-item label="评估结论">
              <el-tag :type="accuracy.conclusion === 'GOOD' ? 'success' : accuracy.conclusion === 'FAIR' ? 'warning' : 'danger'">
                {{ accuracy.conclusion || '—' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- 客流热力图 -->
        <el-tab-pane label="客流热力图" name="heatmap">
          <div ref="heatmapChart" style="width:100%;height:380px;margin-top:8px"></div>
          <el-alert type="info" :closable="false" style="margin-top:8px">
            <template #title>解读：横向为时间（0-23时），纵向为星期（周一~周日），颜色越深=预约越多</template>
          </el-alert>
        </el-tab-pane>

        <!-- 智能排班建议 -->
        <el-tab-pane label="智能排班建议" name="schedule">
          <el-table :data="recommendations" v-loading="scheduleLoading" stripe>
            <el-table-column prop="scheduleDate" label="日期" width="110">
              <template #default="{ row }">
                <span class="mono">{{ row.scheduleDate }}</span>
              </template>
            </el-table-column>
            <el-table-column label="覆盖率" width="140">
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.coverageRate) || 0"
                  :color="(Number(row.coverageRate) || 0) >= 90 ? '#67c23a' : '#e6a23c'"
                />
              </template>
            </el-table-column>
            <el-table-column label="利用率" width="140">
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.utilizationRate) || 0"
                  :color="(Number(row.utilizationRate) || 0) >= 80 ? '#67c23a' : '#e6a23c'"
                />
              </template>
            </el-table-column>
            <el-table-column prop="score" label="评分" width="80">
              <template #default="{ row }">
                <el-tag :type="(Number(row.score) || 0) >= 80 ? 'success' : 'warning'">
                  {{ Number(row.score || 0).toFixed(0) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'APPLIED' ? 'success' : 'info'">
                  {{ row.status === 'APPLIED' ? '已应用' : '待应用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="viewSchedule(row)">查看详情</el-button>
                <el-button
                  size="small"
                  :disabled="row.status === 'APPLIED'"
                  @click="applySchedule(row)"
                >
                  一键应用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 准确度趋势 -->
        <el-tab-pane label="准确度趋势" name="accuracy">
          <div ref="accuracyChart" style="width:100%;height:300px;margin-top:8px"></div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 排班详情弹窗 -->
    <el-dialog v-model="showDetail" title="排班详情" width="600px">
      <el-descriptions :column="2" border v-if="detailData">
        <el-descriptions-item label="日期">{{ detailData.scheduleDate }}</el-descriptions-item>
        <el-descriptions-item label="覆盖率">{{ detailData.coverageRate }}%</el-descriptions-item>
        <el-descriptions-item label="利用率">{{ detailData.utilizationRate }}%</el-descriptions-item>
        <el-descriptions-item label="评分">{{ detailData.score }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>技师安排</el-divider>
      <el-table :data="scheduleDetail" size="small" stripe>
        <el-table-column prop="employeeName" label="技师" />
        <el-table-column prop="skillLevel" label="等级" width="80" />
        <el-table-column prop="assignedSlots" label="分配槽位" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { sysApi } from '@/api/sys'

const activeTab = ref('curve')
const etlLoading = ref(false)
const scheduleLoading = ref(false)
const showDetail = ref(false)
const detailData = ref<any>(null)
const scheduleDetail = ref<any[]>([])

const curveChart = ref<HTMLDivElement>()
const heatmapChart = ref<HTMLDivElement>()
const accuracyChart = ref<HTMLDivElement>()

const curveData = ref<{ history: any[]; forecast: any[] }>({ history: [], forecast: [] })
const heatmapData = ref<any>(null)
const recommendations = ref<any[]>([])
const accuracy = ref<any>(null)
const accuracyTrend = ref<any[]>([])

async function runEtl() {
  etlLoading.value = true
  try {
    const r: any = await sysApi.forecastRunEtl({ windowDays: 90 })
    ElMessage.success(`ETL 完成：${r?.data?.featuresCreated || 0} 条特征，${r?.data?.forecastsGenerated || 0} 条预测`)
    await Promise.all([loadCurve(), loadHeatmap(), loadRecommendations()])
  } catch (e: any) {
    ElMessage.error('ETL 失败: ' + (e?.message || '未知错误'))
  } finally {
    etlLoading.value = false
  }
}

async function loadCurve() {
  try {
    const resp: any = await sysApi.forecastCurve(14, 14)
    curveData.value = resp.data || { history: [], forecast: [] }
    if (curveData.value.accuracy && curveData.value.accuracy.length > 0) {
      accuracy.value = curveData.value.accuracy[0]
    }
    await nextTick()
    renderCurve()
  } catch (e) {
    console.warn('loadCurve failed', e)
  }
}

async function loadHeatmap() {
  try {
    const resp: any = await sysApi.forecastHeatmap(30)
    heatmapData.value = resp.data
    await nextTick()
    renderHeatmap()
  } catch (e) {
    console.warn('loadHeatmap failed', e)
  }
}

async function loadRecommendations() {
  scheduleLoading.value = true
  try {
    const resp: any = await sysApi.forecastScheduleList(7)
    recommendations.value = resp.data || []
  } catch (e) {
    recommendations.value = []
  } finally {
    scheduleLoading.value = false
  }
}

async function loadAccuracyTrend() {
  try {
    const resp: any = await sysApi.forecastAccuracyTrend(10)
    accuracyTrend.value = resp.data || []
    await nextTick()
    renderAccuracyChart()
  } catch (e) {
    console.warn('loadAccuracyTrend failed', e)
  }
}

function renderCurve() {
  if (!curveChart.value) return
  const chart = echarts.init(curveChart.value)
  const hist = curveData.value.history || []
  const fc = curveData.value.forecast || []

  const dates = hist.map((h: any) => h.dt)
  const histVals = hist.map((h: any) => h.value)
  const fcDates = fc.map((f: any) => f.dt)
  const fcVals = fc.map((f: any) => f.value)
  const lower = fc.map((f: any) => f.lower_bound)
  const upper = fc.map((f: any) => f.upper_bound)

  chart.setOption({
    title: { text: '客流趋势（历史 + 预测）', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { top: 30, data: ['实际客流', '预测客流'] },
    xAxis: {
      type: 'category',
      data: [...dates, ...fcDates],
      boundaryGap: false
    },
    yAxis: { type: 'value', name: '预约数' },
    series: [
      {
        name: '实际客流',
        type: 'line',
        data: [...histVals, ...new Array(fcDates.length).fill(null)],
        smooth: true,
        itemStyle: { color: '#409eff' }
      },
      {
        name: '预测客流',
        type: 'line',
        data: [...new Array(dates.length).fill(null), ...fcVals],
        smooth: true,
        itemStyle: { color: '#e6a23c' },
        lineStyle: { type: 'dashed' }
      },
      {
        name: '置信区间',
        type: 'line',
        data: [...new Array(dates.length).fill(null), ...upper],
        smooth: true,
        lineStyle: { opacity: 0 },
        stack: 'confidence',
        areaStyle: { color: 'rgba(230,162,60,0.1)' },
        symbol: 'none'
      },
      {
        name: '置信下界',
        type: 'line',
        data: [...new Array(dates.length).fill(null), ...lower],
        smooth: true,
        lineStyle: { opacity: 0 },
        symbol: 'none'
      }
    ]
  })
}

function renderHeatmap() {
  if (!heatmapChart.value || !heatmapData.value) return
  const chart = echarts.init(heatmapChart.value)
  const matrix: number[][] = heatmapData.value.matrix || []
  const dowLabels = heatmapData.value.dowLabels || ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

  // 找到最大值用于归一化
  let maxVal = 0
  for (const row of matrix) {
    for (const v of row) {
      if (v > maxVal) maxVal = v
    }
  }

  const data: [number, number, number][] = []
  for (let dow = 0; dow < 7; dow++) {
    for (let hour = 0; hour < 24; hour++) {
      const val = matrix[dow]?.[hour] || 0
      data.push([hour, dow, val])
    }
  }

  chart.setOption({
    title: { text: '客流热力图（周×时）', left: 'center' },
    tooltip: {
      position: 'top',
      formatter: (p: any) => `${dowLabels[p.data[1]]} ${p.data[0]}:00<br/>预约数: ${p.data[2].toFixed(1)}`
    },
    grid: { left: 60, right: 20, top: 40, bottom: 50 },
    xAxis: { type: 'category', data: Array.from({ length: 24 }, (_, i) => `${i}:00`), name: '小时' },
    yAxis: { type: 'category', data: dowLabels, name: '星期' },
    visualMap: {
      min: 0, max: maxVal || 10,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#e8f3ff', '#409eff', '#1d39c4'] }
    },
    series: [{
      type: 'heatmap',
      data,
      label: { show: maxVal > 5 },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } }
    }]
  })
}

function renderAccuracyChart() {
  if (!accuracyChart.value) return
  const chart = echarts.init(accuracyChart.value)
  const trend = accuracyTrend.value || []

  const dates = trend.map((t: any) => t.evalDate)
  const mae = trend.map((t: any) => t.mae)
  const mape = trend.map((t: any) => t.mape)

  chart.setOption({
    title: { text: '预测准确度趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { top: 30, data: ['MAE', 'MAPE(%)'] },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: '值' },
    series: [
      { name: 'MAE', type: 'bar', data: mae, itemStyle: { color: '#67c23a' } },
      {
        name: 'MAPE(%)',
        type: 'line',
        data: mape,
        yAxisIndex: 0,
        itemStyle: { color: '#f56c6c' },
        smooth: true
      }
    ]
  })
}

async function viewSchedule(row: any) {
  try {
    const resp: any = await sysApi.request({ method: 'GET', url: `/ai/forecast/schedule/recommendation/${row.scheduleDate}` })
    detailData.value = resp.data
    try {
      scheduleDetail.value = JSON.parse(resp.data?.recommendationJson || '[]')
    } catch {
      scheduleDetail.value = []
    }
    showDetail.value = true
  } catch (e) {
    ElMessage.error('加载失败')
  }
}

async function applySchedule(row: any) {
  try {
    await sysApi.request({ method: 'POST', url: `/ai/forecast/schedule/recommendation/${row.scheduleDate}/apply` })
    ElMessage.success('已应用')
    await loadRecommendations()
  } catch (e) {
    ElMessage.error('应用失败')
  }
}

onMounted(async () => {
  await loadCurve()
  await loadHeatmap()
  await loadRecommendations()
  await loadAccuracyTrend()

  window.addEventListener('resize', () => {
    ;(curveChart.value && echarts.getInstanceByDom(curveChart.value))?.resize()
    ;(heatmapChart.value && echarts.getInstanceByDom(heatmapChart.value))?.resize()
    ;(accuracyChart.value && echarts.getInstanceByDom(accuracyChart.value))?.resize()
  })
})
</script>

<style scoped>
.page { padding: 16px; max-width: 1200px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.chart-wrapper { background: #fafafa; border-radius: 4px; padding: 8px }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px }
</style>
