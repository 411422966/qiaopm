<template>
  <div class="complaint-page">

    <!-- 顶部统计卡片 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-total" @click="filter.status = undefined; filter.pageNum = 1; loadData()">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">投诉总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-pending" @click="filter.status = 1; filter.pageNum = 1; loadData()">
          <div class="stat-value">{{ stats.pendingAssign }}</div>
          <div class="stat-label">待分配</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-progress" @click="filter.status = 3; filter.pageNum = 1; loadData()">
          <div class="stat-value">{{ stats.inProgress }}</div>
          <div class="stat-label">处理中</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-overdue" @click="filter.sla = 'overdue'; filter.pageNum = 1; loadData()">
          <div class="stat-value">{{ stats.overdue }}</div>
          <div class="stat-label">超时未解决</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="会员 / 手机号 / 工单号 / 主题" style="width: 200px" clearable @keyup.enter="filter.pageNum = 1; loadData()" />
      <el-select v-model="filter.complaintType" placeholder="投诉类型" clearable style="width: 130px" @change="filter.pageNum = 1; loadData()">
        <el-option label="服务" value="SERVICE" />
        <el-option label="员工" value="STAFF" />
        <el-option label="产品" value="PRODUCT" />
        <el-option label="价格" value="PRICE" />
        <el-option label="环境" value="ENVIRONMENT" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <el-select v-model="filter.level" placeholder="严重级别" clearable style="width: 120px" @change="filter.pageNum = 1; loadData()">
        <el-option label="一般" :value="1" />
        <el-option label="较严重" :value="2" />
        <el-option label="严重" :value="3" />
        <el-option label="紧急" :value="4" />
      </el-select>
      <el-select v-model="filter.status" placeholder="工单状态" clearable style="width: 130px" @change="filter.pageNum = 1; loadData()">
        <el-option label="待分配" :value="1" />
        <el-option label="已分配" :value="2" />
        <el-option label="处理中" :value="3" />
        <el-option label="等待客户" :value="4" />
        <el-option label="已解决" :value="5" />
        <el-option label="已关闭" :value="6" />
        <el-option label="已升级" :value="7" />
      </el-select>
      <el-select v-model="filter.sla" placeholder="SLA状态" clearable style="width: 140px" @change="filter.pageNum = 1; loadData()">
        <el-option label="即将超时" value="soon" />
        <el-option label="已超时" value="overdue" />
        <el-option label="正常" value="ok" />
      </el-select>
      <el-date-picker v-model="filter.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
        style="width: 240px" value-format="YYYY-MM-DD" @change="filter.pageNum = 1; loadData()" />
      <el-button type="primary" @click="filter.pageNum = 1; loadData()">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <div style="flex: 1" />
      <el-button type="warning" @click="openCreate">+ 新建工单</el-button>
      <el-button @click="seedDemo" :loading="seeding">演示数据</el-button>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span class="table-title">投诉工单列表</span>
          <div class="header-actions">
            <el-button size="small" :loading="loading" @click="refresh">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="ticketNo" label="工单号" width="165" fixed />
        <el-table-column label="会员" width="160">
          <template #default="{ row }">
            <div>{{ row.memberName || '—' }}</div>
            <div style="font-size: 12px; color: #999">{{ row.memberPhone || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.complaintType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="75">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="主题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="storeName" label="门店" width="110" show-overflow-tooltip />
        <el-table-column label="SLA 截止" width="150">
          <template #default="{ row }">
            <span v-if="!row.slaExpireAt">—</span>
            <span v-else :class="slaClass(row)">{{ row.slaExpireAt?.slice(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-muted': !row.handlerName }">{{ row.handlerName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="升级" width="60" align="center">
          <template #default="{ row }">
            <el-badge v-if="row.escalateCount > 0" :value="row.escalateCount" type="danger" />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.refundAmount > 0" style="color: #e6a23c">¥{{ row.refundAmount }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="150" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link size="small" :disabled="row.status !== 1 && row.status !== 7" @click="openAssign(row)">分配</el-button>
            <el-button link size="small" :disabled="row.status !== 2" @click="process(row)">处理</el-button>
            <el-button link size="small" :disabled="row.status !== 3 && row.status !== 4" @click="openResolve(row)">解决</el-button>
            <el-button link size="small" type="success" :disabled="row.status !== 5" @click="openClose(row)">关闭</el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 5 || row.status === 6" @click="escalate(row)">升级</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新建工单 -->
    <el-dialog v-model="createDialog.visible" title="新建投诉工单" width="640px" @closed="resetCreateForm">
      <el-form :model="createForm" label-width="100px" :rules="createRules" ref="createFormRef">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="会员姓名" prop="memberName">
              <el-input v-model="createForm.memberName" placeholder="投诉会员姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="memberPhone">
              <el-input v-model="createForm.memberPhone" placeholder="会员手机" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="投诉类型" prop="complaintType">
              <el-select v-model="createForm.complaintType" style="width: 100%">
                <el-option label="服务问题" value="SERVICE" />
                <el-option label="员工问题" value="STAFF" />
                <el-option label="产品问题" value="PRODUCT" />
                <el-option label="价格问题" value="PRICE" />
                <el-option label="环境问题" value="ENVIRONMENT" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="严重级别" prop="level">
              <el-radio-group v-model="createForm.level">
                <el-radio :value="1">一般</el-radio>
                <el-radio :value="2">较严重</el-radio>
                <el-radio :value="3">严重</el-radio>
                <el-radio :value="4">紧急</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="SLA 时长">
              <el-select v-model="createForm.slaMinutes" style="width: 100%">
                <el-option label="4 小时" :value="240" />
                <el-option label="8 小时" :value="480" />
                <el-option label="24 小时" :value="1440" />
                <el-option label="48 小时" :value="2880" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联来源">
              <el-select v-model="createForm.sourceType" style="width: 100%" clearable>
                <el-option label="订单" value="ORDER" />
                <el-option label="套餐卡" value="CARD" />
                <el-option label="会员" value="MEMBER" />
                <el-option label="回访" value="FOLLOW_UP" />
                <el-option label="评价" value="REVIEW" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="主题" prop="subject">
          <el-input v-model="createForm.subject" placeholder="简要描述投诉内容" />
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="createForm.content" type="textarea" :rows="3" placeholder="详细说明投诉情况..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveCreate" :loading="createDialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情侧抽屉 -->
    <el-drawer v-model="detailDrawer.visible" :title="`工单详情 ${detailDrawer.ticket?.ticketNo || ''}`" size="560px" direction="rtl">
      <div v-if="detailDrawer.ticket" class="detail-content">
        <div class="detail-status-bar">
          <el-tag :type="statusTag(detailDrawer.ticket.status)" size="large">{{ statusLabel(detailDrawer.ticket.status) }}</el-tag>
          <el-tag v-if="slaClass(detailDrawer.ticket) === 'sla-overdue'" type="danger" size="large">已超时</el-tag>
          <el-tag v-else-if="slaClass(detailDrawer.ticket) === 'sla-soon'" type="warning" size="large">即将超时</el-tag>
        </div>

        <el-divider content-position="left">基本信息</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ detailDrawer.ticket.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ detailDrawer.ticket.storeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ detailDrawer.ticket.memberName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ detailDrawer.ticket.memberPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag size="small">{{ typeLabel(detailDrawer.ticket.complaintType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="级别">
            <el-tag :type="levelTag(detailDrawer.ticket.level)" size="small">{{ levelLabel(detailDrawer.ticket.level) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="主题" :span="2">{{ detailDrawer.ticket.subject }}</el-descriptions-item>
          <el-descriptions-item label="详细描述" :span="2">{{ detailDrawer.ticket.content || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detailDrawer.ticket.handlerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="升级次数">{{ detailDrawer.ticket.escalateCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="SLA 截止">{{ detailDrawer.ticket.slaExpireAt?.slice(0, 16) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailDrawer.ticket.createTime?.slice(0, 16) || '—' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detailDrawer.ticket.solution">
          <el-divider content-position="left">解决方案</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="方案类型">
              <el-tag size="small">{{ solutionTypeLabel(detailDrawer.ticket.solutionType) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="退款金额">
              <span v-if="detailDrawer.ticket.refundAmount > 0" style="color: #e6a23c">¥{{ detailDrawer.ticket.refundAmount }}</span>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="方案说明" :span="2">{{ detailDrawer.ticket.solution }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <el-divider content-position="left">
          工单日志
          <el-badge :value="(detailDrawer.logs || []).length" class="log-count" />
        </el-divider>
        <div class="timeline-wrap">
          <el-empty v-if="!detailDrawer.logs?.length" description="暂无日志" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="log in detailDrawer.logs"
              :key="log.id"
              :timestamp="log.createTime?.slice(0, 16) || ''"
              placement="top"
              :type="logColor(log.actionType)"
            >
              <el-tag size="small" :type="logColor(log.actionType)">{{ log.actionType }}</el-tag>
              <span style="margin-left: 8px; font-size: 13px">
                {{ statusLabel(log.fromStatus) || '' }}<template v-if="log.fromStatus && log.toStatus"> → </template>{{ statusLabel(log.toStatus) || '' }}
              </span>
              <p v-if="log.content" style="margin: 6px 0 0; color: #475569; font-size: 13px">{{ log.content }}</p>
              <p style="margin: 2px 0 0; font-size: 12px; color: #94a3b8">操作人: {{ log.operatorName || 'system' }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button v-if="detailDrawer.ticket?.status === 1 || detailDrawer.ticket?.status === 7"
            type="primary" plain @click="openAssignFromDrawer">分配处理人</el-button>
          <el-button v-if="detailDrawer.ticket?.status === 3 || detailDrawer.ticket?.status === 4"
            type="success" plain @click="openResolveFromDrawer">解决方案</el-button>
          <el-button v-if="detailDrawer.ticket?.status === 5"
            type="success" plain @click="openCloseFromDrawer">关闭工单</el-button>
          <el-button v-if="detailDrawer.ticket?.status !== 5 && detailDrawer.ticket?.status !== 6"
            type="danger" plain @click="escalateFromDrawer">升级</el-button>
          <el-button v-if="detailDrawer.ticket?.status === 5 || detailDrawer.ticket?.status === 6"
            type="warning" plain @click="exportPdf">导出 PDF</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 分配 -->
    <el-dialog v-model="assignDialog.visible" title="分配处理人" width="480px">
      <el-form label-width="80px">
        <el-form-item label="处理人姓名" required>
          <el-input v-model="assignDialog.handlerName" placeholder="输入处理人员工姓名" />
        </el-form-item>
        <el-form-item label="员工ID">
          <el-input v-model.number="assignDialog.handlerId" type="number" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="assign" :loading="assignDialog.loading">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 解决 -->
    <el-dialog v-model="resolveDialog.visible" title="提交解决方案" width="560px">
      <el-form label-width="110px">
        <el-form-item label="解决方案" required>
          <el-select v-model="resolveDialog.solutionType" style="width: 100%">
            <el-option label="退款" value="REFUND" />
            <el-option label="更换产品" value="EXCHANGE" />
            <el-option label="道歉" value="APOLOGY" />
            <el-option label="赔偿" value="COMPENSATION" />
            <el-option label="无需处理" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="resolveDialog.solutionType === 'REFUND' || resolveDialog.solutionType === 'COMPENSATION'" label="退款/赔偿金额">
          <el-input-number v-model="resolveDialog.refundAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="方案说明">
          <el-input v-model="resolveDialog.solution" type="textarea" :rows="3" placeholder="描述解决方案..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="resolve" :loading="resolveDialog.loading">提交</el-button>
      </template>
    </el-dialog>

    <!-- 关闭工单（含满意度评价） -->
    <el-dialog v-model="closeDialog.visible" title="关闭工单" width="560px">
      <div v-if="closeDialog.ticket" class="close-dialog-body">
        <el-alert type="success" :closable="false" show-icon style="margin-bottom: 16px">
          工单 <strong>{{ closeDialog.ticket.ticketNo }}</strong> 已解决，当前状态：<el-tag type="success" size="small">已解决</el-tag>
        </el-alert>

        <el-divider content-position="left">满意度评价</el-divider>

        <el-form label-width="100px">
          <el-form-item label="整体评分" required>
            <div class="star-rating">
              <el-rate v-model="closeDialog.rating" :colors="['#99A9BF', '#F7BA2A', '#FF9900']" show-text
                :texts="['非常不满意', '不满意', '一般', '满意', '非常满意']" size="large" />
            </div>
          </el-form-item>
          <el-form-item label="评价备注">
            <el-input v-model="closeDialog.note" type="textarea" :rows="3" placeholder="会员对本次投诉处理的评价与建议..." />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="closeDialog.visible = false">取消</el-button>
        <el-button @click="submitClose(false)" :loading="closeDialog.loading">仅关闭（跳过评价）</el-button>
        <el-button type="success" @click="submitClose(true)" :loading="closeDialog.loading">提交评价并关闭</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import http from '@/utils/http'
import { newIdempotencyKey } from '@/utils/idempotency'

// 统计数据
const stats = reactive({ total: 0, pendingAssign: 0, inProgress: 0, overdue: 0, resolved: 0 })

// 筛选条件
const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', complaintType: undefined as string | undefined,
  level: undefined as number | undefined, status: undefined as number | undefined,
  sla: undefined as string | undefined, dateRange: undefined as string[] | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const seeding = ref(false)

// 辅助函数
function typeLabel(t: string) {
  return ({ SERVICE: '服务', STAFF: '员工', PRODUCT: '产品', PRICE: '价格', ENVIRONMENT: '环境', OTHER: '其他' } as any)[t] || t || '—'
}
function levelLabel(l: number) {
  return { 1: '一般', 2: '较严重', 3: '严重', 4: '紧急' }[l] || '—'
}
function levelTag(l: number) {
  return ({ 1: 'info', 2: 'warning', 3: 'danger', 4: 'danger' } as any)[l] || 'info'
}
function statusLabel(s: number) {
  return { 1: '待分配', 2: '已分配', 3: '处理中', 4: '等待客户', 5: '已解决', 6: '已关闭', 7: '已升级' }[s] || '—'
}
function statusTag(s: number) {
  return ({ 1: 'info', 2: 'primary', 3: 'warning', 4: 'warning', 5: 'success', 6: 'info', 7: 'danger' } as any)[s] || 'info'
}
function solutionTypeLabel(t: string) {
  return ({ REFUND: '退款', EXCHANGE: '更换', APOLOGY: '道歉', COMPENSATION: '赔偿', NONE: '无需处理' } as any)[t] || t || '—'
}
function slaClass(row: any) {
  if (!row.slaExpireAt || row.status === 5 || row.status === 6) return ''
  const expire = new Date(row.slaExpireAt)
  const now = new Date()
  const diff = expire.getTime() - now.getTime()
  if (diff < 0) return 'sla-overdue'
  if (diff < 4 * 3600 * 1000) return 'sla-soon'
  return ''
}
function logColor(action: string) {
  return ({ CREATE: 'primary', ASSIGN: 'primary', REPLY: 'info', RESOLVE: 'success', ESCALATE: 'danger', CLOSE: 'info' } as any)[action] || 'info'
}

// 加载统计
async function loadStats() {
  try {
    const r = await http.get('/crm/care/complaint/stats')
    Object.assign(stats, r.data || {})
  } catch { /* 静默 */ }
}

// 加载列表
async function loadData() {
  loading.value = true
  try {
    const params: any = { ...filter }
    if (!params.complaintType) delete params.complaintType
    if (!params.level) delete params.level
    if (!params.status) delete params.status
    if (!params.sla) delete params.sla
    if (!params.dateRange) delete params.dateRange
    const r = await http.get('/crm/care/complaint/page', { params })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await Promise.all([loadStats(), loadData()])
}

function resetFilter() {
  Object.assign(filter, {
    pageNum: 1, pageSize: 20,
    keyword: '', complaintType: undefined, level: undefined,
    status: undefined, sla: undefined, dateRange: undefined
  })
  loadData()
}

// 新建工单
const createDialog = reactive({ visible: false, saving: false })
const createFormRef = ref<any>()
const createForm = reactive({
  memberName: '', memberPhone: '',
  complaintType: 'SERVICE', level: 1, slaMinutes: 1440,
  sourceType: undefined as string | undefined,
  subject: '', content: ''
})
const createRules = {
  memberName: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }],
  complaintType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  subject: [{ required: true, message: '请输入主题', trigger: 'blur' }]
}

function openCreate() { createDialog.visible = true }
function resetCreateForm() {
  Object.assign(createForm, {
    memberName: '', memberPhone: '',
    complaintType: 'SERVICE', level: 1, slaMinutes: 1440,
    sourceType: undefined, subject: '', content: ''
  })
  createFormRef.value?.clearValidate()
}

async function saveCreate() {
  createFormRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    createDialog.saving = true
    try {
      await http.post('/crm/care/complaint', createForm, {
        headers: { 'Idempotency-Key': newIdempotencyKey() }
      })
      ElMessage.success('工单已创建')
      createDialog.visible = false
      await Promise.all([loadStats(), loadData()])
    } catch {
      ElMessage.error('创建失败')
    } finally {
      createDialog.saving = false
    }
  })
}

// 演示数据
async function seedDemo() {
  seeding.value = true
  try {
    await http.post('/crm/care/complaint/demo-seed')
    ElMessage.success('演示数据已插入')
    await Promise.all([loadStats(), loadData()])
  } catch {
    ElMessage.error('插入失败')
  } finally {
    seeding.value = false
  }
}

// 详情抽屉
const detailDrawer = reactive<any>({ visible: false, ticket: null, logs: null })

async function openDetail(row: any) {
  try {
    const r = await http.get(`/crm/care/complaint/${row.id}`)
    detailDrawer.ticket = r.data?.ticket
    detailDrawer.logs = r.data?.logs || []
    detailDrawer.visible = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

function openAssignFromDrawer() {
  detailDrawer.visible = false
  setTimeout(() => openAssign(detailDrawer.ticket), 100)
}
function openResolveFromDrawer() {
  detailDrawer.visible = false
  setTimeout(() => openResolve(detailDrawer.ticket), 100)
}
function openCloseFromDrawer() {
  detailDrawer.visible = false
  setTimeout(() => openClose(detailDrawer.ticket), 100)
}
function escalateFromDrawer() {
  detailDrawer.visible = false
  setTimeout(() => escalate(detailDrawer.ticket), 100)
}

// 分配
const assignDialog = reactive<any>({ visible: false, ticket: null, handlerName: '', handlerId: undefined, loading: false })
function openAssign(row: any) {
  assignDialog.ticket = row
  assignDialog.handlerName = row.handlerName || ''
  assignDialog.handlerId = undefined
  assignDialog.visible = true
}
async function assign() {
  if (!assignDialog.handlerName) return ElMessage.warning('请输入处理人姓名')
  assignDialog.loading = true
  try {
    await http.post(`/crm/care/complaint/${assignDialog.ticket.id}/assign`, {
      handlerName: assignDialog.handlerName,
      handlerId: assignDialog.handlerId
    })
    ElMessage.success('已分配')
    assignDialog.visible = false
    await Promise.all([loadStats(), loadData()])
  } catch {
    ElMessage.error('分配失败')
  } finally {
    assignDialog.loading = false
  }
}

// 处理
async function process(row: any) {
  try {
    await http.post(`/crm/care/complaint/${row.id}/process`)
    ElMessage.success('已开始处理')
    await Promise.all([loadStats(), loadData()])
  } catch {
    ElMessage.error('操作失败')
  }
}

// 解决
const resolveDialog = reactive<any>({ visible: false, ticket: null, solutionType: 'NONE', solution: '', refundAmount: 0, loading: false })
function openResolve(row: any) {
  resolveDialog.ticket = row
  resolveDialog.solutionType = 'NONE'
  resolveDialog.solution = ''
  resolveDialog.refundAmount = 0
  resolveDialog.visible = true
}
async function resolve() {
  resolveDialog.loading = true
  try {
    await http.post(`/crm/care/complaint/${resolveDialog.ticket.id}/resolve`, {
      solutionType: resolveDialog.solutionType,
      solution: resolveDialog.solution,
      refundAmount: resolveDialog.refundAmount
    })
    ElMessage.success('已解决，请填写满意度评价')
    resolveDialog.visible = false
    await Promise.all([loadStats(), loadData()])
    // 自动弹出关闭+评价对话框
    setTimeout(() => openClose(resolveDialog.ticket), 300)
  } catch {
    ElMessage.error('解决失败')
  } finally {
    resolveDialog.loading = false
  }
}

// 关闭（含满意度评价）
const closeDialog = reactive<any>({ visible: false, ticket: null, rating: 0, note: '', loading: false })
function openClose(row: any) {
  closeDialog.ticket = row
  closeDialog.rating = 0
  closeDialog.note = ''
  closeDialog.visible = true
}
async function submitClose(withRating: boolean) {
  if (withRating && closeDialog.rating === 0) {
    return ElMessage.warning('请至少选择一颗星')
  }
  closeDialog.loading = true
  try {
    // 关闭工单
    await http.post(`/crm/care/complaint/${closeDialog.ticket.id}/close`, {
      satisfactionRating: withRating ? closeDialog.rating : undefined,
      satisfactionNote: withRating ? closeDialog.note : undefined
    })
    if (withRating) {
      // 同步写入满意度表
      try {
        await http.post('/complaint/satisfaction', {
          ticketId: closeDialog.ticket.id,
          rating: closeDialog.rating,
          note: closeDialog.note,
          ratedAt: new Date().toISOString()
        })
      } catch { /* 幂等写入，忽略重复评分报错 */ }
      ElMessage.success('已关闭，感谢您的评价！')
    } else {
      ElMessage.success('工单已关闭')
    }
    closeDialog.visible = false
    await Promise.all([loadStats(), loadData()])
  } catch {
    ElMessage.error('关闭失败')
  } finally {
    closeDialog.loading = false
  }
}

// 升级
async function escalate(row: any) {
  try {
    await http.post(`/crm/care/complaint/${row.id}/escalate`)
    ElMessage.warning('已升级')
    await Promise.all([loadStats(), loadData()])
  } catch {
    ElMessage.error('升级失败')
  }
}

// PDF 导出（利用浏览器打印为 PDF）
function exportPdf() {
  const ticket = detailDrawer.ticket
  const logs = detailDrawer.logs || []
  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>投诉工单 ${ticket.ticketNo}</title>
<style>
  body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; padding: 40px; color: #333; }
  h1 { text-align: center; font-size: 22px; border-bottom: 2px solid #409eff; padding-bottom: 12px; margin-bottom: 24px; }
  .section { margin-bottom: 24px; }
  .section-title { font-size: 16px; font-weight: 700; color: #409eff; border-left: 4px solid #409eff; padding-left: 8px; margin-bottom: 12px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
  td, th { border: 1px solid #ddd; padding: 8px 12px; font-size: 14px; }
  th { background: #f5f7fa; font-weight: 600; width: 120px; color: #666; }
  .log-item { border-left: 3px solid #409eff; padding: 8px 12px; margin-bottom: 10px; background: #f9f9f9; }
  .log-time { font-size: 12px; color: #999; }
  .log-action { font-weight: 600; color: #409eff; margin: 4px 0; }
  .log-content { font-size: 13px; color: #555; }
  .footer { text-align: center; font-size: 12px; color: #999; margin-top: 32px; }
  @media print { body { padding: 20px; } }
</style>
</head>
<body>
<h1>投诉工单详情</h1>

<div class="section">
  <div class="section-title">基本信息</div>
  <table>
    <tr><th>工单号</th><td>${ticket.ticketNo}</td><th>状态</th><td>${statusLabel(ticket.status)}</td></tr>
    <tr><th>会员</th><td>${ticket.memberName || '—'}</td><th>手机</th><td>${ticket.memberPhone || '—'}</td></tr>
    <tr><th>门店</th><td>${ticket.storeName || '—'}</td><th>处理人</th><td>${ticket.handlerName || '—'}</td></tr>
    <tr><th>类型</th><td>${typeLabel(ticket.complaintType)}</td><th>级别</th><td>${levelLabel(ticket.level)}</td></tr>
    <tr><th>SLA 截止</th><td>${ticket.slaExpireAt || '—'}</td><th>升级次数</th><td>${ticket.escalateCount || 0}</td></tr>
    <tr><th>主题</th><td colspan="3">${ticket.subject}</td></tr>
    <tr><th>详细描述</th><td colspan="3">${ticket.content || '—'}</td></tr>
  </table>
</div>

${ticket.solution ? `<div class="section">
  <div class="section-title">解决方案</div>
  <table>
    <tr><th>方案类型</th><td>${solutionTypeLabel(ticket.solutionType)}</td><th>退款金额</th><td>${ticket.refundAmount > 0 ? '¥' + ticket.refundAmount : '—'}</td></tr>
    <tr><th>方案说明</th><td colspan="3">${ticket.solution}</td></tr>
  </table>
</div>` : ''}

<div class="section">
  <div class="section-title">工单日志（共 ${logs.length} 条）</div>
  ${logs.map((log: any) => `
  <div class="log-item">
    <div class="log-time">${log.createTime || ''}</div>
    <div class="log-action">${log.actionType} ${log.fromStatus ? statusLabel(log.fromStatus) + ' → ' : ''}${log.toStatus ? statusLabel(log.toStatus) : ''}</div>
    ${log.content ? `<div class="log-content">${log.content}</div>` : ''}
    <div class="log-time">操作人: ${log.operatorName || 'system'}</div>
  </div>`).join('')}
</div>

<div class="footer">
  <p>本文件由美业 CRM 系统自动生成 · ${new Date().toLocaleString('zh-CN')}</p>
  <p>工单编号：${ticket.ticketNo} · 系统地址：http://localhost:5173</p>
</div>
</body>
</html>`

  const win = window.open('', '_blank')
  if (win) {
    win.document.write(html)
    win.document.close()
    win.onload = () => {
      win.print()
    }
  } else {
    ElMessage.error('请允许弹出窗口以导出 PDF')
  }
}

onMounted(async () => {
  await Promise.all([loadStats(), loadData()])
})
</script>

<style scoped>
.complaint-page { padding: 12px; }

.stats-row { margin-bottom: 12px; }
.stat-card { cursor: pointer; transition: transform 0.15s; text-align: center; }
.stat-card:hover { transform: translateY(-2px); }
.stat-value { font-size: 32px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #666; margin-top: 4px; }
.stat-total .stat-value { color: #409eff; }
.stat-pending .stat-value { color: #909399; }
.stat-progress .stat-value { color: #e6a23c; }
.stat-overdue .stat-value { color: #f56c6c; }

.search-bar { margin-bottom: 12px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.search-bar .el-input, .search-bar .el-select { margin-right: 0; }

.table-header { display: flex; justify-content: space-between; align-items: center; }
.table-title { font-weight: 600; font-size: 15px; }
.header-actions { display: flex; gap: 8px; }

.table-card { }
.pagination { margin-top: 12px; text-align: right; }

.sla-overdue { color: #f56c6c; font-weight: 600; }
.sla-soon { color: #e6a23c; font-weight: 600; }
.text-muted { color: #bbb; }

.detail-content { padding: 0 16px; }
.detail-status-bar { display: flex; gap: 8px; margin-bottom: 16px; }
.log-count { margin-left: 8px; }
.timeline-wrap { max-height: 360px; overflow-y: auto; padding-right: 4px; }
.drawer-footer { display: flex; gap: 8px; flex-wrap: wrap; }

.close-dialog-body { }
.star-rating { display: flex; align-items: center; }
</style>
