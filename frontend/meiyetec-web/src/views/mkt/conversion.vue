<template>
  <div class="conversion-page">
    <el-card class="search-bar">
      <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 280px" />
      <el-button type="primary" @click="loadData">查询</el-button>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="6">
        <el-card class="kpi">
          <div class="kpi-label">触达总数</div>
          <div class="kpi-value">{{ funnel.reach ?? '—' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="kpi green">
          <div class="kpi-label">已发送 ({{ funnel.sentRate ?? '0' }}%)</div>
          <div class="kpi-value">{{ funnel.sent ?? '—' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="kpi blue">
          <div class="kpi-label">已读 ({{ funnel.readRate ?? '0' }}%)</div>
          <div class="kpi-value">{{ funnel.read ?? '—' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="kpi yellow">
          <div class="kpi-label">已转化 ({{ funnel.convertRate ?? '0' }}%)</div>
          <div class="kpi-value">{{ funnel.converted ?? '—' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="12">
        <el-card>
          <h4 style="margin: 0 0 12px">ROI</h4>
          <el-table :data="roiRows" border size="small">
            <el-table-column prop="totalRevenue" label="总营收">
              <template #default="{ row }">¥{{ formatAmount(row.totalRevenue) }}</template>
            </el-table-column>
            <el-table-column prop="totalCost" label="估算成本">
              <template #default="{ row }">¥{{ formatAmount(row.totalCost) }}</template>
            </el-table-column>
            <el-table-column prop="roi" label="ROI">
              <template #default="{ row }">
                <el-tag :type="Number(row.roi) > 0 ? 'success' : 'danger'">
                  {{ row.roi }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="convertCount" label="转化数" align="center" />
            <el-table-column prop="reachCount" label="触达数" align="center" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <h4 style="margin: 0 0 12px">按渠道统计</h4>
          <el-table :data="channelRows" border size="small">
            <el-table-column label="渠道">
              <template #default="{ row }">
                <el-tag size="small">{{ row.channelType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reach" label="触达" align="center" />
            <el-table-column prop="sent" label="已发送" align="center" />
            <el-table-column prop="converted" label="转化" align="center" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'

const period = ref<any>([])
const funnel = reactive<any>({})
const roiRows = ref<any[]>([])
const channelRows = ref<any[]>([])

async function loadData() {
  const params: any = {}
  if (period.value && period.value.length === 2) {
    params.startDate = period.value[0]
    params.endDate = period.value[1]
  }
  const r1 = await http.get('/mkt/conversion/funnel', { params })
  Object.assign(funnel, r1.data || {})
  const r2 = await http.get('/mkt/conversion/roi')
  roiRows.value = [r2.data]
  const r3 = await http.get('/mkt/conversion/by-channel')
  channelRows.value = r3.data || []
}

function formatAmount(v: any) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.kpi { text-align: center; padding: 16px; }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 28px; font-weight: 700; color: #1e293b; margin-top: 6px; }
.kpi.green .kpi-value { color: #67c23a; }
.kpi.blue .kpi-value { color: #409eff; }
.kpi.yellow .kpi-value { color: #e6a23c; }
</style>
