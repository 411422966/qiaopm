<template>
  <div class="alert-page">

    <!-- 功能未启用 -->
    <template v-if="!featureEnabled">
      <el-row justify="center" style="margin-top: 80px">
        <el-col :span="12">
          <el-card shadow="never" class="empty-card">
            <div style="text-align: center; padding: 32px 0">
              <svg width="64" height="64" viewBox="0 0 64 64" fill="none" style="margin-bottom: 16px">
                <circle cx="32" cy="32" r="30" stroke="#dcdfe6" stroke-width="2" stroke-dasharray="6 4"/>
                <path d="M32 18L36 26H28L32 18Z" fill="#909399"/>
                <rect x="29" y="28" width="6" height="8" rx="1" fill="#909399"/>
                <rect x="30" y="38" width="4" height="6" rx="1" fill="#909399"/>
              </svg>
              <h3 style="color: #303133; margin-bottom: 8px">库存预警功能</h3>
              <p style="color: #909399; margin-bottom: 16px">
                低于安全库存时自动提醒，确保耗材及时补货
              </p>
              <el-divider>
                <span style="color: #c0c4cc; font-size: 12px">依赖数据</span>
              </el-divider>
              <div class="flow-steps">
                <el-tag type="info">商品档案</el-tag>
                <el-icon style="color: #c0c4cc"><ArrowRight /></el-icon>
                <el-tag type="info">安全库存设置</el-tag>
                <el-icon style="color: #c0c4cc"><ArrowRight /></el-icon>
                <el-tag type="info">当前库存</el-tag>
                <el-icon style="color: #c0c4cc"><ArrowRight /></el-icon>
                <el-tag type="success">预警触发</el-tag>
              </div>
              <el-divider />
              <p style="color: #c0c4cc; font-size: 12px">
                当前商品未设置安全库存或接口未就绪
              </p>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 功能已启用 -->
    <template v-else>
      <!-- 预警统计 -->
      <el-row :gutter="12" class="stats-row">
        <el-col :span="4">
          <el-card>
            <div class="stat">
              <div class="label">总商品</div>
              <div class="value">{{ stats.total || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card>
            <div class="stat">
              <div class="label danger">缺货</div>
              <div class="value danger">{{ stats.out_of_stock || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card>
            <div class="stat">
              <div class="label warn">低于最低</div>
              <div class="value warn">{{ stats.low_stock || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card>
            <div class="stat">
              <div class="label info">超储</div>
              <div class="value info">{{ stats.over_stock || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card>
            <div class="stat">
              <div class="label expire">临期/过期</div>
              <div class="value expire">{{ stats.expiring || 0 }}</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card>
            <div class="stat">
              <div class="label">正常</div>
              <div class="value normal">{{ stats.normal || 0 }}</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 筛选 -->
      <el-card class="search-bar">
        <el-radio-group v-model="filter.alertType" @change="loadData">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OUT">缺货</el-radio-button>
          <el-radio-button value="LOW">低于最低</el-radio-button>
          <el-radio-button value="OVER">超储</el-radio-button>
          <el-radio-button value="EXPIRING">临期</el-radio-button>
          <el-radio-button value="EXPIRED">已过期</el-radio-button>
        </el-radio-group>
        <el-select v-model="filter.category" placeholder="分类" clearable style="width: 140px; margin-left: 12px" @change="loadData">
          <el-option label="耗材" value="耗材" />
          <el-option label="产品" value="产品" />
          <el-option label="工具" value="工具" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="autoReorder">一键补货建议</el-button>
      </el-card>

      <!-- 预警列表 -->
      <el-card>
        <el-table :data="list" border stripe v-loading="loading" :row-class-name="rowClass">
          <el-table-column label="预警级别" width="100">
            <template #default="{ row }">
              <el-tag :type="(ALERT_TAG[row.alertType] || 'primary') as any">{{ ALERT_LABEL[row.alertType] || '其他' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="productCode" label="编码" width="140" />
          <el-table-column prop="productName" label="商品名称" width="160" />
          <el-table-column prop="category" label="分类" width="80" />
          <el-table-column prop="batchNo" label="批次号" width="160" />
          <el-table-column label="当前库存" width="100" align="right">
            <template #default="{ row }">
              <span :class="row.alertType === 'OUT' ? 'danger' : row.alertType === 'LOW' ? 'warn' : ''">{{ row.quantity }} {{ row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="安全库存" width="100" align="right">
            <template #default="{ row }">
              {{ row.minStock }} ~ {{ row.maxStock }}
            </template>
          </el-table-column>
          <el-table-column label="建议补货" width="100" align="right">
            <template #default="{ row }">
              <span class="suggest">{{ Math.max(0, (row.maxStock || 0) - (row.quantity || 0)) }} {{ row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="expiryDate" label="有效期" width="120">
            <template #default="{ row }">
              <span v-if="row.expiryDate" :class="isExpired(row.expiryDate) ? 'expired' : isExpiring(row.expiryDate) ? 'expiring' : ''">
                {{ row.expiryDate }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="supplier" label="供应商" width="120" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="quickInbound(row)">入库补货</el-button>
              <el-button link type="primary" @click="viewProduct(row)">查看商品</el-button>
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

      <!-- 补货建议弹窗（R2.0-D） -->
      <el-dialog v-model="replenishDialog" title="补货建议（基于近 30 天消耗）" width="900px">
        <div style="margin-bottom: 12px">
          <el-tag type="info">回溯天数</el-tag>
          <el-input-number v-model="replenishOpts.days" :min="7" :max="90" size="small" style="margin: 0 8px" />
          <el-tag type="info">安全库存天数</el-tag>
          <el-input-number v-model="replenishOpts.safetyDays" :min="3" :max="60" size="small" style="margin: 0 8px" />
          <el-button type="primary" size="small" @click="loadReplenish">重新计算</el-button>
          <el-button type="success" size="small" @click="exportReplenish">导出 CSV</el-button>
        </div>
        <el-table :data="replenishList" border size="small" max-height="500">
          <el-table-column label="紧急度" width="100">
            <template #default="{ row }">
              <el-tag :type="URGENCY_TAG[row.urgency] || 'info' as any">{{ URGENCY_LABEL[row.urgency] || '普通' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="productCode" label="编码" width="120" />
          <el-table-column prop="productName" label="商品" width="160" />
          <el-table-column prop="category" label="分类" width="80" />
          <el-table-column label="当前库存" width="100" align="right">
            <template #default="{ row }">{{ row.stockQty }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="日均消耗" width="100" align="right">
            <template #default="{ row }">{{ row.avgDaily }}</template>
          </el-table-column>
          <el-table-column label="安全阈值" width="100" align="right">
            <template #default="{ row }">{{ row.safetyTarget }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="建议补货" width="100" align="right">
            <template #default="{ row }"><b class="suggest">{{ row.suggestQty }} {{ row.unit }}</b></template>
          </el-table-column>
          <el-table-column label="预计成本" width="100" align="right">
            <template #default="{ row }">¥{{ Number(row.estCost).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="quickInboundFromReplenish(row)">入库</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="replenishList.length === 0" style="text-align: center; color: #c0c4cc; padding: 16px">暂无补货建议，没有库存缺口</div>
      </el-dialog>

      <!-- 快速入库弹窗 -->
      <el-dialog v-model="inboundDialog" title="快速入库" width="500px">
        <el-form :model="inboundForm" label-width="100px">
          <el-form-item label="商品"><b>{{ inboundForm.productName }}</b></el-form-item>
          <el-form-item label="批次号"><el-input v-model="inboundForm.batchNo" placeholder="留空自动生成" /></el-form-item>
          <el-form-item label="入库数量" required><el-input-number v-model="inboundForm.quantity" :min="1" style="width: 100%" /></el-form-item>
          <el-form-item label="单价"><el-input-number v-model="inboundForm.unitPrice" :min="0" :precision="2" style="width: 100%" /></el-form-item>
          <el-form-item label="供应商"><el-input v-model="inboundForm.supplier" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="inboundForm.remark" type="textarea" :rows="2" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="inboundDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmInbound" :loading="saving">确认入库</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import stockApi from '@/api/stock'

const featureEnabled = ref(true)
const loading = ref(false)
const saving = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const stats = ref<any>({})
const filter = ref<any>({ alertType: '', category: '' })

const inboundDialog = ref(false)
const inboundForm = ref<any>({ productId: 0, productName: '', batchNo: '', quantity: 1, unitPrice: 0, supplier: '', remark: '' })

const ALERT_LABEL: Record<string, string> = {
  OUT: '缺货', LOW: '低于最低', OVER: '超储', EXPIRING: '临期', EXPIRED: '已过期'
}
const ALERT_TAG: Record<string, string> = {
  OUT: 'danger', LOW: 'warning', OVER: 'info', EXPIRING: 'primary', EXPIRED: 'danger'
}

const URGENCY_LABEL: Record<string, string> = {
  URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低'
}
const URGENCY_TAG: Record<string, string> = {
  URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info'
}

const replenishDialog = ref(false)
const replenishList = ref<any[]>([])
const replenishOpts = ref<any>({ days: 30, safetyDays: 14 })

const isExpired = (date: string) => date && new Date(date) < new Date()
const isExpiring = (date: string) => {
  if (!date) return false
  const d = new Date(date)
  const now = new Date()
  const diff = (d.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)
  return diff >= 0 && diff <= 30
}

const rowClass = ({ row }: any) => {
  if (row.alertType === 'OUT' || row.alertType === 'EXPIRED') return 'danger-row'
  if (row.alertType === 'LOW' || row.alertType === 'EXPIRING') return 'warn-row'
  return ''
}

const loadData = async () => {
  if (!featureEnabled.value && pageNum.value === 1 && !filter.value.alertType && !filter.value.category) {
    // 首次进入已确认为未启用状态，跳过
  }
  loading.value = true
  try {
    const res: any = await stockApi.alertPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      alertType: filter.value.alertType,
      category: filter.value.category
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
    featureEnabled.value = true
  } catch (e: any) {
    if (e?.response?.status === 404) {
      featureEnabled.value = false
      return
    }
  } finally {
    loading.value = false
  }
  loadStats()
}

const loadStats = async () => {
  if (!featureEnabled.value) return
  try {
    const res: any = await stockApi.alertStats(filter.value)
    stats.value = res.data || {}
    featureEnabled.value = true
  } catch (e: any) {
    if (e?.response?.status === 404) {
      featureEnabled.value = false
    }
  }
}

const quickInbound = (row: any) => {
  inboundForm.value = {
    productId: row.productId,
    productName: row.productName,
    batchNo: '',
    quantity: Math.max(1, (row.maxStock || 50) - (row.quantity || 0)),
    unitPrice: row.costPrice || 0,
    supplier: row.supplier || '',
    remark: `预警补货：${row.alertType}`
  }
  inboundDialog.value = true
}

const confirmInbound = async () => {
  saving.value = true
  try {
    await stockApi.inbound(inboundForm.value)
    ElMessage.success('入库成功')
    inboundDialog.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '入库失败') }
  finally { saving.value = false }
}

const viewProduct = (row: any) => {
  window.open(`/stock/product?id=${row.productId}`, '_blank')
}

const autoReorder = async () => {
  replenishDialog.value = true
  await loadReplenish()
}

const loadReplenish = async () => {
  try {
    const res: any = await stockApi.alertReplenish({
      days: replenishOpts.value.days,
      safetyDays: replenishOpts.value.safetyDays
    })
    replenishList.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载补货建议失败')
    replenishList.value = []
  }
}

const exportReplenish = () => {
  if (!replenishList.value.length) { ElMessage.info('暂无数据可导出'); return }
  const params = new URLSearchParams()
  params.append('days', String(replenishOpts.value.days))
  params.append('safetyDays', String(replenishOpts.value.safetyDays))
  window.open(`/api/stock/alert/replenish/export?${params.toString()}`, '_blank')
}

const quickInboundFromReplenish = (row: any) => {
  inboundForm.value = {
    productId: row.productId,
    productName: row.productName,
    batchNo: '',
    quantity: Math.max(1, Number(row.suggestQty) || 1),
    unitPrice: row.costPrice || 0,
    supplier: '',
    recordType: 6, // 6=采购入库（区别于手动补货的普通入库 type=1）
    remark: `补货建议自动入库：${row.urgency}`
  }
  inboundDialog.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.alert-page { padding: 12px; }
.stats-row { margin-bottom: 12px; }
.stat { text-align: center; }
.stat .label { color: #999; font-size: 12px; }
.stat .value { font-size: 22px; font-weight: bold; margin-top: 4px; }
.stat .value.danger { color: #f56c6c; }
.stat .value.warn { color: #e6a23c; }
.stat .value.info { color: #909399; }
.stat .value.expire { color: #e6a23c; }
.stat .value.normal { color: #67c23a; }
.search-bar { margin-bottom: 12px; }
.danger { color: #f56c6c; font-weight: bold; }
.warn { color: #e6a23c; font-weight: bold; }
.expired { color: #f56c6c; }
.expiring { color: #e6a23c; }
.suggest { color: #409eff; font-weight: bold; }
:deep(.danger-row) { background-color: #fef0f0; }
:deep(.warn-row) { background-color: #fdf6ec; }
.empty-card { border: 1px dashed #e4e7ed; }
.flow-steps { display: flex; align-items: center; justify-content: center; gap: 8px; flex-wrap: wrap; }
</style>
