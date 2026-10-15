<template>
  <div class="cross-record-page">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card stat-blue" shadow="hover">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">核销总笔数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stat-orange" shadow="hover">
          <div class="stat-value">{{ stats.today }}</div>
          <div class="stat-label">今日核销</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stat-green" shadow="hover">
          <div class="stat-value">¥{{ formatAmount(stats.feeSum) }}</div>
          <div class="stat-label">累计手续费</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stat-purple" shadow="hover">
          <div class="stat-value">¥{{ formatAmount(stats.netSum) }}</div>
          <div class="stat-label">累计净收</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="会员名 / 手机号 / 订单号" style="width: 220px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.redemptionType" placeholder="类型" clearable style="width: 140px" @change="loadData">
        <el-option label="套餐" value="PACKAGE" />
        <el-option label="项目" value="PROJECT" />
        <el-option label="储值" value="BALANCE" />
        <el-option label="积分" value="POINTS" />
        <el-option label="优惠券" value="COUPON" />
        <el-option label="折扣" value="DISCOUNT" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="已核销" :value="1" />
        <el-option label="已撤销" :value="2" />
      </el-select>
      <el-select v-model="filter.sourceStoreId" placeholder="归属门店" clearable filterable style="width: 160px" @change="loadData">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-select v-model="filter.targetStoreId" placeholder="核销门店" clearable filterable style="width: 160px" @change="loadData">
        <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="→"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 240px"
        @change="onDateChange"
      />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button @click="exportCsv" :loading="exporting">📤 导出 CSV</el-button>
      <el-button type="warning" @click="openLookup">📱 跨店识别</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <template #empty>
          <div class="empty-state">
            <div class="empty-icon">📭</div>
            <div class="empty-title">还没有跨店核销记录</div>
            <div class="empty-desc">
              会员在归属店购买、在其他门店核销后，记录会出现在这里。<br />
              可先用「跨店识别」验证会员是否支持跨店。
            </div>
            <div class="empty-actions">
              <el-button type="primary" @click="openLookup">📱 去识别会员</el-button>
              <el-button @click="loadData">🔄 刷新</el-button>
            </div>
            <div class="empty-link">
              还没有跨店规则？<el-link type="primary" underline="never" @click="$router.push('/crm/cross-rule')">去配置跨店规则 →</el-link>
            </div>
          </div>
        </template>
        <el-table-column prop="orderNo" label="订单号" width="170" />
        <el-table-column prop="memberName" label="会员" width="100" />
        <el-table-column prop="memberPhone" label="手机" width="130" />
        <el-table-column label="核销类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.redemptionType)">{{ typeLabel(row.redemptionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceName" label="项目/套餐" min-width="180" />
        <el-table-column label="归属门店 → 核销门店" width="240">
          <template #default="{ row }">
            <el-tag size="small">{{ row.sourceStoreName || '—' }}</el-tag>
            <span style="margin: 0 6px">→</span>
            <el-tag size="small" type="primary">{{ row.targetStoreName || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原金额" width="100" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.originalAmount) }}</template>
        </el-table-column>
        <el-table-column label="手续费" width="80" align="right">
          <template #default="{ row }">¥{{ formatAmount(displayCrossFee(row)) }}</template>
        </el-table-column>
        <el-table-column label="实收" width="100" align="right">
          <template #default="{ row }">¥{{ formatAmount(displayNetAmount(row)) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已核销' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" :disabled="!row.orderId" @click="viewOrder(row)">查看来源订单</el-button>
            <el-button link size="small" type="danger" :disabled="row.status !== 1" @click="revoke(row)">撤销</el-button>
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

    <!-- 跨店识别弹窗 -->
    <el-dialog v-model="lookupDialog.visible" title="跨店会员识别" width="640px" @closed="resetLookup">
      <el-form label-width="80px">
        <el-form-item label="手机号">
          <el-input v-model="lookupDialog.phone" placeholder="会员手机号" clearable>
            <template #append>
              <el-button @click="doLookup" :loading="lookupDialog.loading">查询</el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>

      <div v-if="lookupDialog.member">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="会员">{{ lookupDialog.member.name }}</el-descriptions-item>
          <el-descriptions-item label="会员号">{{ lookupDialog.member.memberNo }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ lookupDialog.member.phone }}</el-descriptions-item>
          <el-descriptions-item label="积分/储值">{{ lookupDialog.member.points }} 分 / ¥{{ formatAmount(lookupDialog.member.balance) }}</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="lookupDialog.isCross" :title="`当前为跨店识别，会员归属门店 ≠ 当前门店`" type="warning" show-icon style="margin: 12px 0" />

        <div v-if="lookupDialog.matchedRule" style="margin-top: 12px">
          <h4 style="margin: 0 0 8px">跨店规则预览</h4>
          <el-table :data="rulePreviewRows" border size="small">
            <el-table-column prop="item" label="项" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag :type="row.allowed ? 'success' : 'danger'" size="small">
                  {{ row.allowed ? '允许' : '禁止' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="extra" label="备注" />
          </el-table>
        </div>

        <div v-if="lookupDialog.relatedStores?.length" style="margin-top: 12px">
          <h4 style="margin: 0 0 8px">会员关联门店</h4>
          <el-table :data="lookupDialog.relatedStores" border size="small">
            <el-table-column prop="storeName" label="门店" />
            <el-table-column prop="brandName" label="品牌" />
            <el-table-column prop="relationType" label="关系">
              <template #default="{ row }">
                <el-tag size="small" :type="row.relationType === 'ORIGIN' ? 'warning' : 'primary'">
                  {{ row.relationType === 'ORIGIN' ? '归属' : '关联' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="visitCount" label="访问次数" align="center" />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="lookupDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', redemptionType: undefined as string | undefined,
  status: undefined as number | undefined,
  sourceStoreId: undefined as number | undefined,
  targetStoreId: undefined as number | undefined,
  dateFrom: '' as string, dateTo: '' as string
})
const dateRange = ref<[string, string] | null>(null)
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const stores = ref<any[]>([])
const stats = reactive<any>({ total: 0, today: 0, feeSum: 0, netSum: 0 })

function onDateChange(v: [string, string] | null) {
  if (v && v.length === 2) {
    filter.dateFrom = v[0]
    filter.dateTo = v[1]
  } else {
    filter.dateFrom = ''
    filter.dateTo = ''
  }
  filter.pageNum = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const r: any = await http.get('/crm/cross/redemption/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const r: any = await http.get('/crm/cross/redemption/stats', {
      params: { dateFrom: filter.dateFrom || undefined, dateTo: filter.dateTo || undefined }
    })
    Object.assign(stats, r.data || {})
  } catch { /* 静默 */ }
}

async function refresh() {
  await Promise.all([loadData(), loadStats()])
}

async function loadStores() {
  try {
    const r: any = await http.get('/store/list')
    stores.value = r.data || []
  } catch { /* 静默 */ }
}

function typeLabel(t: string) {
  return ({ PACKAGE: '套餐', PROJECT: '项目', BALANCE: '储值', POINTS: '积分', COUPON: '优惠券', DISCOUNT: '折扣' } as any)[t] || t
}
function typeTag(t: string) {
  return ({ PACKAGE: 'primary', PROJECT: 'warning', BALANCE: 'success', POINTS: 'info', COUPON: 'danger', DISCOUNT: 'success' } as any)[t] || ''
}

function formatAmount(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

// 后端历史脏数据兜底：crossFee=0 时按 "原金额×默认5%" 估算（规则已配置则按规则）
const DEFAULT_FALLBACK_FEE_RATE = 0.05
function displayCrossFee(row: any) {
  const fee = Number(row?.crossFee)
  if (!isNaN(fee) && fee > 0) return fee
  const amt = Number(row?.originalAmount) || 0
  if (amt <= 0) return 0
  const rule = row?.ruleSnapshot || row?.matchedRule
  const ruleRate = Number(rule?.crossFeeRate)
  const rate = !isNaN(ruleRate) && ruleRate > 0 ? ruleRate : DEFAULT_FALLBACK_FEE_RATE
  return +(amt * rate).toFixed(2)
}
function displayNetAmount(row: any) {
  const net = Number(row?.netAmount)
  if (!isNaN(net) && net > 0) return net
  const amt = Number(row?.originalAmount) || 0
  const fee = displayCrossFee(row)
  return Math.max(0, +(amt - fee).toFixed(2))
}

function resetFilter() {
  Object.assign(filter, {
    pageNum: 1, pageSize: 20,
    keyword: '', redemptionType: undefined, status: undefined,
    sourceStoreId: undefined, targetStoreId: undefined,
    dateFrom: '', dateTo: ''
  })
  dateRange.value = null
  refresh()
}

async function revoke(row: any) {
  await ElMessageBox.confirm(`撤销跨店核销记录 #${row.id}？`, '提示', { type: 'warning' })
  await http.post(`/crm/cross/redemption/${row.id}/revoke`)
  ElMessage.success('已撤销')
  refresh()
}

function viewOrder(row: any) {
  if (!row.orderId) return ElMessage.warning('来源订单 id 缺失')
  // 尝试按 orderNo 查询 POS 订单详情
  const target = `/pos/order?orderNo=${encodeURIComponent(row.orderNo || '')}`
  $router.push(target)
}

// CSV 导出：拉当前筛选条件的全量数据，本地拼 CSV
async function exportCsv() {
  if (rows.value.length === 0) {
    return ElMessage.warning('当前列表无数据')
  }
  exporting.value = true
  try {
    // 拉到当前条件下的所有记录（pageSize=10000 兜底）
    const params = { ...filter, pageNum: 1, pageSize: 10000 }
    delete (params as any).pageNum
    ;(params as any).pageNum = 1
    const r: any = await http.get('/crm/cross/redemption/page', { params })
    const list: any[] = r.data?.records || []
    if (list.length === 0) return ElMessage.warning('当前筛选条件下无数据可导出')

    const headers = ['订单号', '会员', '手机', '类型', '项目/套餐', '归属门店', '核销门店', '原金额', '手续费', '实收', '状态', '操作人', '时间', '备注']
    const lines = [headers.join(',')]
    for (const row of list) {
      const cells = [
        row.orderNo || '',
        row.memberName || '',
        row.memberPhone || '',
        typeLabel(row.redemptionType),
        row.sourceName || '',
        row.sourceStoreName || '',
        row.targetStoreName || '',
        formatAmount(row.originalAmount),
        formatAmount(displayCrossFee(row)),
        formatAmount(displayNetAmount(row)),
        row.status === 1 ? '已核销' : row.status === 2 ? '已撤销' : '',
        row.operatorName || '',
        row.createTime || '',
        row.remark || ''
      ].map(csvCell)
      lines.push(cells.join(','))
    }
    const csv = '\uFEFF' + lines.join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `跨店核销_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${list.length} 条`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function csvCell(v: any) {
  const s = v == null ? '' : String(v)
  if (/[",\r\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

const lookupDialog = reactive<any>({
  visible: false, phone: '', loading: false, member: null,
  isCross: false, matchedRule: null, relatedStores: null
})

const rulePreviewRows = computed(() => {
  const r = lookupDialog.matchedRule
  if (!r) return []
  return [
    { item: '套餐/项目核销', allowed: !!r.packageAllowed, extra: r.packageAllowed ? `模式 ${r.packageCrossMode || 'BRAND'}` : '' },
    { item: '积分共享', allowed: !!r.pointsAllowed, extra: '' },
    { item: '储值使用', allowed: !!r.balanceAllowed, extra: r.balanceAllowed ? `模式 ${r.balanceCrossMode || 'BRAND'}` : '' },
    { item: '折扣共享', allowed: !!r.discountAllowed, extra: '' },
    { item: '优惠券', allowed: !!r.couponAllowed, extra: '' },
    { item: '跨店手续费', allowed: true, extra: r.crossFeeType === 'NONE' ? '无' : `${r.crossFeeType} ${r.crossFeeRate}` },
    { item: '单笔限额', allowed: true, extra: r.crossMaxAmount > 0 ? `¥${r.crossMaxAmount}` : '不限' }
  ]
})

function openLookup() { lookupDialog.visible = true }

function resetLookup() {
  lookupDialog.phone = ''
  lookupDialog.member = null
  lookupDialog.isCross = false
  lookupDialog.matchedRule = null
  lookupDialog.relatedStores = null
}

async function doLookup() {
  if (!lookupDialog.phone) return ElMessage.warning('请输入手机号')
  lookupDialog.loading = true
  try {
    const r = await http.get('/crm/cross/member/lookup', { params: { phone: lookupDialog.phone } })
    const data = r.data
    lookupDialog.member = data.member
    lookupDialog.isCross = data.isCross
    lookupDialog.matchedRule = data.rulePreview
    lookupDialog.relatedStores = data.relatedStores
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '查询失败')
  } finally {
    lookupDialog.loading = false
  }
}

onMounted(() => {
  refresh()
  loadStores()
})
</script>

<style scoped>
.stats-row { margin-bottom: 12px; }
.stat-card { text-align: center; padding: 14px 0; }
.stat-value { font-size: 24px; font-weight: 600; color: #303133; }
.stat-label { color: #909399; font-size: 12px; margin-top: 4px; }
.stat-card.stat-blue .stat-value { color: #409eff; }
.stat-card.stat-orange .stat-value { color: #e6a23c; }
.stat-card.stat-green .stat-value { color: #67c23a; }
.stat-card.stat-purple .stat-value { color: #9c27b0; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input, .search-bar .el-select, .search-bar .el-date-editor { margin-right: 8px; }
.table-card { overflow-x: auto; }
.table-card :deep(.el-table) { min-width: 1200px; }
.pagination { margin-top: 12px; text-align: right; }
.empty-state { padding: 36px 16px 28px; text-align: center; color: #606266; }
.empty-state .empty-icon { font-size: 56px; line-height: 1; margin-bottom: 12px; opacity: 0.85; }
.empty-state .empty-title { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 6px; }
.empty-state .empty-desc { font-size: 13px; color: #909399; line-height: 1.7; margin-bottom: 16px; }
.empty-state .empty-actions { display: inline-flex; gap: 8px; }
.empty-state .empty-link { margin-top: 10px; font-size: 13px; color: #909399; }
</style>