<template>
  <div class="gift-card-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">礼品卡</span>
          <el-space>
            <el-button type="primary" @click="openPurchaseDialog()">+ 购买礼品卡</el-button>
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="filter.status" @tab-change="loadList">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="未使用" name="UNUSED" />
        <el-tab-pane label="已使用" name="USED" />
        <el-tab-pane label="已过期" name="EXPIRED" />
        <el-tab-pane label="已退款" name="REFUNDED" />
      </el-tabs>

      <div class="filters">
        <el-input v-model="filter.phone" placeholder="按接收人手机筛选" style="width:200px" clearable @keyup.enter="loadList" />
        <el-input v-model="filter.cardNo" placeholder="按卡号筛选" style="width:260px" clearable @keyup.enter="loadList" />
        <el-button @click="loadList">查询</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table :data="cards" v-loading="loading" border stripe>
        <el-table-column prop="cardNo" label="卡号" width="200" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.cardType === 'FIXED' ? 'success' : 'info'">
              {{ row.cardType === 'FIXED' ? '固定面值' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面值/余额" width="160">
          <template #default="{ row }">
            <span>¥{{ row.faceValue }} → </span>
            <span :class="{ 'zero-balance': Number(row.balance) <= 0 }">¥{{ row.balance }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="buyerName" label="购买人" width="100" />
        <el-table-column prop="recipientName" label="接收人" width="100" />
        <el-table-column prop="recipientPhone" label="接收手机" width="130" />
        <el-table-column prop="purchaseTime" label="购买时间" width="170" />
        <el-table-column prop="expireTime" label="过期时间" width="170" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openRedeemDialog(row)">核销</el-button>
            <el-button link size="small" @click="openGiftDialog(row)" :disabled="row.status !== 'UNUSED'">转赠</el-button>
            <el-button link size="small" @click="openTxDialog(row)">流水</el-button>
            <el-button link size="small" type="danger" @click="doRefund(row)" :disabled="row.status === 'REFUNDED'">
              退款
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 购买弹窗 -->
    <el-dialog v-model="purchaseDialog.visible" title="购买礼品卡" width="520px">
      <el-form :model="purchaseDialog.form" label-width="100px">
        <el-form-item label="面值" required>
          <el-input-number v-model="purchaseDialog.form.faceValue" :min="1" :precision="2" />
        </el-form-item>
        <el-form-item label="卡类型">
          <el-radio-group v-model="purchaseDialog.form.cardType">
            <el-radio-button value="CUSTOM">自定义</el-radio-button>
            <el-radio-button value="FIXED">固定面值</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="服务次数">
          <el-input-number v-model="purchaseDialog.form.serviceCount" :min="1" />
        </el-form-item>
        <el-form-item label="绑定服务">
          <el-input-number v-model="purchaseDialog.form.serviceId" :min="0" placeholder="可选" />
        </el-form-item>
        <el-form-item label="购买人">
          <el-input v-model="purchaseDialog.form.buyerName" />
        </el-form-item>
        <el-form-item label="接收人">
          <el-input v-model="purchaseDialog.form.recipientName" />
        </el-form-item>
        <el-form-item label="接收手机">
          <el-input v-model="purchaseDialog.form.recipientPhone" />
        </el-form-item>
        <el-form-item label="祝福语">
          <el-input v-model="purchaseDialog.form.giftMessage" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="主题">
          <el-select v-model="purchaseDialog.form.theme" style="width:100%">
            <el-option label="默认" value="DEFAULT" />
            <el-option label="生日" value="BIRTHDAY" />
            <el-option label="节日" value="FESTIVAL" />
            <el-option label="情人节" value="VALENTINE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="purchaseDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitPurchase" :loading="purchaseDialog.loading">购买</el-button>
      </template>
    </el-dialog>

    <!-- 核销弹窗 -->
    <el-dialog v-model="redeemDialog.visible" title="核销礼品卡" width="480px">
      <el-form :model="redeemDialog.form" label-width="100px">
        <el-form-item label="卡号">
          <span>{{ redeemDialog.target?.cardNo }}</span>
        </el-form-item>
        <el-form-item label="当前余额">
          <span>¥{{ redeemDialog.target?.balance }}</span>
        </el-form-item>
        <el-form-item label="核销金额" required>
          <el-input-number v-model="redeemDialog.form.amount" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="关联订单 ID">
          <el-input-number v-model="redeemDialog.form.orderId" :min="0" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="redeemDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRedeem" :loading="redeemDialog.loading">核销</el-button>
      </template>
    </el-dialog>

    <!-- 转赠弹窗 -->
    <el-dialog v-model="giftDialog.visible" title="转赠礼品卡" width="480px">
      <el-form :model="giftDialog.form" label-width="100px">
        <el-form-item label="新接收人" required>
          <el-input v-model="giftDialog.form.recipientName" />
        </el-form-item>
        <el-form-item label="新手机" required>
          <el-input v-model="giftDialog.form.recipientPhone" />
        </el-form-item>
        <el-form-item label="祝福语">
          <el-input v-model="giftDialog.form.giftMessage" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="giftDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitGift" :loading="giftDialog.loading">转赠</el-button>
      </template>
    </el-dialog>

    <!-- 流水弹窗 -->
    <el-dialog v-model="txDialog.visible" title="礼品卡流水" width="640px">
      <el-table :data="txDialog.data" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.txType === 'REFUND' ? 'danger' : 'success'" size="small">
              {{ row.txType === 'REFUND' ? '退款' : '消费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="余额" width="120">
          <template #default="{ row }">¥{{ row.balance }}</template>
        </el-table-column>
        <el-table-column prop="orderId" label="订单 ID" width="100" />
        <el-table-column prop="notes" label="备注" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { giftCardApi } from '@/api/gift-card'

const loading = ref(false)

const cards = ref<any[]>([])
const filter = reactive({
  status: '' as string,
  phone: '' as string,
  cardNo: '' as string
})

const purchaseDialog = reactive({
  visible: false,
  loading: false,
  form: {
    faceValue: 200,
    cardType: 'CUSTOM',
    serviceCount: 1,
    serviceId: null as number | null,
    buyerName: '',
    recipientName: '',
    recipientPhone: '',
    giftMessage: '',
    theme: 'DEFAULT'
  }
})

const redeemDialog = reactive({
  visible: false,
  loading: false,
  target: null as any,
  form: { amount: 0, orderId: null as number | null }
})

const giftDialog = reactive({
  visible: false,
  loading: false,
  target: null as any,
  form: { recipientName: '', recipientPhone: '', giftMessage: '' }
})

const txDialog = reactive({
  visible: false,
  data: [] as any[]
})

function statusLabel(s?: string) {
  const m: Record<string, string> = { UNUSED: '未使用', USED: '已使用', EXPIRED: '已过期', REFUNDED: '已退款' }
  return m[s || ''] || s || '—'
}

function statusTag(s?: string) {
  const m: Record<string, 'success' | 'info' | 'danger' | 'warning'> = {
    UNUSED: 'success', USED: 'info', EXPIRED: 'warning', REFUNDED: 'danger'
  }
  return m[s || ''] || 'info'
}

async function loadList() {
  loading.value = true
  try {
    const params: any = {}
    if (filter.status) params.status = filter.status
    if (filter.phone) params.phone = filter.phone
    if (filter.cardNo) params.cardNo = filter.cardNo
    const res: any = await giftCardApi.list(params)
    let data = res.data || []
    if (filter.cardNo) {
      data = data.filter((c: any) => c.cardNo.includes(filter.cardNo))
    }
    cards.value = data
  } finally {
    loading.value = false
  }
}

function loadAll() {
  loadList()
}

function openPurchaseDialog() {
  purchaseDialog.form = {
    faceValue: 200,
    cardType: 'CUSTOM',
    serviceCount: 1,
    serviceId: null,
    buyerName: '',
    recipientName: '',
    recipientPhone: '',
    giftMessage: '',
    theme: 'DEFAULT'
  }
  purchaseDialog.visible = true
}

async function submitPurchase() {
  purchaseDialog.loading = true
  try {
    const res: any = await giftCardApi.purchase(purchaseDialog.form)
    ElMessage.success(`已生成卡号：${res.data?.cardNo}`)
    purchaseDialog.visible = false
    loadList()
  } finally {
    purchaseDialog.loading = false
  }
}

function openRedeemDialog(row: any) {
  redeemDialog.target = row
  redeemDialog.form = { amount: Math.min(Number(row.balance), 100), orderId: null }
  redeemDialog.visible = true
}

async function submitRedeem() {
  if (!redeemDialog.form.amount || redeemDialog.form.amount <= 0) {
    ElMessage.warning('请填写核销金额')
    return
  }
  redeemDialog.loading = true
  try {
    const payload: any = {
      cardNo: redeemDialog.target.cardNo,
      amount: redeemDialog.form.amount
    }
    if (redeemDialog.form.orderId) payload.orderId = redeemDialog.form.orderId
    const res: any = await giftCardApi.redeem(payload)
    ElMessage.success(`已核销，余额 ¥${res.data?.balance}，状态 ${res.data?.status}`)
    redeemDialog.visible = false
    loadList()
  } finally {
    redeemDialog.loading = false
  }
}

function openGiftDialog(row: any) {
  giftDialog.target = row
  giftDialog.form = {
    recipientName: row.recipientName || '',
    recipientPhone: row.recipientPhone || '',
    giftMessage: row.giftMessage || ''
  }
  giftDialog.visible = true
}

async function submitGift() {
  giftDialog.loading = true
  try {
    await giftCardApi.gift(giftDialog.target.id, giftDialog.form)
    ElMessage.success('已转赠')
    giftDialog.visible = false
    loadList()
  } finally {
    giftDialog.loading = false
  }
}

async function openTxDialog(row: any) {
  const res: any = await giftCardApi.transactions(row.id)
  txDialog.data = res.data || []
  txDialog.visible = true
}

async function doRefund(row: any) {
  try {
    await ElMessageBox.confirm(`确认退款「${row.cardNo}」？`, '提示', { type: 'warning' })
  } catch { return }
  await giftCardApi.refund(row.id)
  ElMessage.success('已退款')
  loadList()
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.gift-card-page {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.filters {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  align-items: center;
}
.zero-balance {
  color: #909399;
  text-decoration: line-through;
}
</style>