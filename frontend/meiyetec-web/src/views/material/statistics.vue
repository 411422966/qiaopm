<template>
  <div class="material-statistics-page">
    <!-- 顶部统计 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card class="stat-card stat-1">
          <div class="stat-icon"><el-icon :size="28"><Box /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ totalProducts || 0 }}</div>
            <div class="stat-label">消耗耗材种类</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stat-2">
          <div class="stat-icon"><el-icon :size="28"><Document /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ totalConsumption }}</div>
            <div class="stat-label">总消耗数量</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stat-3">
          <div class="stat-icon"><el-icon :size="28"><Money /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">¥{{ totalCost.toFixed(2) }}</div>
            <div class="stat-label">耗材总成本</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card stat-4">
          <div class="stat-icon"><el-icon :size="28"><TrendCharts /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ avgCostRatio.toFixed(1) }}%</div>
            <div class="stat-label">平均成本占比</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>耗材消耗分析</span>
          <div class="filter-form">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="loadData"
            />
            <el-select
              v-model="storeId"
              placeholder="选择门店"
              clearable
              style="width: 160px"
              @change="loadData"
            >
              <el-option
                v-for="s in storeList"
                :key="s.id"
                :label="s.storeName"
                :value="s.id"
              />
            </el-select>
            <el-button @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="按耗材统计" name="product">
          <el-table :data="productStats" border stripe v-loading="loading">
            <el-table-column label="耗材名称" prop="product_name" min-width="180" fixed />
            <el-table-column label="消耗次数" prop="consume_count" width="120" align="center" />
            <el-table-column label="总消耗数量" prop="total_quantity" width="140" align="right" sortable />
            <el-table-column label="总成本" prop="total_cost" width="140" align="right" sortable>
              <template #default="{ row }">
                ¥{{ formatNumber(row.total_cost) }}
              </template>
            </el-table-column>
            <el-table-column label="占比" width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="getRatio(row.total_cost, totalCost)"
                  :stroke-width="8"
                  :show-text="false"
                />
                <span style="margin-left: 8px">{{ getRatio(row.total_cost, totalCost).toFixed(1) }}%</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!productStats.length" description="暂无统计数据" />
        </el-tab-pane>

        <el-tab-pane label="按服务统计" name="service">
          <el-table :data="serviceStats" border stripe v-loading="loading">
            <el-table-column label="服务名称" prop="service_name" min-width="180" fixed />
            <el-table-column label="订单数" prop="order_count" width="120" align="center" />
            <el-table-column label="耗材总成本" prop="total_cost" width="160" align="right" sortable>
              <template #default="{ row }">
                ¥{{ formatNumber(row.total_cost) }}
              </template>
            </el-table-column>
            <el-table-column label="单均成本" width="120">
              <template #default="{ row }">
                <span v-if="row.order_count > 0">
                  ¥{{ formatNumber(row.total_cost / row.order_count) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!serviceStats.length" description="暂无统计数据" />
        </el-tab-pane>

        <el-tab-pane label="成本占比分析" name="cost">
          <el-table :data="costAnalysis" border stripe v-loading="loading">
            <el-table-column label="服务名称" prop="service_name" min-width="180" fixed />
            <el-table-column label="售价" prop="service_price" width="120" align="right">
              <template #default="{ row }">
                <span v-if="row.service_price">¥{{ formatNumber(row.service_price) }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="耗材成本" prop="material_cost" width="120" align="right">
              <template #default="{ row }">
                ¥{{ formatNumber(row.material_cost) }}
              </template>
            </el-table-column>
            <el-table-column label="成本占比" min-width="200">
              <template #default="{ row }">
                <el-progress
                  :percentage="Number(row.cost_ratio || 0)"
                  :stroke-width="12"
                  :status="getCostStatus(row.cost_ratio)"
                />
              </template>
            </el-table-column>
            <el-table-column label="耗材数" prop="item_count" width="100" align="center" />
            <el-table-column label="评级" width="100">
              <template #default="{ row }">
                <el-rate v-model="costRating[row.service_id]" disabled :max="3" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!costAnalysis.length" description="暂无成本数据" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Box, Document, Money, TrendCharts, Refresh } from '@element-plus/icons-vue'
import { materialApi } from '@/api/material-binding'

const activeTab = ref('product')
const productStats = ref<any[]>([])
const serviceStats = ref<any[]>([])
const costAnalysis = ref<any[]>([])
const costRating = reactive<any>({})
const storeList = ref<any[]>([])
const storeId = ref<number | null>(null)
const dateRange = ref<[string, string] | null>(null)
const loading = ref(false)

const totalProducts = computed(() => productStats.value.length)
const totalConsumption = computed(() => {
  return productStats.value.reduce((sum, item) => sum + (Number(item.total_quantity) || 0), 0).toFixed(0)
})
const totalCost = computed(() => {
  return productStats.value.reduce((sum, item) => sum + (Number(item.total_cost) || 0), 0)
})
const avgCostRatio = computed(() => {
  if (!costAnalysis.value.length) return 0
  const sum = costAnalysis.value.reduce((s, r) => s + Number(r.cost_ratio || 0), 0)
  return sum / costAnalysis.value.length
})

const formatNumber = (val: any) => {
  if (val === null || val === undefined) return '0'
  const n = Number(val)
  return isNaN(n) ? '0' : n.toFixed(2)
}

const getRatio = (val: any, total: number) => {
  if (!total || total === 0) return 0
  return Math.round((Number(val) / total) * 1000) / 10
}

const getCostStatus = (ratio: any) => {
  const r = Number(ratio || 0)
  if (r >= 60) return 'exception'
  if (r >= 40) return 'warning'
  return 'success'
}

const loadData = async () => {
  loading.value = true
  try {
    const startDate = dateRange.value?.[0]
    const endDate = dateRange.value?.[1]

    const productRes: any = await materialApi.statisticsByProduct({
      storeId: storeId.value || undefined,
      startDate,
      endDate
    })
    productStats.value = productRes.data || []

    const serviceRes: any = await materialApi.statisticsByService({
      storeId: storeId.value || undefined,
      startDate,
      endDate
    })
    serviceStats.value = serviceRes.data || []

    const costRes: any = await materialApi.costAnalysis()
    costAnalysis.value = costRes.data || []

    // 计算评级
    costAnalysis.value.forEach((row: any) => {
      const ratio = Number(row.cost_ratio || 0)
      let rating = 3
      if (ratio >= 50) rating = 1
      else if (ratio >= 30) rating = 2
      costRating[row.service_id] = rating
    })
  } catch (e) {
    console.error('加载统计数据失败', e)
  } finally {
    loading.value = false
  }
}

const loadStores = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/store/list')
    storeList.value = res.data || []
  } catch (e) {
    console.error('加载门店失败', e)
  }
}

onMounted(() => {
  loadStores()
  loadData()
})
</script>

<style scoped>
.material-statistics-page {
  padding: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-1 .stat-icon { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-2 .stat-icon { background: linear-gradient(135deg, #43e97b, #38f9d7); }
.stat-3 .stat-icon { background: linear-gradient(135deg, #f5576c, #ff6b6b); }
.stat-4 .stat-icon { background: linear-gradient(135deg, #4facfe, #00f2fe); }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>