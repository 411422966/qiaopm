<template>
  <div class="care-suggestion-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="建议生成" name="generate" />
      <el-tab-pane label="模板管理" name="template" />
      <el-tab-pane label="规则管理" name="rule" />
      <el-tab-pane label="建议记录" name="log" />
    </el-tabs>

    <!-- ============ 生成建议 ============ -->
    <div v-if="activeTab === 'generate'">
      <el-row :gutter="16">
        <!-- 左侧：客户输入 -->
        <el-col :span="10">
          <el-card>
            <template #header>
              <span><el-icon><User /></el-icon> 选择客户</span>
            </template>
            <el-form :model="form" label-width="100px">
              <el-form-item label="客户">
                <el-select
                  v-model="form.memberId"
                  placeholder="搜索客户姓名/手机号"
                  filterable
                  remote
                  :remote-method="searchMembers"
                  style="width: 100%"
                  @change="onMemberChange"
                >
                  <el-option
                    v-for="m in memberList"
                    :key="m.id"
                    :label="`${m.name} (${m.phone || '无手机号'})`"
                    :value="m.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="客户姓名">
                <el-input v-model="form.memberName" disabled />
              </el-form-item>
              <el-form-item label="触发方式">
                <el-radio-group v-model="form.triggerType">
                  <el-radio value="manual">手动触发</el-radio>
                  <el-radio value="auto">自动触发</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-button type="primary" size="large" style="width: 100%" :loading="generating" @click="handleGenerate">
                <el-icon><MagicStick /></el-icon>
                生成护理建议
              </el-button>
            </el-form>

            <!-- 客户肌肤档案 -->
            <el-card class="nested-card" shadow="never" style="margin-top: 16px" v-if="skinProfile">
              <template #header>
                <span>肌肤档案</span>
              </template>
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="肤质">
                  <el-tag :color="getSkinColor(skinProfile.skinType)" effect="dark" size="small">
                    {{ getSkinLabel(skinProfile.skinType) }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="敏感度">
                  {{ skinProfile.sensitivityScore || 0 }}/10
                </el-descriptions-item>
                <el-descriptions-item label="水分">
                  {{ skinProfile.moistureScore || 0 }}
                </el-descriptions-item>
                <el-descriptions-item label="油脂">
                  {{ skinProfile.oilScore || 0 }}
                </el-descriptions-item>
                <el-descriptions-item label="色素">
                  {{ skinProfile.pigmentationScore || 0 }}
                </el-descriptions-item>
                <el-descriptions-item label="皱纹">
                  {{ skinProfile.wrinkleScore || 0 }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-card>
        </el-col>

        <!-- 右侧：生成结果 -->
        <el-col :span="14">
          <el-card>
            <template #header>
              <span>生成的建议 ({{ generatedList.length }})</span>
            </template>

            <el-empty v-if="!generatedList.length" description="点击左侧'生成护理建议'开始" />

            <div v-else>
              <el-card
                v-for="log in generatedList"
                :key="log.id"
                class="suggestion-card"
                shadow="hover"
              >
                <div class="suggestion-header">
                  <div>
                    <el-tag size="small" :type="getActionType(log.suggestionAction || 'recommend')">
                      {{ getActionLabel(log.suggestionAction || 'recommend') }}
                    </el-tag>
                    <span class="suggestion-title">{{ log.title }}</span>
                  </div>
                  <div class="suggestion-meta">
                    <el-tag size="small" effect="plain">触发: {{ log.triggerReason }}</el-tag>
                  </div>
                </div>
                <div class="suggestion-content">{{ log.content }}</div>

                <div v-if="log.recommendedServices" class="recommended">
                  <div class="rec-label">推荐服务：</div>
                  <div class="rec-items">
                    <el-tag
                      v-for="svc in parseJSON(log.recommendedServices)"
                      :key="svc.serviceId"
                      type="success"
                      effect="plain"
                      size="small"
                    >
                      {{ svc.serviceName }}
                    </el-tag>
                  </div>
                </div>

                <div v-if="log.recommendedProducts" class="recommended">
                  <div class="rec-label">推荐产品：</div>
                  <div class="rec-items">
                    <el-tag
                      v-for="prd in parseJSON(log.recommendedProducts)"
                      :key="prd.productId"
                      type="warning"
                      effect="plain"
                      size="small"
                    >
                      {{ prd.productName }}
                    </el-tag>
                  </div>
                </div>

                <div class="suggestion-actions">
                  <el-button size="small" type="success" @click="handleAccept(log)">采纳</el-button>
                  <el-button size="small" @click="handleView(log)">标记已查看</el-button>
                  <el-button size="small" type="danger" plain @click="handleReject(log)">拒绝</el-button>
                </div>
              </el-card>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- ============ 模板管理 ============ -->
    <div v-if="activeTab === 'template'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>建议模板管理</span>
            <div class="filter-form">
              <el-select v-model="tmplTypeFilter" placeholder="类型" clearable style="width: 140px" @change="loadTemplates">
                <el-option
                  v-for="opt in templateTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <el-button type="primary" @click="handleAddTemplate">
                <el-icon><Plus /></el-icon>新增模板
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="templateData" border stripe v-loading="templateLoading">
          <el-table-column label="模板名称" prop="templateName" min-width="180" fixed />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag>{{ getTemplateTypeLabel(row.templateType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="编码" prop="templateCode" width="120" />
          <el-table-column label="标题" prop="title" min-width="180" show-overflow-tooltip />
          <el-table-column label="适用标签" prop="targetTags" width="180" show-overflow-tooltip />
          <el-table-column label="优先级" prop="priority" width="100" align="center" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="toggleTemplate(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditTemplate(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!templateData.length" description="暂无模板" />
      </el-card>
    </div>

    <!-- ============ 规则管理 ============ -->
    <div v-if="activeTab === 'rule'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>建议规则管理</span>
            <el-button type="primary" @click="handleAddRule">
              <el-icon><Plus /></el-icon>新增规则
            </el-button>
          </div>
        </template>

        <el-table :data="ruleData" border stripe v-loading="ruleLoading">
          <el-table-column label="规则名称" prop="ruleName" min-width="180" fixed />
          <el-table-column label="触发条件" width="120">
            <template #default="{ row }">
              <el-tag>{{ getConditionTypeLabel(row.conditionType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="条件值" prop="conditionValue" min-width="160" show-overflow-tooltip />
          <el-table-column label="关联模板" prop="templateName" min-width="160" show-overflow-tooltip />
          <el-table-column label="建议动作" width="120">
            <template #default="{ row }">
              <el-tag :type="getActionType(row.suggestionAction)">{{ getActionLabel(row.suggestionAction) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="冷却期(天)" prop="cooldownDays" width="120" align="center" />
          <el-table-column label="优先级" prop="priority" width="100" align="center" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="toggleRule(row)" />
            </template>
          </el-table-column>
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

    <!-- ============ 建议记录 ============ -->
    <div v-if="activeTab === 'log'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>建议发送记录</span>
            <div class="filter-form">
              <el-select v-model="logMemberFilter" placeholder="客户ID" clearable style="width: 160px" @change="loadLogs">
                <el-option
                  v-for="m in memberList"
                  :key="m.id"
                  :label="m.name"
                  :value="m.id"
                />
              </el-select>
              <el-select v-model="logStatusFilter" placeholder="状态" clearable style="width: 140px" @change="loadLogs">
                <el-option
                  v-for="s in suggestionStatusOptions"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </el-select>
            </div>
          </div>
        </template>

        <el-table :data="logData" border stripe v-loading="logLoading">
          <el-table-column label="客户" prop="memberName" width="120" fixed />
          <el-table-column label="标题" prop="title" min-width="180" show-overflow-tooltip />
          <el-table-column label="触发原因" prop="triggerReason" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="触发方式" width="100">
            <template #default="{ row }">
              {{ row.suggestionType === 'auto' ? '自动' : '手动' }}
            </template>
          </el-table-column>
          <el-table-column label="肤质" prop="skinType" width="100" />
          <el-table-column label="发送时间" prop="sendTime" width="160" />
          <el-table-column label="创建时间" prop="createTime" width="160" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="showLogDetail(row)">查看</el-button>
              <el-button v-if="row.status === 'pending' || row.status === 'sent'" type="success" link size="small" @click="handleAcceptLog(row)">采纳</el-button>
              <el-button v-if="row.status === 'pending'" type="danger" link size="small" @click="handleRejectLog(row)">拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!logData.length" description="暂无记录" />
      </el-card>
    </div>

    <!-- ============ 模板对话框 ============ -->
    <el-dialog v-model="tmplDialogVisible" :title="tmplDialogTitle" width="700px">
      <el-form :model="tmplForm" label-width="100px">
        <el-form-item label="模板名称">
          <el-input v-model="tmplForm.templateName" placeholder="如：敏感肌春季护理建议" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="tmplForm.templateCode" placeholder="如：skin_sensitive_spring" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="tmplForm.templateType" placeholder="选择类型" style="width: 100%">
            <el-option
              v-for="opt in templateTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用标签">
          <el-input v-model="tmplForm.targetTags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="tmplForm.title" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="tmplForm.content" type="textarea" :rows="6" placeholder="支持变量如 {member_name} {skin_type}" />
          <div class="var-tips">
            <span>可用变量：</span>
            <el-tag
              v-for="v in templateVarOptions"
              :key="v.value"
              size="small"
              class="var-tag"
              @click="insertVar(v.example)"
            >
              {{ v.example }}
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="tmplForm.priority" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="tmplForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tmplDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 规则对话框 ============ -->
    <el-dialog v-model="ruleDialogVisible" :title="ruleDialogTitle" width="600px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="ruleForm.ruleCode" />
        </el-form-item>
        <el-form-item label="条件类型">
          <el-select v-model="ruleForm.conditionType" placeholder="选择类型" style="width: 100%">
            <el-option
              v-for="opt in conditionTypeOptions"
              :key="opt.value"
              :label="`${opt.label} - ${opt.description}`"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="条件值">
          <el-input
            v-model="ruleForm.conditionValue"
            type="textarea"
            :rows="2"
            placeholder='如：["VIP","sensitive_skin"] 或 18-25 或 spring'
          />
        </el-form-item>
        <el-form-item label="关联模板">
          <el-select v-model="ruleForm.templateId" placeholder="选择模板" filterable style="width: 100%">
            <el-option
              v-for="t in templateData"
              :key="t.id"
              :label="t.templateName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="建议动作">
          <el-select v-model="ruleForm.suggestionAction" placeholder="选择动作" style="width: 100%">
            <el-option
              v-for="opt in suggestionActionOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="冷却期(天)">
          <el-input-number v-model="ruleForm.cooldownDays" :min="0" :max="365" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="ruleForm.priority" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="ruleForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 记录详情对话框 ============ -->
    <el-dialog v-model="logDetailVisible" title="建议详情" width="600px">
      <div v-if="currentLog">
        <h3>{{ currentLog.title }}</h3>
        <el-tag :type="getStatusType(currentLog.status)">{{ getStatusLabel(currentLog.status) }}</el-tag>
        <p style="margin-top: 12px; line-height: 1.8;">{{ currentLog.content }}</p>
        <el-divider />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户">{{ currentLog.memberName }}</el-descriptions-item>
          <el-descriptions-item label="触发原因">{{ currentLog.triggerReason }}</el-descriptions-item>
          <el-descriptions-item label="肤质">{{ currentLog.skinType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="匹配标签">{{ currentLog.matchedTags || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ currentLog.sendTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="查看时间">{{ currentLog.viewTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, User, MagicStick } from '@element-plus/icons-vue'
import {
  careSuggestionApi,
  templateTypeOptions,
  conditionTypeOptions,
  suggestionActionOptions,
  suggestionStatusOptions,
  skinTypeOptions,
  templateVarOptions
} from '@/api/care-suggestion'

const activeTab = ref('generate')

// ============ 生成建议 ============
const form = reactive({
  memberId: null as number | null,
  memberName: '',
  triggerType: 'manual'
})
const memberList = ref<any[]>([])
const generating = ref(false)
const generatedList = ref<any[]>([])
const skinProfile = ref<any>(null)
const logDetailVisible = ref(false)
const currentLog = ref<any>(null)

const searchMembers = async (keyword: string) => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/member/search', { params: { keyword } })
    memberList.value = res.data || []
  } catch (e) {
    memberList.value = []
  }
}

const onMemberChange = async (memberId: number) => {
  const m = memberList.value.find(item => item.id === memberId)
  if (m) form.memberName = m.name
  try {
    const res: any = await careSuggestionApi.skinProfile(memberId)
    skinProfile.value = res.data
  } catch (e) {
    skinProfile.value = null
  }
}

const handleGenerate = async () => {
  if (!form.memberId) {
    ElMessage.warning('请选择客户')
    return
  }
  generating.value = true
  try {
    const res: any = await careSuggestionApi.generate(form.memberId, {
      operatorId: 1,
      operatorName: 'admin',
      triggerType: form.triggerType
    })
    generatedList.value = res.data || []
    ElMessage.success(`已生成 ${generatedList.value.length} 条建议`)
  } catch (e) {
    ElMessage.error('生成失败')
  } finally {
    generating.value = false
  }
}

const handleAccept = async (log: any) => {
  await careSuggestionApi.acceptLog(log.id)
  ElMessage.success('已采纳')
  log.status = 'accepted'
}

const handleView = async (log: any) => {
  await careSuggestionApi.viewLog(log.id)
  ElMessage.success('已标记为已查看')
  log.status = 'viewed'
}

const handleReject = async (log: any) => {
  await careSuggestionApi.rejectLog(log.id)
  ElMessage.success('已拒绝')
  log.status = 'rejected'
}

// ============ 模板 ============
const templateData = ref<any[]>([])
const templateLoading = ref(false)
const tmplTypeFilter = ref<string>('')
const tmplDialogVisible = ref(false)
const tmplDialogTitle = ref('新增模板')
const tmplForm = reactive<any>({
  id: null,
  templateName: '',
  templateCode: '',
  templateType: 'skin',
  targetTags: '',
  title: '',
  content: '',
  priority: 5,
  status: 1
})

const getTemplateTypeLabel = (t: string) => {
  const opt = templateTypeOptions.find(o => o.value === t)
  return opt ? opt.label : t
}

const loadTemplates = async () => {
  templateLoading.value = true
  try {
    const res: any = await careSuggestionApi.templates({
      templateType: tmplTypeFilter.value || undefined,
      status: undefined
    })
    templateData.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    templateLoading.value = false
  }
}

const handleAddTemplate = () => {
  Object.assign(tmplForm, {
    id: null,
    templateName: '',
    templateCode: '',
    templateType: 'skin',
    targetTags: '',
    title: '',
    content: '',
    priority: 5,
    status: 1
  })
  tmplDialogTitle.value = '新增模板'
  tmplDialogVisible.value = true
}

const handleEditTemplate = (row: any) => {
  Object.assign(tmplForm, row)
  tmplDialogTitle.value = '编辑模板'
  tmplDialogVisible.value = true
}

const handleSaveTemplate = async () => {
  try {
    if (tmplForm.id) {
      await careSuggestionApi.updateTemplate(tmplForm)
    } else {
      await careSuggestionApi.createTemplate(tmplForm)
    }
    ElMessage.success('保存成功')
    tmplDialogVisible.value = false
    loadTemplates()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteTemplate = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除模板"${row.templateName}"吗？`, '提示', { type: 'warning' })
    await careSuggestionApi.deleteTemplate(row.id)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleTemplate = async (row: any) => {
  try {
    await careSuggestionApi.updateTemplate(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

const insertVar = (example: string) => {
  tmplForm.content = (tmplForm.content || '') + example
}

// ============ 规则 ============
const ruleData = ref<any[]>([])
const ruleLoading = ref(false)
const ruleDialogVisible = ref(false)
const ruleDialogTitle = ref('新增规则')
const ruleForm = reactive<any>({
  id: null,
  ruleName: '',
  ruleCode: '',
  conditionType: 'tag',
  conditionValue: '',
  templateId: null,
  suggestionAction: 'recommend',
  cooldownDays: 7,
  priority: 5,
  status: 1
})

const getConditionTypeLabel = (t: string) => {
  const opt = conditionTypeOptions.find(o => o.value === t)
  return opt ? opt.label : t
}

const getActionLabel = (a: string) => {
  const opt = suggestionActionOptions.find(o => o.value === a)
  return opt ? opt.label : a
}

const getActionType = (a: string) => {
  const opt = suggestionActionOptions.find(o => o.value === a)
  return opt ? opt.type : 'info'
}

const loadRules = async () => {
  ruleLoading.value = true
  try {
    const res: any = await careSuggestionApi.rules()
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
    ruleCode: '',
    conditionType: 'tag',
    conditionValue: '',
    templateId: null,
    suggestionAction: 'recommend',
    cooldownDays: 7,
    priority: 5,
    status: 1
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
      await careSuggestionApi.updateRule(ruleForm)
    } else {
      await careSuggestionApi.createRule(ruleForm)
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
    await careSuggestionApi.deleteRule(row.id)
    ElMessage.success('删除成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleRule = async (row: any) => {
  try {
    await careSuggestionApi.updateRule(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

// ============ 记录 ============
const logData = ref<any[]>([])
const logLoading = ref(false)
const logMemberFilter = ref<number | null>(null)
const logStatusFilter = ref<string>('')

const getStatusLabel = (s: string) => {
  const opt = suggestionStatusOptions.find(o => o.value === s)
  return opt ? opt.label : s
}

const getStatusType = (s: string) => {
  const opt = suggestionStatusOptions.find(o => o.value === s)
  return opt ? opt.type : 'info'
}

const loadLogs = async () => {
  logLoading.value = true
  try {
    const res: any = await careSuggestionApi.logs({
      memberId: logMemberFilter.value || undefined,
      status: logStatusFilter.value || undefined
    })
    logData.value = res.data || []
  } catch (e) {
    console.error(e)
  } finally {
    logLoading.value = false
  }
}

const showLogDetail = (row: any) => {
  currentLog.value = row
  logDetailVisible.value = true
}

const handleAcceptLog = async (row: any) => {
  await careSuggestionApi.acceptLog(row.id)
  ElMessage.success('已采纳')
  row.status = 'accepted'
}

const handleRejectLog = async (row: any) => {
  await careSuggestionApi.rejectLog(row.id)
  ElMessage.success('已拒绝')
  row.status = 'rejected'
}

// ============ 辅助 ============
const getSkinLabel = (t: string) => {
  const opt = skinTypeOptions.find(o => o.value === t)
  return opt ? opt.label : t
}

const getSkinColor = (t: string) => {
  const opt = skinTypeOptions.find(o => o.value === t)
  return opt ? opt.color : '#909399'
}

const parseJSON = (str: string) => {
  try {
    return JSON.parse(str) || []
  } catch (e) {
    return []
  }
}

const handleTabChange = (tab: string) => {
  if (tab === 'template') loadTemplates()
  else if (tab === 'rule') loadRules()
  else if (tab === 'log') loadLogs()
}

onMounted(() => {
  searchMembers('')
  loadLogs()
})
</script>

<style scoped>
.care-suggestion-page {
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

.nested-card {
  background: #fafbfc;
}

.suggestion-card {
  margin-bottom: 12px;
}

.suggestion-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.suggestion-title {
  margin-left: 8px;
  font-size: 16px;
  font-weight: 600;
}

.suggestion-content {
  line-height: 1.8;
  color: #303133;
  background: #fafbfc;
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 12px;
}

.recommended {
  margin-top: 8px;
}

.rec-label {
  color: #606266;
  font-size: 13px;
  margin-bottom: 4px;
}

.rec-items {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.suggestion-actions {
  margin-top: 12px;
  text-align: right;
}

.var-tips {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.var-tips > span {
  color: #909399;
  font-size: 12px;
  margin-right: 4px;
}

.var-tag {
  cursor: pointer;
}
</style>