<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">补齐 4 项：生日提醒 / NoShow AI / 大众点评 / 独立配方</span>
          <el-space>
            <el-button @click="initDefaultFormula" type="success" plain :loading="initL">初始化配方</el-button>
            <el-button @click="initNoShowModel" type="warning" plain :loading="initMl">初始化模型</el-button>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 1. 生日提醒 -->
        <el-tab-pane label="生日提醒" name="bday">
          <el-row :gutter="20">
            <el-col :span="8">
              <h4>看板</h4>
              <el-row :gutter="8">
                <el-col :span="12"><el-statistic title="录入数" :value="bdayDash.total || 0" /></el-col>
                <el-col :span="12"><el-statistic title="自动发放" :value="bdayDash.autoSend || 0" /></el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:8px">
                <el-col :span="12"><el-statistic title="今年已发" :value="bdayDash.sentThisYear || 0" /></el-col>
                <el-col :span="12"><el-statistic title="7天内生日" :value="bdayDash.upcoming7d?.length || 0" /></el-col>
              </el-row>
            </el-col>
            <el-col :span="8">
              <h4>录入生日</h4>
              <el-form :model="bdayForm" label-width="80px" size="small">
                <el-form-item label="会员ID"><el-input-number v-model="bdayForm.memberId" /></el-form-item>
                <el-form-item label="生日"><el-input v-model="bdayForm.birthday" placeholder="1990-01-15" /></el-form-item>
                <el-form-item label="公历/农历">
                  <el-radio-group v-model="bdayForm.birthdayType">
                    <el-radio value="SOLAR">公历</el-radio>
                    <el-radio value="LUNAR">农历</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="自动发放"><el-switch v-model="bdayForm.autoSend" /></el-form-item>
                <el-form-item label="优惠券"><el-input v-model="bdayForm.autoCoupon" /></el-form-item>
                <el-form-item label="金额"><el-input-number v-model="bdayForm.autoCouponAmount" /></el-form-item>
                <el-button type="primary" @click="registerBday">录入</el-button>
              </el-form>
            </el-col>
            <el-col :span="8">
              <h4>手动发礼</h4>
              <el-form :model="bdaySendForm" label-width="80px" size="small">
                <el-form-item label="会员ID"><el-input-number v-model="bdaySendForm.memberId" /></el-form-item>
                <el-form-item label="礼物类型">
                  <el-select v-model="bdaySendForm.giftType">
                    <el-option label="优惠券" value="COUPON" />
                    <el-option label="积分" value="POINTS" />
                    <el-option label="生日礼包" value="GIFT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="发送通道">
                  <el-select v-model="bdaySendForm.sendType">
                    <el-option label="微信" value="WECHAT" />
                    <el-option label="短信" value="SMS" />
                    <el-option label="邮件" value="EMAIL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="自定义消息"><el-input v-model="bdaySendForm.message" type="textarea" :rows="2" /></el-form-item>
                <el-button type="success" @click="sendBdayGift" :loading="sending">发放</el-button>
              </el-form>
            </el-col>
          </el-row>

          <el-divider />
          <h4>近期生日</h4>
          <el-table :data="bdayDash.upcoming7d || []" size="small" stripe>
            <el-table-column prop="memberId" label="会员ID" width="100" />
            <el-table-column prop="daysToBirthday" label="天数" width="80" />
            <el-table-column prop="nextBirthday" label="日期" />
            <el-table-column prop="vipLevel" label="VIP" width="80" />
          </el-table>

          <h4 style="margin-top:12px">发放日志</h4>
          <el-table :data="bdayLogs" size="small" stripe max-height="200">
            <el-table-column prop="member_name" label="会员" />
            <el-table-column prop="send_year" label="年份" width="80" />
            <el-table-column prop="send_type" label="通道" width="100" />
            <el-table-column prop="gift_type" label="礼物" width="100" />
            <el-table-column prop="gift_value" label="内容" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 2. NoShow AI -->
        <el-tab-pane label="NoShow 预测" name="noshow">
          <el-row :gutter="20">
            <el-col :span="8">
              <h4>看板</h4>
              <el-row :gutter="8">
                <el-col :span="12"><el-statistic title="模型数" :value="noshowDash.modelCount || 0" /></el-col>
                <el-col :span="12"><el-statistic title="预测数" :value="noshowDash.predictionCount || 0" /></el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:8px">
                <el-col :span="12"><el-statistic title="高风险预约" :value="noshowDash.highRiskCount || 0" /></el-col>
                <el-col :span="12"><el-statistic title="模型准确率" :value="(noshowDash.modelAccuracy || 0) + '%'" /></el-col>
              </el-row>
            </el-col>
            <el-col :span="8">
              <h4>模型列表</h4>
              <el-table :data="noshowModels" size="small" stripe>
                <el-table-column prop="modelName" label="模型" />
                <el-table-column prop="algorithm" label="算法" width="80" />
                <el-table-column label="准确率" width="80">
                  <template #default="{ row }">{{ row.accuracy }}%</template>
                </el-table-column>
                <el-table-column label="激活" width="70">
                  <template #default="{ row }">
                    <el-tag v-if="row.isActive" size="small" type="success">激活</el-tag>
                    <el-button v-else size="small" @click="activateModel(row.id)">启用</el-button>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button size="small" @click="retrain(row.id)">重训</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
            <el-col :span="8">
              <h4>实时预测</h4>
              <el-form :model="noshowForm" label-width="80px" size="small">
                <el-form-item label="预约ID"><el-input-number v-model="noshowForm.bookingId" /></el-form-item>
                <el-button type="primary" @click="predictNoShow" :loading="predicting">预测</el-button>
                <el-button @click="predictBatchNoShow">批量预测</el-button>
              </el-form>
              <div v-if="noshowResult" class="noshow-result">
                <p>风险分: <strong>{{ noshowResult.riskScore }}</strong> ({{ noshowResult.riskLevel }})</p>
                <p>原因: {{ noshowResult.reason }}</p>
                <p>建议: {{ noshowResult.recommendation }}</p>
              </div>
            </el-col>
          </el-row>

          <el-divider />
          <h4>高风险预约</h4>
          <el-table :data="highRiskList" size="small" stripe max-height="200">
            <el-table-column prop="member_name" label="会员" />
            <el-table-column prop="booking_date" label="预约日期" />
            <el-table-column prop="risk_score" label="风险分" width="80" />
            <el-table-column prop="risk_level" label="等级" width="80">
              <template #default="{ row }">
                <el-tag size="small" type="danger">{{ row.risk_level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" :show-overflow-tooltip="true" />
          </el-table>
        </el-tab-pane>

        <!-- 3. 大众点评 -->
        <el-tab-pane label="大众点评" name="dianping">
          <el-row :gutter="20">
            <el-col :span="8">
              <h4>看板</h4>
              <el-row :gutter="8">
                <el-col :span="12"><el-statistic title="店铺" :value="dpDash.shopCount || 0" /></el-col>
                <el-col :span="12"><el-statistic title="团购" :value="dpDash.dealCount || 0" /></el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:8px">
                <el-col :span="12"><el-statistic title="订单" :value="dpDash.orderCount || 0" /></el-col>
                <el-col :span="12"><el-statistic title="已核销" :value="dpDash.consumedCount || 0" /></el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:8px">
                <el-col :span="12"><el-statistic title="总营收" :value="dpDash.totalRevenue || 0" /></el-col>
              </el-row>
            </el-col>
            <el-col :span="8">
              <h4>店铺绑定</h4>
              <el-form :model="dpShopForm" label-width="80px" size="small">
                <el-form-item label="店铺ID"><el-input v-model="dpShopForm.shopId" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="dpShopForm.shopName" /></el-form-item>
                <el-form-item label="AppKey"><el-input v-model="dpShopForm.appKey" /></el-form-item>
                <el-form-item label="AppSecret"><el-input v-model="dpShopForm.appSecret" type="password" /></el-form-item>
                <el-form-item label="城市"><el-input v-model="dpShopForm.city" /></el-form-item>
                <el-button type="primary" @click="bindDpShop">绑定</el-button>
              </el-form>

              <el-divider />
              <h4>券码核销</h4>
              <el-input v-model="verifyCode" placeholder="输入券码" />
              <el-button @click="verifyDpCode" type="success" style="margin-top:8px">核销</el-button>
            </el-col>
            <el-col :span="8">
              <h4>同步</h4>
              <el-select v-model="currentShop" placeholder="选择店铺" style="width:100%;margin-bottom:8px">
                <el-option v-for="s in dpShops" :key="s.id" :label="s.shopName + ' (' + s.shopId + ')'" :value="s.shopId" />
              </el-select>
              <el-space>
                <el-button @click="syncDpDeals" :loading="syncingDp" type="primary" plain>同步团购</el-button>
                <el-button @click="syncDpOrders" :loading="syncingDp" type="warning" plain>同步订单</el-button>
              </el-space>

              <h4 style="margin-top:12px">团购</h4>
              <el-table :data="dpDeals" size="small" stripe max-height="200">
                <el-table-column prop="dealName" label="名称" />
                <el-table-column prop="category" label="类目" width="100" />
                <el-table-column prop="price" label="价格" width="80" />
                <el-table-column prop="originalPrice" label="原价" width="80" />
              </el-table>
            </el-col>
          </el-row>

          <el-divider />
          <h4>订单列表</h4>
          <el-table :data="dpOrders" size="small" stripe max-height="200">
            <el-table-column prop="dpOrderId" label="点评单号" width="160" />
            <el-table-column prop="buyerName" label="买家" />
            <el-table-column prop="amount" label="金额" width="80" />
            <el-table-column prop="serialNo" label="券码" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'CONSUMED' ? 'success' : 'warning'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 4. 配方 -->
        <el-tab-pane label="护理配方" name="formula">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>创建配方</h4>
              <el-form :model="formulaForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="formulaForm.formulaCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="formulaForm.formulaName" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="formulaForm.formulaType">
                    <el-option label="护肤" value="SKINCARE" />
                    <el-option label="染发" value="HAIR_DYE" />
                    <el-option label="美甲" value="NAIL" />
                    <el-option label="美容仪器" value="BODY" />
                    <el-option label="医美" value="MEDICAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="肤质"><el-select v-model="formulaForm.skinType" multiple>
                  <el-option label="干性" value="DRY" />
                  <el-option label="油性" value="OILY" />
                  <el-option label="混合" value="MIXED" />
                  <el-option label="敏感" value="SENSITIVE" />
                  <el-option label="中性" value="NORMAL" />
                </el-select></el-form-item>
                <el-form-item label="时长(分)"><el-input-number v-model="formulaForm.durationMin" /></el-form-item>
                <el-form-item label="原料JSON"><el-input v-model="formulaForm.ingredientsJson" type="textarea" :rows="2" placeholder='[{"name":"...","ratio":30}]' /></el-form-item>
                <el-form-item label="步骤JSON"><el-input v-model="formulaForm.stepsJson" type="textarea" :rows="2" placeholder='[{"order":1,"action":"..."}]' /></el-form-item>
                <el-form-item label="注意事项"><el-input v-model="formulaForm.caution" /></el-form-item>
                <el-form-item label="高级"><el-switch v-model="formulaForm.isPremium" /></el-form-item>
                <el-button type="primary" @click="createFormula">创建</el-button>
              </el-form>
            </el-col>
            <el-col :span="15">
              <h4>配方列表（{{ formulaList.length }}）</h4>
              <select v-model="formulaTypeFilter" style="margin-bottom:8px;padding:6px">
                <option value="">全部类型</option>
                <option value="SKINCARE">护肤</option>
                <option value="HAIR_DYE">染发</option>
                <option value="NAIL">美甲</option>
                <option value="MEDICAL">医美</option>
                <option value="BODY">美容仪器</option>
              </select>
              <el-table :data="filteredFormula" size="small" stripe>
                <el-table-column prop="formulaCode" label="编码" width="140" />
                <el-table-column prop="formulaName" label="名称" />
                <el-table-column prop="formulaType" label="类型" width="90">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.formulaType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="适用" width="100">
                  <template #default="{ row }">
                    {{ row.skinType || row.hairType || '通用' }}
                  </template>
                </el-table-column>
                <el-table-column prop="durationMin" label="时长" width="70" />
                <el-table-column label="高级" width="60">
                  <template #default="{ row }">
                    <el-tag v-if="row.isPremium" size="small" type="danger">VIP</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="useCount" label="使用次数" width="80" />
                <el-table-column label="操作" width="150">
                  <template #default="{ row }">
                    <el-button size="small" @click="applyFormula(row)">应用</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <h4 style="margin-top:12px">推荐</h4>
              <el-form :model="recommendForm" label-width="80px" size="small" inline>
                <el-form-item label="类型"><el-select v-model="recommendForm.formulaType" style="width:140px">
                  <el-option label="护肤" value="SKINCARE" />
                  <el-option label="染发" value="HAIR_DYE" />
                  <el-option label="美甲" value="NAIL" />
                </el-select></el-form-item>
                <el-form-item label="肤质"><el-select v-model="recommendForm.skinType" style="width:120px">
                  <el-option label="干性" value="DRY" />
                  <el-option label="油性" value="OILY" />
                  <el-option label="中性" value="NORMAL" />
                </el-select></el-form-item>
                <el-form-item label="发质"><el-select v-model="recommendForm.hairType" style="width:120px">
                  <el-option label="受损" value="DAMAGED" />
                  <el-option label="干性" value="DRIED" />
                  <el-option label="直发" value="STRAIGHT" />
                </el-select></el-form-item>
                <el-button @click="recommendFormula" type="success">智能推荐</el-button>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi } from '@/api/sys'

const activeTab = ref('bday')

// 1. 生日
const bdayDash = ref<any>({})
const bdayForm = ref<any>({ memberId: 0, birthday: '1990-01-15', birthdayType: 'SOLAR', autoSend: true, autoCoupon: '', autoCouponAmount: 100 })
const bdaySendForm = ref<any>({ memberId: 0, giftType: 'COUPON', sendType: 'WECHAT', message: '' })
const bdayLogs = ref<any[]>([])
const sending = ref(false)

async function registerBday() {
  if (!bdayForm.value.memberId || !bdayForm.value.birthday) { ElMessage.warning('请填会员ID和生日'); return }
  await sysApi.request({ method: 'POST', url: '/birthday/member/register', data: bdayForm.value })
  ElMessage.success('已录入')
  loadBdayDash()
}
async function sendBdayGift() {
  if (!bdaySendForm.value.memberId) { ElMessage.warning('请填会员ID'); return }
  sending.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/birthday/send', data: bdaySendForm.value })
    ElMessage.success('已发放')
    loadBdayLogs()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { sending.value = false }
}
async function loadBdayDash() {
  try { bdayDash.value = (await sysApi.request({ method: 'GET', url: '/birthday/dashboard' })).data || {} } catch (e) { bdayDash.value = {} }
}
async function loadBdayLogs() {
  try { bdayLogs.value = (await sysApi.request({ method: 'GET', url: '/birthday/log/list' })).data || [] } catch (e) { bdayLogs.value = [] }
}

// 2. NoShow
const noshowDash = ref<any>({})
const noshowModels = ref<any[]>([])
const noshowForm = ref<any>({ bookingId: 1 })
const noshowResult = ref<any>(null)
const highRiskList = ref<any[]>([])
const predicting = ref(false)
const initMl = ref(false)

async function initNoShowModel() {
  initMl.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/noshow/model/init' })
    ElMessage.success('已初始化')
    loadNoshowModels()
    loadNoshowDash()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initMl.value = false }
}
async function activateModel(id: number) {
  await sysApi.request({ method: 'POST', url: `/noshow/model/${id}/activate` })
  ElMessage.success('已激活')
  loadNoshowModels()
}
async function retrain(id: number) {
  await sysApi.request({ method: 'POST', url: `/noshow/model/${id}/retrain` })
  ElMessage.success('已重训')
  loadNoshowModels()
}
async function loadNoshowModels() {
  try { noshowModels.value = (await sysApi.request({ method: 'GET', url: '/noshow/model/list' })).data || [] } catch (e) { noshowModels.value = [] }
}
async function loadNoshowDash() {
  try { noshowDash.value = (await sysApi.request({ method: 'GET', url: '/noshow/dashboard' })).data || {} } catch (e) { noshowDash.value = {} }
}
async function predictNoShow() {
  if (!noshowForm.value.bookingId) { ElMessage.warning('请填预约ID'); return }
  predicting.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/noshow/predict', data: noshowForm.value })
    noshowResult.value = r.data
    loadHighRisk()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { predicting.value = false }
}
async function predictBatchNoShow() {
  await sysApi.request({ method: 'POST', url: '/noshow/predict/batch', data: {} })
  ElMessage.success('已批量预测')
  loadHighRisk()
}
async function loadHighRisk() {
  try { highRiskList.value = (await sysApi.request({ method: 'GET', url: '/noshow/high-risk' })).data || [] } catch (e) { highRiskList.value = [] }
}

// 3. 大众点评
const dpDash = ref<any>({})
const dpShops = ref<any[]>([])
const dpDeals = ref<any[]>([])
const dpOrders = ref<any[]>([])
const dpShopForm = ref<any>({ shopId: '', shopName: '', appKey: '', appSecret: '', city: '' })
const currentShop = ref('')
const verifyCode = ref('')
const syncingDp = ref(false)

async function bindDpShop() {
  if (!dpShopForm.value.shopId) { ElMessage.warning('请填店铺ID'); return }
  await sysApi.request({ method: 'POST', url: '/dianping/shop/bind', data: dpShopForm.value })
  ElMessage.success('已绑定')
  loadDpShops()
  loadDpDash()
}
async function syncDpDeals() {
  if (!currentShop.value) { ElMessage.warning('请选店铺'); return }
  syncingDp.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/dianping/deal/sync', data: { shopId: currentShop.value } })
    ElMessage.success('已同步')
    loadDpDeals()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { syncingDp.value = false }
}
async function syncDpOrders() {
  if (!currentShop.value) { ElMessage.warning('请选店铺'); return }
  syncingDp.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/dianping/order/sync', data: { shopId: currentShop.value } })
    ElMessage.success('已同步')
    loadDpOrders()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { syncingDp.value = false }
}
async function verifyDpCode() {
  if (!verifyCode.value) { ElMessage.warning('请输券码'); return }
  await sysApi.request({ method: 'POST', url: '/dianping/verify', data: { serialNo: verifyCode.value } })
  ElMessage.success('核销成功')
  loadDpOrders()
}
async function loadDpShops() {
  try { dpShops.value = (await sysApi.request({ method: 'GET', url: '/dianping/shop/list' })).data || [] } catch (e) { dpShops.value = [] }
}
async function loadDpDeals() {
  try { dpDeals.value = (await sysApi.request({ method: 'GET', url: '/dianping/deal/list' })).data || [] } catch (e) { dpDeals.value = [] }
}
async function loadDpOrders() {
  try { dpOrders.value = (await sysApi.request({ method: 'GET', url: '/dianping/order/list' })).data || [] } catch (e) { dpOrders.value = [] }
}
async function loadDpDash() {
  try { dpDash.value = (await sysApi.request({ method: 'GET', url: '/dianping/dashboard' })).data || {} } catch (e) { dpDash.value = {} }
}

// 4. 配方
const formulaList = ref<any[]>([])
const formulaForm = ref<any>({
  formulaCode: 'CUSTOM_001', formulaName: '新配方', formulaType: 'SKINCARE',
  skinType: [], durationMin: 30, isPremium: false,
  ingredientsJson: '[]', stepsJson: '[]', caution: ''
})
const formulaTypeFilter = ref('')
const initL = ref(false)
const recommendForm = ref<any>({ formulaType: 'SKINCARE', skinType: 'DRY', hairType: '' })
const recommendedFormula = ref<any[]>([])

const filteredFormula = computed(() => {
  let r = formulaList.value
  if (formulaTypeFilter.value) r = r.filter(f => f.formulaType === formulaTypeFilter.value)
  return r
})

async function initDefaultFormula() {
  initL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/care/formula/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个配方`)
    loadFormulaList()
    loadFormulaDash()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initL.value = false }
}
async function createFormula() {
  await sysApi.request({ method: 'POST', url: '/care/formula/create', data: formulaForm.value })
  ElMessage.success('已创建')
  loadFormulaList()
}
async function applyFormula(row: any) {
  await sysApi.request({ method: 'POST', url: '/care/formula/apply', data: { formulaId: row.id } })
  ElMessage.success('已记录')
}
async function recommendFormula() {
  const r: any = await sysApi.request({ method: 'POST', url: '/care/formula/recommend', data: recommendForm.value })
  recommendedFormula.value = r.data || []
  ElMessage.success(`推荐 ${recommendedFormula.value.length} 个`)
}
async function loadFormulaList() {
  try { formulaList.value = (await sysApi.request({ method: 'GET', url: '/care/formula/list' })).data || [] } catch (e) { formulaList.value = [] }
}
async function loadFormulaDash() {
  try { dpDash.value = (await sysApi.request({ method: 'GET', url: '/care/formula/dashboard' })).data || {} } catch (e) { dpDash.value = {} }
}

function loadAll() {
  loadBdayDash()
  loadBdayLogs()
  loadNoshowModels()
  loadNoshowDash()
  loadHighRisk()
  loadDpShops()
  loadDpDeals()
  loadDpOrders()
  loadDpDash()
  loadFormulaList()
  loadFormulaDash()
}

onMounted(() => loadAll())
</script>

<style scoped>
.page { padding: 16px; max-width: 1500px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.noshow-result { padding: 10px; background: #fef3c7; border-radius: 6px; margin-top: 12px; font-size: 12px }
</style>