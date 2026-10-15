<template>
  <div class="cohort-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="队列定义" name="def">
        <el-button type="primary" @click="defDialog = true">新建队列</el-button>
        <el-table :data="defs" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="cohortCode" label="编码" width="150"/>
          <el-table-column prop="cohortName" label="名称" width="200"/>
          <el-table-column prop="cohortType" label="类型" width="120"/>
          <el-table-column prop="metric" label="指标"/>
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="runAnalyze(row.id)">运行分析</el-button>
              <el-button size="small" @click="showResults(row.id)">查看结果</el-button>
              <el-button size="small" type="primary" @click="loadRetentionFunnel(row.id)">留存漏斗</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="分析结果" name="result">
        <div ref="funnelChartRef" v-if="funnelData.length" style="width:100%; height:420px; margin-bottom:12px;"></div>
        <el-empty v-else description="尚无留存漏斗数据，请先运行分析" />
        <el-table :data="results" style="width:100%; margin-top: 8px;">
          <el-table-column prop="cohortPeriod" label="期间"/>
          <el-table-column prop="size" label="人数"/>
          <el-table-column prop="value" label="数值"/>
          <el-table-column prop="valueType" label="类型"/>
          <el-table-column prop="periodIndex" label="期数"/>
          <el-table-column prop="retentionRate" label="留存率">
            <template #default="{ row }">
              <span :style="{ color: parseFloat(row.retentionRate) >= 50 ? '#67c23a' : parseFloat(row.retentionRate) >= 20 ? '#e6a23c' : '#f56c6c' }">
                {{ row.retentionRate ? (Number(row.retentionRate).toFixed(2) + '%') : '-' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="defDialog" title="新建队列" width="500px">
      <el-form :model="defForm" label-width="120px">
        <el-form-item label="编码"><el-input v-model="defForm.cohortCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="defForm.cohortName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="defForm.cohortType">
            <el-option label="月度" value="MONTHLY"/>
            <el-option label="周度" value="WEEKLY"/>
            <el-option label="季度" value="QUARTERLY"/>
          </el-select>
        </el-form-item>
        <el-form-item label="指标"><el-input v-model="defForm.metric" placeholder="REVENUE/RETENTION" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDef">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { cohortApi } from '@/api/cohort'

const tab = ref('def')
const defs = ref<any[]>([])
const results = ref<any[]>([])
const funnelData = ref<{ name: string; value: number; rate: string }[]>([])
const defDialog = ref(false)
const defForm = ref<any>({ cohortCode: 'C' + Date.now(), cohortName: '月度队列', cohortType: 'MONTHLY', metric: 'REVENUE' })
const funnelChartRef = ref<HTMLDivElement>()
let funnelChart: echarts.ECharts | null = null

async function loadDefs() { const { data } = await cohortApi.listDefinitions(); defs.value = data || [] }
async function runAnalyze(id: number) {
  const { data } = await cohortApi.analyze(id)
  ElMessage.success(`已分析 ${data.computed} 条`)
}
async function showResults(id: number) {
  tab.value = 'result'
  const { data } = await cohortApi.listResults(id)
  results.value = data || []
  funnelData.value = []
}

async function loadRetentionFunnel(cohortId: number) {
  tab.value = 'result'
  // 后端已提供 funnelData；前端也可以从 results 自算
  const { data } = await cohortApi.listResults(cohortId)
  results.value = data || []
  funnelData.value = buildFunnelFromResults(data || [])
  await nextTick()
  renderFunnel()
}

function buildFunnelFromResults(rows: any[]) {
  if (!rows.length) return []
  // 取最大 periodIndex 的 cohortPeriod 作为留存期数序列（按 periodIndex 排序）
  const sorted = [...rows].sort((a, b) => (a.periodIndex || 0) - (b.periodIndex || 0))
  return sorted.map((r, idx) => ({
    name: r.cohortPeriod || `P${idx + 1}`,
    value: Number(r.size || r.value || 0),
    rate: r.retentionRate ? (Number(r.retentionRate).toFixed(2) + '%') : '-'
  }))
}

function renderFunnel() {
  if (!funnelChartRef.value || !funnelData.value.length) return
  if (funnelChart) funnelChart.dispose()
  funnelChart = echarts.init(funnelChartRef.value)
  funnelChart.setOption({
    title: { text: '留存漏斗（按期）', left: 'center' },
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>人数: ${p.value}<br/>留存率: ${funnelData.value[p.dataIndex]?.rate || '-'}`
    },
    series: [{
      name: '留存漏斗',
      type: 'funnel',
      left: '10%',
      width: '80%',
      label: {
        formatter: (p: any) => `${p.name} (${funnelData.value[p.dataIndex]?.rate || '-'})`,
        fontSize: 14
      },
      itemStyle: { borderColor: '#fff', borderWidth: 1 },
      data: funnelData.value
    }]
  })
}

async function saveDef() {
  await cohortApi.createDefinition(defForm.value)
  ElMessage.success('已保存')
  defDialog.value = false
  loadDefs()
}

const onResize = () => funnelChart?.resize()
window.addEventListener('resize', onResize)
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  funnelChart?.dispose()
  funnelChart = null
})

watch(() => tab.value, async (v) => {
  if (v === 'result' && funnelData.value.length) {
    await nextTick()
    renderFunnel()
  }
})

onMounted(loadDefs)
</script>

<style scoped>
.cohort-page {
  padding: 16px;
}
</style>
