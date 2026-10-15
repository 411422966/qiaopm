<template>
  <div class="page">
    <div class="hero">
      <div>
        <h2>导出管理</h2>
        <p>按报表类型生成 Excel 导出任务，跟踪记录并下载历史文件。</p>
      </div>
      <el-button type="primary" @click="openForm">
        <el-icon><Download /></el-icon> 新建导出
      </el-button>
    </div>

    <!-- KPI -->
    <div class="kpi">
      <div class="kpi-card">
        <div class="stat-icon" style="background:#e3f2fd;color:#1976d2"><el-icon><Document /></el-icon></div>
        <div><div class="stat-label">总任务数</div><div class="stat-value">{{ totalCount }}</div></div>
      </div>
      <div class="kpi-card">
        <div class="stat-icon" style="background:#e8f5e9;color:#4caf50"><el-icon><CircleCheck /></el-icon></div>
        <div><div class="stat-label">成功任务</div><div class="stat-value">{{ successCount }}</div></div>
      </div>
      <div class="kpi-card">
        <div class="stat-icon" style="background:#fff8e1;color:#ff9800"><el-icon><DataAnalysis /></el-icon></div>
        <div><div class="stat-label">总记录数</div><div class="stat-value">{{ totalRowsCount.toLocaleString() }}</div></div>
      </div>
      <div class="kpi-card">
        <div class="stat-icon" style="background:#fce4ec;color:#e91e63"><el-icon><Coin /></el-icon></div>
        <div><div class="stat-label">总导出大小</div><div class="stat-value">{{ formatSize(totalSizeBytes) }}</div></div>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card class="filter-card">
      <el-select v-model="filter.type" placeholder="报表类型" clearable @change="load" style="width:160px">
        <el-option label="营业日报" :value="1" />
        <el-option label="营业月报" :value="2" />
        <el-option label="会员分析" :value="3" />
        <el-option label="员工绩效" :value="4" />
        <el-option label="支付方式" :value="5" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable @change="load" style="width:120px">
        <el-option label="处理中" :value="1" />
        <el-option label="成功" :value="2" />
        <el-option label="失败" :value="3" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </el-card>

    <!-- 列表 -->
    <el-card>
      <el-table :data="rows" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="任务名称" min-width="200" />
        <el-table-column label="报表类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dateRange" label="时间范围" width="140" />
        <el-table-column prop="rowCount" label="记录数" width="100" align="right" />
        <el-table-column label="文件大小" width="120" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              <el-icon v-if="row.status === 1" class="spin"><Loading /></el-icon>
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exportBy" label="导出人" width="100" />
        <el-table-column label="导出时间" width="180">
          <template #default="{ row }">
            {{ row.finishedAt ? row.finishedAt.replace('T', ' ').slice(0, 19) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" size="small" disabled>
              <el-icon><Loading /></el-icon> 处理中
            </el-button>
            <el-button v-if="row.status === 2" type="primary" size="small" @click="downloadAgain(row)">
              下载
            </el-button>
            <template v-if="row.status === 3">
              <el-button type="danger" size="small" @click="showError(row)">
                失败原因
              </el-button>
              <el-button type="warning" size="small" @click="retryExport(row)">
                重试
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新建导出弹窗 -->
    <el-dialog v-model="formVisible" title="新建导出" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.name" placeholder="例如：8月营业日报" />
        </el-form-item>
        <el-form-item label="报表类型" required>
          <el-select v-model="form.type" style="width:100%">
            <el-option label="营业日报" :value="1" />
            <el-option label="营业月报" :value="2" />
            <el-option label="会员分析" :value="3" />
            <el-option label="员工绩效" :value="4" />
            <el-option label="支付方式" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围" required>
          <el-select v-model="form.dateRange" style="width:100%">
            <el-option label="今日" value="today" />
            <el-option label="近7天" value="last_7_days" />
            <el-option label="近30天" value="last_30_days" />
            <el-option label="近季度" value="last_quarter" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">开始导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Document, CircleCheck, DataAnalysis, Coin, Loading } from '@element-plus/icons-vue'
import { reportApi } from '@/api/report'

const rows = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const filter = reactive<{ type: number | null; status: number | null }>({ type: null, status: null })

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', type: 1, dateRange: 'last_30_days' })

const successCount = ref(0)
const totalCount = ref(0)
const totalRowsCount = ref(0)
const totalSizeBytes = ref(0)

function typeLabel(t: number) {
  return ['', '营业日报', '营业月报', '会员分析', '员工绩效', '支付方式'][t] || '其他'
}
function statusType(s: number) {
  return s === 2 ? 'success' : s === 3 ? 'danger' : 'warning'
}
function statusLabel(s: number) {
  return s === 1 ? '处理中' : s === 2 ? '成功' : '失败'
}
function formatSize(b: number) {
  if (!b) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let n = Number(b)
  while (n >= 1024 && i < units.length - 1) { n /= 1024; i++ }
  return `${n.toFixed(1)} ${units[i]}`
}

async function load() {
  loading.value = true
  try {
    const res: any = await reportApi.exportList(pageNum.value, pageSize.value, filter.type ?? undefined, filter.status ?? undefined)
    rows.value = res.data.records || []
    total.value = res.data.total
    const meta = res.data.meta || {}
    totalCount.value = meta.totalCount || 0
    successCount.value = meta.successCount || 0
    totalRowsCount.value = meta.totalRows || 0
    totalSizeBytes.value = meta.totalSize || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  filter.type = null
  filter.status = null
  load()
}

function openForm() {
  Object.assign(form, { name: '', type: 1, dateRange: 'last_30_days' })
  formVisible.value = true
}

async function submit() {
  if (!form.name) {
    ElMessage.warning('请填写任务名称')
    return
  }
  saving.value = true
  try {
    await reportApi.exportCreate(form)
    ElMessage.success('导出任务已创建，处理中...')
    formVisible.value = false
    // 刷新列表 + 保持当前筛选
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    saving.value = false
  }
}

function downloadAgain(row: any) {
  const token = localStorage.getItem('token') || ''
  const url = reportApi.exportDownload(row.id)
  fetch(url, { headers: { Authorization: 'Bearer ' + token } })
    .then(r => {
      if (!r.ok) throw new Error('HTTP ' + r.status)
      return r.blob()
    })
    .then(blob => {
      const objectUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = objectUrl
      a.download = (row.name || '报表') + '.xlsx'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(objectUrl)
    })
    .catch(e => ElMessage.error('下载失败：' + e.message))
}

function showError(row: any) {
  ElMessage.error(row.errorMsg || '导出失败')
}

async function retryExport(row: any) {
  try {
    await reportApi.exportCreate({ name: row.name + ' (重试)', type: row.type, dateRange: row.dateRange })
    ElMessage.success('已重新创建任务')
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '重试失败')
  }
}

let pollTimer: any = null
function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (rows.value.some(r => r.status === 1)) {
      load()
    } else {
      stopPolling()
    }
  }, 3000)
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  load().then(startPolling)
})
onUnmounted(stopPolling)
</script>

<style scoped>
.spin { animation: rotating 1.2s linear infinite; vertical-align: -2px; margin-right: 2px; }
@keyframes rotating { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.page { display:flex; flex-direction:column; gap:14px }
.hero { display:flex; align-items:center; justify-content:space-between; padding:22px 26px; border-radius:12px; color:#fff; background:linear-gradient(120deg,#ff9800,#ffb74d); box-shadow:0 8px 24px #ff980033 }
.hero h2 { margin:0 0 6px }
.hero p { margin:0; opacity:.88 }
.kpi { display:grid; grid-template-columns:repeat(4,1fr); gap:14px }
.kpi-card { background:#fff; padding:18px; border-radius:12px; display:flex; align-items:center; gap:14px; box-shadow:0 4px 16px #00000010 }
.stat-icon { width:48px; height:48px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:22px }
.stat-label { color:#909399; font-size:13px; margin-bottom:4px }
.stat-value { font-size:22px; font-weight:600; color:#303133 }
.filter-card :deep(.el-card__body) { display:flex; gap:10px; align-items:center }
.table-footer { display:flex; justify-content:flex-end; margin-top:14px }
</style>
