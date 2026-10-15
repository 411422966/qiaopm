<template>
  <div class="iot-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">IoT 设备管理</span>
          <el-space>
            <el-button type="primary" @click="loadAll" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-row :gutter="16" v-if="dashboard">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">设备总数</div>
            <div class="stat-value">{{ dashboard.total || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-green">
            <div class="stat-label">在线设备</div>
            <div class="stat-value">{{ dashboard.online || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-red">
            <div class="stat-label">离线设备</div>
            <div class="stat-value">{{ dashboard.offline || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card stat-blue">
            <div class="stat-label">今日遥测条数</div>
            <div class="stat-value">{{ dashboard.telemetryToday || 0 }}</div>
          </el-card>
        </el-col>
      </el-row>
      <div v-if="dashboard && dashboard.byType && dashboard.byType.length" class="by-type">
        按类型：
        <el-tag v-for="t in dashboard.byType" :key="t.device_type" style="margin-left:6px">
          {{ deviceTypeLabel(t.device_type) }} · {{ t.cnt }} 台
        </el-tag>
      </div>
    </el-card>

    <el-tabs v-model="activeTab">
      <!-- 设备列表 -->
      <el-tab-pane label="设备列表" name="devices">
        <el-card>
          <div class="toolbar">
            <el-button type="primary" @click="openRegister()">+ 注册设备</el-button>
            <el-button @click="loadDevices">刷新</el-button>
          </div>
          <el-table :data="devices" v-loading="loading" border stripe>
            <el-table-column prop="deviceCode" label="设备编码" width="160" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ deviceTypeLabel(row.deviceType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="协议" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.protocol }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="mqttTopic" label="MQTT Topic" width="200" show-overflow-tooltip />
            <el-table-column prop="endpoint" label="接入端点" width="200" show-overflow-tooltip />
            <el-table-column label="在线" width="80">
              <template #default="{ row }">
                <el-tag :type="row.onlineStatus === 'ONLINE' ? 'success' : 'danger'" size="small">
                  {{ row.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="绑定会员" width="100">
              <template #default="{ row }">{{ row.bindMemberId || '—' }}</template>
            </el-table-column>
            <el-table-column label="最后心跳" width="170">
              <template #default="{ row }">{{ row.lastOnlineAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openTelemetry(row)">遥测</el-button>
                <el-button link size="small" @click="openCmdDialog(row)" :disabled="row.onlineStatus !== 'ONLINE'">
                  下发命令
                </el-button>
                <el-button link size="small" @click="openBindDialog(row)">绑定</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 上报测试 -->
      <el-tab-pane label="遥测上报测试" name="report">
        <el-card>
          <el-form :model="reportForm" label-width="140px" style="max-width:680px">
            <el-form-item label="设备编码" required>
              <el-input v-model="reportForm.deviceCode" placeholder="如 MASSAGE_001" />
            </el-form-item>
            <el-form-item label="上报协议">
              <el-radio-group v-model="reportForm.protocol">
                <el-radio-button value="HTTP">HTTP REST</el-radio-button>
                <el-radio-button value="MQTT">MQTT</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="reportForm.protocol === 'MQTT'" label="Topic">
              <el-input v-model="reportForm.topic" placeholder="如 MASSAGE_001/telemetry" />
            </el-form-item>
            <el-form-item label="温度">
              <el-input-number v-model="reportForm.temperature" :min="0" :max="100" :precision="1" />
            </el-form-item>
            <el-form-item label="湿度">
              <el-input-number v-model="reportForm.humidity" :min="0" :max="100" :precision="1" />
            </el-form-item>
            <el-form-item label="功率(W)">
              <el-input-number v-model="reportForm.power" :min="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitTelemetry" :loading="reporting">模拟上报</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 注册设备 -->
    <el-dialog v-model="registerDialog.visible" title="注册设备" width="540px">
      <el-form :model="registerDialog.form" label-width="100px">
        <el-form-item label="设备编码" required>
          <el-input v-model="registerDialog.form.deviceCode" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="registerDialog.form.deviceName" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="registerDialog.form.deviceType" style="width:100%">
            <el-option label="美容仪" value="BEAUTY_DEVICE" />
            <el-option label="按摩椅" value="MASSAGE_CHAIR" />
            <el-option label="加湿器" value="HUMIDIFIER" />
            <el-option label="体重秤" value="SCALE" />
            <el-option label="体温计" value="THERMOMETER" />
            <el-option label="LED 灯" value="LED_LIGHT" />
            <el-option label="智能锁" value="SMART_LOCK" />
            <el-option label="皮肤检测仪" value="SKIN_ANALYZER" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议">
          <el-radio-group v-model="registerDialog.form.protocol">
            <el-radio-button value="MQTT">MQTT</el-radio-button>
            <el-radio-button value="HTTP">HTTP</el-radio-button>
            <el-radio-button value="MODBUS">Modbus</el-radio-button>
            <el-radio-button value="BLE">BLE</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="MQTT Topic">
          <el-input v-model="registerDialog.form.mqttTopic" placeholder="自动以 deviceCode 生成" />
        </el-form-item>
        <el-form-item label="接入端点">
          <el-input v-model="registerDialog.form.endpoint" placeholder="如 mqtt://broker..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRegister" :loading="registerDialog.loading">注册</el-button>
      </template>
    </el-dialog>

    <!-- 下发命令 -->
    <el-dialog v-model="cmdDialog.visible" title="下发设备命令" width="480px">
      <el-form label-width="100px">
        <el-form-item label="设备">
          <span>{{ cmdDialog.target?.deviceCode }} · {{ cmdDialog.target?.deviceName }}</span>
        </el-form-item>
        <el-form-item label="命令">
          <el-radio-group v-model="cmdDialog.form.command">
            <el-radio-button value="ON">开</el-radio-button>
            <el-radio-button value="OFF">关</el-radio-button>
            <el-radio-button value="TOGGLE">切换</el-radio-button>
            <el-radio-button value="READ">读取遥测</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="cmdDialog.form.command === 'ON' || cmdDialog.form.command === 'OFF'" label="附加参数">
          <el-input v-model="cmdDialog.form.payload" type="textarea" :rows="2" placeholder='JSON，如 {"intensity": 5}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cmdDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCmd" :loading="cmdDialog.loading">下发</el-button>
      </template>
    </el-dialog>

    <!-- 绑定会员 -->
    <el-dialog v-model="bindDialog.visible" title="绑定会员" width="400px">
      <el-form label-width="100px">
        <el-form-item label="会员 ID">
          <el-input-number v-model="bindDialog.form.memberId" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitBind" :loading="bindDialog.loading">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 遥测抽屉 -->
    <el-drawer v-model="telemetryDrawer.visible" title="设备遥测" size="60%">
      <div v-if="telemetryDrawer.data" class="telemetry">
        <h4>最新指标</h4>
        <el-row :gutter="16" v-if="latestTelemetry && Object.keys(latestTelemetry).length">
          <el-col v-for="(v, k) in latestTelemetry" :key="k" :span="8" style="margin-bottom:12px">
            <el-card shadow="never" class="metric-card">
              <div class="metric-label">{{ k }}</div>
              <div class="metric-value">
                {{ v.value }}
                <span v-if="v.unit" class="unit">{{ v.unit }}</span>
              </div>
              <div class="metric-time">{{ v.time }}</div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="暂无遥测数据" />
        <h4 style="margin-top:16px">历史遥测（最近 {{ telemetryDrawer.data.length }} 条）</h4>
        <el-table :data="telemetryDrawer.data" border stripe size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="metric" label="指标" width="120" />
          <el-table-column label="数值" width="120">
            <template #default="{ row }">
              {{ row.value_num != null ? row.value_num : row.value_str }}
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="topic" label="Topic" width="200" show-overflow-tooltip />
          <el-table-column prop="recorded_at" label="时间" width="170" />
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { iotApi } from '@/api/iot'

const loading = ref(false)
const reporting = ref(false)
const activeTab = ref('devices')

const dashboard = ref<any>(null)
const devices = ref<any[]>([])

const registerDialog = reactive({
  visible: false,
  loading: false,
  form: {} as any
})

const cmdDialog = reactive({
  visible: false,
  loading: false,
  target: null as any,
  form: { command: 'ON', payload: '' }
})

const bindDialog = reactive({
  visible: false,
  loading: false,
  target: null as any,
  form: { memberId: null as number | null }
})

const telemetryDrawer = reactive({
  visible: false,
  data: null as any,
  target: null as any
})

const latestTelemetry = ref<any>({})

const reportForm = reactive({
  deviceCode: '',
  protocol: 'HTTP' as 'HTTP' | 'MQTT',
  topic: '',
  temperature: 25.5,
  humidity: 60,
  power: 100
})

function deviceTypeLabel(t?: string) {
  const m: Record<string, string> = {
    BEAUTY_DEVICE: '美容仪', MASSAGE_CHAIR: '按摩椅', HUMIDIFIER: '加湿器',
    SCALE: '体重秤', THERMOMETER: '体温计', LED_LIGHT: 'LED 灯',
    SMART_LOCK: '智能锁', SKIN_ANALYZER: '皮肤检测仪'
  }
  return m[t || ''] || t || '—'
}

async function loadDashboard() {
  try {
    const res: any = await iotApi.dashboard()
    dashboard.value = res.data || null
  } catch {
    dashboard.value = null
  }
}

async function loadDevices() {
  loading.value = true
  try {
    const res: any = await iotApi.listDevices()
    devices.value = res.data || []
  } finally {
    loading.value = false
  }
}

function loadAll() {
  loadDashboard()
  loadDevices()
}

function openRegister() {
  registerDialog.form = {
    deviceCode: '',
    deviceName: '',
    deviceType: 'MASSAGE_CHAIR',
    protocol: 'MQTT',
    mqttTopic: '',
    endpoint: ''
  }
  registerDialog.visible = true
}

async function submitRegister() {
  if (!registerDialog.form.deviceCode) {
    ElMessage.warning('请填写设备编码')
    return
  }
  registerDialog.loading = true
  try {
    if (!registerDialog.form.mqttTopic) {
      registerDialog.form.mqttTopic = `${registerDialog.form.deviceCode}/telemetry`
    }
    await iotApi.register(registerDialog.form)
    ElMessage.success('已注册')
    registerDialog.visible = false
    loadDevices()
    loadDashboard()
  } finally {
    registerDialog.loading = false
  }
}

function openCmdDialog(row: any) {
  cmdDialog.target = row
  cmdDialog.form = { command: 'ON', payload: '' }
  cmdDialog.visible = true
}

async function submitCmd() {
  cmdDialog.loading = true
  try {
    const payload: any = { command: cmdDialog.form.command }
    if (cmdDialog.form.payload) {
      try { payload.payload = JSON.parse(cmdDialog.form.payload) } catch { payload.payload = cmdDialog.form.payload }
    }
    const res: any = await iotApi.sendCommand(cmdDialog.target.id, payload)
    ElMessage.success(`已下发：${res.data?.command} -> ${res.data?.topic}`)
    cmdDialog.visible = false
  } finally {
    cmdDialog.loading = false
  }
}

function openBindDialog(row: any) {
  bindDialog.target = row
  bindDialog.form = { memberId: row.bindMemberId || null }
  bindDialog.visible = true
}

async function submitBind() {
  bindDialog.loading = true
  try {
    await iotApi.bindMember(bindDialog.target.id, bindDialog.form)
    ElMessage.success('已绑定')
    bindDialog.visible = false
    loadDevices()
  } finally {
    bindDialog.loading = false
  }
}

async function openTelemetry(row: any) {
  telemetryDrawer.target = row
  const [listRes, latestRes]: any[] = await Promise.all([
    iotApi.telemetry(row.id, 100),
    iotApi.latestTelemetry(row.id)
  ])
  telemetryDrawer.data = listRes.data || []
  latestTelemetry.value = latestRes.data || {}
  telemetryDrawer.visible = true
}

async function submitTelemetry() {
  if (!reportForm.deviceCode) {
    ElMessage.warning('请填写设备编码')
    return
  }
  reporting.value = true
  try {
    if (reportForm.protocol === 'HTTP') {
      await iotApi.telemetryHttp({
        deviceCode: reportForm.deviceCode,
        temperature: reportForm.temperature,
        humidity: reportForm.humidity,
        power: reportForm.power
      })
    } else {
      const topic = reportForm.topic || `${reportForm.deviceCode}/telemetry`
      await iotApi.telemetryMqtt({
        topic,
        payload: JSON.stringify({
          temperature: reportForm.temperature,
          humidity: reportForm.humidity,
          power: reportForm.power
        })
      })
    }
    ElMessage.success('上报成功')
    loadDashboard()
    loadDevices()
  } finally {
    reporting.value = false
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.iot-page {
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
  background: #f7f9fc;
}
.stat-card.stat-green { background: linear-gradient(135deg, #67c23a, #95d475); color: #fff; }
.stat-card.stat-red { background: linear-gradient(135deg, #f56c6c, #f89898); color: #fff; }
.stat-card.stat-blue { background: linear-gradient(135deg, #409eff, #66b1ff); color: #fff; }
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-card.stat-green .stat-label,
.stat-card.stat-red .stat-label,
.stat-card.stat-blue .stat-label {
  color: rgba(255,255,255,0.85);
}
.stat-value {
  color: #303133;
  font-size: 26px;
  font-weight: 600;
  margin-top: 8px;
}
.stat-card.stat-green .stat-value,
.stat-card.stat-red .stat-value,
.stat-card.stat-blue .stat-value {
  color: #fff;
}
.by-type {
  margin-top: 12px;
  color: #606266;
  font-size: 13px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.telemetry h4 {
  margin: 8px 0;
  color: #303133;
}
.metric-card {
  text-align: center;
  background: #f7f9fc;
}
.metric-label {
  color: #909399;
  font-size: 12px;
}
.metric-value {
  color: #303133;
  font-size: 20px;
  font-weight: 600;
  margin-top: 4px;
}
.metric-value .unit {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.metric-time {
  color: #c0c4cc;
  font-size: 11px;
  margin-top: 4px;
}
</style>