<template>
  <div class="promotion-page">
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="编码 / 名称 / 描述" style="width: 220px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.promotionType" placeholder="类型" clearable style="width: 140px" @change="loadData">
        <el-option label="满减" value="FULL_REDUCE" />
        <el-option label="折扣" value="DISCOUNT" />
        <el-option label="买赠" value="GIFT" />
        <el-option label="付费券" value="CASH_COUPON" />
        <el-option label="积分兑换" value="POINTS_EXCHANGE" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
        <el-option label="结束" :value="2" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openCreate">+ 新增活动</el-button>
      <el-button type="success" @click="exportCsv">导出 CSV</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="promotionCode" label="编码" width="140" />
        <el-table-column prop="promotionName" label="名称" min-width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeColor(row.promotionType)">{{ typeLabel(row.promotionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">¥{{ row.thresholdAmount }}</template>
        </el-table-column>
        <el-table-column label="减免/折扣" width="120">
          <template #default="{ row }">
            <span v-if="row.promotionType === 'DISCOUNT'">{{ row.discountRate }} 折</span>
            <span v-else>¥{{ row.reduceAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="叠加" width="80">
          <template #default="{ row }">
            <el-tag :type="row.stackable === 1 ? 'success' : 'info'" size="small">
              {{ row.stackable === 1 ? '可叠加' : '互斥' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配额" width="140">
          <template #default="{ row }">
            {{ row.quotaUsed || 0 }} / {{ row.quotaTotal || '∞' }}
          </template>
        </el-table-column>
        <el-table-column label="时段" width="280">
          <template #default="{ row }">
            {{ formatDate(row.startTime) }} ~ {{ formatDate(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑活动' : '新增活动'" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" required>
              <el-input v-model="form.promotionCode" :disabled="!!dialog.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.promotionName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="类型" required>
          <el-select v-model="form.promotionType" style="width: 100%">
            <el-option label="满减" value="FULL_REDUCE" />
            <el-option label="折扣" value="DISCOUNT" />
            <el-option label="买赠" value="GIFT" />
            <el-option label="付费券" value="CASH_COUPON" />
            <el-option label="积分兑换" value="POINTS_EXCHANGE" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="门槛">
              <el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="减免额">
              <el-input-number v-model="form.reduceAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣">
              <el-input-number v-model="form.discountRate" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="可叠加">
              <el-radio-group v-model="form.stackable">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总配额">
              <el-input-number v-model="form.quotaTotal" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="每人限">
              <el-input-number v-model="form.perMemberLimit" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
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
  keyword: '',
  promotionType: undefined as string | undefined,
  status: undefined as number | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/mkt/promotion/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function typeLabel(t: string) {
  return ({ FULL_REDUCE: '满减', DISCOUNT: '折扣', GIFT: '买赠', CASH_COUPON: '付费券', POINTS_EXCHANGE: '积分兑换' } as any)[t] || t
}
function typeColor(t: string) {
  return ({ FULL_REDUCE: 'danger', DISCOUNT: 'warning', GIFT: 'success', CASH_COUPON: 'primary', POINTS_EXCHANGE: 'info' } as any)[t] || ''
}

function statusLabel(s: number) {
  return { 1: '启用', 0: '停用', 2: '结束' }[s] || '未知'
}
function statusTag(s: number) {
  return ({ 1: 'success', 0: 'info', 2: 'warning' } as any)[s] || 'info'
}

function formatDate(d: string) {
  if (!d) return ''
  return d.substring(0, 16)
}

function resetFilter() {
  filter.keyword = ''
  filter.promotionType = undefined
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive<any>({
  promotionCode: '',
  promotionName: '',
  promotionType: 'FULL_REDUCE',
  thresholdAmount: 0,
  reduceAmount: 0,
  discountRate: 1.0,
  scopeType: 'ALL',
  stackable: 0,
  quotaTotal: 0,
  perMemberLimit: 0,
  priority: 0,
  description: '',
  status: 1,
  startTime: '2026-01-01 00:00:00',
  endTime: '2099-12-31 23:59:59'
})

function openCreate() {
  dialog.id = undefined
  dialog.visible = true
}

function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, row)
  dialog.visible = true
}

function resetDialog() {
  Object.assign(form, {
    promotionCode: '',
    promotionName: '',
    promotionType: 'FULL_REDUCE',
    thresholdAmount: 0,
    reduceAmount: 0,
    discountRate: 1.0,
    scopeType: 'ALL',
    stackable: 0,
    quotaTotal: 0,
    perMemberLimit: 0,
    priority: 0,
    description: '',
    status: 1,
    startTime: '2026-01-01 00:00:00',
    endTime: '2099-12-31 23:59:59'
  })
}

async function save() {
  if (!form.promotionCode || !form.promotionName) return ElMessage.warning('编码和名称必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (dialog.id) {
      await http.put(`/mkt/promotion/${dialog.id}`, form, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/mkt/promotion', form, { headers })
      ElMessage.success('已创建')
    }
    dialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    dialog.saving = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除活动「${row.promotionName}」？`, '提示', { type: 'warning' })
  await http.delete(`/mkt/promotion/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

async function exportCsv() {
  try {
    const res = await http.get('/mkt/promotion/export', { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '营销活动.csv'
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
