<template>
  <div class="effect-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">活动订单</div>
        <div class="kpi-value">{{ snapshot.totalOrders ?? '—' }}</div>
      </div>
      <div class="kpi-item green">
        <div class="kpi-label">减免总额</div>
        <div class="kpi-value">¥{{ formatAmount(snapshot.totalDiscount) }}</div>
      </div>
      <div class="kpi-item yellow">
        <div class="kpi-label">券核销率</div>
        <div class="kpi-value">{{ formatPercent(snapshot.couponUsageRate) }}</div>
      </div>
      <div class="kpi-item red">
        <div class="kpi-label">券已用</div>
        <div class="kpi-value">{{ snapshot.usedCoupon ?? '—' }}</div>
      </div>
      <div class="kpi-item blue">
        <div class="kpi-label">积分发放</div>
        <div class="kpi-value">{{ snapshot.pointsGranted ?? '—' }}</div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 280px" />
      <el-button type="primary" @click="loadSnap">查询</el-button>
      <el-button type="warning" @click="saveSnapshot">生成快照</el-button>
    </el-card>

    <el-card class="chart-card">
      <h4 style="margin: 0 0 12px">活动效果明细</h4>
      <el-table :data="snapshot.promotions || []" v-loading="loading" border>
        <el-table-column prop="promotionName" label="活动名称" min-width="200" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="typeColor(row.promotionType)">{{ typeLabel(row.promotionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单" width="100" align="center">
          <template #default="{ row }">{{ row.orderCount }}</template>
        </el-table-column>
        <el-table-column label="减免总额" width="160" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.discountTotal) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const snapshot = reactive<any>({})
const period = ref<any>([])
const loading = ref(false)

async function loadSnap() {
  loading.value = true
  try {
    const params: any = {}
    if (period.value && period.value.length === 2) {
      params.startDate = period.value[0]
      params.endDate = period.value[1]
    }
    const r = await http.get('/mkt/effect/snapshot', { params })
    Object.assign(snapshot, r.data || {})
  } finally {
    loading.value = false
  }
}

async function saveSnapshot() {
  await http.post('/mkt/effect/snapshot/save')
  ElMessage.success('已生成当日快照')
  loadSnap()
}

function formatAmount(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

function formatPercent(v: any) {
  if (v == null) return '0%'
  const n = Number(v)
  return isNaN(n) ? '0%' : n.toFixed(1) + '%'
}

function typeLabel(t: string) {
  return ({ FULL_REDUCE: '满减', DISCOUNT: '折扣', GIFT: '买赠', CASH_COUPON: '付费券', POINTS_EXCHANGE: '积分兑换' } as any)[t] || t
}
function typeColor(t: string) {
  return ({ FULL_REDUCE: 'danger', DISCOUNT: 'warning', GIFT: 'success', CASH_COUPON: 'primary', POINTS_EXCHANGE: 'info' } as any)[t] || ''
}

onMounted(() => loadSnap())
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-item.red { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.kpi-item.blue { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.search-bar { margin-bottom: 12px; }
</style>
