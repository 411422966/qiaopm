<template>
  <div class="ltv-churn-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">LTV / Churn 智能分析</span>
          <el-space>
            <el-button type="primary" @click="computeAll" :loading="computing">立即全量计算</el-button>
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-row :gutter="16" v-if="dashboard">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-blue">
            <div class="stat-label">LTV 累计价值</div>
            <div class="stat-value">¥{{ Math.round(dashboard.totalLtv || 0).toLocaleString() }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-green">
            <div class="stat-label">会员平均 LTV</div>
            <div class="stat-value">¥{{ Math.round(dashboard.avgLtv || 0).toLocaleString() }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-red">
            <div class="stat-label">高流失风险</div>
            <div class="stat-value">{{ dashboard.highRiskCount || 0 }}</div>
            <div class="stat-hint">需立即触达</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-orange">
            <div class="stat-label">中流失风险</div>
            <div class="stat-value">{{ dashboard.mediumRiskCount || 0 }}</div>
            <div class="stat-hint">需主动关怀</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-tabs v-model="activeTab">
      <!-- LTV Top -->
      <el-tab-pane label="LTV 高价值客户" name="ltv">
        <el-card>
          <div class="toolbar">
            <el-input v-model="ltvFilter.limit" placeholder="显示前 N 名" style="width:140px" type="number" />
            <el-button @click="loadLtvTop">查询</el-button>
          </div>
          <el-table :data="ltvTop" v-loading="loading" border stripe>
            <el-table-column prop="member_id" label="会员 ID" width="100" />
            <el-table-column label="LTV" width="160">
              <template #default="{ row }">
                <span class="ltv-value">¥{{ Number(row.value).toLocaleString(undefined, { maximumFractionDigits: 2 }) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="period" label="周期" width="120" />
            <el-table-column prop="value_type" label="类型" width="100" />
            <el-table-column label="详情" min-width="300">
              <template #default="{ row }">
                <span class="details">{{ row.details || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="计算时间" width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- Churn 风险 -->
      <el-tab-pane label="流失风险 Top" name="churn">
        <el-card>
          <div class="toolbar">
            <el-input v-model="churnFilter.limit" placeholder="显示前 N 名" style="width:140px" type="number" />
            <el-button @click="loadChurnTop">查询</el-button>
          </div>
          <el-table :data="churnTop" v-loading="loading" border stripe>
            <el-table-column prop="member_id" label="会员 ID" width="100" />
            <el-table-column label="风险评分" width="120">
              <template #default="{ row }">
                <el-tag :type="(Number(row.value) || 0) >= 70 ? 'danger' : (Number(row.value) || 0) >= 40 ? 'warning' : 'success'" size="small">
                  {{ Math.round(row.value) }} 分
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="等级" width="100">
              <template #default="{ row }">
                <el-tag :type="row.value >= 70 ? 'danger' : row.value >= 40 ? 'warning' : 'success'" size="small">
                  {{ row.value >= 70 ? '高' : row.value >= 40 ? '中' : '低' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="详情" min-width="400">
              <template #default="{ row }">
                <span class="details">{{ row.details || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="计算时间" width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 单会员查询 -->
      <el-tab-pane label="单会员查询" name="member">
        <el-card>
          <div class="toolbar">
            <el-input-number v-model="memberQuery" :min="1" placeholder="会员 ID" />
            <el-button type="primary" @click="queryMember">查询</el-button>
          </div>
          <div v-if="memberData" class="member-result">
            <el-row :gutter="16">
              <el-col :span="12">
                <h4>LTV（客户终身价值）</h4>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="估算 LTV" :span="2">
                    <span class="big-value">¥{{ memberData.ltv?.ltv || 0 }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="历史消费">¥{{ memberData.ltv?.totalRevenue || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="订单数">{{ memberData.ltv?.orderCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="客单价">¥{{ memberData.ltv?.avgOrderValue || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="复购率">{{ memberData.ltv?.repurchaseRate || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="首单日期">{{ memberData.ltv?.firstOrderDate || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="末单日期">{{ memberData.ltv?.lastOrderDate || '—' }}</el-descriptions-item>
                </el-descriptions>
              </el-col>
              <el-col :span="12">
                <h4>Churn（流失风险）</h4>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="风险评分" :span="2">
                    <el-tag :type="(memberData.churn?.riskScore || 0) >= 70 ? 'danger' : (memberData.churn?.riskScore || 0) >= 40 ? 'warning' : 'success'">
                      {{ memberData.churn?.riskScore || 0 }} 分 ·
                      {{ memberData.churn?.riskLevel === 'HIGH' ? '高' : memberData.churn?.riskLevel === 'MEDIUM' ? '中' : '低' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="距上次消费">{{ memberData.churn?.daysSinceLast }} 天</el-descriptions-item>
                  <el-descriptions-item label="消费次数">{{ memberData.churn?.orderCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="未关闭投诉">{{ memberData.churn?.complaintCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="建议触达" :span="2">
                    <el-tag>{{ memberData.churn?.recommendation || '—' }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="触发原因" :span="2">
                    <el-tag v-for="r in memberData.churn?.reasons || []" :key="r" style="margin-right:6px">{{ r }}</el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ltvChurnApi } from '@/api/ltv-churn'

const loading = ref(false)
const computing = ref(false)
const activeTab = ref('ltv')

const dashboard = ref<any>(null)
const ltvTop = ref<any[]>([])
const churnTop = ref<any[]>([])
const memberData = ref<any>(null)

const ltvFilter = reactive({ limit: 100 })
const churnFilter = reactive({ limit: 100 })
const memberQuery = ref<number | null>(null)

async function loadDashboard() {
  try {
    const res: any = await ltvChurnApi.dashboard()
    dashboard.value = res.data || null
  } catch {
    dashboard.value = null
  }
}

async function loadLtvTop() {
  loading.value = true
  try {
    const res: any = await ltvChurnApi.topLtv(ltvFilter.limit || 100)
    ltvTop.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadChurnTop() {
  loading.value = true
  try {
    const res: any = await ltvChurnApi.churnRiskList(churnFilter.limit || 100)
    churnTop.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function computeAll() {
  computing.value = true
  try {
    await ltvChurnApi.computeLtv()
    await ltvChurnApi.computeChurn()
    ElMessage.success('LTV + Churn 已全量计算')
    loadDashboard()
    loadLtvTop()
    loadChurnTop()
  } finally {
    computing.value = false
  }
}

async function queryMember() {
  if (!memberQuery.value) {
    ElMessage.warning('请填写会员 ID')
    return
  }
  loading.value = true
  try {
    const [ltvRes, churnRes]: any[] = await Promise.all([
      ltvChurnApi.memberLtv(memberQuery.value),
      ltvChurnApi.memberChurn(memberQuery.value)
    ])
    memberData.value = {
      ltv: ltvRes.data,
      churn: churnRes.data
    }
  } finally {
    loading.value = false
  }
}

function loadAll() {
  loadDashboard()
  loadLtvTop()
  loadChurnTop()
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.ltv-churn-page {
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
  color: #fff;
}
.stat-card.stat-blue { background: linear-gradient(135deg, #409eff, #66b1ff); }
.stat-card.stat-green { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-card.stat-red { background: linear-gradient(135deg, #f56c6c, #f89898); }
.stat-card.stat-orange { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.stat-label {
  color: rgba(255,255,255,0.85);
  font-size: 13px;
}
.stat-value {
  color: #fff;
  font-size: 26px;
  font-weight: 600;
  margin-top: 8px;
}
.stat-hint {
  color: rgba(255,255,255,0.7);
  font-size: 11px;
  margin-top: 4px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.ltv-value {
  color: #67c23a;
  font-weight: 600;
}
.details {
  color: #909399;
  font-size: 12px;
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.member-result {
  margin-top: 12px;
}
.member-result h4 {
  margin: 8px 0;
}
.big-value {
  color: #67c23a;
  font-weight: 700;
  font-size: 18px;
}
</style>