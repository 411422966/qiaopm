<template>
  <div class="m09-invite-page">
    <!-- 统计看板 -->
    <el-card class="stats-card" shadow="never">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">活跃活动</div>
            <div class="stat-value primary">{{ stats.activeCampaigns ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">已生成邀请码</div>
            <div class="stat-value">{{ stats.totalInviteCodes ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">扫码浏览</div>
            <div class="stat-value">{{ stats.scanCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">裂变带新率</div>
            <div class="stat-value success">{{ stats.inviteNewRate ?? 0 }}%</div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 12px">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">成功邀请</div>
            <div class="stat-value">{{ stats.rewardCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">转化率（浏览→首单）</div>
            <div class="stat-value warning">{{ stats.conversionRate ?? 0 }}%</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">已注册</div>
            <div class="stat-value">{{ stats.registerCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">已奖励</div>
            <div class="stat-value">{{ stats.successCount ?? 0 }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 标签页 -->
    <el-card class="main-card" shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 活动管理 -->
        <el-tab-pane label="活动管理" name="campaign">
          <div class="toolbar">
            <el-input v-model="campaignFilter.keyword" placeholder="编码 / 名称" style="width: 200px" clearable
              @keyup.enter="loadCampaigns" />
            <el-select v-model="campaignFilter.status" placeholder="状态" clearable style="width: 120px"
              @change="loadCampaigns">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-button type="primary" @click="openCampaignCreate">+ 新增活动</el-button>
          </div>

          <el-table :data="campaignRows" v-loading="campaignLoading" border stripe>
            <el-table-column prop="campaignCode" label="编码" width="160" />
            <el-table-column prop="campaignName" label="活动名称" min-width="160" />
            <el-table-column label="邀请人奖励" width="130">
              <template #default="{ row }">
                <span>{{ row.inviterRewardTypeText }} {{ row.inviterRewardValue }}</span>
              </template>
            </el-table-column>
            <el-table-column label="新人奖励" width="130">
              <template #default="{ row }">
                <span>{{ row.inviteeRewardTypeText }} {{ row.inviteeRewardValue }}</span>
              </template>
            </el-table-column>
            <el-table-column label="有效期" width="240">
              <template #default="{ row }">
                {{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="漏斗统计" width="200">
              <template #default="{ row }">
                浏览 {{ row.scanCount }} / 注册 {{ row.registerCount }} / 奖励 {{ row.rewardCount }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.statusText }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openCampaignEdit(row)">编辑</el-button>
                <el-button link size="small" type="primary" @click="toggleCampaign(row)">
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button link size="small" type="danger" @click="deleteCampaign(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination v-model:current-page="campaignFilter.pageNum" v-model:page-size="campaignFilter.pageSize"
              :total="campaignTotal" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
              @current-change="loadCampaigns" @size-change="loadCampaigns" />
          </div>
        </el-tab-pane>

        <!-- 邀请记录 -->
        <el-tab-pane label="邀请记录" name="record">
          <div class="toolbar">
            <el-select v-model="recordFilter.campaignId" placeholder="选择活动" clearable style="width: 200px"
              @change="loadRecords">
              <el-option v-for="c in campaignRows" :key="c.id" :label="c.campaignName" :value="c.id" />
            </el-select>
            <el-select v-model="recordFilter.status" placeholder="状态" clearable style="width: 120px"
              @change="loadRecords">
              <el-option label="待注册" :value="1" />
              <el-option label="已注册" :value="2" />
              <el-option label="已首单" :value="3" />
              <el-option label="已奖励" :value="4" />
              <el-option label="已失效" :value="5" />
              <el-option label="已拒绝" :value="6" />
            </el-select>
            <el-input v-model="recordFilter.keyword" placeholder="手机号 / 姓名 / 邀请码" style="width: 220px"
              clearable @keyup.enter="loadRecords" />
            <el-button type="primary" @click="loadRecords">查询</el-button>
          </div>

          <el-table :data="recordRows" v-loading="recordLoading" border stripe>
            <el-table-column prop="inviteCode" label="邀请码" width="110" />
            <el-table-column prop="campaignName" label="活动" min-width="140" />
            <el-table-column label="邀请人" width="100">
              <template #default="{ row }">{{ row.inviterName }}</template>
            </el-table-column>
            <el-table-column label="受邀人" width="130">
              <template #default="{ row }">
                {{ row.inviteeName }} {{ row.inviteePhone }}
              </template>
            </el-table-column>
            <el-table-column prop="channel" label="渠道" width="80" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ row.statusText }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="注册时间" width="160">
              <template #default="{ row }">{{ fmt(row.registerAt) }}</template>
            </el-table-column>
            <el-table-column label="首单时间" width="160">
              <template #default="{ row }">{{ fmt(row.firstConsumeAt) }}</template>
            </el-table-column>
            <el-table-column label="奖励发放" width="160">
              <template #default="{ row }">
                <span v-if="row.inviterRewardedAt">邀请人 {{ fmt(row.inviterRewardedAt) }}</span>
                <br />
                <span v-if="row.inviteeRewardedAt">新人 {{ fmt(row.inviteeRewardedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="viewRecordDetail(row)">详情</el-button>
                <el-button v-if="row.status === 3 || row.status === 5" link size="small" type="warning"
                  @click="resendReward(row)">重发</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination v-model:current-page="recordFilter.pageNum" v-model:page-size="recordFilter.pageSize"
              :total="recordTotal" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
              @current-change="loadRecords" @size-change="loadRecords" />
          </div>
        </el-tab-pane>

        <!-- 奖励日志 -->
        <el-tab-pane label="奖励日志" name="reward">
          <div class="toolbar">
            <el-select v-model="rewardFilter.campaignId" placeholder="选择活动" clearable style="width: 200px"
              @change="loadRewardLogs">
              <el-option v-for="c in campaignRows" :key="c.id" :label="c.campaignName" :value="c.id" />
            </el-select>
            <el-select v-model="rewardFilter.rewardType" placeholder="奖励类型" clearable style="width: 130px"
              @change="loadRewardLogs">
              <el-option label="优惠券" :value="1" />
              <el-option label="积分" :value="2" />
              <el-option label="储值" :value="3" />
            </el-select>
            <el-select v-model="rewardFilter.status" placeholder="发放状态" clearable style="width: 120px"
              @change="loadRewardLogs">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="2" />
              <el-option label="已回滚" :value="3" />
            </el-select>
            <el-button type="primary" @click="loadRewardLogs">查询</el-button>
          </div>

          <el-table :data="rewardRows" v-loading="rewardLoading" border stripe>
            <el-table-column prop="campaignName" label="活动" min-width="140" />
            <el-table-column prop="rewardTo" label="发放对象" width="80" />
            <el-table-column label="收奖人" width="130">
              <template #default="{ row }">
                {{ row.memberName }} {{ row.memberPhone }}
              </template>
            </el-table-column>
            <el-table-column label="奖励类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="rewardTypeTag(row.rewardType)">
                  {{ row.rewardTypeText }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rewardName" label="奖励内容" width="160" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="rewardStatusTag(row.status)" size="small">
                  {{ row.statusText }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="failReason" label="失败原因" min-width="200" show-overflow-tooltip />
            <el-table-column label="发放时间" width="160">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="自动" width="60">
              <template #default="{ row }">{{ row.isAuto ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination v-model:current-page="rewardFilter.pageNum" v-model:page-size="rewardFilter.pageSize"
              :total="rewardTotal" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
              @current-change="loadRewardLogs" @size-change="loadRewardLogs" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 活动创建/编辑对话框 -->
    <el-dialog v-model="campaignDialog.visible" :title="campaignDialog.id ? '编辑活动' : '新增活动'" width="700px"
      @closed="resetCampaignForm">
      <el-form :model="campaignForm" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="活动编码" required>
              <el-input v-model="campaignForm.campaignCode" :disabled="!!campaignDialog.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="活动名称" required>
              <el-input v-model="campaignForm.campaignName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">邀请人奖励</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="奖励类型" required>
              <el-select v-model="campaignForm.inviterRewardType" style="width:100%">
                <el-option label="优惠券" :value="1" />
                <el-option label="积分" :value="2" />
                <el-option label="储值" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="奖励值" required>
              <el-input-number v-model="campaignForm.inviterRewardValue" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优惠券ID">
              <el-input v-model="campaignForm.inviterRewardMeta"
                placeholder='{"couponId":1} 或纯数字' />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">新人奖励</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="奖励类型" required>
              <el-select v-model="campaignForm.inviteeRewardType" style="width:100%">
                <el-option label="优惠券" :value="1" />
                <el-option label="积分" :value="2" />
                <el-option label="储值" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="奖励值" required>
              <el-input-number v-model="campaignForm.inviteeRewardValue" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优惠券ID">
              <el-input v-model="campaignForm.inviteeRewardMeta"
                placeholder='{"couponId":2} 或纯数字' />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">触发条件</el-divider>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="新人最低消费">
              <el-input-number v-model="campaignForm.inviteeMinConsume" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单人每日上限">
              <el-input-number v-model="campaignForm.inviterQuotaPerDay" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单人累计上限">
              <el-input-number v-model="campaignForm.inviterQuotaTotal" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">时间</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始时间" required>
              <el-date-picker v-model="campaignForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="campaignForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">分享文案</el-divider>
        <el-form-item label="分享标题">
          <el-input v-model="campaignForm.shareTitle" />
        </el-form-item>
        <el-form-item label="分享描述">
          <el-input v-model="campaignForm.shareDesc" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="campaignForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="campaignForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campaignDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveCampaign" :loading="campaignDialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 邀请记录详情对话框 -->
    <el-dialog v-model="recordDetailDialog.visible" title="邀请记录详情" width="680px">
      <el-descriptions :column="2" border v-if="recordDetail">
        <el-descriptions-item label="活动">{{ recordDetail.campaignName }}</el-descriptions-item>
        <el-descriptions-item label="邀请码">{{ recordDetail.inviteCode }}</el-descriptions-item>
        <el-descriptions-item label="邀请人">{{ recordDetail.inviterName }}</el-descriptions-item>
        <el-descriptions-item label="受邀人">{{ recordDetail.inviteeName }}</el-descriptions-item>
        <el-descriptions-item label="受邀人手机">{{ recordDetail.inviteePhone }}</el-descriptions-item>
        <el-descriptions-item label="渠道">{{ recordDetail.channel }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(recordDetail.status)" size="small">{{ recordDetail.statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败原因" v-if="recordDetail.failReason">
          {{ recordDetail.failReason }}
        </el-descriptions-item>
        <el-descriptions-item label="点击时间">{{ fmt(recordDetail.clickedAt) }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ fmt(recordDetail.registerAt) }}</el-descriptions-item>
        <el-descriptions-item label="首单时间">{{ fmt(recordDetail.firstConsumeAt) }}</el-descriptions-item>
        <el-descriptions-item label="邀请人奖励发放">{{ fmt(recordDetail.inviterRewardedAt) }}</el-descriptions-item>
        <el-descriptions-item label="新人奖励发放">{{ fmt(recordDetail.inviteeRewardedAt) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ fmt(recordDetail.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <el-divider v-if="recordDetail?.rewards?.length">奖励发放明细</el-divider>
      <el-table v-if="recordDetail?.rewards?.length" :data="recordDetail.rewards" border size="small">
        <el-table-column prop="rewardTo" label="发放对象" width="80" />
        <el-table-column prop="rewardType" label="类型" width="80" />
        <el-table-column prop="rewardName" label="奖励" />
        <el-table-column prop="rewardValue" label="值" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '成功' ? 'success' : 'danger'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发放时间" width="160" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inviteApi } from '@/api/invite'
import type { InviteCampaign, InviteRecord } from '@/api/invite'

const activeTab = ref('campaign')

// ===== 看板统计 =====
const stats = ref<any>({})

async function loadStats() {
  try {
    const r = await inviteApi.stats()
    if (r.data) stats.value = r.data
  } catch (e: any) {
    // ignore
  }
}

// ===== 活动管理 =====
const campaignFilter = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: undefined as number | undefined })
const campaignRows = ref<any[]>([])
const campaignTotal = ref(0)
const campaignLoading = ref(false)

async function loadCampaigns() {
  campaignLoading.value = true
  try {
    const r = await inviteApi.campaignPage(campaignFilter)
    if (r.data?.records) {
      campaignRows.value = r.data.records
      campaignTotal.value = r.data.total
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    campaignLoading.value = false
  }
}

const campaignDialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const campaignForm = reactive<any>({
  campaignCode: '',
  campaignName: '',
  campaignType: 'INVITE_REWARD',
  inviterRewardType: 1,
  inviterRewardValue: 0,
  inviterRewardMeta: '',
  inviteeRewardType: 1,
  inviteeRewardValue: 0,
  inviteeRewardMeta: '',
  inviteeMinConsume: 0,
  inviteNeedRegister: true,
  inviterQuotaPerDay: 5,
  inviterQuotaTotal: 50,
  campaignQuotaTotal: 0,
  startTime: '',
  endTime: '',
  shareTitle: '',
  shareDesc: '',
  status: 1,
  remark: '',
})

function openCampaignCreate() {
  campaignDialog.id = undefined
  campaignDialog.visible = true
}

function openCampaignEdit(row: any) {
  campaignDialog.id = row.id
  Object.assign(campaignForm, {
    campaignCode: row.campaignCode,
    campaignName: row.campaignName,
    inviterRewardType: row.inviterRewardType,
    inviterRewardValue: row.inviterRewardValue,
    inviterRewardMeta: row.inviterRewardMeta,
    inviteeRewardType: row.inviteeRewardType,
    inviteeRewardValue: row.inviteeRewardValue,
    inviteeRewardMeta: row.inviteeRewardMeta,
    inviteeMinConsume: row.inviteeMinConsume,
    inviteNeedRegister: row.inviteNeedRegister,
    inviterQuotaPerDay: row.inviterQuotaPerDay,
    inviterQuotaTotal: row.inviterQuotaTotal,
    campaignQuotaTotal: row.campaignQuotaTotal,
    startTime: row.startTime,
    endTime: row.endTime,
    shareTitle: row.shareTitle,
    shareDesc: row.shareDesc,
    status: row.status,
    remark: row.remark,
  })
  campaignDialog.visible = true
}

function resetCampaignForm() {
  Object.assign(campaignForm, {
    campaignCode: '',
    campaignName: '',
    campaignType: 'INVITE_REWARD',
    inviterRewardType: 1,
    inviterRewardValue: 0,
    inviterRewardMeta: '',
    inviteeRewardType: 1,
    inviteeRewardValue: 0,
    inviteeRewardMeta: '',
    inviteeMinConsume: 0,
    inviteNeedRegister: true,
    inviterQuotaPerDay: 5,
    inviterQuotaTotal: 50,
    campaignQuotaTotal: 0,
    startTime: '',
    endTime: '',
    shareTitle: '',
    shareDesc: '',
    status: 1,
    remark: '',
  })
}

async function saveCampaign() {
  if (!campaignForm.campaignCode || !campaignForm.campaignName) {
    return ElMessage.warning('编码和名称必填')
  }
  campaignDialog.saving = true
  try {
    if (campaignDialog.id) {
      await inviteApi.campaignUpdate(campaignDialog.id, campaignForm)
      ElMessage.success('已更新')
    } else {
      await inviteApi.campaignCreate(campaignForm as any)
      ElMessage.success('已创建')
    }
    campaignDialog.visible = false
    loadCampaigns()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    campaignDialog.saving = false
  }
}

async function toggleCampaign(row: any) {
  await inviteApi.campaignToggle(row.id)
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  loadCampaigns()
  loadStats()
}

async function deleteCampaign(row: any) {
  await ElMessageBox.confirm(`确认删除活动「${row.campaignName}」？`, '提示', { type: 'warning' })
  await inviteApi.campaignDelete(row.id)
  ElMessage.success('已删除')
  loadCampaigns()
  loadStats()
}

// ===== 邀请记录 =====
const recordFilter = reactive({
  pageNum: 1, pageSize: 20, campaignId: undefined as number | undefined,
  status: undefined as number | undefined, keyword: ''
})
const recordRows = ref<any[]>([])
const recordTotal = ref(0)
const recordLoading = ref(false)

async function loadRecords() {
  recordLoading.value = true
  try {
    const r = await inviteApi.recordPage(recordFilter)
    if (r.data?.records) {
      recordRows.value = r.data.records
      recordTotal.value = r.data.total
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    recordLoading.value = false
  }
}

const recordDetailDialog = reactive({ visible: false })
const recordDetail = ref<any>(null)

async function viewRecordDetail(row: any) {
  const r = await inviteApi.recordDetail(row.id)
  recordDetail.value = r.data
  recordDetailDialog.visible = true
}

async function resendReward(row: any) {
  await ElMessageBox.confirm('确认重发奖励？', '提示', { type: 'warning' })
  await inviteApi.resendReward(row.id)
  ElMessage.success('重发成功')
  loadRecords()
  loadStats()
}

// ===== 奖励日志 =====
const rewardFilter = reactive({
  pageNum: 1, pageSize: 20,
  campaignId: undefined as number | undefined,
  status: undefined as number | undefined,
  rewardType: undefined as number | undefined,
})
const rewardRows = ref<any[]>([])
const rewardTotal = ref(0)
const rewardLoading = ref(false)

async function loadRewardLogs() {
  rewardLoading.value = true
  try {
    const r = await inviteApi.rewardLogPage(rewardFilter)
    if (r.data?.records) {
      rewardRows.value = r.data.records
      rewardTotal.value = r.data.total
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    rewardLoading.value = false
  }
}

// ===== 工具 =====
function fmt(s: string) {
  if (!s) return ''
  return s.substring(0, 19)
}
function statusTag(s: number) {
  return { 1: 'info', 2: 'primary', 3: 'warning', 4: 'success', 5: 'danger', 6: 'danger' }[s] || 'info'
}
function rewardTypeTag(t: number) {
  return { 1: 'success', 2: 'warning', 3: 'primary' }[t] || 'info'
}
function rewardStatusTag(s: number) {
  return { 1: 'success', 2: 'danger', 3: 'warning' }[s] || 'info'
}

onMounted(() => {
  loadStats()
  loadCampaigns()
})
</script>

<style scoped>
.stats-card {
  margin-bottom: 12px;
}
.stat-item {
  text-align: center;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.stat-value.primary { color: #409eff; }
.stat-value.success { color: #67c23a; }
.stat-value.warning { color: #e6a23c; }

.main-card {
  /* 已有 padding */
}

.toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.toolbar .el-input,
.toolbar .el-select {
  margin-right: 0;
}

.pagination {
  margin-top: 12px;
  text-align: right;
}
</style>
