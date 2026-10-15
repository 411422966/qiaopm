<template>
  <div class="subscription-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">订阅 / 会员制</span>
          <el-space>
            <el-button type="primary" @click="initDefault" :loading="initing">初始化默认计划</el-button>
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-row :gutter="16" v-if="dashboard">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">活跃订阅数</div>
            <div class="stat-value">{{ dashboard.activeSubscribers || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">累计订阅</div>
            <div class="stat-value">{{ dashboard.totalSubscribers || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">MRR（月经常性收入）</div>
            <div class="stat-value">¥{{ dashboard.mrr || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">ARR（年化）</div>
            <div class="stat-value">¥{{ dashboard.arr || 0 }}</div>
          </el-card>
        </el-col>
      </el-row>
      <div class="total-rev" v-if="dashboard">
        累计订阅收入：<el-tag type="success">¥{{ dashboard.totalRevenue || 0 }}</el-tag>
      </div>
    </el-card>

    <el-tabs v-model="activeTab">
      <!-- 计划 -->
      <el-tab-pane label="订阅计划" name="plans">
        <el-card>
          <div class="plan-grid">
            <div v-for="p in plans" :key="p.id" class="plan-card">
              <div class="plan-head">
                <h3>{{ p.planName }}</h3>
                <el-tag size="small">{{ p.planType === 'MONTHLY' ? '月度' : p.planType === 'YEARLY' ? '年度' : '周度' }}</el-tag>
              </div>
              <div class="price">¥{{ p.price }}<span class="cycle">/{{ p.billingCycleDays }} 天</span></div>
              <ul class="benefits">
                <li>✓ 每月 {{ p.serviceCount }} 次服务</li>
                <li>✓ 专属 {{ Number(p.discountRate) * 10 }} 折优惠</li>
                <li v-if="p.giftPoints">✓ 每月赠送 {{ p.giftPoints }} 积分</li>
                <li v-if="p.trialDays">✓ {{ p.trialDays }} 天免费试用</li>
              </ul>
              <div class="stats">累计 {{ p.totalSubscribers || 0 }} / 活跃 {{ p.activeSubscribers || 0 }}</div>
            </div>
          </div>
          <el-empty v-if="!plans.length" description="暂无计划，请点击「初始化默认计划」" />
        </el-card>
      </el-tab-pane>

      <!-- 订阅管理 -->
      <el-tab-pane label="订阅查询" name="subs">
        <el-card>
          <div class="toolbar">
            <el-input v-model="memberQuery" placeholder="会员 ID" style="width:200px" clearable @keyup.enter="loadSubs" />
            <el-button type="primary" @click="loadSubs">查询</el-button>
          </div>
          <el-table :data="subs" v-loading="loading" border stripe>
            <el-table-column prop="subNo" label="订阅号" width="180" />
            <el-table-column prop="planCode" label="计划" width="140" />
            <el-table-column label="会员 ID" width="100">
              <template #default="{ row }">{{ row.memberId }}</template>
            </el-table-column>
            <el-table-column prop="startDate" label="开始日期" width="120" />
            <el-table-column prop="nextBillDate" label="下次计费" width="120" />
            <el-table-column prop="endDate" label="结束日期" width="120" />
            <el-table-column label="已用/剩余" width="120">
              <template #default="{ row }">{{ row.usedCount || 0 }} / {{ row.remainingCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="累计支付" width="120">
              <template #default="{ row }">¥{{ row.totalPaid || 0 }}</template>
            </el-table-column>
            <el-table-column label="自动续费" width="100">
              <template #default="{ row }">
                <el-tag :type="row.autoRenew ? 'success' : 'info'" size="small">
                  {{ row.autoRenew ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="doUse(row)" :disabled="row.status !== 'ACTIVE'">
                  使用
                </el-button>
                <el-button link size="small" @click="doPause(row)" :disabled="row.status !== 'ACTIVE'">暂停</el-button>
                <el-button link size="small" @click="doResume(row)" :disabled="row.status !== 'PAUSED'">恢复</el-button>
                <el-button link size="small" type="danger" @click="doCancel(row)" :disabled="row.status !== 'ACTIVE'">
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 自动续费 -->
      <el-tab-pane label="自动续费" name="renew">
        <el-card>
          <el-alert type="info" :closable="false">
            <template #title>每天凌晨 02:00 系统自动扫描待续费订阅，可点击下方按钮立即触发。</template>
          </el-alert>
          <el-button type="primary" @click="triggerRenew" :loading="renewing" style="margin-top:12px">
            手动触发续费扫描
          </el-button>
          <el-statistic v-if="renewResult" :value="renewResult.dueCount" title="待续费订阅数" style="margin-top:16px" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { subscriptionApi } from '@/api/subscription'

const loading = ref(false)
const initing = ref(false)
const renewing = ref(false)
const activeTab = ref('plans')

const plans = ref<any[]>([])
const subs = ref<any[]>([])
const dashboard = ref<any>(null)
const renewResult = ref<any>(null)

const memberQuery = ref<string>('')

function statusLabel(s?: string) {
  const m: Record<string, string> = { ACTIVE: '生效中', PAUSED: '已暂停', CANCELLED: '已取消', EXPIRED: '已过期' }
  return m[s || ''] || s || '—'
}

function statusTag(s?: string) {
  const m: Record<string, 'success' | 'info' | 'danger' | 'warning'> = {
    ACTIVE: 'success', PAUSED: 'warning', CANCELLED: 'danger', EXPIRED: 'info'
  }
  return m[s || ''] || 'info'
}

async function loadPlans() {
  loading.value = true
  try {
    const res: any = await subscriptionApi.listPlans()
    plans.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadSubs() {
  if (!memberQuery.value) {
    ElMessage.warning('请填写会员 ID')
    return
  }
  loading.value = true
  try {
    const res: any = await subscriptionApi.byMember(Number(memberQuery.value))
    subs.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadDashboard() {
  try {
    const res: any = await subscriptionApi.dashboard()
    dashboard.value = res.data || null
  } catch {
    dashboard.value = null
  }
}

function loadAll() {
  loadPlans()
  loadDashboard()
}

async function initDefault() {
  initing.value = true
  try {
    const res: any = await subscriptionApi.initDefaultPlans()
    ElMessage.success(`已创建 ${res.data?.created || 0} 个默认计划`)
    loadPlans()
  } finally {
    initing.value = false
  }
}

async function doUse(row: any) {
  await subscriptionApi.use({ memberId: row.memberId })
  ElMessage.success('已使用 1 次')
  loadSubs()
}

async function doPause(row: any) {
  await subscriptionApi.pause(row.id)
  ElMessage.success('已暂停')
  loadSubs()
}

async function doResume(row: any) {
  await subscriptionApi.resume(row.id)
  ElMessage.success('已恢复')
  loadSubs()
}

async function doCancel(row: any) {
  try {
    await ElMessageBox.confirm(`确认取消订阅「${row.subNo}」？`, '提示', { type: 'warning' })
  } catch { return }
  await subscriptionApi.cancel(row.id)
  ElMessage.success('已取消')
  loadSubs()
  loadDashboard()
}

async function triggerRenew() {
  renewing.value = true
  try {
    const res: any = await subscriptionApi.triggerRenew()
    renewResult.value = res.data || null
    ElMessage.success(`已扫描：${renewResult.value?.dueCount || 0} 条待续费`)
  } finally {
    renewing.value = false
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.subscription-page {
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
.stat-card {
  text-align: center;
  background: #f7f9fc;
}
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-value {
  color: #303133;
  font-size: 26px;
  font-weight: 600;
  margin-top: 8px;
}
.total-rev {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
.plan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.plan-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px;
  background: #fff;
  transition: all 0.2s;
}
.plan-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}
.plan-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.plan-head h3 {
  margin: 0;
  font-size: 15px;
}
.plan-card .price {
  color: #f56c6c;
  font-size: 28px;
  font-weight: 700;
  margin: 8px 0;
}
.plan-card .price .cycle {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 4px;
}
.plan-card .benefits {
  list-style: none;
  padding: 0;
  margin: 8px 0;
  font-size: 13px;
  color: #606266;
}
.plan-card .benefits li {
  margin: 4px 0;
}
.plan-card .stats {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>