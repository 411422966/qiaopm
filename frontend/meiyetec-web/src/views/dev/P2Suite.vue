<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">P2 高级套件：AI 视觉 / LTV-Churn / IoT / GDPR / HIPAA / Maps / POS</span>
          <el-space>
            <el-button @click="initCvModels" type="success" plain :loading="initCvL">初始化 CV</el-button>
            <el-button @click="runLtvCompute" type="warning" plain :loading="ltvL">跑 LTV</el-button>
            <el-button @click="runChurnCompute" type="danger" plain :loading="churnL">跑 Churn</el-button>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- AI 视觉 -->
        <el-tab-pane label="AI 视觉" name="vision">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>CV 模型</h4>
              <el-table :data="cvModels" size="small" stripe>
                <el-table-column prop="modelCode" label="编码" width="160" />
                <el-table-column prop="modelName" label="名称" />
                <el-table-column label="精度" width="80">
                  <template #default="{ row }">{{ row.accuracy }}%</template>
                </el-table-column>
                <el-table-column prop="latencyMs" label="延迟(ms)" width="80" />
                <el-table-column prop="provider" label="厂商" width="100" />
              </el-table>
            </el-col>
            <el-col :span="15">
              <h4>视觉识别</h4>
              <el-tabs v-model="visionSubTab">
                <el-tab-pane label="皮肤检测" name="skin">
                  <el-form :model="skinForm" label-width="100px" size="small">
                    <el-form-item label="会员ID"><el-input-number v-model="skinForm.memberId" /></el-form-item>
                    <el-form-item label="图片URL"><el-input v-model="skinForm.imageUrl" placeholder="https://..." /></el-form-item>
                    <el-button type="primary" @click="recognizeSkin" :loading="recognizing">识别</el-button>
                  </el-form>
                  <div v-if="skinResult" class="cv-result">
                    <h4>识别结果</h4>
                    <p>肤质：{{ skinResult.skin?.skinType }}</p>
                    <p>得分：{{ skinResult.skin?.skinScore }}</p>
                    <p>问题：{{ skinResult.skin?.issues?.join(', ') }}</p>
                    <p>延迟：{{ skinResult.latency }}ms</p>
                  </div>
                </el-tab-pane>
                <el-tab-pane label="美甲试戴" name="nail">
                  <el-form :model="nailForm" label-width="100px" size="small">
                    <el-form-item label="手部图"><el-input v-model="nailForm.handImageUrl" /></el-form-item>
                    <el-form-item label="款式图"><el-input v-model="nailForm.styleImageUrl" /></el-form-item>
                    <el-button type="primary" @click="nailTryon">试戴</el-button>
                  </el-form>
                  <div v-if="nailResult" class="cv-result">
                    <p>结果图：<a :href="nailResult.resultImageUrl" target="_blank">查看</a></p>
                    <p>置信度：{{ nailResult.confidence }}</p>
                  </div>
                </el-tab-pane>
                <el-tab-pane label="人脸识别" name="face">
                  <el-form :model="faceForm" label-width="100px" size="small">
                    <el-form-item label="人脸图"><el-input v-model="faceForm.imageUrl" /></el-form-item>
                    <el-button type="primary" @click="recognizeFace">识别</el-button>
                  </el-form>
                  <div v-if="faceResult" class="cv-result">
                    <p>颜值：{{ faceResult.beautyScore }}</p>
                    <p>年龄：{{ faceResult.ageRange }}</p>
                    <p>性别：{{ faceResult.gender }}</p>
                  </div>
                </el-tab-pane>
              </el-tabs>
              <h4>识别统计</h4>
              <el-row :gutter="12">
                <el-col :span="8"><el-statistic title="总调用" :value="cvStats.totalCalls || 0" /></el-col>
                <el-col :span="8"><el-statistic title="成功" :value="cvStats.successCalls || 0" /></el-col>
                <el-col :span="8"><el-statistic title="平均延迟" :value="(cvStats.avgLatency || 0) + 'ms'" /></el-col>
              </el-row>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- LTV / Churn -->
        <el-tab-pane label="LTV / Churn" name="ltv">
          <el-row :gutter="20">
            <el-col :span="14">
              <h4>看板</h4>
              <el-row :gutter="12">
                <el-col :span="12"><el-statistic title="总 LTV" :value="ltvDash.totalLtv || 0" /></el-col>
                <el-col :span="12"><el-statistic title="平均 LTV" :value="(ltvDash.avgLtv || 0).toFixed(2)" /></el-col>
              </el-row>
              <el-row :gutter="12" style="margin-top:12px">
                <el-col :span="8"><el-statistic title="高流失风险" :value="ltvDash.highRiskCount || 0" /></el-col>
                <el-col :span="8"><el-statistic title="中风险" :value="ltvDash.mediumRiskCount || 0" /></el-col>
                <el-col :span="8"><el-statistic title="低风险" :value="ltvDash.lowRiskCount || 0" /></el-col>
              </el-row>
            </el-col>
            <el-col :span="10">
              <h4>查询会员 LTV</h4>
              <el-input-number v-model="ltvMemberId" :min="1" />
              <el-button @click="loadMemberLtv" style="margin-left:8px">查询</el-button>
              <div v-if="memberLtv" class="ltv-detail">
                <p>总消费：¥{{ memberLtv.totalRevenue }}</p>
                <p>订单数：{{ memberLtv.orderCount }}</p>
                <p>客单价：¥{{ memberLtv.avgOrderValue }}</p>
                <p>复购率：{{ memberLtv.repurchaseRate }}</p>
                <p><strong>LTV 预测：¥{{ memberLtv.ltv }}</strong></p>
              </div>

              <h4 style="margin-top:12px">查询流失风险</h4>
              <el-input-number v-model="churnMemberId" :min="1" />
              <el-button @click="loadMemberChurn" style="margin-left:8px">查询</el-button>
              <div v-if="memberChurn" class="churn-detail">
                <p>风险分：<strong>{{ memberChurn.riskScore }}</strong> ({{ memberChurn.riskLevel }})</p>
                <p>距上次消费：{{ memberChurn.daysSinceLast }} 天</p>
                <p>原因：{{ memberChurn.reasons?.join('; ') }}</p>
                <p>建议：{{ memberChurn.recommendation }}</p>
              </div>
            </el-col>
          </el-row>

          <el-divider />
          <h4>Top 高价值客户</h4>
          <el-table :data="topLtvList" size="small" stripe>
            <el-table-column prop="member_id" label="会员ID" width="100" />
            <el-table-column prop="value" label="LTV" width="100" />
            <el-table-column prop="period" label="月份" width="100" />
          </el-table>

          <h4 style="margin-top:12px">流失风险客户</h4>
          <el-table :data="churnRiskList" size="small" stripe>
            <el-table-column prop="member_id" label="会员ID" width="100" />
            <el-table-column prop="value" label="风险分" width="100" />
            <el-table-column label="详情" :show-overflow-tooltip="true" prop="details" />
          </el-table>
        </el-tab-pane>

        <!-- IoT -->
        <el-tab-pane label="IoT 设备" name="iot">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>注册设备</h4>
              <el-form :model="iotForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="iotForm.deviceCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="iotForm.deviceName" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="iotForm.deviceType">
                    <el-option v-for="t in ['MASSAGE_CHAIR','HUMIDIFIER','SCALE','THERMOMETER','LED','LOCK']" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
                <el-form-item label="协议">
                  <el-select v-model="iotForm.protocol">
                    <el-option v-for="p in ['MQTT','HTTP','MODBUS','ZIGBEE','BLE']" :key="p" :label="p" :value="p" />
                  </el-select>
                </el-form-item>
                <el-form-item label="主题"><el-input v-model="iotForm.mqttTopic" /></el-form-item>
                <el-button type="primary" @click="registerIot" :loading="creatingIot">注册</el-button>
              </el-form>
            </el-col>
            <el-col :span="15">
              <h4>设备看板</h4>
              <el-row :gutter="12">
                <el-col :span="6"><el-statistic title="总数" :value="iotDash.total || 0" /></el-col>
                <el-col :span="6"><el-statistic title="在线" :value="iotDash.online || 0" /></el-col>
                <el-col :span="6"><el-statistic title="离线" :value="iotDash.offline || 0" /></el-col>
                <el-col :span="6"><el-statistic title="今日上报" :value="iotDash.telemetryToday || 0" /></el-col>
              </el-row>

              <h4 style="margin-top:12px">设备列表</h4>
              <el-table :data="iotDevices" size="small" stripe>
                <el-table-column prop="deviceCode" label="编码" width="140" />
                <el-table-column prop="deviceName" label="名称" />
                <el-table-column prop="deviceType" label="类型" width="130" />
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.onlineStatus === 'ONLINE' ? 'success' : 'danger'">{{ row.onlineStatus }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="cmdIot(row.id, 'ON')">开</el-button>
                    <el-button size="small" type="warning" @click="cmdIot(row.id, 'OFF')">关</el-button>
                    <el-button size="small" @click="loadTelemetry(row.id)">遥测</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- GDPR -->
        <el-tab-pane label="GDPR" name="gdpr">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>会员同意管理</h4>
              <el-form :model="gdprForm" label-width="80px" size="small">
                <el-form-item label="会员ID"><el-input-number v-model="gdprForm.memberId" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="gdprForm.consentType">
                    <el-option label="营销" value="MARKETING" />
                    <el-option label="分析" value="ANALYTICS" />
                    <el-option label="画像" value="PROFILING" />
                    <el-option label="第三方" value="THIRD_PARTY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="授权">
                  <el-switch v-model="gdprForm.granted" />
                </el-form-item>
                <el-button type="primary" @click="grantConsent">授权</el-button>
              </el-form>

              <el-divider />
              <h4>数据主体请求</h4>
              <el-form :model="gdprReqForm" label-width="80px" size="small">
                <el-form-item label="会员ID"><el-input-number v-model="gdprReqForm.memberId" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="gdprReqForm.requestType">
                    <el-option label="导出" value="EXPORT" />
                    <el-option label="删除" value="DELETE" />
                    <el-option label="纠正" value="RECTIFY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="原因"><el-input v-model="gdprReqForm.reason" /></el-form-item>
                <el-button type="warning" @click="submitGdprRequest">提交请求</el-button>
              </el-form>
            </el-col>
            <el-col :span="15">
              <h4>GDPR 请求列表</h4>
              <el-table :data="gdprRequests" size="small" stripe>
                <el-table-column prop="requestNo" label="请求号" width="180" />
                <el-table-column prop="memberId" label="会员" width="80" />
                <el-table-column prop="requestType" label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.requestType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : 'warning'">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="exportUrl" label="导出" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="processGdpr(row.id)">处理</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <h4 style="margin-top:12px">审计日志</h4>
              <el-table :data="gdprAudit" size="small" stripe>
                <el-table-column prop="action" label="动作" />
                <el-table-column prop="targetType" label="对象" width="100" />
                <el-table-column prop="targetId" label="ID" width="80" />
                <el-table-column label="PII" width="60">
                  <template #default="{ row }">
                    <el-tag v-if="row.piiData" size="small" type="danger">是</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="时间" width="180" />
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- HIPAA + Maps + POS 合 -->
        <el-tab-pane label="HIPAA + Maps + POS" name="rest">
          <el-row :gutter="20">
            <el-col :span="8">
              <h4>HIPAA PHI 访问</h4>
              <el-table :data="phiAccessList" size="small" stripe>
                <el-table-column prop="memberId" label="会员" width="80" />
                <el-table-column prop="fieldName" label="字段" width="120" />
                <el-table-column prop="accessType" label="类型" width="80" />
                <el-table-column prop="actorName" label="操作人" />
                <el-table-column prop="createdAt" label="时间" width="180" />
              </el-table>
              <el-button size="small" type="primary" @click="loadPhi" style="margin-top:8px">刷新</el-button>
            </el-col>

            <el-col :span="8">
              <h4>地理围栏</h4>
              <el-form :model="fenceForm" label-width="80px" size="small">
                <el-form-item label="名称"><el-input v-model="fenceForm.fenceName" /></el-form-item>
                <el-form-item label="经度"><el-input-number v-model="fenceForm.centerLng" :precision="6" /></el-form-item>
                <el-form-item label="纬度"><el-input-number v-model="fenceForm.centerLat" :precision="6" /></el-form-item>
                <el-form-item label="半径(米)"><el-input-number v-model="fenceForm.radiusM" :min="50" /></el-form-item>
                <el-button type="primary" @click="createFence">创建围栏</el-button>
              </el-form>

              <el-divider />
              <h4>Geocoding</h4>
              <el-input v-model="geocodeAddr" placeholder="输入地址" />
              <el-button @click="geocode" size="small">解析</el-button>
              <div v-if="geocodeResult" class="geo-result">
                <p>lat: {{ geocodeResult.lat }}</p>
                <p>lng: {{ geocodeResult.lng }}</p>
              </div>
            </el-col>

            <el-col :span="8">
              <h4>POS 看板</h4>
              <el-row :gutter="8">
                <el-col :span="12"><el-statistic title="总交易" :value="posDash.totalTx || 0" /></el-col>
                <el-col :span="12"><el-statistic title="营收" :value="posDash.totalRevenue || 0" /></el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:8px">
                <el-col :span="12"><el-statistic title="小费" :value="posDash.totalTips || 0" /></el-col>
                <el-col :span="12"><el-statistic title="在线设备" :value="posDash.onlineDevices || 0" /></el-col>
              </el-row>

              <h4 style="margin-top:12px">POS 支付</h4>
              <el-form :model="posForm" label-width="80px" size="small">
                <el-form-item label="金额"><el-input-number v-model="posForm.amount" :min="0" /></el-form-item>
                <el-form-item label="小费"><el-input-number v-model="posForm.tip" :min="0" /></el-form-item>
                <el-form-item label="币种">
                  <el-select v-model="posForm.currency">
                    <el-option label="USD" value="USD" />
                    <el-option label="EUR" value="EUR" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="posCharge">刷卡</el-button>
              </el-form>
              <div v-if="posResult" class="pos-result">
                <p>txNo: {{ posResult.txNo }}</p>
                <p>状态: <el-tag size="small">{{ posResult.status }}</el-tag></p>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi } from '@/api/sys'

const activeTab = ref('vision')
const visionSubTab = ref('skin')

const cvModels = ref<any[]>([])
const cvStats = ref<any>({})
const initCvL = ref(false)

const skinForm = ref<any>({ memberId: 0, imageUrl: 'https://example.com/face.jpg' })
const recognizing = ref(false)
const skinResult = ref<any>(null)

const nailForm = ref<any>({ handImageUrl: 'https://example.com/hand.jpg', styleImageUrl: 'https://example.com/style.jpg' })
const nailResult = ref<any>(null)

const faceForm = ref<any>({ imageUrl: 'https://example.com/face.jpg' })
const faceResult = ref<any>(null)

const ltvL = ref(false)
const churnL = ref(false)
const ltvDash = ref<any>({})
const ltvMemberId = ref(1)
const memberLtv = ref<any>(null)
const churnMemberId = ref(1)
const memberChurn = ref<any>(null)
const topLtvList = ref<any[]>([])
const churnRiskList = ref<any[]>([])

const iotDash = ref<any>({})
const iotDevices = ref<any[]>([])
const iotForm = ref<any>({ deviceCode: '', deviceName: '', deviceType: 'MASSAGE_CHAIR', protocol: 'MQTT', mqttTopic: '' })
const creatingIot = ref(false)

const gdprForm = ref<any>({ memberId: 0, consentType: 'MARKETING', granted: true })
const gdprReqForm = ref<any>({ memberId: 0, requestType: 'EXPORT', reason: '' })
const gdprRequests = ref<any[]>([])
const gdprAudit = ref<any[]>([])

const phiAccessList = ref<any[]>([])

const fenceForm = ref<any>({ fenceName: '', centerLng: 116.397, centerLat: 39.908, radiusM: 200 })
const geocodeAddr = ref('')
const geocodeResult = ref<any>(null)

const posForm = ref<any>({ amount: 100, tip: 10, currency: 'USD' })
const posResult = ref<any>(null)
const posDash = ref<any>({})

// ==================== AI 视觉 ====================
async function initCvModels() {
  initCvL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/ai/vision/model/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个模型`)
    loadCvModels()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initCvL.value = false }
}

async function loadCvModels() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/ai/vision/model/list' })
    cvModels.value = r.data || []
  } catch (e) { cvModels.value = [] }
}

async function loadCvStats() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/ai/vision/stats' })
    cvStats.value = r.data || {}
  } catch (e) { cvStats.value = {} }
}

async function recognizeSkin() {
  if (!skinForm.value.memberId) { ElMessage.warning('请填会员ID'); return }
  recognizing.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/ai/vision/recognize/skin', data: skinForm.value })
    skinResult.value = r.data?.cvResult
    ElMessage.success('识别完成')
    loadCvStats()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { recognizing.value = false }
}

async function nailTryon() {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/ai/vision/recognize/nail-tryon', data: nailForm.value })
    nailResult.value = r.data
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function recognizeFace() {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/ai/vision/recognize/face', data: faceForm.value })
    faceResult.value = r.data
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

// ==================== LTV / Churn ====================
async function runLtvCompute() {
  ltvL.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/ltv-churn/ltv/compute', data: {} })
    ElMessage.success('LTV 已计算')
    await loadLtvDashboard()
    await loadTopLtv()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { ltvL.value = false }
}

async function runChurnCompute() {
  churnL.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/ltv-churn/churn/compute', data: {} })
    ElMessage.success('Churn 已计算')
    await loadLtvDashboard()
    await loadChurnRisk()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { churnL.value = false }
}

async function loadLtvDashboard() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/ltv-churn/dashboard' })
    ltvDash.value = r.data || {}
  } catch (e) { ltvDash.value = {} }
}

async function loadMemberLtv() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/ltv-churn/ltv/member/${ltvMemberId.value}` })
    memberLtv.value = r.data
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function loadMemberChurn() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/ltv-churn/churn/member/${churnMemberId.value}` })
    memberChurn.value = r.data
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function loadTopLtv() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/ltv-churn/ltv/top', params: { limit: 20 } })
    topLtvList.value = r.data || []
  } catch (e) { topLtvList.value = [] }
}

async function loadChurnRisk() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/ltv-churn/churn/risk-list', params: { limit: 20 } })
    churnRiskList.value = r.data || []
  } catch (e) { churnRiskList.value = [] }
}

// ==================== IoT ====================
async function registerIot() {
  if (!iotForm.value.deviceCode) { ElMessage.warning('请填编码'); return }
  creatingIot.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/iot/device/register', data: iotForm.value })
    ElMessage.success('已注册')
    loadIotDevices()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { creatingIot.value = false }
}

async function loadIotDevices() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/iot/device/list' })
    iotDevices.value = r.data || []
  } catch (e) { iotDevices.value = [] }
}

async function loadIotDash() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/iot/device/dashboard' })
    iotDash.value = r.data || {}
  } catch (e) { iotDash.value = {} }
}

async function cmdIot(id: number, cmd: string) {
  await sysApi.request({ method: 'POST', url: `/iot/device/${id}/command`, data: { command: cmd } })
  ElMessage.success('命令已下发')
}

async function loadTelemetry(id: number) {
  const r: any = await sysApi.request({ method: 'GET', url: `/iot/device/${id}/telemetry/latest` })
  ElMessage.success('遥测已加载')
  console.log(r.data)
}

// ==================== GDPR ====================
async function grantConsent() {
  if (!gdprForm.value.memberId) { ElMessage.warning('请填会员ID'); return }
  await sysApi.request({ method: 'POST', url: '/p2-suite/gdpr/consent', data: gdprForm.value })
  ElMessage.success('已记录')
}

async function submitGdprRequest() {
  if (!gdprReqForm.value.memberId) { ElMessage.warning('请填会员ID'); return }
  await sysApi.request({ method: 'POST', url: '/p2-suite/gdpr/request', data: gdprReqForm.value })
  ElMessage.success('已提交')
  loadGdprRequests()
}

async function loadGdprRequests() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p2-suite/gdpr/request/list' })
    gdprRequests.value = r.data || []
  } catch (e) { gdprRequests.value = [] }
}

async function loadGdprAudit() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p2-suite/gdpr/audit' })
    gdprAudit.value = r.data || []
  } catch (e) { gdprAudit.value = [] }
}

async function processGdpr(id: number) {
  await sysApi.request({ method: 'POST', url: `/p2-suite/gdpr/request/${id}/process`, data: {} })
  ElMessage.success('已处理')
  loadGdprRequests()
}

// ==================== HIPAA + Maps + POS ====================
async function loadPhi() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p2-suite/hipaa/phi-access/list' })
    phiAccessList.value = r.data || []
  } catch (e) { phiAccessList.value = [] }
}

async function createFence() {
  await sysApi.request({ method: 'POST', url: '/p2-suite/map/geofence', data: fenceForm.value })
  ElMessage.success('已创建')
}

async function geocode() {
  if (!geocodeAddr.value) return
  const r: any = await sysApi.request({ method: 'POST', url: '/p2-suite/map/geocode', data: { address: geocodeAddr.value } })
  geocodeResult.value = r.data
}

async function posCharge() {
  const r: any = await sysApi.request({ method: 'POST', url: '/p2-suite/pos/charge', data: posForm.value })
  posResult.value = r.data
  ElMessage.success('已支付')
  loadPosDash()
}

async function loadPosDash() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p2-suite/pos/dashboard' })
    posDash.value = r.data || {}
  } catch (e) { posDash.value = {} }
}

function loadAll() {
  loadCvModels()
  loadCvStats()
  loadLtvDashboard()
  loadTopLtv()
  loadChurnRisk()
  loadIotDevices()
  loadIotDash()
  loadGdprRequests()
  loadGdprAudit()
  loadPhi()
  loadPosDash()
}

onMounted(() => loadAll())
</script>

<style scoped>
.page { padding: 16px; max-width: 1500px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.cv-result { padding: 12px; background: #f0f9ff; border-radius: 8px; margin-top: 12px; font-size: 13px }
.ltv-detail, .churn-detail { padding: 12px; background: #fef3c7; border-radius: 8px; margin-top: 8px; font-size: 12px }
.geo-result, .pos-result { padding: 8px; background: #f5f7fa; border-radius: 4px; margin-top: 8px; font-size: 12px }
</style>