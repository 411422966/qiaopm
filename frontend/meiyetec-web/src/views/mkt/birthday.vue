<template>
  <div class="birthday-page">
    <!-- 顶部 KPI 卡片 -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-pink">
        <div class="kpi-label">已录入</div>
        <div class="kpi-value">{{ dash.totalMembers ?? '—' }}</div>
        <div class="kpi-sub">全部会员</div>
      </div>
      <div class="kpi-card kpi-orange">
        <div class="kpi-label">已开启自动</div>
        <div class="kpi-value">{{ dash.autoSendMembers ?? '—' }}</div>
        <div class="kpi-sub">将在生日触发</div>
      </div>
      <div class="kpi-card kpi-purple">
        <div class="kpi-label">今年已发送</div>
        <div class="kpi-value">{{ dash.sentThisYear ?? '—' }}</div>
        <div class="kpi-sub">条祝福</div>
      </div>
      <div class="kpi-card kpi-blue">
        <div class="kpi-label">7天内生日</div>
        <div class="kpi-value">{{ dash.upcomingCount ?? '—' }}</div>
        <div class="kpi-sub">位会员</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" :icon="Refresh" @click="loadDash">刷新</el-button>
        <el-button type="success" :icon="Promotion" @click="openBatchSend">批量发送</el-button>
        <el-button :icon="View" @click="openPreview">预览发送效果</el-button>
        <span class="toolbar-tip">每天 08:00 自动扫描，提前7天/当天/过期1天 自动发站内信+短信</span>
      </div>

      <el-tabs v-model="activeTab">
        <!-- 即将到来的生日 -->
        <el-tab-pane label="即将到来的生日" name="upcoming">
          <el-table :data="upcomingRows" v-loading="loading" stripe border>
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column prop="phone" label="手机" width="140" />
            <el-table-column label="生日" width="120">
              <template #default="{ row }">
                {{ formatBirthday(row.birthday) }}
              </template>
            </el-table-column>
            <el-table-column label="距生日">
              <template #default="{ row }">
                <el-tag :type="row.daysUntilBirthday === 0 ? 'danger' : row.daysUntilBirthday <= 3 ? 'warning' : 'info'" size="small">
                  {{ row.daysUntilBirthday === 0 ? '🎂 今天' : row.daysUntilBirthday + ' 天后' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="年龄" width="80">
              <template #default="{ row }">{{ row.age ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="已录入">
              <template #default="{ row }">
                <el-tag v-if="row.id" size="small" type="success">已录入</el-tag>
                <el-tag v-else size="small" type="danger">未录入</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <template v-if="row.member_id">
                  <el-button text type="primary" :icon="Message" size="small" @click="openSend(row)">
                    发送礼物
                  </el-button>
                  <el-button text type="warning" :icon="Edit" size="small" @click="openEdit(row)">
                    编辑
                  </el-button>
                </template>
                <template v-else>
                  <el-button text type="success" :icon="Plus" size="small" @click="openRegister(row)">
                    录入生日
                  </el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!upcomingRows.length && !loading" class="empty">暂无数据</div>
        </el-tab-pane>

        <!-- 已录入会员 -->
        <el-tab-pane label="已录入会员" name="registered">
          <el-table :data="registeredRows" v-loading="loadingReg" stripe border>
            <el-table-column label="姓名" width="100">
              <template #default="{ row }">{{ row.name || row.member_name }}</template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" width="140" />
            <el-table-column label="生日" width="120">
              <template #default="{ row }">{{ formatBirthday(row.birthday) }}</template>
            </el-table-column>
            <el-table-column prop="birthdayType" label="类型" width="80">
              <template #default="{ row }">
                {{ row.birthdayType === 'LUNAR' ? '农历' : '公历' }}
              </template>
            </el-table-column>
            <el-table-column label="自动发送" width="100">
              <template #default="{ row }">
                <el-tag :type="row.autoSend === 1 ? 'success' : 'info'" size="small">
                  {{ row.autoSend === 1 ? '已开启' : '未开启' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="VIP等级" width="90">
              <template #default="{ row }">{{ row.vipLevel ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="已发送">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.lastSentYear ? row.lastSentYear + '年' : '未发过' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" :icon="Message" size="small" @click="openSend(row)">发送</el-button>
                <el-button text type="warning" :icon="Edit" size="small" @click="openEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 发送记录 -->
        <el-tab-pane label="发送记录" name="logs">
          <div class="filter-bar">
            <el-select v-model="logFilter.year" placeholder="年份" clearable style="width:100px" @change="loadLogs">
              <el-option v-for="y in logYears" :key="y" :label="y + '年'" :value="y" />
            </el-select>
            <el-select v-model="logFilter.status" placeholder="状态" clearable style="width:100px" @change="loadLogs">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="部分失败" value="PARTIAL_FAILED" />
            </el-select>
            <el-select v-model="logFilter.giftType" placeholder="礼物类型" clearable style="width:120px" @change="loadLogs">
              <el-option label="优惠券" value="COUPON" />
              <el-option label="积分" value="POINTS" />
              <el-option label="仅通知" value="SMS_ONLY" />
            </el-select>
            <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
          </div>
          <el-table :data="logRows" v-loading="loadingLog" stripe border>
            <el-table-column label="会员" width="100">
              <template #default="{ row }">{{ row.memberName || row.member_name || '—' }}</template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" width="130" />
            <el-table-column label="年份" width="80">
              <template #default="{ row }">{{ row.sendYear }}</template>
            </el-table-column>
            <el-table-column label="礼物类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="giftTagType(row.giftType)">{{ giftLabel(row.giftType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优惠券码" min-width="160">
              <template #default="{ row }">
                <code v-if="row.couponCode" style="font-size:12px">{{ row.couponCode }}</code>
                <span v-else style="color:#bbb">—</span>
              </template>
            </el-table-column>
            <el-table-column label="积分" width="80">
              <template #default="{ row }">{{ row.pointsAwarded ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发送时间" width="170">
              <template #default="{ row }">{{ formatDate(row.sentAt) }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.message }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 录入/编辑生日弹窗 -->
    <el-dialog v-model="regDialog.visible" :title="regDialog.isEdit ? '编辑生日信息' : '录入会员生日'" width="480px">
      <el-form :model="regForm" label-width="100px">
        <el-form-item label="会员">
          <el-select v-if="!regDialog.isEdit" v-model="regForm.memberId" filterable remote placeholder="搜索姓名/手机号"
            :remote-method="searchMembers" :loading="searchLoading" style="width:100%">
            <el-option v-for="m in memberOpts" :key="m.id" :label="`${m.name}（${m.phone}）`" :value="m.id" />
          </el-select>
          <el-input v-else :value="regDialog.memberName" disabled />
        </el-form-item>
        <el-form-item label="生日日期" required>
          <el-date-picker v-model="regForm.birthday" type="date" value-format="YYYY-MM-DD"
            placeholder="选择日期" style="width:100%" />
        </el-form-item>
        <el-form-item label="生日类型">
          <el-radio-group v-model="regForm.birthdayType">
            <el-radio value="SOLAR">公历</el-radio>
            <el-radio value="LUNAR">农历</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="自动发送">
          <el-switch v-model="regForm.autoSend" />
          <span class="field-hint">开启后，生日当天/提前7天/过期1天自动发送站内信</span>
        </el-form-item>
        <el-form-item label="VIP等级">
          <el-input-number v-model="regForm.vipLevel" :min="0" :max="10" style="width:100%" placeholder="选填" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="regForm.tags" placeholder="如：重要客户,高消费" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="regDialog.saving" @click="submitRegister">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发送礼物弹窗 -->
    <el-dialog v-model="sendDialog.visible" title="发送生日礼物" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        将通过真实通道（短信/小程序/站内信）发送生日祝福，并可发放优惠券或积分
      </el-alert>
      <el-form :model="sendForm" label-width="110px">
        <el-form-item label="会员">
          <el-input :value="sendDialog.memberName" disabled />
        </el-form-item>
        <el-form-item label="礼物类型" required>
          <el-radio-group v-model="sendForm.giftType">
            <el-radio value="SMS_ONLY">仅发祝福</el-radio>
            <el-radio value="COUPON">发放优惠券</el-radio>
            <el-radio value="POINTS">赠送积分</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="sendForm.giftType === 'COUPON'" label="选择优惠券" required>
          <el-select v-model="sendForm.couponId" placeholder="选择优惠券" style="width:100%">
            <el-option v-for="c in coupons" :key="c.id" :label="`${c.name}（¥${c.parValue}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="sendForm.giftType === 'POINTS'" label="积分数量">
          <el-input-number v-model="sendForm.points" :min="1" :max="99999" :step="100" style="width:200px" />
          <span style="margin-left:8px;color:#909399">分</span>
        </el-form-item>
        <el-form-item label="通知通道" required>
          <el-checkbox-group v-model="sendForm.channels">
            <el-checkbox value="IN_APP">站内信</el-checkbox>
            <el-checkbox value="SMS">短信</el-checkbox>
            <el-checkbox value="MINI_PROGRAM">小程序订阅消息</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="祝福语预览">
          <div class="msg-preview">{{ msgPreview }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="sendDialog.sending" @click="submitSend">确认发送</el-button>
      </template>
    </el-dialog>

    <!-- 批量发送弹窗 -->
    <el-dialog v-model="batchDialog.visible" title="批量发送生日礼物" width="520px">
      <el-alert type="warning" :closable="false" style="margin-bottom:14px">
        将对所选会员批量触发生日礼物发送（每个会员独立事务，可能部分失败）
      </el-alert>
      <el-form :model="batchForm" label-width="110px">
        <el-form-item label="发送范围">
          <el-radio-group v-model="batchForm.scope">
            <el-radio value="upcoming">7天内生日的会员</el-radio>
            <el-radio value="all">全部已录入会员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="礼物类型">
          <el-radio-group v-model="batchForm.giftType">
            <el-radio value="SMS_ONLY">仅发祝福</el-radio>
            <el-radio value="COUPON">发放优惠券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="batchForm.giftType === 'COUPON'" label="选择优惠券" required>
          <el-select v-model="batchForm.couponId" placeholder="选择优惠券" style="width:100%">
            <el-option v-for="c in coupons" :key="c.id" :label="`${c.name}（¥${c.parValue}）`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知通道">
          <el-checkbox-group v-model="batchForm.channels">
            <el-checkbox value="IN_APP">站内信</el-checkbox>
            <el-checkbox value="SMS">短信</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="batchDialog.sending" @click="submitBatch">
          确认批量发送
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewDialog.visible" title="发送预览" width="600px">
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        以下会员将在其生日当天/提前7天收到系统自动发送的站内信通知
      </el-alert>
      <el-table :data="previewRows" size="small" border>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column label="距生日" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.daysUntilBirthday === 0 ? 'danger' : 'info'">
              {{ row.daysUntilBirthday === 0 ? '今天' : row.daysUntilBirthday + '天后' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="birthday" label="生日" width="120" />
        <el-table-column label="已录入">
          <template #default="{ row }">
            <el-tag :type="row.id ? 'success' : 'danger'" size="small">{{ row.id ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="preview-footer">共 {{ previewRows.length }} 位会员将在未来 7 天内生日</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, View, Promotion, Message, Edit, Plus } from '@element-plus/icons-vue'
import { birthdayApi } from '@/api/birthday'
import { memberApi } from '@/api/basic'
import { couponApi } from '@/api/coupon'

const activeTab = ref('upcoming')
const loading = ref(false)
const loadingReg = ref(false)
const loadingLog = ref(false)

// ---- 仪表盘 ----
const dash = ref<any>({})

// ---- 即将到来 ----
const upcomingRows = ref<any[]>([])

async function loadUpcoming() {
  loading.value = true
  try {
    const res: any = await birthdayApi.upcoming(30)
    upcomingRows.value = res.data || []
  } finally { loading.value = false }
}

// ---- 已录入 ----
const registeredRows = ref<any[]>([])
async function loadRegistered() {
  loadingReg.value = true
  try {
    const res: any = await birthdayApi.listMembers({ autoSendOnly: false })
    registeredRows.value = res.data || []
  } finally { loadingReg.value = false }
}

// ---- 发送记录 ----
const logFilter = reactive({ year: null as number | null, status: '', giftType: '' })
const logYears = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - i)
const logRows = ref<any[]>([])
async function loadLogs() {
  loadingLog.value = true
  try {
    const res: any = await birthdayApi.logs({
      limit: 100,
      year: logFilter.year ?? undefined,
      status: logFilter.status || undefined,
      giftType: logFilter.giftType || undefined
    })
    logRows.value = res.data || []
  } finally { loadingLog.value = false }
}

// ---- 仪表盘 ----
async function loadDash() {
  try {
    const res: any = await birthdayApi.dashboard()
    dash.value = res.data || {}
    // 同步 upcoming
    if (dash.value.upcoming7d) upcomingRows.value = dash.value.upcoming7d
    else await loadUpcoming()
  } catch {}
}

// ---- 录入/编辑弹窗 ----
const regDialog = reactive({
  visible: false, saving: false, isEdit: false,
  memberId: null as number | null, memberName: ''
})
const regForm = reactive({
  memberId: null as number | null,
  birthday: '',
  birthdayType: 'SOLAR',
  autoSend: true,
  vipLevel: null as number | null,
  tags: ''
})
const memberOpts = ref<any[]>([])
const searchLoading = ref(false)

function openRegister(row: any) {
  regDialog.isEdit = false
  regDialog.visible = true
  Object.assign(regForm, {
    memberId: row.member_id, birthday: '', birthdayType: 'SOLAR',
    autoSend: true, vipLevel: null, tags: ''
  })
}

function openEdit(row: any) {
  regDialog.isEdit = true
  regDialog.visible = true
  regDialog.memberId = row.member_id
  regDialog.memberName = row.name || row.member_name
  Object.assign(regForm, {
    memberId: row.member_id,
    birthday: row.birthday ? row.birthday.toString().substring(0, 10) : '',
    birthdayType: row.birthdayType || 'SOLAR',
    autoSend: row.autoSend === 1,
    vipLevel: row.vipLevel ?? null,
    tags: row.tags || ''
  })
}

async function searchMembers(kw: string) {
  searchLoading.value = true
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 20, keyword: kw || '' })
    memberOpts.value = res.data?.records || []
  } finally { searchLoading.value = false }
}

async function submitRegister() {
  if (!regDialog.isEdit && !regForm.memberId) { ElMessage.warning('请选择会员'); return }
  if (!regForm.birthday) { ElMessage.warning('请选择生日日期'); return }
  regDialog.saving = true
  try {
    const data: any = {
      memberId: regForm.memberId,
      birthday: regForm.birthday,
      birthdayType: regForm.birthdayType,
      autoSend: regForm.autoSend,
      vipLevel: regForm.vipLevel,
      tags: regForm.tags
    }
    const res: any = await birthdayApi.register(data)
    if (res.code === 200) {
      ElMessage.success('已保存')
      regDialog.visible = false
      loadRegistered()
      loadUpcoming()
      loadDash()
    }
  } finally { regDialog.saving = false }
}

// ---- 发送礼物弹窗 ----
const sendDialog = reactive({ visible: false, sending: false, memberName: '' })
const sendForm = reactive({
  memberId: null as number | null,
  giftType: 'SMS_ONLY',
  couponId: null as number | null,
  points: 500,
  channels: ['IN_APP'] as string[]
})
const coupons = ref<any[]>([])
const msgPreview = computed(() => {
  const name = sendDialog.memberName || '会员'
  if (sendForm.giftType === 'COUPON') return `🎂 亲爱的${name}，生日快乐！您的专属优惠券已到账，请尽快使用~`
  if (sendForm.giftType === 'POINTS') return `🎂 亲爱的${name}，生日快乐！已赠送${sendForm.points}积分到您的账户~`
  return `🎂 亲爱的${name}，生日快乐！感谢一路有您，美业祝您青春美丽！`
})

function openSend(row: any) {
  sendDialog.memberName = row.name || row.member_name || '会员'
  sendForm.memberId = row.member_id || row.memberId
  sendForm.giftType = 'SMS_ONLY'
  sendForm.couponId = null
  sendForm.points = 500
  sendForm.channels = ['IN_APP']
  sendDialog.visible = true
  loadCoupons()
}

async function loadCoupons() {
  try {
    const res: any = await couponApi.page({ pageNum: 1, pageSize: 100, status: 1 })
    coupons.value = res.data?.records || []
  } catch { coupons.value = [] }
}

async function submitSend() {
  if (!sendForm.memberId) return
  if (sendForm.giftType === 'COUPON' && !sendForm.couponId) {
    ElMessage.warning('请选择优惠券'); return
  }
  await ElMessageBox.confirm('确认发送生日礼物？', '发送确认', { type: 'warning' })
    .catch(() => null)
  if (!sendForm.memberId) return
  sendDialog.sending = true
  try {
    const res: any = await birthdayApi.send({
      memberId: sendForm.memberId,
      giftType: sendForm.giftType,
      couponId: sendForm.couponId ?? undefined,
      points: sendForm.giftType === 'POINTS' ? sendForm.points : undefined,
      channelType: sendForm.channels.join(',')
    })
    if (res.code === 200) {
      ElMessage.success('发送成功')
      sendDialog.visible = false
      loadDash()
      loadLogs()
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } finally { sendDialog.sending = false }
}

// ---- 批量发送弹窗 ----
const batchDialog = reactive({ visible: false, sending: false })
const batchForm = reactive({
  scope: 'upcoming',
  giftType: 'SMS_ONLY',
  couponId: null as number | null,
  channels: ['IN_APP'] as string[]
})

function openBatchSend() {
  batchForm.scope = 'upcoming'
  batchForm.giftType = 'SMS_ONLY'
  batchForm.couponId = null
  batchForm.channels = ['IN_APP']
  batchDialog.visible = true
  loadCoupons()
}

async function submitBatch() {
  if (batchForm.giftType === 'COUPON' && !batchForm.couponId) {
    ElMessage.warning('请选择优惠券'); return
  }
  await ElMessageBox.confirm('批量发送生日礼物？', '批量确认', { type: 'warning' })
    .catch(() => null)
  const res: any = await birthdayApi.sendBatch({
    daysBefore: batchForm.scope === 'upcoming' ? 7 : undefined,
    giftType: batchForm.giftType,
    couponId: batchForm.couponId ?? undefined,
    channelType: batchForm.channels.join(',')
  })
  if (res.code === 200) {
    ElMessage.success(`批量发送完成：${res.data?.success} 成功，${res.data?.fail} 失败`)
    batchDialog.visible = false
    loadDash()
    loadLogs()
  }
}

// ---- 预览弹窗 ----
const previewDialog = reactive({ visible: false })
const previewRows = ref<any[]>([])
async function openPreview() {
  previewDialog.visible = true
  try {
    const res: any = await birthdayApi.preview(7)
    previewRows.value = res.data?.members || []
  } catch { previewRows.value = [] }
}

// ---- 工具函数 ----
function formatBirthday(v: any) {
  if (!v) return '—'
  return v.toString().substring(0, 10)
}
function formatDate(v: any) {
  if (!v) return '—'
  return new Date(v).toLocaleString('zh-CN')
}
function giftLabel(t: string) {
  return ({ COUPON: '优惠券', POINTS: '积分', GIFT: '礼包', SMS_ONLY: '仅通知' } as any)[t] || t
}
function giftTagType(t: string) {
  return ({ COUPON: 'success', POINTS: 'warning', GIFT: 'primary', SMS_ONLY: 'info' } as any)[t] || 'info'
}
function statusLabel(s: string) {
  return ({ SUCCESS: '成功', FAILED: '失败', PARTIAL_FAILED: '部分失败', PENDING: '待发送' } as any)[s] || s
}
function statusTagType(s: string) {
  return ({ SUCCESS: 'success', FAILED: 'danger', PARTIAL_FAILED: 'warning', PENDING: 'info' } as any)[s] || 'info'
}

watch(activeTab, (v) => {
  if (v === 'registered') loadRegistered()
  if (v === 'logs') loadLogs()
})

onMounted(() => {
  loadDash()
  loadUpcoming()
})
</script>

<style scoped>
.birthday-page { padding: 0; }
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.kpi-card {
  padding: 16px 20px; border-radius: 12px; color: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08); position: relative; overflow: hidden;
}
.kpi-card::after {
  content: ''; position: absolute; right: -24px; top: -24px;
  width: 80px; height: 80px; border-radius: 50%;
  background: rgba(255,255,255,0.15);
}
.kpi-label { font-size: 13px; opacity: 0.9; }
.kpi-value { font-size: 32px; font-weight: 700; margin: 6px 0 4px; }
.kpi-sub { font-size: 12px; opacity: 0.8; }
.kpi-pink { background: linear-gradient(135deg, #f43f5e, #fb7185); }
.kpi-orange { background: linear-gradient(135deg, #f97316, #fb923c); }
.kpi-purple { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }
.kpi-blue { background: linear-gradient(135deg, #3b82f6, #60a5fa); }

.toolbar {
  display: flex; gap: 8px; align-items: center; margin-bottom: 16px;
}
.toolbar-tip { margin-left: auto; color: #909399; font-size: 12px; }
.filter-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.empty { padding: 30px; text-align: center; color: #909399; }
.field-hint { margin-left: 10px; color: #909399; font-size: 12px; }
.msg-preview {
  background: #f5f7fa; border-radius: 8px; padding: 10px 14px;
  font-size: 13px; color: #606266; line-height: 1.6;
}
.preview-footer { text-align: center; color: #909399; font-size: 13px; margin-top: 10px; }
</style>
