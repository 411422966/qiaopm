<template>
  <div class="posd-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="设备列表" name="device">
        <el-button type="primary" @click="regDialog = true">注册终端</el-button>
        <el-table :data="devices" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="deviceCode" label="编码" width="120"/>
          <el-table-column prop="deviceName" label="名称"/>
          <el-table-column prop="vendor" label="供应商" width="100"/>
          <el-table-column prop="locationId" label="位置ID" width="100"/>
          <el-table-column prop="status" label="状态" width="80"/>
          <el-table-column prop="lastHeartbeat" label="心跳" width="180"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="交易列表" name="tx">
        <el-table :data="txs" style="width:100%;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="posId" label="设备" width="80"/>
          <el-table-column prop="txNo" label="交易号" width="180"/>
          <el-table-column prop="amount" label="金额" width="100"/>
          <el-table-column prop="currency" label="货币" width="80"/>
          <el-table-column prop="cardBrand" label="卡品牌" width="100"/>
          <el-table-column prop="status" label="状态" width="100"/>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="regDialog" title="注册海外 POS" width="500px">
      <el-form :model="regForm" label-width="120px">
        <el-form-item label="设备编码"><el-input v-model="regForm.deviceCode" /></el-form-item>
        <el-form-item label="设备名称"><el-input v-model="regForm.deviceName" /></el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="regForm.vendor">
            <el-option label="Square" value="SQUARE"/>
            <el-option label="Stripe" value="STRIPE"/>
            <el-option label="SumUp" value="SUMUP"/>
          </el-select>
        </el-form-item>
        <el-form-item label="位置ID"><el-input v-model="regForm.locationId" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regDialog = false">取消</el-button>
        <el-button type="primary" @click="doReg">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { posDeviceApi } from '@/api/pos-device'

const tab = ref('device')
const devices = ref<any[]>([])
const txs = ref<any[]>([])
const regDialog = ref(false)
const regForm = ref<any>({ deviceCode: 'SQ' + Date.now(), deviceName: '海外POS', vendor: 'SQUARE', locationId: 'LOC001' })

async function loadDevices() { const { data } = await posDeviceApi.listDevices(); devices.value = data || [] }
async function loadTxs() { const { data } = await posDeviceApi.listTransactions(); txs.value = data || [] }
async function doReg() {
  await posDeviceApi.register(regForm.value)
  ElMessage.success('已注册')
  regDialog.value = false
  loadDevices()
}

onMounted(() => { loadDevices(); loadTxs() })
</script>
