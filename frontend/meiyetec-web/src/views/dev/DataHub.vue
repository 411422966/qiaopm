<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">数据中台 · 主数据 / 元数据 / 指标 / 看板</span>
          <el-space>
            <el-button @click="initBuiltin" type="success" plain :loading="initializing">初始化内置指标</el-button>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 中台概览 -->
        <el-tab-pane label="中台概览" name="overview">
          <el-row :gutter="16">
            <el-col :span="6"><el-statistic title="维度数" :value="health.dimensions || 0" /></el-col>
            <el-col :span="6"><el-statistic title="元数据表" :value="health.metaTables || 0" /></el-col>
            <el-col :span="6"><el-statistic title="指标定义" :value="health.metrics || 0" /></el-col>
            <el-col :span="6"><el-statistic title="快照记录" :value="health.snapshots || 0" /></el-col>
          </el-row>
          <el-row :gutter="16" style="margin-top:16px">
            <el-col :span="6"><el-statistic title="看板数" :value="health.dashboards || 0" /></el-col>
            <el-col :span="6"><el-statistic title="血缘数" :value="health.lineages || 0" /></el-col>
            <el-col :span="6">
              <el-card>
                <div class="stat-label">成熟度</div>
                <el-tag :type="health.maturity === 'ADVANCED' ? 'success' : health.maturity === 'INTERMEDIATE' ? 'warning' : 'info'" size="large">
                  {{ health.maturity || '—' }}
                </el-tag>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-button type="primary" size="large" @click="computeAll" :loading="computing">一键计算所有指标</el-button>
            </el-col>
          </el-row>

          <el-divider>运营看板</el-divider>
          <el-row :gutter="16">
            <el-col v-for="m in kpis" :key="m.code" :span="6">
              <el-card class="kpi-card">
                <div class="kpi-label">{{ m.label }}</div>
                <div class="kpi-value">{{ formatValue(m.snapshot?.value, m.unit) }}</div>
                <div class="kpi-date">{{ m.snapshot?.snapshot_date || '—' }}</div>
              </el-card>
            </el-col>
          </el-row>

          <div ref="trendChart" style="width:100%;height:360px;margin-top:16px"></div>
        </el-tab-pane>

        <!-- 主数据 -->
        <el-tab-pane label="主数据" name="dim">
          <el-row :gutter="20">
            <el-col :span="8">
              <h4>新建维度</h4>
              <el-form :model="dimForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="dimForm.dimCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="dimForm.dimName" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="dimForm.dimType">
                    <el-option label="业务维度" value="BUSINESS" />
                    <el-option label="时间维度" value="TIME" />
                    <el-option label="区域维度" value="REGION" />
                    <el-option label="自定义" value="CUSTOM" />
                  </el-select>
                </el-form-item>
                <el-form-item label="描述"><el-input v-model="dimForm.description" type="textarea" :rows="2" /></el-form-item>
                <el-button type="primary" @click="createDim" :loading="creatingDim">创建</el-button>
              </el-form>
              <el-divider />
              <h4>维度列表</h4>
              <el-table :data="dims" size="small" @row-click="loadDimValues">
                <el-table-column prop="dimCode" label="编码" width="140" />
                <el-table-column prop="dimName" label="名称" />
                <el-table-column prop="dimType" label="类型" width="100" />
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button size="small" @click.stop="loadDimValues(row)">值</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>

            <el-col :span="16">
              <h4>{{ currentDim?.dimName || '维度值' }}</h4>
              <el-form :inline="true" size="small" v-if="currentDim">
                <el-form-item label="编码"><el-input v-model="valueForm.valueCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="valueForm.valueName" /></el-form-item>
                <el-form-item label="父级">
                  <el-select v-model="valueForm.parentId" clearable filterable>
                    <el-option v-for="v in dimValues" :key="v.id" :label="v.valueName" :value="v.id" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="addValue" :loading="addingValue">添加</el-button>
              </el-form>
              <el-table :data="dimValues" size="small" stripe style="margin-top:8px">
                <el-table-column prop="valueCode" label="编码" width="120" />
                <el-table-column prop="valueName" label="名称" />
                <el-table-column prop="parentId" label="父级ID" width="100" />
                <el-table-column prop="sortOrder" label="排序" width="80" />
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 元数据 -->
        <el-tab-pane label="元数据" name="meta">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>注册元数据</h4>
              <el-form :model="metaForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="metaForm.tableCode" placeholder="例如 crm_order" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="metaForm.tableName" /></el-form-item>
                <el-form-item label="分类">
                  <el-select v-model="metaForm.category">
                    <el-option label="业务表" value="BUSINESS" />
                    <el-option label="日志表" value="LOG" />
                    <el-option label="AI表" value="AI" />
                    <el-option label="运维表" value="OPS" />
                  </el-select>
                </el-form-item>
                <el-form-item label="Owner"><el-input v-model="metaForm.owner" /></el-form-item>
                <el-button type="primary" @click="createMeta" :loading="creatingMeta">注册</el-button>
              </el-form>

              <el-divider />
              <el-table :data="metaTables" size="small" stripe>
                <el-table-column prop="tableCode" label="编码" width="140" />
                <el-table-column prop="tableName" label="名称" />
                <el-table-column prop="category" label="分类" width="80" />
                <el-table-column prop="rowCount" label="行数" width="80" />
                <el-table-column label="操作" width="160">
                  <template #default="{ row }">
                    <el-button size="small" @click="syncMeta(row.id)">同步</el-button>
                    <el-button size="small" type="info" @click="showColumns(row)">字段</el-button>
                    <el-button size="small" type="warning" @click="showLineage(row.tableCode)">血缘</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>

            <el-col :span="14">
              <h4>{{ currentMetaTable?.tableName || '字段 / 血缘' }}</h4>
              <el-tabs v-if="currentMetaTable">
                <el-tab-pane label="字段" name="cols">
                  <el-table :data="columns" size="small" stripe max-height="400">
                    <el-table-column prop="columnName" label="字段" width="160" />
                    <el-table-column prop="columnType" label="类型" width="100" />
                    <el-table-column label="主键" width="60">
                      <template #default="{ row }">
                        <el-tag v-if="row.isPrimaryKey === 1" type="warning" size="small">PK</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="可空" width="60">
                      <template #default="{ row }">
                        {{ row.isNullable === 1 ? 'YES' : 'NO' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="敏感" width="60">
                      <template #default="{ row }">
                        <el-tag v-if="row.isSensitive === 1" type="danger" size="small">敏</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="description" label="说明" />
                  </el-table>
                </el-tab-pane>
                <el-tab-pane label="血缘" name="lineage">
                  <div v-if="lineageGraph">
                    <h4>上游（{{ lineageGraph.upstream?.length || 0 }}）</h4>
                    <el-tag v-for="l in lineageGraph.upstream" :key="l.id" size="small" style="margin:4px">
                      {{ l.sourceTableCode }} → {{ l.transformation }}
                    </el-tag>
                    <div v-if="!lineageGraph.upstream?.length" style="color:#909399">无上游血缘</div>

                    <h4 style="margin-top:16px">下游（{{ lineageGraph.downstream?.length || 0 }}）</h4>
                    <el-tag v-for="l in lineageGraph.downstream" :key="l.id" size="small" style="margin:4px">
                      {{ l.targetTableCode }} ← {{ l.transformation }}
                    </el-tag>
                    <div v-if="!lineageGraph.downstream?.length" style="color:#909399">无下游血缘</div>

                    <el-divider />
                    <el-button size="small" type="primary" @click="addLineageDialog = true">添加血缘</el-button>
                  </div>
                </el-tab-pane>
              </el-tabs>
              <el-empty v-else description="选择左侧表查看详情" />
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 指标 -->
        <el-tab-pane label="指标" name="metric">
          <el-row :gutter="20">
            <el-col :span="8">
              <h4>新建指标</h4>
              <el-form :model="metricForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="metricForm.metricCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="metricForm.metricName" /></el-form-item>
                <el-form-item label="分类">
                  <el-select v-model="metricForm.metricCategory">
                    <el-option label="业务" value="BUSINESS" />
                    <el-option label="运营" value="OPERATIONS" />
                    <el-option label="营销" value="MARKETING" />
                    <el-option label="财务" value="FINANCE" />
                    <el-option label="AI" value="AI" />
                  </el-select>
                </el-form-item>
                <el-form-item label="单位"><el-input v-model="metricForm.unit" /></el-form-item>
                <el-form-item label="目标值"><el-input-number v-model="metricForm.targetValue" /></el-form-item>
                <el-form-item label="预警值"><el-input-number v-model="metricForm.warningValue" /></el-form-item>
                <el-form-item label="SQL 模板">
                  <el-input v-model="metricForm.sqlTemplate" type="textarea" :rows="3"
                            :placeholder="sqlPlaceholder" />
                </el-form-item>
                <el-button type="primary" @click="createMetric" :loading="creatingMetric">创建</el-button>
              </el-form>
            </el-col>

            <el-col :span="16">
              <h4>指标列表</h4>
              <el-table :data="metrics" size="small" stripe>
                <el-table-column prop="metricCode" label="编码" width="160" />
                <el-table-column prop="metricName" label="名称" />
                <el-table-column prop="metricCategory" label="分类" width="100" />
                <el-table-column prop="unit" label="单位" width="60" />
                <el-table-column label="目标/预警" width="160">
                  <template #default="{ row }">
                    {{ row.targetValue || '—' }} / {{ row.warningValue || '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="方向" width="60">
                  <template #default="{ row }">
                    <el-tag v-if="row.higherIsBetter === 1" size="small" type="success">↑</el-tag>
                    <el-tag v-else size="small" type="info">↓</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="computeOne(row.id)">计算</el-button>
                    <el-button size="small" @click="showTrend(row.metricCode)">趋势</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <div v-if="trendData.length > 0" ref="metricChart" style="width:100%;height:280px;margin-top:16px"></div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 看板 -->
        <el-tab-pane label="看板" name="dashboard">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>新建看板</h4>
              <el-form :model="dashForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="dashForm.dashboardCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="dashForm.dashboardName" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="dashForm.dashboardType">
                    <el-option label="运营" value="OPS" />
                    <el-option label="销售" value="SALES" />
                    <el-option label="会员" value="MEMBER" />
                    <el-option label="营销" value="MARKETING" />
                    <el-option label="财务" value="FINANCE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="描述"><el-input v-model="dashForm.description" type="textarea" :rows="2" /></el-form-item>
                <el-form-item><el-checkbox v-model="dashForm.isDefault">设为默认</el-checkbox></el-form-item>
                <el-button type="primary" @click="createDashboard" :loading="creatingDash">创建</el-button>
              </el-form>
            </el-col>
            <el-col :span="14">
              <h4>看板列表</h4>
              <el-table :data="dashboards" size="small" stripe>
                <el-table-column prop="dashboardCode" label="编码" width="140" />
                <el-table-column prop="dashboardName" label="名称" />
                <el-table-column prop="dashboardType" label="类型" width="100" />
                <el-table-column label="默认" width="60">
                  <template #default="{ row }">
                    <el-tag v-if="row.isDefault === 1" type="success" size="small">是</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 添加血缘对话框 -->
    <el-dialog v-model="addLineageDialog" title="添加血缘" width="500px">
      <el-form :model="lineageForm" label-width="80px">
        <el-form-item label="源表"><el-input v-model="lineageForm.sourceTableCode" /></el-form-item>
        <el-form-item label="目标表"><el-input v-model="lineageForm.targetTableCode" /></el-form-item>
        <el-form-item label="转换说明"><el-input v-model="lineageForm.transformation" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="lineageForm.lineageType">
            <el-option label="ETL" value="ETL" />
            <el-option label="JOIN" value="JOIN" />
            <el-option label="COPY" value="COPY" />
            <el-option label="AGG" value="AGG" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addLineageDialog = false">取消</el-button>
        <el-button type="primary" @click="addLineage">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, nextTick, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { sysApi } from '@/api/sys'

const activeTab = ref('overview')
const initializing = ref(false)
const computing = ref(false)
const health = ref<any>({})
const kpis = ref<any[]>([])
const trendChart = ref<HTMLDivElement>()
const metricChart = ref<HTMLDivElement>()
let trendEChart: any = null
let metricEChart: any = null

const dims = ref<any[]>([])
const dimValues = ref<any[]>([])
const currentDim = ref<any>(null)
const dimForm = ref<any>({ dimCode: '', dimName: '', dimType: 'BUSINESS', description: '' })
const valueForm = ref<any>({ valueCode: '', valueName: '', parentId: null })
const creatingDim = ref(false)
const addingValue = ref(false)

const metaTables = ref<any[]>([])
const columns = ref<any[]>([])
const currentMetaTable = ref<any>(null)
const lineageGraph = ref<any>(null)
const metaForm = ref<any>({ tableCode: '', tableName: '', category: 'BUSINESS', owner: '' })
const creatingMeta = ref(false)
const addLineageDialog = ref(false)
const lineageForm = ref<any>({ sourceTableCode: '', targetTableCode: '', transformation: '', lineageType: 'ETL' })

const metrics = ref<any[]>([])
const metricForm = ref<any>({ metricCode: '', metricName: '', metricCategory: 'BUSINESS', unit: '', targetValue: 0, warningValue: 0, sqlTemplate: '' })
const creatingMetric = ref(false)
const trendData = ref<any[]>([])
const sqlPlaceholder = 'SELECT ... FROM tbl WHERE tenant_id = {tenant_id} AND date = {date}'

const dashboards = ref<any[]>([])
const dashForm = ref<any>({ dashboardCode: '', dashboardName: '', dashboardType: 'OPS', description: '', isDefault: false })
const creatingDash = ref(false)

async function initBuiltin() {
  initializing.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/datahub/metric/init-builtin' })
    ElMessage.success(`已初始化 ${r.data?.created} 个指标`)
    loadMetrics()
  } catch (e: any) {
    ElMessage.error('初始化失败: ' + (e?.message || ''))
  } finally {
    initializing.value = false
  }
}

async function loadHealth() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/datahub/health' })
    health.value = r.data || {}
  } catch (e) { health.value = {} }
}

async function loadOps() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/datahub/dashboard/ops' })
    const data = r.data || {}
    kpis.value = [
      { code: 'REVENUE_DAILY', label: '今日营收', unit: '元', snapshot: data.REVENUE_DAILY },
      { code: 'ORDERS_DAILY', label: '今日订单', unit: '单', snapshot: data.ORDERS_DAILY },
      { code: 'MEMBER_VISITS_DAILY', label: '访客数', unit: '人', snapshot: data.MEMBER_VISITS_DAILY },
      { code: 'NEW_MEMBERS_DAILY', label: '新增会员', unit: '人', snapshot: data.NEW_MEMBERS_DAILY }
    ]
    renderTrend(data.trend || [])
  } catch (e) { kpis.value = [] }
}

function renderTrend(data: any[]) {
  if (!trendChart.value) return
  if (trendEChart) trendEChart.dispose()
  trendEChart = echarts.init(trendChart.value)
  const codes = [...new Set(data.map((d: any) => d.metric_code))]
  const dates = [...new Set(data.map((d: any) => d.snapshot_date))]
  dates.sort()
  const series = codes.map((code: any) => ({
    name: code,
    type: 'line',
    smooth: true,
    data: dates.map((d: any) => {
      const item = data.find((x: any) => x.snapshot_date === d && x.metric_code === code)
      return item ? Number(item.value) : 0
    })
  }))
  trendEChart.setOption({
    title: { text: '近 7 天趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { top: 30 },
    grid: { left: 60, right: 30, top: 80, bottom: 40 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value' },
    series
  })
}

async function computeAll() {
  computing.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/datahub/metric/compute-all' })
    ElMessage.success(`完成：成功 ${r.data?.success}，失败 ${r.data?.fail}`)
    loadOps()
  } catch (e: any) {
    ElMessage.error('失败: ' + (e?.message || ''))
  } finally {
    computing.value = false
  }
}

function formatValue(v: any, unit?: string) {
  if (!v) return '0' + (unit || '')
  return Number(v).toLocaleString() + (unit || '')
}

// ========= 主数据 =========
async function createDim() {
  if (!dimForm.value.dimCode || !dimForm.value.dimName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creatingDim.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/datahub/dim', data: dimForm.value })
    ElMessage.success('已创建')
    loadDims()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    creatingDim.value = false
  }
}

async function loadDims() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/datahub/dim' })
    dims.value = r.data || []
  } catch (e) { dims.value = [] }
}

async function loadDimValues(row: any) {
  currentDim.value = row
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/datahub/dim/${row.id}/values` })
    dimValues.value = r.data || []
  } catch (e) { dimValues.value = [] }
}

async function addValue() {
  if (!currentDim.value) return
  addingValue.value = true
  try {
    await sysApi.request({ method: 'POST', url: `/datahub/dim/${currentDim.value.id}/value`, data: valueForm.value })
    ElMessage.success('已添加')
    valueForm.value = { valueCode: '', valueName: '', parentId: null }
    loadDimValues(currentDim.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    addingValue.value = false
  }
}

// ========= 元数据 =========
async function createMeta() {
  if (!metaForm.value.tableCode || !metaForm.value.tableName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creatingMeta.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/datahub/meta/table', data: metaForm.value })
    ElMessage.success('已注册')
    loadMetaTables()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    creatingMeta.value = false
  }
}

async function loadMetaTables() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/datahub/meta/table' })
    metaTables.value = r.data || []
  } catch (e) { metaTables.value = [] }
}

async function syncMeta(id: number) {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: `/datahub/meta/table/${id}/sync` })
    ElMessage.success(`已同步：${r.data?.rowCount} 行 / ${r.data?.columnCount} 列`)
    loadMetaTables()
  } catch (e: any) {
    ElMessage.error('同步失败: ' + (e?.message || ''))
  }
}

async function showColumns(row: any) {
  currentMetaTable.value = row
  lineageGraph.value = null
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/datahub/meta/table/${row.id}/columns` })
    columns.value = r.data || []
  } catch (e) { columns.value = [] }
}

async function showLineage(tableCode: string) {
  currentMetaTable.value = metaTables.value.find((m: any) => m.tableCode === tableCode)
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/datahub/meta/lineage/${tableCode}` })
    lineageGraph.value = r.data
  } catch (e) { lineageGraph.value = null }
}

async function addLineage() {
  await sysApi.request({ method: 'POST', url: '/datahub/meta/lineage', data: lineageForm.value })
  ElMessage.success('已添加')
  addLineageDialog.value = false
  if (currentMetaTable.value) showLineage(currentMetaTable.value.tableCode)
}

// ========= 指标 =========
async function createMetric() {
  creatingMetric.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/datahub/metric', data: metricForm.value })
    ElMessage.success('已创建')
    loadMetrics()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    creatingMetric.value = false
  }
}

async function loadMetrics() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/datahub/metric' })
    metrics.value = r.data || []
  } catch (e) { metrics.value = [] }
}

async function computeOne(id: number) {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: `/datahub/metric/${id}/compute`, data: {} })
    ElMessage.success(`${r.data?.value} · ${r.data?.status}`)
  } catch (e: any) {
    ElMessage.error('计算失败: ' + (e?.message || ''))
  }
}

async function showTrend(code: string) {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/datahub/metric/${code}/trend`, params: { days: 30 } })
    trendData.value = r.data || []
    await nextTick()
    renderMetricTrend(code)
  } catch (e) {
    trendData.value = []
  }
}

function renderMetricTrend(code: string) {
  if (!metricChart.value || trendData.value.length === 0) return
  if (metricEChart) metricEChart.dispose()
  metricEChart = echarts.init(metricChart.value)
  metricEChart.setOption({
    title: { text: code + ' - 30天趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 30, top: 60, bottom: 40 },
    xAxis: { type: 'category', data: trendData.map(d => d.date) },
    yAxis: { type: 'value' },
    series: [{
      type: 'line',
      smooth: true,
      data: trendData.map(d => Number(d.value)),
      areaStyle: { opacity: 0.3 }
    }]
  })
}

// ========= 看板 =========
async function createDashboard() {
  if (!dashForm.value.dashboardCode || !dashForm.value.dashboardName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creatingDash.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/datahub/dashboard', data: dashForm.value })
    ElMessage.success('已创建')
    loadDashboards()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    creatingDash.value = false
  }
}

async function loadDashboards() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/datahub/dashboard' })
    dashboards.value = r.data || []
  } catch (e) { dashboards.value = [] }
}

function loadAll() {
  loadHealth()
  loadOps()
  loadDims()
  loadMetaTables()
  loadMetrics()
  loadDashboards()
}

onMounted(() => loadAll())
onUnmounted(() => {
  if (trendEChart) trendEChart.dispose()
  if (metricEChart) metricEChart.dispose()
})
</script>

<style scoped>
.page { padding: 16px; max-width: 1400px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.kpi-card { text-align: center }
.kpi-label { font-size: 13px; color: #909399; margin-bottom: 4px }
.kpi-value { font-size: 24px; font-weight: bold; color: #303133 }
.kpi-date { font-size: 11px; color: #909399; margin-top: 4px }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 8px }
</style>