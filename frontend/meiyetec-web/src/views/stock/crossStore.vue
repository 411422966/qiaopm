<template>
  <div class="cross-store-page">
    <!-- 顶部 KPI 卡片 -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-blue">
        <div class="kpi-label">待审批</div>
        <div class="kpi-value">{{ dash.pendingCount ?? '—' }}</div>
        <div class="kpi-sub">待审批调拨单</div>
      </div>
      <div class="kpi-card kpi-orange">
        <div class="kpi-label">在途库存</div>
        <div class="kpi-value">{{ dash.inTransitQty ?? '—' }}</div>
        <div class="kpi-sub">APPROVED + SHIPPED 数量合计</div>
      </div>
      <div class="kpi-card kpi-green">
        <div class="kpi-label">今日已完成</div>
        <div class="kpi-value">{{ dash.todayDoneCount ?? '—' }}</div>
        <div class="kpi-sub">今日已收货调拨单</div>
      </div>
      <div class="kpi-card kpi-purple">
        <div class="kpi-label">调度成功率</div>
        <div class="kpi-value">{{ (dash.successRate ?? 0) + '%' }}</div>
        <div class="kpi-sub">平均耗时 {{ dash.avgTransferHours ?? 0 }} 小时</div>
      </div>
    </div>

    <!-- 第二行 KPI：建议 + 预警 -->
    <div class="kpi-grid second-row">
      <div class="kpi-card small kpi-red">
        <div class="kpi-label">高优建议</div>
        <div class="kpi-value">{{ dash.highPriorityCount ?? '—' }}</div>
      </div>
      <div class="kpi-card small kpi-orange">
        <div class="kpi-label">中优建议</div>
        <div class="kpi-value">{{ dash.mediumPriorityCount ?? '—' }}</div>
      </div>
      <div class="kpi-card small kpi-gray">
        <div class="kpi-label">低优建议</div>
        <div class="kpi-value">{{ dash.lowPriorityCount ?? '—' }}</div>
      </div>
      <div class="kpi-card small kpi-red-soft">
        <div class="kpi-label">高风险预警</div>
        <div class="kpi-value">{{ dash.highAlerts ?? '—' }}</div>
      </div>
      <div class="kpi-card small kpi-orange-soft">
        <div class="kpi-label">中风险预警</div>
        <div class="kpi-value">{{ dash.mediumAlerts ?? '—' }}</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :icon="Refresh" @click="loadDashboard">刷新</el-button>
        <el-button type="success" :icon="MagicStick" @click="runScheduleScan" :loading="scanning">
          运行智能调度（BALANCE-V2）
        </el-button>
        <el-button type="warning" :icon="Promotion" @click="openManualTransfer">手动创建调拨单</el-button>
        <span class="toolbar-tip">每天 02:00 自动扫描；建议采纳后自动生成调拨单</span>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 调度仪表盘 -->
        <el-tab-pane label="最近活动" name="recent">
          <el-table :data="dash.recentTransfers || []" stripe border>
            <el-table-column prop="transferNo" label="调拨单号" width="180" />
            <el-table-column prop="productName" label="商品" min-width="160" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="源仓" min-width="140">
              <template #default="{ row }">{{ row.fromWarehouseName || '—' }}</template>
            </el-table-column>
            <el-table-column label="目标仓" min-width="140">
              <template #default="{ row }">{{ row.toWarehouseName || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!dash.recentTransfers || !dash.recentTransfers.length" class="empty">暂无活动</div>
        </el-tab-pane>

        <!-- 跨店库存聚合视图 -->
        <el-tab-pane label="跨店库存视图" name="aggregate">
          <div class="filter-bar">
            <el-input v-model="aggFilter.keyword" placeholder="商品编码/名称" style="width:200px" clearable @keyup.enter="loadAggregate" />
            <el-switch v-model="aggFilter.alertOnly" active-text="仅显示预警" @change="loadAggregate" />
            <el-button :icon="Refresh" @click="loadAggregate">刷新</el-button>
          </div>
          <el-table :data="aggregateRows" stripe border v-loading="loadingAgg" row-key="productId"
            :expand-row-keys="expandedRows" @row-click="toggleExpand">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="store-list">
                  <el-table :data="row.stores" size="small" border>
                    <el-table-column prop="storeName" label="门店" width="180" />
                    <el-table-column prop="warehouseName" label="主仓" width="180" />
                    <el-table-column prop="quantity" label="库存" width="100" align="right" />
                    <el-table-column label="库存状态" width="120">
                      <template #default="{ srow }">
                        <el-tag size="small" :type="alertTagType(srow.alert)">{{ alertLabel(srow.alert) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="安全区间" width="160">
                      <template #default="{ srow }">{{ srow.minStock }} ~ {{ srow.maxStock }}</template>
                    </el-table-column>
                    <el-table-column label="调拨建议" min-width="240">
                      <template #default="{ srow }">
                        <span v-if="srow.alert === 'LOW' || srow.alert === 'OUT'" style="color:#f56c6c">
                          需补货 {{ Math.max(0, srow.minStock - srow.quantity) }}
                        </span>
                        <span v-else-if="srow.alert === 'OVER'" style="color:#e6a23c">
                          余量 {{ Math.max(0, srow.quantity - srow.maxStock) }}
                        </span>
                        <span v-else style="color:#67c23a">正常</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="productCode" label="商品编码" width="120" />
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="unit" label="单位" width="60" />
            <el-table-column prop="storeCount" label="门店数" width="80" align="right" />
            <el-table-column prop="totalQty" label="总库存" width="100" align="right" />
            <el-table-column label="预警门店" width="100" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.alertStores > 0 ? '#f56c6c' : '#909399' }">{{ row.alertStores }}</span>
              </template>
            </el-table-column>
            <el-table-column label="超储门店" width="100" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.overloadStores > 0 ? '#e6a23c' : '#909399' }">{{ row.overloadStores }}</span>
              </template>
            </el-table-column>
            <el-table-column label="调度健康度" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="balanceTagType(row.balance)">{{ balanceLabel(row.balance) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click.stop="openManualTransfer(row)">
                  创建调拨
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!aggregateRows.length && !loadingAgg" class="empty">暂无数据</div>
        </el-tab-pane>

        <!-- 智能建议 -->
        <el-tab-pane :label="`智能建议 (${suggFilter.accepted === 0 ? '待采纳' : '已采纳'})`" name="suggestion">
          <div class="filter-bar">
            <el-radio-group v-model="suggFilter.accepted" @change="loadSuggestions">
              <el-radio :value="0">待采纳</el-radio>
              <el-radio :value="1">已采纳</el-radio>
            </el-radio-group>
            <el-select v-model="suggFilter.priority" placeholder="优先级" clearable style="width:120px" @change="loadSuggestions">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
            <el-button :icon="Refresh" @click="loadSuggestions">刷新</el-button>
          </div>
          <el-table :data="suggestionRows" stripe border v-loading="loadingSugg">
            <el-table-column prop="productName" label="商品" min-width="180" />
            <el-table-column label="源仓" min-width="160">
              <template #default="{ row }">{{ row.fromWarehouseName || '—' }}</template>
            </el-table-column>
            <el-table-column label="目标仓" min-width="160">
              <template #default="{ row }">{{ row.toWarehouseName || '—' }}</template>
            </el-table-column>
            <el-table-column prop="suggestedQty" label="建议数量" width="100" align="right" />
            <el-table-column label="优先级" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="priorityTagType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="algorithmVersion" label="算法" width="100" />
            <el-table-column prop="reason" label="原因" min-width="280" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.accepted === 1" size="small" type="success">已采纳</el-tag>
                <el-tag v-else size="small" type="warning">待采纳</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.accepted !== 1" text type="success" size="small"
                  :icon="Check" @click="acceptSugg(row)">采纳</el-button>
                <el-button v-else text type="primary" size="small"
                  @click="goTransfer(row.resultTransferId)">查看调拨单</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!suggestionRows.length && !loadingSugg" class="empty">暂无建议（点击上方"运行智能调度"按钮生成）</div>
        </el-tab-pane>

        <!-- 调拨单 -->
        <el-tab-pane label="调拨单" name="transfer">
          <div class="filter-bar">
            <el-select v-model="transferFilter.status" placeholder="状态" clearable style="width:120px" @change="loadTransfers">
              <el-option label="待审批" value="PENDING" />
              <el-option label="已审批" value="APPROVED" />
              <el-option label="已发货" value="SHIPPED" />
              <el-option label="已收货" value="RECEIVED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-input v-model="transferFilter.keyword" placeholder="单号/商品名" style="width:200px" clearable @keyup.enter="loadTransfers" />
            <el-button :icon="Refresh" @click="loadTransfers">刷新</el-button>
          </div>
          <el-table :data="transferRows" stripe border v-loading="loadingTr">
            <el-table-column prop="transferNo" label="调拨单号" width="180" />
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <div>{{ row.productName }}</div>
                <div v-if="row.productCode" style="color:#909399;font-size:12px">{{ row.productCode }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="right" />
            <el-table-column label="源门店" min-width="140">
              <template #default="{ row }">{{ row.from_store_name || row.fromWarehouseName || '—' }}</template>
            </el-table-column>
            <el-table-column label="目标门店" min-width="140">
              <template #default="{ row }">{{ row.to_store_name || row.toWarehouseName || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button text type="success" size="small" :icon="Check" @click="approveTr(row)">审批</el-button>
                  <el-button text type="danger" size="small" :icon="Close" @click="cancelTr(row)">取消</el-button>
                </template>
                <template v-else-if="row.status === 'APPROVED'">
                  <el-button text type="primary" size="small" :icon="Van" @click="shipTr(row)">发货</el-button>
                  <el-button text type="danger" size="small" :icon="Close" @click="cancelTr(row)">取消</el-button>
                </template>
                <template v-else-if="row.status === 'SHIPPED'">
                  <el-button text type="success" size="small" :icon="Box" @click="receiveTr(row)">收货</el-button>
                </template>
                <template v-else>
                  <span style="color:#909399">—</span>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="transferFilter.pageNum"
            v-model:page-size="transferFilter.pageSize"
            :total="transferTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 12px; justify-content: flex-end; display: flex"
            @current-change="loadTransfers"
            @size-change="loadTransfers" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 手动创建调拨单弹窗 -->
    <el-dialog v-model="manualDialog.visible" title="手动创建调拨单" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        支持在门店间调拨商品；提交后状态为 PENDING（待审批）
      </el-alert>
      <el-form :model="manualForm" label-width="110px">
        <el-form-item label="商品" required>
          <el-select v-model="manualForm.productId" filterable placeholder="搜索商品编码/名称"
            :loading="loadingProducts" style="width:100%" @change="onProductChange">
            <el-option v-for="p in productOpts" :key="p.id"
              :label="`${p.productName}（${p.productCode || '-'}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源门店仓库" required>
          <el-select v-model="manualForm.fromWarehouseId" placeholder="选择源门店" style="width:100%">
            <el-option v-for="s in storeOpts" :key="s.warehouseId"
              :label="`${s.storeName}（${s.warehouseName || '-'}）`" :value="s.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标门店仓库" required>
          <el-select v-model="manualForm.toWarehouseId" placeholder="选择目标门店" style="width:100%">
            <el-option v-for="s in storeOpts" :key="'to-' + s.warehouseId"
              :label="`${s.storeName}（${s.warehouseName || '-'}）`" :value="s.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="调拨数量" required>
          <el-input-number v-model="manualForm.quantity" :min="1" :max="999999" :step="1" style="width:200px" />
          <span style="margin-left:8px;color:#909399">{{ unitLabel }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="manualForm.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="manualDialog.saving" @click="submitManualTransfer">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Promotion, MagicStick, Check, Close, Van, Box } from '@element-plus/icons-vue'
import { crossStoreApi } from '@/api/stock'

const activeTab = ref('recent')
const dash = ref<any>({})
const scanning = ref(false)

// ============= 跨店库存聚合 =============
const aggFilter = reactive({ keyword: '', alertOnly: false })
const aggregateRows = ref<any[]>([])
const loadingAgg = ref(false)
const expandedRows = ref<number[]>([])

async function loadAggregate() {
  loadingAgg.value = true
  try {
    const res: any = await crossStoreApi.inventoryAggregate({
      keyword: aggFilter.keyword || undefined,
      alertOnly: aggFilter.alertOnly ? 'true' : undefined,
      limit: 100
    })
    aggregateRows.value = res.data?.products || []
  } finally { loadingAgg.value = false }
}

function toggleExpand(row: any) {
  const i = expandedRows.value.indexOf(row.productId)
  if (i >= 0) expandedRows.value.splice(i, 1)
  else expandedRows.value.push(row.productId)
}

// ============= 智能建议 =============
const suggFilter = reactive({ accepted: 0 as number, priority: '' })
const suggestionRows = ref<any[]>([])
const loadingSugg = ref(false)

async function loadSuggestions() {
  loadingSugg.value = true
  try {
    const res: any = await crossStoreApi.suggestionPage({
      pageNum: 1,
      pageSize: 100,
      accepted: suggFilter.accepted,
      priority: suggFilter.priority || undefined
    })
    suggestionRows.value = res.data?.records || []
  } finally { loadingSugg.value = false }
}

async function runScheduleScan() {
  scanning.value = true
  try {
    const res: any = await crossStoreApi.scheduleScan({})
    if (res.code === 200) {
      ElMessage.success(
        `扫描完成：扫描 ${res.data?.scannedProducts} SKU，发现 ${res.data?.imbalanceProducts} 个不平衡，生成 ${res.data?.suggestionsGenerated} 条建议`
      )
      loadSuggestions()
      loadDashboard()
    }
  } finally { scanning.value = false }
}

async function acceptSugg(row: any) {
  await ElMessageBox.confirm(
    `确认采纳建议：从 ${row.fromWarehouseName} 调拨 ${row.suggestedQty} ${row.productName} 到 ${row.toWarehouseName}？`,
    '采纳建议', { type: 'warning' }
  ).catch(() => null)
  const res: any = await crossStoreApi.acceptSuggestion(row.id, {})
  if (res.code === 200) {
    ElMessage.success('已采纳，调拨单已生成')
    loadSuggestions()
    loadTransfers()
    loadDashboard()
  }
}

function goTransfer(id: number) {
  activeTab.value = 'transfer'
  ElMessage.info(`跳转到调拨单 #${id}`)
}

// ============= 调拨单 =============
const transferFilter = reactive({ pageNum: 1, pageSize: 20, status: '', keyword: '' })
const transferRows = ref<any[]>([])
const transferTotal = ref(0)
const loadingTr = ref(false)

async function loadTransfers() {
  loadingTr.value = true
  try {
    const res: any = await crossStoreApi.transferPage({
      pageNum: transferFilter.pageNum,
      pageSize: transferFilter.pageSize,
      status: transferFilter.status || undefined,
      keyword: transferFilter.keyword || undefined
    })
    transferRows.value = res.data?.records || []
    transferTotal.value = res.data?.total || 0
  } finally { loadingTr.value = false }
}

async function approveTr(row: any) {
  await ElMessageBox.confirm(
    `审批调拨单 ${row.transferNo}？`,
    '审批确认', { type: 'warning' }
  ).catch(() => null)
  const res: any = await crossStoreApi.approveTransfer(row.id, {})
  if (res.code === 200) { ElMessage.success('已审批'); loadTransfers(); loadDashboard() }
}
async function shipTr(row: any) {
  await ElMessageBox.confirm(
    `确认发货？发货后将扣减源仓库存`,
    '发货确认', { type: 'warning' }
  ).catch(() => null)
  const res: any = await crossStoreApi.shipTransfer(row.id)
  if (res.code === 200) { ElMessage.success('已发货'); loadTransfers(); loadDashboard() }
}
async function receiveTr(row: any) {
  await ElMessageBox.confirm(
    `确认收货？收货后将入库到目标仓`,
    '收货确认', { type: 'success' }
  ).catch(() => null)
  const res: any = await crossStoreApi.receiveTransfer(row.id, {})
  if (res.code === 200) { ElMessage.success('已收货'); loadTransfers(); loadDashboard() }
}
async function cancelTr(row: any) {
  const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消调拨单', { inputPlaceholder: '如：客户取消、库存调整等' })
    .catch(() => ({ value: '' }))
  if (!reason) return
  const res: any = await crossStoreApi.cancelTransfer(row.id, { reason })
  if (res.code === 200) { ElMessage.success('已取消'); loadTransfers(); loadDashboard(); loadSuggestions() }
}

// ============= 手动创建调拨单 =============
const manualDialog = reactive({ visible: false, saving: false })
const manualForm = reactive<any>({
  productId: null,
  fromWarehouseId: null,
  toWarehouseId: null,
  quantity: 1,
  remark: ''
})
const productOpts = ref<any[]>([])
const storeOpts = ref<any[]>([])
const loadingProducts = ref(false)

const unitLabel = computed(() => {
  const p: any = productOpts.value.find((x: any) => x.id === manualForm.productId)
  return p ? p.unit : ''
})

async function openManualTransfer(row?: any) {
  Object.assign(manualForm, {
    productId: row?.productId || null,
    fromWarehouseId: null,
    toWarehouseId: null,
    quantity: 1,
    remark: ''
  })
  manualDialog.visible = true
  await Promise.all([loadStoreOpts(), loadProductOpts()])
}

async function loadProductOpts() {
  loadingProducts.value = true
  try {
    const res: any = await crossStoreApi.productList({})
    productOpts.value = res.data || []
  } finally { loadingProducts.value = false }
}

async function loadStoreOpts() {
  try {
    const res: any = await crossStoreApi.storeList()
    storeOpts.value = (res.data || []).filter((s: any) => s.warehouseId)
  } catch { storeOpts.value = [] }
}

function onProductChange(_pid: number) {
  manualForm.quantity = 1
}

async function submitManualTransfer() {
  if (!manualForm.productId) { ElMessage.warning('请选择商品'); return }
  if (!manualForm.fromWarehouseId) { ElMessage.warning('请选择源门店'); return }
  if (!manualForm.toWarehouseId) { ElMessage.warning('请选择目标门店'); return }
  if (manualForm.fromWarehouseId === manualForm.toWarehouseId) {
    ElMessage.warning('源/目标门店不能相同'); return
  }
  if (!manualForm.quantity || manualForm.quantity <= 0) { ElMessage.warning('请填写数量'); return }
  manualDialog.saving = true
  try {
    const res: any = await crossStoreApi.createTransfer(manualForm)
    if (res.code === 200) {
      ElMessage.success('调拨单已创建（待审批）')
      manualDialog.visible = false
      loadTransfers()
      loadDashboard()
    }
  } finally { manualDialog.saving = false }
}

// ============= 仪表盘 =============
async function loadDashboard() {
  try {
    const res: any = await crossStoreApi.dashboard()
    dash.value = res.data || {}
  } catch {}
}

// ============= 工具函数 =============
function formatDate(v: any) {
  if (!v) return '—'
  return new Date(v).toLocaleString('zh-CN')
}
function statusLabel(s: string) {
  return ({
    PENDING: '待审批', APPROVED: '已审批', SHIPPED: '已发货',
    RECEIVED: '已收货', CANCELLED: '已取消'
  } as any)[s] || s
}
function statusTagType(s: string) {
  return ({
    PENDING: 'warning', APPROVED: 'primary', SHIPPED: 'info',
    RECEIVED: 'success', CANCELLED: 'danger'
  } as any)[s] || 'info'
}
function alertLabel(a: string) {
  return ({ OUT: '缺货', LOW: '低库存', OVER: '超储', OK: '正常' } as any)[a] || a
}
function alertTagType(a: string) {
  return ({ OUT: 'danger', LOW: 'warning', OVER: 'info', OK: 'success' } as any)[a] || 'info'
}
function balanceLabel(b: string) {
  return ({ OK: '正常', SHORTAGE: '缺货', OVERLOAD: '超储', IMBALANCE: '不平衡' } as any)[b] || b
}
function balanceTagType(b: string) {
  return ({ OK: 'success', SHORTAGE: 'danger', OVERLOAD: 'warning', IMBALANCE: 'danger' } as any)[b] || 'info'
}
function priorityLabel(p: string) {
  return ({ HIGH: '高', MEDIUM: '中', LOW: '低' } as any)[p] || p
}
function priorityTagType(p: string) {
  return ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' } as any)[p] || 'info'
}

watch(activeTab, (v) => {
  if (v === 'aggregate') loadAggregate()
  if (v === 'suggestion') loadSuggestions()
  if (v === 'transfer') loadTransfers()
})

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.cross-store-page { padding: 0; }
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-grid.second-row { grid-template-columns: repeat(5, 1fr); }
.kpi-card {
  padding: 16px 20px; border-radius: 12px; color: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08); position: relative; overflow: hidden;
}
.kpi-card.small { padding: 12px 16px; }
.kpi-card.small .kpi-value { font-size: 24px; }
.kpi-card::after {
  content: ''; position: absolute; right: -24px; top: -24px;
  width: 80px; height: 80px; border-radius: 50%;
  background: rgba(255,255,255,0.15);
}
.kpi-label { font-size: 13px; opacity: 0.9; }
.kpi-value { font-size: 32px; font-weight: 700; margin: 6px 0 4px; }
.kpi-card.small .kpi-value { margin: 2px 0; }
.kpi-sub { font-size: 12px; opacity: 0.8; }
.kpi-blue { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
.kpi-orange { background: linear-gradient(135deg, #f97316, #fb923c); }
.kpi-green { background: linear-gradient(135deg, #10b981, #34d399); }
.kpi-purple { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }
.kpi-red { background: linear-gradient(135deg, #dc2626, #ef4444); }
.kpi-red-soft { background: linear-gradient(135deg, #f87171, #fca5a5); }
.kpi-orange-soft { background: linear-gradient(135deg, #fbbf24, #fcd34d); }
.kpi-gray { background: linear-gradient(135deg, #6b7280, #9ca3af); }

.toolbar {
  display: flex; gap: 8px; align-items: center; margin-bottom: 16px;
}
.toolbar-tip { margin-left: auto; color: #909399; font-size: 12px; }
.filter-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.empty { padding: 30px; text-align: center; color: #909399; }
.store-list { padding: 8px 24px; background: #f9fafb; border-radius: 6px; }
</style>
