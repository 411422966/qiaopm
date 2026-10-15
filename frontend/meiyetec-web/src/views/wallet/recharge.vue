<template>
  <div class="wallet-page">
    <!-- 顶部说明 -->
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><Wallet /></el-icon>
        <div>
          <h2>会员储值管理</h2>
          <p>支持充值、消费扣款、退款原路返回，余额实时更新，流水可追溯</p>
        </div>
      </div>
      <div class="banner-right">
        <el-button type="warning" plain @click="goTransactions" :icon="List">
          查看全部流水
        </el-button>
      </div>
    </div>

    <!-- 三列功能区 -->
    <el-row :gutter="20" class="action-row">
      <!-- 充值 -->
      <el-col :span="12">
        <el-card class="action-card recharge-card" shadow="hover">
          <template #header>
            <div class="card-hd">
              <span><el-icon color="#67c23a"><Plus /></el-icon> 会员充值</span>
              <el-tag effect="plain" size="small">赠送活动</el-tag>
            </div>
          </template>

          <el-form :model="recharge" label-width="84px" label-position="right">
            <el-form-item label="选择会员">
              <el-select v-model="recharge.memberId" placeholder="选择要充值的会员"
                filterable remote :remote-method="searchMembers" :loading="searchLoading"
                style="width: 100%" @change="onMemberChange">
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
                <el-option v-for="m in memberOpts" :key="m.id"
                  :label="`${m.name} | ${m.phone}`" :value="m.id">
                  <div class="opt-row">
                    <span class="opt-name">{{ m.name }}</span>
                    <span class="opt-extra">{{ m.memberNo }} · ¥{{ formatMoney(m.balance) }} · {{ m.levelName || '普通' }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item v-if="currentBalance !== null" label="当前余额">
              <div class="balance-display">
                <span class="balance-num">¥{{ formatMoney(currentBalance) }}</span>
                <span class="balance-meta">累计充值 ¥{{ formatMoney(totalRecharge) }} · 累计消费 ¥{{ formatMoney(totalConsume) }}</span>
              </div>
            </el-form-item>

            <el-form-item label="充值金额">
              <el-input-number v-model="recharge.amount" :min="1" :precision="2" :step="100"
                style="width: 200px" placeholder="请输入金额" @change="onAmountChange" />
              <span class="amount-tip">元</span>
            </el-form-item>

            <el-form-item label="快捷金额">
              <div class="quick-amount">
                <el-tag v-for="v in [100, 200, 500, 1000, 2000, 5000]" :key="v"
                  :type="recharge.amount === v ? 'success' : 'info'" effect="dark"
                  class="quick-tag" @click="recharge.amount = v; onAmountChange()">
                  ¥{{ v }}
                </el-tag>
              </div>
            </el-form-item>

            <el-form-item label="赠送金额">
              <el-input-number v-model="recharge.giftAmount" :min="0" :precision="2" :step="10"
                style="width: 200px" placeholder="可选" />
              <span class="amount-tip" v-if="promoCalc">💡 活动 {{ promoCalc.rule }}，建议赠送 ¥{{ promoCalc.gift }}</span>
            </el-form-item>

            <el-form-item label="支付方式">
              <el-radio-group v-model="recharge.payMethod">
                <el-radio value="微信">微信</el-radio>
                <el-radio value="支付宝">支付宝</el-radio>
                <el-radio value="银行卡">银行卡</el-radio>
                <el-radio value="现金">现金</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="备注">
              <el-input v-model="recharge.remark" placeholder="选填，如：活动充值" />
            </el-form-item>

            <el-alert v-if="recharge.amount > 0" :title="`本次合计到账：¥${formatMoney(Number(recharge.amount) + Number(recharge.giftAmount || 0))}`"
              type="success" :closable="false" show-icon style="margin-bottom: 16px" />

            <el-button type="success" size="large" :loading="submitting"
              :disabled="!recharge.memberId || recharge.amount <= 0" @click="submitRecharge" style="width: 100%">
              <el-icon><CreditCard /></el-icon> 确认充值 ¥{{ formatMoney(Number(recharge.amount) + Number(recharge.giftAmount || 0)) }}
            </el-button>
          </el-form>
        </el-card>
      </el-col>

      <!-- 消费 -->
      <el-col :span="6">
        <el-card class="action-card consume-card" shadow="hover">
          <template #header>
            <div class="card-hd">
              <span><el-icon color="#e6a23c"><Minus /></el-icon> 消费扣款</span>
            </div>
          </template>
          <el-form :model="consume" label-width="84px" label-position="right">
            <el-form-item label="选择会员">
              <el-select v-model="consume.memberId" placeholder="选择会员"
                filterable remote :remote-method="searchMembers" :loading="searchLoading"
                style="width: 100%">
                <template #prefix><el-icon><User /></el-icon></template>
                <el-option v-for="m in memberOpts" :key="m.id"
                  :label="`${m.name} | ${m.phone}`" :value="m.id">
                  <div class="opt-row">
                    <span class="opt-name">{{ m.name }}</span>
                    <span class="opt-extra">余额 ¥{{ formatMoney(m.balance) }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="消费金额">
              <el-input-number v-model="consume.amount" :min="0.01" :precision="2" :step="50"
                style="width: 100%" />
            </el-form-item>
            <el-form-item label="关联订单">
              <el-input-number v-model="consume.orderId" :min="1" style="width: 100%" placeholder="订单ID" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="consume.remark" placeholder="如：面部护理" />
            </el-form-item>
            <el-button type="warning" size="large" :loading="consuming"
              :disabled="!consume.memberId || consume.amount <= 0" @click="submitConsume" style="width: 100%">
              <el-icon><CreditCard /></el-icon> 确认扣款
            </el-button>
          </el-form>
        </el-card>
      </el-col>

      <!-- 退款 -->
      <el-col :span="6">
        <el-card class="action-card refund-card" shadow="hover">
          <template #header>
            <div class="card-hd">
              <span><el-icon color="#f56c6c"><RefreshLeft /></el-icon> 退款原路返回</span>
            </div>
          </template>
          <el-form :model="refund" label-width="84px" label-position="right">
            <el-form-item label="选择会员">
              <el-select v-model="refund.memberId" placeholder="选择会员"
                filterable remote :remote-method="searchMembers" :loading="searchLoading"
                style="width: 100%">
                <template #prefix><el-icon><User /></el-icon></template>
                <el-option v-for="m in memberOpts" :key="m.id"
                  :label="`${m.name} | ${m.phone}`" :value="m.id">
                  <div class="opt-row">
                    <span class="opt-name">{{ m.name }}</span>
                    <span class="opt-extra">{{ m.memberNo }} · ¥{{ formatMoney(m.balance) }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="原订单ID">
              <el-input-number v-model="refund.orderId" :min="1" style="width: 100%" placeholder="必填" />
            </el-form-item>
            <el-form-item label="退款金额">
              <el-input-number v-model="refund.amount" :min="0.01" :precision="2" :step="50"
                style="width: 100%" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="refund.remark" placeholder="退款原因" />
            </el-form-item>
            <el-alert title="同一订单只能退一次，幂等保护" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
            <el-button type="danger" size="large" :loading="refunding"
              :disabled="!refund.memberId || !refund.orderId || refund.amount <= 0"
              @click="submitRefund" style="width: 100%">
              <el-icon><RefreshLeft /></el-icon> 确认退款
            </el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 充值赠送活动规则 -->
    <el-card class="promotion-card" shadow="never" style="margin-top: 20px">
      <template #header>
        <div class="card-hd">
          <span><el-icon color="#e6a23c"><Present /></el-icon> 充值赠送活动规则</span>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6" v-for="r in rules" :key="r.recharge">
          <div class="rule-box">
            <div class="rule-title">{{ r.rule }}</div>
            <div class="rule-meta">充 ¥{{ r.recharge }} 送 ¥{{ r.gift }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  Wallet, Plus, Minus, RefreshLeft, CreditCard, User, List, Present
} from '@element-plus/icons-vue'
import { walletApi, memberApi } from '@/api/basic'

const router = useRouter()
const submitting = ref(false)
const consuming = ref(false)
const refunding = ref(false)
const searchLoading = ref(false)

const memberOpts = ref<any[]>([])
const recharge = reactive({ memberId: null as number | null, amount: 100, giftAmount: 0, payMethod: '微信', operatorId: 1, remark: '' })
const consume  = reactive({ memberId: null as number | null, amount: 0, orderId: undefined as number | undefined, payMethod: '扣减', operatorId: 1, remark: '' })
const refund   = reactive({ memberId: null as number | null, amount: 0, orderId: undefined as number | undefined, payMethod: '原路返回', operatorId: 1, remark: '' })

const currentBalance = ref<number | null>(null)
const totalRecharge = ref(0)
const totalConsume = ref(0)
const promoCalc = ref<any>(null)
const rules = ref<any[]>([])

const formatMoney = (v: any) => Number(v || 0).toFixed(2)

const searchMembers = async (kw: string) => {
  searchLoading.value = true
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 30, keyword: kw || '', status: 1 })
    memberOpts.value = res.data?.records || []
  } finally {
    searchLoading.value = false
  }
}

const onMemberChange = async () => {
  if (!recharge.memberId) { currentBalance.value = null; return }
  const res: any = await walletApi.getBalance(recharge.memberId)
  if (res.data) {
    currentBalance.value = Number(res.data.balance)
    totalRecharge.value = Number(res.data.totalRecharge)
    totalConsume.value = Number(res.data.totalConsume)
  }
}

const onAmountChange = async () => {
  if (!recharge.amount || recharge.amount <= 0) { promoCalc.value = null; return }
  const res: any = await walletApi.promotionCalc(Number(recharge.amount))
  promoCalc.value = res.data
  if (res.data?.gift) recharge.giftAmount = Number(res.data.gift)
}

const submitRecharge = async () => {
  submitting.value = true
  try {
    const res: any = await walletApi.recharge({
      memberId: recharge.memberId,
      amount: Number(recharge.amount),
      giftAmount: Number(recharge.giftAmount || 0),
      payMethod: recharge.payMethod,
      operatorId: recharge.operatorId,
      remark: recharge.remark || '充值'
    })
    if (res.code === 200) {
      ElMessage.success(`充值成功，到账 ¥${formatMoney(res.data.totalAdded)}，余额变为 ¥${formatMoney(res.data.balanceAfter)}`)
      recharge.amount = 0; recharge.giftAmount = 0; recharge.remark = ''
      onMemberChange()
    } else {
      ElMessage.error(res.msg || '充值失败')
    }
  } finally {
    submitting.value = false
  }
}

const submitConsume = async () => {
  consuming.value = true
  try {
    const res: any = await walletApi.consume({
      memberId: consume.memberId,
      amount: Number(consume.amount),
      orderId: consume.orderId,
      payMethod: '扣减',
      operatorId: consume.operatorId,
      remark: consume.remark || '消费扣款'
    })
    if (res.code === 200) {
      ElMessage.success(`扣款成功，余额 ¥${formatMoney(res.data.balanceAfter)}`)
      consume.amount = 0; consume.remark = ''
    } else {
      ElMessage.error(res.msg || '扣款失败')
    }
  } finally {
    consuming.value = false
  }
}

const submitRefund = async () => {
  refunding.value = true
  try {
    const res: any = await walletApi.refund({
      memberId: refund.memberId,
      amount: Number(refund.amount),
      orderId: refund.orderId,
      payMethod: '原路返回',
      operatorId: refund.operatorId,
      remark: refund.remark || '退款'
    })
    if (res.code === 200) {
      ElMessage.success(`退款成功，余额 ¥${formatMoney(res.data.balanceAfter)}`)
      refund.amount = 0; refund.remark = ''
    } else {
      ElMessage.error(res.msg || '退款失败')
    }
  } finally {
    refunding.value = false
  }
}

const goTransactions = () => router.push('/wallet/transactions')

const loadRules = async () => {
  const res: any = await walletApi.promotionRules()
  rules.value = res.data || []
}

loadRules()
</script>

<style scoped>
.wallet-page { padding: 0; }

.banner {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; border-radius: 16px; padding: 24px 32px; margin-bottom: 20px;
  display: flex; justify-content: space-between; align-items: center;
}
.banner-left { display: flex; gap: 16px; align-items: center; }
.banner h2 { margin: 0; font-size: 22px; }
.banner p { margin: 4px 0 0; font-size: 13px; opacity: 0.9; }

.action-row { margin-bottom: 0; }
.action-card { border-radius: 12px; }
.card-hd { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.card-hd span { display: flex; align-items: center; gap: 6px; }

.balance-display { display: flex; align-items: baseline; gap: 12px; padding: 8px 0; }
.balance-num { font-size: 26px; font-weight: 700; color: #67c23a; }
.balance-meta { font-size: 12px; color: #909399; }

.amount-tip { margin-left: 12px; color: #909399; font-size: 13px; }

.quick-amount { display: flex; flex-wrap: wrap; gap: 8px; }
.quick-tag { cursor: pointer; padding: 6px 16px; font-size: 14px; }

.promotion-card .rule-box {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border-radius: 10px; padding: 20px; text-align: center;
  transition: transform 0.2s;
}
.promotion-card .rule-box:hover { transform: translateY(-3px); }
.rule-title { font-size: 18px; font-weight: 700; color: #92400e; }
.rule-meta { margin-top: 6px; font-size: 12px; color: #b45309; }

/* 远程搜索选项 */
.opt-row { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.opt-name { font-weight: 600; color: #1f2937; }
.opt-extra { font-size: 12px; color: #909399; }
:deep(.el-select-dropdown__item) { height: auto; padding: 6px 12px; line-height: 1.4; }
</style>
