<template>
  <div class="check-page">

    <!-- 统计卡 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card>
          <div class="stat">
            <div class="label">今日盘点数</div>
            <div class="value">{{ stats.todayCount || 0 }} 单</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat">
            <div class="label">已完成 / 总数</div>
            <div class="value">{{ stats.confirmedChecks || 0 }} / {{ stats.totalChecks || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat">
            <div class="label">异常单数（有差异）</div>
            <div class="value warn">{{ stats.abnormal || 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat">
            <div class="label">差异项汇总</div>
            <div class="value">
              <span class="pos">+{{ stats.profitQty || 0 }}</span>
              <span style="margin: 0 6px; color: #c0c4cc">/</span>
              <span class="neg">-{{ stats.lossQty || 0 }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <el-card class="toolbar">
      <el-button type="success" @click="openDialog">+ 创建盘点单</el-button>
      <el-button @click="loadData">刷新</el-button>
      <el-button type="info" @click="exportExcel">导出</el-button>

      <div class="filter-bar">
        <el-input v-model="filter.keyword" placeholder="单号 / 操作人 / 备注" clearable style="width: 200px" @keyup.enter="loadData" @clear="loadData" />
        <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
          <el-option :value="0" label="草稿" />
          <el-option :value="1" label="进行中" />
          <el-option :value="2" label="已完成" />
          <el-option :value="3" label="已作废" />
        </el-select>
        <el-select v-model="filter.storeId" placeholder="门店" clearable filterable style="width: 160px" @change="loadData">
          <el-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.storeName || s.name" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="margin-left: 8px" @change="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>
    </el-card>

    <!-- 盘点单列表 -->
    <el-card style="margin-top: 12px">
      <el-table :data="checks" border stripe v-loading="loading">
        <el-table-column label="盘点单号" width="200">
          <template #default="{ row }">
            <span v-if="row.checkNo">{{ row.checkNo }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="盘点日期" width="110">
          <template #default="{ row }">
            <span v-if="row.checkDate">{{ row.checkDate }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="门店" width="120">
          <template #default="{ row }">
            <span v-if="row.storeName">{{ row.storeName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="商品数" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.totalItems != null">{{ row.totalItems }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="差异项" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.diffItems > 0" class="warn">{{ row.diffItems }}</span>
            <span v-else-if="row.diffItems != null">0</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="盘盈 / 盘亏" width="120" align="right">
          <template #default="{ row }">
            <span class="pos">+{{ row.profitQty || 0 }}</span>
            <span style="margin: 0 4px; color: #c0c4cc">/</span>
            <span class="neg">-{{ row.lossQty || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异金额" width="110" align="right">
          <template #default="{ row }">
            <span :class="(row.diffAmount || 0) >= 0 ? 'pos' : 'neg'">
              {{ (row.diffAmount || 0) >= 0 ? '+' : '' }}{{ Number(row.diffAmount || 0).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异率" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.diffRate > 0" class="warn">{{ ((row.diffRate || 0) * 100).toFixed(1) }}%</span>
            <span v-else>0%</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="100">
          <template #default="{ row }">
            <span v-if="row.operatorName">{{ row.operatorName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="确认人" width="100">
          <template #default="{ row }">
            <span v-if="row.confirmedByName">{{ row.confirmedByName }}</span>
            <span v-else-if="row.confirmedTime">{{ formatTime(row.confirmedTime) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80" align="right">
          <template #default="{ row }">
            <span v-if="row.durationMin != null && row.durationMin !== ''">{{ row.durationMin }}分</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="CHECK_STATUS_TAG[row.status] || 'info'">{{ CHECK_STATUS_LABEL[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核销" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.dualVerifyStatus === 'PENDING'" type="warning" size="small">待复核</el-tag>
            <el-tag v-else-if="row.dualVerifyStatus === 'VERIFIED'" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.dualVerifyStatus === 'REJECTED'" type="danger" size="small">已驳回</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">明细</el-button>
            <el-button link type="success" v-if="row.status === 1" @click="confirmCheck(row)">确认调整</el-button>
            <el-button link type="danger" v-if="row.status === 1" @click="cancelCheck(row)">作废</el-button>
            <el-button link type="warning" v-if="row.status === 2" @click="cancelCheck(row)">反审</el-button>
            <el-button link type="primary" v-if="row.dualVerifyStatus === 'PENDING'" @click="openDualVerify(row)">复核</el-button>
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

    <!-- 创建盘点单向导 -->
    <CheckCreateWizard v-model="dialog" @submitted="loadData" />

    <!-- 原有快速创建弹窗已注释，保留以备回退 -->
    <!--
    <el-dialog v-model="dialog" title="创建盘点单" width="900px" :close-on-click-modal="false">
      ...（详见 git 历史）
    </el-dialog>
    -->

    <!-- 盘点明细弹窗 -->
    <el-dialog v-model="detailDialog" title="盘点明细" width="800px">
      <el-descriptions :column="3" border v-if="detail">
        <el-descriptions-item label="单号">{{ detail.checkNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.checkDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detail.storeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="确认人">{{ detail.confirmedByName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="CHECK_STATUS_TAG[detail.status] || 'info'">{{ CHECK_STATUS_LABEL[detail.status] || '未知' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品数">{{ detail.totalItems ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="差异项">{{ detail.diffItems ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="差异金额">
          <span :class="(detail.diffAmount || 0) >= 0 ? 'pos' : 'neg'">
            {{ (detail.diffAmount || 0) >= 0 ? '+' : '' }}{{ Number(detail.diffAmount || 0).toFixed(2) }}
          </span>
        </el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <div v-if="!detailItems.length" style="text-align: center; color: #c0c4cc; padding: 24px">
        <el-icon style="font-size: 32px; vertical-align: middle"><Box /></el-icon>
        <div style="margin-top: 8px">暂无明细数据</div>
      </div>
      <el-table v-else :data="detailItems" border size="small" show-summary :summary-method="detailSummaries">
        <el-table-column label="商品">
          <template #default="{ row }">
            <div>
              <span v-if="row.productName">{{ row.productName }}</span>
              <span v-else class="text-muted">-</span>
            </div>
            <div v-if="row.productCode" style="font-size: 11px; color: #909399">{{ row.productCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="批次">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else class="text-muted">全部</span>
          </template>
        </el-table-column>
        <el-table-column label="系统库存" align="right">
          <template #default="{ row }">
            <span v-if="row.systemQty != null">{{ row.systemQty }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="实盘" align="right">
          <template #default="{ row }">
            <span v-if="row.actualQty != null">{{ row.actualQty }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="差异" align="right">
          <template #default="{ row }">
            <span v-if="row.diffQty != null" :class="row.diffQty > 0 ? 'pos' : row.diffQty < 0 ? 'neg' : ''">{{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="成本" align="right">
          <template #default="{ row }">
            <span v-if="row.unitPrice != null">¥{{ Number(row.unitPrice).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="差异金额" align="right">
          <template #default="{ row }">
            <span v-if="row.diffAmount != null" :class="row.diffAmount >= 0 ? 'pos' : 'neg'">{{ row.diffAmount >= 0 ? '+' : '' }}{{ Number(row.diffAmount).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- P2-11 IND-05 双人核销复核弹窗 -->
    <DualVerifyDialog
      v-model="dualVerifyOpen"
      :record="dualVerifyRecord"
      @success="onDualVerifySuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Box } from '@element-plus/icons-vue'
import stockApi from '@/api/stock'
import { storeApi } from '@/api/basic'
import CheckCreateWizard from './CheckCreateWizard.vue'
import DualVerifyDialog from './components/DualVerifyDialog.vue'

const CHECK_STATUS_LABEL: Record<number, string> = { 0: '草稿', 1: '进行中', 2: '已完成', 3: '已作废' }
const CHECK_STATUS_TAG: Record<number, string> = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }

const loading = ref(false)
const checks = ref<any[]>([])
const products = ref<any[]>([])
const stores = ref<any[]>([])
const stats = ref<any>({})
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filter = ref<any>({ keyword: '', status: null, storeId: null })
const dateRange = ref<any[]>([])

const dialog = ref(false)
const detailDialog = ref(false)
const detail = ref<any>(null)
const detailItems = ref<any[]>([])

const formatTime = (t: any) => {
  if (!t) return ''
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return String(t)
    return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch { return String(t) }
}

const detailSummaries = ({ columns, data }: any) => {
  const sums: any[] = []
  let totalDiff = 0
  let totalAmount = 0
  columns.forEach((col: any, idx: number) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    if (col.label === '差异') {
      const total = data.reduce((acc: number, r: any) => acc + (r.diffQty || 0), 0)
      totalDiff = total
      sums[idx] = (total > 0 ? '+' : '') + total
    } else if (col.label === '差异金额') {
      const total = data.reduce((acc: number, r: any) => acc + (r.diffAmount || 0), 0)
      totalAmount = total
      sums[idx] = (total >= 0 ? '+' : '') + total.toFixed(2)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

const loadData = async () => {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value }
    if (filter.value.keyword) params.keyword = filter.value.keyword
    else delete params.keyword
    if (dateRange.value?.length === 2) {
      params.dateFrom = dateRange.value[0]
      params.dateTo = dateRange.value[1]
    }
    const res: any = await stockApi.checkPage(params)
    checks.value = res.data?.records || []
    total.value = res.data?.total || 0
    // 同步统计
    loadStats()
  } finally { loading.value = false }
}

const loadStats = async () => {
  try {
    const params: any = {}
    if (filter.value.storeId) params.storeId = filter.value.storeId
    if (dateRange.value?.length === 2) {
      params.dateFrom = dateRange.value[0]
      params.dateTo = dateRange.value[1]
    }
    const res: any = await stockApi.checkStats(params)
    stats.value = res.data || {}
  } catch {}
}

const loadProducts = async () => {
  try {
    const res: any = await stockApi.productPage({ pageNum: 1, pageSize: 200 })
    products.value = res.data?.records || []
  } catch {}
}

const loadStores = async () => {
  try {
    const res: any = await storeApi.list()
    stores.value = res.data || []
  } catch {}
}

const resetFilter = () => {
  filter.value = { keyword: '', status: null, storeId: null }
  dateRange.value = []
  pageNum.value = 1
  loadData()
}

const openDialog = async () => {
  await Promise.all([loadProducts(), loadStores()])
  dialog.value = true
}

const viewDetail = async (row: any) => {
  detail.value = row
  detailItems.value = []
  detailDialog.value = true
  if (!row.id) return
  try {
    const res: any = await stockApi.checkDetail(row.id)
    const data = res.data || {}
    if (data.check) detail.value = data.check
    detailItems.value = data.items || []
  } catch {}
}

const confirmCheck = async (row: any) => {
  await ElMessageBox.confirm(
    `确认盘点调整 [${row.checkNo || row.id}]？将根据实盘数量更新库存。`,
    '确认调整', { type: 'warning' }
  )
  try {
    const me = JSON.parse(localStorage.getItem('user') || '{}')
    await stockApi.checkConfirm(row.id, {
      confirmedBy: me.id || 1,
      confirmedByName: me.name || me.realName || 'admin'
    })
    ElMessage.success('调整成功')
    loadData()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.msg || '调整失败')
  }
}

const cancelCheck = async (row: any) => {
  const isReverse = row.status === 2
  await ElMessageBox.confirm(
    `确认${isReverse ? '反审' : '作废'}盘点单 [${row.checkNo || row.id}]？该操作不可恢复。`,
    `${isReverse ? '反审' : '作废'}盘点单`, { type: 'warning' }
  )
  try {
    await stockApi.checkCancel(row.id)
    ElMessage.success(isReverse ? '已反审' : '已作废')
    loadData()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.msg || `${isReverse ? '反审' : '作废'}失败`)
  }
}

const exportExcel = async () => {
  try {
    const params: any = { ...filter.value }
    if (filter.value.keyword) params.keyword = filter.value.keyword
    else delete params.keyword
    if (dateRange.value?.length === 2) {
      params.dateFrom = dateRange.value[0]
      params.dateTo = dateRange.value[1]
    }
    const res: any = await stockApi.checkExport(params)
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `盘点记录_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '导出失败')
  }
}

onMounted(() => {
  loadData()
  Promise.allSettled([loadProducts(), loadStores()])
})

// ==================== P2-11 IND-05 双人核销 ====================

const dualVerifyOpen = ref(false)
const dualVerifyRecord = ref<any>({})

async function openDualVerify(row: any) {
  // 调后端按盘点ID查双人核销记录
  try {
    const res: any = await stockApi.dualVerifyByCheck(row.id)
    const r = res?.data
    if (!r) {
      ElMessage.warning('该盘点单未触发双人核销（金额未达阈值或不在核销范围）')
      return
    }
    dualVerifyRecord.value = r
    dualVerifyOpen.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载核销记录失败')
  }
}

function onDualVerifySuccess() {
  loadData()
}
</script>

<style scoped>
.check-page { padding: 12px; }
.stats-row { margin-bottom: 12px; }
.stat { text-align: center; }
.stat .label { color: #999; font-size: 12px; }
.stat .value { font-size: 22px; font-weight: bold; margin-top: 4px; }
.stat .value.warn { color: #e6a23c; }
.stat .value.danger { color: #f56c6c; }
.toolbar { margin-bottom: 0; }
.filter-bar { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }
.pos { color: #67c23a; font-weight: bold; }
.neg { color: #f56c6c; font-weight: bold; }
.warn { color: #e6a23c; font-weight: bold; }
.text-muted { color: #c0c4cc; font-style: italic; font-size: 12px; }
</style>