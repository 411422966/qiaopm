<template>
  <div class="blockchain-trace">
    <el-card class="header-card">
      <div class="header">
        <div>
          <h2>区块链溯源</h2>
          <div class="subtitle">P2-003 区块链集成 · 5 类存证（PRODUCT / DRUG / DEVICE / ORDER / CONSENT）</div>
        </div>
        <div class="header-actions">
          <el-tag v-if="latestBlock" type="success" effect="dark" size="large">
            最新区块 #{{ latestBlock.latestBlockIndex }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <!-- 概览 -->
    <el-row :gutter="16" class="row">
      <el-col :span="6" v-for="chain in chainStats" :key="chain.chainId">
        <el-card class="chain-card" :class="{ active: selectedChain === chain.chainId }"
          @click="selectedChain = chain.chainId">
          <div class="chain-card-title">
            <el-icon><Link /></el-icon>
            <span>{{ chainLabels[chain.chainId] || chain.chainId }}</span>
          </div>
          <div class="chain-card-stat">
            <div class="num">{{ chain.blockCount }}</div>
            <div class="label">区块数</div>
          </div>
          <div class="chain-card-meta">
            最近 24h: <strong>{{ chain.recent24h }}</strong>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row">
      <!-- 左侧：存证分页 -->
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>存证列表</span>
              <div class="filters">
                <el-select v-model="filterType" placeholder="全部类型" clearable size="small"
                  style="width: 160px" @change="loadRecords">
                  <el-option v-for="t in recordTypes" :key="t" :label="t" :value="t" />
                </el-select>
                <el-button size="small" @click="loadRecords">刷新</el-button>
              </div>
            </div>
          </template>
          <el-table :data="records" v-loading="loading" stripe size="small" max-height="540">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="tagType(row.recordType)">{{ row.recordType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="bizNo" label="业务号" width="180" show-overflow-tooltip />
            <el-table-column prop="blockIndex" label="块号" width="80" />
            <el-table-column prop="blockHash" label="块哈希" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="hash">{{ row.blockHash?.substring(0, 8) }}…</span>
              </template>
            </el-table-column>
            <el-table-column prop="difficulty" label="难度" width="70" />
            <el-table-column prop="nonce" label="Nonce" width="100" />
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column prop="createTime" label="时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link @click="showTrace(row)">溯源</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
            :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
            @current-change="loadRecords" @size-change="loadRecords" style="margin-top: 12px" />
        </el-card>
      </el-col>

      <!-- 右侧：链维护 + 趋势 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>链状态校验</span>
          </template>
          <el-button type="primary" @click="runVerify" :loading="verifying" style="width: 100%">
            立即校验所有链
          </el-button>
          <div class="verify-results" v-if="verifyResults.length">
            <div v-for="r in verifyResults" :key="r.chainId" class="verify-item">
              <el-icon v-if="r.valid" color="#67C23A"><CircleCheckFilled /></el-icon>
              <el-icon v-else color="#F56C6C"><CircleCloseFilled /></el-icon>
              <span class="chain-name">{{ chainLabels[r.chainId] || r.chainId }}</span>
              <span class="chain-status">{{ r.valid ? '完整' : '已被篡改' }}</span>
              <span class="chain-blocks">{{ r.blockCount }} 块</span>
            </div>
          </div>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header><span>存证类型分布</span></template>
          <div v-for="t in recordTypeStats" :key="t.recordType" class="type-row">
            <el-tag size="small" :type="tagType(t.recordType)">{{ t.recordType }}</el-tag>
            <div class="bar-wrap">
              <div class="bar" :style="{ width: barWidth(t.count) + '%' }"></div>
            </div>
            <span class="num">{{ t.count }}</span>
          </div>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header><span>7 天存证趋势</span></template>
          <div class="trend-chart">
            <div v-for="(d, idx) in trend" :key="idx" class="trend-col">
              <div class="trend-bar" :style="{ height: trendBarHeight(d.count) + '%' }">
                <span class="trend-num">{{ d.count }}</span>
              </div>
              <div class="trend-date">{{ d.date.substring(5) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 溯源详情 -->
    <el-dialog v-model="traceDialog" :title="`存证溯源 ${traceRecord?.recordType} #${traceRecord?.bizId}`" width="640px">
      <div v-if="traceData" class="trace-detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="存证ID">{{ traceData.current?.id }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ traceData.current?.recordType }}</el-descriptions-item>
          <el-descriptions-item label="业务ID">{{ traceData.current?.bizId }}</el-descriptions-item>
          <el-descriptions-item label="业务号">{{ traceData.current?.bizNo }}</el-descriptions-item>
          <el-descriptions-item label="块号">#{{ traceData.current?.blockIndex }}</el-descriptions-item>
          <el-descriptions-item label="块哈希">
            <span class="hash">{{ traceData.current?.blockHash }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="上一块哈希">
            <span class="hash">{{ traceData.current?.prevHash }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="Payload">
            <pre class="payload">{{ prettyJson(traceData.current?.payload) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="Nonce">{{ traceData.current?.nonce }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ traceData.current?.difficulty }}</el-descriptions-item>
          <el-descriptions-item label="已验证">
            <el-tag v-if="traceData.verified" type="success" size="small">✓ 链上已验证</el-tag>
            <el-tag v-else type="warning" size="small">未验证</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ traceData.current?.operatorName }}</el-descriptions-item>
          <el-descriptions-item label="存证时间">{{ traceData.current?.createTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Link, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { blockchainApi } from '@/api/blockchain'

const recordTypes = ['PRODUCT', 'DRUG', 'DEVICE', 'ORDER', 'CONSENT']
const chainLabels: Record<string, string> = {
  'meiyetec-main': '主链',
  'meiyetec-cosmetic': '化妆品链',
  'meiyetec-drug': '药品链',
  'meiyetec-device': '器械链'
}

const records = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterType = ref('')
const loading = ref(false)
const selectedChain = ref('meiyetec-main')

const chainStats = ref<any[]>([])
const recordTypeStats = ref<any[]>([])
const trend = ref<any[]>([])
const latestBlock = computed(() => chainStats.value.find((c: any) => c.chainId === selectedChain.value))

const verifying = ref(false)
const verifyResults = ref<any[]>([])

const traceDialog = ref(false)
const traceRecord = ref<any>(null)
const traceData = ref<any>(null)

function tagType(t: string) {
  return ({ PRODUCT: '', DRUG: 'warning', DEVICE: 'info', ORDER: 'success', CONSENT: 'danger' } as any)[t] || ''
}

function formatDate(s: string) {
  if (!s) return '-'
  return s.substring(0, 19).replace('T', ' ')
}

function barWidth(count: number) {
  if (!recordTypeStats.value.length) return 0
  const max = Math.max(...recordTypeStats.value.map((t: any) => t.count))
  return max > 0 ? (count / max) * 100 : 0
}

function trendBarHeight(count: number) {
  if (!trend.value.length) return 0
  const max = Math.max(...trend.value.map((t: any) => t.count), 1)
  return (count / max) * 100
}

function prettyJson(s: string) {
  if (!s) return '-'
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch {
    return s
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const res: any = await blockchainApi.recordPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      recordType: filterType.value || undefined
    })
    records.value = res?.data?.records || res?.records || []
    total.value = res?.data?.total || res?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const [chainRes, typeRes, trendRes] = await Promise.all([
      blockchainApi.chainStats(),
      blockchainApi.recordTypeStats(),
      blockchainApi.dailyTrend(7)
    ])
    chainStats.value = (chainRes as any)?.data || chainRes || []
    recordTypeStats.value = (typeRes as any)?.data || typeRes || []
    trend.value = (trendRes as any)?.data || trendRes || []
  } catch (e) {
    console.error('load stats failed', e)
  }
}

async function runVerify() {
  verifying.value = true
  try {
    const res: any = await blockchainApi.chainOverview()
    const overview = (res as any)?.data || res || []
    // 同时校验每条链
    const checks = await Promise.all(
      overview.map(async (c: any) => {
        const v: any = await blockchainApi.verifyChain(c.chainId)
        return (v as any)?.data || v
      })
    )
    verifyResults.value = checks
    ElMessage.success(`链校验完成：${checks.filter((c: any) => c.valid).length}/${checks.length} 链完整`)
  } finally {
    verifying.value = false
  }
}

async function showTrace(row: any) {
  traceRecord.value = row
  const res: any = await blockchainApi.trace(row.recordType, row.bizId)
  traceData.value = (res as any)?.data || res
  traceDialog.value = true
}

onMounted(() => {
  loadRecords()
  loadStats()
})
</script>

<style scoped>
.blockchain-trace { padding: 0; }
.header-card { margin-bottom: 16px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.header h2 { margin: 0 0 4px; font-size: 20px; }
.subtitle { color: #909399; font-size: 13px; }
.row { margin-bottom: 16px; }
.chain-card { cursor: pointer; transition: all 0.2s; }
.chain-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.chain-card.active { border: 2px solid #409EFF; }
.chain-card-title { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #606266; }
.chain-card-stat { display: flex; align-items: baseline; gap: 8px; margin: 12px 0 4px; }
.chain-card-stat .num { font-size: 28px; font-weight: 600; color: #303133; }
.chain-card-stat .label { color: #909399; font-size: 12px; }
.chain-card-meta { color: #909399; font-size: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filters { display: flex; gap: 8px; }
.hash { font-family: monospace; color: #409EFF; }
.verify-results { margin-top: 12px; }
.verify-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #ebeef5; }
.verify-item:last-child { border: none; }
.chain-name { flex: 1; }
.chain-status { font-size: 12px; padding: 2px 8px; border-radius: 4px; }
.verify-item .chain-status { color: #67C23A; }
.verify-item:not(:has(.el-icon--color-success)) .chain-status { color: #F56C6C; }
.chain-blocks { color: #909399; font-size: 12px; }
.type-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.bar-wrap { flex: 1; height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.bar { height: 100%; background: linear-gradient(90deg, #409EFF, #67C23A); border-radius: 4px; transition: width 0.5s; }
.type-row .num { width: 50px; text-align: right; font-weight: 600; }
.trend-chart { display: flex; align-items: flex-end; height: 180px; gap: 8px; padding: 12px 0; }
.trend-col { flex: 1; display: flex; flex-direction: column; align-items: center; }
.trend-bar { width: 100%; background: linear-gradient(180deg, #409EFF, #66b1ff); border-radius: 4px 4px 0 0; min-height: 4px; display: flex; justify-content: center; align-items: flex-start; padding-top: 4px; transition: height 0.5s; }
.trend-num { color: white; font-size: 11px; }
.trend-date { font-size: 11px; color: #909399; margin-top: 4px; }
.payload { background: #f5f7fa; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 200px; overflow: auto; }
</style>
