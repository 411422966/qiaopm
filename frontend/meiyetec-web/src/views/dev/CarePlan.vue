<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">护肤方案推荐 · 多人共享套餐</span>
          <el-space>
            <el-button @click="loadAll" type="primary" plain>刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 肤质档案 -->
        <el-tab-pane label="肤质档案" name="skin">
          <el-form :model="skinForm" label-width="120px" size="small" style="max-width:800px">
            <el-form-item label="会员ID">
              <el-input v-model.number="skinForm.memberId" />
            </el-form-item>
            <el-form-item label="评估日期">
              <el-date-picker v-model="skinForm.assessDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="肤质类型">
              <el-select v-model="skinForm.skinType">
                <el-option label="自动推断" value="" />
                <el-option label="干性" value="DRY" />
                <el-option label="油性" value="OILY" />
                <el-option label="混合性" value="COMBINATION" />
                <el-option label="敏感性" value="SENSITIVE" />
                <el-option label="中性" value="NORMAL" />
              </el-select>
            </el-form-item>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="油分 (0-10)"><el-input-number v-model="skinForm.oilLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="水分 (0-10)"><el-input-number v-model="skinForm.moistureLevel" :min="0" :max="10" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="弹性 (0-10)"><el-input-number v-model="skinForm.elasticityLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="色沉 (0-10)"><el-input-number v-model="skinForm.pigmentationLevel" :min="0" :max="10" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="敏感 (0-10)"><el-input-number v-model="skinForm.sensitivityLevel" :min="0" :max="10" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="皱纹 (0-10)"><el-input-number v-model="skinForm.wrinkleLevel" :min="0" :max="10" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="关注问题">
              <el-checkbox-group v-model="skinForm.concernsList">
                <el-checkbox label="acne">痘痘</el-checkbox>
                <el-checkbox label="pigmentation">色斑</el-checkbox>
                <el-checkbox label="wrinkle">皱纹</el-checkbox>
                <el-checkbox label="dryness">干燥</el-checkbox>
                <el-checkbox label="sensitivity">敏感</el-checkbox>
                <el-checkbox label="dullness">暗沉</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="skinForm.remark" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSkin" :loading="saving">保存档案</el-button>
              <el-button type="success" @click="recommendPlan" :loading="recommending" :disabled="!skinForm.memberId">生成护肤方案</el-button>
            </el-form-item>
          </el-form>

          <el-divider>历史档案</el-divider>
          <el-table :data="skinHistory" size="small" stripe max-height="200">
            <el-table-column prop="assessDate" label="评估日期" width="120" />
            <el-table-column prop="skinType" label="肤质" width="100" />
            <el-table-column label="油/水/敏/皱" width="140">
              <template #default="{ row }">
                {{ row.oilLevel }}/{{ row.moistureLevel }}/{{ row.sensitivityLevel }}/{{ row.wrinkleLevel }}
              </template>
            </el-table-column>
            <el-table-column prop="concerns" label="关注问题" />
          </el-table>
        </el-tab-pane>

        <!-- 护肤方案推荐 -->
        <el-tab-pane label="护肤方案" name="plan">
          <div v-if="planResult">
            <el-alert type="success" :closable="false" style="margin-bottom:12px">
              <template #title>{{ planResult.plan.planName }} — {{ planResult.plan.goal }}</template>
            </el-alert>

            <el-descriptions :column="4" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="肤质">{{ planResult.skinType }}</el-descriptions-item>
              <el-descriptions-item label="年龄段">{{ planResult.ageGroup }}</el-descriptions-item>
              <el-descriptions-item label="总次数">{{ planResult.plan.sessionsTotal }}</el-descriptions-item>
              <el-descriptions-item label="周频次">{{ planResult.plan.sessionsPerWeek }}</el-descriptions-item>
              <el-descriptions-item label="预估费用">¥{{ planResult.plan.estimatedCost }}</el-descriptions-item>
              <el-descriptions-item label="周期">{{ planResult.plan.cycleDays }} 天</el-descriptions-item>
              <el-descriptions-item label="开始日期">{{ planResult.plan.startDate }}</el-descriptions-item>
              <el-descriptions-item label="结束日期">{{ planResult.plan.endDate }}</el-descriptions-item>
            </el-descriptions>

            <h4>推荐理由</h4>
            <ul>
              <li v-for="r in planResult.reasons" :key="r">{{ r }}</li>
            </ul>

            <h4 style="margin-top:16px">推荐服务（{{ planResult.services.length }}）</h4>
            <el-table :data="planResult.services" size="small" stripe>
              <el-table-column prop="name" label="服务名称" />
              <el-table-column prop="categoryName" label="类别" width="120" />
              <el-table-column prop="memberPrice" label="会员价" width="100" />
              <el-table-column prop="duration" label="时长(分)" width="100" />
            </el-table>

            <h4 style="margin-top:16px">护理步骤</h4>
            <el-steps :active="planResult.steps.length" finish-status="success" direction="vertical">
              <el-step v-for="s in planResult.steps" :key="s.step" :title="s.serviceName" :description="s.purpose + ' · ' + s.frequency" />
            </el-steps>

            <el-divider />
            <el-button type="primary" @click="convertToShared">转化为共享套餐</el-button>
          </div>
          <el-empty v-else description="请先录入肤质档案并点击「生成护肤方案」" />
        </el-tab-pane>

        <!-- 共享套餐 -->
        <el-tab-pane label="共享套餐" name="shared">
          <el-row :gutter="20">
            <el-col :span="10">
              <h4>创建共享套餐</h4>
              <el-form :model="sharedForm" label-width="120px" size="small">
                <el-form-item label="主持有人ID">
                  <el-input v-model.number="sharedForm.ownerMemberId" />
                </el-form-item>
                <el-form-item label="共享人数">
                  <el-radio-group v-model="sharedForm.shareMemberLimit">
                    <el-radio-button :value="2">2人</el-radio-button>
                    <el-radio-button :value="3">3人</el-radio-button>
                    <el-radio-button :value="4">4人</el-radio-button>
                    <el-radio-button :value="5">5人</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="原总价">
                  <el-input v-model.number="sharedForm.originalPrice" />
                </el-form-item>
                <el-form-item label="总次数">
                  <el-input-number v-model="sharedForm.totalSessions" :min="1" />
                </el-form-item>
                <el-button type="primary" @click="createShared" :loading="creatingShared">创建</el-button>
              </el-form>

              <el-divider />
              <h4>我的共享套餐</h4>
              <el-input v-model.number="myMemberId" placeholder="会员ID" size="small" style="width:160px;margin-right:8px" />
              <el-button @click="loadMyShared" size="small">查询</el-button>
              <el-table :data="myShared" size="small" stripe style="margin-top:8px" max-height="300">
                <el-table-column prop="packageCode" label="编码" width="180" />
                <el-table-column prop="packageName" label="名称" />
                <el-table-column label="使用进度" width="140">
                  <template #default="{ row }">
                    <el-progress
                      :percentage="row.totalSessions > 0 ? Math.round(row.usedSessions * 100 / row.totalSessions) : 0"
                      :stroke-width="14"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80">
                  <template #default="{ row }">
                    <el-button size="small" @click="viewDetail(row.id)">详情</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>

            <el-col :span="14">
              <h4>套餐详情</h4>
              <div v-if="currentPkg">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="编码">{{ currentPkg.package.packageCode }}</el-descriptions-item>
                  <el-descriptions-item label="状态">
                    <el-tag :type="currentPkg.package.status === 'ACTIVE' ? 'success' : 'info'">{{ currentPkg.package.status }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="共享价">¥{{ currentPkg.package.sharedPrice }}</el-descriptions-item>
                  <el-descriptions-item label="人均价">¥{{ currentPkg.package.perMemberPrice }}</el-descriptions-item>
                  <el-descriptions-item label="总/已用次数">{{ currentPkg.package.usedSessions }} / {{ currentPkg.package.totalSessions }}</el-descriptions-item>
                  <el-descriptions-item label="利用率">{{ currentPkg.utilizationRate }}%</el-descriptions-item>
                  <el-descriptions-item label="有效期">{{ currentPkg.package.validStartDate }} ~ {{ currentPkg.package.validEndDate }}</el-descriptions-item>
                </el-descriptions>

                <h4 style="margin-top:16px">共享成员（{{ currentPkg.members.length }}）</h4>
                <el-table :data="currentPkg.members" size="small" stripe>
                  <el-table-column label="成员" prop="relationship" />
                  <el-table-column label="已用次数" prop="usedSessions" width="100" />
                  <el-table-column label="加入方式" prop="joinMethod" width="100" />
                  <el-table-column label="状态" prop="status" width="100" />
                </el-table>

                <h4 style="margin-top:16px">邀请新成员</h4>
                <el-space>
                  <el-input v-model="invitePhone" placeholder="受邀人手机号" size="small" style="width:180px" />
                  <el-input v-model="inviteName" placeholder="受邀人姓名" size="small" style="width:120px" />
                  <el-button type="primary" size="small" @click="createInvite" :loading="creatingInvite">生成邀请码</el-button>
                </el-space>
                <el-alert v-if="currentInvite" type="info" :closable="false" style="margin-top:8px">
                  <template #title>
                    邀请码：<b>{{ currentInvite.inviteCode }}</b>
                    <el-button size="small" plain @click="copyCode" style="margin-left:12px">复制</el-button>
                  </template>
                </el-alert>

                <h4 style="margin-top:16px">使用记录</h4>
                <el-table :data="currentPkg.usages" size="small" stripe max-height="200">
                  <el-table-column prop="usageDate" label="使用时间" width="170" />
                  <el-table-column prop="memberId" label="会员ID" width="100" />
                  <el-table-column prop="serviceId" label="服务ID" width="100" />
                  <el-table-column prop="sessionsUsed" label="扣减" width="80" />
                </el-table>
              </div>
              <el-empty v-else description="请选择套餐查看详情" />
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 概览统计 -->
        <el-tab-pane label="概览统计" name="overview">
          <el-row :gutter="20">
            <el-col :span="6"><el-statistic title="共享套餐总数" :value="overview.totalPackages || 0" /></el-col>
            <el-col :span="6"><el-statistic title="活跃套餐" :value="overview.activePackages || 0" :value-style="{ color: '#67c23a' }" /></el-col>
            <el-col :span="6"><el-statistic title="已耗尽套餐" :value="overview.depletedPackages || 0" /></el-col>
            <el-col :span="6"><el-statistic title="总营收(¥)" :value="overview.totalRevenue || 0" :precision="2" /></el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi } from '@/api/sys'

const activeTab = ref('skin')
const skinForm = ref<any>({
  memberId: null,
  assessDate: new Date().toISOString().slice(0, 10),
  skinType: '',
  oilLevel: 5,
  moistureLevel: 5,
  elasticityLevel: 5,
  pigmentationLevel: 5,
  sensitivityLevel: 5,
  wrinkleLevel: 0,
  concernsList: [],
  remark: ''
})
const saving = ref(false)
const recommending = ref(false)
const skinHistory = ref<any[]>([])
const planResult = ref<any>(null)

const sharedForm = ref<any>({
  ownerMemberId: null,
  shareMemberLimit: 2,
  originalPrice: 5000,
  totalSessions: 10
})
const creatingShared = ref(false)
const myMemberId = ref<number | null>(null)
const myShared = ref<any[]>([])
const currentPkg = ref<any>(null)
const invitePhone = ref('')
const inviteName = ref('')
const currentInvite = ref<any>(null)
const creatingInvite = ref(false)
const overview = ref<any>({})

async function saveSkin() {
  if (!skinForm.value.memberId) {
    ElMessage.warning('请填写会员ID')
    return
  }
  saving.value = true
  try {
    const body = { ...skinForm.value, concerns: JSON.stringify(skinForm.value.concernsList) }
    delete body.concernsList
    const r: any = await sysApi.request({ method: 'POST', url: '/care/plan/skin/save', data: body })
    ElMessage.success('已保存')
    loadSkinHistory()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

async function loadSkinHistory() {
  if (!skinForm.value.memberId) return
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/care/plan/skin/${skinForm.value.memberId}` })
    skinHistory.value = r.data || []
  } catch (e) {
    skinHistory.value = []
  }
}

async function recommendPlan() {
  if (!skinForm.value.memberId) {
    ElMessage.warning('请填写会员ID')
    return
  }
  recommending.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/care/plan/recommend', data: { memberId: skinForm.value.memberId } })
    planResult.value = r.data
    activeTab.value = 'plan'
    ElMessage.success('方案已生成')
  } catch (e: any) {
    ElMessage.error('推荐失败: ' + (e?.message || ''))
  } finally {
    recommending.value = false
  }
}

function convertToShared() {
  if (!planResult.value) return
  sharedForm.value.totalSessions = planResult.value.plan.sessionsTotal
  sharedForm.value.originalPrice = Number(planResult.value.plan.estimatedCost)
  activeTab.value = 'shared'
}

async function createShared() {
  if (!sharedForm.value.ownerMemberId) {
    ElMessage.warning('请填写主持有人ID')
    return
  }
  creatingShared.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/shared/package/create', data: sharedForm.value })
    ElMessage.success('已创建，共享折扣: ' + (r.data?.sharedDiscount || ''))
    loadMyShared()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.message || ''))
  } finally {
    creatingShared.value = false
  }
}

async function loadMyShared() {
  if (!myMemberId.value) return
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/shared/package/list/my/${myMemberId.value}` })
    myShared.value = r.data || []
  } catch (e) {
    myShared.value = []
  }
}

async function viewDetail(id: number) {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: `/shared/package/detail/${id}` })
    currentPkg.value = r.data
    currentInvite.value = null
  } catch (e: any) {
    ElMessage.error('加载失败')
  }
}

async function createInvite() {
  if (!currentPkg.value || !invitePhone.value) {
    ElMessage.warning('请填写受邀人手机号')
    return
  }
  creatingInvite.value = true
  try {
    const r: any = await sysApi.request({ method: 'POST', url: '/shared/package/invite/create', data: {
      packageId: currentPkg.value.package.id,
      inviterId: currentPkg.value.package.ownerMemberId,
      inviteePhone: invitePhone.value,
      inviteeName: inviteName.value
    }})
    currentInvite.value = r.data.invite
    ElMessage.success('邀请码已生成，7 天内有效')
  } catch (e: any) {
    ElMessage.error('生成失败: ' + (e?.message || ''))
  } finally {
    creatingInvite.value = false
  }
}

function copyCode() {
  if (currentInvite.value?.inviteCode) {
    navigator.clipboard.writeText(currentInvite.value.inviteCode)
    ElMessage.success('已复制')
  }
}

async function loadOverview() {
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/shared/package/overview' })
    overview.value = r.data || {}
  } catch (e) {
    overview.value = {}
  }
}

function loadAll() {
  loadSkinHistory()
  loadMyShared()
  loadOverview()
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.page { padding: 16px; max-width: 1200px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
ul { padding-left: 20px; color: #606266 }
</style>