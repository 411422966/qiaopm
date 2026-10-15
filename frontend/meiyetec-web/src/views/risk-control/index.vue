<template>
  <div class="page">
    <el-row :gutter="16" class="mb16">
      <el-col :span="6"><el-card><div class="stat"><span>规则数</span><strong>{{ stats.rules }}</strong></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><span>拦截数</span><strong>{{ stats.blocked }}</strong></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><span>预警数</span><strong>{{ stats.alerts }}</strong></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat"><span>规则启用率</span><strong>{{ stats.enableRate }}%</strong></div></el-card></el-col>
    </el-row>

    <el-tabs v-model="tab">
      <el-tab-pane label="规则管理" name="rule">
        <el-table :data="rules">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="ruleCode" label="编码" width="120"/>
          <el-table-column prop="ruleName" label="名称"/>
          <el-table-column prop="threshold" label="阈值" width="100"/>
          <el-table-column prop="action" label="动作" width="100"/>
          <el-table-column prop="level" label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="row.level === 'HIGH' ? 'danger' : (row.level === 'MEDIUM' ? 'warning' : 'info')">{{ row.level }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" @change="toggle(row)" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="拦截日志" name="alert">
        <el-table :data="alerts">
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="amount" label="金额" width="100"/>
          <el-table-column prop="channel" label="通道" width="100"/>
          <el-table-column prop="action" label="动作" width="100">
            <template #default="{ row }">
              <el-tag :type="row.action === 'BLOCKED' ? 'danger' : 'warning'">{{ row.action }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="level" label="级别" width="100"/>
          <el-table-column label="命中规则">
            <template #default="{ row }">{{ (row.matchedRules || []).join(', ') }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="180"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="模拟检测" name="check">
        <el-form :model="checkForm" label-width="100px" style="max-width: 500px;">
          <el-form-item label="金额"><el-input-number v-model="checkForm.amount" :precision="2" /></el-form-item>
          <el-form-item label="通道"><el-input v-model="checkForm.channel" placeholder="STRIPE / WECHAT" /></el-form-item>
          <el-form-item label="会员ID"><el-input-number v-model="checkForm.memberId" /></el-form-item>
          <el-button type="primary" @click="runCheck">执行检测</el-button>
        </el-form>
        <el-card v-if="lastResult" style="margin-top: 20px;">
          <h3>检测结果</h3>
          <p>动作: <el-tag :type="lastResult.action === 'BLOCKED' ? 'danger' : (lastResult.action === 'ALERT' ? 'warning' : 'success')">{{ lastResult.action }}</el-tag></p>
          <p>级别: {{ lastResult.level }}</p>
          <p>命中规则: {{ (lastResult.matchedRules || []).join(', ') || '无' }}</p>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { riskControlApi } from '@/api/risk-control'

const tab = ref('rule')
const rules = ref<any[]>([])
const alerts = ref<any[]>([])
const lastResult = ref<any>(null)
const checkForm = ref({ amount: 100, channel: 'STRIPE', memberId: 1 })
const stats = computed(() => {
  const enabled = rules.value.filter(r => r.enabled).length
  return {
    rules: rules.value.length,
    blocked: alerts.value.filter(a => a.action === 'BLOCKED').length,
    alerts: alerts.value.length,
    enableRate: rules.value.length ? Math.round(enabled / rules.value.length * 100) : 0
  }
})

async function load() {
  const r = await riskControlApi.listRules(); rules.value = r.data || []
  const a = await riskControlApi.listAlerts(); alerts.value = a.data || []
}
async function toggle(row: any) {
  await riskControlApi.toggleRule(row.id, { enabled: row.enabled })
  ElMessage.success('已切换')
}
async function runCheck() {
  const { data } = await riskControlApi.checkTransaction(checkForm.value)
  lastResult.value = data
  load()
}

onMounted(load)
</script>

<style scoped>
.stat { display:flex; justify-content:space-between; align-items:center; }
.mb16 { margin-bottom:16px; }
</style>
