<template>
  <div class="stock-list">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6"><el-card><div class="stat"><div class="label">商品种类</div><div class="value">{{ stats.totalProducts || 0 }}</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><div class="label">在库种类</div><div class="value">{{ stats.coveredProducts || 0 }}</div></div></el-card></el-col>
      <el-col :span="4"><el-card><div class="stat"><div class="label danger">缺货</div><div class="value danger">{{ stats.outOfStock || 0 }}</div></div></el-card></el-col>
      <el-col :span="4"><el-card><div class="stat"><div class="label warn">低于最低</div><div class="value warn">{{ stats.lowStock || 0 }}</div></div></el-card></el-col>
      <el-col :span="4"><el-card><div class="stat"><div class="label info">超储</div><div class="value info">{{ stats.overStock || 0 }}</div></div></el-card></el-col>
    </el-row>

    <!-- 搜索筛选 -->
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="商品编码/名称/批次" style="width: 200px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.category" placeholder="分类" clearable style="width: 120px" @change="loadData">
        <el-option label="耗材" value="耗材" />
        <el-option label="产品" value="产品" />
        <el-option label="工具" value="工具" />
      </el-select>
      <el-select v-model="filter.alertType" placeholder="库存状态" clearable style="width: 130px" @change="loadData">
        <el-option label="正常" value="OK" />
        <el-option label="缺货" value="OUT" />
        <el-option label="低于最低" value="LOW" />
        <el-option label="超储" value="OVER" />
      </el-select>
      <el-select v-model="filter.expiryStatus" placeholder="有效期" clearable style="width: 130px" @change="loadData">
        <el-option label="正常" value="OK" />
        <el-option label="临期(30天内)" value="EXPIRING" />
        <el-option label="已过期" value="EXPIRED" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="success" @click="batchPrint">批量打印标签</el-button>
      <el-button type="warning" @click="batchAdjust">批量调整</el-button>
      <el-button @click="exportExcel">导出</el-button>
    </el-card>

    <!-- 库存表 -->
    <el-card>
      <el-table :data="inventory" border stripe v-loading="loading" :row-class-name="rowClass">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="productCode" label="编码" width="140" />
        <el-table-column prop="productName" label="商品名称" min-width="160">
          <template #default="{ row }">
            <span v-if="row.productName">{{ row.productName }}</span>
            <span v-else class="text-muted">{{ row.productCode || '（未命名）' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small">{{ row.category }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" width="120" />
        <el-table-column prop="batchNo" label="批次号" width="180">
          <template #default="{ row }">
            <el-link type="info" @click="goTrace(row.productId)">{{ row.batchNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="80" align="right">
          <template #default="{ row }">
            <span :class="row.alert === 'OUT' ? 'danger' : row.alert === 'LOW' ? 'warn' : ''">{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="costPrice" label="成本(元)" width="80" align="right">
          <template #default="{ row }">{{ Number(row.costPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="安全库存" width="100" align="right">
          <template #default="{ row }">{{ row.minStock }} ~ {{ row.maxStock }}</template>
        </el-table-column>
        <el-table-column label="预警" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.alert === 'OUT'" type="danger" size="small">缺货</el-tag>
            <el-tag v-else-if="row.alert === 'LOW'" type="warning" size="small">低于最低</el-tag>
            <el-tag v-else-if="row.alert === 'OVER'" type="info" size="small">超储</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expiryDate" label="过期日期" width="120">
          <template #default="{ row }">
            <span v-if="row.expiryDate" :class="isExpired(row.expiryDate) ? 'expired' : isExpiring(row.expiryDate) ? 'expiring' : ''">
              {{ row.expiryDate }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" width="120" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goTrace(row.productId)">追溯</el-button>
            <el-button link type="success" @click="openInbound(row)">入库</el-button>
            <el-button link type="warning" @click="openOutbound(row)">出库</el-button>
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

    <!-- 入库弹窗 -->
    <el-dialog v-model="inboundDialog" title="入库" width="540px">
      <el-form :model="inboundForm" label-width="100px">
        <el-form-item label="商品"><b>{{ inboundForm.productName }}</b></el-form-item>
        <el-form-item label="批次号"><el-input v-model="inboundForm.batchNo" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="数量" required><el-input-number v-model="inboundForm.quantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="inboundForm.unitPrice" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="inboundForm.supplier" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="生产日期"><el-date-picker v-model="inboundForm.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="过期日期"><el-date-picker v-model="inboundForm.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="inboundForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inboundDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmInbound" :loading="saving">入库</el-button>
      </template>
    </el-dialog>

    <!-- 出库弹窗 -->
    <el-dialog v-model="outboundDialog" title="出库/领用" width="540px">
      <el-form :model="outboundForm" label-width="100px">
        <el-form-item label="商品"><b>{{ outboundForm.productName }}</b></el-form-item>
        <el-form-item label="批次号"><el-input v-model="outboundForm.batchNo" placeholder="留空扣最早批次" /></el-form-item>
        <el-form-item label="数量" required><el-input-number v-model="outboundForm.quantity" :min="1" :max="outboundForm.maxQty" style="width: 100%" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="outboundForm.type">
            <el-radio :value="4">领用</el-radio>
            <el-radio :value="5">报损</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="outboundForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outboundDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmOutbound" :loading="saving">出库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import stockApi from '@/api/stock'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const inventory = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const stats = ref<any>({})
const featureEnabled = ref(true)
const filter = ref<any>({ keyword: '', category: '', alertType: '', expiryStatus: '' })

const inboundDialog = ref(false)
const inboundForm = ref<any>({ productId: 0, productName: '', batchNo: '', quantity: 1, unitPrice: 0, supplier: '', productionDate: '', expiryDate: '', remark: '' })

const outboundDialog = ref(false)
const outboundForm = ref<any>({ productId: 0, productName: '', batchNo: '', quantity: 1, maxQty: 9999, type: 4, remark: '' })

const isExpired = (date: string) => date && new Date(date) < new Date()
const isExpiring = (date: string) => {
  if (!date) return false
  const d = new Date(date)
  const diff = (d.getTime() - Date.now()) / (1000 * 60 * 60 * 24)
  return diff >= 0 && diff <= 30
}

const rowClass = ({ row }: any) => {
  if (row.alert === 'OUT' || isExpired(row.expiryDate)) return 'danger-row'
  if (row.alert === 'LOW' || isExpiring(row.expiryDate)) return 'warn-row'
  return ''
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await stockApi.inventoryPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: filter.value.keyword,
      category: filter.value.category,
      alertType: filter.value.alertType,
      expiryStatus: filter.value.expiryStatus
    })
    inventory.value = res.data?.records || []
    total.value = res.data?.total || 0
    featureEnabled.value = true
    await loadStats()
  } catch (e: any) {
    if (e?.response?.status === 404) {
      featureEnabled.value = false
    }
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  if (!featureEnabled.value) return
  try { stats.value = (await stockApi.stats({})).data || {} } catch {}
}

const resetFilter = () => {
  filter.value = { keyword: '', category: '', alertType: '', expiryStatus: '' }
  pageNum.value = 1
  pageSize.value = 20
  loadData()
}

const openInbound = (row: any) => {
  inboundForm.value = { productId: row.productId, productName: row.productName, batchNo: '', quantity: 1, unitPrice: row.costPrice || 0, supplier: row.supplier || '', productionDate: '', expiryDate: '', remark: '' }
  inboundDialog.value = true
}

const confirmInbound = async () => {
  saving.value = true
  try {
    const res: any = await stockApi.inbound(inboundForm.value)
    ElMessage.success(`入库成功，库存=${res.data?.inventory?.quantity}`)
    inboundDialog.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '入库失败') }
  finally { saving.value = false }
}

const openOutbound = (row: any) => {
  outboundForm.value = { productId: row.productId, productName: row.productName, batchNo: row.batchNo, quantity: 1, maxQty: row.quantity, type: 4, remark: '' }
  outboundDialog.value = true
}

const confirmOutbound = async () => {
  if (outboundForm.value.quantity > outboundForm.value.maxQty) {
    ElMessage.warning(`出库数量(${outboundForm.value.quantity})超过最大库存(${outboundForm.value.maxQty})`)
    return
  }
  saving.value = true
  try {
    const res: any = await stockApi.outbound(outboundForm.value)
    ElMessage.success(`出库成功，剩余库存=${res.data?.inventory?.quantity}`)
    outboundDialog.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '出库失败') }
  finally { saving.value = false }
}

const goTrace = (productId: number) => { router.push(`/stock/trace/${productId}`) }
const batchPrint = () => { ElMessage.info('批量打印功能开发中') }
const exportExcel = async () => {
  try {
    const res: any = await stockApi.inventoryExport({
      keyword: filter.value.keyword,
      category: filter.value.category,
      alertType: filter.value.alertType,
      expiryStatus: filter.value.expiryStatus
    })
    const blob = new Blob([res.data || res], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `库存清单_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出完成')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '导出失败')
  }
}
const batchAdjust = () => {
  ElMessageBox.confirm(
    '批量调整会修改库存安全上下限，仅店长及以上可操作。是否继续？',
    '安全确认',
    { type: 'warning', confirmButtonText: '我已确认，继续', cancelButtonText: '取消' }
  ).catch(() => ElMessage.info('已取消'))
}

onMounted(() => loadData())
</script>

<style scoped>
.stock-list { padding: 12px; }
.stats-row { margin-bottom: 12px; }
.stat { text-align: center; }
.stat .label { color: #999; font-size: 12px; }
.stat .value { font-size: 24px; font-weight: bold; margin-top: 4px; }
.stat .value.danger { color: #f56c6c; }
.stat .value.warn { color: #e6a23c; }
.stat .value.info { color: #909399; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input, .search-bar .el-select { margin-right: 8px; }
.danger { color: #f56c6c; font-weight: bold; }
.warn { color: #e6a23c; font-weight: bold; }
.text-muted { color: #c0c4cc; font-style: italic; font-size: 12px; }
.expired { color: #f56c6c; }
.expiring { color: #e6a23c; }
:deep(.danger-row) { background-color: #fef0f0; }
:deep(.warn-row) { background-color: #fdf6ec; }
</style>
