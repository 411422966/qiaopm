<template>
  <div class="page-ltv">
    <el-tabs v-model="tab">
      <!-- ================== Tab 1: LTV 概览 ================== -->
      <el-tab-pane label="LTV 概览" name="overview">
        <!-- KPI 卡片 -->
        <el-row :gutter="12">
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-label">会员数</div>
              <div class="kpi-value">{{ ov.memberCount ?? '-' }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-label">历史 LTV 总池</div>
              <div class="kpi-value">¥{{ fmtMoney(ov.totalLtv) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card kpi-emph">
              <div class="kpi-label">预测 365 天 LTV 总池</div>
              <div class="kpi-value">¥{{ fmtMoney(ov.totalPredicted365d) }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover" class="kpi-card">
              <div class="kpi-label">30 天平均复购概率</div>
              <div class="kpi-value">{{ ov.avgRepurchaseProb30d ?? '-' }}%</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 图表：趋势线 + 饼图 -->
        <el-row :gutter="12" style="margin-top:12px">
          <el-col :span="16">
            <el-card>
              <template #header>
                <div class="card-head">
                  <span>LTV 总池趋势（近 12 个月）</span>
                  <span class="card-sub">历史 vs 预测 365d</span>
                </div>
              </template>
              <div ref="trendEl" class="chart-box"></div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-head"><span>分桶分布</span></div>
              </template>
              <div ref="bucketEl" class="chart-box"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- TOP10 表格 -->
        <el-card style="margin-top:12px">
          <template #header>
            <div class="card-head">
              <span>LTV TOP 10 会员</span>
              <el-button text type="primary" @click="goOneFromTop" :disabled="!topRows.length">
                选中第一行查看 →
              </el-button>
            </div>
          </template>
          <el-table :data="topRows" stripe size="small" @row-click="row => { tab='one'; memberId=row.memberId; compute() }">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="memberId" label="会员ID" width="90" />
            <el-table-column prop="memberName" label="姓名" />
            <el-table-column prop="phone" label="手机" width="130" />
            <el-table-column label="预测 365d" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.pred365) }}</template>
            </el-table-column>
            <el-table-column label="终身预测" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.predLife) }}</template>
            </el-table-column>
            <el-table-column label="30d 复购" align="right">
              <template #default="{ row }">{{ (Number(row.prob30) * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column prop="snapshotDate" label="快照日期" width="120" />
          </el-table>
        </el-card>

        <!-- 分桶描述（保留原 desc） -->
        <el-card style="margin-top:12px">
          <template #header><span>分桶明细</span></template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="LTV 分桶" :span="3">
              <el-tag v-for="d in ov.ltvDistribution" :key="d.bucket" style="margin-right:8px">
                {{ d.bucket }}: {{ d.cnt }} (¥{{ fmtMoney(d.avg_ltv) }})
              </el-tag>
              <span v-if="!ov.ltvDistribution?.length" class="muted">暂无数据</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- ================== Tab 2: 单会员 LTV ================== -->
      <el-tab-pane label="单会员 LTV" name="one">
        <el-form :inline="true">
          <el-form-item label="会员ID">
            <el-input v-model.number="memberId" style="width:180px" placeholder="如 1001" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="compute" :loading="loading">计算 LTV</el-button>
            <el-button @click="resetOne">清空</el-button>
          </el-form-item>
        </el-form>

        <!-- A. 会员基本信息（+ 平均间隔 + 最近购买） -->
        <el-card v-if="member" class="member-card">
          <template #header>
            <div class="card-head">
              <span>会员基本信息</span>
              <span class="muted">ID: {{ member.id }}</span>
            </div>
          </template>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="姓名">{{ member.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机">{{ member.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ member.gender === 1 ? '男' : member.gender === 2 ? '女' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="等级">{{ member.level_name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入会时间">{{ formatDate(member.join_time) }}</el-descriptions-item>
            <el-descriptions-item label="最近购买">{{ formatDate(member.last_pay_time) }}</el-descriptions-item>
            <el-descriptions-item label="距今">{{ lastPurchaseDaysFromMember }} 天</el-descriptions-item>
            <el-descriptions-item label="平均间隔">{{ avgOrderGapDays }} 天/单</el-descriptions-item>
            <el-descriptions-item label="历史订单">{{ member.total_orders }}</el-descriptions-item>
            <el-descriptions-item label="累计消费">¥{{ fmtMoney(member.total_amount) }}</el-descriptions-item>
            <el-descriptions-item label="客单价">¥{{ fmtMoney(member.total_orders > 0 ? member.total_amount / member.total_orders : 0) }}</el-descriptions-item>
            <el-descriptions-item label="活跃天数">{{ activeDays }} 天</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- D. 风险提示卡 -->
        <el-card v-if="result" class="member-card">
          <template #header>
            <div class="card-head">
              <span>运营风险提示</span>
              <el-tag :type="riskTagType" effect="dark" size="large">{{ riskLevel.label }}</el-tag>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="risk-block">
                <div class="risk-block-title">复购 30d</div>
                <div class="risk-block-value">{{ (Number(result.repurchaseProb30d) * 100).toFixed(1) }}%</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="risk-block">
                <div class="risk-block-title">距上次购买</div>
                <div class="risk-block-value">{{ result.lastPurchaseDays ?? '-' }} 天</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="risk-block">
                <div class="risk-block-title">建议动作</div>
                <div class="risk-block-value risk-action">{{ riskLevel.action }}</div>
              </div>
            </el-col>
          </el-row>
          <el-alert
            v-if="riskLevel.alertType"
            :title="riskLevel.alertTitle"
            :type="riskLevel.alertType"
            :description="riskLevel.alertDesc"
            show-icon
            :closable="false"
            style="margin-top: 12px;"
          />
        </el-card>

        <!-- LTV 结果 -->
        <el-card v-if="result" class="member-card">
          <template #header>
            <div class="card-head">
              <span>LTV 预测</span>
              <span class="muted">模型版本: {{ result.modelVersion }}</span>
            </div>
          </template>
          <el-row :gutter="12">
            <el-col :span="6">
              <el-statistic title="历史 LTV" :value="Number(result.historicalLtv)" :precision="2" prefix="¥" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="30 天预测" :value="Number(result.predictedLtv30d)" :precision="2" prefix="¥" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="90 天预测" :value="Number(result.predictedLtv90d)" :precision="2" prefix="¥" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="365 天预测" :value="Number(result.predictedLtv365d)" :precision="2" prefix="¥" />
            </el-col>
          </el-row>
          <el-divider />
          <el-row :gutter="12">
            <el-col :span="6">
              <el-statistic title="终身 LTV" :value="Number(result.predictedLtvLifetime)" :precision="2" prefix="¥" />
            </el-col>
            <el-col :span="6"><el-statistic title="月频次" :value="Number(result.monthlyFreq)" :precision="2" /></el-col>
            <el-col :span="6"><el-statistic title="客单价" :value="Number(result.avgOrderValue)" :precision="2" prefix="¥" /></el-col>
            <el-col :span="6">
              <el-statistic title="计算耗时" :value="result.durationMs ?? 0" suffix=" ms" />
            </el-col>
          </el-row>
        </el-card>

        <!-- B. 同分桶平均对比 -->
        <el-card v-if="result && bucketAvg365d !== null" class="member-card">
          <template #header>
            <div class="card-head">
              <span>同分桶平均对比</span>
              <span class="muted">基于最新快照 · 365 天 LTV</span>
            </div>
          </template>
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="cmp-block">
                <div class="cmp-label">当前会员</div>
                <div class="cmp-value-self">¥{{ fmtMoney(result.predictedLtv365d) }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="cmp-block">
                <div class="cmp-label">同分桶平均 ({{ memberBucket || '-' }})</div>
                <div class="cmp-value-base">¥{{ fmtMoney(bucketAvg365d) }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="cmp-block">
                <div class="cmp-label">倍数</div>
                <div :class="['cmp-value-ratio', ratioTier]">
                  {{ bucketAvg365d > 0 ? (Number(result.predictedLtv365d) / bucketAvg365d).toFixed(2) : '-' }}x
                </div>
                <el-tag :type="ratioTagType" size="small" style="margin-top:6px">{{ ratioLabel }}</el-tag>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 复购概率：进度条 + 百分位 -->
        <el-card v-if="result" class="member-card">
          <template #header>
            <div class="card-head">
              <span>复购概率</span>
              <span v-if="rank" class="muted">
                排名 {{ rank.rankNo }} / {{ rank.totalMembers }} · 高于 {{ rank.percentile }}% 会员
              </span>
            </div>
          </template>
          <div class="prob-row">
            <span class="prob-label">30 天</span>
            <el-progress :percentage="(result.repurchaseProb30d * 100)" :format="p => p.toFixed(2) + '%'" :stroke-width="18" />
          </div>
          <div class="prob-row">
            <span class="prob-label">90 天</span>
            <el-progress :percentage="(result.repurchaseProb90d * 100)" :format="p => p.toFixed(2) + '%'" :stroke-width="18" status="success" />
          </div>
          <div class="prob-row">
            <span class="prob-label">180 天</span>
            <el-progress :percentage="(result.repurchaseProb180d * 100)" :format="p => p.toFixed(2) + '%'" :stroke-width="18" status="warning" />
          </div>
        </el-card>

        <!-- C. 历史订单明细（默认 5 条，可展开全部） -->
        <el-card v-if="recentOrders.length" class="member-card">
          <template #header>
            <div class="card-head">
              <span>最近订单明细</span>
              <el-button text type="primary" @click="toggleAllOrders">
                {{ allOrdersExpanded ? '收起全部' : `查看全部 (${recentOrders.length}+)` }}
              </el-button>
            </div>
          </template>
          <el-table :data="displayedOrders" stripe size="small">
            <el-table-column prop="id" label="订单ID" width="90" />
            <el-table-column prop="orderNo" label="订单号" />
            <el-table-column label="金额" align="right">
              <template #default="{ row }">¥{{ fmtMoney(row.pay_amount) }}</template>
            </el-table-column>
            <el-table-column label="支付时间" min-width="160">
              <template #default="{ row }">{{ formatDate(row.pay_time) }}</template>
            </el-table-column>
            <el-table-column label="状态">
              <template #default>已完成</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ================== Tab 3: 模型管理 ================== -->
      <el-tab-pane label="模型管理" name="models">
        <!-- 搜索 + 状态筛选 -->
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索编码 / 名称 / 类型"
            clearable
            style="width:240px"
            size="default"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width:160px">
            <el-option label="已上线" :value="1" />
            <el-option label="训练中" :value="2" />
            <el-option label="已训练" :value="3" />
            <el-option label="已下线" :value="4" />
          </el-select>
          <span class="filter-tip">共 {{ filteredModels.length }} 条</span>
        </div>

        <!-- 批量操作工具栏 -->
        <div class="batch-bar" v-if="models.length">
          <el-checkbox v-model="selectAll" @change="toggleSelectAll" :indeterminate="isIndeterminate">全选</el-checkbox>
          <span class="select-tip">已选 {{ selectedModels.length }} 项</span>
          <el-button size="small" type="primary" :disabled="!selectedModels.length" @click="batchDeploy">批量部署</el-button>
          <el-button size="small" type="warning" :disabled="!selectedModels.length" @click="batchOffline">批量下线</el-button>
          <el-button size="small" type="success" plain @click="openNewModelDialog">新建模型</el-button>
        </div>

        <el-table :data="pagedModels" stripe @selection-change="onSelectionChange">
          <el-table-column type="selection" width="44" />
          <el-table-column prop="modelCode" label="编码" width="180" />
          <el-table-column prop="modelName" label="名称" width="160" />
          <el-table-column prop="modelType" label="类型" width="100" />
          <el-table-column label="样本量" width="90">
            <template #default="{ row }">{{ row.trainingSamples || row.sampleSize || 0 }}</template>
          </el-table-column>
          <el-table-column label="训练时间" width="170">
            <template #default="{ row }">{{ row.trainingTime || row.trainedAt || '—' }}</template>
          </el-table-column>
          <el-table-column label="AUC" width="100">
            <template #default="{ row }">{{ row.auc != null ? Number(row.auc).toFixed(4) : '—' }}</template>
          </el-table-column>
          <el-table-column label="Recall" width="100">
            <template #default="{ row }">{{ row.recall != null ? Number(row.recall).toFixed(2) : '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.isActive === 1" type="success" size="small" effect="dark">已上线</el-tag>
              <el-tag v-else-if="row.status === 1" type="primary" size="small" effect="light">训练中</el-tag>
              <el-tag v-else-if="row.status === 2" type="warning" size="small">已训练</el-tag>
              <el-tag v-else-if="row.status === 3" type="info" size="small">已下线</el-tag>
              <el-tag v-else size="small">{{ row.isActive ? '已上线' : '未部署' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="primary" :disabled="row.isActive === 1" @click="deploy(row.id)">
                {{ row.isActive === 1 ? '已上线' : '部署' }}
              </el-button>
              <el-button size="small" type="warning" :disabled="row.isActive !== 1" @click="offline(row.id)">
                下线
              </el-button>
              <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager" v-if="filteredModels.length">
          <el-pagination
            background
            layout="prev, pager, next, jumper"
            :current-page="currentPage"
            :page-size="pageSize"
            :total="filteredModels.length"
            @current-change="currentPage = $event"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 模型详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`模型详情 · ${detailRow?.modelCode || ''}`" size="520px" direction="rtl">
      <div v-if="detailRow" class="model-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模型编码">{{ detailRow.modelCode }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailRow.modelName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailRow.modelType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="版本号">{{ detailRow.version || '—' }}</el-descriptions-item>
          <el-descriptions-item label="样本量">{{ detailRow.trainingSamples || detailRow.sampleSize || 0 }}</el-descriptions-item>
          <el-descriptions-item label="训练时间">{{ detailRow.trainingTime || detailRow.trainedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailRow.isActive === 1 ? 'success' : detailRow.status === 1 ? 'primary' : detailRow.status === 2 ? 'warning' : 'info'" size="small">
              {{ detailRow.isActive === 1 ? '已上线' : detailRow.status === 1 ? '训练中' : detailRow.status === 2 ? '已训练' : detailRow.status === 3 ? '已下线' : '未部署' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模型路径" :span="2">
            <el-tooltip :content="detailRow.modelPath || detailRow.path || '—'" placement="top" :show-after="300">
              <span class="text-ellipsis">{{ detailRow.modelPath || detailRow.path || '—' }}</span>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="特征快照">{{ detailRow.featureSnapshotDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailRow.creator || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">性能指标</el-divider>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="AUC">{{ detailRow.auc != null ? Number(detailRow.auc).toFixed(4) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="Recall">{{ detailRow.recall != null ? Number(detailRow.recall).toFixed(2) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="Precision">{{ detailRow.precision != null ? Number(detailRow.precision).toFixed(4) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="F1">{{ detailRow.f1 != null ? Number(detailRow.f1).toFixed(4) : '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-button v-if="detailRow.isActive !== 1" type="primary" @click="deploy(detailRow.id); detailVisible = false">部署上线</el-button>
          <el-button v-if="detailRow.isActive === 1" type="warning" @click="offline(detailRow.id); detailVisible = false">下线</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 新建模型弹窗 -->
    <el-dialog v-model="newModelVisible" title="新建模型" width="480px">
      <el-form :model="newForm" label-width="90px">
        <el-form-item label="模型名称" required>
          <el-input v-model="newForm.modelName" placeholder="如：LTV BG/NBD v2" />
        </el-form-item>
        <el-form-item label="算法">
          <el-select v-model="newForm.algorithm" style="width:100%">
            <el-option label="BG/NBD" value="BG_NBD" />
            <el-option label="Pareto/NBD" value="PARETO_NBD" />
            <el-option label="Gamma-Gamma" value="GAMMA_GAMMA" />
            <el-option label="MLR（线性回归）" value="MLR" />
            <el-option label="RF（随机森林）" value="RF" />
            <el-option label="XGBoost" value="XGB" />
          </el-select>
        </el-form-item>
        <el-form-item label="预测周期">
          <el-input-number v-model="newForm.horizon" :min="30" :max="730" :step="30" />
          <span style="margin-left:8px;color:#909399;font-size:12px">天</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newModelVisible = false">取消</el-button>
        <el-button type="primary" :loading="newModelLoading" @click="submitNewModel">创建并训练</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch, reactive } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const tab = ref('overview')

// ================== Overview ==================
const ov = ref({})
const topRows = ref([])
const trendEl = ref(null)
const bucketEl = ref(null)
let trendChart = null
let bucketChart = null

function fmtMoney(v) {
  if (v == null) return '0.00'
  const n = Number(v)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadOverview() {
  const { data } = await http.get('/intel/ltv/overview')
  ov.value = data || {}
}
async function loadTop() {
  const { data } = await http.get('/intel/ltv/top', { params: { limit: 10 } })
  topRows.value = data || []
}
async function loadTrend() {
  const { data } = await http.get('/intel/ltv/trend')
  const rows = data || []
  const months = rows.map(r => r.ym)
  const hist = rows.map(r => Number(r.hist) || 0)
  const pred = rows.map(r => Number(r.pred) || 0)
  if (!trendChart) trendChart = echarts.init(trendEl.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['历史 LTV', '预测 365d LTV'], top: 0 },
    grid: { left: 60, right: 20, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: months, axisLabel: { rotate: 30, fontSize: 11 } },
    yAxis: { type: 'value', name: '¥' },
    series: [
      {
        name: '历史 LTV', type: 'line', data: hist, smooth: true,
        lineStyle: { width: 3, color: '#5470c6' },
        areaStyle: { color: 'rgba(84,112,198,0.15)' }
      },
      {
        name: '预测 365d LTV', type: 'line', data: pred, smooth: true,
        lineStyle: { width: 3, color: '#91cc75' },
        areaStyle: { color: 'rgba(145,204,117,0.15)' }
      }
    ]
  })
}
function renderBucket() {
  const dist = ov.value.ltvDistribution || []
  const data = dist.map(d => ({
    name: d.bucket,
    value: Number(d.cnt) || 0
  }))
  if (!data.length) {
    if (!bucketChart) bucketChart = echarts.init(bucketEl.value)
    bucketChart.clear()
    bucketChart.setOption({
      title: { text: '暂无数据', left: 'center', top: 'middle', textStyle: { color: '#999', fontSize: 14 } }
    })
    return
  }
  if (!bucketChart) bucketChart = echarts.init(bucketEl.value)
  bucketChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    color: ['#f56c6c', '#e6a23c', '#67c23a'],
    series: [{
      name: 'LTV 分桶', type: 'pie', radius: ['45%', '70%'], avoidLabelOverlap: false,
      label: { show: true, formatter: '{b}\n{c}人' },
      data
    }]
  })
}

function goOneFromTop() {
  if (!topRows.value.length) return
  tab.value = 'one'
  memberId.value = topRows.value[0].memberId
  compute()
}

// ================== One Member ==================
const memberId = ref()
const loading = ref(false)
const result = ref(null)
const member = ref(null)
const rank = ref(null)
const recentOrders = ref([])
const allOrders = ref([])
const allOrdersExpanded = ref(false)

// ===== A. 会员基本信息扩展计算 =====
function formatDate(v) {
  if (!v) return '-'
  // 后端返回 'yyyy-MM-dd HH:mm:ss' 字符串，直接展示即可
  return v
}
const activeDays = computed(() => {
  if (!member.value || !member.value.first_pay_time || !member.value.last_pay_time) return 0
  const a = new Date(member.value.first_pay_time).getTime()
  const b = new Date(member.value.last_pay_time).getTime()
  if (Number.isNaN(a) || Number.isNaN(b) || b < a) return 0
  return Math.round((b - a) / 86400000)
})
const avgOrderGapDays = computed(() => {
  if (!member.value || !member.value.total_orders || member.value.total_orders < 2) return 0
  return Math.round(activeDays.value / (member.value.total_orders - 1))
})
const lastPurchaseDaysFromMember = computed(() => {
  if (!member.value || !member.value.last_pay_time) return '-'
  const t = new Date(member.value.last_pay_time).getTime()
  if (Number.isNaN(t)) return '-'
  return Math.max(0, Math.round((Date.now() - t) / 86400000))
})

// ===== B. 同分桶平均对比 =====
const memberBucket = computed(() => {
  const p365 = Number(result.value?.predictedLtv365d || 0)
  if (p365 >= 10000) return 'HIGH'
  if (p365 >= 3000) return 'MID'
  return 'LOW'
})
const bucketAvg365d = computed(() => {
  const dist = ov.value.ltvDistribution || []
  const row = dist.find(d => d.bucket === memberBucket.value)
  return row ? Number(row.avg_ltv) : null
})
const ratioTier = computed(() => {
  if (!result.value || bucketAvg365d.value === null || bucketAvg365d.value <= 0) return ''
  const r = Number(result.value.predictedLtv365d) / bucketAvg365d.value
  if (r >= 1.2) return 'ratio-up'
  if (r >= 0.8) return 'ratio-mid'
  return 'ratio-down'
})
const ratioLabel = computed(() => {
  if (!ratioTier.value) return '-'
  if (ratioTier.value === 'ratio-up') return '高于分桶均值'
  if (ratioTier.value === 'ratio-mid') return '与分桶均值持平'
  return '低于分桶均值'
})
const ratioTagType = computed(() => {
  if (ratioTier.value === 'ratio-up') return 'success'
  if (ratioTier.value === 'ratio-mid') return 'info'
  return 'danger'
})

// ===== C. 订单折叠 =====
const displayedOrders = computed(() => {
  return allOrdersExpanded.value ? allOrders.value : recentOrders.value
})

// ===== D. 风险提示 =====
const riskLevel = computed(() => {
  if (!result.value) return { label: '-', tagType: 'info', action: '-', alertType: null, alertTitle: '', alertDesc: '' }
  const prob30 = Number(result.value.repurchaseProb30d || 0)
  const lastDays = Number(result.value.lastPurchaseDays ?? 9999)
  if (prob30 < 0.3 || lastDays > 90) {
    return {
      label: '高风险 · 流失预警',
      tagType: 'danger',
      action: '建议 P0 召回：发大额券 + 客服外呼',
      alertType: 'error',
      alertTitle: '该会员已呈现流失特征',
      alertDesc: `30 天复购概率仅 ${(prob30 * 100).toFixed(1)}%，距上次购买 ${lastDays} 天，建议立即发起召回任务。`
    }
  }
  if (prob30 < 0.6 || lastDays > 45) {
    return {
      label: '中风险 · 需关怀',
      tagType: 'warning',
      action: '建议 P1 触达：发关怀券 + 短信问候',
      alertType: 'warning',
      alertTitle: '复购趋势转弱',
      alertDesc: `30 天复购概率 ${(prob30 * 100).toFixed(1)}%，建议在本周内发起一次关怀营销。`
    }
  }
  return {
    label: '健康 · 保持观察',
    tagType: 'success',
    action: '无需干预：保持正常运营节奏',
    alertType: 'success',
    alertTitle: '会员活跃度良好',
    alertDesc: `30 天复购概率 ${(prob30 * 100).toFixed(1)}%，距上次购买 ${lastDays} 天，运营节奏保持即可。`
  }
})
const riskTagType = computed(() => riskLevel.value.tagType)

async function loadMember(id) {
  try {
    const { data } = await http.get(`/intel/ltv/member/${id}`)
    member.value = data
  } catch {
    member.value = null
  }
}
async function loadRank(id) {
  try {
    const { data } = await http.get(`/intel/ltv/rank/${id}`)
    rank.value = data
  } catch {
    rank.value = null
  }
}
async function loadRecentOrders(id) {
  try {
    const { data } = await http.get(`/intel/ltv/recent-orders/${id}`, { params: { limit: 5 } })
    recentOrders.value = data || []
  } catch {
    recentOrders.value = []
  }
}
async function loadAllOrders(id) {
  try {
    const { data } = await http.get(`/intel/ltv/recent-orders/${id}`, { params: { limit: 200 } })
    allOrders.value = data || []
  } catch {
    allOrders.value = []
  }
}
async function toggleAllOrders() {
  if (allOrdersExpanded.value) {
    allOrdersExpanded.value = false
    return
  }
  if (!allOrders.value.length && memberId.value) {
    await loadAllOrders(memberId.value)
  }
  allOrdersExpanded.value = true
}

async function compute() {
  if (!memberId.value) return ElMessage.warning('请输入会员ID')
  loading.value = true
  try {
    const { data } = await http.get(`/intel/ltv/compute/${memberId.value}`)
    result.value = data
    await Promise.all([
      loadMember(memberId.value),
      loadRank(memberId.value),
      loadRecentOrders(memberId.value)
    ])
  } catch (e) {
    result.value = null
  } finally { loading.value = false }
}

function resetOne() {
  memberId.value = undefined
  result.value = null
  member.value = null
  rank.value = null
  recentOrders.value = []
  allOrders.value = []
  allOrdersExpanded.value = false
}

// ================== Models ==================
const models = ref([])
const selectedModels = ref([])
const selectAll = ref(false)
const isIndeterminate = computed(() => selectedModels.value.length > 0 && selectedModels.value.length < models.value.length)

// 搜索 + 筛选
const searchKeyword = ref('')
const statusFilter = ref(null)
const currentPage = ref(1)
const pageSize = 10

function matchStatus(row, f) {
  if (!f) return true
  if (f === 1) return row.isActive === 1
  if (f === 2) return row.status === 1
  if (f === 3) return row.status === 2
  if (f === 4) return row.status === 3
  return true
}
function matchKeyword(row, kw) {
  if (!kw) return true
  const k = kw.toLowerCase()
  return (row.modelCode || '').toLowerCase().includes(k)
    || (row.modelName || '').toLowerCase().includes(k)
    || (row.modelType || '').toLowerCase().includes(k)
}
const filteredModels = computed(() => models.value.filter(r => matchStatus(r, statusFilter.value) && matchKeyword(r, searchKeyword.value.trim())))
const pagedModels = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredModels.value.slice(start, start + pageSize)
})
watch([searchKeyword, statusFilter], () => { currentPage.value = 1 })

async function loadModels() {
  const { data } = await http.get('/intel/ltv/model/list')
  models.value = data || []
}
async function deploy(id) {
  await http.post(`/intel/ltv/model/${id}/deploy`)
  ElMessage.success('已上线')
  loadModels()
}
async function offline(id) {
  try {
    await http.post(`/intel/ltv/model/${id}/offline`)
    ElMessage.success('已下线')
    loadModels()
  } catch (e) {
    ElMessage.error('下线失败: ' + (e?.message || e))
  }
}

function onSelectionChange(rows) {
  selectedModels.value = rows
  selectAll.value = rows.length === models.value.length && models.value.length > 0
}
function toggleSelectAll(val) {
  selectedModels.value = val ? [...models.value] : []
}

async function batchDeploy() {
  if (!selectedModels.value.length) return ElMessage.warning('请先勾选模型')
  const ids = selectedModels.value.map(m => m.id)
  await ElMessageBox.confirm(`确定批量部署 ${ids.length} 个模型？`, '批量部署', { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' }).catch(() => {})
  try {
    await Promise.allSettled(ids.map(id => http.post(`/intel/ltv/model/${id}/deploy`)))
    ElMessage.success('批量部署完成')
    selectedModels.value = []
    selectAll.value = false
    loadModels()
  } catch (e) {
    ElMessage.error('批量部署失败')
  }
}

async function batchOffline() {
  if (!selectedModels.value.length) return ElMessage.warning('请先勾选模型')
  const ids = selectedModels.value.map(m => m.id)
  try {
    await ElMessageBox.confirm(`确定批量下线 ${ids.length} 个模型？`, '批量下线确认', { type: 'warning', confirmButtonText: '确认下线', cancelButtonText: '取消' })
  } catch { return }
  try {
    await Promise.allSettled(ids.map(id => http.post(`/intel/ltv/model/${id}/offline`)))
    ElMessage.success('批量下线完成')
    selectedModels.value = []
    selectAll.value = false
    loadModels()
  } catch (e) {
    ElMessage.error('批量下线失败')
  }
}

// 模型详情抽屉
const detailVisible = ref(false)
const detailRow = ref(null)
function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

// 新建模型
const newModelVisible = ref(false)
const newModelLoading = ref(false)
const newForm = reactive({ modelName: '', algorithm: 'BG_NBD', horizon: 365 })

function openNewModelDialog() {
  Object.assign(newForm, { modelName: '', algorithm: 'BG_NBD', horizon: 365 })
  newModelVisible.value = true
}

async function submitNewModel() {
  if (!newForm.modelName.trim()) return ElMessage.warning('请填写模型名称')
  newModelLoading.value = true
  try {
    await http.post('/intel/ltv/model/create', newForm)
    ElMessage.success('模型创建并训练中，请稍后查看进度')
    newModelVisible.value = false
    loadModels()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e?.message || e))
  } finally {
    newModelLoading.value = false
  }
}

// ================== Lifecycle ==================
function resize() {
  trendChart?.resize()
  bucketChart?.resize()
}

async function refreshOverviewCharts() {
  await nextTick()
  loadTrend()
  renderBucket()
}

onMounted(async () => {
  await loadOverview()
  await loadTop()
  await loadModels()
  refreshOverviewCharts()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  trendChart?.dispose(); trendChart = null
  bucketChart?.dispose(); bucketChart = null
})
watch(tab, (v) => {
  if (v === 'overview') refreshOverviewCharts()
})
</script>

<style scoped>
.page-ltv { padding: 16px; }
.kpi-card { text-align: center; }
.kpi-emph :deep(.el-card__body) { background: linear-gradient(135deg,#e8f5ff,#fff); }
.kpi-label { font-size: 12px; color: #909399; }
.kpi-value { font-size: 22px; font-weight: 600; margin-top: 4px; color: #303133; }
.chart-box { width: 100%; height: 300px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-sub { font-size: 12px; color: #909399; }
.muted { color: #909399; font-size: 12px; }
.member-card { margin-top: 12px; }
.prob-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.prob-row:last-child { margin-bottom: 0; }
.prob-label { width: 60px; color: #606266; font-size: 13px; }
.prob-row :deep(.el-progress) { flex: 1; }

/* D. 风险提示 */
.risk-block {
  background: #f7f9fc;
  border-radius: 6px;
  padding: 12px;
  text-align: center;
}
.risk-block-title { font-size: 12px; color: #909399; }
.risk-block-value { font-size: 20px; font-weight: 600; margin-top: 6px; color: #303133; }
.risk-action { font-size: 14px; font-weight: 500; line-height: 1.4; }

/* B. 同分桶对比 */
.cmp-block {
  background: #f7f9fc;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
}
.cmp-label { font-size: 12px; color: #909399; }
.cmp-value-self { font-size: 22px; font-weight: 600; margin-top: 6px; color: #409eff; }
.cmp-value-base { font-size: 22px; font-weight: 600; margin-top: 6px; color: #606266; }
.cmp-value-ratio { font-size: 28px; font-weight: 700; margin-top: 6px; }
.ratio-up { color: #67c23a; }
.ratio-mid { color: #909399; }
.ratio-down { color: #f56c6c; }

/* Tab3 模型管理 */
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px }
.filter-tip { color: #909399; font-size: 13px }
.batch-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 8px 12px; background: #f5f7fa; border-radius: 6px; }
.select-tip { color: #909399; font-size: 13px; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px }
.text-ellipsis { display: inline-block; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom }
.model-detail { padding: 0 8px }
.detail-actions { margin-top: 20px; display: flex; gap: 12px }
</style>
