<template>
  <div class="page-dcep">
    <el-tabs v-model="tab">
      <el-tab-pane label="通道" name="channel">
        <el-button type="primary" @click="healthCheck(form.id)" v-if="form.id">健康检查</el-button>
        <el-table :data="channels" stripe>
          <el-table-column prop="channelCode" label="通道编码" />
          <el-table-column prop="channelName" label="通道名" />
          <el-table-column prop="merchantId" label="商户号" />
          <el-table-column prop="envMode" label="环境" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="row.status ? 'success' : 'danger'">{{ row.status ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastHealthMsg" label="健康" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="预支付" name="prepay">
        <el-form :inline="true">
          <el-form-item label="业务订单ID"><el-input v-model.number="form.bizOrderId" style="width:140px" /></el-form-item>
          <el-form-item label="金额(分)"><el-input v-model.number="form.amountCents" style="width:140px" /></el-form-item>
          <el-form-item label="通道"><el-input v-model="form.channelCode" style="width:180px" /></el-form-item>
          <el-form-item label="用户手机"><el-input v-model="form.payerPhone" style="width:140px" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="prepay" :loading="loading">预支付</el-button>
          </el-form-item>
        </el-form>
        <el-card v-if="result">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="DCEP 订单号">{{ result.dcepOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="预支付 ID">{{ result.prepayId }}</el-descriptions-item>
            <el-descriptions-item label="过期时间">{{ result.expireAt }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="result.status === 1 ? 'warning' : result.status === 2 ? 'success' : 'danger'">
                {{ statusName(result.status) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="订单列表" name="orders">
        <el-table :data="orders" stripe>
          <el-table-column prop="bizOrderNo" label="业务订单号" />
          <el-table-column prop="dcepOrderNo" label="DCEP 单号" />
          <el-table-column prop="channelCode" label="通道" />
          <el-table-column prop="amountCents" label="金额(分)" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="row.status === 2 ? 'success' : 'warning'">{{ statusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="paidAt" label="支付时间" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const tab = ref('channel')
const channels = ref([])
const orders = ref([])
const form = reactive({ bizOrderId: null, amountCents: 10000, channelCode: 'DCEP-DCBC-MOCK', payerPhone: '13800000000', id: null })
const result = ref(null)
const loading = ref(false)

async function loadChannels() {
  const { data } = await http.get('/pay/dcep/channel/list')
  channels.value = data || []
  if (channels.value.length) form.id = channels.value[0].id
}
async function loadOrders() {
  const { data } = await http.get('/pay/dcep/order/page', { params: { pageSize: 20 } })
  orders.value = data.records || []
}
async function healthCheck(id) {
  const { data } = await http.post(`/pay/dcep/channel/${id}/health`)
  ElMessage.success(`健康: ${data.healthy ? 'OK' : 'FAIL'}`)
}
async function prepay() {
  loading.value = true
  try {
    const { data } = await http.post('/pay/dcep/prepay', { ...form, bizOrderNo: 'BIZ' + Date.now() })
    result.value = data
    ElMessage.success('预支付已发起')
  } finally { loading.value = false }
}
function statusName(s) {
  return ({1:'待支付',2:'已支付',3:'已关闭',4:'已退款',5:'失败'})[s] || s
}
onMounted(() => { loadChannels(); loadOrders() })
</script>

<style scoped>
.page-dcep { padding: 16px; }
</style>