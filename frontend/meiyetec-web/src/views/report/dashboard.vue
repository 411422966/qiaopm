<template>
  <div class="report-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">订单数</div>
        <div class="kpi-value">{{ data.sales?.orderCount ?? '—' }}</div>
      </div>
      <div class="kpi-item green">
        <div class="kpi-label">GMV</div>
        <div class="kpi-value">¥{{ formatAmount(data.sales?.gmv) }}</div>
      </div>
      <div class="kpi-item yellow">
        <div class="kpi-label">净营收</div>
        <div class="kpi-value">¥{{ formatAmount(data.sales?.netRevenue) }}</div>
      </div>
      <div class="kpi-item red">
        <div class="kpi-label">退款金额</div>
        <div class="kpi-value">¥{{ formatAmount(data.sales?.refundAmount) }}</div>
      </div>
      <div class="kpi-item blue">
        <div class="kpi-label">客单价</div>
        <div class="kpi-value">¥{{ formatAmount(data.sales?.avgOrder) }}</div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <el-select v-model="filter.storeId" placeholder="门店" clearable style="width: 140px" @change="loadData">
        <el-option label="默认门店" :value="1" />
      </el-select>
      <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 280px" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button type="warning" @click="exportSales">⚡ 导出销售报表</el-button>
      <el-button type="warning" @click="exportPay">导出支付报表</el-button>
      <el-button type="warning" @click="exportMember">导出会员报表</el-button>
      <el-button type="warning" @click="exportCommission">导出提成报表</el-button>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="8">
        <el-card>
          <h4 style="margin: 0 0 12px">会员</h4>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="新增会员">{{ data.member?.newCount ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="活跃会员">{{ data.member?.activeCount ?? '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <h4 style="margin: 0 0 12px">提成总额</h4>
          <div style="font-size: 24px; font-weight: 700; color: #1e293b">¥{{ formatAmount(data.commission?.total) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <h4 style="margin: 0 0 12px">支付方式</h4>
          <el-table :data="data.payMethods || []" size="small" border>
            <el-table-column prop="payMethod" label="方式" />
            <el-table-column prop="cnt" label="笔数" />
            <el-table-column label="金额" align="right">
              <template #default="{ row }">¥{{ formatAmount(row.total) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="trend-card">
      <h4 style="margin: 0 0 12px">最近 14 天趋势</h4>
      <el-table :data="data.trend || []" size="small" border>
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="orderCount" label="订单数" width="120" align="center" />
        <el-table-column label="净营收" width="160" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.netRevenue) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 导出弹窗（带进度） -->
    <el-dialog v-model="taskDialog.visible" title="导出任务" width="500px">
      <el-form label-width="80px">
        <el-form-item label="状态">
          <el-tag :type="taskStatusTag(taskDialog.task?.status)" size="small">
            {{ taskStatusLabel(taskDialog.task?.status) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="进度">
          <el-progress :percentage="taskDialog.task?.progress || 0" />
        </el-form-item>
        <el-form-item v-if="taskDialog.task?.rowCount" label="行数">
          {{ taskDialog.task.rowCount }}
        </el-form-item>
        <el-form-item v-if="taskDialog.task?.fileSize" label="大小">
          {{ formatFileSize(taskDialog.task.fileSize) }}
        </el-form-item>
        <el-form-item v-if="taskDialog.task?.errorMsg" label="错误">
          <span style="color: #f56c6c">{{ taskDialog.task.errorMsg }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="taskDialog.task?.status === 3" type="success" @click="downloadFile">下载文件</el-button>
        <el-button @click="taskDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const data = reactive<any>({})
const period = ref<any>([])
const filter = reactive<{ storeId?: number }>({ storeId: undefined })
const taskDialog = reactive<any>({ visible: false, task: null, pollHandle: undefined })

async function loadData() {
  const params: any = { storeId: filter.storeId }
  if (period.value && period.value.length === 2) {
    params.startDate = period.value[0]
    params.endDate = period.value[1]
  }
  const r = await http.get('/report/v2/dashboard', { params })
  Object.assign(data, r.data || {})
}

function formatAmount(v: any) {
  if (v == null) return '0.00'
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

function formatFileSize(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(2) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

function taskStatusLabel(s: number) {
  return { 1: '待处理', 2: '处理中', 3: '成功', 4: '失败', 5: '已取消' }[s] || '未知'
}
function taskStatusTag(s: number) {
  return ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger', 5: 'info' } as any)[s] || 'info'
}

async function submitExport(code: string, name: string) {
  try {
    const r = await http.post('/report/v2/task', {
      reportCode: code,
      reportName: name,
      storeId: filter.storeId,
      startDate: period.value?.[0] || null,
      endDate: period.value?.[1] || null,
      operatorName: 'admin',
      idempotencyKey: newIdempotencyKey()
    })
    taskDialog.task = r.data
    taskDialog.visible = true
    pollTask()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '提交失败')
  }
}

function exportSales() { submitExport('SALES', '销售报表') }
function exportPay() { submitExport('PAY', '支付报表') }
function exportMember() { submitExport('MEMBER', '会员报表') }
function exportCommission() { submitExport('COMMISSION', '提成报表') }

function pollTask() {
  if (taskDialog.pollHandle) clearInterval(taskDialog.pollHandle)
  taskDialog.pollHandle = setInterval(async () => {
    if (!taskDialog.task || !taskDialog.task.id) return
    const r = await http.get(`/report/v2/task/${taskDialog.task.id}`)
    taskDialog.task = r.data
    if (taskDialog.task.status === 3 || taskDialog.task.status === 4 || taskDialog.task.status === 5) {
      clearInterval(taskDialog.pollHandle)
      taskDialog.pollHandle = undefined
    }
  }, 1000)
}

async function downloadFile() {
  if (!taskDialog.task) return
  try {
    const res = await http.get(`/report/v2/task/${taskDialog.task.id}/download`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${taskDialog.task.taskNo}_${taskDialog.task.reportCode}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已下载')
  } catch (e: any) {
    ElMessage.error('下载失败')
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-item.green { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }
.kpi-item.yellow { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.kpi-item.red { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.kpi-item.blue { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.kpi-label { font-size: 12px; color: #475569; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e293b; margin-top: 4px; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.trend-card { margin-top: 12px; }
</style>
