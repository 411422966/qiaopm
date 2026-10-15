<template>
  <div class="ai-vision-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">AI 视觉识别算法</span>
          <el-space>
            <el-button type="primary" @click="initDefault" :loading="initing">初始化默认模型</el-button>
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-row :gutter="16" v-if="stats">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">总调用</div>
            <div class="stat-value">{{ stats.totalCalls || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">成功调用</div>
            <div class="stat-value">{{ stats.successCalls || 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">成功率</div>
            <div class="stat-value">
              {{ stats.totalCalls ? Math.round((stats.successCalls / stats.totalCalls) * 100) : 0 }}%
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">平均延迟</div>
            <div class="stat-value">{{ Math.round(stats.avgLatency || 0) }} ms</div>
          </el-card>
        </el-col>
      </el-row>
      <el-alert v-if="stats && stats.byBizType && stats.byBizType.length" type="info" :closable="false" style="margin-top:12px">
        <template #title>
          按业务类型：
          <el-tag v-for="b in stats.byBizType" :key="b.biz_type" style="margin-left:6px">
            {{ bizTypeLabel(b.biz_type) }} · {{ b.cnt }} 次 · 平均 {{ Math.round(b.avg_latency) }} ms
          </el-tag>
        </template>
      </el-alert>
    </el-card>

    <el-tabs v-model="activeTab">
      <!-- 模型库 -->
      <el-tab-pane label="模型库" name="models">
        <el-card>
          <div class="model-grid">
            <div v-for="m in models" :key="m.id" class="model-card">
              <div class="model-head">
                <h4>{{ m.modelName }}</h4>
                <el-tag size="small" :type="m.provider === 'TENCENT' ? 'primary' : m.provider === 'ALIYUN' ? 'warning' : 'info'">
                  {{ m.provider }}
                </el-tag>
              </div>
              <div class="model-type">{{ typeLabel(m.modelType) }}</div>
              <div class="metrics">
                <div class="metric">
                  <span class="label">准确率</span>
                  <span class="value">{{ m.accuracy }}%</span>
                </div>
                <div class="metric">
                  <span class="label">延迟</span>
                  <span class="value">{{ m.latencyMs }} ms</span>
                </div>
                <div class="metric">
                  <span class="label">模式</span>
                  <el-tag size="small" :type="m.isSandbox ? 'warning' : 'success'">
                    {{ m.isSandbox ? 'Sandbox' : '生产' }}
                  </el-tag>
                </div>
              </div>
              <div class="endpoint">{{ m.endpoint }}</div>
              <div class="desc">{{ m.description }}</div>
            </div>
          </div>
          <el-empty v-if="!models.length" description="暂无模型，请点击「初始化默认模型」" />
        </el-card>
      </el-tab-pane>

      <!-- 皮肤检测 -->
      <el-tab-pane label="皮肤检测" name="skin">
        <el-card>
          <el-form :model="skinForm" label-width="120px" style="max-width:680px">
            <el-form-item label="图片 URL" required>
              <el-input v-model="skinForm.imageUrl" placeholder="https://..." />
              <el-button size="small" @click="fillSkinTestUrl" style="margin-left:8px">填入测试 URL</el-button>
            </el-form-item>
            <el-form-item label="会员 ID">
              <el-input-number v-model="skinForm.memberId" :min="0" placeholder="可选" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doSkinRecognize" :loading="recognizing">开始识别</el-button>
            </el-form-item>
          </el-form>
          <div v-if="skinResult" class="result-box">
            <h4>识别结果</h4>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="肤质">{{ skinResult.skin?.skinType }}</el-descriptions-item>
              <el-descriptions-item label="综合得分">
                <el-tag :type="(skinResult.skin?.skinScore || 0) >= 80 ? 'success' : (skinResult.skin?.skinScore || 0) >= 60 ? 'warning' : 'danger'">
                  {{ skinResult.skin?.skinScore }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="置信度">{{ (skinResult.confidence * 100).toFixed(1) }}%</el-descriptions-item>
              <el-descriptions-item label="油分">{{ skinResult.skin?.oilLevel }} / 10</el-descriptions-item>
              <el-descriptions-item label="水分">{{ skinResult.skin?.moistureLevel }} / 10</el-descriptions-item>
              <el-descriptions-item label="弹性">{{ skinResult.skin?.elasticityLevel }} / 10</el-descriptions-item>
              <el-descriptions-item label="色素">{{ skinResult.skin?.pigmentLevel }} / 10</el-descriptions-item>
              <el-descriptions-item label="毛孔">{{ skinResult.skin?.poreLevel }} / 10</el-descriptions-item>
              <el-descriptions-item label="皱纹">{{ skinResult.skin?.wrinkleLevel }} / 10</el-descriptions-item>
              <el-descriptions-item label="主要问题" :span="3">
                <el-tag v-for="i in skinResult.skin?.issues" :key="i" type="danger" style="margin-right:6px">{{ i }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="调用延迟">{{ skinResult.latency }} ms</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 人脸识别 -->
      <el-tab-pane label="人脸识别" name="face">
        <el-card>
          <el-form :model="faceForm" label-width="120px" style="max-width:680px">
            <el-form-item label="图片 URL" required>
              <el-input v-model="faceForm.imageUrl" placeholder="https://..." />
              <el-button size="small" @click="fillSkinTestUrl('face')" style="margin-left:8px">填入测试 URL</el-button>
            </el-form-item>
            <el-form-item label="会员 ID">
              <el-input-number v-model="faceForm.memberId" :min="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doFaceRecognize" :loading="faceRecognizing">开始识别</el-button>
            </el-form-item>
          </el-form>
          <div v-if="faceResult" class="result-box">
            <h4>识别结果</h4>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="Face ID">{{ faceResult.faceId }}</el-descriptions-item>
              <el-descriptions-item label="颜值评分">{{ faceResult.beautyScore }}</el-descriptions-item>
              <el-descriptions-item label="置信度">{{ ((faceResult.confidence || 0) * 100).toFixed(1) }}%</el-descriptions-item>
              <el-descriptions-item label="年龄区间">{{ faceResult.ageRange }}</el-descriptions-item>
              <el-descriptions-item label="性别">{{ faceResult.gender }}</el-descriptions-item>
              <el-descriptions-item label="表情">{{ faceResult.expression }}</el-descriptions-item>
              <el-descriptions-item label="特征点">{{ faceResult.landmarks }} 个</el-descriptions-item>
              <el-descriptions-item label="调用延迟" :span="2">{{ faceResult.latency }} ms</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 美甲试戴 -->
      <el-tab-pane label="美甲试戴" name="tryon">
        <el-card>
          <el-form :model="tryonForm" label-width="140px" style="max-width:680px">
            <el-form-item label="手部图片 URL" required>
              <el-input v-model="tryonForm.handImageUrl" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="款式图片 URL" required>
              <el-input v-model="tryonForm.styleImageUrl" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="会员 ID">
              <el-input-number v-model="tryonForm.memberId" :min="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doTryon" :loading="tryonLoading">生成试戴图</el-button>
            </el-form-item>
          </el-form>
          <div v-if="tryonResult" class="result-box">
            <h4>试戴结果</h4>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="结果图 URL" :span="2">
                <el-link :href="tryonResult.resultImageUrl" target="_blank" type="primary">
                  {{ tryonResult.resultImageUrl }}
                </el-link>
              </el-descriptions-item>
              <el-descriptions-item label="置信度">{{ ((tryonResult.confidence || 0) * 100).toFixed(1) }}%</el-descriptions-item>
              <el-descriptions-item label="指甲识别">{{ tryonResult.nailDetected ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="颜色匹配">{{ tryonResult.colorMatched ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="调用延迟">{{ tryonResult.latency }} ms</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 识别日志 -->
      <el-tab-pane label="识别日志" name="logs">
        <el-card>
          <div class="toolbar">
            <el-select v-model="bizFilter" placeholder="业务类型" clearable style="width:160px" @change="loadLogs">
              <el-option label="皮肤检测" value="SKIN_TEST" />
              <el-option label="人脸识别" value="FACE_RECOGNIZE" />
              <el-option label="美甲试戴" value="NAIL_TRYON" />
            </el-select>
          </div>
          <el-table :data="logs" v-loading="loading" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="modelCode" label="模型" width="160" />
            <el-table-column label="业务" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ bizTypeLabel(row.bizType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="imageUrl" label="图片 URL" min-width="240" show-overflow-tooltip />
            <el-table-column prop="confidence" label="置信度" width="100">
              <template #default="{ row }">
                <el-tag :type="(Number(row.confidence) || 0) >= 0.9 ? 'success' : 'warning'" size="small">
                  {{ ((Number(row.confidence) || 0) * 100).toFixed(1) }}%
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="latencyMs" label="延迟" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'SUCCESS' ? '成功' : row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiVisionApi } from '@/api/ai-vision'

const loading = ref(false)
const initing = ref(false)
const recognizing = ref(false)
const faceRecognizing = ref(false)
const tryonLoading = ref(false)
const activeTab = ref('models')

const models = ref<any[]>([])
const logs = ref<any[]>([])
const stats = ref<any>(null)
const bizFilter = ref<string>('')

const skinForm = reactive({
  imageUrl: '',
  memberId: null as number | null
})
const skinResult = ref<any>(null)

const faceForm = reactive({
  imageUrl: '',
  memberId: null as number | null
})
const faceResult = ref<any>(null)

const tryonForm = reactive({
  handImageUrl: '',
  styleImageUrl: '',
  memberId: null as number | null
})
const tryonResult = ref<any>(null)

function bizTypeLabel(t?: string) {
  const m: Record<string, string> = {
    SKIN_TEST: '皮肤检测', FACE_RECOGNIZE: '人脸识别', NAIL_TRYON: '美甲试戴'
  }
  return m[t || ''] || t || '—'
}

function typeLabel(t?: string) {
  const m: Record<string, string> = {
    FACE: '人脸', SKIN: '皮肤', MEDICAL: '医学影像', SCENE: '通用场景'
  }
  return m[t || ''] || t || '—'
}

function fillSkinTestUrl(target = 'skin') {
  const url = `https://cdn.example.com/test/${Date.now()}.jpg`
  if (target === 'skin') skinForm.imageUrl = url
  else faceForm.imageUrl = url
}

async function loadModels() {
  loading.value = true
  try {
    const res: any = await aiVisionApi.listModels()
    models.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params: any = {}
    if (bizFilter.value) params.bizType = bizFilter.value
    const res: any = await aiVisionApi.listLogs(params)
    logs.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res: any = await aiVisionApi.stats()
    stats.value = res.data || null
  } catch {
    stats.value = null
  }
}

function loadAll() {
  loadModels()
  loadLogs()
  loadStats()
}

async function initDefault() {
  initing.value = true
  try {
    const res: any = await aiVisionApi.initDefaultModels()
    ElMessage.success(`已创建 ${res.data?.created || 0} 个默认模型`)
    loadModels()
  } finally {
    initing.value = false
  }
}

async function doSkinRecognize() {
  if (!skinForm.imageUrl) {
    ElMessage.warning('请填写图片 URL')
    return
  }
  recognizing.value = true
  try {
    const payload: any = { imageUrl: skinForm.imageUrl }
    if (skinForm.memberId) payload.memberId = skinForm.memberId
    const res: any = await aiVisionApi.recognizeSkin(payload)
    skinResult.value = res.data?.cvResult
    ElMessage.success(`识别完成：${res.data?.cvResult?.skin?.skinType}，延迟 ${res.data?.latency} ms`)
    loadStats()
    loadLogs()
  } finally {
    recognizing.value = false
  }
}

async function doFaceRecognize() {
  if (!faceForm.imageUrl) {
    ElMessage.warning('请填写图片 URL')
    return
  }
  faceRecognizing.value = true
  try {
    const payload: any = { imageUrl: faceForm.imageUrl }
    if (faceForm.memberId) payload.memberId = faceForm.memberId
    const res: any = await aiVisionApi.recognizeFace(payload)
    faceResult.value = res.data
    ElMessage.success(`识别完成：颜值 ${faceResult.value.beautyScore}`)
    loadStats()
    loadLogs()
  } finally {
    faceRecognizing.value = false
  }
}

async function doTryon() {
  if (!tryonForm.handImageUrl || !tryonForm.styleImageUrl) {
    ElMessage.warning('请填写两张图片 URL')
    return
  }
  tryonLoading.value = true
  try {
    const payload: any = {
      handImageUrl: tryonForm.handImageUrl,
      styleImageUrl: tryonForm.styleImageUrl
    }
    if (tryonForm.memberId) payload.memberId = tryonForm.memberId
    const res: any = await aiVisionApi.nailTryon(payload)
    tryonResult.value = res.data
    ElMessage.success('已生成试戴图')
    loadStats()
    loadLogs()
  } finally {
    tryonLoading.value = false
  }
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.ai-vision-page {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.stat-card {
  text-align: center;
  background: #f7f9fc;
}
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-value {
  color: #303133;
  font-size: 26px;
  font-weight: 600;
  margin-top: 8px;
}
.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.model-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px;
  background: #fff;
  transition: all 0.2s;
}
.model-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}
.model-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.model-head h4 {
  margin: 0;
  font-size: 15px;
}
.model-type {
  color: #909399;
  font-size: 12px;
  margin-bottom: 12px;
}
.model-card .metrics {
  display: flex;
  gap: 16px;
  margin: 8px 0;
}
.model-card .metric {
  display: flex;
  flex-direction: column;
}
.model-card .metric .label {
  color: #909399;
  font-size: 11px;
}
.model-card .metric .value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.model-card .endpoint {
  color: #909399;
  font-size: 11px;
  margin-top: 8px;
  word-break: break-all;
}
.model-card .desc {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
.result-box {
  margin-top: 16px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>