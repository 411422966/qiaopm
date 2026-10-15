<template>
  <div class="service-material-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="项目耗材配置" name="config" />
      <el-tab-pane label="扣减记录" name="deduct" />
      <el-tab-pane label="库存不足" name="shortage" />
    </el-tabs>

    <!-- ============ 项目耗材配置 ============ -->
    <div v-if="activeTab === 'config'">
      <el-row :gutter="16">
        <!-- 左侧：服务列表 -->
        <el-col :span="8">
          <el-card>
            <template #header>
              <div class="card-header">
                <span><el-icon><Goods /></el-icon> 服务项目</span>
                <el-input v-model="serviceSearchKey" placeholder="搜索服务" size="small" style="width:160px" clearable>
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
              </div>
            </template>
            <div class="service-list">
              <div
                v-for="svc in filteredServices"
                :key="svc.id"
                class="service-item"
                :class="{ active: currentService?.id === svc.id }"
                @click="selectService(svc)"
              >
                <div class="si-icon"><el-icon><Star /></el-icon></div>
                <div class="si-info">
                  <div class="si-name">{{ svc.serviceName }}</div>
                  <div class="si-meta">{{ svc.category || '未分类' }} · ¥{{ svc.price || 0 }}</div>
                </div>
                <el-badge :value="getRuleCount(svc.id)" :max="99" type="primary" />
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 右侧：耗材配置 -->
        <el-col :span="16">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>
                  <el-icon><SetUp /></el-icon>
                  {{ currentService ? `${currentService.serviceName} - 耗材配置` : '请选择服务' }}
                </span>
                <el-button v-if="currentService" type="primary" @click="handleAddRule">
                  <el-icon><Plus /></el-icon>添加耗材
                </el-button>
              </div>
            </template>

            <el-empty v-if="!currentService" description="请先在左侧选择服务" />

            <div v-else>
              <!-- 统计概览 -->
              <el-row :gutter="12" class="stat-row">
                <el-col :span="8">
                  <div class="stat-mini">
                    <div class="stat-num">{{ ruleList.length }}</div>
                    <div class="stat-text">耗材数量</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="stat-mini">
                    <div class="stat-num">{{ requiredCount }}</div>
                    <div class="stat-text">必用耗材</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="stat-mini">
                    <div class="stat-num">{{ fefoCount }}</div>
                    <div class="stat-text">FEFO启用</div>
                  </div>
                </el-col>
              </el-row>

              <el-table :data="ruleList" border stripe v-loading="ruleLoading">
                <el-table-column label="耗材" min-width="160" fixed>
                  <template #default="{ row }">
                    <div>{{ row.productName }}</div>
                    <div class="muted">{{ row.spec }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="标准用量" prop="standardQuantity" width="120" align="right">
                  <template #default="{ row }">
                    {{ row.standardQuantity }}{{ row.unit }}
                  </template>
                </el-table-column>
                <el-table-column label="损耗率" prop="wasteRate" width="100">
                  <template #default="{ row }">
                    {{ ((row.wasteRate || 0) * 100).toFixed(1) }}%
                  </template>
                </el-table-column>
                <el-table-column label="实际用量" width="120" align="right">
                  <template #default="{ row }">
                    <strong>{{ calculateActual(row) }}{{ row.unit }}</strong>
                  </template>
                </el-table-column>
                <el-table-column label="是否必用" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.required === 1" type="danger">必用</el-tag>
                    <el-tag v-else type="info">可选</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="FEFO" width="100">
                  <template #default="{ row }">
                    <el-switch v-model="row.fefoEnabled" :active-value="1" :inactive-value="0" @change="toggleRuleFefo(row)" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
                    <el-button type="danger" link size="small" @click="handleDeleteRule(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!ruleLoading && !ruleList.length" description="该服务尚未配置耗材" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- ============ 扣减记录 ============ -->
    <div v-if="activeTab === 'deduct'">
      <!-- 统计 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon blue"><el-icon :size="24"><Histogram /></el-icon></div>
            <div>
              <div class="stat-value">{{ deductStats.total_count || 0 }}</div>
              <div class="stat-label">总扣减次数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon green"><el-icon :size="24"><Select /></el-icon></div>
            <div>
              <div class="stat-value">{{ deductStats.success_count || 0 }}</div>
              <div class="stat-label">成功</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon orange"><el-icon :size="24"><Switch /></el-icon></div>
            <div>
              <div class="stat-value">{{ deductStats.substitute_count || 0 }}</div>
              <div class="stat-label">替代次数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon purple"><el-icon :size="24"><Coin /></el-icon></div>
            <div>
              <div class="stat-value">¥{{ formatNumber(deductStats.total_cost) }}</div>
              <div class="stat-label">总成本</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card style="margin-top: 16px">
        <template #header>
          <div class="card-header">
            <span>扣减明细</span>
            <div class="filter-form">
              <el-input v-model="orderSearchKey" placeholder="订单号" size="small" style="width:160px" clearable />
              <el-select v-model="statusFilter" placeholder="状态" clearable style="width:140px">
                <el-option
                  v-for="opt in deductStatusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
          </div>
        </template>

        <el-table :data="filteredDeductLogs" border stripe v-loading="deductLoading">
          <el-table-column label="订单号" prop="orderNo" width="160" fixed />
          <el-table-column label="服务" prop="serviceName" width="140" />
          <el-table-column label="耗材" prop="productName" width="140" />
          <el-table-column label="批次" prop="batchNo" width="120" />
          <el-table-column label="计划/实际" width="120" align="right">
            <template #default="{ row }">
              <div>{{ row.plannedQuantity }}</div>
              <div class="muted">实际: {{ row.actualQuantity }}</div>
            </template>
          </el-table-column>
          <el-table-column label="策略" width="140">
            <template #default="{ row }">
              <el-tag>{{ getStrategyLabel(row.deductStrategy) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="是否替代" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.isSubstitute === 1" type="warning">是</el-tag>
              <el-tag v-else type="info">否</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="扣减前/后" width="120" align="right">
            <template #default="{ row }">
              {{ row.beforeStock }} → {{ row.afterStock }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试" prop="retryCount" width="80" align="center" />
          <el-table-column label="技师" prop="employeeName" width="100" />
          <el-table-column label="扣减时间" prop="deductTime" width="160" />
        </el-table>
        <el-empty v-if="!deductLoading && !filteredDeductLogs.length" description="暂无扣减记录" />
      </el-card>
    </div>

    <!-- ============ 库存不足 ============ -->
    <div v-if="activeTab === 'shortage'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span><el-icon><Warning /></el-icon> 库存不足告警</span>
            <el-select v-model="shortageStatusFilter" placeholder="状态" clearable style="width:140px" @change="loadShortages">
              <el-option
                v-for="opt in shortageStatusOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
        </template>

        <el-table :data="shortageData" border stripe v-loading="shortageLoading">
          <el-table-column label="订单号" prop="orderNo" width="160" fixed />
          <el-table-column label="服务" prop="serviceName" width="140" />
          <el-table-column label="耗材" prop="productName" width="140" />
          <el-table-column label="需求" prop="requiredQuantity" width="100" align="right" />
          <el-table-column label="当前库存" prop="currentStock" width="100" align="right" />
          <el-table-column label="缺口" prop="shortageQuantity" width="100" align="right">
            <template #default="{ row }">
              <span style="color:#F56C6C;font-weight:600">
                {{ row.shortageQuantity }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="row.alertLevel === 'critical' ? 'danger' : 'warning'">
                {{ row.alertLevel === 'critical' ? '严重' : '轻微' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="是否已替代" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.substituted === 1" type="success">是</el-tag>
              <el-tag v-else type="danger">否</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'resolved' ? 'success' : 'danger'">
                {{ row.status === 'resolved' ? '已替代' : '待处理' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="告警时间" prop="createTime" width="160" />
        </el-table>
        <el-empty v-if="!shortageLoading && !shortageData.length" description="暂无库存不足告警" />
      </el-card>
    </div>

    <!-- ============ 规则配置对话框 ============ -->
    <el-dialog v-model="ruleDialogVisible" :title="ruleDialogTitle" width="500px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="耗材">
          <el-select
            v-model="ruleForm.productId"
            placeholder="选择耗材"
            filterable
            style="width:100%"
            @change="onProductChange"
          >
            <el-option
              v-for="p in stockProductList"
              :key="p.id"
              :label="`${p.productName} (${p.spec || ''})`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标准用量">
          <el-input-number v-model="ruleForm.standardQuantity" :min="0" :precision="2" />
          <span style="margin-left:8px">{{ ruleForm.unit || '' }}</span>
        </el-form-item>
        <el-form-item label="损耗率">
          <el-input-number v-model="ruleForm.wasteRate" :min="0" :max="1" :step="0.01" :precision="2" />
          <span style="margin-left:8px;color:#909399">建议 0.05 (5%)</span>
        </el-form-item>
        <el-form-item label="是否必用">
          <el-radio-group v-model="ruleForm.required">
            <el-radio :value="1">必用</el-radio>
            <el-radio :value="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="FEFO">
          <el-switch v-model="ruleForm.fefoEnabled" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:#909399">先过期先出</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Goods, Search, Plus, SetUp, Histogram, Select, Switch, Coin, Warning, Star
} from '@element-plus/icons-vue'
import request from '@/utils/http'
import {
  serviceMaterialApi,
  deductStatusOptions,
  deductStrategyOptions,
  shortageStatusOptions
} from '@/api/service-material'

const activeTab = ref('config')

// ============ 服务列表 ============
const serviceList = ref<any[]>([])
const serviceSearchKey = ref('')
const currentService = ref<any>(null)
const stockProductList = ref<any[]>([])

const filteredServices = computed(() => {
  if (!serviceSearchKey.value) return serviceList.value
  const k = serviceSearchKey.value.toLowerCase()
  return serviceList.value.filter(s => s.serviceName?.toLowerCase().includes(k))
})

const loadServices = async () => {
  try {
    const res: any = await request.get('/service/list')
    serviceList.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const loadStockProducts = async () => {
  try {
    const res: any = await request.get('/stock/product/list')
    stockProductList.value = res.data || []
  } catch (e) {}
}

const selectService = (svc: any) => {
  currentService.value = svc
  loadRules(svc.id)
}

// ============ 规则配置 ============
const ruleList = ref<any[]>([])
const ruleLoading = ref(false)
const ruleDialogVisible = ref(false)
const ruleDialogTitle = ref('新增规则')
const ruleForm = reactive<any>({
  id: null,
  serviceId: null,
  productId: null,
  productName: '',
  spec: '',
  standardQuantity: 1,
  unit: '',
  wasteRate: 0.05,
  required: 1,
  fefoEnabled: 1,
  remark: ''
})

const requiredCount = computed(() => ruleList.value.filter(r => r.required === 1).length)
const fefoCount = computed(() => ruleList.value.filter(r => r.fefoEnabled === 1).length)

const getRuleCount = (serviceId: number) => {
  // 通过当前已加载的规则来计数(简化实现)
  return currentService.value?.id === serviceId ? ruleList.value.length : 0
}

const loadRules = async (serviceId: number) => {
  ruleLoading.value = true
  try {
    const res: any = await serviceMaterialApi.rulesByService(serviceId)
    ruleList.value = res.data || []
  } catch (e) {
    ruleList.value = []
  } finally {
    ruleLoading.value = false
  }
}

const calculateActual = (row: any) => {
  const waste = row.wasteRate || 0
  const qty = row.standardQuantity || 0
  return (qty * (1 + waste)).toFixed(2)
}

const handleAddRule = () => {
  Object.assign(ruleForm, {
    id: null,
    serviceId: currentService.value.id,
    productId: null,
    productName: '',
    spec: '',
    standardQuantity: 1,
    unit: '',
    wasteRate: 0.05,
    required: 1,
    fefoEnabled: 1,
    remark: ''
  })
  ruleDialogTitle.value = '新增规则'
  ruleDialogVisible.value = true
}

const handleEditRule = (row: any) => {
  Object.assign(ruleForm, row)
  ruleDialogTitle.value = '编辑规则'
  ruleDialogVisible.value = true
}

const onProductChange = (productId: number) => {
  const p = stockProductList.value.find(item => item.id === productId)
  if (p) {
    ruleForm.productName = p.productName
    ruleForm.spec = p.spec
    ruleForm.unit = p.unit || ''
  }
}

const handleSaveRule = async () => {
  try {
    if (ruleForm.id) {
      await serviceMaterialApi.updateRule(ruleForm)
    } else {
      await serviceMaterialApi.createRule(ruleForm)
    }
    ElMessage.success('保存成功')
    ruleDialogVisible.value = false
    if (currentService.value) loadRules(currentService.value.id)
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteRule = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该规则吗？', '提示', { type: 'warning' })
    await serviceMaterialApi.deleteRule(row.id)
    ElMessage.success('删除成功')
    if (currentService.value) loadRules(currentService.value.id)
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleRuleFefo = async (row: any) => {
  try {
    await serviceMaterialApi.updateRule(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

// ============ 扣减记录 ============
const deductLogList = ref<any[]>([])
const deductLoading = ref(false)
const deductStats = ref<any>({})
const orderSearchKey = ref('')
const statusFilter = ref('')

const filteredDeductLogs = computed(() => {
  let data = deductLogList.value
  if (orderSearchKey.value) {
    data = data.filter(d => d.orderNo?.includes(orderSearchKey.value))
  }
  if (statusFilter.value) {
    data = data.filter(d => d.status === statusFilter.value)
  }
  return data
})

const loadDeductLogs = async () => {
  deductLoading.value = true
  try {
    const res: any = await serviceMaterialApi.deductLogs({})
    deductLogList.value = res.data || []
  } catch (e) {
    deductLogList.value = []
  } finally {
    deductLoading.value = false
  }
}

const loadDeductStats = async () => {
  try {
    const res: any = await serviceMaterialApi.statistics({})
    deductStats.value = res.data || {}
  } catch (e) {
    deductStats.value = {}
  }
}

const getStatusLabel = (s: string) => {
  const opt = deductStatusOptions.find(o => o.value === s)
  return opt ? opt.label : s
}

const getStatusType = (s: string) => {
  const opt = deductStatusOptions.find(o => o.value === s)
  return opt ? opt.type : 'info'
}

const getStrategyLabel = (s: string) => {
  const opt = deductStrategyOptions.find(o => o.value === s)
  return opt ? opt.label : s || '-'
}

const formatNumber = (val: any) => {
  if (val == null) return '0'
  const n = Number(val)
  return isNaN(n) ? '0' : n.toFixed(2)
}

// ============ 库存不足 ============
const shortageData = ref<any[]>([])
const shortageLoading = ref(false)
const shortageStatusFilter = ref('')

const loadShortages = async () => {
  shortageLoading.value = true
  try {
    const res: any = await serviceMaterialApi.shortages({
      status: shortageStatusFilter.value || undefined
    })
    shortageData.value = res.data || []
  } catch (e) {
    shortageData.value = []
  } finally {
    shortageLoading.value = false
  }
}

// ============ Tab 切换 ============
const handleTabChange = (tab: string) => {
  if (tab === 'deduct') {
    loadDeductLogs()
    loadDeductStats()
  } else if (tab === 'shortage') {
    loadShortages()
  }
}

onMounted(() => {
  loadServices()
  loadStockProducts()
})
</script>

<style scoped>
.service-material-page {
  padding: 20px;
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

.service-list {
  max-height: 600px;
  overflow-y: auto;
}

.service-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.service-item:hover {
  background: #f5f7fa;
  border-color: #ebeef5;
}

.service-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.si-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.si-info {
  flex: 1;
  min-width: 0;
}

.si-name {
  font-weight: 600;
  color: #303133;
}

.si-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-mini {
  text-align: center;
  padding: 12px;
  background: #fafbfc;
  border-radius: 6px;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
}

.stat-text {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-icon.blue { background: linear-gradient(135deg, #409eff, #79bbff); }
.stat-icon.green { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-icon.orange { background: linear-gradient(135deg, #e6a23c, #f3d19e); }
.stat-icon.purple { background: linear-gradient(135deg, #9c27b0, #ce93d8); }

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.muted {
  color: #909399;
  font-size: 12px;
}
</style>