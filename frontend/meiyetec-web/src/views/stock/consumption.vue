<template>
  <div class="consumption-page">
    <!-- 概览统计卡 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6"><el-card><div class="stat"><div class="label">今日消耗次数</div><div class="value">{{ overview.todayCount || 0 }} 次</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><div class="label">今日消耗成本</div><div class="value danger">¥{{ Number(overview.todayAmount || 0).toFixed(2) }}</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><div class="label">近 30 天订单</div><div class="value">{{ overview.orderCount || 0 }}</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><div class="label">近 30 天耗材种类</div><div class="value">{{ overview.productCount || 0 }}</div></div></el-card></el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card class="search-bar">
      <el-select v-model="filter.productId" placeholder="商品" clearable filterable style="width: 200px" @change="loadData">
        <el-option v-for="p in products" :key="p.id" :value="p.id" :label="`${p.productCode} - ${p.productName}`" />
      </el-select>
      <el-select v-model="filter.orderId" placeholder="订单" clearable filterable style="width: 180px" @change="loadData">
        <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="`${o.orderNo}`" />
      </el-select>
      <el-select v-model="filter.technicianId" placeholder="技师" clearable style="width: 140px" @change="loadData">
        <el-option v-for="t in technicians" :key="t.id" :value="t.id" :label="t.name" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="margin-left: 8px" @change="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="success" @click="exportExcel">导出 CSV</el-button>
    </el-card>

    <!-- 消耗明细 -->
    <el-card>
      <el-table :data="list" border stripe v-loading="loading" show-summary :summary-method="summaries">
        <el-table-column label="消耗时间" width="160">
          <template #default="{ row }">
            <span v-if="row.recordTime">{{ row.recordTime }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="服务订单" width="180">
          <template #default="{ row }">
            <el-link v-if="row.orderId" type="primary" @click="viewOrder(row.orderId)">{{ row.orderNo }}</el-link>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="服务项目" width="160">
          <template #default="{ row }">
            <span v-if="row.serviceName">{{ row.serviceName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="技师" width="100">
          <template #default="{ row }">
            <span v-if="row.technicianName">{{ row.technicianName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="客户" width="100">
          <template #default="{ row }">
            <span v-if="row.customerName">{{ row.customerName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品编码" width="120">
          <template #default="{ row }">
            <span v-if="row.productCode">{{ row.productCode }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品名称" width="160">
          <template #default="{ row }">
            <span v-if="row.productName">{{ row.productName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="160">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="消耗数量" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.quantity != null" class="neg">-{{ row.quantity }} {{ row.unit || '' }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="成本价" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.costPrice != null">¥{{ Number(row.costPrice).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="消耗成本" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.quantity != null" class="neg">¥{{ ((row.quantity || 0) * (row.costPrice || 0)).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[20, 50, 100]"
        @current-change="loadData"
        @size-change="loadData"
        style="margin-top: 12px"
      />
    </el-card>

    <!-- 关联订单详情弹窗 -->
    <el-dialog v-model="orderDialog" title="订单详情" width="700px">
      <el-descriptions :column="2" border v-if="orderDetail">
        <el-descriptions-item label="订单号">{{ orderDetail.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ orderDetail.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ orderDetail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ Number(orderDetail.amount || 0).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">消耗明细</el-divider>
      <el-table :data="orderConsumptions" border size="small">
        <el-table-column label="商品">
          <template #default="{ row }">
            <span v-if="row.productName">{{ row.productName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="批次">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" align="right">
          <template #default="{ row }">
            <span v-if="row.quantity != null">-{{ row.quantity }} {{ row.unit || '' }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="成本" align="right">
          <template #default="{ row }">
            <span v-if="row.quantity != null">¥{{ ((row.quantity || 0) * (row.costPrice || 0)).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="orderConsumptions.length === 0" style="text-align: center; color: #c0c4cc; padding: 16px">该订单暂无消耗记录</div>
      <div style="margin-top: 12px; text-align: right;">
        <b>消耗成本合计：</b>
        <span class="neg" style="font-size: 16px;">¥{{ orderTotalCost.toFixed(2) }}</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import stockApi from '@/api/stock'
import { orderApi } from '@/api/pos'
import { employeeApi } from '@/api/basic'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const overview = ref<any>({})
const products = ref<any[]>([])
const orders = ref<any[]>([])
const technicians = ref<any[]>([])

const filter = ref<any>({ productId: null, orderId: null, technicianId: null })
const dateRange = ref<any[]>([])

const orderDialog = ref(false)
const orderDetail = ref<any>(null)
const orderConsumptions = ref<any[]>([])

const orderTotalCost = computed(() =>
  orderConsumptions.value.reduce((sum, r) => sum + (r.quantity || 0) * (r.costPrice || 0), 0)
)

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value }
    if (dateRange.value?.length === 2) {
      params.dateFrom = dateRange.value[0]
      params.dateTo = dateRange.value[1]
    }
    const res: any = await stockApi.consumptionPage(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const loadOverview = async () => {
  try {
    const res: any = await stockApi.consumptionOverview({ days: 30 })
    overview.value = res.data || {}
  } catch {
    overview.value = {}
  }
}

const loadProducts = async () => {
  try {
    const res: any = await stockApi.productPage({ pageNum: 1, pageSize: 200 })
    products.value = res.data?.records || []
  } catch {}
}

const loadOrders = async () => {
  try {
    const res: any = await orderApi.page({ pageNum: 1, pageSize: 100 })
    orders.value = res.data?.records || []
  } catch {}
}

const loadTechnicians = async () => {
  try {
    const res: any = await employeeApi.list()
    technicians.value = res.data || []
  } catch {}
}

const resetFilter = () => {
  filter.value = { productId: null, orderId: null, technicianId: null }
  dateRange.value = []
  loadData()
}

const viewOrder = async (orderId: number) => {
  try {
    const res: any = await orderApi.detail(orderId)
    orderDetail.value = res.data
    const cres: any = await stockApi.consumptionByOrder(orderId)
    orderConsumptions.value = cres.data || []
    orderDialog.value = true
  } catch { ElMessage.error('加载订单失败') }
}

const exportExcel = () => {
  ElMessage.success('正在生成导出文件，请稍候…')
  // 直接走后端 CSV 接口，保持与入库/盘点导出口径一致
  const params = new URLSearchParams()
  Object.entries(filter.value).forEach(([k, v]) => { if (v != null) params.append(k, String(v)) })
  if (dateRange.value?.length === 2) {
    params.append('dateFrom', dateRange.value[0])
    params.append('dateTo', dateRange.value[1])
  }
  const url = `/api/stock/consumption/export?${params.toString()}`
  window.open(url, '_blank')
}

const summaries = ({ columns, data }: any) => {
  const sums: any[] = []
  columns.forEach((col: any, idx: number) => {
    if (idx === 0) {
      sums[idx] = '合计'
    } else if (col.label === '消耗数量') {
      sums[idx] = data.reduce((acc: number, r: any) => acc + (r.quantity || 0), 0)
    } else if (col.label === '消耗成本') {
      sums[idx] = '¥' + data.reduce((acc: number, r: any) => acc + (r.quantity || 0) * (r.costPrice || 0), 0).toFixed(2)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

onMounted(() => {
  loadOverview()
  loadData()
  Promise.allSettled([loadProducts(), loadOrders(), loadTechnicians()])
})
</script>

<style scoped>
.consumption-page { padding: 12px; }
.stats-row { margin-bottom: 12px; }
.stats-row .stat { text-align: center; }
.stats-row .label { color: #909399; font-size: 12px; margin-bottom: 6px; }
.stats-row .value { font-size: 22px; font-weight: bold; color: #303133; }
.stats-row .value.danger { color: #f56c6c; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-select, .search-bar .el-date-editor, .search-bar .el-button { margin-right: 8px; margin-bottom: 4px; }
.text-muted { color: #c0c4cc; }
.neg { color: #f56c6c; font-weight: 600; }
</style>
