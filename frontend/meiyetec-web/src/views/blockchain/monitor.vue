<template>
  <div class="blockchain-monitor">
    <el-row :gutter="16">
      <!-- 4 链总览 -->
      <el-col :span="6" v-for="c in chainStats" :key="c.chainId">
        <el-card class="chain-tile" :class="`chain-${c.chainId.split('-')[1]}`">
          <div class="chain-tile-header">
            <el-icon :size="20"><Lightning /></el-icon>
            <span class="chain-name">{{ chainLabels[c.chainId] || c.chainId }}</span>
          </div>
          <div class="chain-tile-body">
            <div class="big-num">{{ c.blockCount }}</div>
            <div class="big-label">累计区块</div>
          </div>
          <el-divider style="margin: 8px 0" />
          <div class="chain-tile-footer">
            <div>
              <div class="small-num">#{{ c.latestBlockIndex }}</div>
              <div class="small-label">最新块号</div>
            </div>
            <div>
              <div class="small-num">{{ c.recent24h || 0 }}</div>
              <div class="small-label">24h 存证</div>
            </div>
          </div>
          <div class="chain-tile-hash" v-if="c.latestBlockHash">
            <el-tooltip :content="c.latestBlockHash">
              <span class="hash-mini">{{ c.latestBlockHash.substring(0, 12) }}…</span>
            </el-tooltip>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>30 天存证趋势</span>
              <el-button size="small" @click="loadAll">刷新</el-button>
            </div>
          </template>
          <div class="trend-chart-large">
            <div v-for="(d, idx) in trend" :key="idx" class="trend-col-large">
              <div class="trend-bar-large" :style="{ height: trendHeight(d.count) + '%' }">
                <span class="trend-num-large">{{ d.count }}</span>
              </div>
              <div class="trend-date-large">{{ d.date.substring(5) }}</div>
            </div>
          </div>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header><span>存证类型分布</span></template>
          <el-table :data="recordTypeStats" stripe size="default">
            <el-table-column prop="recordType" label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="tagType(row.recordType)" size="small">{{ row.recordType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="存证数" min-width="200">
              <template #default="{ row }">
                <div class="bar-wrap-large">
                  <div class="bar-large" :style="{ width: barWidth(row.count) + '%' }"></div>
                </div>
                <span class="bar-num">{{ row.count }}</span>
              </template>
            </el-table-column>
            <el-table-column label="占比">
              <template #default="{ row }">
                {{ totalRecords > 0 ? ((row.count / totalRecords) * 100).toFixed(1) : 0 }}%
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>存证上链</span>
              <el-tag size="small" type="info">{{ totalRecords }} 总数</el-tag>
            </div>
          </template>
          <el-tabs v-model="activeTab" type="card">
            <el-tab-pane label="商品" name="PRODUCT">
              <RecordForm type="PRODUCT" :fields="productFields" @saved="onSaved" />
            </el-tab-pane>
            <el-tab-pane label="药品" name="DRUG">
              <RecordForm type="DRUG" :fields="drugFields" @saved="onSaved" />
            </el-tab-pane>
            <el-tab-pane label="器械" name="DEVICE">
              <RecordForm type="DEVICE" :fields="deviceFields" @saved="onSaved" />
            </el-tab-pane>
            <el-tab-pane label="订单" name="ORDER">
              <RecordForm type="ORDER" :fields="orderFields" @saved="onSaved" />
            </el-tab-pane>
            <el-tab-pane label="同意书" name="CONSENT">
              <RecordForm type="CONSENT" :fields="consentFields" @saved="onSaved" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { Lightning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { blockchainApi } from '@/api/blockchain'
import RecordForm from './RecordForm.vue'

const chainLabels: Record<string, string> = {
  'meiyetec-main': '主链',
  'meiyetec-cosmetic': '化妆品链',
  'meiyetec-drug': '药品链',
  'meiyetec-device': '器械链'
}

const chainStats = ref<any[]>([])
const recordTypeStats = ref<any[]>([])
const trend = ref<any[]>([])
const totalRecords = ref(0)
const activeTab = ref('PRODUCT')

function tagType(t: string) {
  return ({ PRODUCT: '', DRUG: 'warning', DEVICE: 'info', ORDER: 'success', CONSENT: 'danger' } as any)[t] || ''
}

function trendHeight(c: number) {
  if (!trend.value.length) return 0
  const max = Math.max(...trend.value.map((t: any) => t.count), 1)
  return (c / max) * 100
}

function barWidth(c: number) {
  if (!recordTypeStats.value.length) return 0
  const max = Math.max(...recordTypeStats.value.map((t: any) => t.count), 1)
  return (c / max) * 100
}

async function loadAll() {
  try {
    const [chain, type, t, overview] = await Promise.all([
      blockchainApi.chainStats(),
      blockchainApi.recordTypeStats(),
      blockchainApi.dailyTrend(30),
      blockchainApi.overview()
    ])
    chainStats.value = (chain as any)?.data || chain || []
    recordTypeStats.value = (type as any)?.data || type || []
    trend.value = (t as any)?.data || t || []
    totalRecords.value = ((overview as any)?.data || overview)?.totalRecords || 0
  } catch (e) {
    console.error('loadAll failed', e)
  }
}

function onSaved(rec: any) {
  ElMessage.success(`已上链: 块 #${rec?.blockIndex || '?'} (${rec?.recordType || activeTab.value})`)
  loadAll()
}

const productFields = [
  { key: 'productId', label: '商品ID', type: 'number', required: true },
  { key: 'productNo', label: '商品编号', required: true },
  { key: 'productName', label: '商品名称', required: true },
  { key: 'batchNo', label: '批次号' },
  { key: 'manufacturer', label: '生产厂商' },
  { key: 'price', label: '价格', type: 'number' }
]

const drugFields = [
  { key: 'drugId', label: '药品ID', type: 'number', required: true },
  { key: 'drugNo', label: '药品编号', required: true },
  { key: 'drugName', label: '药品名称', required: true },
  { key: 'batchNo', label: '批号' },
  { key: 'approvalNo', label: '国药准字' },
  { key: 'expiryDate', label: '有效期', placeholder: '2027-12-31' }
]

const deviceFields = [
  { key: 'deviceId', label: '器械ID', type: 'number', required: true },
  { key: 'deviceNo', label: '器械编号', required: true },
  { key: 'deviceName', label: '器械名称', required: true },
  { key: 'registrationNo', label: '注册证号' },
  { key: 'serialNo', label: '序列号' }
]

const orderFields = [
  { key: 'orderId', label: '订单ID', type: 'number', required: true },
  { key: 'orderNo', label: '订单号', required: true },
  { key: 'amount', label: '金额(元)', type: 'number', required: true },
  { key: 'paymentMethod', label: '支付方式', placeholder: 'WECHAT_PAY / ALIPAY / DCEP' }
]

const consentFields = [
  { key: 'consentId', label: '同意书ID', type: 'number', required: true },
  { key: 'consentNo', label: '同意书编号', required: true },
  { key: 'consentType', label: '类型', placeholder: 'MEDICAL / SURGICAL / CONSUMER' },
  { key: 'memberId', label: '客户ID', type: 'number' },
  { key: 'memberName', label: '客户姓名' }
]

onMounted(loadAll)
</script>

<style scoped>
.blockchain-monitor { padding: 0; }
.chain-tile { color: white; border: none; }
.chain-tile.chain-meiyetec-main { background: linear-gradient(135deg, #409EFF, #66b1ff); }
.chain-tile.chain-meiyetec-cosmetic { background: linear-gradient(135deg, #67C23A, #95d475); }
.chain-tile.chain-meiyetec-drug { background: linear-gradient(135deg, #E6A23C, #ebb563); }
.chain-tile.chain-meiyetec-device { background: linear-gradient(135deg, #F56C6C, #f89898); }
.chain-tile-header { display: flex; align-items: center; gap: 6px; font-size: 14px; opacity: 0.9; }
.chain-tile-body { margin: 8px 0; }
.big-num { font-size: 32px; font-weight: 600; }
.big-label { opacity: 0.8; font-size: 12px; }
.chain-tile-footer { display: flex; justify-content: space-between; }
.small-num { font-size: 16px; font-weight: 500; }
.small-label { opacity: 0.8; font-size: 11px; }
.chain-tile-hash { margin-top: 8px; text-align: right; opacity: 0.7; }
.hash-mini { font-family: monospace; font-size: 11px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.trend-chart-large { display: flex; align-items: flex-end; height: 240px; gap: 4px; padding: 12px 0; overflow-x: auto; }
.trend-col-large { flex: 1; min-width: 16px; display: flex; flex-direction: column; align-items: center; }
.trend-bar-large { width: 100%; background: linear-gradient(180deg, #409EFF, #66b1ff); border-radius: 4px 4px 0 0; min-height: 4px; display: flex; justify-content: center; align-items: flex-start; padding-top: 4px; transition: height 0.5s; }
.trend-num-large { color: white; font-size: 10px; }
.trend-date-large { font-size: 10px; color: #909399; margin-top: 4px; }
.bar-wrap-large { display: inline-block; width: 70%; background: #f0f0f0; height: 8px; border-radius: 4px; overflow: hidden; vertical-align: middle; }
.bar-large { height: 100%; background: linear-gradient(90deg, #409EFF, #67C23A); border-radius: 4px; transition: width 0.5s; }
.bar-num { margin-left: 8px; font-weight: 600; }
</style>
