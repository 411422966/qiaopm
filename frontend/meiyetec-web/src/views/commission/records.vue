<template>
  <div class="page">
    <div class="hero"><div><h2>提成记录</h2><p>订单支付后自动按规则计算；重复计算保持幂等，退款记录自动作废。</p></div></div>
    <div class="kpis">
      <el-card><span>本月提成</span><b>¥{{ money(stats.commissionAmount) }}</b></el-card>
      <el-card><span>提成业绩</span><b>¥{{ money(stats.performanceAmount) }}</b></el-card>
      <el-card><span>员工人数</span><b>{{ stats.employeeCount || 0 }}</b></el-card>
      <el-card><span>提成笔数</span><b>{{ stats.recordCount || 0 }}</b></el-card>
    </div>
    <el-card class="filter-card">
      <el-date-picker v-model="filter.salaryMonth" type="month" value-format="YYYY-MM" placeholder="工资月份" @change="reload" />
      <el-select v-model="filter.employeeId" placeholder="员工" clearable filterable @change="loadData"><el-option v-for="e in employees" :key="e.id" :label="e.employeeName" :value="e.id" /></el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable @change="loadData"><el-option label="待结算" :value="1"/><el-option label="已结算" :value="2"/><el-option label="已作废" :value="3"/></el-select>
      <el-input v-model="filter.keyword" placeholder="员工/订单/项目" clearable @keyup.enter="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="openCalculate">补算订单</el-button>
      <el-button type="success" @click="exportExcel">导出</el-button>
    </el-card>
    <el-card>
      <div v-if="selectedRows.length" class="table-toolbar">
        <span>已选择 {{ selectedRows.length }} 条</span>
        <el-button size="small" type="success" @click="batchSettle" :loading="batchLoading">批量结算</el-button>
        <el-button size="small" type="info" @click="batchInvalidate" :loading="batchLoading">批量作废</el-button>
        <el-button size="small" @click="selectedRows=[]">取消选择</el-button>
      </div>
      <el-table :data="rows" border stripe v-loading="loading" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column label="员工" width="130">
          <template #default="{row}">
            <div class="employee-cell">
              <el-avatar :size="28" :src="row.avatar"><el-icon><User /></el-icon></el-avatar>
              <span>{{ row.employeeName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="businessTime" label="业务时间" width="170" />
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column prop="serviceName" label="服务项目" min-width="150" />
        <el-table-column label="订单实收" width="110" align="right">
          <template #default="{row}"><span class="text-muted">¥{{ money(row.orderAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="业绩基数" width="110" align="right"><template #default="{row}">¥{{ money(row.baseAmount) }}</template></el-table-column>
        <el-table-column prop="ruleName" label="匹配规则" min-width="150" />
        <el-table-column label="提成" width="110" align="right"><template #default="{row}"><b class="amount">¥{{ money(row.commissionAmount) }}</b></template></el-table-column>
        <el-table-column prop="salaryMonth" label="工资月" width="95" />
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="statusType(row.status)">{{statusText(row.status)}}</el-tag></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="暂无提成记录" :image-size="100" />
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @change="loadData" />
    </el-card>
    <el-dialog v-model="calculateDialog" title="补算已支付订单" width="420px"><el-form label-width="90px"><el-form-item label="订单ID"><el-input-number v-model="orderId" :min="1" style="width:100%" /></el-form-item></el-form><template #footer><el-button @click="calculateDialog=false">取消</el-button><el-button type="primary" :loading="calculating" @click="calculate">开始计算</el-button></template></el-dialog>
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
const calculateDialog = ref(false)
const calculating = ref(false)
const orderId = ref<number>()
const selectedRows = ref<any[]>([])
const batchLoading = ref(false)

const filter = ref<any>({ salaryMonth: currentMonth(), employeeId: null, status: null, keyword: '' })

const money = (v: any) => Number(v || 0).toFixed(2)
const statusText = (s: number) => ({ 1: '待结算', 2: '已结算', 3: '已作废' }[s] || '-')
const statusType = (s: number) => s === 1 ? 'warning' : s === 2 ? 'success' : 'info'

function onSelectionChange(selection: any[]) {
  selectedRows.value = selection
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await commissionApi.recordPage({ pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res: any = await commissionApi.recordStats({ salaryMonth: filter.value.salaryMonth })
    stats.value = res.data || {}
  } catch {
    stats.value = {}
  }
}

async function reload() {
  pageNum.value = 1
  await Promise.all([loadData(), loadStats()])
}

function openCalculate() {
  orderId.value = undefined
  calculateDialog.value = true
}

async function calculate() {
  if (!orderId.value) {
    ElMessage.warning('请输入订单ID')
    return
  }
  calculating.value = true
  try {
    const res: any = await commissionApi.calculateOrder(orderId.value)
    ElMessage.success(`计算完成，共 ${res.data?.length || 0} 条`)
    calculateDialog.value = false
    reload()
  } finally {
    calculating.value = false
  }
}

async function exportExcel() {
  try {
    ElMessage.info('正在导出...')
    const res: any = await commissionApi.recordExport({ ...filter.value })
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `提成记录_${filter.value.salaryMonth || currentMonth()}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

async function batchSettle() {
  const ids = selectedRows.value.filter(r => r.status === 1).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请选择待结算的记录')
    return
  }
  await ElMessageBox.confirm(`确认结算选中的 ${ids.length} 条记录？`, '批量结算', { type: 'warning' })
  batchLoading.value = true
  try {
    await commissionApi.recordBatchSettle(ids)
    ElMessage.success('批量结算成功')
    selectedRows.value = []
    loadData()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    batchLoading.value = false
  }
}

async function batchInvalidate() {
  const ids = selectedRows.value.filter(r => r.status === 1).map(r => r.id)
  if (ids.length === 0) {
    ElMessage.warning('请选择待结算的记录')
    return
  }
  await ElMessageBox.confirm(`确认作废选中的 ${ids.length} 条记录？`, '批量作废', { type: 'warning' })
  batchLoading.value = true
  try {
    await commissionApi.recordBatchInvalidate(ids)
    ElMessage.success('批量作废成功')
    selectedRows.value = []
    loadData()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    batchLoading.value = false
  }
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
.hero { padding: 22px 26px; border-radius: 12px; color: #fff; background: linear-gradient(120deg, #00695c, #26a69a) }
.hero h2 { margin: 0 0 6px }
.hero p { margin: 0; opacity: .9 }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px }
.kpis :deep(.el-card__body) { display: flex; flex-direction: column; gap: 10px }
.kpis span { color: #778 }
.kpis b { font-size: 24px; color: #263238 }
.filter-card :deep(.el-card__body) { display: flex; gap: 10px; flex-wrap: wrap }
.filter-card .el-input, .filter-card .el-select { width: 180px }
.amount { color: #00897b }
.employee-cell { display: flex; align-items: center; gap: 8px }
.employee-cell span { font-weight: 500 }
.text-muted { color: #909399; font-size: 13px }
.table-toolbar { padding: 8px 12px; margin-bottom: 10px; background: #f5f7fa; border-radius: 6px; display: flex; align-items: center; gap: 10px; font-size: 13px }
.table-toolbar span { color: #606266; font-weight: 500 }
.el-pagination { margin-top: 14px; justify-content: flex-end }
</style>
