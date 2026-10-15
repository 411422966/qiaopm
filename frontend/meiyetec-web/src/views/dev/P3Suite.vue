<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">P3 生态套件：插件市场 / 财税 / CRM / 电商</span>
          <el-space>
            <el-button @click="initPlugins" type="success" plain :loading="initPlL">初始化插件</el-button>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 插件市场 -->
        <el-tab-pane label="插件市场" name="plugin">
          <el-row :gutter="20">
            <el-col :span="14">
              <h4>市场（{{ market.length }} 个插件）</h4>
              <el-input v-model="pluginSearch" placeholder="搜索插件" style="margin-bottom:12px" clearable />
              <select v-model="pluginCategory" style="margin-bottom:12px;padding:6px">
                <option value="">全部分类</option>
                <option v-for="c in ['BUSINESS','MARKETING','PAYMENT','INTEGRATION','ANALYTICS','INDUSTRY']" :key="c" :value="c">{{ c }}</option>
              </select>
              <el-row :gutter="12">
                <el-col v-for="p in filteredMarket" :key="p.id" :span="12">
                  <el-card class="plugin-card">
                    <div class="plugin-head">
                      <span class="plugin-icon">{{ p.pluginCode?.substring(0, 2) }}</span>
                      <div class="plugin-title">
                        <div class="plugin-name">{{ p.pluginName }}</div>
                        <div class="plugin-vendor">{{ p.vendor }} · v{{ p.version }}</div>
                      </div>
                      <el-tag v-if="p.isOfficial" size="small" type="primary">官方</el-tag>
                      <el-tag v-if="p.isFeatured" size="small" type="danger" style="margin-left:4px">推荐</el-tag>
                    </div>
                    <p class="plugin-desc">{{ p.shortDesc }}</p>
                    <div class="plugin-stats">
                      <span>📦 {{ p.installCount }}</span>
                      <span>⭐ {{ p.rating }}</span>
                      <span>💰 {{ p.price > 0 ? '¥' + p.price + '/' + p.billingType : '免费' }}</span>
                    </div>
                    <el-button type="primary" size="small" @click="installPlugin(p)" :disabled="isInstalled(p.id)">
                      {{ isInstalled(p.id) ? '已安装' : '安装' }}
                    </el-button>
                  </el-card>
                </el-col>
              </el-row>
            </el-col>
            <el-col :span="10">
              <h4>已安装（{{ installedList.length }}）</h4>
              <el-table :data="installedList" size="small" stripe>
                <el-table-column prop="plugin_name" label="名称" />
                <el-table-column prop="category" label="分类" width="100" />
                <el-table-column label="状态" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
                      {{ row.enabled === 1 ? '启用' : '禁用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150">
                  <template #default="{ row }">
                    <el-button size="small" @click="togglePlugin(row)">{{ row.enabled === 1 ? '禁' : '启' }}</el-button>
                    <el-button size="small" type="danger" @click="uninstallPlugin(row.id)">卸</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <h4 style="margin-top:12px">评价</h4>
              <el-form :model="reviewForm" label-width="80px" size="small">
                <el-form-item label="插件">
                  <el-select v-model="reviewForm.pluginId">
                    <el-option v-for="p in market" :key="p.id" :label="p.pluginName" :value="p.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="评分">
                  <el-rate v-model="reviewForm.rating" />
                </el-form-item>
                <el-form-item label="评价"><el-input v-model="reviewForm.content" type="textarea" :rows="2" /></el-form-item>
                <el-button type="primary" @click="submitReview">提交</el-button>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 财税 -->
        <el-tab-pane label="财税直连" name="fiscal">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>申请开票</h4>
              <el-form :model="fiscalForm" label-width="80px" size="small">
                <el-form-item label="订单ID"><el-input-number v-model="fiscalForm.orderId" /></el-form-item>
                <el-form-item label="金额"><el-input-number v-model="fiscalForm.amount" :min="0" /></el-form-item>
                <el-form-item label="税率"><el-input-number v-model="fiscalForm.taxRate" :min="0" :max="0.3" :step="0.01" /></el-form-item>
                <el-form-item label="购方名称"><el-input v-model="fiscalForm.buyerName" /></el-form-item>
                <el-form-item label="税号"><el-input v-model="fiscalForm.buyerTaxNo" /></el-form-item>
                <el-form-item label="邮箱"><el-input v-model="fiscalForm.buyerEmail" /></el-form-item>
                <el-form-item label="手机"><el-input v-model="fiscalForm.buyerPhone" /></el-form-item>
                <el-form-item label="开票渠道">
                  <el-select v-model="fiscalForm.channel">
                    <el-option label="百望云" value="BAIWANG" />
                    <el-option label="航信" value="LIANLU" />
                    <el-option label="连连" value="AIOPEN" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="issueInvoice" :loading="issuing">开票</el-button>
              </el-form>
            </el-col>
            <el-col :span="15">
              <h4>发票列表</h4>
              <el-table :data="invoices" size="small" stripe>
                <el-table-column prop="invoiceNo" label="发票号" width="180" />
                <el-table-column prop="buyerName" label="购方" />
                <el-table-column prop="totalAmount" label="总额" width="100" />
                <el-table-column prop="taxRate" label="税率" width="80" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="fiscalStatusType(row.invoiceStatus)">{{ row.invoiceStatus }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="channel" label="渠道" width="100" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button v-if="row.invoiceStatus === 'SUCCEEDED'" size="small" type="danger" @click="redInvoice(row.id)">冲红</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- CRM / 电商 -->
        <el-tab-pane label="CRM + 电商" name="integration">
          <el-row :gutter="20">
            <el-col :span="12">
              <h4>CRM 集成</h4>
              <el-form :model="crmForm" label-width="80px" size="small">
                <el-form-item label="平台">
                  <el-select v-model="crmForm.platform">
                    <el-option label="HubSpot" value="HUBSPOT" />
                    <el-option label="Salesforce" value="SALESFORCE" />
                    <el-option label="Zoho" value="ZOHO" />
                    <el-option label="Pipedrive" value="PIPEDRIVE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="ClientId"><el-input v-model="crmForm.clientId" /></el-form-item>
                <el-form-item label="ClientSecret"><el-input v-model="crmForm.clientSecret" type="password" /></el-form-item>
                <el-form-item label="PortalId"><el-input v-model="crmForm.portalId" /></el-form-item>
                <el-form-item label="API地址"><el-input v-model="crmForm.apiBaseUrl" /></el-form-item>
                <el-button type="primary" @click="saveCrm">保存</el-button>
                <el-button @click="crmOAuth" type="success">OAuth授权</el-button>
              </el-form>

              <el-divider />
              <h4>同步</h4>
              <el-space>
                <el-button @click="syncCrm('members')" type="primary" plain :loading="syncing">同步会员</el-button>
                <el-button @click="syncCrm('orders')" type="warning" plain :loading="syncing">同步订单</el-button>
              </el-space>
              <div v-if="crmSyncResult" class="sync-result">
                <p>成功: {{ crmSyncResult.success }}</p>
                <p>失败: {{ crmSyncResult.fail }}</p>
                <p>耗时: {{ crmSyncResult.duration }} ms</p>
              </div>

              <h4 style="margin-top:12px">同步日志</h4>
              <el-table :data="crmLogs" size="small" stripe max-height="200">
                <el-table-column prop="platform" label="平台" width="100" />
                <el-table-column prop="syncType" label="类型" width="100" />
                <el-table-column prop="successCount" label="成功" width="60" />
                <el-table-column prop="failCount" label="失败" width="60" />
                <el-table-column prop="status" label="状态" width="100" />
              </el-table>
            </el-col>

            <el-col :span="12">
              <h4>电商集成</h4>
              <el-form :model="ecomForm" label-width="80px" size="small">
                <el-form-item label="平台">
                  <el-select v-model="ecomForm.platform">
                    <el-option label="微店" value="WEIDIAN" />
                    <el-option label="有赞" value="YOUZAN" />
                    <el-option label="Shopify" value="SHOPIFY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="店铺名"><el-input v-model="ecomForm.shopName" /></el-form-item>
                <el-form-item label="AppKey"><el-input v-model="ecomForm.appKey" /></el-form-item>
                <el-form-item label="AppSecret"><el-input v-model="ecomForm.appSecret" type="password" /></el-form-item>
                <el-button type="primary" @click="saveEcom">保存</el-button>
                <el-button @click="ecomAuth" type="success">授权</el-button>
              </el-form>

              <el-divider />
              <h4>同步</h4>
              <el-space>
                <el-button @click="syncEcom('products')" type="primary" plain :loading="ecoming">同步商品</el-button>
                <el-button @click="syncEcom('orders')" type="warning" plain :loading="ecoming">同步订单</el-button>
              </el-space>

              <h4 style="margin-top:12px">电商看板</h4>
              <el-row :gutter="8">
                <el-col :span="12"><el-statistic title="商品数" :value="ecomDash.productCount || 0" /></el-col>
                <el-col :span="12"><el-statistic title="订单数" :value="ecomDash.orderCount || 0" /></el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:8px">
                <el-col :span="12"><el-statistic title="营收" :value="ecomDash.totalRevenue || 0" /></el-col>
              </el-row>

              <h4 style="margin-top:12px">订单</h4>
              <el-table :data="ecomOrders" size="small" stripe max-height="200">
                <el-table-column prop="orderNo" label="订单号" width="160" />
                <el-table-column prop="buyerName" label="买家" width="80" />
                <el-table-column prop="totalAmount" label="金额" width="80" />
                <el-table-column prop="status" label="状态" width="100" />
              </el-table>
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

const activeTab = ref('plugin')

// 插件
const market = ref<any[]>([])
const installedList = ref<any[]>([])
const pluginSearch = ref('')
const pluginCategory = ref('')
const initPlL = ref(false)
const reviewForm = ref<any>({ pluginId: 0, rating: 5, content: '' })

const filteredMarket = computed(() => {
  let r = market.value
  if (pluginCategory.value) r = r.filter(p => p.category === pluginCategory.value)
  if (pluginSearch.value) {
    const s = pluginSearch.value.toLowerCase()
    r = r.filter(p => (p.pluginName || '').toLowerCase().includes(s) || (p.tags || '').toLowerCase().includes(s))
  }
  return r
})

function isInstalled(id: number) {
  return installedList.value.some(i => i.plugin_id === id || i.pluginId === id)
}

async function initPlugins() {
  initPlL.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/p3-suite/plugin/init-default', data: {} })
    ElMessage.success(`已初始化 ${r.data?.created} 个插件`)
    loadMarket()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { initPlL.value = false }
}

async function loadMarket() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p3-suite/plugin/market' })
    market.value = r.data || []
  } catch (e) { market.value = [] }
}

async function loadInstalled() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p3-suite/plugin/installed' })
    installedList.value = r.data || []
  } catch (e) { installedList.value = [] }
}

async function installPlugin(p: any) {
  try {
    await sysApi.request({ method: 'POST', url: '/p3-suite/plugin/install', data: { pluginId: p.id } })
    ElMessage.success('已安装')
    loadInstalled()
    loadMarket()
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

async function uninstallPlugin(id: number) {
  await sysApi.request({ method: 'POST', url: `/p3-suite/plugin/${id}/uninstall` })
  ElMessage.success('已卸载')
  loadInstalled()
  loadMarket()
}

async function togglePlugin(row: any) {
  await sysApi.request({ method: 'POST', url: `/p3-suite/plugin/${row.id}/enable`, data: { enabled: row.enabled !== 1 } })
  loadInstalled()
}

async function submitReview() {
  if (!reviewForm.value.pluginId) { ElMessage.warning('请选择插件'); return }
  await sysApi.request({ method: 'POST', url: '/p3-suite/plugin/review', data: reviewForm.value })
  ElMessage.success('已评价')
  reviewForm.value = { pluginId: 0, rating: 5, content: '' }
}

// 财税
const invoices = ref<any[]>([])
const fiscalForm = ref<any>({
  orderId: 0, amount: 0, taxRate: 0.06, buyerName: '', buyerTaxNo: '',
  buyerEmail: '', buyerPhone: '', channel: 'BAIWANG'
})
const issuing = ref(false)

function fiscalStatusType(s: string): any {
  if (s === 'SUCCEEDED') return 'success'
  if (s === 'PENDING') return 'warning'
  if (s === 'FAILED') return 'danger'
  if (s === 'REFUNDED') return 'info'
  return ''
}

async function issueInvoice() {
  if (!fiscalForm.value.buyerName) { ElMessage.warning('请填购方名称'); return }
  issuing.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/p3-suite/fiscal/invoice', data: fiscalForm.value })
    ElMessage.success('已开票')
    loadInvoices()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { issuing.value = false }
}

async function loadInvoices() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p3-suite/fiscal/invoice/list' })
    invoices.value = r.data || []
  } catch (e) { invoices.value = [] }
}

async function redInvoice(id: number) {
  await sysApi.request({ method: 'POST', url: `/p3-suite/fiscal/invoice/${id}/red` })
  ElMessage.success('已冲红')
  loadInvoices()
}

// CRM
const crmForm = ref<any>({ platform: 'HUBSPOT', clientId: '', clientSecret: '', portalId: '', apiBaseUrl: '' })
const crmLogs = ref<any[]>([])
const crmSyncResult = ref<any>(null)
const syncing = ref(false)

async function saveCrm() {
  await sysApi.request({ method: 'POST', url: '/p3-suite/crm/config', data: crmForm.value })
  ElMessage.success('已保存')
}

async function crmOAuth() {
  await sysApi.request({ method: 'POST', url: '/p3-suite/crm/oauth', data: { platform: crmForm.value.platform, code: 'demo' } })
  ElMessage.success('授权成功')
}

async function syncCrm(type: string) {
  syncing.value = true
  try {
    const r: any = await sysApi.request({
      method: 'POST',
      url: type === 'members' ? '/p3-suite/crm/sync/members' : '/p3-suite/crm/sync/orders',
      data: { platform: crmForm.value.platform }
    })
    crmSyncResult.value = r.data
    ElMessage.success('已同步')
    loadCrmLogs()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { syncing.value = false }
}

async function loadCrmLogs() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p3-suite/crm/sync-log' })
    crmLogs.value = r.data || []
  } catch (e) { crmLogs.value = [] }
}

// 电商
const ecomForm = ref<any>({ platform: 'WEIDIAN', shopName: '', appKey: '', appSecret: '' })
const ecomOrders = ref<any[]>([])
const ecomDash = ref<any>({})
const ecoming = ref(false)

async function saveEcom() {
  await sysApi.request({ method: 'POST', url: '/p3-suite/ecommerce/platform', data: ecomForm.value })
  ElMessage.success('已保存')
}

async function ecomAuth() {
  await sysApi.request({ method: 'POST', url: '/p3-suite/ecommerce/auth', data: { platform: ecomForm.value.platform } })
  ElMessage.success('已授权')
}

async function syncEcom(type: string) {
  ecoming.value = true
  try {
    await sysApi.request({
      method: 'POST',
      url: type === 'products' ? '/p3-suite/ecommerce/sync/products' : '/p3-suite/ecommerce/sync/orders',
      data: { platform: ecomForm.value.platform }
    })
    ElMessage.success('已同步')
    loadEcomDash()
    loadEcomOrders()
  } catch (e: any) { ElMessage.error(e?.message || '失败') } finally { ecoming.value = false }
}

async function loadEcomDash() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p3-suite/ecommerce/dashboard' })
    ecomDash.value = r.data || {}
  } catch (e) { ecomDash.value = {} }
}

async function loadEcomOrders() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/p3-suite/ecommerce/order/list' })
    ecomOrders.value = r.data || []
  } catch (e) { ecomOrders.value = [] }
}

function loadAll() {
  loadMarket()
  loadInstalled()
  loadInvoices()
  loadCrmLogs()
  loadEcomDash()
  loadEcomOrders()
}

onMounted(() => loadAll())
</script>

<style scoped>
.page { padding: 16px; max-width: 1500px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.plugin-card { margin-bottom: 12px }
.plugin-head { display: flex; align-items: center; margin-bottom: 8px }
.plugin-icon { width: 40px; height: 40px; background: linear-gradient(135deg, #667eea, #764ba2); color: white; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: bold; margin-right: 8px }
.plugin-title { flex: 1 }
.plugin-name { font-weight: 600; font-size: 14px }
.plugin-vendor { font-size: 11px; color: #909399 }
.plugin-desc { font-size: 12px; color: #606266; min-height: 32px; margin: 6px 0 }
.plugin-stats { font-size: 11px; color: #909399; margin-bottom: 8px }
.plugin-stats span { margin-right: 10px }
.sync-result { padding: 8px; background: #f0f9ff; border-radius: 4px; margin-top: 8px; font-size: 12px }
</style>