<template>
  <div class="page-fiscal">
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="s in statCards" :key="s.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="stat-hint">{{ s.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="nav-row">
      <el-col :span="8">
        <el-card shadow="hover" class="nav-card" @click="$router.push('/fiscal/config')">
          <div class="nav-icon">⚙️</div>
          <div class="nav-title">渠道配置</div>
          <div class="nav-desc">百望 / 航天信息 / 模拟通道的凭据与销方信息</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="nav-card" @click="$router.push('/fiscal/list')">
          <div class="nav-icon">📄</div>
          <div class="nav-title">发票列表</div>
          <div class="nav-desc">开具 / 作废 / 状态机 + 红冲按钮</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="nav-card" @click="issueDialog = true">
          <div class="nav-icon">➕</div>
          <div class="nav-title">开具发票</div>
          <div class="nav-desc">为已支付订单开具百望电子发票</div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="issueDialog" title="开具发票" width="500px">
      <el-form :model="issueForm" label-width="120px">
        <el-form-item label="订单ID"><el-input-number v-model="issueForm.orderId" /></el-form-item>
        <el-form-item label="金额(元)"><el-input-number v-model="issueForm.amount" :precision="2" :step="100" /></el-form-item>
        <el-form-item label="购买方"><el-input v-model="issueForm.buyerName" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="issueForm.buyerTaxNo" /></el-form-item>
        <el-form-item label="通道">
          <el-select v-model="issueForm.channel">
            <el-option label="百望电子发票" value="BAI_WANG"/>
            <el-option label="航天信息" value="YIN_TIAN"/>
            <el-option label="数字人民币" value="DCEP"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialog = false">取消</el-button>
        <el-button type="primary" @click="doIssue">开具</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { fiscalApi } from '@/api/fiscal'

const router = useRouter()
const dashboard = ref<any>({})
const issueDialog = ref(false)
const issueForm = ref<any>({
  orderId: 1, amount: 100,
  buyerName: '某某有限公司', buyerTaxNo: '91110000XXXXX',
  channel: 'BAI_WANG'
})

const statCards = computed(() => [
  { label: '已开票',   value: dashboard.value.totalIssued  ?? 0, hint: 'ISSUED 状态',  color: '#67c23a' },
  { label: '已红冲',   value: dashboard.value.totalVoided  ?? 0, hint: 'VOIDED 状态',  color: '#909399' },
  { label: '待开票',   value: dashboard.value.totalPending ?? 0, hint: 'PENDING 状态', color: '#e6a23c' },
  { label: '失败',     value: dashboard.value.totalFailed  ?? 0, hint: 'FAILED 状态',  color: '#f56c6c' }
])

async function loadDashboard() {
  const { data } = await fiscalApi.dashboard()
  dashboard.value = data || {}
}
async function doIssue() {
  await fiscalApi.issue(issueForm.value)
  ElMessage.success('已开具')
  issueDialog.value = false
  loadDashboard()
}

onMounted(() => { loadDashboard() })
</script>

<style scoped>
.page-fiscal { padding: 16px; }
.stat-row { margin-bottom: 16px; }
.stat-card .stat-label { color: #909399; font-size: 13px; }
.stat-card .stat-value { font-size: 28px; font-weight: 700; margin: 8px 0; }
.stat-card .stat-hint { color: #c0c4cc; font-size: 12px; }
.nav-row { margin-top: 8px; }
.nav-card { cursor: pointer; text-align: center; padding: 16px 0; transition: transform .2s; }
.nav-card:hover { transform: translateY(-2px); }
.nav-icon { font-size: 32px; margin-bottom: 8px; }
.nav-title { font-weight: 600; font-size: 16px; margin-bottom: 4px; }
.nav-desc { color: #909399; font-size: 12px; }
</style>
