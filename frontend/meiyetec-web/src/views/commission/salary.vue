<template>
  <div class="page">
    <div class="hero">
      <div>
        <h2>工资报表</h2>
        <p>汇总基础工资、有效业绩和提成，支持调整、确认与发放闭环。</p>
      </div>
      <el-button type="primary" :loading="generating" @click="generate">生成 / 刷新本月工资</el-button>
    </div>
    <div class="kpis">
      <el-card><span>应发总额</span><b>¥{{ money(stats.payableTotal) }}</b></el-card>
      <el-card><span>已确认</span><b>¥{{ money(stats.confirmedTotal) }}</b></el-card>
      <el-card><span>已发放</span><b>¥{{ money(stats.paidTotal) }}</b></el-card>
      <el-card><span>待发放</span><b>¥{{ money(stats.pendingTotal) }}</b></el-card>
    </div>
    <el-card class="filter-card">
      <el-date-picker v-model="filter.salaryMonth" type="month" value-format="YYYY-MM" @change="reload" />
      <el-select v-model="filter.employeeId" placeholder="员工" clearable filterable @change="loadData"><el-option v-for="e in employees" :key="e.id" :label="e.employeeName" :value="e.id" /></el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable @change="loadData"><el-option label="草稿" :value="1"/><el-option label="已确认" :value="2"/><el-option label="已发放" :value="3"/></el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="success" @click="exportExcel">导出</el-button>
    </el-card>
    <el-card>
      <div v-if="selectedRows.length" class="table-toolbar">
        <span>已选择 {{ selectedRows.length }} 条</span>
        <el-button size="small" type="success" @click="batchConfirm" :loading="batchLoading">批量确认</el-button>
        <el-button size="small" type="warning" @click="batchPay" :loading="batchLoading">批量发放</el-button>
        <el-button size="small" @click="selectedRows=[]">取消选择</el-button>
      </div>
      <el-table :data="rows" border stripe v-loading="loading" show-summary :summary-method="summary" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="salaryMonth" label="月份" width="90" />
        <el-table-column label="员工" width="130">
          <template #default="{row}">
            <div class="employee-cell">
              <el-avatar :size="28" :src="row.avatar"><el-icon><User /></el-icon></el-avatar>
              <span>{{ row.employeeName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="基础工资" width="110" align="right"><template #default="{row}">¥{{ money(row.baseSalary) }}</template></el-table-column>
        <el-table-column label="业绩" width="120" align="right"><template #default="{row}">¥{{ money(row.performanceAmount) }}</template></el-table-column>
        <el-table-column label="提成" width="110" align="right"><template #default="{row}"><span class="commission">¥{{ money(row.commissionAmount) }}</span></template></el-table-column>
        <el-table-column label="调整" width="110" align="right"><template #default="{row}"><span :class="Number(row.adjustmentAmount)<0?'negative':''">¥{{ money(row.adjustmentAmount) }}</span></template></el-table-column>
        <el-table-column label="应发工资" width="130" align="right"><template #default="{row}"><b>¥{{ money(row.payableAmount) }}</b></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="190" fixed="right">
          <template #default="{row}">
            <el-button v-if="row.status===1" link type="primary" @click="openAdjustment(row)">调整</el-button>
            <el-button v-if="row.status===1" link type="success" @click="confirm(row)">确认</el-button>
            <el-button v-if="row.status===2" link type="warning" @click="pay(row)">标记发放</el-button>
            <span v-if="row.status===3" class="done">已完成</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="暂无工资记录，点击上方按钮生成" :image-size="100" />
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @change="loadData" />
    </el-card>
    <el-dialog v-model="adjustDialog" title="工资调整" width="460px">
      <el-form :model="adjustForm" label-width="90px">
        <el-form-item label="员工">{{ adjustForm.employeeName }}</el-form-item>
        <el-form-item label="调整金额">
          <el-input-number v-model="adjustForm.adjustmentAmount" :precision="2" style="width:100%" />
          <div class="hint">正数为奖励，负数为扣款</div>
        </el-form-item>
        <el-form-item label="调整说明"><el-input v-model="adjustForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialog=false">取消</el-button>
        <el-button type="primary" @click="saveAdjustment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import commissionApi from '@/api/commission'
import { employeeApi } from '@/api/basic'

const currentMonth = () => new Date().toISOString().slice(0, 7)

const rows = ref<any[]>([])
const employees = ref<any[]>([])
const stats = ref<any>({})
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const generating = ref(false)
const adjustDialog = ref(false)
const adjustForm = ref<any>({})
const selectedRows = ref<any[]>([])
const batchLoading = ref(false)

const filter = ref<any>({ salaryMonth: currentMonth(), employeeId: null, status: null })

const money = (v: any) => Number(v || 0).toFixed(2)
const statusText = (s: number) => ({ 1: '草稿', 2: '已确认', 3: '已发放' }[s] || '-')
const statusType = (s: number) => s === 1 ? 'info' : s === 2 ? 'warning' : 'success'

function onSelectionChange(selection: any[]) {
  selectedRows.value = selection
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await commissionApi.salaryPage({ pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res: any = await commissionApi.salaryStats({ salaryMonth: filter.value.salaryMonth })
    stats.value = res.data || {}
  } catch {
    stats.value = {}
  }
}

async function reload() {
  pageNum.value = 1
  await Promise.all([loadData(), loadStats()])
}

async function generate() {
  generating.value = true
  try {
    const res: any = await commissionApi.generateSalary({ storeId: 1, salaryMonth: filter.value.salaryMonth })
    ElMessage.success(`已生成 ${res.data?.length || 0} 名员工工资`)
    reload()
  } finally {
    generating.value = false
  }
}

function openAdjustment(row: any) {
  adjustForm.value = { id: row.id, employeeName: row.employeeName, adjustmentAmount: Number(row.adjustmentAmount || 0), remark: row.remark || '' }
  adjustDialog.value = true
}

async function saveAdjustment() {
  await commissionApi.updateAdjustment(adjustForm.value.id, { adjustmentAmount: adjustForm.value.adjustmentAmount, remark: adjustForm.value.remark })
  ElMessage.success('调整成功')
  adjustDialog.value = false
  loadData()
}

async function confirm(row: any) {
  await ElMessageBox.confirm(`确认 ${row.employeeName} ${row.salaryMonth} 工资？确认后提成将结算且不可调整。`, '工资确认', { type: 'warning' })
  await commissionApi.confirmSalary(row.id)
  ElMessage.success('工资已确认')
  loadData()
  loadStats()
}

async function pay(row: any) {
  await ElMessageBox.confirm(`确认将 ${row.employeeName} 工资标记为已发放？`, '发放确认', { type: 'warning' })
  await commissionApi.paySalary(row.id)
  ElMessage.success('已标记发放')
  loadData()
  loadStats()
}

async function exportExcel() {
  try {
    ElMessage.info('正在导出...')
    const res: any = await commissionApi.salaryExport({ ...filter.value })
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `工资报表_${filter.value.salaryMonth || currentMonth()}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

async function batchConfirm() {
  const ids = selectedRows.value.filter(r => r.status === 1).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请选择草稿状态的记录')
    return
  }
  await ElMessageBox.confirm(`确认选中的 ${ids.length} 条工资？确认后不可调整。`, '批量确认', { type: 'warning' })
  batchLoading.value = true
  try {
    await commissionApi.salaryBatchConfirm(ids)
    ElMessage.success('批量确认成功')
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
    ElMessage.warning('请选择已确认的记录')
    return
  }
  await ElMessageBox.confirm(`确认发放选中的 ${ids.length} 条工资？`, '批量发放', { type: 'warning' })
  batchLoading.value = true
  try {
    await commissionApi.salaryBatchPay(ids)
    ElMessage.success('批量发放成功')
    selectedRows.value = []
    reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    batchLoading.value = false
  }
}

function summary({ columns, data }: any) {
  return columns.map((_: any, index: number) => {
    if (index === 0) return '合计'
    const fields = ['', '', '', 'baseSalary', 'performanceAmount', 'commissionAmount', 'adjustmentAmount', 'payableAmount']
    const f = fields[index]
    return f ? `¥${data.reduce((s: number, r: any) => s + Number(r[f] || 0), 0).toFixed(2)}` : ''
  })
}

onMounted(async () => {
  try {
    const res: any = await employeeApi.list()
    employees.value = res.data || []
  } catch { }
  await reload()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px }
.hero { display: flex; align-items: center; justify-content: space-between; padding: 22px 26px; border-radius: 12px; color: #fff; background: linear-gradient(120deg, #6a1b9a, #ab47bc) }
.hero h2 { margin: 0 0 6px }
.hero p { margin: 0; opacity: .9 }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px }
.kpis :deep(.el-card) { background: linear-gradient(135deg, #6a1b9a, #ab47bc); border: none }
.kpis :deep(.el-card__body) { display: flex; flex-direction: column; gap: 8px }
.kpis span { color: rgba(255,255,255,0.8); font-size: 13px }
.kpis b { font-size: 26px; color: #fff; font-weight: 700 }
.filter-card :deep(.el-card__body) { display: flex; gap: 10px; flex-wrap: wrap }
.filter-card .el-select { width: 180px }
.commission { color: #ce93d8; font-weight: 700 }
.negative { color: #ef5350 }
.done { color: #66bb6a }
.hint { font-size: 12px; color: #909399 }
.employee-cell { display: flex; align-items: center; gap: 8px }
.employee-cell span { font-weight: 500 }
.table-toolbar { padding: 8px 12px; margin-bottom: 10px; background: #f5f7fa; border-radius: 6px; display: flex; align-items: center; gap: 10px; font-size: 13px }
.table-toolbar span { color: #606266; font-weight: 500 }
.el-pagination { margin-top: 14px; justify-content: flex-end }
</style>
