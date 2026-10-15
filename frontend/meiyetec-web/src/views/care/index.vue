<template>
  <div class="care-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">配方 / 护理建议</span>
          <el-space>
            <el-button type="primary" @click="initDefault" :loading="initing">
              初始化默认配方
            </el-button>
            <el-button @click="loadAll" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <div class="filters">
        <el-select v-model="filter.skinType" placeholder="肤质" clearable style="width:140px" @change="loadRecipes">
          <el-option label="全部肤质" value="ALL" />
          <el-option label="油性" value="OILY" />
          <el-option label="干性" value="DRY" />
          <el-option label="混合性" value="COMBINATION" />
          <el-option label="敏感性" value="SENSITIVE" />
          <el-option label="熟龄肌" value="MATURE" />
        </el-select>
        <el-select v-model="filter.concern" placeholder="肌肤问题" clearable style="width:160px" @change="loadRecipes">
          <el-option label="美白" value="WHITENING" />
          <el-option label="抗衰" value="ANTI_AGE" />
          <el-option label="祛痘" value="ACNE" />
          <el-option label="补水" value="MOISTURE" />
          <el-option label="毛孔" value="PORE" />
          <el-option label="皱纹" value="WRINKLE" />
          <el-option label="泛红" value="REDNESS" />
        </el-select>
        <el-input v-model="filter.memberId" placeholder="会员 ID（检测/推荐）" style="width:200px" clearable />
        <el-button type="primary" @click="loadHistory" :disabled="!filter.memberId">查看检测历史</el-button>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="care-tabs">
      <!-- 配方库 -->
      <el-tab-pane label="配方库" name="recipes">
        <el-card>
          <div class="toolbar">
            <el-button type="primary" @click="openRecipeDialog()">+ 新建配方</el-button>
            <span class="hint">共 {{ recipes.length }} 个配方</span>
          </div>
          <el-table :data="recipes" v-loading="loading" border stripe>
            <el-table-column prop="recipeCode" label="编码" width="160" />
            <el-table-column prop="recipeName" label="名称" min-width="180" />
            <el-table-column label="适用肤质" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ skinLabel(row.skinType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="针对问题" min-width="160">
              <template #default="{ row }">
                <el-tag v-for="c in (row.concerns || '').split(',')" :key="c" size="small" style="margin-right:4px">
                  {{ concernLabel(c) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="疗程" width="120">
              <template #default="{ row }">
                {{ row.sessionCount }} 次 / {{ row.intervalDays }} 天
              </template>
            </el-table-column>
            <el-table-column label="价格" width="100">
              <template #default="{ row }">¥{{ row.estimatedCost || 0 }}</template>
            </el-table-column>
            <el-table-column label="评分" width="80">
              <template #default="{ row }">
                <el-tag :type="(row.score || 0) >= 80 ? 'success' : 'warning'" size="small">
                  {{ row.score || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="使用次数" width="100">
              <template #default="{ row }">{{ row.useCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openRecipeDetail(row)">详情</el-button>
                <el-button link size="small" @click="openRecipeDialog(row)">编辑</el-button>
                <el-button link size="small" type="primary" @click="quickRecommend(row)">
                  推荐
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 皮肤检测 -->
      <el-tab-pane label="皮肤检测" name="assessment">
        <el-card>
          <el-form :model="assessmentForm" label-width="100px" class="assess-form">
            <el-form-item label="会员 ID" required>
              <el-input-number v-model="assessmentForm.memberId" :min="1" />
            </el-form-item>
            <el-form-item label="肤质" required>
              <el-select v-model="assessmentForm.skinType" placeholder="选择肤质">
                <el-option label="油性" value="OILY" />
                <el-option label="干性" value="DRY" />
                <el-option label="混合性" value="COMBINATION" />
                <el-option label="敏感性" value="SENSITIVE" />
                <el-option label="熟龄肌" value="MATURE" />
              </el-select>
            </el-form-item>
            <el-form-item label="问题清单">
              <el-input v-model="assessmentForm.concernsText" placeholder="如：ACNE,PORE（逗号分隔）" />
            </el-form-item>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="油分 (0-10)"><el-input-number v-model="assessmentForm.oilLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="水分 (0-10)"><el-input-number v-model="assessmentForm.moistureLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="弹性 (0-10)"><el-input-number v-model="assessmentForm.elasticityLevel" :min="0" :max="10" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="色素 (0-10)"><el-input-number v-model="assessmentForm.pigmentLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="毛孔 (0-10)"><el-input-number v-model="assessmentForm.poreLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="皱纹 (0-10)"><el-input-number v-model="assessmentForm.wrinkleLevel" :min="0" :max="10" /></el-form-item></el-col>
            </el-row>
            <el-form-item>
              <el-button type="primary" @click="submitAssessment" :loading="assessing">提交检测</el-button>
              <el-button @click="resetAssessmentForm">重置</el-button>
            </el-form-item>
          </el-form>

          <el-divider />
          <h4>会员检测历史</h4>
          <el-table :data="assessmentHistory" v-loading="loading" border stripe>
            <el-table-column prop="assessmentNo" label="检测号" width="180" />
            <el-table-column prop="assessmentDate" label="检测日期" width="170" />
            <el-table-column label="肤质" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ skinLabel(row.skinType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="综合得分" width="100">
              <template #default="{ row }">
                <el-tag :type="(row.skinScore || 0) >= 80 ? 'success' : (row.skinScore || 0) >= 60 ? 'warning' : 'danger'" size="small">
                  {{ row.skinScore || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="推荐配方" min-width="200">
              <template #default="{ row }">
                <span v-if="row.recommendedRecipeId">
                  主推 ID #{{ row.recommendedRecipeId }}，详见分析
                </span>
                <span v-else style="color:#909399">未匹配</span>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="100" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 智能推荐 -->
      <el-tab-pane label="智能推荐" name="recommend">
        <el-card>
          <div class="toolbar">
            <el-select v-model="recForm.skinType" placeholder="肤质" style="width:140px">
              <el-option label="油性" value="OILY" />
              <el-option label="干性" value="DRY" />
              <el-option label="混合性" value="COMBINATION" />
              <el-option label="敏感性" value="SENSITIVE" />
              <el-option label="熟龄肌" value="MATURE" />
            </el-select>
            <el-input v-model="recForm.concernsText" placeholder="问题代码（逗号分隔）" style="width:280px" />
            <el-button type="primary" @click="runRecommend" :loading="recLoading">生成推荐</el-button>
          </div>
          <el-empty v-if="!recResults.length" description="选择肤质和问题后点击生成推荐" />
          <el-row v-else :gutter="16">
            <el-col v-for="r in recResults" :key="r.id" :span="8" style="margin-bottom:16px">
              <el-card shadow="hover">
                <div class="rec-card">
                  <h4>{{ r.recipeName }}</h4>
                  <div class="meta">
                    <el-tag size="small" type="info">{{ skinLabel(r.skinType) }}</el-tag>
                    <el-tag size="small">评分 {{ r.score }}</el-tag>
                    <el-tag size="small" type="warning">使用 {{ r.useCount || 0 }} 次</el-tag>
                  </div>
                  <p class="desc">{{ r.description }}</p>
                  <div class="info">
                    <span>疗程：{{ r.sessionCount }} 次 / {{ r.intervalDays }} 天</span>
                    <span class="price">¥{{ r.estimatedCost || 0 }}</span>
                  </div>
                  <div class="improve">预期改善：{{ r.expectedImprovement }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 配方详情 -->
    <el-dialog v-model="recipeDetail.visible" :title="recipeDetail.title" width="720px">
      <div v-if="recipeDetail.data">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="编码">{{ recipeDetail.data.recipe.recipeCode }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ recipeDetail.data.recipe.recipeName }}</el-descriptions-item>
          <el-descriptions-item label="适用肤质">{{ skinLabel(recipeDetail.data.recipe.skinType) }}</el-descriptions-item>
          <el-descriptions-item label="针对问题">{{ recipeDetail.data.recipe.concerns }}</el-descriptions-item>
          <el-descriptions-item label="疗程">{{ recipeDetail.data.recipe.sessionCount }} 次 / {{ recipeDetail.data.recipe.intervalDays }} 天</el-descriptions-item>
          <el-descriptions-item label="价格">¥{{ recipeDetail.data.recipe.estimatedCost || 0 }}</el-descriptions-item>
          <el-descriptions-item label="适用季节" :span="2">{{ recipeDetail.data.recipe.applicableSeason }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ recipeDetail.data.recipe.description }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top:16px">步骤（{{ (recipeDetail.data.items || []).length }}）</h4>
        <el-table :data="recipeDetail.data.items || []" border>
          <el-table-column prop="sequence" label="顺序" width="80" />
          <el-table-column prop="itemName" label="步骤名称" />
          <el-table-column prop="itemType" label="类型" width="100" />
          <el-table-column prop="duration" label="时长(分钟)" width="120" />
          <el-table-column prop="notes" label="备注" />
        </el-table>
      </div>
    </el-dialog>

    <!-- 配方编辑 -->
    <el-dialog v-model="recipeDialog.visible" :title="recipeDialog.id ? '编辑配方' : '新建配方'" width="640px">
      <el-form :model="recipeDialog.form" label-width="100px">
        <el-form-item label="编码" required>
          <el-input v-model="recipeDialog.form.recipeCode" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="recipeDialog.form.recipeName" />
        </el-form-item>
        <el-form-item label="适用肤质">
          <el-select v-model="recipeDialog.form.skinType" style="width:100%">
            <el-option label="全部" value="ALL" />
            <el-option label="油性" value="OILY" />
            <el-option label="干性" value="DRY" />
            <el-option label="混合性" value="COMBINATION" />
            <el-option label="敏感性" value="SENSITIVE" />
            <el-option label="熟龄肌" value="MATURE" />
          </el-select>
        </el-form-item>
        <el-form-item label="针对问题">
          <el-input v-model="recipeDialog.form.concerns" placeholder="如：WHITENING,ANTI_AGE" />
        </el-form-item>
        <el-form-item label="疗程">
          <el-input-number v-model="recipeDialog.form.sessionCount" :min="1" />
        </el-form-item>
        <el-form-item label="间隔(天)">
          <el-input-number v-model="recipeDialog.form.intervalDays" :min="1" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="recipeDialog.form.estimatedCost" :min="0" />
        </el-form-item>
        <el-form-item label="评分">
          <el-input-number v-model="recipeDialog.form.score" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="recipeDialog.form.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="INACTIVE">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="recipeDialog.form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recipeDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRecipe" :loading="recipeDialog.saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { careApi } from '@/api/care'

const loading = ref(false)
const initing = ref(false)
const assessing = ref(false)
const recLoading = ref(false)
const activeTab = ref('recipes')

const recipes = ref<any[]>([])
const assessmentHistory = ref<any[]>([])
const recResults = ref<any[]>([])

const filter = reactive({
  skinType: '' as string,
  concern: '' as string,
  memberId: '' as string
})

const assessmentForm = reactive({
  memberId: null as number | null,
  skinType: 'OILY',
  concernsText: '',
  oilLevel: 5,
  moistureLevel: 5,
  elasticityLevel: 5,
  pigmentLevel: 5,
  poreLevel: 5,
  wrinkleLevel: 5
})

const recForm = reactive({
  skinType: 'OILY',
  concernsText: 'ACNE'
})

const recipeDialog = reactive({
  visible: false,
  id: null as number | null,
  saving: false,
  form: {} as any
})

const recipeDetail = reactive({
  visible: false,
  title: '',
  data: null as any
})

function skinLabel(t?: string) {
  const m: Record<string, string> = {
    ALL: '全部', OILY: '油性', DRY: '干性', COMBINATION: '混合性',
    SENSITIVE: '敏感性', MATURE: '熟龄肌'
  }
  return m[t || ''] || t || '—'
}

function concernLabel(c?: string) {
  const m: Record<string, string> = {
    WHITENING: '美白', ANTI_AGE: '抗衰', ACNE: '祛痘', MOISTURE: '补水',
    PORE: '毛孔', WRINKLE: '皱纹', REDNESS: '泛红', DULL: '暗沉'
  }
  return m[c?.trim() || ''] || c
}

async function loadRecipes() {
  loading.value = true
  try {
    const params: any = {}
    if (filter.skinType) params.skinType = filter.skinType
    if (filter.concern) params.concern = filter.concern
    const res: any = await careApi.listRecipes(params)
    recipes.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!filter.memberId) return
  loading.value = true
  try {
    const res: any = await careApi.historyByMember(Number(filter.memberId))
    assessmentHistory.value = res.data || []
  } finally {
    loading.value = false
  }
}

function loadAll() {
  loadRecipes()
}

async function initDefault() {
  initing.value = true
  try {
    const res: any = await careApi.initDefaultRecipes()
    ElMessage.success(`已创建 ${res.data?.created || 0} 个默认配方`)
    loadRecipes()
  } finally {
    initing.value = false
  }
}

function openRecipeDialog(row?: any) {
  if (row) {
    recipeDialog.id = row.id
    recipeDialog.form = { ...row }
  } else {
    recipeDialog.id = null
    recipeDialog.form = {
      recipeCode: '',
      recipeName: '',
      skinType: 'ALL',
      concerns: '',
      sessionCount: 4,
      intervalDays: 14,
      estimatedCost: 0,
      score: 80,
      status: 'ACTIVE',
      description: ''
    }
  }
  recipeDialog.visible = true
}

async function submitRecipe() {
  if (!recipeDialog.form.recipeCode || !recipeDialog.form.recipeName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  recipeDialog.saving = true
  try {
    if (recipeDialog.id) {
      await careApi.updateRecipe(recipeDialog.id, recipeDialog.form)
      ElMessage.success('已更新')
    } else {
      await careApi.createRecipe(recipeDialog.form)
      ElMessage.success('已创建')
    }
    recipeDialog.visible = false
    loadRecipes()
  } finally {
    recipeDialog.saving = false
  }
}

async function openRecipeDetail(row: any) {
  const res: any = await careApi.getRecipe(row.id)
  recipeDetail.title = `${row.recipeName} - 详情`
  recipeDetail.data = res.data
  recipeDetail.visible = true
}

async function quickRecommend(row: any) {
  recForm.skinType = row.skinType && row.skinType !== 'ALL' ? row.skinType : 'OILY'
  recForm.concernsText = row.concerns || ''
  activeTab.value = 'recommend'
  await runRecommend()
}

async function runRecommend() {
  recLoading.value = true
  try {
    const res: any = await careApi.recommend({
      skinType: recForm.skinType,
      concerns: recForm.concernsText
    })
    recResults.value = res.data || []
  } finally {
    recLoading.value = false
  }
}

async function submitAssessment() {
  if (!assessmentForm.memberId) {
    ElMessage.warning('请填写会员 ID')
    return
  }
  assessing.value = true
  try {
    const payload: any = {
      memberId: assessmentForm.memberId,
      skinType: assessmentForm.skinType,
      concernsJson: assessmentForm.concernsText ? `[${assessmentForm.concernsText}]` : '[]',
      oilLevel: assessmentForm.oilLevel,
      moistureLevel: assessmentForm.moistureLevel,
      elasticityLevel: assessmentForm.elasticityLevel,
      pigmentLevel: assessmentForm.pigmentLevel,
      poreLevel: assessmentForm.poreLevel,
      wrinkleLevel: assessmentForm.wrinkleLevel
    }
    const res: any = await careApi.submitAssessment(payload)
    ElMessage.success(`检测完成，综合得分 ${res.data?.assessment?.skinScore}`)
    filter.memberId = String(assessmentForm.memberId)
    await loadHistory()
    activeTab.value = 'assessment'
  } finally {
    assessing.value = false
  }
}

function resetAssessmentForm() {
  assessmentForm.memberId = null
  assessmentForm.skinType = 'OILY'
  assessmentForm.concernsText = ''
  assessmentForm.oilLevel = 5
  assessmentForm.moistureLevel = 5
  assessmentForm.elasticityLevel = 5
  assessmentForm.pigmentLevel = 5
  assessmentForm.poreLevel = 5
  assessmentForm.wrinkleLevel = 5
}

onMounted(() => {
  loadRecipes()
})
</script>

<style scoped>
.care-page {
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
.filters {
  display: flex;
  gap: 8px;
  align-items: center;
}
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar .hint {
  color: #909399;
  font-size: 13px;
  margin-left: auto;
}
.rec-card h4 {
  margin: 0 0 8px 0;
}
.rec-card .meta {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.rec-card .desc {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  min-height: 40px;
}
.rec-card .info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.rec-card .price {
  color: #f56c6c;
  font-weight: 600;
  font-size: 16px;
}
.rec-card .improve {
  margin-top: 6px;
  font-size: 12px;
  color: #67c23a;
}
.assess-form {
  max-width: 720px;
}
</style>