<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">P1 套件：款式图库 / LBS / 礼品卡 / 订阅 / 支付 / 国际预约 / 签到 / SOP / 风控 / Cohort</span>
          <el-space>
            <el-button @click="initStyles" type="success" plain :loading="initStylesL">初始化款式</el-button>
            <el-button @click="initSubs" type="warning" plain :loading="initSubsL">初始化订阅</el-button>
            <el-button @click="initSops" type="danger" plain :loading="initSopsL">初始化 SOP</el-button>
            <el-button @click="initRisks" type="info" plain :loading="initRisksL">初始化风控</el-button>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 款式图库 -->
        <el-tab-pane label="款式图库" name="style">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>新建款式</h4>
              <el-form :model="styleForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="styleForm.styleCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="styleForm.styleName" /></el-form-item>
                <el-form-item label="分类">
                  <el-select v-model="styleForm.category">
                    <el-option v-for="c in ['NORMAL','ART','GLITTER','FRENCH','MATTE','CHROME','3D']" :key="c" :label="c" :value="c" />
                  </el-select>
                </el-form-item>
                <el-form-item label="难度">
                  <el-rate v-model="styleForm.difficulty" />
                </el-form-item>
                <el-form-item label="耗时(分)"><el-input-number v-model="styleForm.estimatedMinutes" :min="20" /></el-form-item>
                <el-form-item label="价格"><el-input-number v-model="styleForm.basePrice" :min="0" /></el-form-item>
                <el-form-item label="标签"><el-input v-model="styleForm.tags" placeholder="HOT,CLASSIC" /></el-form-item>
                <el-form-item label="描述"><el-input v-model="styleForm.description" type="textarea" :rows="2" /></el-form-item>
                <el-button type="primary" @click="createStyle" :loading="creatingStyle">创建</el-button>
              </el-form>
            </el-col>
            <el-col :span="15">
              <h4>款式列表</h4>
              <el-row :gutter="12">
                <el-col v-for="s in styles" :key="s.id" :span="8" class="style-card">
                  <el-card :body-style="{ padding: 0 }">
                    <div class="style-cover">
                      <span class="style-name">{{ s.styleName }}</span>
                      <div class="style-tags">
                        <el-tag v-if="s.isHot" size="small" type="danger">HOT</el-tag>
                        <el-tag v-if="s.isNew" size="small" type="success">NEW</el-tag>
                        <el-tag v-if="s.isRecommend" size="small" type="warning">推荐</el-tag>
                      </div>
                    </div>
                    <div class="style-info">
                      <div class="style-price">¥{{ s.basePrice }}</div>
                      <div class="style-desc">{{ s.description }}</div>
                      <div class="style-stats">
                        <span>👁 {{ s.viewCount }}</span>
                        <span>❤️ {{ s.likeCount }}</span>
                        <span>📦 {{ s.orderCount }}</span>
                      </div>
                      <el-space>
                        <el-button size="small" type="primary" @click="quickOrder(s)">下单</el-button>
                        <el-button size="small" @click="likeStyle(s.id)">点赞</el-button>
                      </el-space>
                    </div>
                  </el-card>
                </el-col>
              </el-row>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- LBS 上门 -->
        <el-tab-pane label="LBS 上门" name="lbs">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>注册技师</h4>
              <el-form :model="techForm" label-width="80px" size="small">
                <el-form-item label="技师ID"><el-input-number v-model="techForm.technicianId" /></el-form-item>
                <el-form-item label="姓名"><el-input v-model="techForm.name" /></el-form-item>
                <el-form-item label="电话"><el-input v-model="techForm.phone" /></el-form-item>
                <el-form-item label="经度"><el-input-number v-model="techForm.currentLng" :precision="6" /></el-form-item>
                <el-form-item label="纬度"><el-input-number v-model="techForm.currentLat" :precision="6" /></el-form-item>
                <el-form-item label="半径(km)"><el-input-number v-model="techForm.maxRadiusKm" :min="1" /></el-form-item>
                <el-button type="primary" @click="registerTech" :loading="creatingTech">注册</el-button>
              </el-form>

              <el-divider />
              <h4>下单（自动派单）</h4>
              <el-form :model="dispatchForm" label-width="80px" size="small">
                <el-form-item label="会员ID"><el-input-number v-model="dispatchForm.memberId" /></el-form-item>
                <el-form-item label="姓名"><el-input v-model="dispatchForm.memberName" /></el-form-item>
                <el-form-item label="电话"><el-input v-model="dispatchForm.memberPhone" /></el-form-item>
                <el-form-item label="服务ID"><el-input-number v-model="dispatchForm.serviceId" /></el-form-item>
                <el-form-item label="服务名称"><el-input v-model="dispatchForm.serviceName" /></el-form-item>
                <el-form-item label="地址"><el-input v-model="dispatchForm.address" /></el-form-item>
                <el-form-item label="经度"><el-input-number v-model="dispatchForm.lng" :precision="6" /></el-form-item>
                <el-form-item label="纬度"><el-input-number v-model="dispatchForm.lat" :precision="6" /></el-form-item>
                <el-form-item label="预约时间"><el-input v-model="dispatchForm.scheduledTime" placeholder="2026-08-20T10:00:00" /></el-form-item>
                <el-form-item label="金额"><el-input-number v-model="dispatchForm.amount" /></el-form-item>
                <el-button type="warning" @click="dispatchOrder" :loading="dispatching">下单并派单</el-button>
              </el-form>
            </el-col>
            <el-col :span="15">
              <h4>上门订单</h4>
              <el-table :data="lbsOrders" size="small" stripe>
                <el-table-column prop="order_no" label="订单号" width="180" />
                <el-table-column prop="member_name" label="会员" width="100" />
                <el-table-column prop="address" label="地址" />
                <el-table-column label="距离/费用" width="120">
                  <template #default="{ row }">
                    <span v-if="row.technician_id">¥{{ row.travel_fee }} · {{ row.technicianId }}</span>
                    <span v-else>未派</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="lbsStatusType(row.dispatch_status)">{{ row.dispatch_status }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>

              <div v-if="lastDispatch" class="dispatch-result">
                <h4>派单结果</h4>
                <p>技师：{{ lastDispatch.technicianName }} ({{ lastDispatch.phone }})</p>
                <p>距离：{{ lastDispatch.distanceKm }} km</p>
                <p>路费：¥{{ lastDispatch.travelFee }}</p>
                <p>评分：{{ lastDispatch.rating }}</p>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 礼品卡 -->
        <el-tab-pane label="礼品卡" name="gift">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>购卡</h4>
              <el-form :model="giftForm" label-width="80px" size="small">
                <el-form-item label="面值"><el-input-number v-model="giftForm.faceValue" :min="100" /></el-form-item>
                <el-form-item label="收卡人"><el-input v-model="giftForm.recipientName" /></el-form-item>
                <el-form-item label="收卡手机"><el-input v-model="giftForm.recipientPhone" /></el-form-item>
                <el-form-item label="祝福语"><el-input v-model="giftForm.giftMessage" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="主题">
                  <el-select v-model="giftForm.theme">
                    <el-option label="默认" value="DEFAULT" />
                    <el-option label="生日" value="BIRTHDAY" />
                    <el-option label="节日" value="HOLIDAY" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="purchaseGift" :loading="purchasingGift">购买</el-button>
              </el-form>

              <div v-if="lastGift" class="gift-display">
                <el-card>
                  <h4>🎁 {{ lastGift.recipientName }}</h4>
                  <p>面值：¥{{ lastGift.faceValue }}</p>
                  <p>卡号：{{ lastGift.cardNo }}</p>
                  <p>主题：{{ lastGift.theme }}</p>
                  <p>消息：{{ lastGift.giftMessage }}</p>
                </el-card>
              </div>
            </el-col>
            <el-col :span="14">
              <h4>礼品卡列表</h4>
              <el-table :data="giftCards" size="small" stripe>
                <el-table-column prop="cardNo" label="卡号" width="160" />
                <el-table-column prop="recipientName" label="收卡人" width="100" />
                <el-table-column prop="faceValue" label="面值" width="80" />
                <el-table-column prop="balance" label="余额" width="80" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="giftStatusType(row.status)">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="expireTime" label="过期" width="160" />
                <el-table-column label="操作" width="140">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="redeemGift(row)">核销</el-button>
                    <el-button size="small" type="danger" @click="refundGift(row.id)">退款</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 订阅 -->
        <el-tab-pane label="订阅" name="subscription">
          <el-row :gutter="20">
            <el-col :span="14">
              <h4>订阅计划</h4>
              <el-row :gutter="12">
                <el-col v-for="p in plans" :key="p.id" :span="8">
                  <el-card class="plan-card">
                    <h4>{{ p.planName }}</h4>
                    <div class="plan-price">¥{{ p.price }} / {{ p.planType }}</div>
                    <p>次数：{{ p.serviceCount }} 次/月</p>
                    <p>折扣：{{ p.discountRate }}折</p>
                    <p>积分：{{ p.giftPoints }}</p>
                    <el-button type="primary" @click="subscribePlan(p.id)" :loading="subscribing">订阅</el-button>
                  </el-card>
                </el-col>
              </el-row>
            </el-col>
            <el-col :span="10">
              <h4>订阅看板</h4>
              <el-row :gutter="12">
                <el-col :span="12"><el-statistic title="活跃订阅" :value="subDashboard.activeSubscribers || 0" /></el-col>
                <el-col :span="12"><el-statistic title="MRR" :value="subDashboard.mrr || 0" /></el-col>
              </el-row>
              <el-row :gutter="12" style="margin-top:12px">
                <el-col :span="12"><el-statistic title="ARR" :value="subDashboard.arr || 0" /></el-col>
                <el-col :span="12"><el-statistic title="总营收" :value="subDashboard.totalRevenue || 0" /></el-col>
              </el-row>
              <el-button type="warning" @click="triggerRenew" style="margin-top:12px">手动触发续费扫描</el-button>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 支付/国际/签到 简版 -->
        <el-tab-pane label="支付 + 国际预约" name="pay">
          <el-row :gutter="20">
            <el-col :span="12">
              <h4>支付意图（Stripe/Square/BNPL）</h4>
              <el-form :model="payForm" label-width="80px" size="small">
                <el-form-item label="金额"><el-input-number v-model="payForm.amount" :min="1" /></el-form-item>
                <el-form-item label="网关">
                  <el-select v-model="payForm.gatewayCode">
                    <el-option v-for="g in gateways" :key="g.gatewayCode" :label="g.gatewayName || g.gatewayCode" :value="g.gatewayCode" />
                  </el-select>
                </el-form-item>
                <el-form-item label="币种">
                  <el-select v-model="payForm.currency">
                    <el-option label="USD" value="USD" />
                    <el-option label="EUR" value="EUR" />
                    <el-option label="CNY" value="CNY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="方式">
                  <el-select v-model="payForm.method">
                    <el-option label="信用卡" value="CARD" />
                    <el-option label="BNPL" value="BNPL" />
                    <el-option label="钱包" value="WALLET" />
                  </el-select>
                </el-form-item>
                <el-form-item label="分期" v-if="payForm.method === 'BNPL'">
                  <el-select v-model="payForm.installments">
                    <el-option :label="'3期'" :value="3" />
                    <el-option :label="'6期'" :value="6" />
                    <el-option :label="'12期'" :value="12" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="createIntent" :loading="creatingIntent">创建支付</el-button>
              </el-form>
              <div v-if="lastIntent" class="intent-result">
                <p>Intent: {{ lastIntent.intentNo }}</p>
                <p>Secret: {{ lastIntent.clientSecret }}</p>
                <p>Txn: {{ lastIntent.transactionId }}</p>
                <el-tag>{{ lastIntent.status }}</el-tag>
              </div>
            </el-col>
            <el-col :span="12">
              <h4>国际预约（Google/IG）</h4>
              <el-form :model="obForm" label-width="80px" size="small">
                <el-form-item label="来源">
                  <el-select v-model="obForm.source">
                    <el-option label="Google" value="GOOGLE" />
                    <el-option label="Instagram" value="INSTAGRAM" />
                  </el-select>
                </el-form-item>
                <el-form-item label="客户姓名"><el-input v-model="obForm.customerName" /></el-form-item>
                <el-form-item label="客户电话"><el-input v-model="obForm.customerPhone" /></el-form-item>
                <el-form-item label="客户邮箱"><el-input v-model="obForm.customerEmail" /></el-form-item>
                <el-form-item label="服务名称"><el-input v-model="obForm.serviceName" /></el-form-item>
                <el-form-item label="预约日期"><el-input v-model="obForm.bookingDate" placeholder="2026-08-20" /></el-form-item>
                <el-form-item label="预约时间"><el-input v-model="obForm.bookingTime" placeholder="14:00" /></el-form-item>
                <el-button type="success" @click="webhookBooking" :loading="creatingOb">Webhook 入站</el-button>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 签到 + SOP -->
        <el-tab-pane label="签到 + SOP" name="kiosk-sop">
          <el-row :gutter="20">
            <el-col :span="12">
              <h4>自助签到</h4>
              <el-form :model="checkinForm" label-width="80px" size="small">
                <el-form-item label="设备ID"><el-input-number v-model="checkinForm.deviceId" /></el-form-item>
                <el-form-item label="会员ID"><el-input-number v-model="checkinForm.memberId" /></el-form-item>
                <el-form-item label="预约ID"><el-input-number v-model="checkinForm.bookingId" /></el-form-item>
                <el-form-item label="方式">
                  <el-select v-model="checkinForm.method">
                    <el-option label="扫码" value="QRCODE" />
                    <el-option label="手机" value="PHONE" />
                    <el-option label="人脸" value="FACEID" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="checkin">签到</el-button>
              </el-form>

              <el-divider />
              <h4>签到流水</h4>
              <el-table :data="checkins" size="small" stripe>
                <el-table-column prop="id" label="ID" width="60" />
                <el-table-column prop="member_name" label="会员" width="120" />
                <el-table-column prop="checkin_method" label="方式" width="80" />
                <el-table-column prop="status" label="状态" width="120" />
                <el-table-column prop="checkin_time" label="时间" />
              </el-table>
            </el-col>
            <el-col :span="12">
              <h4>并发症 SOP</h4>
              <el-table :data="sops" size="small" stripe>
                <el-table-column prop="sopCode" label="编码" width="120" />
                <el-table-column prop="sopName" label="名称" />
                <el-table-column label="严重性" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="sopSeverityType(row.severity)">{{ row.severity }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="responseDeadlineMin" label="响应(分)" width="80" />
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 风控 + Cohort -->
        <el-tab-pane label="风控 + Cohort" name="risk-cohort">
          <el-row :gutter="20">
            <el-col :span="12">
              <h4>风控规则</h4>
              <el-table :data="riskRules" size="small" stripe>
                <el-table-column prop="ruleCode" label="编码" width="140" />
                <el-table-column prop="ruleName" label="名称" />
                <el-table-column label="等级" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="riskLevelType(row.ruleLevel)">{{ row.ruleLevel }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="hitCount" label="命中" width="60" />
              </el-table>
            </el-col>
            <el-col :span="12">
              <h4>Cohort 留存透视</h4>
              <el-button type="warning" @click="computeCohort" :loading="computingCohort">重新计算</el-button>
              <div ref="cohortChart" style="width:100%;height:300px;margin-top:12px"></div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { sysApi } from '@/api/sys'

const activeTab = ref('style')

const styles = ref<any[]>([])
const styleForm = ref<any>({ styleCode: '', styleName: '', category: 'NORMAL', difficulty: 2, estimatedMinutes: 60, basePrice: 0, tags: '', description: '' })
const creatingStyle = ref(false)
const initStylesL = ref(false)

const techs = ref<any[]>([])
const techForm = ref<any>({ technicianId: 0, name: '', phone: '', currentLat: 39.908823, currentLng: 116.397470, maxRadiusKm: 10 })
const creatingTech = ref(false)
const dispatchForm = ref<any>({ memberId: 0, memberName: '', memberPhone: '', serviceId: 0, serviceName: '', address: '', lat: 39.908823, lng: 116.397470, scheduledTime: '2026-08-20T10:00:00', amount: 0 })
const dispatching = ref(false)
const lbsOrders = ref<any[]>([])
const lastDispatch = ref<any>(null)

const giftCards = ref<any[]>([])
const giftForm = ref<any>({ faceValue: 200, recipientName: '', recipientPhone: '', giftMessage: '', theme: 'DEFAULT' })
const purchasingGift = ref(false)
const lastGift = ref<any>(null)

const plans = ref<any[]>([])
const subDashboard = ref<any>({})
const subscribing = ref(false)
const initSubsL = ref(false)

const gateways = ref<any[]>([])
const payForm = ref<any>({ amount: 100, gatewayCode: 'STRIPE', currency: 'USD', method: 'CARD', installments: 3 })
const creatingIntent = ref(false)
const lastIntent = ref<any>(null)

const obForm = ref<any>({ source: 'GOOGLE', customerName: '', customerPhone: '', customerEmail: '', serviceName: '', bookingDate: '', bookingTime: '' })
const creatingOb = ref(false)

const checkinForm = ref<any>({ deviceId: 0, memberId: 0, bookingId: 0, method: 'QRCODE' })
const checkins = ref<any[]>([])

const sops = ref<any[]>([])
const initSopsL = ref(false)

const riskRules = ref<any[]>([])
const initRisksL = ref(false)

const cohortChart = ref<HTMLDivElement>()
let cohortEChart: any = null
const computingCohort = ref(false)

function lbsStatusType(s: string): any {
  if (s === 'DISPATCHED') return 'warning'
  if (s === 'ACCEPTED' || s === 'ARRIVED') return 'primary'
  if (s === 'COMPLETED') return 'success'
  if (s === 'CANCELLED') return 'info'
  return ''
}

function giftStatusType(s: string): any {
  if (s === 'UNUSED') return 'success'
  if (s === 'USED') return 'info'
  if (s === 'EXPIRED') return 'warning'
  if (s === 'REFUNDED') return 'danger'
  return ''
}

function sopSeverityType(s: string): any {
  if (s === 'CRITICAL') return 'danger'
  if (s === 'HIGH') return 'warning'
  if (s === 'MEDIUM') return ''
  return 'success'
}

function riskLevelType(s: string): any {
  if (s === 'CRITICAL') return 'danger'
  if (s === 'HIGH') return 'warning'
  if (s === 'RISK') return ''
  if (s === 'WARN') return 'info'
  return 'success'
}

// ========= 款式 =========
async function initStyles() {
  initStylesL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/style/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个款式`)
    loadStyles()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initStylesL.value = false }
}

async function createStyle() {
  if (!styleForm.value.styleCode || !styleForm.value.styleName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creatingStyle.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/style', data: styleForm.value })
    ElMessage.success('已创建')
    styleForm.value = { styleCode: '', styleName: '', category: 'NORMAL', difficulty: 2, estimatedMinutes: 60, basePrice: 0, tags: '', description: '' }
    loadStyles()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { creatingStyle.value = false }
}

async function loadStyles() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/style/list' })
    styles.value = r.data || []
  } catch (e) { styles.value = [] }
}

async function quickOrder(s: any) {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: `/style/${s.id}/order`, data: {} })
    ElMessage.success(`已下单：${r.data?.styleName}`)
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function likeStyle(id: number) {
  await sysApi.request({ method: 'POST', url: `/style/${id}/like` })
  loadStyles()
}

// ========= LBS =========
async function registerTech() {
  creatingTech.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/lbs/technician', data: techForm.value })
    ElMessage.success('已注册')
    loadTechs()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { creatingTech.value = false }
}

async function loadTechs() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/lbs/technician/list' })
    techs.value = r.data || []
  } catch (e) { techs.value = [] }
}

async function dispatchOrder() {
  dispatching.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/lbs/order/dispatch', data: dispatchForm.value })
    ElMessage.success('已派单')
    lastDispatch.value = r.data
    loadLbsOrders()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { dispatching.value = false }
}

async function loadLbsOrders() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/lbs/order/list' })
    lbsOrders.value = r.data || []
  } catch (e) { lbsOrders.value = [] }
}

// ========= 礼品卡 =========
async function purchaseGift() {
  purchasingGift.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/gift-card/purchase', data: giftForm.value })
    ElMessage.success('已购卡')
    lastGift.value = r.data
    loadGiftCards()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { purchasingGift.value = false }
}

async function loadGiftCards() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/gift-card/list' })
    giftCards.value = r.data || []
  } catch (e) { giftCards.value = [] }
}

async function redeemGift(row: any) {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/gift-card/redeem', data: { cardNo: row.cardNo, amount: 100 } })
    ElMessage.success(`已核销：余额 ${r.data?.balance}`)
    loadGiftCards()
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function refundGift(id: number) {
  await sysApi.request({ method: 'POST', url: `/gift-card/${id}/refund` })
  ElMessage.success('已退款')
  loadGiftCards()
}

// ========= 订阅 =========
async function initSubs() {
  initSubsL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/subscription/plan/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个计划`)
    loadPlans()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initSubsL.value = false }
}

async function loadPlans() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/subscription/plan/list' })
    plans.value = r.data || []
  } catch (e) { plans.value = [] }
}

async function subscribePlan(planId: number) {
  subscribing.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/subscription/subscribe', data: { planId, memberId: 1, paymentMethod: 'WECHAT' } })
    ElMessage.success('已订阅')
    loadSubDashboard()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { subscribing.value = false }
}

async function loadSubDashboard() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/subscription/dashboard' })
    subDashboard.value = r.data || {}
  } catch (e) { subDashboard.value = {} }
}

async function triggerRenew() {
  await sysApi.request({ method: 'POST', url: '/subscription/trigger-renew' })
  ElMessage.success('已扫描')
  loadSubDashboard()
}

// ========= 支付 =========
async function loadGateways() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p1-suite/payment/gateway/list' })
    gateways.value = r.data || []
  } catch (e) { gateways.value = [] }
}

async function createIntent() {
  creatingIntent.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/p1-suite/payment/intent', data: payForm.value })
    ElMessage.success('已创建')
    lastIntent.value = r.data
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { creatingIntent.value = false }
}

async function webhookBooking() {
  creatingOb.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/p1-suite/booking/webhook', data: obForm.value })
    ElMessage.success('已入站')
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { creatingOb.value = false }
}

// ========= 签到 =========
async function checkin() {
  try {
    await sysApi.request({ method: 'POST', url: '/p1-suite/kiosk/checkin', data: checkinForm.value })
    ElMessage.success('已签到')
    loadCheckins()
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function loadCheckins() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p1-suite/kiosk/checkin/list' })
    checkins.value = r.data || []
  } catch (e) { checkins.value = [] }
}

// ========= SOP =========
async function initSops() {
  initSopsL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/p1-suite/sop/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个 SOP`)
    loadSops()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initSopsL.value = false }
}

async function loadSops() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p1-suite/sop/list' })
    sops.value = r.data || []
  } catch (e) { sops.value = [] }
}

// ========= 风控 =========
async function initRisks() {
  initRisksL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/p1-suite/risk/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个规则`)
    loadRisks()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initRisksL.value = false }
}

async function loadRisks() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p1-suite/risk/rule/list' })
    riskRules.value = r.data || []
  } catch (e) { riskRules.value = [] }
}

// ========= Cohort =========
async function computeCohort() {
  computingCohort.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/p1-suite/cohort/compute', data: { months: 12 } })
    ElMessage.success('已计算')
    await loadCohortChart()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { computingCohort.value = false }
}

async function loadCohortChart() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p1-suite/cohort/pivot' })
    const data = r.data || {}
    const rows = data.rows || []
    if (rows.length === 0) return
    await nextTick()
    if (!cohortChart.value) return
    if (cohortEChart) cohortEChart.dispose()
    cohortEChart = echarts.init(cohortChart.value)
    cohortEChart.setOption({
      title: { text: 'Cohort 留存率', left: 'center' },
      tooltip: { trigger: 'axis' },
      legend: { top: 30 },
      xAxis: { type: 'category', data: rows.map(r => r.cohort) },
      yAxis: { type: 'value', min: 0, max: 1, axisLabel: { formatter: (v: number) => (v * 100).toFixed(0) + '%' } },
      series: [0, 1, 2, 3, 4, 5].map(i => ({
        name: `M${i}`,
        type: 'line',
        data: rows.map(r => r['s' + i] || 0)
      }))
    })
  } catch (e) {}
}

function loadAll() {
  loadStyles()
  loadTechs()
  loadLbsOrders()
  loadGiftCards()
  loadPlans()
  loadSubDashboard()
  loadGateways()
  loadCheckins()
  loadSops()
  loadRisks()
  loadCohortChart()
}

onMounted(() => loadAll())
</script>

<style scoped>
.page { padding: 16px; max-width: 1500px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.style-card { margin-bottom: 12px }
.style-cover { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; padding: 12px; position: relative }
.style-name { font-size: 14px; font-weight: 600 }
.style-tags { position: absolute; top: 8px; right: 8px }
.style-info { padding: 8px }
.style-price { font-size: 18px; font-weight: bold; color: #f56c6c }
.style-desc { font-size: 12px; color: #909399; margin: 4px 0; min-height: 32px }
.style-stats { font-size: 11px; color: #909399; margin-bottom: 6px }
.style-stats span { margin-right: 8px }
.plan-card { text-align: center; margin-bottom: 12px }
.plan-price { font-size: 22px; font-weight: bold; color: #f56c6c; margin: 8px 0 }
.dispatch-result { padding: 12px; background: #f0f9ff; border-radius: 8px; margin-top: 12px }
.intent-result { padding: 12px; background: #f0fdf4; border-radius: 8px; margin-top: 12px; font-size: 12px }
.gift-display { margin-top: 12px }
</style>