<template>
  <div class="reversal-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">待审核</div>
        <div class="kpi-value">{{ stats.pending ?? '—' }}</div>
      </div>
      <div class="kpi-item green">
        <div class="kpi-label">已生效</div>
        <div class="kpi-value">{{ stats.approved ?? '—' }}</div>
      </div>
      <div class="kpi-item red">
        <div class="kpi-label">已拒绝</div>
        <div class="kpi-value">{{ stats.rejected ?? '—' }}</div>
      </div>
      <div class="kpi-item yellow">
        <div class="kpi-label">冲正总额</div>
        <div class="kpi-value">¥{{ formatAmount(stats.totalAmount) }}</div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="冲正号 / 员工 / 原因" style="width: 220px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 140px" @change="loadData">
        <el-option label="待审核" :value="1" />
        <el-option label="已生效" :value="2" />
        <el-option label="已拒绝" :value="3" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="warning" @click="openCreate">+ 触发冲正</el-button>
      <el-button type="success" @click="exportCsv">导出 CSV</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="reversalNo" label="冲正号" width="170" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.reversalType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="120" />
        <el-table-column prop="sourceId" label="来源ID" width="100" />
        <el-table-column prop="employeeName" label="员工" width="100" />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approveByName" label="审核人" width="100" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="success" :disabled="row.status !== 1" @click="approve(row)">审核通过</el-button>
            <el-button link size="small" type="danger" :disabled="row.status !== 1" @click="reject(row)">拒绝</el-button>
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

    <!-- 触发冲正 -->
    <el-dialog v-model="dialog.visible" title="触发冲正（演示）" width="500px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="类型" required>
          <el-select v-model="form.reversalType" style="width: 100%">
            <el-option label="退款" value="REFUND" />
            <el-option label="撤销核销" value="UNDO_REDEEM" />
            <el-option label="人工调整" value="MANUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" required>
          <el-select v-model="form.sourceType" style="width: 100%">
            <el-option label="订单" value="ORDER" />
            <el-option label="套餐核销" value="PACKAGE_REDEMPTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源ID" required>
          <el-input-number v-model="form.sourceId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="明细ID">
          <el-input-number v-model="form.sourceItemId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">提交</el-button>
      </template>
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
  keyword: '', status: undefined as number | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = reactive<any>({})

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/commission/reversal/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const r = await http.get('/commission/reversal/stats')
    Object.assign(stats, r.data || {})
  } catch {}
}

function typeLabel(t: string) {
  return { REFUND: '退款', UNDO_REDEEM: '撤销核销', MANUAL: '人工' }[t] || t
}

function statusLabel(s: number) {
  return { 1: '待审核', 2: '已生效', 3: '已拒绝' }[s] || '未知'
}
function statusTag(s: number) {
  return ({ 1: 'warning', 2: 'success', 3: 'danger' } as any)[s] || 'info'
}

function formatAmount(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

function resetFilter() {
  filter.keyword = ''
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

const dialog = reactive({ visible: false, saving: false })
const form = reactive<any>({
  reversalType: 'REFUND',
  sourceType: 'ORDER',
  sourceId: undefined,
  sourceItemId: undefined,
  reason: ''
})

function openCreate() {
  dialog.visible = true
}

function resetDialog() {
  Object.assign(form, {
    reversalType: 'REFUND',
    sourceType: 'ORDER',
    sourceId: undefined,
    sourceItemId: undefined,
    reason: ''
  })
}

async function save() {
  if (!form.sourceId) return ElMessage.warning('来源ID必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    await http.post('/commission/reversal', form, { headers })
    ElMessage.success('冲正已提交（待审核）')
    dialog.visible = false
    loadData()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  } finally {
    dialog.saving = false
  }
}

async function approve(row: any) {
  await ElMessageBox.confirm(`审核通过 ${row.reversalNo}？`, '提示', { type: 'warning' })
  await http.post(`/commission/reversal/${row.id}/approve`)
  ElMessage.success('已生效')
  loadData()
  loadStats()
}

async function reject(row: any) {
  await ElMessageBox.confirm(`拒绝 ${row.reversalNo}？`, '提示', { type: 'warning' })
  await http.post(`/commission/reversal/${row.id}/reject`)
  ElMessage.success('已拒绝')
  loadData()
  loadStats()
}

async function exportCsv() {
  try {
    const res = await http.get('/commission/reversal/export', {
      params: { status: filter.status },
      responseType: 'blob'
    })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '提成冲正.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  await loadData()
  await loadStats()
})
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.red { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
</style>
