<template>
  <div class="record-page">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="5"><el-card><div class="stat"><div class="label">今日入库</div><div class="value success">+{{ stats.todayIn || 0 }}</div></div></el-card></el-col>
      <el-col :span="5"><el-card><div class="stat"><div class="label">今日出库</div><div class="value danger">-{{ stats.todayOut || 0 }}</div></div></el-card></el-col>
      <el-col :span="5"><el-card><div class="stat"><div class="label">本月入库</div><div class="value success">+{{ stats.monthIn || 0 }}</div></div></el-card></el-col>
      <el-col :span="5"><el-card><div class="stat"><div class="label">本月出库</div><div class="value danger">-{{ stats.monthOut || 0 }}</div></div></el-card></el-col>
      <el-col :span="4"><el-card><div class="stat"><div class="label">本月金额</div><div class="value">¥{{ Number(stats.monthAmount || 0).toFixed(2) }}</div></div></el-card></el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card class="search-bar">
      <el-select v-model="filter.type" placeholder="类型" clearable style="width: 140px" @change="loadData">
        <el-option label="入库" :value="1" />
        <el-option label="出库" :value="2" />
        <el-option label="盘点" :value="3" />
        <el-option label="领用" :value="4" />
        <el-option label="报损" :value="5" />
        <el-option label="采购入库" :value="6" />
        <el-option label="服务消耗" :value="7" />
      </el-select>
      <el-select v-model="filter.productId" placeholder="商品" clearable filterable style="width: 200px" @change="loadData">
        <el-option v-for="p in products" :key="p.id" :value="p.id" :label="`${p.productCode} - ${p.productName}`" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="margin-left: 8px" @change="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="success" @click="exportExcel">导出</el-button>
    </el-card>

    <!-- 流水列表 -->
    <el-card>
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <span v-if="row.recordTime">{{ row.recordTime }}</span>
            <span v-else-if="row.createTime">{{ row.createTime }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="RECORD_TAG[row.type] || 'primary'">{{ RECORD_LABEL[row.type] || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务订单" width="180">
          <template #default="{ row }">
            <el-link v-if="row.orderId" type="primary" @click="viewOrder(row.orderId)">{{ row.orderNo }}</el-link>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="编码" width="140">
          <template #default="{ row }">
            <span v-if="row.productCode">{{ row.productCode }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="160">
          <template #default="{ row }">
            <span v-if="row.productName">{{ row.productName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="180">
          <template #default="{ row }">
            <el-link v-if="row.batchNo" type="info" @click="goTrace(row.productId)">{{ row.batchNo }}</el-link>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.quantity != null" :class="row.quantity > 0 ? 'pos' : 'neg'">{{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }} {{ row.unit || '' }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作前" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.beforeQty != null">{{ row.beforeQty }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作后" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.afterQty != null">{{ row.afterQty }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.totalAmount">¥{{ Number(row.totalAmount).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="100">
          <template #default="{ row }">
            <span v-if="row.operatorName">{{ row.operatorName }}</span>
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

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="orderDialog" title="关联订单详情" width="640px">
      <el-descriptions :column="2" border v-if="orderDetail">
        <el-descriptions-item label="订单号">{{ orderDetail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ orderDetail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">{{ orderDetail.serviceName }}</el-descriptions-item>
        <el-descriptions-item label="技师">{{ orderDetail.technicianName }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ Number(orderDetail.amount || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ orderDetail.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import stockApi from '@/api/stock'
import { orderApi } from '@/api/pos'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const records = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const stats = ref<any>({})
const products = ref<any[]>([])
const filter = ref<any>({ type: null, productId: null })
const dateRange = ref<any[]>([dayjs().subtract(30, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')])

const orderDialog = ref(false)
const orderDetail = ref<any>(null)

const RECORD_LABEL: Record<number, string> = {
  1: '入库', 2: '出库', 3: '盘点', 4: '领用', 5: '报损', 6: '采购入库', 7: '服务消耗'
}
const RECORD_TAG: Record<number, any> = {
  1: 'success', 2: 'warning', 3: 'info', 4: 'primary', 5: 'danger', 6: 'success', 7: 'primary'
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value }
    if (dateRange.value?.length === 2) {
      params.dateFrom = dateRange.value[0]
      params.dateTo = dateRange.value[1]
    }
    const res: any = await stockApi.recordPage(params)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

const loadStats = async () => {
  try {
    const res: any = await stockApi.stats({})
    stats.value = res.data || {}
  } catch {}
}

const loadProducts = async () => {
  try {
    const res: any = await stockApi.productPage({ pageNum: 1, pageSize: 100 })
    products.value = res.data?.records || []
  } catch {}
}

const resetFilter = () => {
  filter.value = { type: null, productId: null }
  dateRange.value = []
  pageNum.value = 1
  pageSize.value = 20
  loadData()
  loadStats()
}

const viewOrder = async (orderId: number) => {
  try {
    const res: any = await orderApi.detail(orderId)
    orderDetail.value = res.data
    orderDialog.value = true
  } catch { ElMessage.error('加载订单失败') }
}

const goTrace = (productId: number) => {
  router.push(`/stock/trace/${productId}`)
}

const exportExcel = () => {
  ElMessage.info('导出功能开发中')
}

onMounted(() => {
  loadData()
  loadStats()
  loadProducts()
})
</script>

<style scoped>
.record-page { padding: 12px; }
.stats-row { margin-bottom: 12px; }
.stat { text-align: center; }
.stat .label { color: #999; font-size: 12px; }
.stat .value { font-size: 22px; font-weight: bold; margin-top: 4px; }
.stat .value.success { color: #67c23a; }
.stat .value.danger { color: #f56c6c; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-select { margin-right: 8px; }
.pos { color: #67c23a; font-weight: bold; }
.neg { color: #f56c6c; font-weight: bold; }
.text-muted { color: #c0c4cc; font-style: italic; font-size: 12px; }
</style>
