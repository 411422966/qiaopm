<template>
  <div class="page-employee">
    <div class="page-header">
      <h2>员工绩效</h2>
      <div class="header-actions">
        <el-select v-model="range" style="width: 160px" @change="load">
          <el-option label="最近 7 天" value="last_7_days" />
          <el-option label="最近 30 天" value="last_30_days" />
          <el-option label="最近 90 天" value="last_quarter" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="kpi-label">参与员工数</div>
          <div class="kpi-value">{{ rows.length }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="kpi-label">总订单数</div>
          <div class="kpi-value">{{ totalOrders }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="kpi-label">总销售额 (元)</div>
          <div class="kpi-value">¥{{ formatMoney(totalRevenue) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="chart-card" shadow="hover">
      <div ref="chartRef" class="chart" />
    </el-card>

    <el-card class="table-card" shadow="hover">
      <template #header><strong>员工排行</strong></template>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="employeeId" label="员工ID" width="100" />
        <el-table-column prop="employeeName" label="员工姓名" />
        <el-table-column prop="orderCount" label="订单数" width="120" />
        <el-table-column label="销售额(元)" width="160">
          <template #default="{ row }">¥{{ formatMoney(row.revenue) }}</template>
        </el-table-column>
        <el-table-column label="客单价(元)" width="140">
          <template #default="{ row }">
            ¥{{ row.orderCount ? formatMoney(Number(row.revenue) / Number(row.orderCount)) : '0.00' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { User, DataLine } from '@element-plus/icons-vue'
import { reportApi } from '@/api/report'
import * as echarts from 'echarts'

const range = ref('last_30_days')
const loading = ref(false)
const rows = ref<any[]>([])
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const totalOrders = computed(() => rows.value.reduce((s, r) => s + Number(r.orderCount || 0), 0))
const totalRevenue = computed(() => rows.value.reduce((s, r) => s + Number(r.revenue || 0), 0))

function formatMoney(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

async function load() {
  loading.value = true
  try {
    const days = range.value === 'last_7_days' ? 7 : range.value === 'last_quarter' ? 90 : 30
    const res: any = await reportApi.employeePerformance(days)
    rows.value = res.data || []
    await nextTick()
    renderChart()
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value || rows.value.length === 0) return
  if (chart) { chart.dispose(); chart = null }
  chart = echarts.init(chartRef.value)
  const top = [...rows].slice(0, 20)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['订单数', '销售额'], top: 0 },
    grid: { left: 60, right: 60, bottom: 60, top: 40 },
    xAxis: { type: 'category', data: top.map((r: any) => r.employeeName) },
    yAxis: [
      { type: 'value', name: '订单数', position: 'left' },
      { type: 'value', name: '销售额(元)', position: 'right' }
    ],
    series: [
      { name: '订单数', type: 'bar', data: top.map((r: any) => Number(r.orderCount) || 0), itemStyle: { color: '#409EFF' } },
      { name: '销售额', type: 'bar', yAxisIndex: 1, data: top.map((r: any) => Number(r.revenue) || 0), itemStyle: { color: '#67C23A' } }
    ]
  })
}

function handleResize() { chart?.resize() }
onMounted(() => { load(); window.addEventListener('resize', handleResize) })
onUnmounted(() => { chart?.dispose(); window.removeEventListener('resize', handleResize) })
</script>

<style scoped>
.page-employee { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.header-actions { display: flex; gap: 12px; }
.kpi-row { margin-bottom: 16px; }
.kpi-label { color: #909399; font-size: 13px; margin-bottom: 4px; }
.kpi-value { font-size: 24px; font-weight: 600; color: #303133; }
.chart-card { margin-bottom: 16px; }
.chart { width: 100%; height: 360px; }
.table-card { margin-bottom: 16px; }
</style>
