<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">LTV + Churn 模型训练</span>
          <el-space>
            <el-button type="primary" @click="runEtl" :loading="etlLoading">运行 ETL</el-button>
            <el-button type="success" @click="trainBoth" :loading="trainLoading" :disabled="!etlCompleted">
              训练全部模型
            </el-button>
            <el-button @click="loadAll">刷新</el-button>
            <el-divider direction="vertical" />
            <el-button type="primary" plain @click="openNewModelDialog">新建模型</el-button>
            <el-button type="warning" plain :disabled="!hasSelectedModels" @click="batchOffline">
              批量下线
            </el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- LTV 模型 -->
        <el-tab-pane label="LTV 模型" name="ltv">
          <el-descriptions :column="4" border size="small" v-if="etlStats">
            <el-descriptions-item label="特征快照">{{ etlStats.featureDate }}</el-descriptions-item>
            <el-descriptions-item label="样本数">{{ etlStats.trainingReady || 0 }}</el-descriptions-item>
            <el-descriptions-item label="MAPE 目标">&lt;30%</el-descriptions-item>
            <el-descriptions-item label="评估结论">
              <el-tag :type="ltvConclusion === 'GOOD' ? 'success' : ltvConclusion === 'FAIR' ? 'warning' : 'danger'">
                {{ ltvConclusion || '—' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-table :data="ltvModels" v-loading="modelLoading" size="small" stripe style="margin-top:12px"
            @selection-change="onLtvSelectionChange">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="modelCode" label="模型编码" width="200" />
            <el-table-column prop="algorithm" label="算法" width="100" />
            <el-table-column label="样本量" width="90">
              <template #default="{ row }">{{ row.trainingSamples || 0 }}</template>
            </el-table-column>
            <el-table-column label="训练时间" width="170">
              <template #default="{ row }">{{ row.trainingTime || row.trainedAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="VAL MAE" width="100">
              <template #default="{ row }">{{ row.valMae || '—' }}</template>
            </el-table-column>
            <el-table-column label="VAL MAPE" width="120">
              <template #default="{ row }">
                <span :class="getClass(row.valMape)">{{ row.valMape ? row.valMape + '%' : '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="VAL R²" width="100">
              <template #default="{ row }">{{ row.valR2 ? Number(row.valR2).toFixed(4) : '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.isActive === 1" type="success" size="small" effect="dark">已上线</el-tag>
                <el-tag v-else-if="row.status === 1" type="primary" size="small" effect="light">训练中</el-tag>
                <el-tag v-else-if="row.status === 2" type="warning" size="small">已训练</el-tag>
                <el-tag v-else-if="row.status === 3" type="info" size="small">已下线</el-tag>
                <el-tag v-else size="small">{{ row.status ?? '—' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230">
              <template #default="{ row }">
                <el-button size="small" type="primary" :disabled="row.isActive === 1" @click="deployLtv(row)">
                  {{ row.isActive === 1 ? '已上线' : '部署' }}
                </el-button>
                <el-button size="small" type="warning" :disabled="row.isActive !== 1" @click="offlineLtv(row)">
                  下线
                </el-button>
                <el-button size="small" link type="primary" @click="openModelDetail(row, 'ltv')">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Churn 模型 -->
        <el-tab-pane label="Churn 模型" name="churn">
          <el-descriptions :column="4" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="流失定义">超过 90 天不来</el-descriptions-item>
            <el-descriptions-item label="AUC 目标">&gt;0.7</el-descriptions-item>
            <el-descriptions-item label="F1 目标">&gt;0.5</el-descriptions-item>
            <el-descriptions-item label="评估结论">
              <el-tag :type="churnConclusion === 'GOOD' ? 'success' : churnConclusion === 'FAIR' ? 'warning' : 'danger'">
                {{ churnConclusion || '—' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-table :data="churnModels" v-loading="churnLoading" size="small" stripe
            @selection-change="onChurnSelectionChange">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="modelCode" label="模型编码" width="200" />
            <el-table-column prop="algorithm" label="算法" width="100" />
            <el-table-column label="正/负样本" width="130">
              <template #default="{ row }">
                {{ row.positiveSamples || 0 }} / {{ row.negativeSamples || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="训练时间" width="170">
              <template #default="{ row }">{{ row.trainingTime || row.trainedAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="VAL AUC" width="100">
              <template #default="{ row }">
                <span :class="getClass(row.valAuc, true)">{{ row.valAuc ? Number(row.valAuc).toFixed(4) : '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="VAL F1" width="100">
              <template #default="{ row }">
                <span :class="getClass(row.valF1, true)">{{ row.valF1 ? Number(row.valF1).toFixed(4) : '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="VAL Precision" width="120">
              <template #default="{ row }">{{ row.valPrecision ? Number(row.valPrecision).toFixed(4) : '—' }}</template>
            </el-table-column>
            <el-table-column label="VAL Recall" width="100">
              <template #default="{ row }">{{ row.valRecall != null ? Number(row.valRecall).toFixed(2) : '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.isActive === 1" type="success" size="small" effect="dark">已上线</el-tag>
                <el-tag v-else-if="row.status === 1" type="primary" size="small" effect="light">训练中</el-tag>
                <el-tag v-else-if="row.status === 2" type="warning" size="small">已训练</el-tag>
                <el-tag v-else-if="row.status === 3" type="info" size="small">已下线</el-tag>
                <el-tag v-else size="small">{{ row.status ?? '—' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230">
              <template #default="{ row }">
                <el-button size="small" type="primary" :disabled="row.isActive === 1" @click="deployChurn(row)">
                  {{ row.isActive === 1 ? '已上线' : '部署' }}
                </el-button>
                <el-button size="small" type="warning" :disabled="row.isActive !== 1" @click="offlineChurn(row)">
                  下线
                </el-button>
                <el-button size="small" link type="primary" @click="openModelDetail(row, 'churn')">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- Churn 特征重要性 -->
          <el-divider />
          <div v-if="currentChurn">
            <h4>Churn 特征重要性（Top 10）</h4>
            <div ref="churnFeatureChart" style="width:100%;height:360px"></div>
          </div>
        </el-tab-pane>

        <!-- 训练日志 -->
        <el-tab-pane label="训练日志" name="logs">
          <el-tabs>
            <el-tab-pane label="LTV 日志" name="ltv-logs">
              <el-table :data="ltvLogs" size="small" stripe max-height="500">
                <el-table-column prop="createdAt" label="时间" width="170" />
                <el-table-column prop="step" label="步骤" width="100" />
                <el-table-column prop="samples" label="样本" width="80" />
                <el-table-column prop="metric" label="指标" width="120" />
                <el-table-column prop="metricValue" label="值" width="100" />
                <el-table-column prop="durationMs" label="耗时" width="80" />
                <el-table-column prop="message" label="消息" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="Churn 日志" name="churn-logs">
              <el-table :data="churnLogs" size="small" stripe max-height="500">
                <el-table-column prop="createdAt" label="时间" width="170" />
                <el-table-column prop="step" label="步骤" width="100" />
                <el-table-column prop="samples" label="样本" width="80" />
                <el-table-column prop="metric" label="指标" width="120" />
                <el-table-column prop="metricValue" label="值" width="100" />
                <el-table-column prop="durationMs" label="耗时" width="80" />
                <el-table-column prop="message" label="消息" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- 模型对比 -->
        <el-tab-pane label="模型对比" name="compare">
          <h4>模型 A/B 验证 + 综合报告</h4>
          <el-space style="margin:12px 0">
            <el-button type="primary" size="small" @click="runAbValidate" :loading="abLoading">A/B 验证</el-button>
            <el-button type="warning" size="small" @click="runBatchPredict" :loading="batchLoading">批量预测</el-button>
            <el-button type="info" size="small" @click="loadReport">刷新报告</el-button>
          </el-space>

          <el-descriptions v-if="abResult" :column="2" border title="A/B 验证结果" size="small" style="margin-bottom:12px">
            <el-descriptions-item label="类型">{{ abResult.modelType }}</el-descriptions-item>
            <el-descriptions-item label="对比窗口">{{ abResult.windowDays }} 天</el-descriptions-item>
            <el-descriptions-item label="A 模型">{{ abResult.comparison?.modelA?.model_code || '—' }}</el-descriptions-item>
            <el-descriptions-item label="B 模型">{{ abResult.comparison?.modelB?.model_code || abResult.comparison?.note || '—' }}</el-descriptions-item>
            <el-descriptions-item label="A MAPE">{{ abResult.comparison?.modelAMAPE || '—' }}%</el-descriptions-item>
            <el-descriptions-item label="B MAPE">{{ abResult.comparison?.modelBMAPE || '—' }}%</el-descriptions-item>
            <el-descriptions-item label="A AUC">{{ abResult.comparison?.modelAAUC || '—' }}</el-descriptions-item>
            <el-descriptions-item label="B AUC">{{ abResult.comparison?.modelBAUC || '—' }}</el-descriptions-item>
            <el-descriptions-item label="胜出">{{ abResult.comparison?.winner || '—' }}</el-descriptions-item>
            <el-descriptions-item label="建议">{{ abResult.comparison?.recommendation || '—' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions v-if="modelReport" :column="3" border title="模型健康报告" size="small">
            <el-descriptions-item label="总体状态">
              <el-tag :type="modelReport.summary?.overallStatus === 'READY' ? 'success' : modelReport.summary?.overallStatus === 'PARTIAL' ? 'warning' : 'danger'">
                {{ modelReport.summary?.overallStatus || '—' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="消息">{{ modelReport.summary?.message || '—' }}</el-descriptions-item>
            <el-descriptions-item label="训练就绪">
              <el-tag :type="modelReport.trainingStatus?.dataReady ? 'success' : 'info'">
                {{ modelReport.trainingStatus?.dataReady ? '是' : '否' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="特征样本">{{ modelReport.trainingStatus?.memberCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="最新快照">{{ modelReport.trainingStatus?.latestFeatureDate || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>

      <!-- 模型详情抽屉 -->
      <el-drawer v-model="detailVisible" :title="`模型详情 · ${detailModel?.modelCode || ''}`" size="560px" direction="rtl">
        <div v-if="detailModel" class="model-detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="模型编码">{{ detailModel.modelCode }}</el-descriptions-item>
            <el-descriptions-item label="版本号">{{ detailModel.version || '—' }}</el-descriptions-item>
            <el-descriptions-item label="算法">{{ detailModel.algorithm }}</el-descriptions-item>
            <el-descriptions-item label="样本量">{{ detailModel.trainingSamples || detailModel.positiveSamples || 0 }}</el-descriptions-item>
            <el-descriptions-item label="训练时间">{{ detailModel.trainingTime || detailModel.trainedAt || '—' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detailModel.isActive === 1 ? 'success' : detailModel.status === 1 ? 'primary' : detailModel.status === 2 ? 'warning' : 'info'" size="small">
                {{ detailModel.isActive === 1 ? '已上线' : detailModel.status === 1 ? '训练中' : detailModel.status === 2 ? '已训练' : detailModel.status === 3 ? '已下线' : '—' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="模型路径" :span="2">
              <el-tooltip :content="detailModel.modelPath || '—'" placement="top" :show-after="300">
                <span class="text-ellipsis">{{ detailModel.modelPath || '—' }}</span>
              </el-tooltip>
            </el-descriptions-item>
            <el-descriptions-item label="特征快照">{{ detailModel.featureSnapshotDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detailModel.creator || '—' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 性能指标 -->
          <el-divider content-position="left">性能指标</el-divider>
          <el-descriptions v-if="detailModelType === 'ltv'" :column="3" border size="small">
            <el-descriptions-item label="VAL MAE">{{ detailModel.valMae != null ? Number(detailModel.valMae).toFixed(4) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="VAL MAPE">{{ detailModel.valMape != null ? Number(detailModel.valMape).toFixed(2) + '%' : '—' }}</el-descriptions-item>
            <el-descriptions-item label="VAL R²">{{ detailModel.valR2 != null ? Number(detailModel.valR2).toFixed(4) : '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions v-else :column="4" border size="small">
            <el-descriptions-item label="VAL AUC">{{ detailModel.valAuc != null ? Number(detailModel.valAuc).toFixed(4) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="VAL F1">{{ detailModel.valF1 != null ? Number(detailModel.valF1).toFixed(4) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="VAL Precision">{{ detailModel.valPrecision != null ? Number(detailModel.valPrecision).toFixed(4) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="VAL Recall">{{ detailModel.valRecall != null ? Number(detailModel.valRecall).toFixed(2) : '—' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 特征重要性（如果有） -->
          <div v-if="detailModel.featureImportanceJson">
            <el-divider content-position="left">特征重要性（Top 10）</el-divider>
            <div ref="detailFeatureChart" style="width:100%;height:280px"></div>
          </div>

          <div class="detail-actions">
            <el-button v-if="detailModel.isActive !== 1" type="primary" @click="detailModelType === 'ltv' ? deployLtv(detailModel) : deployChurn(detailModel)">
              部署上线
            </el-button>
            <el-button v-if="detailModel.isActive === 1" type="warning" @click="detailModelType === 'ltv' ? offlineLtv(detailModel) : offlineChurn(detailModel)">
              下线
            </el-button>
          </div>
        </div>
      </el-drawer>

      <!-- 新建模型弹窗 -->
      <el-dialog v-model="newModelVisible" title="新建模型" width="520px">
        <el-form :model="newModelForm" label-width="90px">
          <el-form-item label="模型类型" required>
            <el-radio-group v-model="newModelForm.modelType">
              <el-radio-button value="LTV">LTV 模型</el-radio-button>
              <el-radio-button value="CHURN">Churn 模型</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="算法">
            <el-select v-model="newModelForm.algorithm" style="width:100%" placeholder="选择算法">
              <el-option v-if="newModelForm.modelType === 'LTV'" label="MLR（多元线性回归）" value="MLR" />
              <el-option v-if="newModelForm.modelType === 'LTV'" label="RF（随机森林）" value="RF" />
              <el-option v-if="newModelForm.modelType === 'LTV'" label="XGBoost" value="XGB" />
              <el-option v-if="newModelForm.modelType === 'CHURN'" label="LR（逻辑回归）" value="LR" />
              <el-option v-if="newModelForm.modelType === 'CHURN'" label="RF（随机森林）" value="RF" />
              <el-option v-if="newModelForm.modelType === 'CHURN'" label="XGBoost" value="XGB" />
            </el-select>
          </el-form-item>
          <el-form-item label="流失阈值" v-if="newModelForm.modelType === 'CHURN'">
            <el-input-number v-model="newModelForm.churnThreshold" :min="30" :max="365" :step="10" />
            <span style="margin-left:8px;color:#909399;font-size:12px">天（超过该天数不来视为流失）</span>
          </el-form-item>
          <el-form-item label="预测周期">
            <el-input-number v-model="newModelForm.horizon" :min="30" :max="365" :step="30" />
            <span style="margin-left:8px;color:#909399;font-size:12px">天</span>
          </el-form-item>
          <el-form-item label="Epochs" v-if="newModelForm.modelType === 'LTV'">
            <el-input-number v-model="newModelForm.epochs" :min="50" :max="1000" :step="50" />
          </el-form-item>
          <el-form-item label="学习率">
            <el-input-number v-model="newModelForm.learningRate" :min="0.001" :max="1" :step="0.001" :precision="3" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="newModelVisible = false">取消</el-button>
          <el-button type="primary" :loading="newModelSubmitting" @click="submitNewModel">创建并训练</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, nextTick, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { sysApi } from '@/api/sys'

const activeTab = ref('ltv')
const etlLoading = ref(false)
const trainLoading = ref(false)
const modelLoading = ref(false)
const churnLoading = ref(false)
const etlCompleted = ref(false)
const etlStats = ref<any>(null)
const ltvModels = ref<any[]>([])
const churnModels = ref<any[]>([])
const ltvLogs = ref<any[]>([])
const churnLogs = ref<any[]>([])
const ltvConclusion = ref('')
const churnConclusion = ref('')
const abLoading = ref(false)
const batchLoading = ref(false)
const reportLoading = ref(false)
const abResult = ref<any>(null)
const modelReport = ref<any>(null)
const currentChurn = ref<any>(null)
const churnFeatureChart = ref<HTMLDivElement>()
let churnChart: any = null

// 批量操作
const selectedLtvModels = ref<any[]>([])
const selectedChurnModels = ref<any[]>([])
const hasSelectedModels = computed(() => selectedLtvModels.value.length + selectedChurnModels.value.length > 0)
const batchOfflineLoading = ref(false)

function onLtvSelectionChange(rows: any[]) {
  selectedLtvModels.value = rows
}
function onChurnSelectionChange(rows: any[]) {
  selectedChurnModels.value = rows
}

async function batchOffline() {
  const all = [...selectedLtvModels.value, ...selectedChurnModels.value]
  if (!all.length) return ElMessage.warning('请先勾选要下线的模型')
  const ltvCnt = selectedLtvModels.value.length
  const churnCnt = selectedChurnModels.value.length
  try {
    await ElMessageBox.confirm(
      `确定批量下线 ${ltvCnt} 个 LTV 模型和 ${churnCnt} 个 Churn 模型？`,
      '批量下线确认', { type: 'warning', confirmButtonText: '确认下线', cancelButtonText: '取消' }
    )
  } catch { return }

  batchOfflineLoading.value = true
  try {
    await Promise.allSettled([
      ...selectedLtvModels.value.map(m => sysApi.request({ method: 'POST', url: `/intel/ltv/training/model/${m.id}/offline` })),
      ...selectedChurnModels.value.map(m => sysApi.request({ method: 'POST', url: `/intel/churn/training/model/${m.id}/offline` })),
    ])
    ElMessage.success('批量下线完成')
    selectedLtvModels.value = []
    selectedChurnModels.value = []
    await loadAll()
  } catch (e: any) {
    ElMessage.error('批量下线失败: ' + (e?.message || '未知'))
  } finally {
    batchOfflineLoading.value = false
  }
}

// 新建模型弹窗
const newModelVisible = ref(false)
const newModelSubmitting = ref(false)
const newModelForm = reactive({
  modelType: 'LTV',
  algorithm: 'MLR',
  churnThreshold: 90,
  horizon: 90,
  epochs: 200,
  learningRate: 0.01,
})

function openNewModelDialog() {
  Object.assign(newModelForm, { modelType: 'LTV', algorithm: 'MLR', churnThreshold: 90, horizon: 90, epochs: 200, learningRate: 0.01 })
  newModelVisible.value = true
}

async function submitNewModel() {
  newModelSubmitting.value = true
  try {
    const url = newModelForm.modelType === 'LTV'
      ? '/intel/ltv/training/train'
      : '/intel/churn/training/train'
    const data = newModelForm.modelType === 'LTV'
      ? { algorithm: newModelForm.algorithm, epochs: newModelForm.epochs, learningRate: newModelForm.learningRate }
      : { algorithm: newModelForm.algorithm, churnThreshold: newModelForm.churnThreshold, horizon: newModelForm.horizon }

    await sysApi.request({ method: 'POST', url, data })
    ElMessage.success('模型创建并训练中，请稍后在列表查看进度')
    newModelVisible.value = false
    await loadAll()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.message || '未知'))
  } finally {
    newModelSubmitting.value = false
  }
}

// 模型详情抽屉
const detailVisible = ref(false)
const detailModel = ref<any>(null)
const detailModelType = ref<'ltv' | 'churn'>('ltv')
const detailFeatureChart = ref<HTMLDivElement>()
let detailChart: any = null

function openModelDetail(row: any, type: 'ltv' | 'churn') {
  detailModel.value = row
  detailModelType.value = type
  detailVisible.value = true
  // 渲染详情中的特征重要性图
  nextTick(() => {
    renderDetailFeature()
  })
}

function renderDetailFeature() {
  if (!detailFeatureChart.value || !detailModel.value?.featureImportanceJson) return
  if (detailChart) { detailChart.dispose() }
  detailChart = echarts.init(detailFeatureChart.value)
  try {
    const importance: Array<{feature: string; importance: number}> = JSON.parse(detailModel.value.featureImportanceJson)
    importance.sort((a, b) => a.importance - b.importance)
    const top10 = importance.slice(-10)
    const color = detailModelType.value === 'churn' ? '#f56c6c' : '#409eff'
    detailChart.setOption({
      title: { text: '特征重要性（Top 10）', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { left: 160, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'value', name: '重要性' },
      yAxis: { type: 'category', data: top10.map(i => i.feature), name: '特征' },
      series: [{
        type: 'bar',
        data: top10.map(i => Number(i.importance.toFixed(4))),
        itemStyle: { color }
      }]
    })
  } catch (e) {
    console.warn('parse feature importance failed', e)
  }
}

async function runEtl() {
  etlLoading.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/intel/ltv/training/etl/run', data: {} })
    etlStats.value = r.data
    etlCompleted.value = (r.data?.trainingReady || 0) >= 100
    if (etlCompleted.value) {
      ElMessage.success(`ETL 完成：${r.data.trainingReady} 条可用样本`)
    } else {
      ElMessage.warning(`样本不足（${r.data.trainingReady || 0} 条）`)
    }
    await loadLtvLogs()
  } catch (e: any) {
    ElMessage.error('ETL 失败: ' + (e?.message || '未知'))
  } finally {
    etlLoading.value = false
  }
}

async function trainBoth() {
  trainLoading.value = true
  try {
    // 并行训练 LTV 和 Churn
    const [ltvResult, churnResult] = await Promise.allSettled([
      sysApi.request({ method: 'POST', url: '/intel/ltv/training/train', data: { algorithm: 'MLR', epochs: 200, learningRate: 0.01 } }),
      sysApi.request({ method: 'POST', url: '/intel/churn/training/train', data: { algorithm: 'LR', churnThreshold: 90, horizon: 90 } })
    ])

    if (ltvResult.status === 'fulfilled') {
      const r = ltvResult.value as any
      ltvConclusion.value = r.data?.conclusion || ''
      ElMessage.success(`LTV 训练完成: MAPE=${r.data?.valMAPE}%, R²=${r.data?.valR2}`)
    } else {
      ElMessage.warning('LTV 训练失败: ' + (ltvResult.reason?.message || ''))
    }

    if (churnResult.status === 'fulfilled') {
      const r = churnResult.value as any
      churnConclusion.value = r.data?.conclusion || ''
      ElMessage.success(`Churn 训练完成: AUC=${r.data?.valAUC}, F1=${r.data?.valF1}`)
    } else {
      ElMessage.warning('Churn 训练失败: ' + (churnResult.reason?.message || ''))
    }

    await Promise.all([loadAll()])
  } catch (e: any) {
    ElMessage.error('训练异常: ' + (e?.message || '未知'))
  } finally {
    trainLoading.value = false
  }
}

async function loadLtvModels() {
  modelLoading.value = true
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/ltv/training/model/list' })
    ltvModels.value = r.data || []
    const active = ltvModels.value.find((m: any) => m.isActive === 1)
    ltvConclusion.value = active?.valMape ? evalConclusion(active.valMape, false) : ''
  } catch (e) {
    ltvModels.value = []
  } finally {
    modelLoading.value = false
  }
}

async function loadChurnModels() {
  churnLoading.value = true
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/churn/training/model/list' })
    churnModels.value = r.data || []
    const active = churnModels.value.find((m: any) => m.isActive === 1)
    currentChurn.value = active || (churnModels.value.length > 0 ? churnModels.value[0] : null)
    churnConclusion.value = active?.valAuc ? evalConclusion(active.valAuc, true) : ''
    await nextTick()
    renderChurnFeature()
  } catch (e) {
    churnModels.value = []
  } finally {
    churnLoading.value = false
  }
}

async function loadLtvLogs() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/ltv/training/run/log', params: { limit: 20 } })
    ltvLogs.value = r.data || []
  } catch (e) {
    ltvLogs.value = []
  }
}

async function loadChurnLogs() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/churn/training/run/log', params: { limit: 20 } })
    churnLogs.value = r.data || []
  } catch (e) {
    churnLogs.value = []
  }
}

async function runAbValidate() {
  abLoading.value = true
  try {
    const r: any = await sysApi.modelAbValidate('LTV', 90)
    abResult.value = r.data
    ElMessage.success('A/B 验证完成')
  } catch (e: any) {
    ElMessage.error('A/B 验证失败: ' + (e?.message || '未知'))
    abResult.value = null
  } finally {
    abLoading.value = false
  }
}

async function runBatchPredict() {
  batchLoading.value = true
  try {
    const r: any = await sysApi.modelBatchPredict('LTV', 10000)
    ElMessage.success('批量预测完成: ' + (r.data?.predicted || 0) + ' 条')
  } catch (e: any) {
    ElMessage.error('批量预测失败: ' + (e?.message || '未知'))
  } finally {
    batchLoading.value = false
  }
}

async function loadReport() {
  reportLoading.value = true
  try {
    const r: any = await sysApi.modelReport()
    modelReport.value = r.data
  } catch (e: any) {
    modelReport.value = null
  } finally {
    reportLoading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadLtvModels(), loadChurnModels(), loadLtvLogs(), loadChurnLogs()])
}

async function deployLtv(row: any) {
  try {
    await sysApi.request({ method: 'POST', url: `/intel/ltv/training/model/${row.id}/deploy` })
    ElMessage.success('LTV 模型已上线')
    await loadLtvModels()
  } catch (e: any) {
    ElMessage.error('部署失败: ' + (e?.message || '未知'))
  }
}

async function offlineLtv(row: any) {
  try {
    await sysApi.request({ method: 'POST', url: `/intel/ltv/training/model/${row.id}/offline` })
    ElMessage.success('LTV 模型已下线')
    await loadLtvModels()
  } catch (e: any) {
    ElMessage.error('下线失败: ' + (e?.message || '未知'))
  }
}

async function deployChurn(row: any) {
  try {
    await sysApi.request({ method: 'POST', url: `/intel/churn/training/model/${row.id}/deploy` })
    ElMessage.success('Churn 模型已上线')
    await loadChurnModels()
  } catch (e: any) {
    ElMessage.error('部署失败: ' + (e?.message || '未知'))
  }
}

async function offlineChurn(row: any) {
  try {
    await sysApi.request({ method: 'POST', url: `/intel/churn/training/model/${row.id}/offline` })
    ElMessage.success('Churn 模型已下线')
    await loadChurnModels()
  } catch (e: any) {
    ElMessage.error('下线失败: ' + (e?.message || '未知'))
  }
}

function evalConclusion(v: any, isChurn: boolean): string {
  if (!v) return '—'
  const val = Number(v)
  if (isChurn) {
    return val >= 0.8 ? 'GOOD' : val >= 0.7 ? 'FAIR' : 'POOR'
  }
  return val < 20 ? 'GOOD' : val < 35 ? 'FAIR' : 'POOR'
}

function getConclusion(auc: any, f1: any): string {
  if (!auc || !f1) return '—'
  const a = Number(auc), f = Number(f1)
  return a >= 0.8 && f >= 0.7 ? 'GOOD' : a >= 0.7 && f >= 0.5 ? 'FAIR' : 'POOR'
}

function getClass(v: any, isChurn = false): string {
  if (!v) return ''
  const val = Number(v)
  if (isChurn) {
    return val >= 0.8 ? 'good' : val >= 0.7 ? 'fair' : 'poor'
  }
  return val < 20 ? 'good' : val < 35 ? 'fair' : 'poor'
}

function renderChurnFeature() {
  if (!churnFeatureChart.value || !currentChurn.value?.featureImportanceJson) return
  if (churnChart) { churnChart.dispose() }
  churnChart = echarts.init(churnFeatureChart.value)
  try {
    const importance: Array<{feature: string; importance: number}> = JSON.parse(currentChurn.value.featureImportanceJson)
    importance.sort((a, b) => a.importance - b.importance)
    const top10 = importance.slice(-10)
    churnChart.setOption({
      title: { text: 'Churn 特征重要性（Top 10）', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'axis' },
      grid: { left: 160, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'value', name: '重要性' },
      yAxis: { type: 'category', data: top10.map(i => i.feature), name: '特征' },
      series: [{
        type: 'bar',
        data: top10.map(i => Number(i.importance.toFixed(4))),
        itemStyle: { color: '#f56c6c' }
      }]
    })
  } catch (e) {
    console.warn('parse feature importance failed', e)
  }
}

onMounted(() => { loadAll() })
onUnmounted(() => { if (churnChart) churnChart.dispose(); if (detailChart) detailChart.dispose() })
</script>

<style scoped>
.page { padding: 16px; max-width: 1200px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.good { color: #67c23a; font-weight: 600 }
.fair { color: #e6a23c; font-weight: 600 }
.poor { color: #f56c6c; font-weight: 600 }
.text-ellipsis { display: inline-block; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom }
.model-detail { padding: 0 8px }
.detail-actions { margin-top: 20px; display: flex; gap: 12px }
</style>
