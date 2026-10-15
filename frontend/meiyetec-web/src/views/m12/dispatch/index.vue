<template>
  <div class="dispatch-page">
    <!-- 顶部看板 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="6" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">下发单总数</div>
          <div class="kpi-value">{{ stats.totalDispatches ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="4">
        <el-card shadow="hover" class="kpi-card success">
          <div class="kpi-label">已完成</div>
          <div class="kpi-value">{{ stats.doneCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="4">
        <el-card shadow="hover" class="kpi-card warning">
          <div class="kpi-label">草稿</div>
          <div class="kpi-value">{{ stats.draftCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="4">
        <el-card shadow="hover" class="kpi-card danger">
          <div class="kpi-label">已取消</div>
          <div class="kpi-value">{{ stats.cancelledCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="4">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-label">成功门店</div>
          <div class="kpi-value">{{ stats.successfulDispatches ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="4">
        <el-card shadow="hover" class="kpi-card danger">
          <div class="kpi-label">失败门店</div>
          <div class="kpi-value">{{ stats.failedDispatches ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="tab">
      <!-- 下发单列表 -->
      <el-tab-pane label="下发单" name="list">
        <el-card class="toolbar">
          <el-select v-model="filter.dispatchType" placeholder="类型" clearable style="width: 140px" @change="loadList">
            <el-option v-for="(label, code) in DISPATCH_TYPE_LABEL" :key="code" :label="label" :value="code" />
          </el-select>
          <el-select v-model="filter.dispatchSubtype" placeholder="子类型" clearable style="width: 140px" @change="loadList">
            <el-option v-for="(label, code) in DISPATCH_SUBTYPE_LABEL" :key="code" :label="label" :value="code" />
          </el-select>
          <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadList">
            <el-option v-for="(s, code) in DISPATCH_STATUS_LABEL" :key="code" :label="s.label" :value="code" />
          </el-select>
          <el-button type="primary" @click="loadList">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-button type="primary" @click="openCreate">+ 新建下发单</el-button>
          <el-button @click="loadStats">刷新统计</el-button>
        </el-card>

        <el-card>
          <el-table :data="rows" v-loading="loading" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="dispatchNo" label="下发单号" width="200" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ DISPATCH_TYPE_LABEL[row.dispatchType] || row.dispatchType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="子类型" width="140">
              <template #default="{ row }">
                <el-tag size="small" type="info" v-if="row.dispatchSubtype">
                  {{ DISPATCH_SUBTYPE_LABEL[row.dispatchSubtype] || row.dispatchSubtype }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column label="源" width="80">
              <template #default="{ row }">{{ row.sourceId || '-' }}</template>
            </el-table-column>
            <el-table-column label="下发版本" width="80">
              <template #default="{ row }">v{{ row.publishedVersion }}</template>
            </el-table-column>
            <el-table-column label="目标" width="120">
              <template #default="{ row }">
                <span v-if="row.targetType === 'ALL'">全部门店</span>
                <span v-else>指定 {{ row.targetStoreIds ? row.targetStoreIds.split(',').length : 0 }} 店</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusInfo(row.status).tag" size="small">
                  {{ statusInfo(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="成功/失败/总数" width="160">
              <template #default="{ row }">
                <span class="suc">{{ row.successCount }}</span>
                /
                <span class="fail">{{ row.failCount }}</span>
                /
                <span>{{ row.totalCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openDetail(row)">详情</el-button>
                <el-button
                  v-if="['DRAFT', 'PUBLISHED'].includes(row.status)"
                  link
                  size="small"
                  type="primary"
                  @click="publish(row)"
                >下发</el-button>
                <el-button
                  v-if="['DRAFT', 'PUBLISHED'].includes(row.status) && row.totalCount > 50"
                  link
                  size="small"
                  type="warning"
                  @click="publishAsync(row)"
                >异步下发</el-button>
                <el-button
                  v-if="['DRAFT', 'PUBLISHED'].includes(row.status)"
                  link
                  size="small"
                  type="danger"
                  @click="cancel(row)"
                >取消</el-button>
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
      </el-tab-pane>

      <!-- 看板 -->
      <el-tab-pane label="按类型分布" name="stats">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-card>
              <template #header><b>按 dispatchType 分布</b></template>
              <div v-if="typeEntries.length === 0" class="empty">暂无数据</div>
              <div v-else class="bar-list">
                <div v-for="item in typeEntries" :key="item.code" class="bar-row">
                  <div class="bar-label">{{ DISPATCH_TYPE_LABEL[item.code] || item.code }}</div>
                  <div class="bar-track">
                    <div class="bar-fill" :style="{ width: barWidth(item.count) + '%' }"></div>
                    <span class="bar-count">{{ item.count }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card>
              <template #header><b>按 dispatchSubtype 分布</b></template>
              <div v-if="subtypeEntries.length === 0" class="empty">暂无数据</div>
              <div v-else class="bar-list">
                <div v-for="item in subtypeEntries" :key="item.code" class="bar-row">
                  <div class="bar-label">{{ DISPATCH_SUBTYPE_LABEL[item.code] || item.code }}</div>
                  <div class="bar-track">
                    <div class="bar-fill alt" :style="{ width: barWidth(item.count) + '%' }"></div>
                    <span class="bar-count">{{ item.count }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建下发单对话框 -->
    <el-dialog v-model="createDialog" title="新建业务策略下发单" width="640px" @closed="resetForm">
      <el-form :model="form" label-width="110px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="例：国庆全员价格规则 v2 下发" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="form.dispatchType" @change="onTypeChange">
                <el-option v-for="(label, code) in DISPATCH_TYPE_LABEL" :key="code" :label="label" :value="code" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="子类型">
              <el-select v-model="form.dispatchSubtype" :disabled="subtypeLocked" allow-clear>
                <el-option label="价格规则 (PRICING_RULE)" value="PRICING_RULE" />
                <el-option label="营销活动 (ACTIVITY)" value="ACTIVITY" />
                <el-option label="优惠券批次 (COUPON_BATCH)" value="COUPON_BATCH" />
                <el-option label="实验 (EXPERIMENT)" value="EXPERIMENT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="源策略 ID" :required="needsSource">
              <el-input-number v-model="form.sourceId" :min="1" style="width: 100%" :disabled="!needsSource" />
              <div class="form-tip">价格规则/活动/批次/实验 的源 ID</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="源版本">
              <el-input v-model="form.sourceVersion" placeholder="例：v1.2.0" :disabled="!needsSource" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="目标门店">
          <el-radio-group v-model="form.targetType">
            <el-radio value="ALL">全部门店</el-radio>
            <el-radio value="SPECIFIC">指定门店</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.targetType === 'SPECIFIC'" label="门店 ID 列表">
          <el-input v-model="storeIdsText" placeholder="逗号分隔，例如：101,102,103" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存为草稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDrawer" :title="detailTitle" size="800px">
      <div v-if="detail" class="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="下发单号">{{ detail.dispatchNo }}</el-descriptions-item>
          <el-descriptions-item label="下发单状态">
            <el-tag :type="statusInfo(detail.status).tag" size="small">
              {{ statusInfo(detail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ DISPATCH_TYPE_LABEL[detail.dispatchType] }}</el-descriptions-item>
          <el-descriptions-item label="子类型">{{ DISPATCH_SUBTYPE_LABEL[detail.dispatchSubtype] || '-' }}</el-descriptions-item>
          <el-descriptions-item label="源 ID">{{ detail.sourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="源版本">{{ detail.sourceVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下发版本">v{{ detail.publishedVersion }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDate(detail.publishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="成功/失败/总数">
            <span class="suc">{{ detail.successCount }}</span> /
            <span class="fail">{{ detail.failCount }}</span> /
            <span>{{ detail.totalCount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="目标" :span="2">
            <span v-if="detail.targetType === 'ALL'">全部门店</span>
            <span v-else>指定门店：{{ detail.targetStoreIds }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab" class="detail-tabs">
          <el-tab-pane label="执行日志" name="logs">
            <el-table :data="detailLogs" size="small" border stripe>
              <el-table-column prop="storeName" label="门店" />
              <el-table-column prop="dispatchType" label="类型" width="120" />
              <el-table-column prop="status" label="结果" width="90">
                <template #default="{ row }">
                  <el-tag :type="logTag(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
              <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="目标门店" name="targets">
            <el-table :data="detailTargets" size="small" border stripe>
              <el-table-column prop="storeId" label="门店 ID" width="100" />
              <el-table-column prop="storeName" label="门店名称" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="targetTag(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="rejectedReason" label="拒绝原因" show-overflow-tooltip />
              <el-table-column prop="executedAt" label="执行时间" width="170">
                <template #default="{ row }">{{ formatDate(row.executedAt) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  dispatchApi,
  DISPATCH_TYPE_LABEL,
  DISPATCH_SUBTYPE_LABEL,
  DISPATCH_STATUS_LABEL
} from '@/api/dispatch'

const tab = ref('list')

const filter = reactive({
  pageNum: 1,
  pageSize: 20,
  dispatchType: undefined as string | undefined,
  dispatchSubtype: undefined as string | undefined,
  status: undefined as string | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<Record<string, any>>({})

const typeEntries = computed(() =>
  Object.entries(stats.value.byType || {}).map(([code, count]) => ({ code, count: count as number }))
)
const subtypeEntries = computed(() =>
  Object.entries(stats.value.bySubtype || {}).map(([code, count]) => ({ code, count: count as number }))
)
const maxBar = computed(() => {
  const all = [...typeEntries.value, ...subtypeEntries.value]
  return all.reduce((m, e) => Math.max(m, e.count), 1)
})
function barWidth(c: number) {
  return Math.round((c / maxBar.value) * 100)
}

async function loadList() {
  loading.value = true
  try {
    const res = await dispatchApi.page({
      pageNum: filter.pageNum,
      pageSize: filter.pageSize,
      dispatchType: filter.dispatchType || undefined,
      dispatchSubtype: filter.dispatchSubtype || undefined,
      status: filter.status || undefined
    })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await dispatchApi.stats()
    stats.value = res.data || {}
  } catch {
    stats.value = {}
  }
}

function resetFilter() {
  filter.dispatchType = undefined
  filter.dispatchSubtype = undefined
  filter.status = undefined
  filter.pageNum = 1
  loadList()
}

function statusInfo(s: string) {
  return DISPATCH_STATUS_LABEL[s] || { label: s || '-', tag: 'info' as const }
}

function formatDate(d: string) {
  if (!d) return '-'
  return d.replace('T', ' ').substring(0, 19)
}

function logTag(s: string) {
  return ({ SUCCESS: 'success', FAILED: 'danger', SKIPPED: 'warning' } as any)[s] || 'info'
}
function targetTag(s: string) {
  return ({ PENDING: 'info', ACCEPTED: 'primary', EXECUTED: 'success', REJECTED: 'danger' } as any)[s] || 'info'
}

// 新建
const createDialog = ref(false)
const submitting = ref(false)
const form = reactive<any>({
  title: '',
  dispatchType: 'PRICING_RULE',
  dispatchSubtype: 'PRICING_RULE',
  sourceId: undefined,
  sourceVersion: '',
  targetType: 'ALL',
  remark: ''
})
const storeIdsText = ref('')
const subtypeLocked = ref(true)
const needsSource = computed(() => ['PRICING_RULE', 'ACTIVITY'].includes(form.dispatchType))

function openCreate() {
  resetForm()
  createDialog.value = true
}

function resetForm() {
  form.title = ''
  form.dispatchType = 'PRICING_RULE'
  form.dispatchSubtype = 'PRICING_RULE'
  form.sourceId = undefined
  form.sourceVersion = ''
  form.targetType = 'ALL'
  form.remark = ''
  storeIdsText.value = ''
  subtypeLocked.value = true
}

function onTypeChange(t: string) {
  // 默认锁定 + 回填同名 subtype
  subtypeLocked.value = true
  form.dispatchSubtype = t === 'PRICING_RULE' || t === 'ACTIVITY' ? t : undefined
}

async function save() {
  if (!form.title) return ElMessage.warning('请填写标题')
  if (!form.dispatchType) return ElMessage.warning('请选择类型')
  if (needsSource.value && !form.sourceId) return ElMessage.warning('请填写源策略 ID')

  submitting.value = true
  try {
    const payload: any = {
      title: form.title,
      dispatchType: form.dispatchType,
      dispatchSubtype: form.dispatchSubtype || undefined,
      sourceId: form.sourceId || undefined,
      sourceVersion: form.sourceVersion || undefined,
      targetType: form.targetType,
      remark: form.remark || undefined
    }
    if (form.targetType === 'SPECIFIC') {
      const ids = storeIdsText.value.split(',').map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n) && n > 0)
      payload.storeIds = ids
      payload.targetStoreIds = ids.join(',')
    }
    await dispatchApi.create(payload)
    ElMessage.success('已创建为 DRAFT')
    createDialog.value = false
    loadList()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

async function publish(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认对「${row.title}」执行下发？\n门店数：${row.totalCount}`,
      '下发确认',
      { type: 'warning' }
    )
  } catch { return }
  const res = await dispatchApi.publish(row.id)
  ElMessage.success(res?.message || '已完成下发')
  loadList()
  loadStats()
}

async function publishAsync(row: any) {
  await dispatchApi.publishAsync(row.id)
  ElMessage.success('异步下发任务已提交，请稍后刷新查看')
  loadList()
  loadStats()
}

async function cancel(row: any) {
  await ElMessageBox.confirm(`确认取消下发单「${row.title}」？`, '提示', { type: 'warning' })
  await dispatchApi.cancel(row.id)
  ElMessage.success('已取消')
  loadList()
  loadStats()
}

// 详情抽屉
const detailDrawer = ref(false)
const detail = ref<any>(null)
const detailLogs = ref<any[]>([])
const detailTargets = ref<any[]>([])
const detailTab = ref('logs')
const detailTitle = computed(() => detail.value ? `下发单 ${detail.value.dispatchNo}` : '下发单详情')

async function openDetail(row: any) {
  detailDrawer.value = true
  detailTab.value = 'logs'
  try {
    const [d, l, t] = await Promise.all([
      dispatchApi.detail(row.id),
      dispatchApi.logs(row.id),
      dispatchApi.targets(row.id)
    ])
    detail.value = d.data
    detailLogs.value = l.data || []
    detailTargets.value = t.data || []
  } catch {
    detail.value = row
    detailLogs.value = []
    detailTargets.value = []
  }
}

onMounted(() => {
  loadList()
  loadStats()
})
</script>

<style scoped>
.dispatch-page { padding: 0; }
.kpi-row { margin-bottom: 12px; }
.kpi-card { text-align: center; }
.kpi-card .kpi-label { color: #909399; font-size: 13px; }
.kpi-card .kpi-value { font-size: 24px; font-weight: 600; margin-top: 6px; }
.kpi-card.success .kpi-value { color: #67c23a; }
.kpi-card.warning .kpi-value { color: #e6a23c; }
.kpi-card.danger .kpi-value { color: #f56c6c; }
.toolbar { margin-bottom: 12px; }
.toolbar .el-select,
.toolbar .el-input { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
.suc { color: #67c23a; }
.fail { color: #f56c6c; }
.detail-tabs { margin-top: 16px; }
.bar-list { padding: 8px 0; }
.bar-row { display: flex; align-items: center; margin-bottom: 12px; }
.bar-label { width: 140px; color: #606266; }
.bar-track {
  flex: 1;
  background: #f0f3f5;
  border-radius: 4px;
  height: 22px;
  position: relative;
  overflow: hidden;
}
.bar-fill {
  background: linear-gradient(90deg, #409eff, #79bbff);
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}
.bar-fill.alt {
  background: linear-gradient(90deg, #67c23a, #95d475);
}
.bar-count {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: #606266;
}
.empty { color: #909399; text-align: center; padding: 30px; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
