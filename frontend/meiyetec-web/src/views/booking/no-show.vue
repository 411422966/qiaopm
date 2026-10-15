<template>
  <div class="no-show-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">累计爽约</div>
        <div class="kpi-value">{{ stats.totalNoShow ?? '—' }}</div>
      </div>
      <div class="kpi-item red">
        <div class="kpi-label">当前锁定</div>
        <div class="kpi-value">{{ stats.totalLocked ?? '—' }}</div>
      </div>
      <div class="kpi-item yellow">
        <div class="kpi-label">低信用会员</div>
        <div class="kpi-value">{{ stats.lowCreditMembers ?? '—' }}</div>
      </div>
      <div class="kpi-item green">
        <div class="kpi-label">调整次数</div>
        <div class="kpi-value">{{ stats.totalCreditChanges ?? '—' }}</div>
      </div>
      <div class="kpi-item blue">
        <div class="kpi-label">最近 7 天</div>
        <div class="kpi-value">{{ stats.recent7Days ?? '—' }}</div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <el-input v-model="memberQuery" placeholder="会员ID查询信用" style="width: 200px" clearable @keyup.enter="loadMember" />
      <el-button type="primary" @click="loadMember">查询会员</el-button>
      <el-button type="warning" @click="openTriggerDialog">触发爽约</el-button>
    </el-card>

    <el-tabs v-model="activeTab" class="main-tabs">
      <el-tab-pane label="规则配置" name="rules">
        <el-card>
          <div style="margin-bottom: 12px">
            <el-button type="primary" @click="openEditRule()">+ 新增规则</el-button>
          </div>
          <el-table :data="rules" v-loading="loadingRules" border>
            <el-table-column prop="name" label="规则名称" min-width="180" />
            <el-table-column label="爽约次数阈值" width="120" align="center">
              <template #default="{ row }">≥ {{ row.noShowCountThreshold }} 次</template>
            </el-table-column>
            <el-table-column prop="lockDays" label="锁定天数" width="100" align="center" />
            <el-table-column prop="creditPenalty" label="扣分" width="80" align="center" />
            <el-table-column prop="feePenalty" label="扣费" width="100" align="center">
              <template #default="{ row }">¥{{ row.feePenalty }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
                  {{ row.enabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openEditRule(row)">编辑</el-button>
                <el-button link size="small" type="danger" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="会员信用" name="credit">
        <el-card v-if="memberData">
          <h3 style="margin: 0 0 12px">会员 #{{ memberData.credit.memberId }} 信用情况</h3>
          <el-descriptions :column="4" border>
            <el-descriptions-item label="当前信用分">{{ memberData.credit.credit }}</el-descriptions-item>
            <el-descriptions-item label="累计爽约">{{ memberData.credit.noShowCount }} 次</el-descriptions-item>
            <el-descriptions-item label="累计预约">{{ memberData.credit.totalBookings }} 次</el-descriptions-item>
            <el-descriptions-item label="锁定截止">
              {{ memberData.credit.lockedUntil || '未锁定' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">信用日志</el-divider>
          <el-table :data="memberData.logs" border size="small">
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="changeTypeTag(row.changeType)">{{ changeTypeLabel(row.changeType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变更" width="80">
              <template #default="{ row }">
                <span :class="row.changeValue >= 0 ? 'text-success' : 'text-danger'">{{ row.changeValue > 0 ? '+' : '' }}{{ row.changeValue }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变更后" prop="creditAfter" width="90" />
            <el-table-column label="扣费" width="100">
              <template #default="{ row }">¥{{ row.feePenalty }}</template>
            </el-table-column>
            <el-table-column label="操作人" prop="operatorName" width="100" />
            <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
          </el-table>
        </el-card>
        <el-empty v-else description="请输入会员ID查询" />
      </el-tab-pane>
    </el-tabs>

    <!-- 规则编辑 -->
    <el-dialog v-model="ruleDialog.visible" :title="ruleDialog.id ? '编辑规则' : '新增规则'" width="600px" @closed="resetRule">
      <el-form :model="ruleForm" label-width="120px">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.name" />
        </el-form-item>
        <el-form-item label="爽约次数阈值" required>
          <el-input-number v-model="ruleForm.noShowCountThreshold" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="锁定天数" required>
          <el-input-number v-model="ruleForm.lockDays" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="扣分" required>
          <el-input-number v-model="ruleForm.creditPenalty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="扣费">
          <el-input-number v-model="ruleForm.feePenalty" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用">
          <el-radio-group v-model="ruleForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="ruleDialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 触发爽约 -->
    <el-dialog v-model="triggerDialog.visible" title="触发爽约（演示）" width="500px" @closed="resetTrigger">
      <el-form :model="triggerForm" label-width="100px">
        <el-form-item label="会员ID" required>
          <el-input-number v-model="triggerForm.memberId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预约ID">
          <el-input-number v-model="triggerForm.bookingId" :min="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triggerDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="runTrigger" :loading="triggerDialog.running">触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const stats = reactive<any>({})
const rules = ref<any[]>([])
const loadingRules = ref(false)
const memberQuery = ref<number | undefined>(undefined)
const memberData = ref<any>(null)
const activeTab = ref('rules')

async function loadStats() {
  try {
    const r = await http.get('/biz/booking/no-show/stats')
    Object.assign(stats, r.data || {})
  } catch {}
}

async function loadRules() {
  loadingRules.value = true
  try {
    const r = await http.get('/biz/booking/no-show-rule/list')
    rules.value = r.data || []
  } finally {
    loadingRules.value = false
  }
}

async function loadMember() {
  if (!memberQuery.value) return
  const r = await http.get('/biz/booking/member-credit', { params: { memberId: memberQuery.value } })
  memberData.value = r.data
  activeTab.value = 'credit'
}

function changeTypeLabel(t: string) {
  return { NO_SHOW: '爽约', ON_TIME: '按时到达', CANCEL_LATE: '临时取消', MANUAL: '人工调整' }[t] || t
}
function changeTypeTag(t: string) {
  return ({ NO_SHOW: 'danger', ON_TIME: 'success', CANCEL_LATE: 'warning', MANUAL: 'info' } as any)[t] || 'info'
}

function formatDate(d: string) {
  if (!d) return ''
  return d.substring(0, 16).replace('T', ' ')
}

const ruleDialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const ruleForm = reactive<any>({
  name: '', noShowCountThreshold: 3, lockDays: 30,
  creditPenalty: 10, feePenalty: 20, description: '', enabled: 1, sortOrder: 0
})

function openEditRule(row?: any) {
  if (row) {
    ruleDialog.id = row.id
    Object.assign(ruleForm, row)
  } else {
    ruleDialog.id = undefined
  }
  ruleDialog.visible = true
}

function resetRule() {
  Object.assign(ruleForm, {
    name: '', noShowCountThreshold: 3, lockDays: 30,
    creditPenalty: 10, feePenalty: 20, description: '', enabled: 1, sortOrder: 0
  })
}

async function saveRule() {
  if (!ruleForm.name) return ElMessage.warning('名称必填')
  ruleDialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (ruleDialog.id) {
      await http.put(`/biz/booking/no-show-rule/${ruleDialog.id}`, ruleForm, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/biz/booking/no-show-rule', ruleForm, { headers })
      ElMessage.success('已创建')
    }
    ruleDialog.visible = false
    loadRules()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    ruleDialog.saving = false
  }
}

async function removeRule(row: any) {
  await ElMessageBox.confirm(`确认删除规则「${row.name}」？`, '提示', { type: 'warning' })
  await http.delete(`/biz/booking/no-show-rule/${row.id}`)
  ElMessage.success('已删除')
  loadRules()
}

const triggerDialog = reactive({ visible: false, running: false })
const triggerForm = reactive({ memberId: undefined as number | undefined, bookingId: undefined as number | undefined })

function openTriggerDialog() {
  triggerDialog.visible = true
}
function resetTrigger() {
  triggerForm.memberId = undefined
  triggerForm.bookingId = undefined
}

async function runTrigger() {
  if (!triggerForm.memberId) return ElMessage.warning('会员ID必填')
  triggerDialog.running = true
  try {
    const r = await http.post('/biz/booking/no-show-rule/trigger', triggerForm)
    const d = r.data
    ElMessage.success(`已处理：累计 ${d.newCount} 次，信用 ${d.newCredit}，锁定 ${d.locked ? '是' : '否'}`)
    triggerDialog.visible = false
    loadStats()
    if (memberData.value && memberData.value.credit?.memberId === triggerForm.memberId) {
      await loadMember()
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '触发失败')
  } finally {
    triggerDialog.running = false
  }
}

onMounted(async () => {
  await loadStats()
  await loadRules()
})
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-item.red { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.blue { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input { margin-right: 8px; }
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }
</style>
