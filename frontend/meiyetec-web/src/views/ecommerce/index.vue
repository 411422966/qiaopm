<template>
  <div class="ec-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="平台管理" name="platform">
        <el-button type="primary" @click="addDialog = true">添加平台</el-button>
        <el-table :data="platforms" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="platform" label="平台" width="120"/>
          <el-table-column prop="shopName" label="店铺"/>
          <el-table-column prop="shopId" label="店铺ID" width="150"/>
          <el-table-column prop="lastSyncAt" label="最近同步" width="180"/>
          <el-table-column prop="status" label="状态" width="100"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="商品同步" name="product">
        <el-button type="primary" @click="syncProducts">同步商品</el-button>
        <el-table :data="products" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="platformId" label="平台" width="80"/>
          <el-table-column prop="productId" label="商品ID" width="150"/>
          <el-table-column prop="productName" label="商品名称"/>
          <el-table-column prop="price" label="价格" width="100"/>
          <el-table-column prop="stock" label="库存" width="80"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="订单同步" name="order">
        <el-button type="primary" @click="syncOrders">同步订单</el-button>
        <el-table :data="orders" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="platformId" label="平台" width="80"/>
          <el-table-column prop="orderNo" label="订单号" width="180"/>
          <el-table-column prop="amount" label="金额" width="100"/>
          <el-table-column prop="status" label="状态" width="100"/>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="addDialog" title="添加电商平台" width="700px" :style="{ maxHeight: '620px' }">
      <el-form :model="addForm" label-width="140px" style="max-height: 540px; overflow-y: auto;">
        <el-form-item label="平台">
          <el-select v-model="addForm.platform" @change="onPlatformChange">
            <el-option label="微店" value="WEIDIAN"/>
            <el-option label="有赞" value="YOUZAN"/>
            <el-option label="淘宝" value="TAOBAO"/>
            <el-option label="抖店" value="DOUYIN"/>
            <el-option label="MOCK（内部 dev）" value="MOCK"/>
          </el-select>
        </el-form-item>
        <el-form-item label="店铺名称"><el-input v-model="addForm.shopName" placeholder="如：微店商城" /></el-form-item>
        <el-form-item label="店铺ID"><el-input v-model="addForm.shopId" :placeholder="addForm.platform === 'YOUZAN' ? 'kdtId（与店铺ID一致）' : '如：WD1234567'" /></el-form-item>

        <!-- OAuth 凭据（全平台需 appKey + appSecret） -->
        <el-divider content-position="left">OAuth 凭据</el-divider>
        <el-form-item label="App Key"><el-input v-model="addForm.appKey" placeholder="OAuth2 client_id / 平台 AppKey" /></el-form-item>
        <el-form-item label="App Secret">
          <el-input v-model="addForm.appSecret" type="password" show-password placeholder="OAuth2 client_secret" />
          <div style="color: #909399; font-size: 12px; margin-top: 4px;">⚠️ 保存后不回显（WRITE_ONLY）</div>
        </el-form-item>

        <!-- YOUZAN 专属字段才显示：appId / kdtId / signSecret（消息验签 + webhook 签名用） -->
        <template v-if="addForm.platform === 'YOUZAN'">
          <el-divider content-position="left">有赞专属（消息验签 + Webhook 签名）</el-divider>
          <el-form-item label="App ID"><el-input v-model="addForm.appId" placeholder="有赞开放平台 AppID" /></el-form-item>
          <el-form-item label="Kdt ID"><el-input v-model="addForm.kdtId" placeholder="店铺 kdtId（与店铺ID可一致）" /></el-form-item>
          <el-form-item label="Sign Secret">
            <el-input v-model="addForm.signSecret" type="password" show-password placeholder="有赞消息签名密钥 X-Youzan-Sign" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px;">⚠️ 保存后不回显（WRITE_ONLY），webhook 验签必备</div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" @click="doAdd">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ecommerceApi } from '@/api/ecommerce'

const tab = ref('platform')
const platforms = ref<any[]>([])
const products = ref<any[]>([])
const orders = ref<any[]>([])
const addDialog = ref(false)
// I028：表单新增 5 字段（appKey / appSecret / appId / kdtId / signSecret）
// - 全平台：appKey + appSecret（OAuth2 通用）
// - 仅 YOUZAN：appId + kdtId + signSecret（消息验签 / webhook 签名）
// - MOCK：仅 dev 用，后端 MockEcommerceAdapter fallback
const addForm = ref<any>({ platform: 'WEIDIAN', shopName: '微店商城', shopId: 'WD' + Date.now(),
  appKey: '', appSecret: '', appId: '', kdtId: '', signSecret: '' })

// I028：平台切换清空不适用字段（避免 YOUZAN → WEIDAN 后 signSecret 仍残留脏数据）
function onPlatformChange() {
  addForm.value.appKey = ''
  addForm.value.appSecret = ''
  addForm.value.appId = ''
  addForm.value.kdtId = ''
  addForm.value.signSecret = ''
}

async function loadPlatforms() { const { data } = await ecommerceApi.listPlatforms(); platforms.value = data || [] }
async function loadProducts() { const { data } = await ecommerceApi.listProducts(); products.value = data || [] }
async function loadOrders() { const { data } = await ecommerceApi.listOrders(); orders.value = data || [] }
async function doAdd() {
  await ecommerceApi.createPlatform(addForm.value)
  ElMessage.success('已添加')
  addDialog.value = false
  loadPlatforms()
}
async function syncProducts() {
  if (!platforms.value.length) return ElMessage.warning('请先添加平台')
  await ecommerceApi.syncProducts(platforms.value[0].id)
  ElMessage.success('已触发商品同步')
}
async function syncOrders() {
  if (!platforms.value.length) return ElMessage.warning('请先添加平台')
  await ecommerceApi.syncOrders(platforms.value[0].id)
  ElMessage.success('已触发订单同步')
}

onMounted(() => { loadPlatforms(); loadProducts(); loadOrders() })
</script>
