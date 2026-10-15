<template>
  <div class="member-day-page">
    <el-card class="overview-card">
      <template #header>
        <div class="card-header">
          <span class="title">会员日自动化</span>
          <el-space>
            <el-button type="primary" @click="loadAll" :loading="loading">刷新</el-button>
            <el-button @click="initDefault" :loading="initing">初始化默认配置</el-button>
          </el-space>
        </div>
      </template>

      <!-- ROI 概览 -->
      <el-descriptions :column="4" border v-if="roi">
        <el-descriptions-item label="统计天数">
          <el-tag>{{ roi.days }} 天</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="触发总数">
          <el-tag type="info">{{ roi.totalTriggered }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="转化率">
          <el-tag :type="roi.conversionRate >= 10 ? 'success' : 'warning'">
            {{ roi.conversionRate }}%
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="ROI">
          <el-tag :type="roi.roi >= 1 ? 'success' : 'danger'">{{ roi.roi }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已发送">{{ roi.totalSent }}</el-descriptions-item>
        <el-descriptions-item label="已使用">{{ roi.totalUsed }}</el-descriptions-item>
        <el-descriptions-item label="礼券成本">¥{{ roi.giftCost }}</el-descriptions-item>
        <el-descriptions-item label="带来营收">¥{{ roi.revenue }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="roi && roi.byDayType && roi.byDayType.length" class="by-type">
        <span class="label">按类型：</span>
        <el-tag v-for="t in roi.byDayType" :key="t.day_type" style="margin-right:8px">
          {{ dayTypeLabel(t.day_type) }} · {{ t.cnt }} 条 · 转化 {{ t.used_cnt }}
        </el-tag>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="day-tabs">
      <!-- 配置 -->
      <el-tab-pane label="会员日配置" name="config">
        <el-card>
          <div class="toolbar">
            <el-button type="primary" @click="openConfigDialog()">+ 新建配置</el-button>
            <el-button @click="triggerAll" :loading="triggering" type="success">
              立即全部触发
            </el-button>
          </div>
          <el-table :data="configs" v-loading="loading" border stripe>
            <el-table-column prop="configName" label="名称" min-width="160" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ dayTypeLabel(row.dayType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="triggerRule" label="触发规则" min-width="180" />
            <el-table-column label="折扣" width="100">
              <template #default="{ row }">
                <span v-if="row.discountRate">{{ Number(row.discountRate) * 10 }} 折</span>
              </template>
            </el-table-column>
            <el-table-column label="礼券" width="100">
              <template #default="{ row }">¥{{ row.giftAmount || 0 }}</template>
            </el-table-column>
            <el-table-column label="积分" width="80">
              <template #default="{ row }">{{ row.giftPoints || 0 }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="80">
              <template #default="{ row }">
                <el-tag :type="row.priority >= 80 ? 'danger' : row.priority >= 40 ? 'warning' : 'info'" size="small">
                  {{ row.priority }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openConfigDialog(row)">编辑</el-button>
                <el-button link size="small" type="primary" @click="triggerOne(row)">
                  触发
                </el-button>
                <el-button link size="small" type="danger" @click="archiveConfig(row)">
                  归档
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 执行记录 -->
      <el-tab-pane label="执行记录" name="executions">
        <el-card>
          <div class="toolbar">
            <el-date-picker
              v-model="execFilter.date"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="按日期筛选"
              style="width:160px"
              clearable
              @change="loadExecutions"
            />
            <el-select v-model="execFilter.status" placeholder="状态" clearable style="width:140px" @change="loadExecutions">
              <el-option label="已发送" value="SENT" />
              <el-option label="已使用" value="USED" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button @click="resetExecFilter">重置</el-button>
          </div>
          <el-table :data="executions" v-loading="loading" border stripe>
            <el-table-column prop="triggerDate" label="触发日期" width="120" />
            <el-table-column prop="memberName" label="会员" width="100" />
            <el-table-column prop="memberPhone" label="手机" width="130" />
            <el-table-column label="触发原因" width="110">
              <template #default="{ row }">
                <el-tag size="small">{{ dayTypeLabel(row.triggerReason) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="couponCode" label="礼券码" width="170" />
            <el-table-column label="礼券" width="80">
              <template #default="{ row }">¥{{ row.giftAmount || 0 }}</template>
            </el-table-column>
            <el-table-column label="积分" width="80">
              <template #default="{ row }">{{ row.giftPoints || 0 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.sendStatus === 'USED' ? 'success' : row.sendStatus === 'SENT' ? 'info' : 'danger'" size="small">
                  {{ row.sendStatus === 'USED' ? '已使用' : row.sendStatus === 'SENT' ? '已发送' : row.sendStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="使用时间" width="170">
              <template #default="{ row }">{{ row.usedAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" type="primary" :disabled="row.sendStatus === 'USED'" @click="markUsed(row)">
                  标记使用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 即将到来 -->
      <el-tab-pane label="即将到来的生日" name="upcoming">
        <el-card>
          <div class="toolbar">
            <span class="hint">未来 7 天内生日的会员（按规则自动触发祝福）</span>
            <el-button @click="loadUpcoming">刷新</el-button>
          </div>
          <el-table :data="upcomingList" v-loading="loading" border stripe>
            <el-table-column prop="member_name" label="姓名" width="120" />
            <el-table-column prop="phone" label="手机" width="140" />
            <el-table-column prop="birthday" label="生日" width="120" />
            <el-table-column prop="age" label="年龄" width="80" />
            <el-table-column label="距生日">
              <template #default="{ row }">
                <el-tag :type="row.days_until <= 1 ? 'danger' : row.days_until <= 3 ? 'warning' : 'info'">
                  {{ row.days_until === 0 ? '今天' : `${row.days_until} 天后` }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 配置编辑弹窗 -->
    <el-dialog
      v-model="configDialog.visible"
      :title="configDialog.id ? '编辑会员日配置' : '新建会员日配置'"
      width="640px"
      @closed="resetConfigForm"
    >
      <el-form :model="configDialog.form" label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="configDialog.form.configName" placeholder="如：月度会员日" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="configDialog.form.dayType" placeholder="选择类型" style="width:100%">
            <el-option label="生日特惠" value="BIRTHDAY" />
            <el-option label="月度会员日" value="MEMBER_DAY" />
            <el-option label="周年庆" value="ANNIVERSARY" />
            <el-option label="季节活动" value="SEASONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发规则">
          <el-input v-model="configDialog.form.triggerRule" placeholder="如：每月 18 日 / 生日当月" />
        </el-form-item>
        <el-form-item label="目标会员等级">
          <el-select v-model="configDialog.form.targetLevel" clearable placeholder="全部等级" style="width:100%">
            <el-option label="全部等级" value="ALL" />
            <el-option label="普通会员" value="NORMAL" />
            <el-option label="银卡" value="SILVER" />
            <el-option label="金卡" value="GOLD" />
            <el-option label="钻石卡" value="DIAMOND" />
          </el-select>
        </el-form-item>
        <el-form-item label="折扣率">
          <el-input-number v-model="configDialog.form.discountRate" :min="0" :max="1" :step="0.05" placeholder="0.85 = 85 折" />
        </el-form-item>
        <el-form-item label="礼券金额">
          <el-input-number v-model="configDialog.form.giftAmount" :min="0" :step="50" />
        </el-form-item>
        <el-form-item label="赠送积分">
          <el-input-number v-model="configDialog.form.giftPoints" :min="0" :step="100" />
        </el-form-item>
        <el-form-item label="生日偏移(天)">
          <el-input-number v-model="configDialog.form.birthdayOffsetDays" :min="0" :max="30" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="configDialog.form.priority" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="短信模板">
          <el-input v-model="configDialog.form.smsTemplate" type="textarea" :rows="3" placeholder="支持 {name} 占位符" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="configDialog.form.status">
            <el-radio-button value="ACTIVE">启用</el-radio-button>
            <el-radio-button value="PAUSED">暂停</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig" :loading="configDialog.saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { memberDayApi } from '@/api/member-day'

const loading = ref(false)
const initing = ref(false)
const triggering = ref(false)
const activeTab = ref('config')

const configs = ref<any[]>([])
const executions = ref<any[]>([])
const upcomingList = ref<any[]>([])
const roi = ref<any>(null)

const execFilter = reactive({
  date: '' as string,
  status: '' as string
})

const configDialog = reactive({
  visible: false,
  id: null as number | null,
  saving: false,
  form: {} as any
})

function dayTypeLabel(type?: string) {
  const map: Record<string, string> = {
    BIRTHDAY: '生日',
    MEMBER_DAY: '会员日',
    ANNIVERSARY: '周年庆',
    SEASONAL: '季节活动'
  }
  return map[type || ''] || type || '—'
}

async function loadConfigs() {
  loading.value = true
  try {
    const res: any = await memberDayApi.listConfigs()
    configs.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadExecutions() {
  loading.value = true
  try {
    const params: any = {}
    if (execFilter.date) params.date = execFilter.date
    if (execFilter.status) params.status = execFilter.status
    const res: any = await memberDayApi.listExecutions(params)
    executions.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadUpcoming() {
  loading.value = true
  try {
    const res: any = await memberDayApi.upcoming(7)
    upcomingList.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadRoi() {
  try {
    const res: any = await memberDayApi.roi(30)
    roi.value = res.data || null
  } catch {
    roi.value = null
  }
}

function loadAll() {
  loadConfigs()
  loadExecutions()
  loadUpcoming()
  loadRoi()
}

function resetExecFilter() {
  execFilter.date = ''
  execFilter.status = ''
  loadExecutions()
}

async function initDefault() {
  initing.value = true
  try {
    const res: any = await memberDayApi.initDefault()
    ElMessage.success(`已初始化 ${res.data?.created || 0} 个默认配置`)
    loadConfigs()
  } finally {
    initing.value = false
  }
}

async function triggerAll() {
  triggering.value = true
  try {
    const res: any = await memberDayApi.trigger()
    ElMessage.success(`已触发：发送 ${res.data?.sent}，跳过 ${res.data?.skipped}，失败 ${res.data?.errors}`)
    loadExecutions()
    loadRoi()
  } finally {
    triggering.value = false
  }
}

async function triggerOne(row: any) {
  try {
    await ElMessageBox.confirm(`确认触发「${row.configName}」？`, '提示', { type: 'warning' })
  } catch { return }
  const res: any = await memberDayApi.triggerByConfig(row.id)
  ElMessage.success(`已触发：发送 ${res.data?.sent}，跳过 ${res.data?.skipped}`)
  loadExecutions()
}

function openConfigDialog(row?: any) {
  if (row) {
    configDialog.id = row.id
    configDialog.form = { ...row }
  } else {
    configDialog.id = null
    configDialog.form = {
      configName: '',
      dayType: 'MEMBER_DAY',
      triggerRule: '',
      targetLevel: 'ALL',
      discountRate: 0.85,
      giftAmount: 100,
      giftPoints: 200,
      birthdayOffsetDays: 0,
      priority: 50,
      smsTemplate: '',
      status: 'ACTIVE'
    }
  }
  configDialog.visible = true
}

function resetConfigForm() {
  configDialog.id = null
  configDialog.form = {}
}

async function submitConfig() {
  if (!configDialog.form.configName) {
    ElMessage.warning('请填写配置名称')
    return
  }
  configDialog.saving = true
  try {
    if (configDialog.id) {
      await memberDayApi.updateConfig(configDialog.id, configDialog.form)
      ElMessage.success('已更新')
    } else {
      await memberDayApi.createConfig(configDialog.form)
      ElMessage.success('已创建')
    }
    configDialog.visible = false
    loadConfigs()
  } finally {
    configDialog.saving = false
  }
}

async function archiveConfig(row: any) {
  try {
    await ElMessageBox.confirm(`确认归档「${row.configName}」？归档后不再触发。`, '提示', { type: 'warning' })
  } catch { return }
  await memberDayApi.deleteConfig(row.id)
  ElMessage.success('已归档')
  loadConfigs()
}

async function markUsed(row: any) {
  await memberDayApi.markUsed(row.id)
  ElMessage.success('已标记使用')
  loadExecutions()
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.member-day-page {
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
.by-type {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
.by-type .label {
  margin-right: 6px;
  font-weight: 600;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.toolbar .hint {
  color: #909399;
  font-size: 13px;
  margin-right: auto;
}
</style>