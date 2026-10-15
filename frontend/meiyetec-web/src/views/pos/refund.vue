<template>
  <div class="refund-list">
    <!-- 顶部统计看板 -->
    <el-card class="mb-3" v-if="stats">
      <template #header>
        <div class="flex-between">
          <span class="fw-bold">POS-008 · 退款审核看板</span>
          <div class="flex-gap">
            <el-tag v-if="stats.slaOverdue > 0" type="danger">SLA 超时 {{ stats.slaOverdue }}</el-tag>
            <el-button link type="primary" @click="refreshAll">刷新</el-button>
            <el-button link @click="showRulesDialog = true">审核规则</el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="12">
        <el-col :span="4">
          <el-statistic title="L1 待审（店长）" :value="stats.l1Pending || 0" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="L2 待审（区域）" :value="stats.l2Pending || 0" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="L3 待审（财务）" :value="stats.l3Pending || 0" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="今日申请" :value="stats.todayApply || 0" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="今日通过" :value="stats.todayApprove || 0" value-style="color: #67c23a" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="今日驳回" :value="stats.todayReject || 0" value-style="color: #f56c6c" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 待我审核（按当前用户级别） -->
    <el-card class="mb-3" v-if="pendingRows.length > 0">
      <template #header>
        <div class="flex-between">
          <span class="fw-bold">
            待我审核（当前级别：{{ levelLabel(currentAuditLevel) }}）
          </span>
          <el-radio-group v-model="pendingLevel" size="small" @change="loadPending">
            <el-radio-button :value="1">L1 店长</el-radio-button>
            <el-radio-button :value="2">L2 区域</el-radio-button>
            <el-radio-button :value="3">L3 财务</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="pendingRows" size="small" stripe>
        <el-table-column prop="refundNo" label="退款单号" width="180" />
        <el-table-column prop="orderNo" label="原订单号" width="180" />
        <el-table-column prop="memberName" label="会员" width="100" />
        <el-table-column prop="refundAmount" label="金额" width="100">
          <template #default="{ row }">¥{{ row.refundAmount }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" />
        <el-table-column label="SLA" width="180">
          <template #default="{ row }">
            <el-tag :type="row.slaExceeded ? 'danger' : 'info'" size="small">
              {{ row.hoursSinceApply }}h {{ row.slaExceeded ? '(超时)' : '' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="180">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="onApprove(row)">通过</el-button>
            <el-button link type="danger" size="small" @click="onReject(row)">驳回</el-button>
            <el-button link type="primary" size="small" @click="onDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 退款单列表 -->
    <el-card>
      <el-form :inline="true" :model="query" @submit.prevent="reload">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 160px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核级别">
          <el-select v-model="query.currentAuditLevel" clearable placeholder="全部" style="width: 140px">
            <el-option label="无需审核" :value="0" />
            <el-option label="L1 店长" :value="1" />
            <el-option label="L2 区域" :value="2" />
            <el-option label="L3 财务" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            @change="onDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt-4">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="refundNo" label="退款单号" width="200" />
        <el-table-column prop="orderNo" label="原订单号" width="200" />
        <el-table-column prop="memberName" label="会员" width="120" />
        <el-table-column prop="refundType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.refundType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="refundAmount" label="退款金额" width="120">
          <template #default="{ row }">¥{{ row.refundAmount }}</template>
        </el-table-column>
        <el-table-column prop="refundStrategy" label="策略" width="160" />
        <el-table-column label="审核级别" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.currentAuditLevel > 0" type="warning" size="small">
              {{ levelLabel(row.currentAuditLevel) }}
            </el-tag>
            <el-tag v-else type="info" size="small">无需</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="180" />
        <el-table-column label="操作" fixed="right" width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="onDetail(row)">详情</el-button>
            <el-button link type="success" v-if="row.status === 'AUDIT'" @click="onApprove(row)">审核通过</el-button>
            <el-button link type="danger" v-if="row.status === 'AUDIT'" @click="onReject(row)">拒绝</el-button>
            <el-button link type="warning" v-if="row.status === 'REJECTED'" @click="onResubmit(row)">重新提交</el-button>
            <el-button link type="primary" v-if="['APPROVED'].includes(row.status)" @click="onProcess(row)">执行</el-button>
            <el-button link type="danger" v-if="['DRAFT','PENDING','AUDIT','APPROVED'].includes(row.status)" @click="onCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="reload"
        @size-change="reload"
        class="mt-3"
      />
    </el-card>

    <!-- 退款单详情 + 审核时间线 -->
    <el-dialog v-model="detailVisible" title="退款单详情" width="780px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="退款单号">{{ current.refundNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(current.status)">{{ statusLabel(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="原订单">#{{ current.orderId }} / {{ current.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">¥{{ current.refundAmount }}</el-descriptions-item>
        <el-descriptions-item label="已退金额">¥{{ current.refundedAmount }}</el-descriptions-item>
        <el-descriptions-item label="剩余金额">¥{{ current.remainingAmount }}</el-descriptions-item>
        <el-descriptions-item label="策略">{{ current.refundStrategy }}</el-descriptions-item>
        <el-descriptions-item label="审核级别">
          <el-tag v-if="current.currentAuditLevel > 0" type="warning">
            {{ levelLabel(current.currentAuditLevel) }}
          </el-tag>
          <span v-else>无需审核</span>
        </el-descriptions-item>
        <el-descriptions-item label="审核人">{{ current.auditorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
        <el-descriptions-item label="原因" :span="2">{{ current.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="拒绝原因" :span="2" v-if="current.rejectReason">
          <el-text type="danger">{{ current.rejectReason }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="幂等键" :span="2"><span class="mono">{{ current.idempotencyKey }}</span></el-descriptions-item>
        <el-descriptions-item label="traceId" :span="2"><span class="mono">{{ current.traceId }}</span></el-descriptions-item>
      </el-descriptions>

      <el-divider>POS-008 · 审核时间线</el-divider>
      <el-timeline v-if="timeline.length > 0">
        <el-timeline-item
          v-for="(item, idx) in timeline"
          :key="idx"
          :timestamp="item.createTime"
          :type="actionType(item.action)"
        >
          <el-tag size="small" :type="actionTag(item.action)">{{ item.action }}</el-tag>
          <span class="ml-2">{{ item.actorName || 'system' }}</span>
          <span class="ml-2" v-if="item.auditLevel > 0">[{{ item.auditLevelName }}]</span>
          <div class="mt-1 muted">{{ item.remark }}</div>
          <div class="muted" v-if="item.beforeStatus || item.afterStatus">
            {{ item.beforeStatus || '-' }} → {{ item.afterStatus || '-' }}
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无审核流水" :image-size="60" />

      <el-divider>退款明细</el-divider>
      <el-table v-if="current?.items?.length" :data="current.items" size="small">
        <el-table-column prop="paymentMethod" label="支付方式" />
        <el-table-column prop="paymentAmount" label="原支付金额" />
        <el-table-column prop="refundAmount" label="退款金额" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="errorMsg" label="错误" />
      </el-table>

      <el-divider>逆向补偿</el-divider>
      <el-table v-if="current?.reverseLogs?.length" :data="current.reverseLogs" size="small">
        <el-table-column prop="reverseType" label="类型" />
        <el-table-column prop="refId" label="关联ID" />
        <el-table-column prop="createdAt" label="时间" />
      </el-table>
    </el-dialog>

    <!-- 审核规则配置 -->
    <el-dialog v-model="showRulesDialog" title="退款审核规则" width="720px">
      <el-table :data="ruleRows" v-loading="rulesLoading" border>
        <el-table-column prop="auditLevel" label="级别" width="80">
          <template #default="{ row }">L{{ row.auditLevel }}</template>
        </el-table-column>
        <el-table-column prop="auditLevelName" label="角色" width="120" />
        <el-table-column label="金额区间" width="180">
          <template #default="{ row }">¥{{ row.minAmount }} ~ ¥{{ row.maxAmount }}</template>
        </el-table-column>
        <el-table-column label="必审" width="80">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'warning' : 'info'" size="small">
              {{ row.required ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="自动通过" width="100">
          <template #default="{ row }">
            <el-tag :type="row.autoApprove ? 'success' : 'info'" size="small">
              {{ row.autoApprove ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="editRule(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="muted mt-2">
        规则按金额自动匹配：≤5000 自动通过 / 5000-30000 区域经理 / >30000 财务总监。可针对租户独立配置。
      </div>
    </el-dialog>

    <!-- 编辑规则 -->
    <el-dialog v-model="ruleEditVisible" title="编辑审核规则" width="500px">
      <el-form :model="ruleForm" label-width="100px" v-if="ruleForm.auditLevel">
        <el-form-item label="级别">
          <el-tag>L{{ ruleForm.auditLevel }} - {{ ruleForm.auditLevelName }}</el-tag>
        </el-form-item>
        <el-form-item label="最小金额">
          <el-input-number v-model="ruleForm.minAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大金额">
          <el-input-number v-model="ruleForm.maxAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="是否必审">
          <el-switch v-model="ruleForm.required" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否自动通过">
          <el-switch v-model="ruleForm.autoApprove" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleEditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重新提交（驳回后） -->
    <el-dialog v-model="resubmitVisible" title="重新提交退款申请" width="500px">
      <el-form :model="resubmitForm" label-width="100px" v-if="resubmitForm.id">
        <el-form-item label="退款单号">{{ resubmitForm.refundNo }}</el-form-item>
        <el-form-item label="原金额">¥{{ resubmitForm.refundAmount }}</el-form-item>
        <el-form-item label="可退余额">¥{{ resubmitForm.remainingAmount }}</el-form-item>
        <el-form-item label="新退款金额">
          <el-input-number
            v-model="resubmitForm.newAmount"
            :min="0"
            :max="Number(resubmitForm.remainingAmount)"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="新原因">
          <el-input v-model="resubmitForm.newReason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="提示" v-if="resubmitHint">
          <el-alert :title="resubmitHint" type="info" :closable="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resubmitVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmResubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { refundApi } from '@/api/pos'

// ===== 查询条件 =====
const query = reactive<any>({
  pageNum: 1,
  pageSize: 20,
  status: '',
  currentAuditLevel: '',
  dateFrom: '',
  dateTo: ''
})
const dateRange = ref<[string, string] | null>(null)
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const current = ref<any>(null)
const timeline = ref<any[]>([])

// ===== 看板 =====
const stats = ref<any>(null)
const pendingRows = ref<any[]>([])
const pendingLevel = ref(1)
const currentAuditLevel = ref(1)

// ===== 规则 =====
const showRulesDialog = ref(false)
const ruleRows = ref<any[]>([])
const rulesLoading = ref(false)
const ruleEditVisible = ref(false)
const ruleForm = ref<any>({
  auditLevel: null,
  auditLevelName: '',
  minAmount: 0,
  maxAmount: 999999,
  required: 1,
  autoApprove: 0,
  enabled: 1,
  remark: ''
})

// ===== 重新提交 =====
const resubmitVisible = ref(false)
const resubmitForm = ref<any>({
  id: null,
  refundNo: '',
  refundAmount: 0,
  remainingAmount: 0,
  newAmount: 0,
  newReason: ''
})
const resubmitHint = computed(() => {
  if (!resubmitForm.value.newAmount) return ''
  const amt = Number(resubmitForm.value.newAmount)
  if (amt <= 5000) return '新金额 ≤5000，可能自动通过（无审核）'
  if (amt <= 30000) return '新金额 5000-30000，将进入 L2（区域经理）审核'
  return '新金额 >30000，将进入 L3（财务总监）审核'
})

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待处理' },
  { value: 'AUDIT', label: '待审核' },
  { value: 'APPROVED', label: '已审核' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' }
]

const statusLabel = (s: string) => statusOptions.find(o => o.value === s)?.label || s
const statusTag = (s: string) => {
  switch (s) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'AUDIT': return 'warning'
    case 'PROCESSING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'danger'
    case 'CANCELLED': return 'info'
    default: return 'info'
  }
}

const levelLabel = (lv: number) => {
  return lv === 0 ? '无需' : lv === 1 ? 'L1 店长' : lv === 2 ? 'L2 区域' : 'L3 财务'
}

const actionTag = (a: string) => {
  if (a === 'REJECT') return 'danger'
  if (a.includes('APPROVE') || a === 'AUTO_APPROVE' || a === 'AUTO_NEXT' || a === 'SUCCESS') return 'success'
  if (a === 'SUBMIT' || a === 'RESUBMIT') return 'warning'
  if (a === 'FAILED' || a === 'CANCEL') return 'info'
  return 'primary'
}
const actionType = (a: string) => {
  if (a === 'REJECT') return 'danger'
  if (a === 'SUCCESS') return 'success'
  if (a === 'FAILED') return 'danger'
  return 'primary'
}

function onDateChange(v: [string, string] | null) {
  if (v) {
    query.dateFrom = v[0]
    query.dateTo = v[1]
  } else {
    query.dateFrom = ''
    query.dateTo = ''
  }
}

async function reload() {
  loading.value = true
  try {
    const r = await refundApi.page(query)
    rows.value = r.data.records || []
    total.value = r.data.total || 0
  } finally {
    loading.value = false
  }
}

function reset() {
  query.status = ''
  query.currentAuditLevel = ''
  query.dateFrom = ''
  query.dateTo = ''
  dateRange.value = null
  query.pageNum = 1
  reload()
}

async function loadStats() {
  try {
    const r = await refundApi.stats()
    stats.value = r.data
  } catch {
    stats.value = null
  }
}

async function loadPending() {
  try {
    const r = await refundApi.pending(pendingLevel.value)
    pendingRows.value = r.data || []
    currentAuditLevel.value = pendingLevel.value
  } catch {
    pendingRows.value = []
  }
}

async function loadRules() {
  rulesLoading.value = true
  try {
    const r = await refundApi.rules()
    ruleRows.value = r.data || []
  } finally {
    rulesLoading.value = false
  }
}

function editRule(row: any) {
  ruleForm.value = {
    auditLevel: row.auditLevel,
    auditLevelName: row.auditLevelName,
    minAmount: row.minAmount,
    maxAmount: row.maxAmount,
    required: row.required,
    autoApprove: row.autoApprove,
    enabled: row.enabled,
    remark: row.remark || ''
  }
  ruleEditVisible.value = true
}

async function saveRule() {
  await refundApi.upsertRule(ruleForm.value)
  ElMessage.success('规则已保存')
  ruleEditVisible.value = false
  await loadRules()
}

function refreshAll() {
  reload()
  loadStats()
  loadPending()
}

async function onDetail(row: any) {
  const r = await refundApi.detail(row.id)
  current.value = r.data
  timeline.value = []
  detailVisible.value = true
  // 加载时间线
  try {
    const tl = await refundApi.timeline(row.id)
    timeline.value = tl.data || []
  } catch {
    timeline.value = []
  }
}

async function onApprove(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认审核通过 ${row.refundNo}（当前级别 ${levelLabel(row.currentAuditLevel || 1)}）？`,
      '审核',
      { type: 'success' }
    )
  } catch {
    return
  }
  await refundApi.approve(row.id, { actorName: '当前用户', actorRole: 'auditor' })
  ElMessage.success('已审核通过')
  refreshAll()
}

async function onReject(row: any) {
  let reason = ''
  try {
    const res: any = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      inputPlaceholder: '例如：客户已消费完毕',
      inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '原因不能为空')
    })
    reason = res.value
  } catch {
    return
  }
  await refundApi.reject(row.id, { rejectReason: reason, actorName: '当前用户', actorRole: 'auditor' })
  ElMessage.success('已驳回')
  refreshAll()
}

async function onResubmit(row: any) {
  resubmitForm.value = {
    id: row.id,
    refundNo: row.refundNo,
    refundAmount: row.refundAmount,
    remainingAmount: row.remainingAmount,
    newAmount: Number(row.refundAmount),
    newReason: ''
  }
  resubmitVisible.value = true
}

async function confirmResubmit() {
  await refundApi.resubmit(resubmitForm.value.id, {
    refundAmount: resubmitForm.value.newAmount,
    reason: resubmitForm.value.newReason,
    operatorName: '当前用户'
  })
  ElMessage.success('已重新提交')
  resubmitVisible.value = false
  refreshAll()
}

async function onProcess(row: any) {
  try {
    await ElMessageBox.confirm(`确认执行退款 ${row.refundNo} ？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await refundApi.process(row.id)
  ElMessage.success('已处理')
  reload()
}

async function onCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认取消退款 ${row.refundNo} ？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await refundApi.cancel(row.id, { reason: '收银员取消' })
  ElMessage.success('已取消')
  reload()
}

watch(showRulesDialog, (v) => {
  if (v) loadRules()
})

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.mt-4 { margin-top: 16px; }
.mt-3 { margin-top: 12px; }
.mt-2 { margin-top: 8px; }
.mt-1 { margin-top: 4px; }
.mb-3 { margin-bottom: 12px; }
.ml-2 { margin-left: 8px; }
.mono { font-family: monospace; color: #67c23a; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
.flex-gap { display: flex; gap: 8px; align-items: center; }
.fw-bold { font-weight: 600; }
.muted { color: #909399; font-size: 12px; }
</style>