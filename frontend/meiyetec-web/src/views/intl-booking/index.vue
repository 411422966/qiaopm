<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="渠道连接" name="connect">
        <el-row :gutter="16">
          <el-col v-for="c in channels" :key="c.id" :span="6" style="margin-bottom: 16px;">
            <el-card>
              <h3>{{ c.name }}</h3>
              <el-tag :type="c.status === 'ACTIVE' || c.status === 'READY' ? 'success' : 'info'">
                {{ c.status }}
              </el-tag>
              <div style="margin-top: 12px;">
                <el-button v-if="c.channel === 'GOOGLE'" size="small" @click="connectGoogle">绑定</el-button>
                <el-button v-if="c.channel === 'INSTAGRAM'" size="small" @click="connectInstagram">绑定</el-button>
                <el-button v-if="c.status === 'ACTIVE'" size="small" type="primary" @click="pull(c.id)">拉取预约</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
      <el-tab-pane label="国际预约" name="list">
        <el-table :data="bookings">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="source" label="渠道" width="100"/>
          <el-table-column prop="bookingNo" label="预约号" width="180"/>
          <el-table-column prop="customerName" label="客户" width="120"/>
          <el-table-column prop="customerLocale" label="语言" width="60"/>
          <el-table-column prop="serviceName" label="服务"/>
          <el-table-column prop="bookingDate" label="日期" width="100"/>
          <el-table-column prop="bookingTime" label="时间" width="60"/>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'NEW'" size="small" type="success" @click="setStatus(row, 'CONFIRMED')">确认</el-button>
              <el-button v-if="row.status !== 'CANCELLED' && row.status !== 'COMPLETED'" size="small" type="danger" @click="setStatus(row, 'CANCELLED')">取消</el-button>
              <el-button size="small" @click="viewTemplate(row.id)">确认模板</el-button>
              <el-button v-if="row.status === 'CONFIRMED' && !row.convertedMemberId" size="small" type="primary" @click="convert(row.id)">转化为会员</el-button>
              <el-tag v-if="row.convertedMemberId" size="small" type="warning">M:{{ row.convertedMemberId }} / O:{{ row.convertedOrderId }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="googleDialog" title="绑定 Google Business Profile" width="500px">
      <el-form :model="googleForm" label-width="120px">
        <el-form-item label="Client ID"><el-input v-model="googleForm.clientId" /></el-form-item>
        <el-form-item label="Client Secret"><el-input v-model="googleForm.clientSecret" type="password" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="googleDialog = false">取消</el-button>
        <el-button type="primary" @click="doGoogle">获取授权链接</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="igDialog" title="绑定 Instagram" width="500px">
      <el-form :model="igForm" label-width="120px">
        <el-form-item label="App ID"><el-input v-model="igForm.appId" /></el-form-item>
        <el-form-item label="App Secret"><el-input v-model="igForm.appSecret" type="password" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="igDialog = false">取消</el-button>
        <el-button type="primary" @click="doInstagram">获取授权链接</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialog" title="预约确认模板（多语言）" width="600px">
      <div v-if="template">
        <p><strong>语言代码：</strong>{{ template.locale }}</p>
        <p><strong>主题：</strong>{{ template.subject }}</p>
        <el-divider/>
        <pre style="white-space: pre-wrap;">{{ template.body }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { intlBookingApi } from '@/api/intl-booking'

const tab = ref('connect')
const channels = ref<any[]>([])
const bookings = ref<any[]>([])
const googleDialog = ref(false)
const igDialog = ref(false)
const googleForm = ref({ clientId: '', clientSecret: '' })
const igForm = ref({ appId: '', appSecret: '' })
const templateDialog = ref(false)
const template = ref<any>(null)

async function load() {
  const { data } = await intlBookingApi.listChannels(); channels.value = data || []
  const r = await intlBookingApi.listBookings(); bookings.value = r.data || []
}
async function connectGoogle() { googleDialog.value = true }
async function connectInstagram() { igDialog.value = true }
async function doGoogle() {
  const { data } = await intlBookingApi.connectGoogle(googleForm.value)
  googleDialog.value = false
  ElMessage.success(`请用浏览器打开授权链接完成绑定`)
  if (data?.authUrl) {
    window.open(data.authUrl, '_blank')
  }
  load()
}
async function doInstagram() {
  const { data } = await intlBookingApi.connectInstagram(igForm.value)
  igDialog.value = false
  ElMessage.success(`请用浏览器打开授权链接完成绑定`)
  if (data?.authUrl) {
    window.open(data.authUrl, '_blank')
  }
  load()
}
async function pull(id: number) {
  const { data } = await intlBookingApi.pullBookings(id)
  ElMessage.success(`拉取 ${data.pulled} 条`)
  load()
}
async function setStatus(row: any, status: string) {
  await intlBookingApi.updateStatus(row.id, { status })
  ElMessage.success('已更新状态')
  load()
}
async function convert(id: number) {
  try {
    const { data } = await intlBookingApi.convertBooking(id)
    ElMessage.success(`已转化为会员 ${data.memberId}，订单 ${data.orderId}`)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '转化失败')
  }
}
async function viewTemplate(id: number) {
  const { data } = await intlBookingApi.confirmationTemplate(id)
  template.value = data
  templateDialog.value = true
}
function statusTagType(status: string) {
  if (status === 'CONFIRMED' || status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED') return 'danger'
  return 'info'
}

onMounted(load)
</script>
