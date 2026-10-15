<template>
  <div class="comp-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="SOP 模板" name="sop">
        <el-button type="primary" @click="initDefault">初始化 5 个默认 SOP</el-button>
        <el-button @click="sopDialog = true" style="margin-left: 8px;">新建 SOP</el-button>
        <el-table :data="sops" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="sopCode" label="编码" width="120"/>
          <el-table-column prop="sopName" label="名称" width="120"/>
          <el-table-column prop="category" label="类别" width="100"/>
          <el-table-column prop="severity" label="严重度" width="100">
            <template #default="{ row }">
              <el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="triggerTiming" label="响应窗口"/>
          <el-table-column prop="responsibleRole" label="责任人" width="120"/>
          <el-table-column prop="status" label="状态" width="80"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="并发症事件" name="event">
        <el-button type="danger" @click="eventDialog = true">上报事件</el-button>
        <el-table :data="events" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="memberId" label="会员" width="80"/>
          <el-table-column prop="sopCode" label="SOP" width="120"/>
          <el-table-column prop="severity" label="严重度" width="100">
            <template #default="{ row }">
              <el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="symptoms" label="症状"/>
          <el-table-column prop="status" label="状态" width="100"/>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="resolveEvent(row)">结案</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="sopDialog" title="新建 SOP" width="600px">
      <el-form :model="sopForm" label-width="120px">
        <el-form-item label="编码"><el-input v-model="sopForm.sopCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="sopForm.sopName" /></el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="sopForm.severity">
            <el-option label="LOW" value="LOW"/><el-option label="MEDIUM" value="MEDIUM"/>
            <el-option label="HIGH" value="HIGH"/><el-option label="CRITICAL" value="CRITICAL"/>
          </el-select>
        </el-form-item>
        <el-form-item label="响应窗口"><el-input v-model="sopForm.triggerTiming" /></el-form-item>
        <el-form-item label="步骤 JSON"><el-input v-model="sopForm.stepsJson" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sopDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSop">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="eventDialog" title="上报并发症" width="500px">
      <el-form :model="eventForm" label-width="120px">
        <el-form-item label="会员ID"><el-input-number v-model="eventForm.memberId" /></el-form-item>
        <el-form-item label="SOP 编码"><el-input v-model="eventForm.sopCode" /></el-form-item>
        <el-form-item label="症状描述"><el-input v-model="eventForm.symptoms" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="eventForm.severity">
            <el-option label="LOW" value="LOW"/><el-option label="MEDIUM" value="MEDIUM"/>
            <el-option label="HIGH" value="HIGH"/><el-option label="CRITICAL" value="CRITICAL"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="eventDialog = false">取消</el-button>
        <el-button type="danger" @click="reportEvent">上报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { complicationApi } from '@/api/complication'

const tab = ref('sop')
const sops = ref<any[]>([])
const events = ref<any[]>([])
const sopDialog = ref(false)
const eventDialog = ref(false)
const sopForm = ref<any>({ sopCode: '', sopName: '', severity: 'LOW', triggerTiming: '24h', stepsJson: '[]' })
const eventForm = ref<any>({ memberId: 1, sopCode: 'RED_SWELL', symptoms: '', severity: 'LOW' })

function severityType(s: string) {
  if (s === 'CRITICAL' || s === 'HIGH') return 'danger'
  if (s === 'MEDIUM') return 'warning'
  return 'success'
}

async function loadSops() { const { data } = await complicationApi.listSops(); sops.value = data || [] }
async function loadEvents() { const { data } = await complicationApi.listEvents(); events.value = data || [] }
async function initDefault() {
  await complicationApi.initDefaultSops()
  ElMessage.success('已初始化')
  loadSops()
}
async function saveSop() {
  await complicationApi.createSop(sopForm.value)
  ElMessage.success('已保存')
  sopDialog.value = false
  loadSops()
}
async function reportEvent() {
  await complicationApi.createEvent(eventForm.value)
  ElMessage.success('已上报')
  eventDialog.value = false
  loadEvents()
}
async function resolveEvent(row: any) {
  await complicationApi.updateEventStatus(row.id, { status: 'RESOLVED', resolution: '已治愈' })
  ElMessage.success('已结案')
  loadEvents()
}

onMounted(() => { loadSops(); loadEvents() })
</script>
