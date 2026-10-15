<template>
  <div class="batch-page">
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="批次号 / 备注" style="width: 200px" clearable @keyup.enter="loadData" />
      <el-input v-model="filter.salaryMonth" placeholder="工资月 YYYY-MM" style="width: 160px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 140px" @change="loadData">
        <el-option label="草稿" :value="1" />
        <el-option label="已确认" :value="2" />
        <el-option label="已发放" :value="3" />
        <el-option label="已取消" :value="4" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openGenerate">⚡ 生成批次</el-button>
      <el-button type="success" @click="exportBatchCsv">导出 CSV</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="batchNo" label="批次号" width="170" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ batchTypeLabel(row.batchType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="期间" width="220">
          <template #default="{ row }">
            {{ formatDate(row.periodStart) }} ~ {{ formatDate(row.periodEnd) }}
          </template>
        </el-table-column>
        <el-table-column prop="salaryMonth" label="工资月" width="100" />
        <el-table-column prop="employeeCount" label="员工" width="80" align="center" />
        <el-table-column prop="recordCount" label="记录" width="80" align="center" />
        <el-table-column prop="totalCommission" label="提成总额" width="120" align="right">
          <template #default="{ row }">¥{{ row.totalCommission }}</template>
        </el-table-column>
        <el-table-column prop="totalAdjustment" label="调整" width="100" align="right">
          <template #default="{ row }">¥{{ row.totalAdjustment }}</template>
        </el-table-column>
        <el-table-column prop="totalPayable" label="应付" width="120" align="right">
          <template #default="{ row }">¥{{ row.totalPayable }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confirmByName" label="确认人" width="100" />
        <el-table-column prop="payByName" label="发放人" width="100" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="viewDetail(row)">明细</el-button>
            <el-button link size="small" type="success" :disabled="row.status !== 1" @click="confirm(row)">确认</el-button>
            <el-button link size="small" type="warning" :disabled="row.status !== 2" @click="pay(row)">发放</el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 3" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- 生成批次 -->
    <el-dialog v-model="genDialog.visible" title="生成批次" width="640px" @closed="resetGen">
      <el-form :model="genForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="期间起" required>
              <el-date-picker v-model="genForm.periodStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期间止" required>
              <el-date-picker v-model="genForm.periodEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="工资月" required>
              <el-input v-model="genForm.salaryMonth" placeholder="YYYY-MM" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次类型">
              <el-select v-model="genForm.batchType" style="width: 100%">
                <el-option label="月度" value="MONTHLY" />
                <el-option label="周度" value="WEEKLY" />
                <el-option label="自定义" value="CUSTOM" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="genForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="runGenerate" :loading="genDialog.running">生成</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailDialog.visible" :title="detailDialog.batch?.batchNo || ''" width="800px" @closed="resetDetail">
      <div v-if="detailDialog.batch">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="期间">{{ formatDate(detailDialog.batch.periodStart) }} ~ {{ formatDate(detailDialog.batch.periodEnd) }}</el-descriptions-item>
          <el-descriptions-item label="工资月">{{ detailDialog.batch.salaryMonth }}</el-descriptions-item>
          <el-descriptions-item label="员工数">{{ detailDialog.batch.employeeCount }}</el-descriptions-item>
          <el-descriptions-item label="记录数">{{ detailDialog.batch.recordCount }}</el-descriptions-item>
          <el-descriptions-item label="总提成">¥{{ detailDialog.batch.totalCommission }}</el-descriptions-item>
          <el-descriptions-item label="调整额">¥{{ detailDialog.batch.totalAdjustment }}</el-descriptions-item>
          <el-descriptions-item label="应付额">¥{{ detailDialog.batch.totalPayable }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detailDialog.batch.status)" size="small">{{ statusLabel(detailDialog.batch.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">员工明细</el-divider>
        <el-table :data="detailDialog.items" border size="small">
          <el-table-column prop="employeeId" label="员工ID" width="80" />
          <el-table-column prop="employeeName" label="员工" width="120" />
          <el-table-column prop="recordCount" label="记录" width="80" align="center" />
          <el-table-column label="提成" width="120" align="right">
            <template #default="{ row }">¥{{ row.commissionAmount }}</template>
          </el-table-column>
          <el-table-column label="调整" width="120" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.adjustmentAmount" :step="1" :precision="2" size="small" style="width: 100px" @change="(val: any) => adjustItem(row, val)" />
            </template>
          </el-table-column>
          <el-table-column label="冲正" width="100" align="right">
            <template #default="{ row }">¥{{ row.reversalAmount }}</template>
          </el-table-column>
          <el-table-column label="应付" width="120" align="right">
            <template #default="{ row }">¥{{ row.payableAmount }}</template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 12px; text-align: right">
          <el-button @click="exportItemsCsv(detailDialog.batch.id)">导出明细 CSV</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', salaryMonth: '', status: undefined as number | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/commission/batch/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function statusLabel(s: number) {
  return { 1: '草稿', 2: '已确认', 3: '已发放', 4: '已取消', 5: '审批中' }[s] || '未知'
}
function statusTag(s: number) {
  return ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger', 5: 'primary' } as any)[s] || 'info'
}
function batchTypeLabel(t: string) {
  return { MONTHLY: '月度', WEEKLY: '周度', CUSTOM: '自定义' }[t] || t
}

function formatDate(d: string) {
  if (!d) return ''
  return d.length > 10 ? d.substring(0, 10) : d
}

function resetFilter() {
  filter.keyword = ''
  filter.salaryMonth = ''
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

const genDialog = reactive({ visible: false, running: false })
const genForm = reactive({
  periodStart: undefined as string | undefined,
  periodEnd: undefined as string | undefined,
  salaryMonth: '',
  batchType: 'MONTHLY',
  remark: ''
})

function openGenerate() {
  genDialog.visible = true
}

function resetGen() {
  Object.assign(genForm, {
    periodStart: undefined, periodEnd: undefined,
    salaryMonth: '', batchType: 'MONTHLY', remark: ''
  })
}

async function runGenerate() {
  if (!genForm.periodStart || !genForm.periodEnd) return ElMessage.warning('期间必填')
  if (!genForm.salaryMonth) return ElMessage.warning('工资月必填')
  genDialog.running = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    const r = await http.post('/commission/batch/generate', genForm, { headers })
    ElMessage.success(`已生成批次 ${r.data.batchNo}，员工 ${r.data.employeeCount} 人，金额 ¥${r.data.totalPayable}`)
    genDialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成失败')
  } finally {
    genDialog.running = false
  }
}

async function confirm(row: any) {
  await ElMessageBox.confirm(`确认批次 ${row.batchNo}？`, '提示', { type: 'warning' })
  await http.post(`/commission/batch/${row.id}/confirm`)
  ElMessage.success('已确认')
  loadData()
}

async function pay(row: any) {
  await ElMessageBox.confirm(`发放批次 ${row.batchNo}？`, '提示', { type: 'warning' })
  await http.post(`/commission/batch/${row.id}/pay`)
  ElMessage.success('已发放')
  loadData()
}

async function cancel(row: any) {
  await ElMessageBox.confirm(`取消批次 ${row.batchNo}？`, '提示', { type: 'warning' })
  await http.post(`/commission/batch/${row.id}/cancel`)
  ElMessage.success('已取消')
  loadData()
}

const detailDialog = reactive<any>({ visible: false, batch: null, items: [] })
async function viewDetail(row: any) {
  const r = await http.get(`/commission/batch/${row.id}`)
  detailDialog.batch = r.data.batch
  detailDialog.items = r.data.items
  detailDialog.visible = true
}

function resetDetail() {
  detailDialog.batch = null
  detailDialog.items = []
}

async function adjustItem(row: any, val: any) {
  try {
    await http.put(`/commission/batch/item/${row.id}/adjust`, { adjustmentAmount: val })
    ElMessage.success('已调整')
    const r = await http.get(`/commission/batch/${detailDialog.batch.id}`)
    detailDialog.batch = r.data.batch
    detailDialog.items = r.data.items
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '调整失败')
  }
}

async function exportBatchCsv() {
  try {
    const res = await http.get('/commission/batch/export', {
      params: { salaryMonth: filter.salaryMonth, status: filter.status },
      responseType: 'blob'
    })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '提成结算批次.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

async function exportItemsCsv(id: number) {
  try {
    const res = await http.get(`/commission/batch/${id}/item/export`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '批次明细.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
</style>
