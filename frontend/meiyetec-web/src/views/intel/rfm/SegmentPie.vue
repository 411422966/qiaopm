<template>
  <div class="segment-pie">
    <div ref="chartEl" class="segment-pie__chart" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { SEGMENT_META, SEGMENT_FALLBACK, type SegmentCode } from '../../../api/rfm'
import type { SegmentOverviewItem } from '../../../api/rfm'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  segments: SegmentOverviewItem[]
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function buildOption() {
  const data = props.segments.map(s => {
    const meta = SEGMENT_META[s.segment as SegmentCode] ?? SEGMENT_FALLBACK
    return {
      name: s.segmentName ?? s.segment,
      value: s.memberCount ?? 0,
      itemStyle: { color: meta.color }
    }
  })

  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const total = data.reduce((s, d) => s + d.value, 0) || 1
        const pct = ((p.value / total) * 100).toFixed(1)
        return `<b>${p.name}</b><br/>会员数：${p.value}<br/>占比：${pct}%`
      }
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 8,
      top: 'middle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { fontSize: 12, color: '#606266' }
    },
    series: [
      {
        name: 'RFM 分群',
        type: 'pie',
        radius: ['45%', '72%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: { show: false },
        labelLine: { show: false },
        data
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
  }
})

watch(
  () => props.segments,
  () => { chart?.setOption(buildOption(), true) },
  { deep: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.segment-pie { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.segment-pie__chart { width: 100%; height: 320px; }
</style>