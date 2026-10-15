<template>
  <div class="gdpr-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="同意管理" name="consent">
        <el-button type="primary" @click="grantDialog = true">记录同意</el-button>
        <el-table :data="consents" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="memberId" label="会员" width="80"/>
          <el-table-column prop="consentType" label="用途" width="120"/>
          <el-table-column label="已授权" width="100">
            <template #default="{ row }">
              <el-tag :type="row.granted === 1 ? 'success' : 'danger'">{{ row.granted === 1 ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="100"/>
          <el-table-column prop="channel" label="渠道" width="100"/>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.granted === 1" size="small" type="danger" @click="revoke(row.id)">撤销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="数据请求" name="request">
        <el-button type="primary" @click="reqDialog = true">提交请求</el-button>
        <el-table :data="requests" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="requestNo" label="请求号" width="180"/>
          <el-table-column prop="memberId" label="会员" width="80"/>
          <el-table-column prop="requestType" label="类型" width="100"/>
          <el-table-column prop="status" label="状态" width="100"/>
          <el-table-column prop="reason" label="原因"/>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="processRequest(row, 'COMPLETED')">完成</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="审计日志" name="audit">
        <el-table :data="auditLogs" style="width:100%;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="actorName" label="操作者" width="120"/>
          <el-table-column prop="action" label="动作" width="200"/>
          <el-table-column prop="targetType" label="目标" width="120"/>
          <el-table-column prop="details" label="详情"/>
          <el-table-column prop="ipAddress" label="IP" width="120"/>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="grantDialog" title="记录用户同意" width="400px">
      <el-form :model="grantForm" label-width="120px">
        <el-form-item label="会员ID"><el-input-number v-model="grantForm.memberId" /></el-form-item>
        <el-form-item label="用途">
          <el-select v-model="grantForm.consentType">
            <el-option label="MARKETING" value="MARKETING"/>
            <el-option label="ANALYTICS" value="ANALYTICS"/>
            <el-option label="DATA_SHARE" value="DATA_SHARE"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialog = false">取消</el-button>
        <el-button type="primary" @click="doGrant">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reqDialog" title="提交数据请求" width="400px">
      <el-form :model="reqForm" label-width="120px">
        <el-form-item label="会员ID"><el-input-number v-model="reqForm.memberId" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="reqForm.requestType">
            <el-radio value="EXPORT">数据导出</el-radio>
            <el-radio value="DELETE">删除数据</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reqDialog = false">取消</el-button>
        <el-button type="primary" @click="doRequest">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const tab = ref('consent')
const consents = ref<any[]>([])
const requests = ref<any[]>([])
const auditLogs = ref<any[]>([])
const grantDialog = ref(false)
const reqDialog = ref(false)
const grantForm = ref({ memberId: 1, consentType: 'MARKETING' })
const reqForm = ref({ memberId: 1, requestType: 'EXPORT' })

async function loadConsents() {
  const r: any = await http.get('/gdpr/consent/list')
  consents.value = r.data || []
}
async function loadRequests() {
  const r: any = await http.get('/gdpr/data-request/list')
  requests.value = r.data || []
}
async function loadAudit() {
  const r: any = await http.get('/gdpr/audit-log/list')
  auditLogs.value = r.data || []
}
async function revoke(id: number) {
  await http.post(`/gdpr/consent/${id}/revoke`)
  ElMessage.success('已撤销')
  loadConsents()
  loadAudit()
}
async function doGrant() {
  await http.post('/gdpr/consent', grantForm.value)
  ElMessage.success('已记录')
  grantDialog.value = false
  loadConsents()
  loadAudit()
}
async function doRequest() {
  await http.post('/gdpr/data-request', { memberId: reqForm.value.memberId, requestType: reqForm.value.requestType })
  ElMessage.success('已提交')
  reqDialog.value = false
  loadRequests()
}
async function processRequest(row: any, status: string) {
  await http.post(`/gdpr/data-request/${row.id}/process`, { status })
  ElMessage.success('已更新')
  loadRequests()
}

onMounted(() => { loadConsents(); loadRequests(); loadAudit() })
</script>
