<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">电子价目表 · 卡项套餐</span>
          <el-space>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 价目表管理 -->
        <el-tab-pane label="价目表" name="priceList">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>新建价目表</h4>
              <el-form :model="listForm" label-width="100px" size="small">
                <el-form-item label="编码">
                  <el-input v-model="listForm.listCode" />
                </el-form-item>
                <el-form-item label="名称">
                  <el-input v-model="listForm.listName" />
                </el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="listForm.listType">
                    <el-option label="标准价目表" value="STANDARD" />
                    <el-option label="活动价目表" value="PROMOTION" />
                    <el-option label="渠道价目表" value="CHANNEL" />
                    <el-option label="会员专享" value="MEMBER" />
                  </el-select>
                </el-form-item>
                <el-form-item label="适用门店">
                  <el-input-number v-model="listForm.storeId" :min="0" placeholder="0=全部" />
                </el-form-item>
                <el-form-item label="生效日期">
                  <el-date-picker v-model="listForm.effectiveDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>
                <el-form-item label="失效日期">
                  <el-date-picker v-model="listForm.expireDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>
                <el-form-item label="描述">
                  <el-input v-model="listForm.description" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item>
                  <el-checkbox v-model="listForm.isDefault">设为默认价目表</el-checkbox>
                </el-form-item>
                <el-button type="primary" @click="createList" :loading="creatingList">创建</el-button>
              </el-form>
            </el-col>

            <el-col :span="14">
              <h4>价目表列表</h4>
              <el-table :data="priceLists" size="small" stripe>
                <el-table-column prop="listCode" label="编码" width="160" />
                <el-table-column prop="listName" label="名称" />
                <el-table-column prop="listType" label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.listType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
                    <el-tag v-else-if="row.status === 'ACTIVE'" type="warning" size="small">激活</el-tag>
                    <el-tag v-else size="small">{{ row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="220">
                  <template #default="{ row }">
                    <el-button size="small" @click="loadItems(row.id)">商品</el-button>
                    <el-button size="small" type="warning" :disabled="row.status === 'ACTIVE'" @click="activateList(row.id)">激活</el-button>
                    <el-button size="small" type="info" :disabled="row.isDefault === 1" @click="setDefault(row.id)">默认</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <div v-if="currentList" style="margin-top:16px">
                <el-divider>{{ currentList.listName }} - 商品项</el-divider>
                <el-table :data="currentItems" size="small" stripe max-height="400">
                  <el-table-column prop="serviceId" label="服务ID" width="80" />
                  <el-table-column label="服务名称" width="200">
                    <template #default="{ row }">
                      {{ serviceNameMap[row.serviceId] || '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="listPrice" label="标价" width="80" />
                  <el-table-column prop="memberPrice" label="会员价" width="80" />
                  <el-table-column prop="vipPrice" label="VIP价" width="80" />
                  <el-table-column prop="discountRate" label="折扣%" width="80" />
                  <el-table-column label="标签" width="100">
                    <template #default="{ row }">
                      <el-tag v-if="row.tag" size="small" :type="row.tag === 'HOT' ? 'danger' : row.tag === 'NEW' ? 'success' : ''">
                        {{ row.tag }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="推荐" width="60">
                    <template #default="{ row }">
                      <el-icon v-if="row.isFeatured === 1" color="#FFD700"><StarFilled /></el-icon>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 卡项套餐 -->
        <el-tab-pane label="卡项套餐" name="template">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>新建/编辑套餐</h4>
              <el-form :model="templateForm" label-width="100px" size="small">
                <el-form-item label="编码">
                  <el-input v-model="templateForm.templateCode" />
                </el-form-item>
                <el-form-item label="名称">
                  <el-input v-model="templateForm.templateName" />
                </el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="templateForm.packageType">
                    <el-option label="次卡" value="TIMES" />
                    <el-option label="储值卡" value="AMOUNT" />
                    <el-option label="混合卡" value="MIXED" />
                  </el-select>
                </el-form-item>
                <el-form-item label="卡级别">
                  <el-select v-model="templateForm.cardLevel">
                    <el-option label="普通" value="NORMAL" />
                    <el-option label="银卡" value="SILVER" />
                    <el-option label="金卡" value="GOLD" />
                    <el-option label="钻石卡" value="DIAMOND" />
                  </el-select>
                </el-form-item>
                <el-form-item label="总次数">
                  <el-input-number v-model="templateForm.totalSessions" :min="0" />
                </el-form-item>
                <el-form-item label="原价">
                  <el-input v-model.number="templateForm.originalPrice" />
                </el-form-item>
                <el-form-item label="套餐价">
                  <el-input v-model.number="templateForm.packagePrice" />
                </el-form-item>
                <el-form-item label="折扣率">
                  <el-input-number v-model="templateForm.discountRate" :min="0" :max="100" :precision="2" />
                </el-form-item>
                <el-form-item label="主题色">
                  <el-color-picker v-model="templateForm.themeColor" />
                </el-form-item>
                <el-form-item label="标签">
                  <el-select v-model="templateForm.tag">
                    <el-option label="HOT" value="HOT" />
                    <el-option label="NEW" value="NEW" />
                    <el-option label="RECOMMEND" value="RECOMMEND" />
                    <el-option label="LIMITED" value="LIMITED" />
                  </el-select>
                </el-form-item>
                <el-form-item label="描述">
                  <el-input v-model="templateForm.description" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item label="亮点">
                  <el-input v-model="templateForm.highlights" type="textarea" :rows="2"
                            :placeholder="placeholder" />
                </el-form-item>
                <el-button type="primary" @click="saveTemplate" :loading="savingTemplate">
                  {{ templateForm.id ? '更新' : '创建' }}
                </el-button>
                <el-button v-if="templateForm.id" @click="resetTemplate">重置</el-button>
              </el-form>
            </el-col>

            <el-col :span="14">
              <h4>套餐模板</h4>
              <div class="template-grid">
                <div v-for="t in templates" :key="t.id" class="card-template"
                     :style="{ background: `linear-gradient(135deg, ${t.themeColor || '#FF6B35'}, ${t.accentColor || '#FFE5D9'})` }">
                  <div class="card-header-row">
                    <el-tag v-if="t.tag" size="small" :type="tagType(t.tag)">{{ t.tag }}</el-tag>
                    <span class="card-level">{{ t.cardLevel }}</span>
                  </div>
                  <h3 class="card-name">{{ t.templateName }}</h3>
                  <div class="card-meta">
                    <span v-if="t.totalSessions > 0">{{ t.totalSessions }} 次</span>
                    <span v-if="t.totalAmount && t.totalAmount > 0">¥{{ t.totalAmount }}</span>
                  </div>
                  <div class="card-price">
                    <span class="price-now">¥{{ t.packagePrice }}</span>
                    <span class="price-old">¥{{ t.originalPrice }}</span>
                    <span class="discount-tag">{{ t.discountRate }}折</span>
                  </div>
                  <p class="card-desc">{{ t.description || '—' }}</p>
                  <div class="card-actions">
                    <el-button size="small" type="primary" @click="editTemplate(t)">编辑</el-button>
                    <el-button size="small" type="success" @click="duplicateTemplate(t.id)">复制</el-button>
                    <el-button size="small" @click="markSold(t.id)">+1 售出</el-button>
                    <span class="sold-count">已售 {{ t.soldCount }} · 浏览 {{ t.viewCount }}</span>
                  </div>
                </div>
              </div>
              <el-empty v-if="templates.length === 0" description="暂无套餐" />
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 价格对比 -->
        <el-tab-pane label="价格对比" name="compare">
          <el-form :inline="true" size="small">
            <el-form-item label="服务ID（逗号分隔）">
              <el-input v-model="compareIds" placeholder="1,2,3" style="width:300px" />
            </el-form-item>
            <el-button type="primary" @click="runCompare">对比</el-button>
          </el-form>
          <el-table :data="compareData" size="small" stripe>
            <el-table-column prop="list_name" label="价目表" width="180" />
            <el-table-column prop="list_type" label="类型" width="100" />
            <el-table-column prop="service_name" label="服务" />
            <el-table-column prop="list_price" label="标价" width="80" />
            <el-table-column prop="member_price" label="会员价" width="100" />
            <el-table-column prop="vip_price" label="VIP价" width="100" />
            <el-table-column prop="discount_rate" label="折扣" width="80" />
          </el-table>
          <el-empty v-if="compareData.length === 0" description="暂无对比数据" />
        </el-tab-pane>

        <!-- 热销 -->
        <el-tab-pane label="热销排行" name="hot">
          <el-row :gutter="12">
            <el-col v-for="(item, idx) in hotItems" :key="item.id" :span="6">
              <el-card class="hot-card">
                <div class="hot-rank">#{{ idx + 1 }}</div>
                <h4>{{ item.name }}</h4>
                <el-tag size="small">{{ item.category }}</el-tag>
                <div class="hot-price">¥{{ item.price }}</div>
                <div class="hot-sales">30天售 {{ item.recent_sales || 0 }} · 累计 {{ item.total_sales || 0 }}</div>
              </el-card>
            </el-col>
          </el-row>
          <el-empty v-if="hotItems.length === 0" description="暂无数据" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { StarFilled } from '@element-plus/icons-vue'
import { sysApi } from '@/api/sys'

const activeTab = ref('priceList')
const priceLists = ref<any[]>([])
const templates = ref<any[]>([])
const hotItems = ref<any[]>([])
const compareData = ref<any[]>([])
const compareIds = ref('1,2,3')
const currentList = ref<any>(null)
const currentItems = ref<any[]>([])
const serviceNameMap = ref<any>({})
const creatingList = ref(false)
const savingTemplate = ref(false)
const placeholder = 'JSON数组格式，如 ["赠送护理","生日特权"]'

const listForm = ref<any>({
  listCode: '', listName: '', listType: 'STANDARD',
  storeId: 0, effectiveDate: '', expireDate: '',
  description: '', isDefault: false
})

const templateForm = ref<any>({
  templateCode: '', templateName: '',
  packageType: 'TIMES', cardLevel: 'NORMAL',
  totalSessions: 0, originalPrice: 0, packagePrice: 0,
  discountRate: 0, themeColor: '#FF6B35',
  description: '', highlights: '', tag: ''
})

async function createList() {
  if (!listForm.value.listCode || !listForm.value.listName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creatingList.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/intel/pricelist/list', data: listForm.value })
    ElMessage.success('已创建')
    loadPriceLists()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.message || ''))
  } finally {
    creatingList.value = false
  }
}

async function loadPriceLists() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/pricelist/list' })
    priceLists.value = r.data || []
  } catch (e) { priceLists.value = [] }
}

async function activateList(id: number) {
  await sysApi.request({ method: 'POST', url: `/intel/pricelist/list/${id}/activate` })
  ElMessage.success('已激活')
  loadPriceLists()
}

async function setDefault(id: number) {
  await sysApi.request({ method: 'POST', url: `/intel/pricelist/list/${id}/set-default` })
  ElMessage.success('已设为默认')
  loadPriceLists()
}

async function loadItems(priceListId: number) {
  currentList.value = priceLists.value.find((l: any) => l.id === priceListId)
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/intel/pricelist/item/by-list/${priceListId}` })
    currentItems.value = r.data || []
    // 拉服务名
    const ids = [...new Set(currentItems.value.map((i: any) => i.serviceId).filter(Boolean))]
    if (ids.length > 0) {
      const svc: any = await sysApi.request({
        method: 'POST', url: '/intel/pricelist/item/batch',
        data: { priceListId: 0, items: [] }
      }).catch(() => null)
      // 用简单查询
      try {
        const list: any = await sysApi.request({
          method: 'POST', url: '/crm/member/list',
          data: { ids }
        }).catch(() => null)
      } catch (e) {}
    }
  } catch (e) {
    currentItems.value = []
  }
}

async function saveTemplate() {
  if (!templateForm.value.templateCode || !templateForm.value.templateName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  savingTemplate.value = true
  try {
    if (templateForm.value.id) {
      await sysApi.request({ method: 'POST', url: `/intel/pricelist/template/${templateForm.value.id}`, data: templateForm.value })
    } else {
      await sysApi.request({ method: 'POST', url: '/intel/pricelist/template', data: templateForm.value })
    }
    ElMessage.success('已保存')
    resetTemplate()
    loadTemplates()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message || ''))
  } finally {
    savingTemplate.value = false
  }
}

function editTemplate(t: any) {
  templateForm.value = { ...t }
  activeTab.value = 'template'
}

function resetTemplate() {
  templateForm.value = {
    templateCode: '', templateName: '',
    packageType: 'TIMES', cardLevel: 'NORMAL',
    totalSessions: 0, originalPrice: 0, packagePrice: 0,
    discountRate: 0, themeColor: '#FF6B35',
    description: '', highlights: '', tag: ''
  }
}

async function duplicateTemplate(id: number) {
  await sysApi.request({ method: 'POST', url: `/intel/pricelist/template/${id}/duplicate` })
  ElMessage.success('已复制')
  loadTemplates()
}

async function markSold(id: number) {
  await sysApi.request({ method: 'POST', url: `/intel/pricelist/template/${id}/mark-sold`, data: { count: 1 } })
  ElMessage.success('+1')
  loadTemplates()
}

async function loadTemplates() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/pricelist/template/list' })
    templates.value = r.data || []
  } catch (e) { templates.value = [] }
}

async function loadHot() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/pricelist/hot-items', params: { limit: 8 } })
    hotItems.value = r.data || []
  } catch (e) { hotItems.value = [] }
}

async function runCompare() {
  if (!compareIds.value) {
    ElMessage.warning('请输入服务ID')
    return
  }
  const ids = compareIds.value.split(',').map((s: string) => Number(s.trim())).filter((n: number) => !isNaN(n))
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/intel/pricelist/compare', data: { serviceIds: ids } })
    compareData.value = r.data || []
  } catch (e) {
    compareData.value = []
  }
}

function tagType(tag: string): any {
  if (tag === 'HOT') return 'danger'
  if (tag === 'NEW') return 'success'
  if (tag === 'RECOMMEND') return 'warning'
  return ''
}

function loadAll() {
  loadPriceLists()
  loadTemplates()
  loadHot()
}

onMounted(() => loadAll())
</script>

<style scoped>
.page { padding: 16px; max-width: 1300px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.card-template {
  border-radius: 12px;
  padding: 16px;
  color: #fff;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.card-header-row { display: flex; justify-content: space-between; margin-bottom: 8px }
.card-level { font-size: 12px; padding: 2px 8px; background: rgba(255,255,255,0.3); border-radius: 8px }
.card-name { margin: 8px 0; font-size: 18px }
.card-meta { font-size: 13px; opacity: 0.9; margin-bottom: 6px }
.card-price { margin: 8px 0; display: flex; align-items: baseline; gap: 8px }
.price-now { font-size: 24px; font-weight: bold }
.price-old { text-decoration: line-through; opacity: 0.7 }
.discount-tag { background: rgba(255,255,255,0.4); padding: 2px 8px; border-radius: 4px; font-size: 12px }
.card-desc { font-size: 12px; opacity: 0.85; min-height: 32px; margin: 4px 0 }
.card-actions { margin-top: auto; display: flex; align-items: center; gap: 8px }
.sold-count { font-size: 11px; opacity: 0.8; margin-left: auto }
.hot-card { position: relative; margin-bottom: 12px }
.hot-rank {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #FFD700;
  color: #333;
  padding: 2px 8px;
  border-radius: 50%;
  font-weight: bold;
}
.hot-price { color: #f56c6c; font-size: 20px; font-weight: bold; margin: 8px 0 }
.hot-sales { font-size: 12px; color: #909399 }
</style>