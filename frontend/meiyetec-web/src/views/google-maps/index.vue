<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="配置" name="config">
        <el-form :model="configForm" label-width="160px" style="max-width: 600px;">
          <el-form-item label="API Key">
            <el-input v-model="configForm.apiKey" type="password" show-password placeholder="Google Maps API Key" />
          </el-form-item>
          <el-form-item label="默认缩放">
            <el-input-number v-model="configForm.defaultZoom" :min="1" :max="20" />
          </el-form-item>
          <!-- EXT-22: Place Details 计费开关 -->
          <el-form-item label="Place Details（计费）">
            <el-switch
              v-model="enablePlaceDetails"
              :active-value="1"
              :inactive-value="0"
              active-text="启用（按次计费 ≈ $0.025/次）"
              inactive-text="关闭（仅 Geocoding）"
              inline-prompt
              style="--el-switch-on-color: #f56c6c;"
            />
            <el-tooltip content="启用后 Place Details 接口将按 Basic+Contact+Atmosphere 三 SKU 计费" placement="top">
              <el-icon style="margin-left: 8px;"><Warning /></el-icon>
            </el-tooltip>
          </el-form-item>
          <el-alert
            v-if="enablePlaceDetails === 1"
            type="warning"
            :closable="false"
            show-icon
            style="margin-top: -8px; margin-bottom: 16px;"
          >
            ⚠️ 计费警告：Place Details 启用后单次调用可能命中 3 个 SKU（Basic + Contact + Atmosphere）。
            请确认 GCP 后台已开通 Places API (New)、绑定结算账号、设置预算告警。
          </el-alert>
          <el-button type="primary" @click="saveConfig">保存配置</el-button>
        </el-form>
        <el-alert v-if="config.configured" type="success" :closable="false" show-icon style="margin-top: 16px;">
          已配置 API Key（长度 {{ config.apiKeyLength || 0 }}）
        </el-alert>
      </el-tab-pane>
      <el-tab-pane label="门店地图" name="stores">
        <el-row :gutter="16">
          <el-col :span="14">
            <div class="map-placeholder">
              <h2>📍 门店地图</h2>
              <p v-for="s in stores" :key="s.id">
                <strong>{{ s.name }}</strong> — {{ s.address }}<br>
                ({{ s.lat.toFixed(4) }}, {{ s.lng.toFixed(4) }}) · 月预约 {{ s.monthlyBookings }}
              </p>
            </div>
          </el-col>
          <el-col :span="10">
            <h3>客户分布热力图（演示）</h3>
            <p>总点数: {{ heatmap.total || 0 }}</p>
            <p>覆盖范围：5 国 5 店，{{ stores.length }} 个服务点</p>
          </el-col>
        </el-row>
      </el-tab-pane>
      <el-tab-pane label="地理编码测试" name="geocode">
        <el-form :inline="true">
          <el-form-item label="门店ID"><el-input-number v-model="geoForm.storeId" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="geoForm.address" style="width: 300px;" /></el-form-item>
          <el-button type="primary" @click="doGeocode">解析</el-button>
        </el-form>
        <el-card v-if="geocodeResult" style="margin-top: 16px;">
          <p>格式化地址: {{ geocodeResult.formattedAddress }}</p>
          <p>纬度: {{ geocodeResult.lat.toFixed(6) }}</p>
          <p>经度: {{ geocodeResult.lng.toFixed(6) }}</p>
          <p>Place ID: {{ geocodeResult.placeId }}</p>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="Place Details 测试" name="place-details">
        <el-alert
          v-if="enablePlaceDetails !== 1"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
        >
          Place Details 未启用。请先在「配置」Tab 中开启开关后使用。
        </el-alert>
        <el-form :inline="true" :disabled="enablePlaceDetails !== 1">
          <el-form-item label="Place ID">
            <el-input v-model="placeDetailsForm.placeId" style="width: 380px;" placeholder="ChIJN1t_tDeuEmsRUsoyG83frY4" />
          </el-form-item>
          <el-form-item label="语言">
            <el-select v-model="placeDetailsForm.language" style="width: 120px;">
              <el-option label="中文" value="zh-CN" />
              <el-option label="English" value="en-US" />
              <el-option label="日本語" value="ja-JP" />
            </el-select>
          </el-form-item>
          <el-button type="primary" @click="doGetPlaceDetails" :disabled="enablePlaceDetails !== 1">
            查询（计费）
          </el-button>
        </el-form>
        <el-card v-if="placeDetailsResult" style="margin-top: 16px;">
          <template #header>
            <strong>{{ placeDetailsResult.name }}</strong>
            <el-tag size="small" type="warning" style="margin-left: 8px;">已计费</el-tag>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="地址">{{ placeDetailsResult.formattedAddress }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ placeDetailsResult.formattedPhoneNumber || '—' }}</el-descriptions-item>
            <el-descriptions-item label="国际电话">{{ placeDetailsResult.internationalPhoneNumber || '—' }}</el-descriptions-item>
            <el-descriptions-item label="官网">
              <a v-if="placeDetailsResult.website" :href="placeDetailsResult.website" target="_blank">{{ placeDetailsResult.website }}</a>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="评分">
              <el-rate v-model="placeDetailsResult.rating" disabled show-score :max="5" />
            </el-descriptions-item>
            <el-descriptions-item label="评价数">{{ placeDetailsResult.userRatingsTotal || 0 }}</el-descriptions-item>
            <el-descriptions-item label="营业状态">{{ placeDetailsResult.businessStatus || '—' }}</el-descriptions-item>
            <el-descriptions-item label="坐标">({{ placeDetailsResult.lat?.toFixed(6) }}, {{ placeDetailsResult.lng?.toFixed(6) }})</el-descriptions-item>
          </el-descriptions>
          <p style="margin-top: 12px; color: #909399; font-size: 12px;">
            查询耗时: {{ placeDetailsResult.elapsedMs }}ms · 状态: {{ placeDetailsResult.status }}
          </p>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { googleMapsApi } from '@/api/google-maps'
import { Warning } from '@element-plus/icons-vue'

const tab = ref('config')
const config = ref<any>({ configured: false })
const stores = ref<any[]>([])
const heatmap = ref<any>({})
const configForm = ref({ apiKey: '', defaultZoom: 13, enablePlaceDetails: 0 })
const enablePlaceDetails = ref(0)
const geoForm = ref({ storeId: 1, address: '上海外滩' })
const geocodeResult = ref<any>(null)
const placeDetailsForm = ref({ placeId: '', language: 'zh-CN' })
const placeDetailsResult = ref<any>(null)

async function loadConfig() {
  const { data } = await googleMapsApi.getConfig()
  config.value = data || {}
  enablePlaceDetails.value = data?.enablePlaceDetails ?? 0
  configForm.value.enablePlaceDetails = enablePlaceDetails.value
}
async function loadStores() {
  const { data } = await googleMapsApi.listStores(); stores.value = data || []
}
async function loadHeatmap() {
  const { data } = await googleMapsApi.getMemberHeatmap(); heatmap.value = data || {}
}
async function saveConfig() {
  await googleMapsApi.saveConfig(configForm.value)
  ElMessage.success('已保存')
  loadConfig()
}
async function doGeocode() {
  const { data } = await googleMapsApi.geocodeStore(geoForm.value)
  geocodeResult.value = data
}
async function doGetPlaceDetails() {
  if (!placeDetailsForm.value.placeId) {
    ElMessage.warning('请输入 Place ID')
    return
  }
  try {
    const { data } = await googleMapsApi.getPlaceDetails(placeDetailsForm.value)
    placeDetailsResult.value = data
  } catch (e: any) {
    ElMessage.error('查询失败：' + (e?.message || '未知错误'))
  }
}

onMounted(() => { loadConfig(); loadStores(); loadHeatmap() })
</script>

<style scoped>
.map-placeholder {
  background: linear-gradient(135deg, #1a73e8 0%, #34a853 50%, #fbbc04 100%);
  border-radius: 8px;
  padding: 24px;
  color: white;
  min-height: 400px;
}
.map-placeholder h2 { color: white; }
.map-placeholder p { margin: 8px 0; line-height: 1.6; }
</style>
