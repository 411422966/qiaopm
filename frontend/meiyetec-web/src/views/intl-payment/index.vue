<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="支付网关" name="gateway">
        <el-row :gutter="16">
          <el-col v-for="g in gateways" :key="g.code" :span="8" style="margin-bottom: 16px;">
            <el-card>
              <h3>{{ g.name }}</h3>
              <p>费率: {{ g.fee }}</p>
              <p>支持币种: {{ g.currency }}</p>
              <el-tag v-if="g.feature" type="success">{{ g.feature }}</el-tag>
            </el-card>
          </el-col>
        </el-row>
        <el-button type="primary" @click="chargeDialog = true">发起一笔测试支付</el-button>
      </el-tab-pane>
      <el-tab-pane label="交易列表" name="list">
        <el-table :data="charges">
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="gateway" label="网关" width="100"/>
          <el-table-column prop="amount" label="金额" width="100"/>
          <el-table-column prop="currency" label="币种" width="80"/>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCEEDED' ? 'success' : (row.status === 'REFUNDED' ? 'warning' : 'danger')">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="txnNo" label="交易号" width="200"/>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button v-if="row.status === 'SUCCEEDED'" size="small" @click="refund(row.id)">退款</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="chargeDialog" title="发起支付" width="400px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="网关">
          <el-select v-model="form.gateway">
            <el-option v-for="g in gateways" :key="g.code" :label="g.name" :value="g.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :precision="2" /></el-form-item>
        <el-form-item label="币种"><el-input v-model="form.currency" /></el-form-item>
        <el-form-item label="订单ID"><el-input-number v-model="form.orderId" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chargeDialog = false">取消</el-button>
        <el-button type="primary" @click="doCharge">发起</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { intlPaymentApi } from '@/api/intl-payment'

const tab = ref('gateway')
const gateways = ref<any[]>([])
const charges = ref<any[]>([])
const chargeDialog = ref(false)
const form = ref({ gateway: 'STRIPE', amount: 100, currency: 'USD', orderId: 1 })

async function load() {
  const { data } = await intlPaymentApi.listGateways(); gateways.value = data || []
  const r = await intlPaymentApi.listCharges(); charges.value = r.data || []
}
async function doCharge() {
  await intlPaymentApi.createCharge(form.value)
  ElMessage.success('已发起')
  chargeDialog.value = false
  load()
}
async function refund(id: number) {
  await intlPaymentApi.refund(id, { reason: '用户申请' })
  ElMessage.success('已退款')
  load()
}

onMounted(load)
</script>
