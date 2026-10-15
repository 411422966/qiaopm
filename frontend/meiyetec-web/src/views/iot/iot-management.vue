<template>
  <div class="iot-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="设备管理" name="device" />
      <el-tab-pane label="数据采集" name="data" />
      <el-tab-pane label="联动规则" name="rule" />
      <el-tab-pane label="告警中心" name="alert" />
    </el-tabs>

    <!-- ============ 设备管理 ============ -->
    <div v-if="activeTab === 'device'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon blue"><el-icon :size="24"><Cpu /></el-icon></div>
            <div>
              <div class="stat-value">{{ deviceList.length }}</div>
              <div class="stat-label">设备总数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon green"><el-icon :size="24"><Connection /></el-icon></div>
            <div>
              <div class="stat-value">{{ onlineCount }}</div>
              <div class="stat-label">在线设备</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon orange"><el-icon :size="24"><Warning /></el-icon></div>
            <div>
              <div class="stat-value">{{ errorCount }}</div>
              <div class="stat-label">故障设备</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon purple"><el-icon :size="24"><DataLine /></el-icon></div>
            <div>
              <div class="stat-value">{{ totalReports }}</div>
              <div class="stat-label">累计上报</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>IoT 设备列表</span>
            <div class="filter-form">
              <el-select v-model="deviceTypeFilter" placeholder="设备类型" clearable style="width:160px" @change="loadDevices">
                <el-option v-for="o in deviceTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-model="statusFilter" placeholder="状态" clearable style="width:120px" @change="loadDevices">
                <el-option v-for="o in deviceStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleAddDevice">
                <el-icon><Plus /></el-icon>新增设备
              </el-button>
            </div>
          </div>
        </template>

        <el-row :gutter="12">
          <el-col v-for="d in deviceList" :key="d.id" :xs="24" :sm="12" :md="8" :lg="6">
            <div class="device-card">
              <div class="device-header" :style="{ background: getDeviceColor(d.deviceType) }">
                <div class="device-icon">
                  <el-icon :size="32"><component :is="getDeviceIcon(d.deviceType)" /></el-icon>
                </div>
                <div class="device-status">
                  <el-tag :type="getStatusType(d.status)" size="small">
                    {{ getStatusLabel(d.status) }}
                  </el-tag>
                </div>
              </div>
              <div class="device-body">
                <h4 class="device-name">{{ d.deviceName || d.deviceCode }}</h4>
                <div class="device-type">{{ getDeviceTypeLabel(d.deviceType) }}</div>
                <div class="device-info">
                  <div class="info-line">
                    <span class="info-key">编号</span>
                    <span class="info-val">{{ d.deviceCode }}</span>
                  </div>
                  <div class="info-line">
                    <span class="info-key">协议</span>
                    <span class="info-val">{{ d.protocol?.toUpperCase() }}</span>
                  </div>
                  <div class="info-line">
                    <span class="info-key">位置</span>
                    <span class="info-val">{{ d.location || d.roomName || '-' }}</span>
                  </div>
                  <div class="info-line" v-if="d.lastDataTime">
                    <span class="info-key">最后上报</span>
                    <span class="info-val">{{ formatTime(d.lastDataTime) }}</span>
                  </div>
                </div>
              </div>
              <div class="device-actions">
                <el-button type="primary" link size="small" @click="handleEditDevice(d)">编辑</el-button>
                <el-button type="warning" link size="small" @click="handleSendCommand(d)">下发指令</el-button>
                <el-button type="success" link size="small" @click="handleHealthCheck(d)" :loading="healthChecking === d.id">
                  健康检查
                </el-button>
                <el-button type="danger" link size="small" @click="handleDeleteDevice(d)">删除</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
        <el-empty v-if="!deviceList.length" description="暂无设备" />
      </el-card>
    </div>

    <!-- ============ 数据采集 ============ -->
    <div v-if="activeTab === 'data'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="8">
          <el-card>
            <template #header>
              <span><el-icon><Search /></el-icon> 客户筛选</span>
            </template>
            <el-input v-model="memberKeyword" placeholder="客户名/手机号" clearable>
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <div class="data-search-actions">
              <el-button type="primary" @click="loadMemberRecentData" :disabled="!currentMember">加载数据</el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card v-if="currentMember">
            <template #header>
              <span>{{ currentMember.name }} 的 IoT 数据</span>
            </template>

            <el-tabs v-model="dataTab">
              <el-tab-pane label="最近数据" name="recent" />
              <el-tab-pane label="趋势分析" name="trend" />
            </el-tabs>

            <div v-if="dataTab === 'recent'">
              <el-table :data="recentData" border v-loading="dataLoading">
                <el-table-column label="时间" prop="collectTime" width="160" />
                <el-table-column label="设备类型" width="120">
                  <template #default="{ row }">
                    <el-tag size="small">{{ getDeviceTypeLabel(row.deviceType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="数据类型" width="100" prop="dataType" />
                <el-table-column label="主指标" width="120">
                  <template #default="{ row }">
                    {{ row.primaryMetric || '-' }}
                  </template>
                </el-table-column>
                <el-table-column label="辅助指标" width="120">
                  <template #default="{ row }">
                    {{ row.secondaryMetric || '-' }}
                  </template>
                </el-table-column>
                <el-table-column label="指标明细" prop="metrics" show-overflow-tooltip />
                <el-table-column label="来源" prop="source" width="100" />
              </el-table>
              <el-empty v-if="!dataLoading && !recentData.length" description="暂无数据" />
            </div>

            <div v-if="dataTab === 'trend'">
              <div class="trend-summary" v-if="trendData.length">
                <el-row :gutter="16">
                  <el-col :span="8">
                    <el-card class="trend-stat">
                      <div class="trend-label">检测次数</div>
                      <div class="trend-value">{{ trendData.length }}</div>
                    </el-card>
                  </el-col>
                  <el-col :span="8">
                    <el-card class="trend-stat">
                      <div class="trend-label">主指标均值</div>
                      <div class="trend-value">
                        {{ avgPrimary.toFixed(2) }}
                      </div>
                    </el-card>
                  </el-col>
                  <el-col :span="8">
                    <el-card class="trend-stat">
                      <div class="trend-label">辅助指标均值</div>
                      <div class="trend-value">
                        {{ avgSecondary.toFixed(2) }}
                      </div>
                    </el-card>
                  </el-col>
                </el-row>
              </div>

              <el-table :data="trendData" border stripe v-loading="trendLoading" style="margin-top:16px">
                <el-table-column label="日期" prop="date" width="160" />
                <el-table-column label="主指标均值" width="160">
                  <template #default="{ row }">
                    {{ Number(row.avg_primary || 0).toFixed(2) }}
                  </template>
                </el-table-column>
                <el-table-column label="辅助指标均值" width="160">
                  <template #default="{ row }">
                    {{ Number(row.avg_secondary || 0).toFixed(2) }}
                  </template>
                </el-table-column>
                <el-table-column label="上报次数" prop="report_count" width="120" align="center" />
              </el-table>
              <el-empty v-if="!trendLoading && !trendData.length" description="暂无趋势数据" />
            </div>
          </el-card>
          <el-empty v-else description="请先在左侧输入客户关键词" />
        </el-col>
      </el-row>
    </div>

    <!-- ============ 联动规则 ============ -->
    <div v-if="activeTab === 'rule'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>联动触发规则</span>
            <el-button type="primary" @click="handleAddRule">
              <el-icon><Plus /></el-icon>新增规则
            </el-button>
          </div>
        </template>

        <el-table :data="ruleList" border stripe v-loading="ruleLoading">
          <el-table-column label="规则名称" prop="ruleName" min-width="180" fixed />
          <el-table-column label="设备类型" width="140">
            <template #default="{ row }">
              <el-tag size="small">{{ getDeviceTypeLabel(row.deviceType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="数据类型" prop="dataType" width="100" />
          <el-table-column label="触发条件" prop="triggerCondition" min-width="200" show-overflow-tooltip />
          <el-table-column label="动作类型" width="140">
            <template #default="{ row }">
              <el-tag type="success">{{ getActionLabel(row.actionType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="优先级" prop="priority" width="80" align="center" />
          <el-table-column label="触发次数" prop="triggerCount" width="100" align="center" />
          <el-table-column label="最后触发" prop="lastTriggerTime" width="160" />
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="toggleRule(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!ruleLoading && !ruleList.length" description="暂无规则" />
      </el-card>
    </div>

    <!-- ============ 告警中心 ============ -->
    <div v-if="activeTab === 'alert'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>设备告警中心</span>
            <div class="filter-form">
              <el-select v-model="alertLevelFilter" placeholder="告警级别" clearable style="width:120px" @change="loadAlerts">
                <el-option v-for="o in alertLevelOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-model="alertStatusFilter" placeholder="状态" clearable style="width:120px" @change="loadAlerts">
                <el-option label="待处理" value="pending" />
                <el-option label="处理中" value="handling" />
                <el-option label="已处理" value="resolved" />
                <el-option label="已忽略" value="ignored" />
              </el-select>
            </div>
          </div>
        </template>

        <el-table :data="alertList" border stripe v-loading="alertLoading">
          <el-table-column label="告警时间" prop="alertTime" width="160" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="getAlertLevelType(row.alertLevel)">{{ getAlertLevelLabel(row.alertLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ getAlertTypeLabel(row.alertType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="设备" prop="deviceName" width="160" />
          <el-table-column label="标题" prop="title" min-width="180" />
          <el-table-column label="详情" prop="message" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getAlertStatusType(row.status)">{{ getAlertStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理人" prop="handlerName" width="100" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending'" type="primary" link size="small" @click="handleResolveAlert(row)">
                处理
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!alertLoading && !alertList.length" description="暂无告警" />
      </el-card>
    </div>

    <!-- 设备编辑对话框 -->
    <el-dialog v-model="deviceDialogVisible" :title="deviceDialogTitle" width="700px">
      <el-form :model="deviceForm" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备编号">
              <el-input v-model="deviceForm.deviceCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称">
              <el-input v-model="deviceForm.deviceName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-select v-model="deviceForm.deviceType" style="width:100%">
                <el-option v-for="o in deviceTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议">
              <el-select v-model="deviceForm.protocol" style="width:100%">
                <el-option v-for="o in protocolOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="deviceForm.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="deviceForm.deviceModel" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="MAC 地址">
              <el-input v-model="deviceForm.macAddress" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序列号">
              <el-input v-model="deviceForm.serialNumber" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="安装位置">
          <el-input v-model="deviceForm.location" placeholder="如:VIP1号房间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDevice">保存</el-button>
      </template>
    </el-dialog>

    <!-- 下发指令对话框 -->
    <el-dialog v-model="commandDialogVisible" title="下发设备指令" width="500px">
      <el-form :model="commandForm" label-width="100px">
        <el-form-item label="指令类型">
          <el-select v-model="commandForm.commandType" style="width:100%">
            <el-option label="启动" value="start" />
            <el-option label="停止" value="stop" />
            <el-option label="锁定" value="lock" />
            <el-option label="解锁" value="unlock" />
            <el-option label="调整参数" value="adjust" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数">
          <el-input v-model="commandForm.paramStr" type="textarea" :rows="3" placeholder='{"key":"value"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commandDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitCommand">下发</el-button>
      </template>
    </el-dialog>

    <!-- 规则编辑对话框 -->
    <el-dialog v-model="ruleDialogVisible" :title="ruleDialogTitle" width="600px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-select v-model="ruleForm.deviceType" style="width:100%">
                <el-option v-for="o in deviceTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据类型">
              <el-select v-model="ruleForm.dataType" style="width:100%">
                <el-option v-for="o in dataTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="触发条件">
          <el-input v-model="ruleForm.triggerCondition" type="textarea" :rows="2"
                    placeholder='{"operator":"lt","metric":"水分","value":30}' />
          <div style="font-size:12px;color:#909399;margin-top:4px">
            支持操作符: lt/le/gt/ge/eq
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="动作类型">
              <el-select v-model="ruleForm.actionType" style="width:100%">
                <el-option v-for="o in actionTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="ruleForm.priority" :min="0" :max="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用">
          <el-radio-group v-model="ruleForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 处理告警对话框 -->
    <el-dialog v-model="resolveDialogVisible" title="处理告警" width="500px">
      <el-form :model="resolveForm" label-width="100px">
        <el-form-item label="处理结果">
          <el-radio-group v-model="resolveForm.status">
            <el-radio value="resolved">已处理</el-radio>
            <el-radio value="ignored">已忽略</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="resolveForm.handlerName" />
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="resolveForm.handleNote" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResolveAlert">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Cpu, Connection, Warning, DataLine,
  Search, DataAnalysis, FirstAidKit, Operation,
  WarningFilled, Tools, Upload, Lightning as Battery
} from '@element-plus/icons-vue'
import request from '@/utils/http'
import {
  iotApi,
  deviceTypeOptions,
  deviceStatusOptions,
  protocolOptions,
  dataTypeOptions,
  actionTypeOptions,
  alertLevelOptions,
  alertTypeOptions
} from '@/api/iot'

const activeTab = ref('device')

// ============ 设备 ============
const deviceList = ref<any[]>([])
const deviceTypeFilter = ref('')
const statusFilter = ref('')
const deviceDialogVisible = ref(false)
const deviceDialogTitle = ref('新增设备')
const deviceForm = reactive<any>({
  id: null,
  deviceCode: '',
  deviceName: '',
  deviceType: 'skin_analyzer',
  deviceModel: '',
  brand: '',
  macAddress: '',
  serialNumber: '',
  protocol: 'mqtt',
  location: ''
})

const onlineCount = computed(() => deviceList.value.filter(d => d.status === 'online').length)
const errorCount = computed(() => deviceList.value.filter(d => d.status === 'error').length)
const totalReports = computed(() => deviceList.value.reduce((s, d) => s + (d.totalReports || 0), 0))

const loadDevices = async () => {
  try {
    const res: any = await iotApi.devices({
      deviceType: deviceTypeFilter.value || undefined,
      status: statusFilter.value || undefined
    })
    deviceList.value = res.data || []
  } catch (e) {
    deviceList.value = []
  }
}

const handleAddDevice = () => {
  Object.assign(deviceForm, {
    id: null,
    deviceCode: '',
    deviceName: '',
    deviceType: 'skin_analyzer',
    deviceModel: '',
    brand: '',
    macAddress: '',
    serialNumber: '',
    protocol: 'mqtt',
    location: ''
  })
  deviceDialogTitle.value = '新增设备'
  deviceDialogVisible.value = true
}

const handleEditDevice = (row: any) => {
  Object.assign(deviceForm, row)
  deviceDialogTitle.value = '编辑设备'
  deviceDialogVisible.value = true
}

const handleSaveDevice = async () => {
  try {
    if (deviceForm.id) {
      await iotApi.updateDevice(deviceForm)
    } else {
      await iotApi.createDevice(deviceForm)
    }
    ElMessage.success('保存成功')
    deviceDialogVisible.value = false
    loadDevices()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteDevice = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除设备"${row.deviceName || row.deviceCode}"吗？`, '提示', { type: 'warning' })
    await iotApi.deleteDevice(row.id)
    ElMessage.success('删除成功')
    loadDevices()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const healthChecking = ref<number | null>(null)
const handleHealthCheck = async (row: any) => {
  healthChecking.value = row.id
  try {
    const res: any = await iotApi.deviceHealthCheck(row.id)
    if (res.data?.online) {
      ElMessage.success('设备在线')
    } else {
      ElMessage.warning('设备离线')
    }
    loadDevices()
  } catch (e) {
    ElMessage.error('检查失败')
  } finally {
    healthChecking.value = null
  }
}

// 下发指令
const commandDialogVisible = ref(false)
const commandForm = reactive<any>({ commandType: 'start', paramStr: '{}' })

const handleSendCommand = (row: any) => {
  commandForm.commandType = 'start'
  commandForm.paramStr = '{}'
  commandDialogVisible.value = true
  // 保存当前设备ID
  ;(commandForm as any).deviceId = row.id
}

const handleSubmitCommand = async () => {
  try {
    let params = {}
    try { params = JSON.parse(commandForm.paramStr) } catch { ElMessage.error('参数格式错误'); return }
    const cmd = { commandType: commandForm.commandType, params }
    await iotApi.sendCommand((commandForm as any).deviceId, cmd)
    ElMessage.success('指令已下发')
    commandDialogVisible.value = false
  } catch (e) {
    ElMessage.error('下发失败')
  }
}

// ============ 数据采集 ============
const memberKeyword = ref('')
const currentMember = ref<any>(null)
const recentData = ref<any[]>([])
const dataLoading = ref(false)
const dataTab = ref('recent')

const searchMember = async () => {
  if (!memberKeyword.value) return
  try {
    const res: any = await request.get('/member/search', { params: { keyword: memberKeyword.value } })
    if (res.data?.length) currentMember.value = res.data[0]
  } catch (e) {}
}

const loadMemberRecentData = async () => {
  if (!currentMember.value) {
    await searchMember()
    if (!currentMember.value) return
  }
  dataLoading.value = true
  try {
    const res: any = await iotApi.memberRecentData(currentMember.value.id)
    recentData.value = res.data || []
  } catch (e) {
    recentData.value = []
  } finally {
    dataLoading.value = false
  }
}

// 趋势
const trendData = ref<any[]>([])
const trendLoading = ref(false)

const loadTrend = async () => {
  if (!currentMember.value) return
  trendLoading.value = true
  try {
    const end = new Date()
    const start = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
    const res: any = await iotApi.memberTrend(currentMember.value.id, {
      dataType: 'detection',
      startTime: start.toISOString().slice(0, 19).replace('T', ' '),
      endTime: end.toISOString().slice(0, 19).replace('T', ' ')
    })
    trendData.value = res.data || []
  } catch (e) {
    trendData.value = []
  } finally {
    trendLoading.value = false
  }
}

const avgPrimary = computed(() => {
  if (!trendData.value.length) return 0
  const sum = trendData.value.reduce((s, t) => s + Number(t.avg_primary || 0), 0)
  return sum / trendData.value.length
})

const avgSecondary = computed(() => {
  if (!trendData.value.length) return 0
  const sum = trendData.value.reduce((s, t) => s + Number(t.avg_secondary || 0), 0)
  return sum / trendData.value.length
})

// ============ 规则 ============
const ruleList = ref<any[]>([])
const ruleLoading = ref(false)
const ruleDialogVisible = ref(false)
const ruleDialogTitle = ref('新增规则')
const ruleForm = reactive<any>({
  id: null,
  ruleName: '',
  deviceType: 'skin_analyzer',
  dataType: 'detection',
  triggerCondition: '{"operator":"lt","metric":"水分","value":30}',
  triggerAction: '',
  actionType: 'send_coupon',
  priority: 5,
  enabled: 1
})

const loadRules = async () => {
  ruleLoading.value = true
  try {
    const res: any = await iotApi.rules()
    ruleList.value = res.data || []
  } catch (e) {
    ruleList.value = []
  } finally {
    ruleLoading.value = false
  }
}

const handleAddRule = () => {
  Object.assign(ruleForm, {
    id: null,
    ruleName: '',
    deviceType: 'skin_analyzer',
    dataType: 'detection',
    triggerCondition: '{"operator":"lt","metric":"水分","value":30}',
    triggerAction: '',
    actionType: 'send_coupon',
    priority: 5,
    enabled: 1
  })
  ruleDialogTitle.value = '新增规则'
  ruleDialogVisible.value = true
}

const handleEditRule = (row: any) => {
  Object.assign(ruleForm, row)
  ruleDialogTitle.value = '编辑规则'
  ruleDialogVisible.value = true
}

const handleSaveRule = async () => {
  try {
    if (ruleForm.id) {
      await iotApi.updateRule(ruleForm)
    } else {
      await iotApi.createRule(ruleForm)
    }
    ElMessage.success('保存成功')
    ruleDialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteRule = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该规则吗？', '提示', { type: 'warning' })
    await iotApi.deleteRule(row.id)
    ElMessage.success('删除成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleRule = async (row: any) => {
  try { await iotApi.updateRule(row) } catch { ElMessage.error('更新失败') }
}

// ============ 告警 ============
const alertList = ref<any[]>([])
const alertLoading = ref(false)
const alertLevelFilter = ref('')
const alertStatusFilter = ref('')
const resolveDialogVisible = ref(false)
const resolveForm = reactive<any>({ alertId: null, status: 'resolved', handlerName: '', handleNote: '' })

const loadAlerts = async () => {
  alertLoading.value = true
  try {
    const res: any = await iotApi.alerts({
      alertLevel: alertLevelFilter.value || undefined,
      status: alertStatusFilter.value || undefined
    })
    alertList.value = res.data || []
  } catch (e) {
    alertList.value = []
  } finally {
    alertLoading.value = false
  }
}

const handleResolveAlert = (row: any) => {
  resolveForm.alertId = row.id
  resolveForm.status = 'resolved'
  resolveForm.handlerName = ''
  resolveForm.handleNote = ''
  resolveDialogVisible.value = true
}

const submitResolveAlert = async () => {
  try {
    await iotApi.handleAlert(resolveForm.alertId, resolveForm)
    ElMessage.success('处理成功')
    resolveDialogVisible.value = false
    loadAlerts()
  } catch (e) {
    ElMessage.error('处理失败')
  }
}

// ============ 辅助方法 ============
const getDeviceIcon = (type: string) => deviceTypeOptions.find(o => o.value === type)?.icon || 'Cpu'
const getDeviceColor = (type: string) => deviceTypeOptions.find(o => o.value === type)?.color || '#909399'
const getDeviceTypeLabel = (t: string) => deviceTypeOptions.find(o => o.value === t)?.label || t

const getStatusLabel = (s: string) => deviceStatusOptions.find(o => o.value === s)?.label || s
const getStatusType = (s: string) => deviceStatusOptions.find(o => o.value === s)?.type || 'info'

const getActionLabel = (a: string) => actionTypeOptions.find(o => o.value === a)?.label || a

const getAlertLevelLabel = (l: string) => alertLevelOptions.find(o => o.value === l)?.label || l
const getAlertLevelType = (l: string) => alertLevelOptions.find(o => o.value === l)?.type || 'info'

const getAlertTypeLabel = (t: string) => alertTypeOptions.find(o => o.value === t)?.label || t

const getAlertStatusLabel = (s: string) => {
  const map: any = { pending: '待处理', handling: '处理中', resolved: '已处理', ignored: '已忽略' }
  return map[s] || s
}

const getAlertStatusType = (s: string) => {
  const map: any = { pending: 'warning', handling: 'info', resolved: 'success', ignored: 'info' }
  return map[s] || 'info'
}

const formatTime = (dt: string) => {
  if (!dt) return '-'
  return dt.slice(0, 19).replace('T', ' ')
}

const handleTabChange = (tab: string) => {
  if (tab === 'device') loadDevices()
  else if (tab === 'rule') loadRules()
  else if (tab === 'alert') loadAlerts()
}

onMounted(() => {
  loadDevices()
})
</script>