<template>
  <div class="page">
    <div class="hero">
      <div><h2>结算管理</h2><p>生成月度结算单 → 确认 → 发放。结算后自动关联提成记录。</p></div>
      <el-button type="primary" @click="openGenerate">生成结算单</el-button>
    </div>
    <div class="kpis">
      <el-card><span>应付总额</span><b>¥{{ money(stats.totalPayable) }}</b></el-card>
      <el-card><span>待确认</span><b>¥{{ money(stats.pendingTotal) }}</b></el-card>
      <el-card><span>已确认</span><b>¥{{ money(stats.confirmedTotal) }}</b></el-card>
      <el-card><span>已发放</span><b>¥{{ money(stats.paidTotal) }}</b></el-card>
    </div>
    <el-card class="filter-card">
      <el-date-picker v-model="filter.salaryMonth" type="month" value-format="YYYY-MM" placeholder="工资月份" @change="reload" />
      <el-select v-model="filter.status" placeholder="状态" clearable @change="reload">
        <el-option label="待确认" :value="1"/><el-option label="已确认" :value="2"/><el-option label="已发放" :value="3"/>
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button><el-button @click="reset">重置</el-button>
    </el-card>
    <el-card>
      <div v-if="selectedRows.length" class="table-toolbar">
        <span>已选择 {{ selectedRows.length }} 条</span>
        <el-button size="small" type="primary" @click="batchConfirm" :loading="batchLoading">批量确认</el-button>
        <el-button size="small" type="success" @click="batchPay" :loading="batchLoading">批量发放</el-button>
        <el-button size="small" @click="selectedRows=[]">取消选择</el-button>
      </div>
      <el-table :data="rows" border stripe v-loading="loading" show-summary :summary-method="getSummaries" @row-click="openDetail" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="settlementNo" label="结算单号" width="180" />
        <el-table-column prop="salaryMonth" label="工资月份" width="110" />
        <el-table-column prop="employeeCount" label="员工人数" width="100" align="center">
          <template #default="{ row }">{{ row.employeeCount }} 人</template>
        </el-table-column>
        <el-table-column prop="totalCommission" label="总提成" width="120" align="right">
          <template #default="{ row }"><b>¥{{ money(row.totalCommission) }}</b></template>
        </el-table-column>
        <el-table-column prop="totalAdjustment" label="调整金额" width="120" align="right">
          <template #default="{ row }">{{ money(row.totalAdjustment) }}</template>
        </el-table-column>
        <el-table-column prop="totalPayable" label="应付总额" width="130" align="right">
          <template #default="{ row }"><b class="amount">¥{{ money(row.totalPayable) }}</b></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="确认/发放" width="165">
          <template #default="{ row }">
            <span v-if="row.confirmTime" class="time">确认 {{ row.confirmTime?.slice(0,16) }}</span>
            <span v-if="row.payTime" class="time">发放 {{ row.payTime?.slice(0,16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 1">
              <el-button link type="primary" @click.stop="confirmSettle(row)">确认</el-button>
            </template>
            <template v-else-if="row.status === 2">
              <el-button link type="success" @click.stop="paySettle(row)">发放</el-button>
            </template>
            <template v-else>
              <span class="done">已完成</span>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-button size="small" @click="exportExcel">导出结算单</el-button>
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @change="loadData" />
      </div>
    </el-card>

    <!-- 生成对话框 -->
    <el-dialog v-model="genDialog" title="生成结算单" width="420px">
      <el-form label-width="90px">
        <el-form-item label="工资月份">
          <el-date-picker v-model="genMonth" type="month" value-format="YYYY-MM" style="width:100%" placeholder="选择月份" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialog=false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="generate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 明细抽屉 -->
    <el-drawer v-model="detailVisible" title="结算明细" size="720px">
      <template v-if="currentSettlement">
        <div class="summary">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="结算单号">{{ currentSettlement.settlementNo }}</el-descriptions-item>
            <el-descriptions-item label="工资月份">{{ currentSettlement.salaryMonth }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(currentSettlement.status)">{{ statusText(currentSettlement.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="员工人数">{{ currentSettlement.employeeCount }}</el-descriptions-item>
            <el-descriptions-item label="总提成">¥{{ money(currentSettlement.totalCommission) }}</el-descriptions-item>
            <el-descriptions-item label="应付总额"><b>¥{{ money(currentSettlement.totalPayable) }}</b></el-descriptions-item>
          </el-descriptions>
        </div>
        <el-table :data="detailRows" border stripe size="small" show-summary>
          <el-table-column prop="employeeName" label="员工" width="120" />
          <el-table-column prop="baseSalary" label="底薪" width="100" align="right">
            <template #default="{ row }">¥{{ money(row.baseSalary) }}</template>
          </el-table-column>
          <el-table-column prop="commissionAmount" label="提成" width="110" align="right">
            <template #default="{ row }"><b>¥{{ money(row.commissionAmount) }}</b></template>
          </el-table-column>
          <el-table-column prop="adjustmentAmount" label="调整" width="100" align="right">
            <template #default="{ row }">{{ money(row.adjustmentAmount) }}</template>
          </el-table-column>
          <el-table-column prop="payableAmount" label="应付" width="110" align="right">
            <template #default="{ row }"><b class="amount">¥{{ money(row.payableAmount) }}</b></template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import commissionApi from '@/api/commission'

const currentMonth = () => new Date().toISOString().slice(0, 7)
const money = (v: any) => Number(v || 0).toFixed(2)
const statusText = (s: number) => ({ 1: '待确认', 2: '已确认', 3: '已发放' }[s] || '-')
const statusType = (s: number) => s === 1 ? 'warning' : s === 2 ? 'primary' : 'success'

const rows = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const filter = ref<any>({ salaryMonth: '', status: null })
const stats = ref<any>({})
const selectedRows = ref<any[]>([])
const batchLoading = ref(false)

const genDialog = ref(false)
const generating = ref(false)
const genMonth = ref(currentMonth())

const detailVisible = ref(false)
const currentSettlement = ref<any>(null)
const detailRows = ref<any[]>([])

function getSummaries({ columns, data }: any) {
  const sums: any[] = []
  columns.forEach((col: any, idx: number) => {
    if (idx === 0) { sums[idx] = '合计'; return }
    if (['employeeCount', 'totalCommission', 'totalAdjustment', 'totalPayable'].includes(col.property)) {
      sums[idx] = rows.value.reduce((acc: number, r: any) => acc + Number(r[col.property] || 0), 0)
      sums[idx] = idx === 1 ? sums[idx] + ' 人' : '¥' + sums[idx].toFixed(2)
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

function onSelectionChange(selection: any[]) {
  selectedRows.value = selection
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await commissionApi.settlementPage({ pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res: any = await commissionApi.settlementStats({ ...filter.value })
    stats.value = res.data || {}
  } catch {
    stats.value = {}
  }
}

function reload() {
  pageNum.value = 1
  Promise.all([loadData(), loadStats()])
}

function reset() {
  filter.value = { salaryMonth: '', status: null }
  pageNum.value = 1
  loadData()
  loadStats()
}

function openGenerate() {
  genMonth.value = currentMonth()
  genDialog.value = true
}

async function generate() {
  generating.value = true
  try {
    const res: any = await commissionApi.generateSettlement({ storeId: 1, salaryMonth: genMonth.value })
    if (res.code === 200) {
      ElMessage.success('结算单生成成功')
      genDialog.value = false
      reload()
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } finally {
    generating.value = false
  }
}

async function openDetail(row: any) {
  currentSettlement.value = row
  const res: any = await commissionApi.settlementDetail(row.id)
  if (res.code === 200) {
    detailRows.value = res.data?.records || []
  }
  detailVisible.value = true
}

async function confirmSettle(row: any) {
  await ElMessageBox.confirm(`确认结算单 ${row.settlementNo}？确认后将锁定结算数据。`, '确认结算')
  const res: any = await commissionApi.confirmSettlement(row.id)
  if (res.code === 200) {
    ElMessage.success('结算单已确认')
    loadData()
    loadStats()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

async function paySettle(row: any) {
  await ElMessageBox.confirm(`确认发放结算单 ${row.settlementNo}？`, '发放确认')
  const res: any = await commissionApi.paySettlement(row.id)
  if (res.code === 200) {
    ElMessage.success('结算单已发放')
    loadData()
    loadStats()
  } else {
    ElMessage.error(res.message || '操作失败')
  }
}

async function batchConfirm() {
  const ids = selectedRows.value.filter(r => r.status === 1).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请选择待确认状态的结算单')
    return
  }
  await ElMessageBox.confirm(`确认选中的 ${ids.length} 条结算单？确认后将锁定结算数据。`, '批量确认', { type: 'warning' })
  batchLoading.value = true
  try {
    const res: any = await commissionApi.settlementBatchConfirm(ids)
    ElMessage.success(res.message || '批量确认成功')
    selectedRows.value = []
    reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    batchLoading.value = false
  }
}

async function batchPay() {
  const ids = selectedRows.value.filter(r => r.status === 2).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请选择已确认状态的结算单')
    return
  }
  await ElMessageBox.confirm(`确认发放选中的 ${ids.length} 条结算单？`, '批量发放', { type: 'warning' })
  batchLoading.value = true
  try {
    const res: any = await commissionApi.settlementBatchPay(ids)
    ElMessage.success(res.message || '批量发放成功')
    selectedRows.value = []
    reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    batchLoading.value = false
  }
}

async function exportExcel() {
  try {
    ElMessage.info('正在导出...')
    const res: any = await commissionApi.settlementExport({ ...filter.value })
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `结算单_${filter.value.salaryMonth || currentMonth()}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

onMounted(() => {
  loadStats()
  loadData()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px }
.hero { display: flex; align-items: center; justify-content: space-between; padding: 22px 26px; border-radius: 12px; color: #fff; background: linear-gradient(120deg, #1565c0, #42a5f5); box-shadow: 0 8px 24px #1565c033 }
.hero h2 { margin: 0 0 6px }
.hero p { margin: 0; opacity: .88 }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px }
.kpis :deep(.el-card) { background: linear-gradient(135deg, #1565c0, #42a5f5); border: none }
.kpis :deep(.el-card__body) { display: flex; flex-direction: column; gap: 8px }
.kpis span { color: rgba(255,255,255,0.8); font-size: 13px }
.kpis b { font-size: 26px; color: #fff; font-weight: 700 }
.filter-card :deep(.el-card__body) { display: flex; gap: 10px; align-items: center }
.filter-card .el-input, .filter-card .el-select { width: 180px }
.amount { color: #1565c0 }
.time { font-size: 12px; color: #888 }
.done { color: #aaa; font-size: 13px }
.summary { margin-bottom: 16px }
.table-toolbar { padding: 8px 12px; margin-bottom: 10px; background: #f5f7fa; border-radius: 6px; display: flex; align-items: center; gap: 10px; font-size: 13px }
.table-toolbar span { color: #606266; font-weight: 500 }
.table-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 14px }
.el-pagination { justify-content: flex-end }
</style>
