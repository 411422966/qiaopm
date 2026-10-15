<template>
  <div class="share-page">

    <!-- 顶部统计 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.totalOrders }}</div>
          <div class="stat-label">已开启共享的订单</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-blue">
          <div class="stat-value">{{ stats.totalShares }}</div>
          <div class="stat-label">共享成员总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-green">
          <div class="stat-value">{{ stats.activeShares }}</div>
          <div class="stat-label">正常可用</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-orange">
          <div class="stat-value">{{ stats.exhaustedShares }}</div>
          <div class="stat-label">已用尽</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">多人共享套餐管理</span>
          <el-space>
            <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadList">
              <el-option label="正常" :value="1" />
              <el-option label="暂停" :value="2" />
              <el-option label="已用尽" :value="3" />
              <el-option label="已撤销" :value="4" />
            </el-select>
            <el-input v-model="filter.keyword" placeholder="订单号 / 会员姓名 / 手机" clearable style="width: 200px" @keyup.enter="loadList" />
            <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
            <el-button :icon="Refresh" @click="loadList">刷新</el-button>
            <el-button type="primary" @click="openEnable">+ 开启共享</el-button>
          </el-space>
        </div>
      </template>

      <!-- 批量操作 -->
      <div class="batch-bar" v-if="selected.length">
        <el-tag effect="dark" type="info">已选 {{ selected.length }} 条</el-tag>
        <el-button size="small" type="warning" @click="batchPause">批量暂停</el-button>
        <el-button size="small" type="success" @click="batchResume">批量恢复</el-button>
        <el-button size="small" type="danger" @click="batchRevoke">批量撤销</el-button>
        <el-button size="small" @click="selected = []">取消</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe
        @selection-change="(v: any[]) => selected = v"
        :row-key="(r: any) => r.id">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="packageOrderId" label="订单号" width="110" />
        <el-table-column label="套餐名称" min-width="160">
          <template #default="{ row }">
            <div class="pkg-name">{{ row.packageName || '—' }}</div>
            <div class="pkg-code muted">{{ row.packageCode || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="持有人" width="120">
          <template #default="{ row }">
            <div>{{ row.ownerName || '—' }}</div>
            <div class="muted">{{ row.ownerPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="共享成员" width="160">
          <template #default="{ row }">
            <div>{{ row.memberName || '—' }}</div>
            <div class="muted">{{ row.memberPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="已用/上限" width="130" align="center">
          <template #default="{ row }">
            <div class="quota-bar">
              <el-progress :percentage="quotaPct(row)" :stroke-width="8"
                :color="quotaColor(row)" :status="quotaStatus(row)"
                :format="(p: number) => `${row.useTimes || 0}/${row.maxTimes == null ? '∞' : row.maxTimes}`" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="邀请时间" width="160">
          <template #default="{ row }">{{ row.inviteAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="首次使用" width="160">
          <template #default="{ row }">{{ row.firstUsedAt || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="shareStatusTag(row.status)" size="small">{{ shareStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="adjustQuota(row)">调整配额</el-button>
            <el-button link size="small" :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)">
              {{ row.status === 1 ? '暂停' : '恢复' }}
            </el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 4" @click="revoke(row)">撤销</el-button>
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
          @current-change="loadList"
          @size-change="loadList"
        />
      </div>
    </el-card>

    <!-- 开启共享弹窗 -->
    <el-dialog v-model="enableDialog.visible" title="开启共享" width="560px">
      <el-form :model="enableForm" label-width="130px">
        <el-form-item label="套餐订单 ID" required>
          <el-input-number v-model="enableForm.packageOrderId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最多共享人数">
          <el-input-number v-model="enableForm.maxMembers" :min="1" :max="20" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每人配额上限">
          <el-input-number v-model="enableForm.defaultQuota" :min="1" style="width: 100%" placeholder="留空=不限制" />
        </el-form-item>
        <el-divider content-position="left">首批邀请（可选）</el-divider>
        <el-form-item label="邀请人手机">
          <el-input v-model="enableForm.memberPhone" placeholder="被邀请人手机号" />
        </el-form-item>
        <el-form-item label="邀请人姓名">
          <el-input v-model="enableForm.memberName" placeholder="被邀请人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enableDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="doEnable" :loading="enableDialog.loading">开启共享</el-button>
      </template>
    </el-dialog>

    <!-- 调整配额弹窗 -->
    <el-dialog v-model="quotaDialog.visible" title="调整配额" width="440px">
      <el-form label-width="120px" v-if="quotaDialog.row">
        <el-form-item label="共享成员">
          <span>{{ quotaDialog.row.memberName }}（{{ quotaDialog.row.memberPhone }}）</span>
        </el-form-item>
        <el-form-item label="原配额">
          <span>{{ quotaDialog.row.maxTimes == null ? '无限制' : quotaDialog.row.maxTimes + ' 次' }}</span>
        </el-form-item>
        <el-form-item label="新配额" required>
          <el-input-number v-model="quotaDialog.newQuota" :min="0" style="width: 100%"
            placeholder="0 = 不限制" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="doAdjustQuota" :loading="quotaDialog.loading">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const stats = reactive({ totalOrders: 0, totalShares: 0, activeShares: 0, exhaustedShares: 0 })

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', status: undefined as number | undefined
})
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<any[]>([])

// 统计
async function loadStats() {
  try {
    const res: any = await import('@/api/package-share').then(m => m.packageShareApi.stats())
    Object.assign(stats, res.data || {})
  } catch { /* 静默 */ }
}

// 列表
async function loadList() {
  loading.value = true
  try {
    const params: any = { ...filter }
    if (!params.status) delete params.status
    const res: any = await import('@/api/package-share').then(m => m.packageShareApi.page(params))
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}

// 配额辅助
function quotaPct(row: any) {
  if (row.maxTimes == null) return 0
  return Math.min(100, Math.round(((row.useTimes || 0) / row.maxTimes) * 100))
}
function quotaColor(row: any) {
  const p = quotaPct(row)
  if (p >= 100) return '#f56c6c'
  if (p >= 80) return '#e6a23c'
  return '#67c23a'
}
function quotaStatus(row: any) {
  return quotaPct(row) >= 100 ? 'exception' : ''
}

// 状态
function shareStatusLabel(s?: number) {
  return ({ 1: '正常', 2: '暂停', 3: '已用尽', 4: '已撤销' } as any)[s || 0] || '—'
}
function shareStatusTag(s?: number) {
  return ({ 1: 'success', 2: 'warning', 3: 'danger', 4: 'info' } as any)[s || 0] || 'info'
}

// 开启共享
const enableDialog = reactive({ visible: false, loading: false })
const enableForm = reactive({ packageOrderId: null as number | null, maxMembers: 5, defaultQuota: null as number | null, memberPhone: '', memberName: '' })
function openEnable() {
  Object.assign(enableForm, { packageOrderId: null, maxMembers: 5, defaultQuota: null, memberPhone: '', memberName: '' })
  enableDialog.visible = true
}
async function doEnable() {
  if (!enableForm.packageOrderId) return ElMessage.warning('订单ID必填')
  enableDialog.loading = true
  try {
    const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
    await api.enableShare(enableForm.packageOrderId, enableForm.maxMembers, {
      defaultQuota: enableForm.defaultQuota,
      memberPhone: enableForm.memberPhone,
      memberName: enableForm.memberName
    })
    ElMessage.success('已开启共享')
    enableDialog.visible = false
    await Promise.all([loadList(), loadStats()])
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '开启失败')
  } finally {
    enableDialog.loading = false
  }
}

// 调整配额
const quotaDialog = reactive({ visible: false, row: null as any, newQuota: 0, loading: false })
function adjustQuota(row: any) {
  quotaDialog.row = row
  quotaDialog.newQuota = row.maxTimes == null ? 0 : row.maxTimes
  quotaDialog.visible = true
}
async function doAdjustQuota() {
  try {
    const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
    await api.adjustQuota(quotaDialog.row.packageOrderId, quotaDialog.row.memberId, quotaDialog.newQuota || null)
    ElMessage.success('配额已调整')
    quotaDialog.visible = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '调整失败') }
}

// 暂停/恢复
async function toggleStatus(row: any) {
  const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
  if (row.status === 1) {
    await api.pause(row.packageOrderId, row.memberId)
    ElMessage.success('已暂停')
  } else {
    await api.resume(row.packageOrderId, row.memberId)
    ElMessage.success('已恢复')
  }
  loadList()
}

// 撤销
async function revoke(row: any) {
  try {
    await ElMessageBox.confirm(`确认撤销「${row.memberName}」的共享资格？`, '撤销确认', { type: 'warning' })
    const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
    await api.revoke(row.packageOrderId, row.memberId)
    ElMessage.success('已撤销')
    await Promise.all([loadList(), loadStats()])
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.response?.data?.message || '撤销失败')
  }
}

// 批量暂停/恢复/撤销
async function batchPause() {
  if (!selected.value.length) return
  const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
  for (const r of selected.value.filter((x: any) => x.status === 1)) {
    try { await api.pause(r.packageOrderId, r.memberId) } catch {}
  }
  ElMessage.success('批量暂停完成')
  selected.value = []
  await Promise.all([loadList(), loadStats()])
}
async function batchResume() {
  if (!selected.value.length) return
  const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
  for (const r of selected.value.filter((x: any) => x.status !== 1)) {
    try { await api.resume(r.packageOrderId, r.memberId) } catch {}
  }
  ElMessage.success('批量恢复完成')
  selected.value = []
  await Promise.all([loadList(), loadStats()])
}
async function batchRevoke() {
  try {
    await ElMessageBox.confirm(`确认批量撤销 ${selected.value.length} 条共享资格？`, '批量撤销', { type: 'warning' })
    const api: any = await import('@/api/package-share').then(m => m.packageShareApi)
    for (const r of selected.value) {
      try { await api.revoke(r.packageOrderId, r.memberId) } catch {}
    }
    ElMessage.success('批量撤销完成')
    selected.value = []
    await Promise.all([loadList(), loadStats()])
  } catch (e: any) { if (e !== 'cancel') ElMessage.error('批量撤销失败') }
}

onMounted(async () => { await Promise.all([loadList(), loadStats()]) })
</script>

<style scoped>
.share-page { padding: 16px; }

.stats-row { margin-bottom: 12px; }
.stat-card { text-align: center; cursor: default; transition: transform 0.15s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-value { font-size: 32px; font-weight: 700; color: #1e293b; }
.stat-label { font-size: 13px; color: #666; margin-top: 4px; }
.stat-blue .stat-value { color: #409eff; }
.stat-green .stat-value { color: #67c23a; }
.stat-orange .stat-value { color: #e6a23c; }

.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 16px; font-weight: 600; }

.batch-bar {
  display: flex; align-items: center; gap: 8px;
  background: #f0f7ff; padding: 8px 12px; border-radius: 6px;
  margin-bottom: 12px; border: 1px solid #d9ecff;
}

.pkg-name { font-weight: 600; color: #303133; }
.pkg-code { font-size: 12px; }
.muted { color: #909399; font-size: 12px; }
.quota-bar { padding: 2px 0; }
.pagination { margin-top: 12px; text-align: right; }
</style>
