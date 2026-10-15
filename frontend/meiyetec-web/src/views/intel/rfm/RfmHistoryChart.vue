<template>
  <div class="rfm-history">
    <div class="rfm-history__head">
      <div class="rfm-history__title">RFM 历史趋势</div>
      <div class="rfm-history__range">
        <el-radio-group v-model="days" size="small" @change="reload">
          <el-radio-button :value="30">30天</el-radio-button>
          <el-radio-button :value="60">60天</el-radio-button>
          <el-radio-button :value="90">90天</el-radio-button>
        </el-radio-group>
      </div>
    </div>
    <div ref="chartEl" class="rfm-history__chart" />
    <el-empty
      v-if="!isLoading && points.length === 0"
      description="该会员暂无历史快照"
    />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { RfmHistoryPoint } from '../../../api/rfm'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  memberId: number | null
}>()

const days = ref<number>(90)
const points = ref<RfmHistoryPoint[]>([])
const isLoading = ref(false)
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

async function loadHistory() {
  if (!props.memberId) {
    points.value = []
    return
  }
  isLoading.value = true
  try {
    const { rfmApi } = await import('../../../api/rfm')
    const data = await rfmApi.memberRfmHistory(props.memberId, days.value)
    points.value = Array.isArray(data) ? data : []
  } catch {
    points.value = []
  } finally {
    isLoading.value = false
  }
}

watch(() => props.memberId, () => {
  loadHistory().then(() => chart?.setOption(buildOption(), true))
})

async function reload() {
  await loadHistory()
  chart?.setOption(buildOption(), true)
}

function buildOption() {
  const xs = points.value.map(p => p.snapshotDate)
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const head = `<b>${params[0]?.axisValue ?? ''}</b>`
        const lines = params.map(p =>
          `${p.marker}${p.seriesName}：<b>${p.value}</b>`
        ).join('<br/>')
        const seg = points.value.find(p => p.snapshotDate === params[0]?.axisValue)
        const segLabel = seg ? `<br/>分群：${seg.segmentName ?? seg.segment ?? '—'}` : ''
        return `${head}<br/>${lines}${segLabel}`
      }
    },
    legend: {
      data: ['R', 'F', 'M', '频次', '金额'],
      top: 0,
      textStyle: { fontSize: 12 }
    },
    grid: { left: 40, right: 60, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: xs,
      axisLabel: { fontSize: 11, color: '#909399' }
    },
    yAxis: [
      {
        type: 'value',
        name: 'R/F/M 评分',
        min: 0,
        max: 5,
        interval: 1,
        splitLine: { lineStyle: { color: '#f0f1f5' } },
        axisLabel: { fontSize: 11, color: '#909399' }
      },
      {
        type: 'value',
        name: '金额/频次',
        position: 'right',
        splitLine: { show: false },
        axisLabel: { fontSize: 11, color: '#909399' }
      }
    ],
    series: [
      {
        name: 'R', type: 'line', smooth: true, yAxisIndex: 0,
        data: points.value.map(p => p.rScore),
        itemStyle: { color: '#5B8DEF' }, symbol: 'circle', symbolSize: 6
      },
      {
        name: 'F', type: 'line', smooth: true, yAxisIndex: 0,
        data: points.value.map(p => p.fScore),
        itemStyle: { color: '#67C23A' }, symbol: 'circle', symbolSize: 6
      },
      {
        name: 'M', type: 'line', smooth: true, yAxisIndex: 0,
        data: points.value.map(p => p.mScore),
        itemStyle: { color: '#E6A23C' }, symbol: 'circle', symbolSize: 6
      },
      {
        name: '频次', type: 'line', smooth: true, yAxisIndex: 1,
        data: points.value.map(p => p.frequency),
        itemStyle: { color: '#909399' }, lineStyle: { type: 'dashed' }
      },
      {
        name: '金额', type: 'line', smooth: true, yAxisIndex: 1,
        data: points.value.map(p => Number(p.monetary ?? 0)),
        itemStyle: { color: '#B37FEB' }, lineStyle: { type: 'dashed' }
      }
    ]
  }
}

function resize() { chart?.resize() }

onMounted(() => {
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
    chart.setOption(buildOption())
    window.addEventListener('resize', resize)
    reload()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.rfm-history {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
}
.rfm-history__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.rfm-history__title {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}
.rfm-history__chart {
  width: 100%;
  height: 280px;
}
</style>