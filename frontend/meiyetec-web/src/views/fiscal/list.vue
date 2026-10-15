<template>
  <div class="page-fiscal-list">
    <el-page-header @back="$router.push('/fiscal')" content="发票列表" />

    <!-- 筛选区 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:160px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value"
                       :label="s.label" :value="s.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="订单ID">
          <el-input v-model.number="filter.orderId" placeholder="可选" clearable style="width:160px"/>
        </el-form-item>
        <el-form-item label="通道">
          <el-select v-model="filter.channel" placeholder="全部" clearable style="width:160px">
            <el-option v-for="c in channels" :key="c.code"
                       :label="c.name + '（' + c.code + '）'" :value="c.code"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="undefined" @click="loadList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-button @click="loadList" :loading="loading">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 状态小卡片 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6" v-for="s in summaryCards" :key="s.key">
        <el-card shadow="hover" class="stat-card"
                 :class="{ active: filter.status === s.value }"
                 @click="onStatClick(s.value)">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value" :style="{ color: s.color }">{{ s.count }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 发票表格 -->
    <el-card class="table-card">
      <el-table :data="list" stripe v-loading="loading" empty-text="暂无发票">
        <el-table-column prop="id" label="ID" width="70"/>
        <el-table-column prop="invoiceNo" label="发票号" min-width="160"/>
        <el-table-column prop="orderId" label="订单ID" width="90"/>
        <el-table-column label="通道" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.channel || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="购买方" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="buyerTaxNo" label="购方税号" min-width="160" show-overflow-tooltip/>
        <el-table-column label="金额(元)" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="100" align="right">
          <template #default="{ row }">{{ formatAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column label="价税合计" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.invoiceStatus)" size="small">
              {{ statusName(row.invoiceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platformInvoiceNo" label="平台发票号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.platformInvoiceNo || row.fiscalNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="开票时间" width="160">
          <template #default="{ row }">{{ formatDate(row.issuedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="viewDetail(row)">详情</el-button>
            <el-button v-if="canVoid(row)" size="small" type="danger" link
                       @click="onVoid(row)">红冲</el-button>
            <el-button v-if="row.pdfUrl" size="small" link type="primary"
                       @click="openPdf(row.pdfUrl)">PDF</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" :title="`发票详情 #${current?.id || ''}`" width="720px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="发票号">{{ current.invoiceNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ current.orderId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="通道">{{ current.channel }}</el-descriptions-item>
        <el-descriptions-item label="发票类型">{{ current.invoiceType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(current.invoiceStatus)" size="small">
            {{ statusName(current.invoiceStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开票时间">{{ formatDate(current.issuedAt) }}</el-descriptions-item>
        <el-descriptions-item label="购买方">{{ current.buyerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="购方税号">{{ current.buyerTaxNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="购方地址">{{ current.buyerAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="购方电话">{{ current.buyerPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="购方邮箱">{{ current.buyerEmail || '-' }}</el-descriptions-item>
        <el-descriptions-item label="购方开户行">{{ current.buyerBank || '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ formatAmount(current.amount) }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ formatAmount(current.taxAmount) }}</el-descriptions-item>
        <el-descriptions-item label="价税合计">{{ formatAmount(current.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="税率">{{ current.taxRate }}</el-descriptions-item>
        <el-descriptions-item label="平台发票号">{{ current.platformInvoiceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="红冲号">{{ current.platformVoidNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="平台请求ID">{{ current.platformRequestId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="网关模式">{{ current.requestMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="PDF URL" :span="2">
          <a v-if="current.pdfUrl" :href="current.pdfUrl" target="_blank">{{ current.pdfUrl }}</a>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2" v-if="current.errorMessage">
          <span style="color:#f56c6c">{{ current.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(current.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(current.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button v-if="current && canVoid(current)" type="danger" @click="onVoid(current!)">红冲此发票</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fiscalApi } from '@/api/fiscal'

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待开票' },
  { value: 'ISSUED',  label: '已开票' },
  { value: 'VOIDED',  label: '已红冲' },
  { value: 'FAILED',  label: '失败' }
]

const STATUS_MAP: Record<string, { name: string; type: 'success'|'warning'|'info'|'danger' }> = {
  PENDING: { name: '待开票', type: 'warning' },
  ISSUED:  { name: '已开票', type: 'success' },
  VOIDED:  { name: '已红冲', type: 'info' },
  FAILED:  { name: '失败',   type: 'danger' },
  SUCCEEDED: { name: '已开票(旧)', type: 'success' },
  REFUNDED:  { name: '已退款(旧)', type: 'info' }
}

const filter = reactive<any>({ status: '', orderId: null, channel: '' })
const list = ref<any[]>([])
const channels = ref<any[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const current = ref<any>(null)

const summary = computed(() => {
  const m = { PENDING: 0, ISSUED: 0, VOIDED: 0, FAILED: 0 }
  for (const r of list.value) {
    if (r.invoiceStatus in m) m[r.invoiceStatus as keyof typeof m]++
  }
  return m
})

const summaryCards = computed(() => [
  { key: 'ALL',     label: '当前列表', value: '',     count: list.value.length, color: '#303133' },
  { key: 'PENDING', label: '待开票',   value: 'PENDING', count: summary.value.PENDING, color: '#e6a23c' },
  { key: 'ISSUED',  label: '已开票',   value: 'ISSUED',  count: summary.value.ISSUED,  color: '#67c23a' },
  { key: 'VOIDED',  label: '已红冲',   value: 'VOIDED',  count: summary.value.VOIDED,  color: '#909399' }
])

function statusName(s: string) { return STATUS_MAP[s]?.name ?? s ?? '-' }
function statusTagType(s: string) { return STATUS_MAP[s]?.type ?? 'info' }
function formatAmount(v: any) {
  if (v == null) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function formatDate(v: any) {
  if (!v) return '-'
  const d = new Date(v)
  if (isNaN(d.getTime())) return v
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function canVoid(row: any) {
  // 后端要求：仅 ISSUED 状态可红冲；必须有 platformInvoiceNo
  return row.invoiceStatus === 'ISSUED' && row.platformInvoiceNo
}
function openPdf(url: string) { window.open(url, '_blank') }

async function loadChannels() {
  const { data } = await fiscalApi.listChannels()
  channels.value = data || []
}

async function loadList() {
  loading.value = true
  try {
    const params: any = {}
    if (filter.status) params.status = filter.status
    if (filter.orderId) params.orderId = filter.orderId
    if (filter.channel) params.channel = filter.channel
    const { data } = await fiscalApi.listInvoices(params)
    list.value = data || []
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.status = ''
  filter.orderId = null
  filter.channel = ''
  loadList()
}

function onStatClick(value: string) {
  filter.status = filter.status === value ? '' : value
  loadList()
}

async function viewDetail(row: any) {
  const { data } = await fiscalApi.getInvoice(row.id)
  current.value = data || row
  detailVisible.value = true
}

async function onVoid(row: any) {
  if (!canVoid(row)) {
    return ElMessage.warning(`当前状态为「${statusName(row.invoiceStatus)}」，不可红冲`)
  }
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `确认对发票 ${row.invoiceNo} 进行红冲？\n红冲后该发票将标记为 VOIDED。`,
      '红冲确认',
      {
        inputPlaceholder: '请输入红冲原因（可选）',
        inputValue: '',
        confirmButtonText: '确认红冲',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await fiscalApi.voidInvoice(row.id, { reason: reason || '用户申请' })
    ElMessage.success('已红冲')
    detailVisible.value = false
    await loadList()
  } catch (e: any) {
    // 用户取消不报错
    if (e === 'cancel' || e?.message?.includes('cancel')) return
    ElMessage.error(e?.message || '红冲失败')
  }
}

onMounted(async () => {
  await loadChannels()
  await loadList()
})
</script>

<style scoped>
.page-fiscal-list { padding: 16px; }
.filter-card { margin-bottom: 12px; }
.stat-row { margin-bottom: 12px; }
.stat-card { cursor: pointer; text-align: center; padding: 12px 0; transition: transform .2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-card.active { box-shadow: 0 0 0 2px #409EFF inset; }
.stat-card .stat-label { color: #909399; font-size: 13px; }
.stat-card .stat-value { font-size: 22px; font-weight: 700; margin-top: 4px; }
.table-card { margin-bottom: 16px; }
</style>