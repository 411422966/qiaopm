<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">配方/护理建议 + 会员日自动化</span>
          <el-space>
            <el-button @click="initRecipes" type="success" plain :loading="initRecipeLoading">初始化配方</el-button>
            <el-button @click="initMemberDays" type="warning" plain :loading="initMdLoading">初始化会员日</el-button>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 配方管理 -->
        <el-tab-pane label="配方/护理方案" name="recipe">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>新建配方</h4>
              <el-form :model="recipeForm" label-width="80px" size="small">
                <el-form-item label="编码"><el-input v-model="recipeForm.recipeCode" /></el-form-item>
                <el-form-item label="名称"><el-input v-model="recipeForm.recipeName" /></el-form-item>
                <el-form-item label="适用肤质">
                  <el-select v-model="recipeForm.skinType" clearable>
                    <el-option label="全部" value="ALL" />
                    <el-option label="干性" value="DRY" />
                    <el-option label="油性" value="OILY" />
                    <el-option label="混合" value="MIXED" />
                    <el-option label="敏感" value="SENSITIVE" />
                    <el-option label="中性" value="NORMAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="针对问题">
                  <el-select v-model="recipeForm.concerns" multiple collapse-tags>
                    <el-option label="美白" value="WHITENING" />
                    <el-option label="抗衰" value="ANTI_AGE" />
                    <el-option label="祛痘" value="ACNE" />
                    <el-option label="保湿" value="MOISTURE" />
                    <el-option label="毛孔" value="PORE" />
                    <el-option label="皱纹" value="WRINKLE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="次数"><el-input-number v-model="recipeForm.sessionCount" :min="1" /></el-form-item>
                <el-form-item label="间隔(天)"><el-input-number v-model="recipeForm.intervalDays" :min="1" /></el-form-item>
                <el-form-item label="预估费用"><el-input-number v-model="recipeForm.estimatedCost" :min="0" /></el-form-item>
                <el-form-item label="季节">
                  <el-select v-model="recipeForm.applicableSeason">
                    <el-option label="全部" value="ALL" />
                    <el-option label="春" value="SPRING" />
                    <el-option label="夏" value="SUMMER" />
                    <el-option label="秋" value="AUTUMN" />
                    <el-option label="冬" value="WINTER" />
                  </el-select>
                </el-form-item>
                <el-form-item label="标签">
                  <el-select v-model="recipeForm.tag">
                    <el-option label="HOT" value="HOT" />
                    <el-option label="NEW" value="NEW" />
                    <el-option label="RECOMMEND" value="RECOMMEND" />
                    <el-option label="SEASONAL" value="SEASONAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="描述">
                  <el-input v-model="recipeForm.description" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item label="预期效果">
                  <el-input v-model="recipeForm.expectedImprovement" type="textarea" :rows="2" />
                </el-form-item>
                <el-button type="primary" @click="createRecipe" :loading="creatingRecipe">创建</el-button>
              </el-form>
            </el-col>

            <el-col :span="15">
              <h4>配方列表</h4>
              <el-form :inline="true" size="small">
                <el-form-item label="肤质">
                  <el-select v-model="filterSkin" clearable size="small" style="width:120px">
                    <el-option v-for="s in ['DRY','OILY','MIXED','SENSITIVE','NORMAL']" :key="s" :label="s" :value="s" />
                  </el-select>
                </el-form-item>
                <el-form-item label="问题">
                  <el-select v-model="filterConcern" clearable size="small" style="width:120px">
                    <el-option v-for="c in ['WHITENING','ANTI_AGE','ACNE','MOISTURE','PORE','WRINKLE']" :key="c" :label="c" :value="c" />
                  </el-select>
                </el-form-item>
                <el-button @click="loadRecipes" size="small">筛选</el-button>
              </el-form>
              <el-table :data="recipes" size="small" stripe max-height="500">
                <el-table-column prop="recipeCode" label="编码" width="160" />
                <el-table-column prop="recipeName" label="名称" />
                <el-table-column prop="skinType" label="肤质" width="80" />
                <el-table-column label="标签" width="80">
                  <template #default="{ row }">
                    <el-tag v-if="row.tag" size="small" :type="tagType(row.tag)">{{ row.tag }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="次数" width="60">
                  <template #default="{ row }">{{ row.sessionCount }}×</template>
                </el-table-column>
                <el-table-column prop="estimatedCost" label="费用" width="80" />
                <el-table-column prop="useCount" label="使用" width="60" />
                <el-table-column label="操作" width="160">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="showRecipeDetail(row.id)">详情</el-button>
                    <el-button size="small" type="success" @click="quickAssess(row)">检测</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 皮肤检测 -->
        <el-tab-pane label="皮肤检测 + 智能推荐" name="assessment">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>提交皮肤检测</h4>
              <el-form :model="assessmentForm" label-width="100px" size="small">
                <el-form-item label="会员ID"><el-input-number v-model="assessmentForm.memberId" /></el-form-item>
                <el-form-item label="肤质">
                  <el-select v-model="assessmentForm.skinType">
                    <el-option v-for="s in ['DRY','OILY','MIXED','SENSITIVE','NORMAL']" :key="s" :label="s" :value="s" />
                  </el-select>
                </el-form-item>
                <el-form-item label="油分(1-10)">
                  <el-slider v-model="assessmentForm.oilLevel" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="水分(1-10)">
                  <el-slider v-model="assessmentForm.moistureLevel" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="弹性(1-10)">
                  <el-slider v-model="assessmentForm.elasticityLevel" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="色素(1-10)">
                  <el-slider v-model="assessmentForm.pigmentLevel" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="毛孔(1-10)">
                  <el-slider v-model="assessmentForm.poreLevel" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="皱纹(1-10)">
                  <el-slider v-model="assessmentForm.wrinkleLevel" :min="1" :max="10" />
                </el-form-item>
                <el-form-item label="问题">
                  <el-select v-model="assessmentForm.concerns" multiple collapse-tags>
                    <el-option v-for="c in ['WHITENING','ANTI_AGE','ACNE','MOISTURE','PORE','WRINKLE']" :key="c" :label="c" :value="c" />
                  </el-select>
                </el-form-item>
                <el-button type="primary" @click="submitAssessment" :loading="submittingAssessment">提交检测</el-button>
                <el-button @click="resetAssessment">重置</el-button>
              </el-form>
            </el-col>

            <el-col :span="14">
              <h4>最新检测结果</h4>
              <div v-if="lastAssessment" class="assessment-result">
                <div class="score-display">
                  <div class="score-circle" :class="scoreClass(lastAssessment.skinScore)">
                    <div class="score-num">{{ lastAssessment.skinScore }}</div>
                    <div class="score-label">综合得分</div>
                  </div>
                </div>
                <el-descriptions :column="2" size="small" border>
                  <el-descriptions-item label="肤质">{{ lastAssessment.skinType }}</el-descriptions-item>
                  <el-descriptions-item label="油分">{{ lastAssessment.oilLevel }}</el-descriptions-item>
                  <el-descriptions-item label="水分">{{ lastAssessment.moistureLevel }}</el-descriptions-item>
                  <el-descriptions-item label="弹性">{{ lastAssessment.elasticityLevel }}</el-descriptions-item>
                  <el-descriptions-item label="色素">{{ lastAssessment.pigmentLevel }}</el-descriptions-item>
                  <el-descriptions-item label="毛孔">{{ lastAssessment.poreLevel }}</el-descriptions-item>
                  <el-descriptions-item label="皱纹">{{ lastAssessment.wrinkleLevel }}</el-descriptions-item>
                  <el-descriptions-item label="问题">{{ lastAssessment.concernsJson }}</el-descriptions-item>
                </el-descriptions>

                <h4 style="margin-top:12px">推荐方案 Top 3</h4>
                <div v-for="r in lastRecommendations" :key="r.id" class="recipe-recommend">
                  <strong>{{ r.recipeName }}</strong>
                  <el-tag size="small" style="margin-left:8px">{{ r.skinType }}</el-tag>
                  <p>{{ r.description }}</p>
                  <span class="cost">¥{{ r.estimatedCost }} · {{ r.sessionCount }}次 · 间隔{{ r.intervalDays }}天</span>
                </div>
              </div>
              <el-empty v-else description="尚未检测，提交后查看结果" />
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 会员日配置 -->
        <el-tab-pane label="会员日配置" name="memberday">
          <el-row :gutter="20">
            <el-col :span="9">
              <h4>新建会员日</h4>
              <el-form :model="mdForm" label-width="100px" size="small">
                <el-form-item label="名称"><el-input v-model="mdForm.configName" /></el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="mdForm.dayType">
                    <el-option label="会员日" value="MEMBER_DAY" />
                    <el-option label="生日特惠" value="BIRTHDAY" />
                    <el-option label="周年庆" value="ANNIVERSARY" />
                    <el-option label="季节性" value="SEASONAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="触发规则"><el-input v-model="mdForm.triggerRule" /></el-form-item>
                <el-form-item label="折扣(0-10)"><el-input-number v-model="mdForm.discountRate" :min="0" :max="10" :precision="1" /></el-form-item>
                <el-form-item label="赠送金额"><el-input-number v-model="mdForm.giftAmount" /></el-form-item>
                <el-form-item label="赠送积分"><el-input-number v-model="mdForm.giftPoints" /></el-form-item>
                <el-form-item label="适用等级">
                  <el-select v-model="mdForm.targetLevel">
                    <el-option label="全部" value="ALL" />
                    <el-option label="普通" value="NORMAL" />
                    <el-option label="银卡" value="SILVER" />
                    <el-option label="金卡" value="GOLD" />
                    <el-option label="钻石" value="DIAMOND" />
                  </el-select>
                </el-form-item>
                <el-form-item label="生日偏移(天)"><el-input-number v-model="mdForm.birthdayOffsetDays" :min="0" /></el-form-item>
                <el-form-item label="短信模板"><el-input v-model="mdForm.smsTemplate" type="textarea" :rows="2" /></el-form-item>
                <el-button type="primary" @click="createMdConfig" :loading="creatingMd">创建</el-button>
              </el-form>
            </el-col>

            <el-col :span="15">
              <h4>会员日列表</h4>
              <el-table :data="mdConfigs" size="small" stripe>
                <el-table-column prop="configName" label="名称" />
                <el-table-column prop="dayType" label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.dayType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="折扣" width="80">
                  <template #default="{ row }">{{ row.discountRate }}折</template>
                </el-table-column>
                <el-table-column label="赠送" width="140">
                  <template #default="{ row }">
                    <span v-if="row.giftAmount">¥{{ row.giftAmount }}</span>
                    <span v-if="row.giftPoints">{{ row.giftPoints }}积分</span>
                  </template>
                </el-table-column>
                <el-table-column prop="targetLevel" label="等级" width="80" />
                <el-table-column label="操作" width="180">
                  <template #default="{ row }">
                    <el-button size="small" type="warning" @click="triggerMd(row.id)">触发</el-button>
                    <el-button size="small" type="danger" @click="deleteMd(row.id)">归档</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <el-button type="success" @click="runDailyTrigger" :loading="runningTrigger" style="margin-top:12px">
                执行今日触发扫描
              </el-button>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 执行记录 + ROI -->
        <el-tab-pane label="执行 + ROI" name="executions">
          <el-row :gutter="20">
            <el-col :span="14">
              <h4>执行记录</h4>
              <el-form :inline="true" size="small">
                <el-form-item label="日期"><el-date-picker v-model="filterDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="filterStatus" clearable size="small" style="width:140px">
                    <el-option label="待发送" value="PENDING" />
                    <el-option label="已发送" value="SENT" />
                    <el-option label="已使用" value="USED" />
                    <el-option label="失败" value="FAILED" />
                  </el-select>
                </el-form-item>
                <el-button @click="loadExecutions" size="small">筛选</el-button>
              </el-form>
              <el-table :data="executions" size="small" stripe max-height="500">
                <el-table-column prop="memberName" label="会员" width="100" />
                <el-table-column prop="memberPhone" label="手机" width="120" />
                <el-table-column prop="triggerReason" label="原因" width="100" />
                <el-table-column prop="triggerDate" label="日期" width="100" />
                <el-table-column prop="giftAmount" label="赠送" width="80" />
                <el-table-column prop="giftPoints" label="积分" width="60" />
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="execStatusType(row.sendStatus)">{{ row.sendStatus }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="couponCode" label="券码" />
              </el-table>
            </el-col>

            <el-col :span="10">
              <h4>ROI 报告</h4>
              <el-form :inline="true" size="small">
                <el-form-item label="周期">
                  <el-select v-model="roiDays" size="small" style="width:120px">
                    <el-option :label="'近7天'" :value="7" />
                    <el-option :label="'近30天'" :value="30" />
                    <el-option :label="'近90天'" :value="90" />
                  </el-select>
                </el-form-item>
                <el-button @click="loadRoi" size="small">查询</el-button>
              </el-form>
              <el-row :gutter="12">
                <el-col :span="12"><el-statistic title="触发" :value="roiData.totalTriggered || 0" /></el-col>
                <el-col :span="12"><el-statistic title="使用" :value="roiData.totalUsed || 0" /></el-col>
              </el-row>
              <el-row :gutter="12" style="margin-top:12px">
                <el-col :span="12"><el-statistic title="转化率" :value="(roiData.conversionRate || 0) + '%'" /></el-col>
                <el-col :span="12"><el-statistic title="营收" :value="(roiData.revenue || 0)" /></el-col>
              </el-row>
              <el-row :gutter="12" style="margin-top:12px">
                <el-col :span="12"><el-statistic title="ROI" :value="roiData.roi || 0" /></el-col>
                <el-col :span="12"><el-statistic title="成本" :value="roiData.giftCost || 0" /></el-col>
              </el-row>

              <h4 style="margin-top:12px">按类型</h4>
              <el-table :data="roiData.byDayType || []" size="small">
                <el-table-column prop="day_type" label="类型" />
                <el-table-column prop="cnt" label="触发" width="80" />
                <el-table-column prop="used_cnt" label="使用" width="80" />
              </el-table>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 即将生日 -->
        <el-tab-pane label="即将生日" name="upcoming">
          <el-table :data="upcoming" size="small" stripe>
            <el-table-column prop="member_name" label="会员" width="120" />
            <el-table-column prop="phone" label="手机" width="140" />
            <el-table-column label="年龄" width="80">
              <template #default="{ row }">{{ row.age }}</template>
            </el-table-column>
            <el-table-column prop="birthday" label="生日" width="120" />
            <el-table-column label="距今" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.days_until === 0" type="danger" size="small">今天</el-tag>
                <el-tag v-else-if="row.days_until <= 3" type="warning" size="small">{{ row.days_until }}天</el-tag>
                <el-tag v-else size="small">{{ row.days_until }}天</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 配方详情对话框 -->
    <el-dialog v-model="recipeDialog" title="配方详情" width="700">
      <div v-if="recipeDetail">
        <h3>{{ recipeDetail.recipe?.recipeName }}</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="编码">{{ recipeDetail.recipe?.recipeCode }}</el-descriptions-item>
          <el-descriptions-item label="肤质">{{ recipeDetail.recipe?.skinType }}</el-descriptions-item>
          <el-descriptions-item label="次数">{{ recipeDetail.recipe?.sessionCount }}</el-descriptions-item>
          <el-descriptions-item label="间隔">{{ recipeDetail.recipe?.intervalDays }}天</el-descriptions-item>
          <el-descriptions-item label="费用">¥{{ recipeDetail.recipe?.estimatedCost }}</el-descriptions-item>
          <el-descriptions-item label="评分">{{ recipeDetail.recipe?.score }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ recipeDetail.recipe?.description }}</el-descriptions-item>
          <el-descriptions-item label="预期效果" :span="2">{{ recipeDetail.recipe?.expectedImprovement }}</el-descriptions-item>
        </el-descriptions>
        <h4>包含步骤</h4>
        <el-table :data="recipeDetail.items || []" size="small">
          <el-table-column prop="sequence" label="#" width="50" />
          <el-table-column prop="itemName" label="项目" />
          <el-table-column prop="itemType" label="类型" width="80" />
          <el-table-column prop="usageQty" label="用量" width="100" />
          <el-table-column prop="estimatedMinutes" label="耗时(分)" width="100" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi } from '@/api/sys'

const activeTab = ref('recipe')

const recipes = ref<any[]>([])
const filterSkin = ref('')
const filterConcern = ref('')
const recipeForm = ref<any>({ recipeCode: '', recipeName: '', skinType: 'ALL', concerns: [], sessionCount: 4, intervalDays: 14, estimatedCost: 0, applicableSeason: 'ALL', tag: '', description: '', expectedImprovement: '' })
const creatingRecipe = ref(false)
const initRecipeLoading = ref(false)
const recipeDialog = ref(false)
const recipeDetail = ref<any>(null)

const assessmentForm = ref<any>({
  memberId: 0, skinType: 'NORMAL',
  oilLevel: 5, moistureLevel: 5, elasticityLevel: 5,
  pigmentLevel: 5, poreLevel: 5, wrinkleLevel: 5,
  concerns: []
})
const submittingAssessment = ref(false)
const lastAssessment = ref<any>(null)
const lastRecommendations = ref<any[]>([])

const mdConfigs = ref<any[]>([])
const mdForm = ref<any>({ configName: '', dayType: 'MEMBER_DAY', triggerRule: '', discountRate: 8, giftAmount: 100, giftPoints: 200, targetLevel: 'ALL', birthdayOffsetDays: 3, smsTemplate: '' })
const creatingMd = ref(false)
const initMdLoading = ref(false)
const runningTrigger = ref(false)

const executions = ref<any[]>([])
const filterDate = ref('')
const filterStatus = ref('')

const roiData = ref<any>({})
const roiDays = ref(30)

const upcoming = ref<any[]>([])

function tagType(tag: string): any {
  if (tag === 'HOT') return 'danger'
  if (tag === 'NEW') return 'success'
  if (tag === 'RECOMMEND') return 'warning'
  if (tag === 'SEASONAL') return 'info'
  return ''
}

function execStatusType(s: string): any {
  if (s === 'SENT') return 'success'
  if (s === 'USED') return 'warning'
  if (s === 'FAILED') return 'danger'
  return 'info'
}

function scoreClass(score: number): string {
  if (score >= 80) return 'score-good'
  if (score >= 60) return 'score-normal'
  return 'score-bad'
}

// ========= 配方 =========
async function initRecipes() {
  initRecipeLoading.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/care/recipe/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个配方`)
    loadRecipes()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    initRecipeLoading.value = false
  }
}

async function createRecipe() {
  if (!recipeForm.value.recipeCode || !recipeForm.value.recipeName) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creatingRecipe.value = true
  try {
    const data = { ...recipeForm.value, concerns: Array.isArray(recipeForm.value.concerns) ? recipeForm.value.concerns.join(',') : recipeForm.value.concerns }
    await sysApi.request({ method: 'POST', url: '/care/recipe', data })
    ElMessage.success('已创建')
    recipeForm.value = { recipeCode: '', recipeName: '', skinType: 'ALL', concerns: [], sessionCount: 4, intervalDays: 14, estimatedCost: 0, applicableSeason: 'ALL', tag: '', description: '', expectedImprovement: '' }
    loadRecipes()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    creatingRecipe.value = false
  }
}

async function loadRecipes() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/care/recipe/list', params: { skinType: filterSkin.value || undefined, concern: filterConcern.value || undefined } })
    recipes.value = r.data || []
  } catch (e) { recipes.value = [] }
}

async function showRecipeDetail(id: number) {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/care/recipe/${id}` })
    recipeDetail.value = r.data
    recipeDialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

function quickAssess(row: any) {
  assessmentForm.value.skinType = row.skinType || 'NORMAL'
  activeTab.value = 'assessment'
}

// ========= 皮肤检测 =========
async function submitAssessment() {
  if (!assessmentForm.value.memberId) {
    ElMessage.warning('请填写会员ID')
    return
  }
  submittingAssessment.value = true
  try {
    const data = { ...assessmentForm.value, concernsJson: JSON.stringify(assessmentForm.value.concerns) }
    const r: any = await sysApi.request({ method: 'POST', url: '/care/assessment', data })
    lastAssessment.value = r.data?.assessment
    const ids = (r.data?.recommendedRecipes || []) as number[]
    if (ids.length > 0) {
      const recipesRes: any = await sysApi.request({ method: 'POST', url: '/care/recommend', data: { skinType: assessmentForm.value.skinType, concerns: assessmentForm.value.concerns.join(',') } })
      lastRecommendations.value = recipesRes.data || []
    }
    ElMessage.success('检测完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    submittingAssessment.value = false
  }
}

function resetAssessment() {
  assessmentForm.value = { memberId: 0, skinType: 'NORMAL', oilLevel: 5, moistureLevel: 5, elasticityLevel: 5, pigmentLevel: 5, poreLevel: 5, wrinkleLevel: 5, concerns: [] }
  lastAssessment.value = null
  lastRecommendations.value = []
}

// ========= 会员日 =========
async function initMemberDays() {
  initMdLoading.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/member-day/config/init-default' })
    ElMessage.success(`已初始化 ${r.data?.created} 个会员日`)
    loadMdConfigs()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    initMdLoading.value = false
  }
}

async function createMdConfig() {
  if (!mdForm.value.configName) {
    ElMessage.warning('请填写名称')
    return
  }
  creatingMd.value = true
  try {
    await sysApi.request({ method: 'POST', url: '/member-day/config', data: mdForm.value })
    ElMessage.success('已创建')
    mdForm.value = { configName: '', dayType: 'MEMBER_DAY', triggerRule: '', discountRate: 8, giftAmount: 100, giftPoints: 200, targetLevel: 'ALL', birthdayOffsetDays: 3, smsTemplate: '' }
    loadMdConfigs()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    creatingMd.value = false
  }
}

async function loadMdConfigs() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/member-day/config/list' })
    mdConfigs.value = r.data || []
  } catch (e) { mdConfigs.value = [] }
}

async function triggerMd(id: number) {
  try {
    const r: any = await sysApi.request({ method: 'POST', url: `/member-day/trigger-by-config/${id}`, data: {} })
    ElMessage.success(`已发送 ${r.data?.sent} 条`)
    loadExecutions()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

async function deleteMd(id: number) {
  await sysApi.request({ method: 'POST', url: `/member-day/config/${id}/delete` })
  ElMessage.success('已归档')
  loadMdConfigs()
}

async function runDailyTrigger() {
  runningTrigger.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/member-day/trigger', data: {} })
    ElMessage.success(`完成：发送 ${r.data?.sent}，跳过 ${r.data?.skipped}`)
    loadExecutions()
    loadRoi()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  } finally {
    runningTrigger.value = false
  }
}

// ========= 执行 + ROI =========
async function loadExecutions() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/member-day/executions', params: { date: filterDate.value || undefined, status: filterStatus.value || undefined } })
    executions.value = r.data || []
  } catch (e) { executions.value = [] }
}

async function loadRoi() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/member-day/roi', params: { days: roiDays.value } })
    roiData.value = r.data || {}
  } catch (e) { roiData.value = {} }
}

async function loadUpcoming() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/member-day/upcoming', params: { days: 7 } })
    upcoming.value = r.data || []
  } catch (e) { upcoming.value = [] }
}

function loadAll() {
  loadRecipes()
  loadMdConfigs()
  loadExecutions()
  loadRoi()
  loadUpcoming()
}

onMounted(() => loadAll())
</script>

<style scoped>
.page { padding: 16px; max-width: 1400px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.assessment-result { padding: 12px; background: #f5f7fa; border-radius: 8px }
.score-display { text-align: center; padding: 16px }
.score-circle {
  display: inline-block; width: 100px; height: 100px; border-radius: 50%;
  text-align: center; line-height: 1.2; padding-top: 20px; color: #fff;
}
.score-good { background: linear-gradient(135deg, #67c23a, #95d475) }
.score-normal { background: linear-gradient(135deg, #e6a23c, #eebe77) }
.score-bad { background: linear-gradient(135deg, #f56c6c, #fab6b6) }
.score-num { font-size: 28px; font-weight: bold }
.score-label { font-size: 11px; opacity: 0.9 }
.recipe-recommend { padding: 8px 12px; margin: 4px 0; background: #fff; border-left: 3px solid #409eff; border-radius: 4px }
.recipe-recommend p { font-size: 12px; color: #909399; margin: 4px 0 }
.cost { font-size: 12px; color: #f56c6c }
</style>