<template>
  <div class="expiry-batch-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="预警看板" name="dashboard" />
      <el-tab-pane label="临期批次" name="batch" />
      <el-tab-pane label="预警处理" name="alert" />
      <el-tab-pane label="处理记录" name="handle" />
      <el-tab-pane label="预警规则" name="rule" />
    </el-tabs>

    <!-- ============ 预警看板 ============ -->
    <div v-if="activeTab === 'dashboard'">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card class="dash-card stat-expired">
            <div class="dash-icon"><el-icon :size="32"><CircleClose /></el-icon></div>
            <div class="dash-info">
              <div class="dash-value">{{ dashboardData.expired_count || 0 }}</div>
              <div class="dash-label">已过期批次</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="dash-card stat-critical">
            <div class="dash-icon"><el-icon :size="32"><WarningFilled /></el-icon></div>
            <div class="dash-info">
              <div class="dash-value">{{ dashboardData.critical_count || 0 }}</div>
              <div class="dash-label">严重预警(7天内)</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="dash-card stat-warning">
            <div class="dash-icon"><el-icon :size="32"><Warning /></el-icon></div>
            <div class="dash-info">
              <div class="dash-value">{{ dashboardData.warning_count || 0 }}</div>
              <div class="dash-label">警告(30天内)</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="dash-card stat-pending">
            <div class="dash-icon"><el-icon :size="32"><Bell /></el-icon></div>
            <div class="dash-info">
              <div class="dash-value">{{ dashboardData.pending_count || 0 }}</div>
              <div class="dash-label">待处理预警</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <el-card>
            <template #header>
              <span><el-icon><Histogram /></el-icon> 效期分布</span>
            </template>
            <div class="dist-bar">
              <div class="dist-item" :style="{ width: getDistPercent('expired') + '%' }" :title="`已过期 ${dashboardData.expired_count}`">
                <span v-if="getDistPercent('expired') > 5">{{ dashboardData.expired_count }}</span>
              </div>
              <div class="dist-item critical" :style="{ width: getDistPercent('critical') + '%' }" :title="`严重 ${dashboardData.critical_count}`">
                <span v-if="getDistPercent('critical') > 5">{{ dashboardData.critical_count }}</span>
              </div>
              <div class="dist-item warning" :style="{ width: getDistPercent('warning') + '%' }" :title="`警告 ${dashboardData.warning_count}`">
                <span v-if="getDistPercent('warning') > 5">{{ dashboardData.warning_count }}</span>
              </div>
              <div class="dist-item normal" :style="{ width: getDistPercent('normal') + '%' }" :title="`正常 ${dashboardData.normal_count}`">
                <span v-if="getDistPercent('normal') > 5">{{ dashboardData.normal_count }}</span>
              </div>
            </div>
            <div class="dist-legend">
              <span><i class="dot" style="background:#F56C6C"></i>已过期</span>
              <span><i class="dot" style="background:#E6A23C"></i>严重预警</span>
              <span><i class="dot" style="background:#F0A020"></i>警告</span>
              <span><i class="dot" style="background:#67C23A"></i>正常</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header>
              <span><el-icon><Money /></el-icon> 累计损失</span>
            </template>
            <div class="loss-display">
              <div class="loss-value">¥{{ totalLoss.toFixed(2) }}</div>
              <div class="loss-label">累计处理损失金额</div>
            </div>
            <el-button type="primary" size="large" @click="handleTriggerScan" :loading="scanning">
              <el-icon><Refresh /></el-icon>
              立即扫描预警
            </el-button>
          </el-card>
        </el-col>
      </el-row>

      <el-card style="margin-top: 16px">
        <template #header>
          <span>处理方式分布</span>
        </template>
        <el-table :data="methodStats" border stripe v-loading="methodLoading">
          <el-table-column label="处理方式" width="120">
            <template #default="{ row }">
              <el-tag :type="getMethodType(row.handle_method)">
                {{ getMethodLabel(row.handle_method) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="次数" prop="count" width="120" align="center" />
          <el-table-column label="总数量" prop="total_quantity" width="140" align="right" />
          <el-table-column label="总损失" prop="total_loss" width="140" align="right">
            <template #default="{ row }">
              ¥{{ formatNumber(row.total_loss) }}
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!methodStats.length" description="暂无处理记录" />
      </el-card>
    </div>

    <!-- ============ 临期批次 ============ -->
    <div v-if="activeTab === 'batch'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>临期批次查询</span>
            <div class="filter-form">
              <el-select v-model="batchStoreId" placeholder="门店" clearable style="width: 140px" @change="loadNearExpiry">
                <el-option
                  v-for="s in storeList"
                  :key="s.id"
                  :label="s.storeName"
                  :value="s.id"
                />
              </el-select>
              <el-select v-model="batchDays" placeholder="天数" style="width: 140px" @change="loadNearExpiry">
                <el-option label="7天内" :value="7" />
                <el-option label="15天内" :value="15" />
                <el-option label="30天内" :value="30" />
                <el-option label="60天内" :value="60" />
                <el-option label="90天内" :value="90" />
              </el-select>
              <el-button @click="loadNearExpiry">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="batchData" border stripe v-loading="batchLoading">
          <el-table-column label="耗材" prop="product_name" min-width="160" fixed />
          <el-table-column label="分类" prop="category" width="120" />
          <el-table-column label="批次号" prop="batch_no" width="140" />
          <el-table-column label="数量" prop="quantity" width="100" align="center" />
          <el-table-column label="生产日期" prop="production_date" width="120" />
          <el-table-column label="过期日期" prop="expiry_date" width="120" />
          <el-table-column label="距过期" width="120">
            <template #default="{ row }">
              <el-tag v-if="getDaysToExpiry(row.expiry_date) < 0" type="danger">
                已过期{{ -getDaysToExpiry(row.expiry_date) }}天
              </el-tag>
              <el-tag v-else-if="getDaysToExpiry(row.expiry_date) <= 7" type="danger">
                {{ getDaysToExpiry(row.expiry_date) }}天
              </el-tag>
              <el-tag v-else-if="getDaysToExpiry(row.expiry_date) <= 30" type="warning">
                {{ getDaysToExpiry(row.expiry_date) }}天
              </el-tag>
              <el-tag v-else type="success">
                {{ getDaysToExpiry(row.expiry_date) }}天
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="单次成本" prop="cost_price" width="120">
            <template #default="{ row }">
              <span v-if="row.cost_price">¥{{ row.cost_price }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleCreateHandle(row)">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!batchData.length" description="暂无临期批次" />
      </el-card>
    </div>

    <!-- ============ 预警处理 ============ -->
    <div v-if="activeTab === 'alert'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>预警处理</span>
            <div class="filter-form">
              <el-select v-model="alertLevelFilter" placeholder="预警级别" clearable style="width: 140px" @change="loadAlerts">
                <el-option
                  v-for="opt in alertLevelOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <el-select v-model="alertStatusFilter" placeholder="状态" clearable style="width: 140px" @change="loadAlerts">
                <el-option
                  v-for="opt in alertStatusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
          </div>
        </template>

        <el-table :data="alertData" border stripe v-loading="alertLoading">
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="getLevelType(row.alertLevel)">{{ getLevelLabel(row.alertLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗材" prop="productName" width="140" fixed />
          <el-table-column label="批次号" prop="batchNo" width="140" />
          <el-table-column label="数量" prop="quantity" width="100" align="center" />
          <el-table-column label="距过期" width="100">
            <template #default="{ row }">
              <span v-if="row.daysToExpiry < 0" style="color:#F56C6C;font-weight:600">
                已过期{{ -row.daysToExpiry }}天
              </span>
              <span v-else>{{ row.daysToExpiry }}天</span>
            </template>
          </el-table-column>
          <el-table-column label="预警内容" prop="alertMessage" min-width="280" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createTime" width="160" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending'" type="primary" link size="small" @click="handleAlert(row)">处理</el-button>
              <el-button v-if="row.status === 'pending'" type="info" link size="small" @click="ignoreAlert(row)">忽略</el-button>
              <el-tag v-else-if="row.status === 'resolved'" type="success" size="small">
                {{ getMethodLabel(row.handleMethod) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!alertData.length" description="暂无预警" />
      </el-card>
    </div>

    <!-- ============ 处理记录 ============ -->
    <div v-if="activeTab === 'handle'">
      <el-card>
        <template #header>
          <span>处理记录</span>
        </template>
        <el-table :data="handleData" border stripe v-loading="handleLoading">
          <el-table-column label="处理方式" width="120">
            <template #default="{ row }">
              <el-tag :type="getMethodType(row.handleMethod)">{{ getMethodLabel(row.handleMethod) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗材" prop="productName" width="140" />
          <el-table-column label="批次号" prop="batchNo" width="140" />
          <el-table-column label="数量" prop="handleQuantity" width="100" align="center" />
          <el-table-column label="单价" prop="unitPrice" width="120">
            <template #default="{ row }">
              <span v-if="row.unitPrice">¥{{ row.unitPrice }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="损失" prop="lossAmount" width="120">
            <template #default="{ row }">
              <span v-if="row.lossAmount" style="color:#F56C6C">¥{{ row.lossAmount }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作人" prop="operatorName" width="120" />
          <el-table-column label="处理时间" prop="handleDate" width="140" />
          <el-table-column label="原因" prop="handleReason" min-width="200" show-overflow-tooltip />
          <el-table-column label="创建时间" prop="createTime" width="160" />
        </el-table>
        <el-empty v-if="!handleData.length" description="暂无处理记录" />
      </el-card>
    </div>

    <!-- ============ 预警规则 ============ -->
    <div v-if="activeTab === 'rule'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>预警规则配置</span>
            <el-button type="primary" @click="handleAddRule">
              <el-icon><Plus /></el-icon>新增规则
            </el-button>
          </div>
        </template>

        <el-table :data="ruleData" border stripe v-loading="ruleLoading">
          <el-table-column label="规则名称" prop="ruleName" min-width="180" fixed />
          <el-table-column label="耗材/分类" width="200">
            <template #default="{ row }">
              <span v-if="row.productName">{{ row.productName }}</span>
              <el-tag v-else-if="row.category">{{ getCategoryLabel(row.category) }}</el-tag>
              <el-tag v-else type="info">全部耗材</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="严重预警(天)" prop="criticalDays" width="120" align="center" />
          <el-table-column label="警告(天)" prop="warningDays" width="120" align="center" />
          <el-table-column label="通知渠道" width="120">
            <template #default="{ row }">
              <el-tag>{{ getChannelLabel(row.notifyChannels) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="自动处理" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.autoHandle === 1" type="success">是</el-tag>
              <el-tag v-else type="info">否</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="toggleRule(row)" />
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!ruleData.length" description="暂无规则" />
      </el-card>
    </div>

    <!-- 处理预警对话框 -->
    <el-dialog v-model="alertHandleVisible" title="处理预警" width="500px">
      <el-form :model="alertHandleForm" label-width="100px">
        <el-form-item label="处理方式">
          <el-select v-model="alertHandleForm.handleMethod" placeholder="选择处理方式" style="width: 100%">
            <el-option
              v-for="opt in handleMethodOptions"
              :key="opt.value"
              :label="`${opt.label} - ${opt.description}`"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="alertHandleForm.handlerName" />
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="alertHandleForm.handleRemark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="alertHandleVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmHandleAlert">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 处理批次对话框(从临期批次直接登记) -->
    <el-dialog v-model="batchHandleVisible" title="登记处理" width="500px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="耗材">{{ currentBatch.product_name }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ currentBatch.batch_no }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ currentBatch.quantity }}</el-descriptions-item>
        <el-descriptions-item label="过期日期">{{ currentBatch.expiry_date }}</el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <el-form :model="batchHandleForm" label-width="100px">
        <el-form-item label="处理方式">
          <el-select v-model="batchHandleForm.handleMethod" placeholder="选择处理方式" style="width: 100%">
            <el-option
              v-for="opt in handleMethodOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="处理数量">
          <el-input-number v-model="batchHandleForm.handleQuantity" :min="1" :max="currentBatch.quantity" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="batchHandleForm.unitPrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="处理原因">
          <el-input v-model="batchHandleForm.handleReason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="batchHandleForm.operatorName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchHandleVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateHandle">确认登记</el-button>
      </template>
    </el-dialog>

    <!-- 规则对话框 -->
    <el-dialog v-model="ruleDialogVisible" :title="ruleDialogTitle" width="600px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" />
        </el-form-item>
        <el-form-item label="适用耗材">
          <el-select
            v-model="ruleForm.productId"
            placeholder="具体耗材（可选）"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="p in stockProductList"
              :key="p.id"
              :label="p.productName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用分类" v-if="!ruleForm.productId">
          <el-select
            v-model="ruleForm.category"
            placeholder="选择分类"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="opt in categoryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="严重预警(天)">
          <el-input-number v-model="ruleForm.criticalDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="普通预警(天)">
          <el-input-number v-model="ruleForm.warningDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-select v-model="ruleForm.notifyChannels" placeholder="选择渠道" style="width: 100%">
            <el-option
              v-for="opt in notifyChannelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="自动处理">
          <el-radio-group v-model="ruleForm.autoHandle">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="ruleForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
  CircleClose, WarningFilled, Warning, Bell, Refresh,
  Plus, Histogram, Money
} from '@element-plus/icons-vue'
import {
  expiryApi,
  alertLevelOptions,
  alertStatusOptions,
  alertTypeOptions,
  handleMethodOptions,
  categoryOptions,
  notifyChannelOptions
} from '@/api/expiry-tracking'

const activeTab = ref('dashboard')

// ============ Dashboard ============
const dashboardData = ref<any>({})
const methodStats = ref<any[]>([])
const methodLoading = ref(false)
const totalLoss = ref(0)
const scanning = ref(false)
const storeList = ref<any[]>([])
const stockProductList = ref<any[]>([])

const loadDashboard = async () => {
  try {
    const res: any = await expiryApi.dashboard()
    dashboardData.value = res.data || {}
  } catch (e) {
    console.error(e)
  }

  try {
    const res: any = await expiryApi.totalLoss()
    totalLoss.value = Number(res.data || 0)
  } catch (e) {
    totalLoss.value = 0
  }

  methodLoading.value = true
  try {
    const res: any = await expiryApi.statisticsByMethod({})
    methodStats.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    methodLoading.value = false
  }
}

const handleTriggerScan = async () => {
  scanning.value = true
  try {
    const res: any = await expiryApi.triggerScan()
    const data = res.data || {}
    ElMessage.success(`扫描完成，共发现 ${data.alertCount} 条预警`)
    loadDashboard()
  } catch (e) {
    ElMessage.error('扫描失败')
  } finally {
    scanning.value = false
  }
}

const getDistPercent = (type: string) => {
  const total = (dashboardData.value.expired_count || 0) +
                (dashboardData.value.critical_count || 0) +
                (dashboardData.value.warning_count || 0) +
                (dashboardData.value.normal_count || 0)
  if (!total) return 0
  const map: any = {
    expired: dashboardData.value.expired_count || 0,
    critical: dashboardData.value.critical_count || 0,
    warning: dashboardData.value.warning_count || 0,
    normal: dashboardData.value.normal_count || 0
  }
  return Math.round((map[type] / total) * 100)
}

// ============ 临期批次 ============
const batchData = ref<any[]>([])
const batchLoading = ref(false)
const batchStoreId = ref<number | null>(null)
const batchDays = ref(30)

const loadNearExpiry = async () => {
  batchLoading.value = true
  try {
    const res: any = await expiryApi.nearExpiry({
      storeId: batchStoreId.value || undefined,
      withinDays: batchDays.value
    })
    batchData.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    batchLoading.value = false
  }
}

const getDaysToExpiry = (expiryDate: string) => {
  if (!expiryDate) return 9999
  const today = new Date()
  const expiry = new Date(expiryDate)
  return Math.floor((expiry.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
}

// ============ 预警处理 ============
const alertData = ref<any[]>([])
const alertLoading = ref(false)
const alertLevelFilter = ref<string>('')
const alertStatusFilter = ref<string>('')
const alertHandleVisible = ref(false)
const alertHandleForm = reactive({
  id: null as number | null,
  handleMethod: 'use_first',
  handlerName: '',
  handleRemark: ''
})

const loadAlerts = async () => {
  alertLoading.value = true
  try {
    const res: any = await expiryApi.alerts({
      alertLevel: alertLevelFilter.value || undefined,
      status: alertStatusFilter.value || undefined
    })
    alertData.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    alertLoading.value = false
  }
}

const handleAlert = (row: any) => {
  alertHandleForm.id = row.id
  alertHandleForm.handleMethod = 'use_first'
  alertHandleForm.handlerName = ''
  alertHandleForm.handleRemark = ''
  alertHandleVisible.value = true
}

const confirmHandleAlert = async () => {
  try {
    await expiryApi.handleAlert(alertHandleForm.id!, {
      handleMethod: alertHandleForm.handleMethod,
      handlerName: alertHandleForm.handlerName,
      handleRemark: alertHandleForm.handleRemark
    })
    ElMessage.success('处理成功')
    alertHandleVisible.value = false
    loadAlerts()
    loadDashboard()
  } catch (e) {
    ElMessage.error('处理失败')
  }
}

const ignoreAlert = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定忽略该预警吗？', '提示', { type: 'warning' })
    await expiryApi.ignoreAlert(row.id)
    ElMessage.success('已忽略')
    loadAlerts()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 处理记录 ============
const handleData = ref<any[]>([])
const handleLoading = ref(false)

const loadHandles = async () => {
  handleLoading.value = true
  try {
    const res: any = await expiryApi.handles({})
    handleData.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    handleLoading.value = false
  }
}

// ============ 批次处理 ============
const batchHandleVisible = ref(false)
const currentBatch = ref<any>({})
const batchHandleForm = reactive({
  handleMethod: 'use_first',
  handleQuantity: 1,
  unitPrice: 0,
  handleReason: '',
  operatorName: ''
})

const handleCreateHandle = (row: any) => {
  currentBatch.value = row
  batchHandleForm.handleMethod = 'use_first'
  batchHandleForm.handleQuantity = row.quantity
  batchHandleForm.unitPrice = row.cost_price || 0
  batchHandleForm.handleReason = ''
  batchHandleForm.operatorName = ''
  batchHandleVisible.value = true
}

const confirmCreateHandle = async () => {
  try {
    await expiryApi.createHandle({
      inventoryId: currentBatch.value.id,
      productId: currentBatch.value.product_id,
      productName: currentBatch.value.product_name,
      batchNo: currentBatch.value.batch_no,
      handleMethod: batchHandleForm.handleMethod,
      handleQuantity: batchHandleForm.handleQuantity,
      unitPrice: batchHandleForm.unitPrice,
      handleReason: batchHandleForm.handleReason,
      operatorName: batchHandleForm.operatorName
    })
    ElMessage.success('登记成功')
    batchHandleVisible.value = false
    loadHandles()
    loadDashboard()
  } catch (e) {
    ElMessage.error('登记失败')
  }
}

// ============ 预警规则 ============
const ruleData = ref<any[]>([])
const ruleLoading = ref(false)
const ruleDialogVisible = ref(false)
const ruleDialogTitle = ref('新增规则')
const ruleForm = reactive<any>({
  id: null,
  ruleName: '',
  productId: null,
  category: '',
  criticalDays: 7,
  warningDays: 30,
  notifyChannels: 'system',
  autoHandle: 0,
  status: 1,
  remark: ''
})

const loadRules = async () => {
  ruleLoading.value = true
  try {
    const res: any = await expiryApi.rules({})
    ruleData.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    ruleLoading.value = false
  }
}

const handleAddRule = () => {
  Object.assign(ruleForm, {
    id: null,
    ruleName: '',
    productId: null,
    category: '',
    criticalDays: 7,
    warningDays: 30,
    notifyChannels: 'system',
    autoHandle: 0,
    status: 1,
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

const handleSaveRule = async () => {
  try {
    if (ruleForm.id) {
      await expiryApi.updateRule(ruleForm)
    } else {
      await expiryApi.createRule(ruleForm)
    }
    ElMessage.success('保存成功')
    ruleDialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteRule = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除规则"${row.ruleName}"吗？`, '提示', { type: 'warning' })
    await expiryApi.deleteRule(row.id)
    ElMessage.success('删除成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleRule = async (row: any) => {
  try {
    await expiryApi.updateRule(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

// ============ 辅助 ============
const getLevelLabel = (l: string) => {
  const opt = alertLevelOptions.find(o => o.value === l)
  return opt ? opt.label : l
}

const getLevelType = (l: string) => {
  const opt = alertLevelOptions.find(o => o.value === l)
  return opt ? opt.type : 'info'
}

const getStatusLabel = (s: string) => {
  const opt = alertStatusOptions.find(o => o.value === s)
  return opt ? opt.label : s
}

const getStatusType = (s: string) => {
  const opt = alertStatusOptions.find(o => o.value === s)
  return opt ? opt.type : 'info'
}

const getMethodLabel = (m: string) => {
  const opt = handleMethodOptions.find(o => o.value === m)
  return opt ? opt.label : m
}

const getMethodType = (m: string) => {
  const opt = handleMethodOptions.find(o => o.value === m)
  return opt ? opt.type : 'info'
}

const getCategoryLabel = (c: string) => {
  const opt = categoryOptions.find(o => o.value === c)
  return opt ? opt.label : c
}

const getChannelLabel = (c: string) => {
  const opt = notifyChannelOptions.find(o => o.value === c)
  return opt ? opt.label : c
}

const formatNumber = (val: any) => {
  if (val == null) return '0'
  const n = Number(val)
  return isNaN(n) ? '0' : n.toFixed(2)
}

const handleTabChange = (tab: string) => {
  if (tab === 'dashboard') loadDashboard()
  else if (tab === 'batch') loadNearExpiry()
  else if (tab === 'alert') loadAlerts()
  else if (tab === 'handle') loadHandles()
  else if (tab === 'rule') loadRules()
}

const loadStores = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/store/list')
    storeList.value = res.data || []
  } catch (e) {}
}

const loadStockProducts = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/stock/product/list')
    stockProductList.value = res.data || []
  } catch (e) {}
}

onMounted(() => {
  loadStores()
  loadStockProducts()
  loadDashboard()
})
</script>

<style scoped>
.expiry-batch-page {
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

.dash-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.dash-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-expired .dash-icon { background: linear-gradient(135deg, #F56C6C, #fab6b6); }
.stat-critical .dash-icon { background: linear-gradient(135deg, #F78918, #FFAA66); }
.stat-warning .dash-icon { background: linear-gradient(135deg, #E6A23C, #f3d19e); }
.stat-pending .dash-icon { background: linear-gradient(135deg, #4facfe, #00f2fe); }

.dash-info { flex: 1; }
.dash-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}
.dash-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.dist-bar {
  display: flex;
  height: 32px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
}

.dist-item {
  background: #F56C6C;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  transition: width 0.5s;
}

.dist-item.critical { background: #E6A23C; }
.dist-item.warning { background: #F0A020; }
.dist-item.normal { background: #67C23A; }

.dist-legend {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #606266;
}

.dist-legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

.loss-display {
  text-align: center;
  padding: 24px 0;
}

.loss-value {
  font-size: 36px;
  font-weight: bold;
  color: #F56C6C;
}

.loss-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
  margin-bottom: 16px;
}
</style>