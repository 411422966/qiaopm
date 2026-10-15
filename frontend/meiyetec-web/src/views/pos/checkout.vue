<template>
  <div class="pos-page">
    <!-- 顶部状态条 -->
    <div class="top-bar">
      <div class="brand">
        <el-icon><Money /></el-icon>
        <span>收银台</span>
      </div>
      <div class="stat-row">
        <span class="stat-chip">今日订单 <b>{{ stats.todayOrderCount ?? 0 }}</b></span>
        <span class="stat-chip green">今日营收 <b>¥{{ Number(stats.todayRevenue ?? 0).toFixed(2) }}</b></span>
        <span class="stat-chip orange">进行中 <b>{{ stats.pendingCount ?? 0 }}</b></span>
        <el-button v-if="lastPaidOrder" type="success" size="small" @click="printReceipt">🖨 打印小票</el-button>
      </div>
    </div>

    <div class="pos-grid">
      <!-- 左侧：服务选择 -->
      <el-card shadow="never" class="left-pane">
        <div class="pane-title">
          <span><el-icon><Goods /></el-icon> 选择服务/产品</span>
          <el-input v-model="svcKw" placeholder="搜索服务" clearable style="width: 200px" :prefix-icon="Search" />
        </div>
        <div class="svc-list">
          <div v-for="s in filteredServices" :key="s.id" class="svc-item" @click="addItem(s)">
            <div class="svc-name">{{ s.serviceName }}</div>
            <div class="svc-meta">
              <span class="price">¥{{ Number(s.unitPrice).toFixed(2) }}</span>
              <span v-if="s.memberPrice" class="m-price">¥{{ Number(s.memberPrice).toFixed(2) }}</span>
              <span v-if="s.duration" class="dur">{{ s.duration }}'</span>
            </div>
          </div>
          <el-empty v-if="!filteredServices.length" description="暂无服务" />
        </div>
      </el-card>

      <!-- 中间：订单明细 -->
      <el-card shadow="never" class="mid-pane">
        <div class="pane-title">
          <span v-if="member">
            <el-icon><User /></el-icon>
            <b>{{ member.name }}</b>
            <span class="muted">{{ member.phone }}</span>
            <!-- P0-MONEY-002：余额已通过 /wallet/{memberId} 拉取 SSOT 权威值 -->
            <el-tooltip content="余额来自 crm_wallet.balance（SSOT）" placement="top">
              <span class="muted">余额 ¥{{ Number(member.balance).toFixed(2) }}</span>
            </el-tooltip>
          </span>
          <span v-else class="muted">
            <el-icon><User /></el-icon> 请先选择会员
          </span>
          <el-button v-if="member" size="small" link type="danger" @click="clearMember">移除</el-button>        </div>

        <!-- 会员选择 -->
        <div class="member-bar">
          <el-input v-model="memberKw" placeholder="输入手机号 / 姓名查找" clearable @input="onMemberSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button :icon="User" @click="showMemberPicker = true">选择会员</el-button>
        </div>

        <!-- M14 定价实验分组提示 -->
        <div v-if="member && expVariant" class="exp-badge-row">
          <el-tag :type="expVariant === 'A' ? 'info' : 'success'" size="small">
            A/B 实验 · 您的分组：<b>{{ expVariant }}</b> 组
            <span v-if="expVariant === 'B' && expDiscount > 0" style="margin-left:6px">
              → 预计折扣 {{ expDiscount }}%
            </span>
          </el-tag>
          <span class="exp-hint">定价实验优惠将在结算时自动应用</span>
        </div>

        <!-- 未选会员提示 -->
        <el-alert v-if="!member" type="info" :closable="false" show-icon
                  title="请先选择会员，再添加服务"
                  description="支持手机号 / 姓名搜索，也可以点击右侧按钮选择会员"
                  style="margin-bottom: 8px" />

        <!-- 订单明细表 -->
        <el-table :data="items" border size="small" empty-text="尚未添加服务" style="width: 100%; flex: 1; min-height: 0;" height="auto">
          <el-table-column type="index" label="#" width="40" align="center" fixed />
          <el-table-column prop="serviceName" label="服务/产品" min-width="110" fixed />
          <el-table-column label="技师" width="120">
            <template #default="{ row }">
              <el-select v-model="row.employeeId" placeholder="选择技师" clearable size="small" filterable style="width: 110px" @change="(v: any) => onEmployeeChange(row, v)">
                <el-option
                  v-for="e in employees" :key="e.id" :label="e.employeeName" :value="e.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="单价" width="80" align="right">
            <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="100" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="1" :max="99" size="small" :step="1" controls-position="right" style="width: 90px" @change="recalc" />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="80" align="right">
            <template #default="{ row }">¥{{ Number(row.totalPrice).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="折扣" width="140">
            <template #default="{ row }">
              <div style="display:flex; align-items:center; gap:4px">
                <el-input-number v-model="row.discountAmount" :min="0" :precision="2" :step="10" size="small" controls-position="right" style="flex:1" @change="recalc" />
                <el-dropdown trigger="click" size="small" @command="(p: any) => applyPctDiscount(row, Number(p))">
                  <el-button size="small" style="flex-shrink:0">折%</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="0.05">5%</el-dropdown-item>
                      <el-dropdown-item command="0.10">10%</el-dropdown-item>
                      <el-dropdown-item command="0.15">15%</el-dropdown-item>
                      <el-dropdown-item command="0.20">20%</el-dropdown-item>
                      <el-dropdown-item command="0.30">30%</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="实收" width="90" align="right">
            <template #default="{ row }">
              <b style="color: #f56c6c">¥{{ Number(row.finalPrice).toFixed(2) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button size="small" type="danger" link @click="items.splice($index, 1); recalc()">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 备注 -->
        <div class="remark-row">
          <span>订单备注：</span>
          <el-input v-model="remark" placeholder="可选" style="flex: 1" />
        </div>

        <!-- 金额汇总 -->
        <div class="totals">
          <div class="totals-row">
            <span>原价合计</span>
            <b>¥{{ Number(totals.total).toFixed(2) }}</b>
          </div>

          <!-- 整单折扣输入（M02） -->
          <div class="totals-row order-discount-row">
            <span>整单折扣</span>
            <div style="display:flex; align-items:center; gap:6px">
              <el-input-number
                v-model="orderDiscountInput"
                :min="0"
                :precision="2"
                :step="10"
                size="small"
                controls-position="right"
                style="width:120px"
                placeholder="0.00"
                @change="onOrderDiscountChange" />
              <el-dropdown trigger="click" size="small" @command="applyOrderPctDiscount">
                <el-button size="small">折扣%</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="0.05">95折（5%）</el-dropdown-item>
                    <el-dropdown-item command="0.10">9折（10%）</el-dropdown-item>
                    <el-dropdown-item command="0.15">85折（15%）</el-dropdown-item>
                    <el-dropdown-item command="0.20">8折（20%）</el-dropdown-item>
                    <el-dropdown-item command="0.30">7折（30%）</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button v-if="orderDiscountAmount > 0" size="small" type="danger" plain @click="clearOrderDiscount">清除</el-button>
            </div>
          </div>

          <div v-if="Number(totals.discount) + Number(orderDiscountAmount) > 0" class="totals-row">
            <span>折扣合计</span>
            <b class="discount">-¥{{ (Number(totals.discount) + Number(orderDiscountAmount)).toFixed(2) }}</b>
          </div>
          <div class="totals-row grand">
            <span>应收</span>
            <b class="final">¥{{ (Number(totals.final) - Number(orderDiscountAmount)).toFixed(2) }}</b>
          </div>
          <div v-if="items.length === 0" class="totals-row muted" style="font-size:12px; color:#909399; padding-top:4px">
            请先添加服务
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="actions">
          <el-button @click="resetOrder" :icon="Refresh">清空</el-button>
          <el-button @click="voidDialog = true" :disabled="!currentOrderId || currentStatus !== 1" :icon="CircleClose">撤销整单</el-button>
          <div class="spacer"></div>
          <el-button type="warning" @click="saveDraft" :icon="Document" :disabled="!items.length">保存草稿</el-button>
          <el-button type="primary" @click="goPay" :icon="Wallet" :disabled="!items.length || !member">去支付</el-button>
        </div>
      </el-card>

      <!-- 右侧：最近订单（可折叠） -->
      <el-card shadow="never" class="right-pane" :class="{ collapsed: rightCollapsed }">
        <div class="pane-title">
          <span v-if="!rightCollapsed"><el-icon><Clock /></el-icon> 最近订单</span>
          <span v-else><el-icon><Clock /></el-icon> 订单</span>
          <el-button-group size="small">
            <el-button size="small" link @click="loadToday" v-if="!rightCollapsed">刷新</el-button>
            <el-button size="small" link @click="rightCollapsed = !rightCollapsed">
              <el-icon><ArrowRight v-if="!rightCollapsed" /><ArrowLeft v-else /></el-icon>
            </el-button>
          </el-button-group>
        </div>
        <div class="recent">
          <div v-for="o in recentOrders" :key="o.id" class="recent-item" @click="openOrder(o)">
            <div class="recent-top">
              <span class="no">{{ o.orderNo }}</span>
              <el-tag :type="statusType(o.status)" size="small">{{ statusLabel(o.status) }}</el-tag>
            </div>
            <div class="recent-meta">
              <span>{{ o.memberName || '散客' }}</span>
              <b>¥{{ Number(o.finalAmount).toFixed(2) }}</b>
            </div>
          </div>
          <el-empty v-if="!recentOrders.length" description="暂无订单" />
        </div>
      </el-card>
    </div>

    <!-- 窄屏：浮动按钮打开"最近订单" -->
    <el-button class="recent-fab" :class="{ 'show-when-collapsed': rightCollapsed }" type="primary" @click="recentDrawer = true">
      <el-icon><Clock /></el-icon>
      <span class="recent-fab-num" v-if="recentOrders.length">{{ recentOrders.length }}</span>
    </el-button>
    <el-drawer v-model="recentDrawer" title="最近订单" direction="rtl" size="320px">
      <div class="recent">
        <div v-for="o in recentOrders" :key="o.id" class="recent-item" @click="openOrder(o); recentDrawer = false">
          <div class="recent-top">
            <span class="no">{{ o.orderNo }}</span>
            <el-tag :type="statusType(o.status)" size="small">{{ statusLabel(o.status) }}</el-tag>
          </div>
          <div class="recent-meta">
            <span>{{ o.memberName || '散客' }}</span>
            <b>¥{{ Number(o.finalAmount).toFixed(2) }}</b>
          </div>
        </div>
        <el-empty v-if="!recentOrders.length" description="暂无订单" />
      </div>
    </el-drawer>

    <!-- 底部快捷键提示 -->
  <div class="kbd-hint">
    <span><kbd>F2</kbd> 选会员</span>
    <span><kbd>F4</kbd> 清空</span>
    <span><kbd>F8</kbd> 草稿</span>
    <span><kbd>F9</kbd> 支付</span>
    <span><kbd>Esc</kbd> 关弹窗</span>
  </div>

  <!-- 会员选择弹窗 -->
    <el-dialog v-model="showMemberPicker" title="选择会员" width="640px">
      <el-input v-model="memberPickerKw" placeholder="搜索会员（手机号/姓名）" clearable @input="onMemberSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-table :data="filteredMembers" max-height="380" highlight-current-row
                @row-click="pickMember" style="margin-top: 12px">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="levelName" label="等级" width="100" />
        <el-table-column label="余额" align="right">
          <template #default="{ row }">¥{{ Number(row.balance || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="累计消费" align="right">
          <template #default="{ row }">¥{{ Number(row.totalConsume || 0).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 支付弹窗 -->
    <el-dialog v-model="payDialog" title="收银结算" width="620px" :close-on-click-modal="false">
      <div class="pay-summary">
        <div class="pay-row"><span>会员</span><b>{{ member?.name }}</b></div>
        <div class="pay-row"><span>应付</span><b class="final">¥{{ Number(totals.final).toFixed(2) }}</b></div>
        <div class="pay-row"><span>已付</span><b>¥{{ Number(paidAmount).toFixed(2) }}</b></div>
        <div class="pay-row"><span>剩余</span><b class="remain">¥{{ Math.max(0, Number(totals.final - paidAmount)).toFixed(2) }}</b></div>
      </div>

      <!-- 混合支付：每点一次"添加支付"累加一行 -->
      <div class="pay-methods">
        <div class="method-label">添加支付方式（支持多种组合）</div>
        <div class="add-pay-row">
          <el-select v-model="newPayMethod" style="width: 140px">
            <el-option label="现金" value="cash" />
            <el-option label="储值" value="wallet" :disabled="(member?.balance || 0) < remainAmount" />
            <el-option label="微信" value="wechat" />
            <el-option label="支付宝" value="alipay" />
            <el-option label="银联" value="unionpay" />
            <el-option label="数字人民币(软)" value="e_cny_soft" />
            <el-option label="数字人民币(硬)" value="e_cny_hard" />
            <el-option label="次卡核销" value="package" />
          </el-select>
          <el-input-number v-model="newPayAmount" :min="0" :max="remainAmount + newPayAmount" :precision="2" :step="50" style="width: 140px" />
          <el-button type="primary" :icon="Plus" @click="addPayLine" :disabled="!newPayMethod || newPayAmount <= 0 && newPayMethod !== 'package'">添加</el-button>
        </div>
      </div>

      <div class="pay-history" v-if="payLines.length">
        <div class="method-label">支付明细（点击删除可移除）</div>
        <el-table :data="payLines" size="small" border>
          <el-table-column label="方式" width="140">
            <template #default="{ row }">{{ methodLabel(row.method) }}</template>
          </el-table-column>
          <el-table-column label="金额" align="right" width="140">
            <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" @click="payLines.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmPay" :loading="paying" :disabled="!payLines.length || payTotal > remainAmount + 0.01">
          确认支付 {{ payLines.length ? `(¥${payTotal.toFixed(2)})` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 撤销原因 -->
    <el-dialog v-model="voidDialog" title="撤销订单" width="420px">
      <el-input v-model="voidReason" placeholder="撤销原因（可选）" type="textarea" :rows="3" />
      <template #footer>
        <el-button @click="voidDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmVoid">确认撤销</el-button>
      </template>
    </el-dialog>

    <!-- 退款：R2.0-C 升级版 -->
    <RefundDialog v-model="refundDialog" :order="activeOrder" @success="onRefundSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, User, Wallet, Refresh, Document, Money, Goods, Clock, CircleClose, Plus } from '@element-plus/icons-vue'
import { orderApi, serviceApi } from '@/api/pos'
import { memberApi, employeeApi, walletApi } from '@/api/basic'
import { couponApi } from '@/api/coupon'
import { pricingExperimentApi } from '@/api/pricing-experiment'
import { useRouter } from 'vue-router'
import RefundDialog from '@/components/RefundDialog.vue'

const router = useRouter()

// 状态数据
const stats = ref<any>({})
const services = ref<any[]>([])
const svcKw = ref('')
// 员工（技师）列表
const employees = ref<any[]>([])
let employeesLoaded = false
const loadEmployees = async (force = false) => {
  if (employeesLoaded && !force) return
  try {
    const res: any = await employeeApi.list()
    employees.value = res.data || res || []
    employeesLoaded = true
  } catch {}
}
const onEmployeeChange = (row: any, id: any) => {
  const emp = employees.value.find(e => e.id === id)
  row.employeeName = emp?.employeeName || null
}
const filteredServices = computed(() => {
  if (!svcKw.value) return services.value
  const kw = svcKw.value.toLowerCase()
  return services.value.filter(s =>
    (s.serviceName || '').toLowerCase().includes(kw) ||
    (s.serviceCode || '').toLowerCase().includes(kw))
})

// 会员
const member = ref<any>(null)
const memberKw = ref('')
const showMemberPicker = ref(false)
const memberPickerKw = ref('')
const allMembers = ref<any[]>([])
const filteredMembers = computed(() => {
  const k = memberPickerKw.value
  if (!k) return allMembers.value.slice(0, 50)
  const kw = k.toLowerCase()
  return allMembers.value.filter(m =>
    (m.name || '').toLowerCase().includes(kw) ||
    (m.phone || '').toLowerCase().includes(kw) ||
    String(m.id || '').includes(k))
})

// 订单明细
const items = ref<any[]>([])
const remark = ref('')
const currentOrderId = ref<number | null>(null)
const currentStatus = ref(0)
const paidAmount = ref(0)
const paymentHistory = ref<any[]>([])

// M02 整单折扣（独立于每行 per-item discount，保存订单时随单或调 applyDiscount）
const orderDiscountInput = ref<number>(0)
const orderDiscountAmount = ref<number>(0)
const applyingOrderDiscount = ref(false)

// 金额合计
const totals = computed(() => {
  let total = 0, discount = 0, final = 0
  for (const i of items.value) {
    const q = Number(i.quantity) || 1
    const up = Number(i.unitPrice) || 0
    const da = Number(i.discountAmount) || 0
    const tp = up * q
    const fp = Math.max(0, tp - da)
    i.totalPrice = tp
    i.finalPrice = fp
    total += tp
    discount += da
    final += fp
  }
  return { total, discount, final }
})

// 操作按钮
const resetOrder = () => {
  items.value = []
  remark.value = ''
  currentOrderId.value = null
  currentStatus.value = 0
  paidAmount.value = 0
  paymentHistory.value = []
}

const clearMember = () => {
  member.value = null
  selectedCoupon.value = null
  memberCoupons.value = []
  expVariant.value = null
  expDiscount.value = BigDecimal.ZERO
}

// ==================== M14 定价实验查询 ====================
const expVariant = ref<string | null>(null)
const expDiscount = ref<number>(0)

async function loadPricingExperiment() {
  expVariant.value = null
  expDiscount.value = 0
  if (!member.value) return
  try {
    const res: any = await pricingExperimentApi.page({ pageNum: 1, pageSize: 10, status: 'RUNNING' })
    const running = res.data?.records || []
    if (running.length > 0 && member.value.id) {
      // 确定性分组（与后端 computeVariant 算法一致）
      const m = member.value
      const key = running[0].expKey || ''
      const hash = Math.abs([...key].reduce((h, c) => ((h << 5) - h + c.charCodeAt(0)) | 0, 0))
      const bucket = Math.abs((m.id * 31 + hash) % 1000)
      const ratio = running[0].controlTrafficRatio || 0.5
      const abRatio = running[0].controlABRatio || 0.5
      const ctrlEnd = Math.round(ratio * 1000)
      const aEnd = Math.round(ctrlEnd * abRatio)
      expVariant.value = bucket < aEnd ? 'A' : 'B'
      if (expVariant.value === 'B' && running[0].groupBMode) {
        const mode = running[0].groupBMode
        const val = running[0].groupBValue || 0
        if (mode === 'PERCENT') expDiscount.value = val // 预期折扣%
        else if (mode === 'FIXED') expDiscount.value = -val // 减免金额
      }
    }
  } catch (e) { /* 实验查询失败静默 */ }
}

// ==================== COUPON-008 会员优惠券查询 ====================
const memberCoupons = ref<any[]>([])
const selectedCoupon = ref<any | null>(null)
const couponDiscount = computed(() => {
  const c = selectedCoupon.value
  if (!c) return 0
  const t = Number(totals.value.final) || 0
  const type = c.type || c.couponType
  if (type === 1 || type === 'CASH') { // 满减
    const min = Number(c.minAmount || c.minConsume || 0)
    if (t < min) return 0
    return Number(c.ruleValue || c.discountAmount || 0)
  }
  if (type === 2 || type === 'PERCENT') { // 折扣
    const rate = Number(c.discountRate || 0)
    return Math.round(t * (1 - rate) * 100) / 100
  }
  return 0
})
async function loadMemberCoupons() {
  if (!member.value) { memberCoupons.value = []; selectedCoupon.value = null; return }
  try {
    const r: any = await couponApi.memberCoupons(member.value.id, { pageNum: 1, pageSize: 50, status: 1 })
    memberCoupons.value = r.data?.records || []
  } catch (e) {
    memberCoupons.value = []
  }
}
function selectMemberCoupon(cg: any) {
  selectedCoupon.value = cg
}
function clearCoupon() {
  selectedCoupon.value = null
}

// 会员列表缓存（避免每次按键都请求后端）
let membersCache: any[] | null = null
let membersCacheAt = 0
const MEMBERS_TTL = 30_000 // 30 秒
let memberSearchTimer: number | null = null

const loadAllMembers = async (force = false) => {
  const now = Date.now()
  if (!force && membersCache && now - membersCacheAt < MEMBERS_TTL) {
    allMembers.value = membersCache
    return
  }
  try {
    const res: any = await memberApi.list()
    membersCache = res.data || res || []
    membersCacheAt = now
    allMembers.value = membersCache
  } catch {}
}

const onMemberSearch = () => {
  // 250ms 防抖：避免每个键击都触发请求
  if (memberSearchTimer) window.clearTimeout(memberSearchTimer)
  memberSearchTimer = window.setTimeout(() => loadAllMembers(false), 250)
}

const pickMember = async (row: any) => {
  member.value = row
  showMemberPicker.value = false
  // COUPON-008：加载该会员的优惠券
  await loadMemberCoupons()
  // M14：查询定价实验分组
  loadPricingExperiment()
  // P0-MONEY-002：拉取 crm_wallet.balance 作为 SSOT 权威值
  // 前端展示的余额以 /wallet/{memberId} 为准，member.balance 是只读同步字段
  try {
    const res: any = await walletApi.getBalance(row.id)
    if (res.code === 200 && res.data) {
      member.value.balance = Number(res.data.balance)
    }
  } catch (e) {
    // 拉取失败时保留 member.balance（兜底，不阻断选会员流程）
    console.warn('[P0-MONEY-002] 拉取钱包余额失败，使用 member.balance 兜底', e)
  }
}

// 加服务
const addItem = (s: any) => {
  const list = Number(s.unitPrice)
  const memberP = s.memberPrice ? Number(s.memberPrice) : null
  const up = member.value && memberP != null ? memberP : list
  items.value.push({
    serviceId: s.id,
    serviceName: s.serviceName,
    quantity: 1,
    unitPrice: up,
    unitPriceOld: list,         // 原价（未打折前），用于切换会员时比对
    totalPrice: up,
    discountAmount: 0,
    finalPrice: up,
    employeeId: null,
    employeeName: null
  })
}

// 选中会员后，更新已有 item 的会员价；
// 清空会员后，恢复原价
watch(member, (m) => {
  for (const it of items.value) {
    const svc = services.value.find(s => s.id === it.serviceId)
    if (!svc) continue
    // 始终把当前价存到 unitPriceOld，便于后续切换会员时还原
    if (it.unitPriceOld == null) it.unitPriceOld = Number(svc.unitPrice)
    // 始终把 unitPrice 重算为"当前会员视角下的单价"
    it.unitPrice = m && svc.memberPrice ? Number(svc.memberPrice) : Number(svc.unitPrice)
  }
})

const recalc = () => { /* computed 自动计算 */ }

// 快速按比例设置折扣
const applyPctDiscount = (row: any, pct: number) => {
  const tp = Number(row.unitPrice) * Number(row.quantity)
  row.discountAmount = Math.round(tp * pct * 100) / 100
}

// M02 整单折扣 — 直接金额输入
const onOrderDiscountChange = () => {
  let v = Number(orderDiscountInput.value) || 0
  if (v < 0) v = 0
  // 上限：不能超过订单合计
  const max = Number(totals.value.final) || 0
  if (v > max) {
    ElMessage.warning(`整单折扣不能超过 ¥${max.toFixed(2)}`)
    orderDiscountInput.value = max
    v = max
  }
  orderDiscountAmount.value = v
}

// M02 整单折扣 — 百分比快捷
const applyOrderPctDiscount = (pct: string) => {
  const base = Number(totals.value.final) || 0
  const v = Math.round(base * Number(pct) * 100) / 100
  orderDiscountInput.value = v
  orderDiscountAmount.value = v
}

// M02 整单折扣 — 清除
const clearOrderDiscount = () => {
  orderDiscountInput.value = 0
  orderDiscountAmount.value = 0
}

// 保存草稿
const saveDraft = async () => {
  if (!member.value) {
    ElMessage.warning('请先选择会员')
    return
  }
  try {
    const payload = {
      storeId: 1,
      memberId: member.value.id,
      remark: remark.value,
      items: items.value.map(i => ({
        serviceId: i.serviceId,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        discountAmount: i.discountAmount,
        employeeId: i.employeeId
      }))
    }
    const res: any = await orderApi.create(payload)
    const data = res.data || res
    currentOrderId.value = data.id
    currentStatus.value = 1
    ElMessage.success(`订单创建成功 #${data.orderNo}`)
    // M02: 若有整单折扣，调用后端 applyDiscount
    if (orderDiscountAmount.value > 0 && data.id) {
      try {
        await orderApi.applyDiscount(data.id, orderDiscountAmount.value)
        ElMessage.success(`整单折扣 ¥${orderDiscountAmount.value.toFixed(2)} 已应用`)
      } catch (e: any) {
        ElMessage.warning('整单折扣应用失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
      }
    }
    loadToday()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 去支付
const payDialog = ref(false)
const paying = ref(false)
// 混合支付：每笔一个 method + amount
interface PayLine { method: string; amount: number }
const payLines = ref<PayLine[]>([])
const newPayMethod = ref<string>('cash')
const newPayAmount = ref(0)

const METHOD_LABELS: Record<string, string> = {
  cash: '现金', wallet: '储值', wechat: '微信', alipay: '支付宝',
  unionpay: '银联', e_cny_soft: '数字人民币(软)', e_cny_hard: '数字人民币(硬)', package: '次卡核销'
}
const methodLabel = (m: string) => METHOD_LABELS[m] || m

const remainAmount = computed(() => Math.max(0, Number(totals.value.final) - Number(paidAmount.value)))
const payTotal = computed(() => payLines.value.reduce((s, x) => s + Number(x.amount || 0), 0))

// 当选择"次卡核销"或"储值"时，自动填当前剩余金额，避免用户手动输入
watch(newPayMethod, (m) => {
  if (m === 'package' || m === 'wallet') {
    newPayAmount.value = Number(remainAmount.value.toFixed(2))
  }
})

const addPayLine = () => {
  if (!newPayMethod.value) return
  // 次卡核销允许 amount=0
  if (newPayMethod.value !== 'package' && newPayAmount.value <= 0) {
    ElMessage.warning('请输入金额'); return
  }
  if (payTotal.value + newPayAmount.value > remainAmount.value + 0.01) {
    ElMessage.error('总金额超过剩余应付'); return
  }
  payLines.value.push({ method: newPayMethod.value, amount: Number(newPayAmount.value) })
  newPayMethod.value = 'cash'
  newPayAmount.value = 0
}

const goPay = async () => {
  payLines.value = []
  newPayMethod.value = 'cash'
  newPayAmount.value = 0
  // 没有 currentOrderId 则先创建
  if (!currentOrderId.value) {
    await saveDraft()
    if (!currentOrderId.value) return
  }
  payDialog.value = true
}

const confirmPay = async () => {
  if (!currentOrderId.value) { ElMessage.warning('请保存订单'); return }
  if (!payLines.value.length) { ElMessage.warning('请添加至少一笔支付'); return }
  if (payTotal.value > remainAmount.value + 0.01) { ElMessage.error('总金额超过剩余应付'); return }

  paying.value = true
  try {
    // 后端已支持 payments 数组；为兼容老接口，单笔时也传 payments
    const body = {
      payments: payLines.value.map(l => ({
        method: l.method,
        amount: l.amount,
        transactionId: ['cash', 'package'].includes(l.method) ? null : `${l.method.toUpperCase()}-${Date.now()}-${Math.floor(Math.random()*1000)}`,
        memberCardId: l.method === 'package' ? 1 : null  // TODO 实际场景让用户选具体次卡
      }))
    }
    const res: any = await orderApi.pay(currentOrderId.value, body)
    const data = res.data || res
    paidAmount.value = Number(data.paidAmount)
    paymentHistory.value.push(...payLines.value.map(l => ({ method: methodLabel(l.method), amount: l.amount })))
    ElMessage.success(`支付成功 ¥${payTotal.value.toFixed(2)}（${payLines.value.length} 笔）`)

    // COUPON-007：核销选中的优惠券
    if (selectedCoupon.value && currentOrderId.value) {
      try {
        await couponApi.use(selectedCoupon.value.id, currentOrderId.value)
        ElMessage.success(`券核销成功（${selectedCoupon.value.code || selectedCoupon.value.id}）`)
        selectedCoupon.value = null
      } catch (e: any) {
        ElMessage.warning(`券核销失败：${e?.message || '未知错误'}（订单已支付，请手动核销）`)
      }
    }

    // 准备打印小票快照
    lastPaidOrder.value = {
      orderNo: data.orderNo || `TEMP-${currentOrderId.value}`,
      memberName: member.value?.name,
      memberPhone: member.value?.phone,
      items: items.value.map(i => ({ ...i })),
      totalAmount: totals.value.total,
      discountAmount: totals.value.discount,
      finalAmount: totals.value.final,
      payments: payLines.value.map(l => ({ method: l.method, amount: l.amount }))
    }
    if (paidAmount.value >= totals.value.final || payLines.value.some(l => l.method === 'package')) {
      payDialog.value = false
      // 不立即重置：让用户先看到 "打印小票" 按钮，5 秒后或用户手动清空
      // 这里给出友好提示，并展示打印入口
      ElMessage.success('订单已完成，可点击右上方"打印小票"按钮出小票')
      loadToday()
    } else {
      payLines.value = []
      newPayMethod.value = 'cash'
      newPayAmount.value = 0
    }
  } catch (e: any) {
    ElMessage.error('支付失败：' + (e?.response?.data?.msg || e.message))
  } finally {
    paying.value = false
  }
}

// 撤销
const voidDialog = ref(false)
const voidReason = ref('')
const lastPaidOrder = ref<any>(null)   // 最近一笔已支付订单，用于打印小票

const printReceipt = () => {
  const o = lastPaidOrder.value
  if (!o) { ElMessage.warning('暂无已支付订单可打印'); return }
  const w = window.open('', '_blank', 'width=380,height=600')
  if (!w) { ElMessage.error('浏览器拦截了弹窗'); return }
  const itemRows = (o.items || []).map((it: any) => `
    <tr>
      <td>${it.serviceName}</td>
      <td style="text-align:center">${it.quantity}</td>
      <td style="text-align:right">¥${Number(it.unitPrice).toFixed(2)}</td>
      <td style="text-align:right">¥${Number(it.finalPrice).toFixed(2)}</td>
    </tr>
  `).join('')
  const payRows = (o.payments || []).map((p: any) => `
    <tr><td>${methodLabel(p.method)}</td><td style="text-align:right">¥${Number(p.amount).toFixed(2)}</td></tr>
  `).join('')
  w.document.write(`
    <!doctype html><html><head><meta charset="utf-8"/>
    <title>小票 ${o.orderNo}</title>
    <style>
      body { font-family: Consolas, monospace; width: 280px; padding: 8px; font-size: 12px; color: #000; }
      h2 { text-align: center; margin: 0 0 8px; font-size: 16px; }
      .center { text-align: center; }
      hr { border: none; border-top: 1px dashed #000; margin: 6px 0; }
      table { width: 100%; border-collapse: collapse; }
      th { border-bottom: 1px solid #000; padding: 2px 0; }
      td { padding: 2px 0; }
      .total { font-size: 14px; font-weight: bold; }
      @media print { body { width: 58mm; } }
    </style></head><body>
    <h2>美业 SaaS 旗舰总店</h2>
    <div class="center">收银小票</div>
    <hr/>
    <div>订单号：${o.orderNo}</div>
    <div>日　期：${new Date().toLocaleString()}</div>
    <div>会　员：${o.memberName || '散客'}${o.memberPhone ? ' (' + o.memberPhone + ')' : ''}</div>
    <hr/>
    <table>
      <thead><tr><th style="text-align:left">项目</th><th>数量</th><th>单价</th><th>小计</th></tr></thead>
      <tbody>${itemRows || '<tr><td colspan="4" class="center">无</td></tr>'}</tbody>
    </table>
    <hr/>
    <table>
      <tr><td>原价合计</td><td style="text-align:right">¥${Number(o.totalAmount || 0).toFixed(2)}</td></tr>
      <tr><td>折扣</td><td style="text-align:right">-¥${Number(o.discountAmount || 0).toFixed(2)}</td></tr>
      <tr class="total"><td>应付</td><td style="text-align:right">¥${Number(o.finalAmount || 0).toFixed(2)}</td></tr>
    </table>
    <hr/>
    <div>支付方式：</div>
    <table>${payRows || '<tr><td colspan="2" class="center">无</td></tr>'}</table>
    <hr/>
    <div class="center">谢谢惠顾，欢迎再次光临</div>
    <div class="center" style="margin-top: 12px">
      <button onclick="window.print()" style="padding:4px 12px">打印</button>
    </div>
    </body></html>
  `)
  w.document.close()
}
const confirmVoid = async () => {
  try {
    await orderApi.voidOrder(currentOrderId.value!, voidReason.value)
    ElMessage.success('订单已撤销')
    voidDialog.value = false
    resetOrder()
    loadToday()
  } catch (e) {
    ElMessage.error('撤销失败')
  }
}

// 退款
const refundDialog = ref(false)
const activeOrder = ref<any>(null)

const openOrder = async (o: any) => {
  try {
    const res: any = await orderApi.detail(o.id)
    activeOrder.value = res.data || res
    if (activeOrder.value.status === 2) {
      // 已支付 → 弹出新退款对话框
      refundDialog.value = true
    } else {
      ElMessage.info(`订单状态: ${statusLabel(activeOrder.value.status)}`)
    }
  } catch (e) {
    ElMessage.error('加载订单失败')
  }
}

const onRefundSuccess = () => {
  refundDialog.value = false
  loadToday()
}

// 最近订单
const recentOrders = ref<any[]>([])
const recentDrawer = ref(false)   // 窄屏下从右下角浮窗打开"最近订单"
const rightCollapsed = ref(false) // 宽屏下右侧栏可折叠
const loadToday = async () => {
  try {
    const [o, s]: any[] = await Promise.all([
      orderApi.today(),
      orderApi.stats()
    ])
    recentOrders.value = o.data || []
    stats.value = s.data || {}
  } catch {}
}

const loadServices = async () => {
  try {
    const res: any = await serviceApi.list()
    services.value = res.data || res || []
  } catch {}
}

// 状态枚举
const STATUS = { 1: '进行中', 2: '已支付', 3: '已退款', 4: '已撤销' }
const statusLabel = (s: number) => STATUS[s as keyof typeof STATUS] || '未知'
const statusType = (s: number) => {
  switch (s) {
    case 1: return 'warning'
    case 2: return 'success'
    case 3: return 'info'
    case 4: return 'danger'
    default: return ''
  }
}

// 键盘快捷键：F2 打开会员选择 / F4 清空 / F8 草稿 / F9 去支付 / Esc 关弹窗
const onKeydown = (e: KeyboardEvent) => {
  // 弹窗打开时只响应 Esc
  if (payDialog.value || voidDialog.value || showMemberPicker.value) {
    if (e.key === 'Escape') {
      payDialog.value = false
      voidDialog.value = false
      showMemberPicker.value = false
    }
    return
  }
  // 焦点在输入控件时不抢键盘
  const tag = (e.target as HTMLElement)?.tagName
  const isInput = tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable
  if (isInput) return

  switch (e.key) {
    case 'F2':
      e.preventDefault()
      showMemberPicker.value = true
      loadAllMembers(false)
      break
    case 'F4':
      e.preventDefault()
      resetOrder()
      break
    case 'F8':
      e.preventDefault()
      if (items.value.length) saveDraft()
      break
    case 'F9':
      e.preventDefault()
      if (items.value.length && member.value) goPay()
      break
  }
}

onMounted(() => {
  loadServices()
  loadToday()
  onMemberSearch()
  loadEmployees()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.pos-page { padding: 12px; }
.kbd-hint {
  position: fixed; right: 16px; bottom: 12px;
  background: rgba(255,255,255,0.95); border: 1px solid #e4e7ed;
  border-radius: 4px; padding: 6px 10px;
  font-size: 12px; color: #606266;
  display: flex; gap: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  z-index: 100;
}
@media (max-width: 1279px) {
  .kbd-hint { display: none; }   /* 窄屏不显示快捷键提示，留给 FAB */
}
.kbd-hint kbd {
  background: #f5f7fa; border: 1px solid #dcdfe6;
  border-radius: 3px; padding: 1px 6px;
  font-family: Consolas, monospace; font-size: 11px;
  color: #409eff;
}
.top-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.brand { font-size: 20px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.stat-row { display: flex; gap: 8px; }
.stat-chip {
  background: #ecf5ff; color: #409eff; padding: 6px 12px; border-radius: 4px;
  font-size: 13px;
}
.stat-chip.green { background: #f0f9eb; color: #67c23a; }
.stat-chip.orange { background: #fdf6ec; color: #e6a23c; }
.stat-chip b { margin-left: 6px; font-size: 15px; }

.pos-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 260px;
  gap: 12px;
  height: calc(100vh - 130px);
  min-width: 0;          /* 允许窄屏压缩 */
}
/* 折叠态：右栏变成 60px，只剩图标 */
.pos-grid:has(.right-pane.collapsed) {
  grid-template-columns: 300px minmax(0, 1fr) 60px;
}
.right-pane.collapsed .recent,
.right-pane.collapsed .pane-title span { display: none; }
.right-pane.collapsed :deep(.el-card__body) { padding: 12px 6px; }
.right-pane.collapsed :deep(.el-card__header),
.right-pane.collapsed :deep(.el-card__header.iscard) { display: none; }
.right-pane.collapsed :deep(.el-card) { border: none; box-shadow: none; }
@media (max-width: 1279px) {
  .pos-grid {
    grid-template-columns: 240px minmax(0, 1fr);  /* 窄屏隐藏右栏 */
  }
  .left-pane { min-width: 240px; }
  .right-pane { display: none; }                     /* 窄屏彻底隐藏 */
}
@media (min-width: 1280px) {
  .left-pane { min-width: 300px; }
  .right-pane { display: flex; }
}
.left-pane, .mid-pane, .right-pane {
  display: flex; flex-direction: column;
  overflow: hidden;
}
.left-pane :deep(.el-card__body), .right-pane :deep(.el-card__body) {
  display: flex; flex-direction: column;
  flex: 1; min-height: 0; overflow: hidden;
}
.mid-pane :deep(.el-card__body) {
  display: flex; flex-direction: column;
  flex: 1; min-height: 0; overflow-y: auto;
}
.left-pane .svc-list { flex: 1; overflow-y: auto; padding-right: 4px; }
.right-pane .recent { flex: 1; overflow-y: auto; padding-right: 4px; }
.pane-title {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.svc-list { flex: 1; overflow-y: auto; min-height: 0; }
.svc-item {
  border: 1px solid #ebeef5;
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  background: #fff;
  transition: all 0.2s;
  display: flex; justify-content: space-between; align-items: center; gap: 8px;
}
.svc-item:hover { border-color: #409eff; background: #ecf5ff; box-shadow: 0 2px 8px rgba(64,158,255,0.1); }
.svc-item:active { transform: scale(0.98); }
.svc-name { font-weight: 500; flex: 1; min-width: 0; }
.svc-meta {
  display: flex; gap: 6px; font-size: 12px; color: #909399; align-items: center; flex-shrink: 0;
}
.price { color: #f56c6c; font-weight: 600; font-size: 14px; }
.m-price { color: #67c23a; background: #f0f9eb; padding: 1px 6px; border-radius: 3px; font-weight: 500; }
.dur { background: #f5f7fa; padding: 1px 6px; border-radius: 3px; font-size: 11px; }

.member-bar { display: flex; gap: 8px; margin-bottom: 12px; }
.remark-row { display: flex; align-items: center; gap: 8px; margin: 12px 0; }
.totals {
  background: #f5f7fa; padding: 16px; border-radius: 4px; margin-top: 16px;
  position: sticky; bottom: 56px; z-index: 2;
}
.totals-row { display: flex; justify-content: space-between; padding: 4px 0; }
.totals-row.grand {
  font-size: 18px; padding-top: 10px; margin-top: 6px;
  border-top: 1px dashed #c0c4cc;
}
.discount { color: #67c23a; }
.final { color: #f56c6c; font-size: 22px; }

.actions {
  display: flex; gap: 8px; margin-top: 16px;
  position: sticky; bottom: 0; z-index: 3;
  background: #fff; padding: 10px 0; border-top: 1px solid #ebeef5;
}
.spacer { flex: 1; }

.recent { flex: 1; overflow-y: auto; min-height: 0; }
/* 窄屏右下浮窗按钮 (隐藏于宽屏) */
.recent-fab {
  position: fixed; right: 16px; bottom: 64px;
  border-radius: 50%; width: 56px; height: 56px;
  box-shadow: 0 4px 12px rgba(64,158,255,0.4);
  display: none;     /* 默认隐藏，宽屏不显示 */
  z-index: 99;
}
.recent-fab-num {
  position: absolute; top: -4px; right: -4px;
  background: #f56c6c; color: #fff;
  border-radius: 10px; min-width: 18px; height: 18px; line-height: 18px;
  font-size: 11px; padding: 0 4px;
}
@media (max-width: 1279px) {
  .recent-fab { display: inline-flex; }     /* 窄屏：显示 */
}
@media (min-width: 1280px) {
  /* 宽屏右栏折叠时也显示 FAB，点开抽屉 */
  .recent-fab.show-when-collapsed { display: inline-flex; }
  /* 默认宽屏且右栏展开时隐藏 */
  .recent-fab { display: none; }
}
.recent-item {
  padding: 10px; border: 1px solid #ebeef5; border-radius: 4px;
  margin-bottom: 8px; cursor: pointer;
}
.recent-item:hover { border-color: #409eff; background: #ecf5ff; }
.recent-top { display: flex; justify-content: space-between; margin-bottom: 4px; }
.no { font-family: monospace; font-size: 12px; color: #606266; }
.recent-meta { display: flex; justify-content: space-between; font-size: 13px; }
.muted { color: #909399; font-size: 13px; margin-left: 8px; }

.exp-badge-row { display: flex; align-items: center; gap: 10px; padding: 6px 10px; background: #f0f9eb; border-radius: 4px; margin-bottom: 8px; font-size: 13px; }
.exp-hint { color: #67c23a; font-size: 12px; }

.pay-summary {
  background: #f5f7fa; padding: 16px; border-radius: 4px; margin-bottom: 16px;
}
.pay-row { display: flex; justify-content: space-between; padding: 4px 0; }
.pay-row .final { color: #f56c6c; font-size: 22px; font-weight: 600; }
.pay-row .remain { color: #e6a23c; }

.pay-methods { margin-bottom: 16px; }
.method-label { font-weight: 600; margin-bottom: 8px; }
.methods { display: flex; flex-wrap: wrap; gap: 8px; }

.pay-amount {
  display: flex; align-items: center; gap: 16px;
  background: #fdf6ec; padding: 12px; border-radius: 4px;
}
.pay-amount span { font-weight: 600; }
.pay-history { margin-top: 16px; }

.refund-info {
  background: #f5f7fa; padding: 12px; border-radius: 4px; margin-bottom: 12px;
}
.refund-info div { padding: 4px 0; }
</style>
