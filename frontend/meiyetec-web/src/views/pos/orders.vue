<template>
  <div class="orders-page">
    <!-- 顶部 Banner -->
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><Document /></el-icon>
        <div>
          <h2>订单管理</h2>
          <p>查看所有订单 · 退款 / 撤销 / 导出报表</p>
        </div>
      </div>
      <div class="banner-right">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button :icon="Printer" @click="printCurrentPage" plain>打印当前页</el-button>
        <el-button type="success" :icon="Download" @click="exportCSV" plain>导出CSV</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card s-1" @click="filter.status = null" :class="{ active: filter.status === null }">
        <div class="stat-label">总订单数</div>
        <div class="stat-num">{{ stats.total }}</div>
        <small>所有状态</small>
      </div>
      <div class="stat-card s-2" @click="filter.status = 1" :class="{ active: filter.status === 1 }">
        <div class="stat-label">待支付</div>
        <div class="stat-num">{{ stats.pending }}</div>
        <small>未结账</small>
      </div>
      <div class="stat-card s-3" @click="filter.status = 2" :class="{ active: filter.status === 2 }">
        <div class="stat-label">已支付</div>
        <div class="stat-num">{{ stats.paid }}</div>
        <small>已完成</small>
      </div>
      <div class="stat-card s-4" @click="filter.status = 3" :class="{ active: filter.status === 3 }">
        <div class="stat-label">已退款</div>
        <div class="stat-num">{{ stats.refunded }}</div>
        <small>已退款金额 ¥{{ stats.refundAmount.toFixed(2) }}</small>
      </div>
      <div class="stat-card s-5" @click="filter.status = 4" :class="{ active: filter.status === 4 }">
        <div class="stat-label">总营收</div>
        <div class="stat-num">¥{{ stats.revenue.toFixed(2) }}</div>
        <small>已支付订单总额</small>
      </div>
    </div>

    <!-- 状态 Tab + 快捷日期 -->
    <div class="tab-bar">
      <el-radio-group :model-value="statusTabValue" @change="onStatusTabChange" size="default">
        <el-radio-button :value="-1">全部 <span class="badge b-default">{{ tabCountAll }}</span></el-radio-button>
        <el-radio-button v-for="t in STATUS_TABS" :key="t.value" :value="t.value">
          {{ t.label }}
          <span class="badge" :class="`b-${t.color}`">{{ tabCount(t.value) }}</span>
        </el-radio-button>
      </el-radio-group>
      <div class="quick-date">
        <el-button-group size="small">
          <el-button :type="quickDate === 'today' ? 'primary' : ''" @click="setQuickDate('today')">今天</el-button>
          <el-button :type="quickDate === 'week' ? 'primary' : ''" @click="setQuickDate('week')">本周</el-button>
          <el-button :type="quickDate === 'month' ? 'primary' : ''" @click="setQuickDate('month')">本月</el-button>
          <el-button :type="quickDate === '' ? 'primary' : ''" @click="setQuickDate('')">全部</el-button>
        </el-button-group>
      </div>
    </div>

    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="订单号">
          <el-input v-model="filter.orderNo" placeholder="搜索订单号  Ctrl+K" clearable
                    style="width: 180px" ref="orderNoInputRef" @keydown.enter="load">
            <template #suffix>
              <kbd class="kbd-hint">⌘K</kbd>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="会员">
          <el-input v-model="filter.memberKw" placeholder="姓名 / 手机号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="s in STATUS_LIST" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="filter.dateRange" type="daterange"
                          range-separator="至" start-placeholder="开始" end-placeholder="结束"
                          value-format="YYYY-MM-DD" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
          <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 订单列表 -->
    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="hint">
          <el-icon><InfoFilled /></el-icon>
          共 {{ filteredOrders.length }} 条订单，{{ pagedOrders.length }} 条显示在当前页
          <span class="scroll-hint" v-if="filteredOrders.length > 0">（左右滚动查看更多列）</span>
        </span>
        <div class="table-extra">
          <el-checkbox-group v-model="visibleColumns" size="small" class="col-toggle">
            <el-checkbox v-for="col in allColumns" :key="col.key" :label="col.key" :value="col.key" border>
              {{ col.label }}
            </el-checkbox>
          </el-checkbox-group>

          <span class="toolbar-spacer" />

          <!-- 密度切换 -->
          <el-tooltip content="表格密度" placement="top">
            <el-button-group size="small">
              <el-button :type="tableSize === 'small' ? 'primary' : 'default'" @click="tableSize = 'small'">
                <el-icon><Minus /></el-icon>
              </el-button>
              <el-button :type="tableSize === 'default' ? 'primary' : 'default'" @click="tableSize = 'default'">
                <el-icon><Menu /></el-icon>
              </el-button>
              <el-button :type="tableSize === 'large' ? 'primary' : 'default'" @click="tableSize = 'large'">
                <el-icon><Plus /></el-icon>
              </el-button>
            </el-button-group>
          </el-tooltip>

          <!-- 视图 -->
          <el-dropdown trigger="click" @command="handleViewCmd">
            <el-button :icon="Collection" size="small">视图</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="save">💾 保存当前筛选/排序</el-dropdown-item>
                <el-dropdown-item v-if="savedViews.length === 0" disabled>（暂无保存的视图）</el-dropdown-item>
                <el-dropdown-item
                  v-for="v in savedViews"
                  :key="v.id"
                  :command="`apply:${v.id}`">
                  📋 {{ v.name }}
                </el-dropdown-item>
                <el-dropdown-item divided v-if="savedViews.length > 0" command="manage">管理视图…</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <el-table :data="pagedOrders" :size="tableSize" border stripe v-loading="loading" empty-text="暂无订单" :max-height="tableHeight">
        <el-table-column type="index" label="#" width="55" align="center" fixed="left" />
        <el-table-column v-if="visibleColumns.includes('orderNo')" prop="orderNo" label="订单号" width="200" fixed="left">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('member')" label="会员" min-width="160">
          <template #default="{ row }">
            <div class="member-cell">
              <strong>{{ row.memberName || '散客' }}</strong>
              <small v-if="row.memberPhone">{{ row.memberPhone }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('status')" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('amount')" label="订单金额" width="140" align="right">
          <template #default="{ row }">
            <div class="amount">
              <span v-if="row.discountAmount > 0" class="line-through">¥{{ Number(row.totalAmount).toFixed(2) }}</span>
              <b class="final">¥{{ Number(row.finalAmount).toFixed(2) }}</b>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('paid')" label="已支付" width="110" align="right">
          <template #default="{ row }">¥{{ Number(row.paidAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('refund')" label="退款" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.refundAmount > 0" class="refund">¥{{ Number(row.refundAmount).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('payMethod')" label="支付方式" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.payments && row.payments.length">{{ methodLabel(row.payments[0].method) }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('cashier')" label="收银员" width="100">
          <template #default="{ row }">{{ row.cashierName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('payTime')" label="支付时间" width="170">
          <template #default="{ row }">
            <el-tooltip :content="formatDateTime(row.payTime)" placement="top">
              <span>{{ relativeTime(row.payTime) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column v-if="visibleColumns.includes('createTime')" label="下单时间" width="170">
          <template #default="{ row }">
            <el-tooltip :content="formatDateTime(row.createTime)" placement="top">
              <span>{{ relativeTime(row.createTime) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 2" size="small" link type="danger" @click="openRefund(row)">退款</el-button>
            <el-button v-if="row.status === 1" size="small" link @click="openVoid(row)">撤销</el-button>
            <el-button size="small" link type="success" @click="reprint(row)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="filteredOrders.length"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="pageNum = $event"
        @size-change="pageSize = $event"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="`订单详情 · ${activeOrder?.orderNo || ''}`" width="640">
      <div v-if="activeOrder" class="detail-body">
        <div class="info-row">
          <span class="info-label">状态</span>
          <el-tag :type="statusType(activeOrder.status)" size="default">{{ statusLabel(activeOrder.status) }}</el-tag>
        </div>
        <div class="info-row"><span class="info-label">会员</span><b>{{ activeOrder.memberName || '散客' }}</b> ({{ activeOrder.memberPhone || '-' }})</div>
        <div class="info-row"><span class="info-label">收银员</span>{{ activeOrder.cashierName || '-' }}</div>
        <div class="info-row"><span class="info-label">下单时间</span>{{ formatDateTime(activeOrder.createTime) }}</div>
        <div class="info-row" v-if="activeOrder.payTime"><span class="info-label">支付时间</span>{{ formatDateTime(activeOrder.payTime) }}</div>
        <div class="info-row" v-if="activeOrder.refundTime"><span class="info-label">退款时间</span>{{ formatDateTime(activeOrder.refundTime) }}</div>
        <div class="info-row" v-if="activeOrder.refundReason"><span class="info-label">退款原因</span>{{ activeOrder.refundReason }}</div>
        <div class="info-row" v-if="activeOrder.remark"><span class="info-label">备注</span>{{ activeOrder.remark }}</div>

        <el-divider>商品明细</el-divider>
        <el-table :data="activeOrder.items || []" border size="small">
          <el-table-column prop="serviceName" label="服务" min-width="160" />
          <el-table-column prop="quantity" label="数量" width="70" align="center" />
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="120" align="right">
            <template #default="{ row }">¥{{ Number(row.finalPrice || row.totalPrice).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <el-divider>支付记录</el-divider>
        <el-table :data="activeOrder.payments || []" border size="small" empty-text="未支付">
          <el-table-column prop="paymentNo" label="流水号" min-width="180" />
          <el-table-column prop="method" label="方式" width="100">
            <template #default="{ row }">{{ methodLabel(row.method) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="支付时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.payTime) }}</template>
          </el-table-column>
        </el-table>

        <el-divider>金额汇总</el-divider>
        <div class="amount-summary">
          <div><span>原价：</span>¥{{ Number(activeOrder.totalAmount).toFixed(2) }}</div>
          <div><span>折扣：</span>-¥{{ Number(activeOrder.discountAmount || 0).toFixed(2) }}</div>
          <div class="hl"><span>应收：</span><b>¥{{ Number(activeOrder.finalAmount).toFixed(2) }}</b></div>
          <div><span>实付：</span>¥{{ Number(activeOrder.paidAmount || 0).toFixed(2) }}</div>
          <div v-if="activeOrder.refundAmount > 0" class="refund"><span>已退：</span>¥{{ Number(activeOrder.refundAmount).toFixed(2) }}</div>
        </div>
      </div>
    </el-dialog>

    <!-- 退款弹窗（R2.0-C T-C05：统一使用 RefundDialog，支持策略选择） -->
    <RefundDialog
      v-if="refundVisible && activeOrder"
      v-model="refundVisible"
      :order="activeOrder"
      @success="onRefunded"
    />

    <!-- 撤销弹窗 -->
    <el-dialog v-model="voidVisible" title="撤销订单" width="420">
      <div class="refund-info">
        <div>订单号：<b>{{ activeOrder?.orderNo }}</b></div>
        <div>状态：<el-tag type="warning">待支付（未结账）</el-tag></div>
      </div>
      <el-input v-model="voidReason" placeholder="撤销原因（可选）" type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="voidVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmVoid" :loading="voiding">确认撤销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Refresh, Download, Search, RefreshLeft, InfoFilled, Printer, Minus, Menu, Plus, Collection } from '@element-plus/icons-vue'
import { orderApi } from '@/api/pos'
import RefundDialog from '@/components/RefundDialog.vue'

// ========== 表格列控制（用户可隐藏不需要的列） ==========
const allColumns = [
  { key: 'orderNo', label: '订单号' },
  { key: 'member', label: '会员' },
  { key: 'status', label: '状态' },
  { key: 'amount', label: '订单金额' },
  { key: 'paid', label: '已支付' },
  { key: 'refund', label: '退款' },
  { key: 'payMethod', label: '支付方式' },
  { key: 'cashier', label: '收银员' },
  { key: 'payTime', label: '支付时间' },
  { key: 'createTime', label: '下单时间' }
]
const visibleColumns = ref<string[]>(['orderNo', 'member', 'status', 'amount', 'paid', 'payMethod', 'payTime', 'cashier'])
const quickDate = ref<string>('')   // today / week / month / ''

// 订单号输入框 ref（Ctrl+K 快捷键聚焦）
const orderNoInputRef = ref<any>(null)
const handleGlobalKey = (e: KeyboardEvent) => {
  // Ctrl+K / Cmd+K 聚焦搜索框
  if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
    e.preventDefault()
    nextTick(() => {
      const el = orderNoInputRef.value?.$el?.querySelector?.('input')
      if (el) { el.focus(); el.select?.() }
    })
  }
}

// ========== 状态枚举 ==========
const STATUS_LIST = [
  { value: 1, label: '待支付' },
  { value: 2, label: '已支付' },
  { value: 3, label: '已退款' },
  { value: 4, label: '已撤销' }
]
const STATUS: any = { 1: '待支付', 2: '已支付', 3: '已退款', 4: '已撤销' }
const STATUS_TABS = [
  { value: 1, label: '待支付', color: 'warning' },
  { value: 2, label: '已支付', color: 'success' },
  { value: 3, label: '已退款', color: 'info' },
  { value: 4, label: '已撤销', color: 'danger' }
]
const METHOD: any = {
  cash: '现金', wallet: '储值', wechat: '微信', alipay: '支付宝',
  unionpay: '银联', e_cny_soft: '数字人民币', e_cny_hard: '硬件钱包',
  points: '积分', coupon: '优惠券'
}
const statusLabel = (s: number) => STATUS[s] || '未知'
const statusType = (s: number) => ({ 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }[s] || '')
const methodLabel = (m: string) => METHOD[m] || m

const formatDateTime = (s: string) => s ? s.replace('T', ' ').slice(0, 19) : '-'

// ========== 相对时间 ==========
const relativeTime = (s: string): string => {
  if (!s) return '-'
  const t = new Date(s.replace(' ', 'T')).getTime()
  if (isNaN(t)) return formatDateTime(s)
  const now = Date.now()
  const diffSec = Math.floor((now - t) / 1000)
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const dDate = new Date(t); dDate.setHours(0, 0, 0, 0)
  const dayDiff = Math.floor((today.getTime() - dDate.getTime()) / 86400000)
  const hh = new Date(t).toTimeString().slice(0, 5)   // HH:MM

  // 未来时间（容错） → 完整日期
  if (diffSec < 0) return formatDateTime(s)
  // 1 分钟内
  if (diffSec < 60) return '刚刚'
  // 1 小时内
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)} 分钟前`
  // 今天
  if (dayDiff === 0) return `今天 ${hh}`
  // 昨天
  if (dayDiff === 1) return `昨天 ${hh}`
  // 7 天内
  if (dayDiff < 7) return `${dayDiff} 天前 ${hh}`
  // 今年
  const yr = new Date(t).getFullYear()
  if (yr === new Date().getFullYear()) {
    return `${new Date(t).getMonth() + 1}月${new Date(t).getDate()}日 ${hh}`
  }
  // 跨年
  return formatDateTime(s)
}

// ========== 数据 ==========
const allOrders = ref<any[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)

const filter = ref<any>({ orderNo: '', memberKw: '', status: null as number | null, dateRange: null as [string, string] | null })

// ===== 筛选条件持久化 =====
const FILTER_STORAGE_KEY = 'pos-orders-filter'
function loadPersistedFilter() {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as any
    if (saved.orderNo !== undefined) filter.value.orderNo = saved.orderNo
    if (saved.memberKw !== undefined) filter.value.memberKw = saved.memberKw
    if (saved.status !== undefined) filter.value.status = saved.status
    if (saved.dateRange !== undefined) filter.value.dateRange = saved.dateRange
  } catch {}
}
watch(
  () => ({
    o: filter.value.orderNo, m: filter.value.memberKw, s: filter.value.status, d: filter.value.dateRange
  }),
  (v) => localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(v)),
  { deep: false }
)

// ===== 表格密度 =====
const tableSize = ref<'small' | 'default' | 'large'>(
  (localStorage.getItem('pos-orders-size') as any) || 'default'
)
watch(tableSize, (v) => localStorage.setItem('pos-orders-size', v))

// ===== 自适应高度 =====
const tableHeight = computed(() => Math.max(420, window.innerHeight - 360))

// ===== 视图 =====
interface SavedView { id: string; name: string; filter: any }
const VIEWS_KEY = 'pos-orders-views'
const savedViews = ref<SavedView[]>([])
function loadSavedViews() {
  try {
    const raw = localStorage.getItem(VIEWS_KEY)
    savedViews.value = raw ? JSON.parse(raw) : []
  } catch { savedViews.value = [] }
}
function persistSavedViews() {
  localStorage.setItem(VIEWS_KEY, JSON.stringify(savedViews.value))
}
async function saveCurrentAsView() {
  const { value: name } = await ElMessageBox.prompt('给当前视图起个名字（最多 20 字）', '保存视图', {
    inputValue: '',
    inputValidator: (v: string) => {
      const t = v?.trim() || ''
      if (!t) return '名称不能为空'
      if (t.length > 20) return '名称过长'
      if (savedViews.value.some((x) => x.name === t)) return '已存在同名视图'
      return true
    }
  }).catch(() => ({ value: '' }))
  if (!name) return
  savedViews.value.push({
    id: 'v' + Date.now(),
    name: name.trim(),
    filter: { ...filter.value }
  })
  persistSavedViews()
  ElMessage.success('已保存视图')
}
function applyView(v: SavedView) {
  filter.value = { ...filter.value, ...v.filter }
  ElMessage.success(`已应用视图：${v.name}`)
}
async function deleteView(v: SavedView) {
  await ElMessageBox.confirm(`确认删除视图「${v.name}」？`, '提示', { type: 'warning' })
  savedViews.value = savedViews.value.filter((x) => x.id !== v.id)
  persistSavedViews()
}
async function handleViewCmd(cmd: string) {
  if (cmd === 'save') return saveCurrentAsView()
  if (cmd === 'manage') {
    const { value: picked } = await ElMessageBox.prompt(
      `请输入要删除的视图名（${savedViews.value.map((v) => v.name).join(' / ')}）`,
      '管理视图',
      { inputValue: '', confirmButtonText: '删除', cancelButtonText: '关闭' }
    ).catch(() => ({ value: '' }))
    if (!picked) return
    const found = savedViews.value.find((v) => v.name === picked.trim())
    if (!found) return ElMessage.warning('未找到该视图')
    await deleteView(found)
    return
  }
  if (cmd.startsWith('apply:')) {
    const v = savedViews.value.find((x) => x.id === cmd.slice(6))
    if (v) applyView(v)
  }
}

// ========== 计算属性 ==========
const filteredOrders = computed(() => {
  let arr = [...allOrders.value]
  const f = filter.value
  if (f.orderNo) arr = arr.filter(o => o.orderNo?.includes(f.orderNo))
  if (f.memberKw) {
    const k = f.memberKw.toLowerCase()
    arr = arr.filter(o =>
      (o.memberName || '').toLowerCase().includes(k) ||
      (o.memberPhone || '').includes(k))
  }
  if (f.status) arr = arr.filter(o => o.status === f.status)
  if (f.dateRange && f.dateRange.length === 2) {
    const [s, e] = f.dateRange
    arr = arr.filter(o => {
      const t = (o.createTime || '').slice(0, 10)
      return t >= s && t <= e
    })
  }
  // 按下单时间倒序
  arr.sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
  return arr
})

const pagedOrders = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return filteredOrders.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const all = filteredOrders.value
  const paid = all.filter(o => o.status === 2)
  const refunded = all.filter(o => o.status === 3)
  const revenue = paid.reduce((sum, o) => sum + Number(o.paidAmount || 0), 0)
  return {
    total: all.length,
    pending: all.filter(o => o.status === 1).length,
    paid: paid.length,
    refunded: refunded.length,
    refundAmount: refunded.reduce((sum, o) => sum + Number(o.refundAmount || 0), 0),
    revenue
  }
})

// 状态 Tab 数字（基于**当前已日期筛选**后的数据计算）
const tabCount = (status: number) => {
  const f = filter.value
  let arr = [...allOrders.value]
  if (f.dateRange && f.dateRange.length === 2) {
    const [s, e] = f.dateRange
    arr = arr.filter(o => {
      const t = (o.createTime || '').slice(0, 10)
      return t >= s && t <= e
    })
  }
  return arr.filter(o => o.status === status).length
}

// "全部" Tab 的总数（用 allOrders 总数 - 其他状态之和，与 stats.total 不一定一致）
const tabCountAll = computed(() => {
  let arr = [...allOrders.value]
  const f = filter.value
  if (f.dateRange && f.dateRange.length === 2) {
    const [s, e] = f.dateRange
    arr = arr.filter(o => {
      const t = (o.createTime || '').slice(0, 10)
      return t >= s && t <= e
    })
  }
  return arr.length
})

// 状态 Tab 与 filter.status 的映射：
// - UI 上"全部" 用 -1（避免 el-radio-button value=null 触发 ElementPlus 弃用警告）
// - filter.status 用 null 表示"全部"
const statusTabValue = computed(() => filter.value.status ?? -1)
const onStatusTabChange = (val: number) => {
  filter.value.status = val === -1 ? null : val
}

// 快捷日期
const setQuickDate = (q: string) => {
  quickDate.value = q
  const today = new Date()
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  if (q === 'today') {
    filter.value.dateRange = [fmt(today), fmt(today)]
  } else if (q === 'week') {
    const start = new Date(today)
    start.setDate(today.getDate() - today.getDay())
    filter.value.dateRange = [fmt(start), fmt(today)]
  } else if (q === 'month') {
    const start = new Date(today.getFullYear(), today.getMonth(), 1)
    filter.value.dateRange = [fmt(start), fmt(today)]
  } else {
    filter.value.dateRange = null
  }
}

// ========== 加载 ==========
const load = async () => {
  loading.value = true
  try {
    // 默认拉最近 200 单（够用）
    const res: any = await orderApi.page({ pageNum: 1, pageSize: 200 })
    allOrders.value = res.data?.records || res.data?.list || res.records || res.list || []
  } catch (e: any) {
    ElMessage.error('加载订单失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const reset = () => {
  filter.value = { orderNo: '', memberKw: '', status: null, dateRange: null }
  pageNum.value = 1
}

// ========== 详情弹窗 ==========
const detailVisible = ref(false)
const activeOrder = ref<any>(null)

const showDetail = async (row: any) => {
  try {
    const res: any = await orderApi.detail(row.id)
    activeOrder.value = res.data || row
    detailVisible.value = true
  } catch {
    activeOrder.value = row
    detailVisible.value = true
  }
}

// ========== 退款（R2.0-C T-C05：使用统一 RefundDialog 组件） ==========
const refundVisible = ref(false)

const openRefund = (row: any) => {
  activeOrder.value = row
  refundVisible.value = true
}

const onRefunded = () => {
  refundVisible.value = false
  load()
}

// ========== 撤销 ==========
const voidVisible = ref(false)
const voidReason = ref('')
const voiding = ref(false)

const openVoid = (row: any) => {
  ElMessageBox.confirm(
    `确定撤销订单 ${row.orderNo}？撤销后无法恢复`,
    '撤销确认',
    { type: 'warning', confirmButtonText: '撤销订单', cancelButtonText: '取消' }
  ).then(() => {
    activeOrder.value = row
    voidReason.value = ''
    voidVisible.value = true
  }).catch(() => { /* 用户取消 */ })
}

const confirmVoid = async () => {
  voiding.value = true
  try {
    await orderApi.voidOrder(activeOrder.value.id, voidReason.value)
    ElMessage.success('已撤销订单')
    voidVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error('撤销失败：' + (e.message || '未知错误'))
  } finally {
    voiding.value = false
  }
}

// ========== 重打小票 ==========
const reprint = (row: any) => {
  const win = window.open('', '_blank', 'width=380,height=720')
  if (!win) return
  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>小票 ${row.orderNo}</title>
    <style>
      body { font-family: 'Courier New', monospace; width: 320px; padding: 12px; }
      h2 { text-align: center; margin: 0 0 8px; font-size: 18px; }
      .center { text-align: center; font-size: 12px; }
      hr { border: none; border-top: 1px dashed #000; margin: 6px 0; }
      .row { display: flex; justify-content: space-between; font-size: 13px; margin: 2px 0; }
      .total { font-size: 16px; font-weight: bold; }
      .footer { margin-top: 12px; text-align: center; font-size: 12px; }
      @media print { body { width: 80mm; } }
    </style></head><body>
    <h2>美业管理 · 收银小票</h2>
    <div class="center">订单号：${row.orderNo}</div>
    <div class="center">${formatDateTime(row.createTime)}</div>
    <hr>
    <div class="row"><span>会员</span><span>${row.memberName || '散客'}</span></div>
    <div class="row"><span>收银员</span><span>${row.cashierName || '-'}</span></div>
    <hr>
    <div class="row"><span>订单金额</span><span>¥${Number(row.totalAmount).toFixed(2)}</span></div>
    ${row.discountAmount > 0 ? `<div class="row"><span>折扣</span><span>-¥${Number(row.discountAmount).toFixed(2)}</span></div>` : ''}
    <div class="row total"><span>应付</span><span>¥${Number(row.finalAmount).toFixed(2)}</span></div>
    <div class="row"><span>实付</span><span>¥${Number(row.paidAmount || 0).toFixed(2)}</span></div>
    ${row.refundAmount > 0 ? `<div class="row"><span>已退</span><span>¥${Number(row.refundAmount).toFixed(2)}</span></div>` : ''}
    <hr>
    <div class="footer">★ 感谢您的光临 ★</div>
    <div class="footer" style="margin-top: 8px">
      <button onclick="window.print()" style="padding: 6px 16px">打印</button>
      <button onclick="window.close()" style="padding: 6px 16px; margin-left: 8px">关闭</button>
    </div>
    </body></html>`
  win.document.write(html)
  win.document.close()
}

// ========== 导出 CSV ==========
const exportCSV = () => {
  const headers = ['订单号', '会员', '手机号', '状态', '原价', '折扣', '应付', '实付', '退款', '收银员', '下单时间', '支付时间']
  const rows = filteredOrders.value.map(o => [
    o.orderNo, o.memberName || '散客', o.memberPhone || '',
    STATUS[o.status] || o.status,
    Number(o.totalAmount).toFixed(2), Number(o.discountAmount || 0).toFixed(2),
    Number(o.finalAmount).toFixed(2), Number(o.paidAmount || 0).toFixed(2),
    Number(o.refundAmount || 0).toFixed(2),
    o.cashierName || '', formatDateTime(o.createTime), formatDateTime(o.payTime)
  ])
  const csv = [headers, ...rows].map(r =>
    r.map(v => {
      const s = String(v ?? '')
      return s.includes(',') || s.includes('"') ? `"${s.replace(/"/g, '""')}"` : s
    }).join(',')
  ).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `订单列表_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${filteredOrders.value.length} 条订单`)
}

onMounted(() => {
  loadPersistedFilter()
  loadSavedViews()
  load()
  window.addEventListener('keydown', handleGlobalKey)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKey)
})

// ========== 打印当前页（批量打印小票） ==========
const printCurrentPage = () => {
  if (pagedOrders.value.length === 0) {
    ElMessage.warning('当前页没有订单可打印')
    return
  }
  const win = window.open('', '_blank', 'width=400,height=800')
  if (!win) {
    ElMessage.error('请允许浏览器弹窗')
    return
  }
  const itemsHtml = pagedOrders.value.map((row, idx) => `
    <div style="page-break-after: ${idx < pagedOrders.value.length - 1 ? 'always' : 'auto'}; padding: 16px; border-bottom: 2px dashed #000;">
      <div style="text-align: center; font-size: 18px; font-weight: bold; margin-bottom: 8px;">美业管理 · 收银小票</div>
      <div style="text-align: center; font-size: 12px;">订单号：${row.orderNo}</div>
      <div style="text-align: center; font-size: 12px;">${formatDateTime(row.createTime)}</div>
      <hr>
      <div style="display: flex; justify-content: space-between; font-size: 13px;"><span>会员</span><span>${row.memberName || '散客'}</span></div>
      <div style="display: flex; justify-content: space-between; font-size: 13px;"><span>收银员</span><span>${row.cashierName || '-'}</span></div>
      <hr>
      <div style="display: flex; justify-content: space-between;"><span>订单金额</span><span>¥${Number(row.totalAmount).toFixed(2)}</span></div>
      ${row.discountAmount > 0 ? `<div style="display: flex; justify-content: space-between;"><span>折扣</span><span>-¥${Number(row.discountAmount).toFixed(2)}</span></div>` : ''}
      <div style="display: flex; justify-content: space-between; font-weight: bold; font-size: 16px;"><span>应付</span><span>¥${Number(row.finalAmount).toFixed(2)}</span></div>
      <div style="display: flex; justify-content: space-between;"><span>实付</span><span>¥${Number(row.paidAmount || 0).toFixed(2)}</span></div>
      ${row.refundAmount > 0 ? `<div style="display: flex; justify-content: space-between;"><span>已退</span><span>¥${Number(row.refundAmount).toFixed(2)}</span></div>` : ''}
      <hr>
      <div style="text-align: center; font-size: 12px;">★ 感谢您的光临 ★</div>
    </div>
  `).join('')
  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>批量打印 ${pagedOrders.value.length} 张小票</title>
    <style>
      body { font-family: 'Courier New', monospace; max-width: 320px; margin: 0 auto; }
      hr { border: none; border-top: 1px dashed #000; margin: 6px 0; }
      @media print { body { max-width: 80mm; } @page { margin: 8mm; } }
    </style></head><body>
    ${itemsHtml}
    <div style="text-align: center; margin-top: 16px;">
      <button onclick="window.print()" style="padding: 8px 24px; font-size: 14px;">打印全部 (${pagedOrders.value.length})</button>
      <button onclick="window.close()" style="padding: 8px 24px; font-size: 14px; margin-left: 8px;">关闭</button>
    </div>
    </body></html>`
  win.document.write(html)
  win.document.close()
}
</script>

<style scoped>
.orders-page { padding: 16px; }
.banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff; padding: 24px; border-radius: 10px;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.banner h2 { margin: 0; font-size: 22px; }
.banner p { margin: 4px 0 0; opacity: 0.85; font-size: 13px; }
.banner-right { display: flex; gap: 8px; }

/* 统计卡片 */
.stat-row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 150px; padding: 14px 16px;
  border-radius: 8px; color: #fff;
}
.stat-label { font-size: 13px; opacity: 0.9; }
.stat-num   { font-size: 26px; font-weight: bold; margin: 4px 0; line-height: 1.2; }
.stat-card small { font-size: 12px; opacity: 0.8; }
.s-1 { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.s-2 { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
.s-3 { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.s-4 { background: linear-gradient(135deg, #6b7280 0%, #374151 100%); }
.s-5 { background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%); }

.filter-card { margin-bottom: 16px; }

/* 状态 Tab + 快捷日期 */
.tab-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.tab-bar .badge {
  display: inline-block; margin-left: 4px;
  padding: 0 6px; border-radius: 8px;
  font-size: 11px; font-weight: 600;
  background: rgba(255,255,255,0.18); color: #fff;
}
/* 主题色 badge（与 status.color 一致，醒目） */
.b-warning { background: #f59e0b; color: #fff; }
.b-success { background: #10b981; color: #fff; }
.b-info    { background: #909399; color: #fff; }
.b-danger  { background: #ef4444; color: #fff; }
.b-default { background: #fff; color: #667eea; border: 1px solid #667eea; }
.tab-bar :deep(.el-radio-button__inner) { padding: 8px 14px; }
.tab-bar :deep(.el-radio-button.is-active .el-radio-button__inner) { background: #409eff; border-color: #409eff; color: #fff; }
.tab-bar .quick-date { display: flex; gap: 8px; }

/* 表格工具栏 */
.table-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; padding: 8px 0; flex-wrap: wrap; gap: 8px;
}
.table-toolbar .hint { color: #606266; font-size: 13px; display: inline-flex; align-items: center; gap: 4px; }
.table-toolbar .hint .scroll-hint { color: #909399; font-size: 12px; margin-left: 4px; }
.table-toolbar .col-toggle { display: inline-flex; flex-wrap: wrap; gap: 4px; }
.table-toolbar .col-toggle :deep(.el-checkbox) { margin-right: 4px; }
.table-toolbar .col-toggle :deep(.el-checkbox__label) { padding-left: 4px; font-size: 12px; }

/* 订单号高亮 */
.order-no { font-family: 'Courier New', monospace; font-weight: 600; color: #409eff; }

/* Ctrl+K 快捷键角标 */
.kbd-hint {
  display: inline-block; font-family: 'Courier New', monospace;
  font-size: 10px; padding: 1px 5px;
  background: #f4f4f5; color: #909399;
  border: 1px solid #dcdfe6; border-radius: 3px;
  line-height: 1.4;
}

.stat-card { cursor: pointer; transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
.stat-card.active { outline: 3px solid #fff; box-shadow: 0 0 0 3px #409eff, 0 4px 12px rgba(64,158,255,0.4); }

.muted { color: #c0c4cc; }

.member-cell strong { display: block; font-size: 13px; }
.member-cell small { color: #909399; font-size: 12px; }
.amount .line-through { text-decoration: line-through; color: #909399; font-size: 12px; margin-right: 4px; }
.amount .final { color: #ef4444; font-size: 14px; }
.refund { color: #909399; }

/* 详情弹窗 */
.detail-body { font-size: 14px; }
.info-row {
  display: flex; gap: 12px; padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.info-label { width: 80px; color: #909399; }
.amount-summary {
  padding: 12px 16px; background: #f8f9fb; border-radius: 6px;
  font-size: 14px;
}
.amount-summary > div { display: flex; justify-content: space-between; padding: 4px 0; }
.amount-summary .hl { color: #ef4444; font-size: 16px; border-top: 1px solid #e0e0e0; padding-top: 8px; margin-top: 4px; }
.amount-summary .refund { color: #909399; }

/* 退款 */
.refund-info { padding: 0 0 16px; font-size: 14px; }
.refund-info > div { padding: 4px 0; }

.toolbar-spacer { flex: 1; }
</style>
