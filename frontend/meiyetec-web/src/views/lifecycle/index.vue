<template>
  <div class="lifecycle-page">
    <!-- 总览卡片 -->
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">会员生命周期营销（P4-02）</span>
          <el-space>
            <el-button type="primary" @click="loadDashboard" :loading="loading">刷新看板</el-button>
            <el-button @click="initDefaults" :loading="initing" type="success">
              初始化默认规则
            </el-button>
          </el-space>
        </div>
      </template>

      <el-descriptions :column="5" border v-if="dashboard">
        <el-descriptions-item label="统计区间">
          {{ dashboard.dateRange?.start }} ~ {{ dashboard.dateRange?.end }}
        </el-descriptions-item>
        <el-descriptions-item label="触发总数">
          <el-tag type="info" size="large">{{ dashboard.totalTriggered }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送成功">
          <el-tag type="success" size="large">{{ dashboard.totalSent }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已转化">
          <el-tag type="warning" size="large">{{ dashboard.totalUsed }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="转化率">
          <el-tag :type="dashboard.conversionRate >= 10 ? 'success' : 'danger'" size="large">
            {{ dashboard.conversionRate }}%
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="启用规则">
          <el-tag>{{ dashboard.activeRules }} / {{ dashboard.totalRules }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败次数">
          <el-tag :type="dashboard.totalFailed > 0 ? 'danger' : 'success'">
            {{ dashboard.totalFailed }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="dashboard?.byType?.length" class="by-type">
        <span class="label">按规则类型：</span>
        <el-tag v-for="t in dashboard.byType" :key="t.ruleType" style="margin-right:8px">
          {{ ruleTypeLabel(t.ruleType) }} · 触发 {{ t.total }} · 发送 {{ t.sent }} · 转化 {{ t.used }}
        </el-tag>
      </div>
    </el-card>

    <!-- 主 Tab -->
    <el-tabs v-model="activeTab" class="lifecycle-tabs">
      <!-- 1. 规则配置 -->
      <el-tab-pane label="规则配置" name="rules">
        <el-card>
          <el-table :data="rules" v-loading="rulesLoading" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="ruleCode" label="规则编码" width="180" />
            <el-table-column prop="ruleType" label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="ruleTypeColor(row.ruleType)">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ruleName" label="规则名称" min-width="200" />
            <el-table-column prop="rfmSegment" label="RFM 分群" width="140">
              <template #default="{ row }">{{ row.rfmSegment || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
                  {{ row.enabled === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  :type="row.enabled === 1 ? 'warning' : 'success'"
                  @click="toggleRule(row)"
                >
                  {{ row.enabled === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button size="small" type="primary" @click="triggerRule(row)">
                  手动触发
                </el-button>
                <el-button size="small" type="danger" @click="deleteRule(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 2. 触发日志 -->
      <el-tab-pane label="触发日志" name="logs">
        <el-card>
          <div class="filter-row">
            <el-select v-model="logFilter.ruleType" clearable placeholder="规则类型" style="width:160px">
              <el-option label="生日" value="BIRTHDAY" />
              <el-option label="会员日" value="MEMBER_DAY" />
              <el-option label="复购唤醒" value="RETENTION" />
              <el-option label="裂变邀请" value="INVITE" />
            </el-select>
            <el-select v-model="logFilter.status" clearable placeholder="状态" style="width:140px">
              <el-option label="PENDING" value="PENDING" />
              <el-option label="SENT" value="SENT" />
              <el-option label="FAILED" value="FAILED" />
              <el-option label="USED" value="USED" />
              <el-option label="EXPIRED" value="EXPIRED" />
            </el-select>
            <el-button type="primary" @click="loadLogs">查询</el-button>
          </div>

          <el-table :data="logs" v-loading="logsLoading" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="triggerDate" label="触发日期" width="120" />
            <el-table-column label="规则" min-width="220">
              <template #default="{ row }">
                <div>{{ row.ruleCode }} · {{ row.ruleName }}</div>
                <el-tag size="small">{{ ruleTypeLabel(row.ruleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="会员" width="180">
              <template #default="{ row }">
                <div>{{ row.memberName || '—' }}</div>
                <div class="muted">{{ row.memberPhone }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="triggerReason" label="触发原因" min-width="180" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusColor(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sentAt" label="发送时间" width="170" />
            <el-table-column prop="errorMessage" label="错误" min-width="200">
              <template #default="{ row }">
                <span class="error-msg">{{ row.errorMessage || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="logPageNum"
            v-model:page-size="logPageSize"
            :total="logTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadLogs"
            @size-change="loadLogs"
            style="margin-top:16px; text-align:right"
          />
        </el-card>
      </el-tab-pane>

      <!-- 3. 邀请码演示 -->
      <el-tab-pane label="裂变邀请" name="invite">
        <el-card>
          <div class="invite-test">
            <el-input
              v-model="memberId"
              placeholder="输入会员 ID"
              style="width:200px; margin-right:12px"
            />
            <el-button type="primary" @click="generateInviteCode" :loading="inviteLoading">
              生成邀请码
            </el-button>
            <el-button @click="loadInviteRewards">查看奖励</el-button>
          </div>

          <div v-if="inviteCode" class="invite-result">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="邀请码">
                <el-tag type="success" size="large">{{ inviteCode.inviteCode }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag>{{ inviteCode.status }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="邀请次数">{{ inviteCode.inviteCount }}</el-descriptions-item>
              <el-descriptions-item label="成功注册">{{ inviteCode.successCount }}</el-descriptions-item>
              <el-descriptions-item label="奖励金额">¥{{ inviteCode.rewardCouponAmount }}</el-descriptions-item>
              <el-descriptions-item label="失效时间">{{ inviteCode.expireAt }}</el-descriptions-item>
              <el-descriptions-item label="分享文案" :span="2">
                <div class="share-text">{{ inviteCode.shareText }}</div>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <div v-if="inviteRewards.length" class="invite-rewards">
            <h4>我的裂变奖励（最近 100 条）</h4>
            <el-table :data="inviteRewards" stripe>
              <el-table-column prop="inviteeName" label="被邀请人" width="120" />
              <el-table-column prop="inviteePhone" label="手机号" width="140" />
              <el-table-column prop="registerAt" label="注册时间" width="170" />
              <el-table-column label="邀请人奖励" min-width="200">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.inviterRewardType }}</el-tag>
                  {{ row.inviterRewardValue }}
                  <el-tag size="small" :type="statusColor(row.inviterRewardStatus)">
                    {{ row.inviterRewardStatus }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="被邀请人奖励" min-width="200">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.inviteeRewardType }}</el-tag>
                  {{ row.inviteeRewardValue }}
                  <el-tag size="small" :type="statusColor(row.inviteeRewardStatus)">
                    {{ row.inviteeRewardStatus }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
const http = request

const loading = ref(false)
const initing = ref(false)
const dashboard = ref<any>(null)
const activeTab = ref('rules')

// 规则
const rules = ref<any[]>([])
const rulesLoading = ref(false)

// 日志
const logs = ref<any[]>([])
const logsLoading = ref(false)
const logPageNum = ref(1)
const logPageSize = ref(20)
const logTotal = ref(0)
const logFilter = reactive({ ruleType: '', status: '' })

// 邀请码
const memberId = ref('1')
const inviteCode = ref<any>(null)
const inviteLoading = ref(false)
const inviteRewards = ref<any[]>([])

onMounted(() => {
  loadDashboard()
  loadRules()
})

const ruleTypeLabel = (t: string) =>
  ({ BIRTHDAY: '生日', MEMBER_DAY: '会员日', RETENTION: '复购唤醒', INVITE: '裂变邀请' }[t] || t)

const ruleTypeColor = (t: string) =>
  ({ BIRTHDAY: 'success', MEMBER_DAY: 'warning', RETENTION: 'danger', INVITE: 'info' }[t] || '')

const statusColor = (s: string) => {
  const m: Record<string, string> = {
    SENT: 'success', USED: 'success', PENDING: 'warning',
    FAILED: 'danger', EXPIRED: 'info', ACTIVE: 'success', DISABLED: 'info'
  }
  return m[s] || ''
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const r = await http.get('/lifecycle/dashboard')
    dashboard.value = r.data
  } finally {
    loading.value = false
  }
}

const initDefaults = async () => {
  initing.value = true
  try {
    const r = await http.post('/lifecycle/rule/init-default')
    ElMessage.success(r.message || `已初始化 ${r.data} 条规则`)
    loadRules()
    loadDashboard()
  } finally {
    initing.value = false
  }
}

const loadRules = async () => {
  rulesLoading.value = true
  try {
    const r = await http.get('/lifecycle/rule/page', { params: { pageNum: 1, pageSize: 50 } })
    rules.value = r.data?.records || []
  } finally {
    rulesLoading.value = false
  }
}

const toggleRule = async (row: any) => {
  const enable = row.enabled !== 1
  const r = await http.post(`/lifecycle/rule/${row.id}/toggle`, null, { params: { enable } })
  ElMessage.success(r.message)
  loadRules()
  loadDashboard()
}

const triggerRule = async (row: any) => {
  const r = await http.post(`/lifecycle/trigger/${row.id}`)
  ElMessage.success(`${r.message} · 触发 ${r.data?.triggered || 0} 人`)
  loadLogs()
}

const deleteRule = async (row: any) => {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '提示', { type: 'warning' })
  const r = await http.delete(`/lifecycle/rule/${row.id}`)
  ElMessage.success(r.message)
  loadRules()
  loadDashboard()
}

const loadLogs = async () => {
  logsLoading.value = true
  try {
    const r = await http.get('/lifecycle/log/page', {
      params: {
        pageNum: logPageNum.value,
        pageSize: logPageSize.value,
        ruleType: logFilter.ruleType || undefined,
        status: logFilter.status || undefined
      }
    })
    logs.value = r.data?.records || []
    logTotal.value = r.data?.total || 0
  } finally {
    logsLoading.value = false
  }
}

const generateInviteCode = async () => {
  inviteLoading.value = true
  try {
    const r = await http.get('/lifecycle/invite/code', { params: { memberId: memberId.value } })
    inviteCode.value = r.data
    ElMessage.success(r.message)
  } finally {
    inviteLoading.value = false
  }
}

const loadInviteRewards = async () => {
  const r = await http.get('/lifecycle/invite/my-invites', { params: { memberId: memberId.value } })
  inviteRewards.value = r.data || []
}
</script>

<style scoped>
.lifecycle-page {
  padding: 16px;
}
.overview-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header .title {
  font-size: 18px;
  font-weight: 600;
}
.by-type {
  margin-top: 16px;
}
.by-type .label {
  margin-right: 8px;
  color: #606266;
  font-weight: 500;
}
.lifecycle-tabs {
  margin-top: 16px;
}
.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.error-msg {
  color: #f56c6c;
  font-size: 12px;
}
.invite-test {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
}
.invite-result {
  margin-bottom: 24px;
}
.share-text {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  color: #303133;
  white-space: pre-wrap;
}
.invite-rewards h4 {
  margin-bottom: 12px;
  color: #303133;
}
</style>