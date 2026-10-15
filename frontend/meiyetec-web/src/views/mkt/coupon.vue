<template>
  <div class="coupon-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">已发放</div>
        <div class="kpi-value">{{ stats.issued || 0 }}</div>
      </div>
      <div class="kpi-item green">
        <div class="kpi-label">已使用</div>
        <div class="kpi-value">{{ stats.used || 0 }}</div>
      </div>
      <div class="kpi-item yellow">
        <div class="kpi-label">未使用</div>
        <div class="kpi-value">{{ stats.unused || 0 }}</div>
      </div>
      <div class="kpi-item red">
        <div class="kpi-label">已过期</div>
        <div class="kpi-value">{{ stats.expired || 0 }}</div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="main-tabs">
      <el-tab-pane label="批次" name="batch">
        <el-card>
          <el-input v-model="batchFilter.keyword" placeholder="批次号 / 名称" style="width: 220px" clearable @keyup.enter="loadBatch" />
          <el-select v-model="batchFilter.status" placeholder="状态" clearable style="width: 140px" @change="loadBatch">
            <el-option label="草稿" :value="1" />
            <el-option label="发放中" :value="2" />
            <el-option label="已结束" :value="3" />
            <el-option label="已取消" :value="4" />
          </el-select>
          <el-button type="primary" @click="loadBatch">查询</el-button>
          <el-button @click="resetBatch">重置</el-button>
          <el-button type="primary" @click="openBatch">+ 新建批次</el-button>
          <el-button type="warning" @click="runSweep">⚡ 过期扫描</el-button>

          <el-table :data="batches" v-loading="loadingBatch" border style="margin-top: 12px">
            <el-table-column prop="batchNo" label="批次号" width="170" />
            <el-table-column prop="couponName" label="名称" width="180" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.couponType === 'CASH' ? 'danger' : row.couponType === 'PERCENT' ? 'warning' : 'info'">
                  {{ cpType(row.couponType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="面值" width="120">
              <template #default="{ row }">
                <span v-if="row.couponType === 'CASH'">¥{{ row.discountAmount }}</span>
                <span v-else-if="row.couponType === 'PERCENT'">{{ row.discountRate }} 折</span>
              </template>
            </el-table-column>
            <el-table-column label="门槛" width="100">
              <template #default="{ row }">¥{{ row.minConsume }}</template>
            </el-table-column>
            <el-table-column label="发放" width="200">
              <template #default="{ row }">
                已发 {{ row.issuedCount }} / 已用 {{ row.usedCount }} / 总 {{ row.totalCount }}
              </template>
            </el-table-column>
            <el-table-column label="有效期" width="200">
              <template #default="{ row }">
                <span v-if="row.fixedStartDate">{{ row.fixedStartDate }} ~ {{ row.fixedEndDate }}</span>
                <span v-else>领取后 {{ row.effectiveDays }} 天</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="batchStatusTag(row.status)" size="small">{{ batchStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" :disabled="row.status !== 1" @click="publishBatch(row)">发布</el-button>
                <el-button link size="small" type="success" :disabled="row.status !== 2" @click="openGrant(row)">发放</el-button>
                <el-button link size="small" type="warning" :disabled="row.status === 3" @click="endBatch(row)">结束</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="券码" name="coupon">
        <el-card>
          <el-input v-model="couponFilter.keyword" placeholder="券码 / 会员" style="width: 220px" clearable @keyup.enter="loadCoupon" />
          <el-select v-model="couponFilter.status" placeholder="状态" clearable style="width: 140px" @change="loadCoupon">
            <el-option label="未使用" :value="1" />
            <el-option label="已使用" :value="2" />
            <el-option label="已过期" :value="3" />
            <el-option label="已作废" :value="4" />
          </el-select>
          <el-button type="primary" @click="loadCoupon">查询</el-button>
          <el-button @click="resetCoupon">重置</el-button>
          <el-button type="success" @click="exportCouponCsv">导出 CSV</el-button>

          <el-table :data="coupons" v-loading="loadingCoupon" border style="margin-top: 12px">
            <el-table-column prop="couponCode" label="券码" width="170" />
            <el-table-column prop="memberId" label="会员ID" width="80" />
            <el-table-column prop="memberName" label="会员" width="120" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }"><el-tag size="small">{{ cpType(row.couponType) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="面值" width="120">
              <template #default="{ row }">
                <span v-if="row.couponType === 'CASH'">¥{{ row.discountAmount }}</span>
                <span v-else-if="row.couponType === 'PERCENT'">{{ row.discountRate }} 折</span>
              </template>
            </el-table-column>
            <el-table-column label="有效期" width="220">
              <template #default="{ row }">{{ row.effectiveDate }} ~ {{ row.expireDate }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="cpStatusTag(row.status)" size="small">{{ cpStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="usedTime" label="使用时间" width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建批次 -->
    <el-dialog v-model="batchDialog.visible" title="新建批次" width="640px" @closed="resetBatchForm">
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="批次号">
          <el-input v-model="batchForm.batchNo" placeholder="自动生成 / 手动" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="batchForm.couponName" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="类型" required>
              <el-select v-model="batchForm.couponType" style="width: 100%">
                <el-option label="现金券" value="CASH" />
                <el-option label="折扣券" value="PERCENT" />
                <el-option label="积分券" value="POINTS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="面额">
              <el-input-number v-model="batchForm.discountAmount" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣">
              <el-input-number v-model="batchForm.discountRate" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="门槛">
              <el-input-number v-model="batchForm.minConsume" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效天数">
              <el-input-number v-model="batchForm.effectiveDays" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="总数量">
              <el-input-number v-model="batchForm.totalCount" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期提醒">
              <el-input-number v-model="batchForm.expireWarningDays" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveBatch" :loading="batchDialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发放 -->
    <el-dialog v-model="grantDialog.visible" :title="`发放券 — ${grantDialog.batch?.couponName || ''}`" width="500px" @closed="resetGrantForm">
      <el-form :model="grantForm" label-width="100px">
        <el-form-item label="会员ID" required>
          <el-input-number v-model="grantForm.memberId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="张数">
          <el-input-number v-model="grantForm.count" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="会员姓名">
          <el-input v-model="grantForm.memberName" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="runGrant" :loading="grantDialog.running">发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const activeTab = ref('batch')
const stats = reactive<any>({})

const batchFilter = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: undefined as number | undefined })
const batches = ref<any[]>([])
const loadingBatch = ref(false)

async function loadBatch() {
  loadingBatch.value = true
  try {
    const r = await http.get('/mkt/coupon/batch/page', { params: batchFilter })
    batches.value = r.data?.records || []
    updateStats()
  } finally {
    loadingBatch.value = false
  }
}

function resetBatch() {
  batchFilter.keyword = ''
  batchFilter.status = undefined
  batchFilter.pageNum = 1
  loadBatch()
}

function cpType(t: string) {
  return { CASH: '现金券', PERCENT: '折扣券', POINTS: '积分券' }[t] || t
}

function batchStatusLabel(s: number) {
  return { 1: '草稿', 2: '发放中', 3: '已结束', 4: '已取消' }[s] || '未知'
}
function batchStatusTag(s: number) {
  return ({ 1: 'info', 2: 'success', 3: 'warning', 4: 'danger' } as any)[s] || 'info'
}

function cpStatusLabel(s: number) {
  return { 1: '未使用', 2: '已使用', 3: '已过期', 4: '已作废' }[s] || '未知'
}
function cpStatusTag(s: number) {
  return ({ 1: 'primary', 2: 'success', 3: 'info', 4: 'danger' } as any)[s] || 'info'
}

const batchDialog = reactive({ visible: false, saving: false })
const batchForm = reactive<any>({
  batchNo: '',
  couponName: '',
  couponType: 'CASH',
  discountAmount: 10,
  discountRate: 1.0,
  minConsume: 0,
  effectiveDays: 30,
  expireWarningDays: 3,
  totalCount: 0
})

function openBatch() {
  batchDialog.visible = true
}

function resetBatchForm() {
  Object.assign(batchForm, {
    batchNo: '', couponName: '', couponType: 'CASH',
    discountAmount: 10, discountRate: 1.0, minConsume: 0,
    effectiveDays: 30, expireWarningDays: 3, totalCount: 0
  })
}

async function saveBatch() {
  if (!batchForm.couponName) return ElMessage.warning('名称必填')
  batchDialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    await http.post('/mkt/coupon/batch', batchForm, { headers })
    ElMessage.success('已创建')
    batchDialog.visible = false
    loadBatch()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    batchDialog.saving = false
  }
}

async function publishBatch(row: any) {
  await ElMessageBox.confirm(`发布批次 ${row.batchNo}？`, '提示', { type: 'warning' })
  await http.post(`/mkt/coupon/batch/${row.id}/publish`)
  ElMessage.success('已发布')
  loadBatch()
}

async function endBatch(row: any) {
  await ElMessageBox.confirm(`结束批次 ${row.batchNo}？`, '提示', { type: 'warning' })
  await http.post(`/mkt/coupon/batch/${row.id}/end`)
  ElMessage.success('已结束')
  loadBatch()
}

const grantDialog = reactive<any>({ visible: false, batch: null, running: false })
const grantForm = reactive({ memberId: undefined as number | undefined, memberName: '', count: 1 })

function openGrant(row: any) {
  grantDialog.batch = row
  grantDialog.visible = true
}

function resetGrantForm() {
  grantForm.memberId = undefined
  grantForm.memberName = ''
  grantForm.count = 1
}

async function runGrant() {
  if (!grantForm.memberId) return ElMessage.warning('会员ID必填')
  grantDialog.running = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    const r = await http.post(`/mkt/coupon/batch/${grantDialog.batch.id}/grant`, {
      members: [{ memberId: grantForm.memberId, memberName: grantForm.memberName, count: grantForm.count }]
    }, { headers })
    ElMessage.success(`已发放 ${r.data.grantedCount} 张`)
    grantDialog.visible = false
    loadBatch()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发放失败')
  } finally {
    grantDialog.running = false
  }
}

async function runSweep() {
  const r = await http.post('/mkt/coupon/expire-sweep')
  ElMessage.success(`已扫描：过期 ${r.data.expiredCount} 张，即将过期 ${r.data.nearExpireCount} 张`)
}

const couponFilter = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: undefined as number | undefined })
const coupons = ref<any[]>([])
const loadingCoupon = ref(false)

async function loadCoupon() {
  loadingCoupon.value = true
  try {
    const r = await http.get('/mkt/coupon/page', { params: couponFilter })
    coupons.value = r.data?.records || []
    updateStats()
  } finally {
    loadingCoupon.value = false
  }
}

function resetCoupon() {
  couponFilter.keyword = ''
  couponFilter.status = undefined
  couponFilter.pageNum = 1
  loadCoupon()
}

function updateStats() {
  stats.issued = batches.value.reduce((sum, b) => sum + (b.issuedCount || 0), 0)
  stats.used = batches.value.reduce((sum, b) => sum + (b.usedCount || 0), 0)
  let unused = 0, expired = 0
  for (const c of coupons.value) {
    if (c.status === 1) unused++
    if (c.status === 3) expired++
  }
  stats.unused = unused
  stats.expired = expired
}

async function exportCouponCsv() {
  try {
    const res = await http.get('/mkt/coupon/export', { params: { status: couponFilter.status }, responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '优惠券.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => loadBatch())
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-item.red { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
</style>
