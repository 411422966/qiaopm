<template>
  <div class="page">
    <div class="stats-row">
      <div class="stat-card stat-today">
        <div class="stat-icon">🚪</div>
        <div class="stat-body">
          <div class="stat-label">今日登录</div>
          <div class="stat-value">{{ stats.todayCount ?? 0 }}</div>
          <div class="stat-sub">总 {{ stats.totalCount ?? 0 }} 条</div>
        </div>
      </div>
      <div class="stat-card stat-rate-today">
        <div class="stat-icon">✅</div>
        <div class="stat-body">
          <div class="stat-label">今日成功率</div>
          <div class="stat-value">{{ stats.todaySuccessRate ?? 100 }}<small>%</small></div>
          <div class="stat-sub">失败 {{ stats.todayFailCount ?? 0 }} 条</div>
        </div>
      </div>
      <div class="stat-card stat-rate-total">
        <div class="stat-icon">📊</div>
        <div class="stat-body">
          <div class="stat-label">累计成功率</div>
          <div class="stat-value">{{ stats.totalSuccessRate ?? 100 }}<small>%</small></div>
          <div class="stat-sub">总失败 {{ stats.totalFailCount ?? 0 }} 条</div>
        </div>
      </div>
      <div class="stat-card stat-topip">
        <div class="stat-icon">🌐</div>
        <div class="stat-body">
          <div class="stat-label">最活跃 IP</div>
          <div class="stat-value mono">{{ stats.topIp || '—' }}</div>
          <div class="stat-sub">{{ stats.topIpCount ?? 0 }} 次登录</div>
        </div>
      </div>
    </div>

    <el-card class="anomaly-card">
      <template #header>
        <div class="trend-header">
          <span class="trend-title">🚨 近 {{ anomalyDays }} 天失败 Top{{ anomalyTop }}</span>
          <el-radio-group v-model="anomalyDays" size="small" @change="loadAnomaly">
            <el-radio-button :value="1">24小时</el-radio-button>
            <el-radio-button :value="7">7天</el-radio-button>
            <el-radio-button :value="30">30天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div class="anomaly-grid">
        <section>
          <header class="anom-head">📍 失败最多 IP</header>
          <ul v-if="anomaly.topFailIps?.length" class="anom-list">
            <li v-for="(it, i) in anomaly.topFailIps" :key="it.k" class="anom-item">
              <span class="rank" :class="rankClass(i)">{{ i + 1 }}</span>
              <span class="key mono">{{ it.k }}</span>
              <span class="fail">{{ it.failCount }} 次</span>
              <el-tag size="small" :type="it.failRate >= 50 ? 'danger' : it.failRate >= 20 ? 'warning' : 'info'">
                {{ it.failRate }}%
              </el-tag>
            </li>
          </ul>
          <div v-else class="muted small">无失败记录</div>
        </section>
        <section>
          <header class="anom-head">👤 失败最多 用户</header>
          <ul v-if="anomaly.topFailUsers?.length" class="anom-list">
            <li v-for="(it, i) in anomaly.topFailUsers" :key="it.k" class="anom-item">
              <span class="rank" :class="rankClass(i)">{{ i + 1 }}</span>
              <span class="key">{{ it.k }}</span>
              <span class="fail">{{ it.failCount }} 次</span>
              <el-tag size="small" :type="it.failRate >= 50 ? 'danger' : it.failRate >= 20 ? 'warning' : 'info'">
                {{ it.failRate }}%
              </el-tag>
            </li>
          </ul>
          <div v-else class="muted small">无失败记录</div>
        </section>
      </div>
    </el-card>

    <el-card class="trend-card">
      <template #header>
        <div class="trend-header">
          <span class="trend-title">📊 近 {{ trendDays }} 天登录趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :value="7">7天</el-radio-button>
            <el-radio-button :value="14">14天</el-radio-button>
            <el-radio-button :value="30">30天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>

    <el-card>
      <div class="toolbar">
        <el-input v-model="filter.username" placeholder="用户名" clearable style="width:140px" @keyup.enter="load(1)" />
        <el-input v-model="filter.ip" placeholder="IP" clearable style="width:140px" @keyup.enter="load(1)" />
        <el-select v-model="filter.status" placeholder="状态" clearable style="width:100px">
          <el-option :value="1" label="成功" />
          <el-option :value="0" label="失败" />
        </el-select>
        <el-select v-model="filter.loginType" placeholder="登录方式" clearable style="width:120px">
          <el-option value="password" label="密码" />
          <el-option value="sms" label="短信" />
          <el-option value="wechat" label="微信" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width:340px"
        />
        <el-button type="primary" :icon="Search" @click="load(1)">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
        <el-button type="success" :icon="Download" @click="onExport" :loading="exporting">导出CSV</el-button>
        <el-button type="primary" plain :icon="Search" @click="searchDialog = true">全文搜索</el-button>
      </div>

      <div class="toolbar toolbar-row">
        <span class="muted small" style="margin-right:4px">快捷：</span>
        <el-button size="small" :type="rangeActive === 1 ? 'primary' : ''" @click="applyRange(1)">最近1小时</el-button>
        <el-button size="small" :type="rangeActive === 24 ? 'primary' : ''" @click="applyRange(24)">最近24小时</el-button>
        <el-button size="small" :type="rangeActive === 168 ? 'primary' : ''" @click="applyRange(168)">最近7天</el-button>
        <el-button size="small" :type="rangeActive === 720 ? 'primary' : ''" @click="applyRange(720)">最近30天</el-button>
        <span class="divider" />
        <el-checkbox v-model="onlyFail" @change="load(1)">只看失败</el-checkbox>
        <span class="divider" />
        <el-switch v-model="autoRefresh" active-text="自动刷新 5s" size="small" />
        <span class="divider" />
        <el-button type="danger" plain :icon="Delete" @click="onClear" :loading="clearing">清理日志</el-button>
      </div>

      <el-table
        :data="list"
        v-loading="loading"
        stripe
        :row-class-name="rowClass"
        :max-height="tableMaxHeight"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="loginType" label="方式" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.loginType" size="small">{{ row.loginType }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openUa(row)">详情</el-button>
            <el-button size="small" type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
          :total="total" :page-sizes="[20,50,100,200,500]"
          layout="total,sizes,prev,pager,next,jumper" @current-change="load()" @size-change="load(1)" />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="uaDialog.open" :title="uaDialog.title" width="640px">
      <div class="kv"><span class="k">ID</span><span class="v">{{ uaDialog.row?.id }}</span></div>
      <div class="kv"><span class="k">用户ID</span><span class="v">{{ uaDialog.row?.userId || '—' }}</span></div>
      <div class="kv"><span class="k">用户名</span><span class="v">{{ uaDialog.row?.username || '—' }}</span></div>
      <div class="kv"><span class="k">状态</span><span class="v"><el-tag :type="uaDialog.row?.status === 1 ? 'success' : 'danger'" size="small">{{ uaDialog.row?.status === 1 ? '成功' : '失败' }}</el-tag></span></div>
      <div class="kv"><span class="k">消息</span><span class="v">{{ uaDialog.row?.message || '—' }}</span></div>
      <div class="kv"><span class="k">IP</span><span class="v mono">{{ uaDialog.row?.ip || '—' }}</span></div>
      <div class="kv kv-block">
        <span class="k">User-Agent</span>
        <pre class="block ua">{{ uaDialog.row?.userAgent || '—' }}</pre>
      </div>
    </el-dialog>

    <!-- 全文搜索弹窗 -->
    <el-dialog v-model="searchDialog" title="全文搜索" width="80%" top="5vh">
      <div class="toolbar">
        <el-input v-model="searchQ" placeholder="搜索 用户名 / IP / UA / 消息" clearable style="width:340px" @keyup.enter="onSearch" />
        <el-select v-model="searchStatus" placeholder="状态" clearable style="width:100px">
          <el-option :value="1" label="成功" />
          <el-option :value="0" label="失败" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch" :loading="searching">搜索</el-button>
      </div>
      <el-table :data="searchList" v-loading="searching" size="small" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="userAgent" label="UA" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="searchPageNum" v-model:page-size="searchPageSize"
          :total="searchTotal" :page-sizes="[20,50,100]"
          layout="total,sizes,prev,pager,next" @current-change="onSearch" @size-change="onSearch(1)" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Delete } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { sysApi } from '@/api/sys'
import request from '@/api/request'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const exporting = ref(false)
const clearing = ref(false)

const stats = ref<any>({})
const trend = ref<Array<{date: string; total: number; success: number; fail: number}>>([])
const trendDays = ref(7)
const chartRef = ref<HTMLDivElement>()
let chartInst: echarts.ECharts | null = null

const anomaly = ref<{ topFailIps: any[]; topFailUsers: any[] }>({ topFailIps: [], topFailUsers: [] })
const anomalyDays = ref(7)
const anomalyTop = 5

const filter = reactive({
  username: '',
  ip: '',
  status: undefined as number | undefined,
  loginType: '',
  startTime: '',
  endTime: ''
})
const dateRange = ref<[string, string] | null>(null)
const rangeActive = ref<number | null>(null)
const onlyFail = ref(false)
const autoRefresh = ref(false)
let autoRefreshTimer: number | null = null

const tableMaxHeight = computed(() => window.innerHeight - 580)

const uaDialog = reactive({ open: false, title: '', row: null as any })
const searchDialog = ref(false)
const searchQ = ref('')
const searchStatus = ref<number | undefined>(undefined)
const searchList = ref<any[]>([])
const searchTotal = ref(0)
const searchPageNum = ref(1)
const searchPageSize = ref(20)
const searching = ref(false)

function buildParams() {
  const params: any = {}
  if (filter.username) params.username = filter.username
  if (filter.ip) params.ip = filter.ip
  if (filter.loginType) params.loginType = filter.loginType
  if (onlyFail.value) params.status = 0
  else if (filter.status !== undefined) params.status = filter.status
  if (filter.startTime) params.startTime = filter.startTime
  if (filter.endTime) params.endTime = filter.endTime
  return params
}

async function load(p?: number) {
  if (p) pageNum.value = p
  loading.value = true
  try {
    const r: any = await sysApi.loginLogPage(pageNum.value, pageSize.value, buildParams())
    list.value = r.data.records || []
    total.value = r.data.total || 0
  } finally {
    loading.value = false
  }
}

function refreshAll() {
  load()
  loadStats()
  loadTrend()
  loadAnomaly()
}

function onReset() {
  filter.username = ''
  filter.ip = ''
  filter.status = undefined
  filter.loginType = ''
  filter.startTime = ''
  filter.endTime = ''
  dateRange.value = null
  rangeActive.value = null
  onlyFail.value = false
  load(1)
}

function applyRange(hours: number) {
  rangeActive.value = hours
  const end = new Date()
  const start = new Date(end.getTime() - hours * 3600 * 1000)
  const fmt = (d: Date) => {
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }
  const s = fmt(start), e = fmt(end)
  filter.startTime = s
  filter.endTime = e
  dateRange.value = [s, e]
  load(1)
}

watch(dateRange, (v) => {
  if (v && v.length === 2) {
    filter.startTime = v[0]
    filter.endTime = v[1]
    rangeActive.value = null
  } else {
    filter.startTime = ''
    filter.endTime = ''
  }
})

watch([onlyFail], () => { load(1) })

async function onExport() {
  exporting.value = true
  try {
    const query: any = buildParams()
    const usp = new URLSearchParams()
    Object.keys(query).forEach(k => usp.append(k, String(query[k])))
    const realUrl = '/sys/login-log/export?' + usp.toString()
    const token = (request as any).defaults?.headers?.common?.Authorization
    const res = await fetch(realUrl, { headers: token ? { Authorization: token } : {} })
    if (!res.ok) throw new Error('导出失败')
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `sys_login_log_${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('已导出')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function onClear() {
  let days = 30
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入要清理几天前的登录日志（最小 1 天）。\n提示：会保留最近 N 天的日志，更早的全部删除，不可恢复。',
      '清理历史日志',
      {
        confirmButtonText: '下一步', cancelButtonText: '取消',
        inputPattern: /^[1-9]\d*$/, inputErrorMessage: '请输入正整数',
        inputValue: '30', type: 'warning'
      }
    )
    days = parseInt(value, 10)
  } catch { return }

  try {
    const { value: confirm } = await ElMessageBox.prompt(
      `确认清理 ${days} 天前的全部登录日志？\n此操作不可恢复！请输入 "清空" 以确认。`,
      '危险操作',
      {
        confirmButtonText: '确认清理', cancelButtonText: '取消',
        inputPattern: /^清空$/, inputErrorMessage: '请输入"清空"',
        inputValue: '', type: 'error'
      }
    )
    if (confirm !== '清空') return
  } catch { return }

  clearing.value = true
  try {
    const r: any = await sysApi.loginLogClear(days)
    ElMessage.success(r.message || `已清理 ${days} 天前的日志`)
    refreshAll()
  } catch (e: any) {
    ElMessage.error(e.message || '清理失败')
  } finally {
    clearing.value = false
  }
}

async function onSearch(p?: number) {
  if (p) searchPageNum.value = p
  searching.value = true
  try {
    const r: any = await sysApi.loginLogSearch({
      q: searchQ.value,
      status: searchStatus.value,
      pageNum: searchPageNum.value,
      pageSize: searchPageSize.value
    })
    searchList.value = r.data?.records || []
    searchTotal.value = r.data?.total || 0
  } catch (e) {
    ElMessage.error('搜索失败')
  } finally {
    searching.value = false
  }
}

const rankClass = (i: number) => {
  if (i === 0) return 'rank-1'
  if (i === 1) return 'rank-2'
  if (i === 2) return 'rank-3'
  return 'rank-default'
}

const rowClass = ({ row }: { row: any }) => row.status === 0 ? 'row-fail' : ''

function openUa(row: any) {
  uaDialog.row = row
  uaDialog.title = `登录详情 #${row.id}`
  uaDialog.open = true
}

async function remove(row: any) {
  await ElMessageBox.confirm('确认删除该登录日志？', '提示', { type: 'warning' })
  await sysApi.loginLogDelete(row.id)
  ElMessage.success('已删除')
  load()
}

watch(autoRefresh, (v) => {
  if (v) {
    autoRefreshTimer = window.setInterval(() => load(), 5000) as any
    ElMessage.success('已开启 5s 自动刷新')
  } else {
    if (autoRefreshTimer) { clearInterval(autoRefreshTimer); autoRefreshTimer = null }
  }
})

onMounted(() => {
  refreshAll()
  window.addEventListener('resize', resizeChart)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chartInst?.dispose()
  chartInst = null
  if (autoRefreshTimer) clearInterval(autoRefreshTimer)
})

function resizeChart() { chartInst?.resize() }

async function loadStats() {
  try {
    const r: any = await sysApi.loginLogStats(30)
    stats.value = r.data || {}
  } catch (e) {
    stats.value = {}
  }
}

async function loadTrend() {
  try {
    const r: any = await sysApi.loginLogTrend(trendDays.value)
    trend.value = r.data || []
    await nextTick()
    drawChart()
  } catch (e) {
    trend.value = []
  }
}

async function loadAnomaly() {
  try {
    const r: any = await sysApi.loginLogAnomaly(anomalyDays.value, anomalyTop)
    anomaly.value = r.data || { topFailIps: [], topFailUsers: [] }
  } catch {
    anomaly.value = { topFailIps: [], topFailUsers: [] }
  }
}

function drawChart() {
  if (!chartRef.value) return
  if (!chartInst) chartInst = echarts.init(chartRef.value)
  const dates = trend.value.map(p => p.date.slice(5))
  const succ = trend.value.map(p => p.success)
  const fail = trend.value.map(p => p.fail)
  chartInst.setOption({
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['成功', '失败'], top: 0, right: 0 },
    xAxis: { type: 'category', data: dates, boundaryGap: true, axisLine: { lineStyle: { color: '#dcdfe6' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f0f0' } } },
    series: [
      { name: '成功', type: 'bar', stack: 'total', data: succ, itemStyle: { color: '#67c23a', borderRadius: [3,3,0,0] }, barWidth: 18 },
      { name: '失败', type: 'bar', stack: 'total', data: fail, itemStyle: { color: '#f56c6c', borderRadius: [3,3,0,0] } }
    ]
  })
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; align-items: center }
.toolbar-row { margin-top: -4px }
.divider { width: 1px; height: 18px; background: #ebeef5; margin: 0 6px }
.pagination { display: flex; justify-content: flex-end; margin-top: 14px }
.small { font-size: 12px }

.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px }
.stat-card { display: flex; align-items: center; gap: 14px; padding: 18px 20px; background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); border: 1px solid #ebeef5; transition: transform .15s, box-shadow .15s }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08) }
.stat-icon { font-size: 32px; line-height: 1 }
.stat-body { flex: 1; min-width: 0 }
.stat-label { color: #909399; font-size: 13px; margin-bottom: 4px }
.stat-value { font-size: 26px; font-weight: 600; color: #303133; line-height: 1.1 }
.stat-value small { font-size: 14px; font-weight: 400; margin-left: 2px; color: #909399 }
.stat-sub { font-size: 12px; color: #909399; margin-top: 6px }
.stat-today { border-left: 3px solid #409eff }
.stat-rate-today { border-left: 3px solid #67c23a }
.stat-rate-total { border-left: 3px solid #e6a23c }
.stat-topip { border-left: 3px solid #909399 }

.anomaly-card { margin-bottom: 14px }
.anomaly-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px }
.anom-head { font-weight: 600; color: #303133; font-size: 13px; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px dashed #ebeef5 }
.anom-list { list-style: none; padding: 0; margin: 0 }
.anom-item { display: grid; grid-template-columns: 24px 1fr auto auto; gap: 8px; align-items: center; padding: 6px 4px; font-size: 13px; border-bottom: 1px dashed #f5f5f5 }
.anom-item:last-child { border-bottom: none }
.rank { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 4px; font-size: 12px; font-weight: 600; color: #fff }
.rank-1 { background: #f56c6c }
.rank-2 { background: #e6a23c }
.rank-3 { background: #67c23a }
.rank-default { background: #c0c4cc }
.anom-item .key { overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.anom-item .fail { color: #909399; font-size: 12px }

.trend-card { margin-bottom: 14px }
.trend-header { display: flex; justify-content: space-between; align-items: center }
.trend-title { font-size: 15px; font-weight: 600; color: #303133 }
.chart { width: 100%; height: 240px }
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 12px }
.muted { color: #c0c4cc }

.kv { display: flex; gap: 12px; padding: 4px 0; align-items: flex-start; font-size: 13px }
.kv .k { width: 90px; color: #909399; flex-shrink: 0 }
.kv .v { flex: 1; color: #303133; word-break: break-all }
.kv-block { flex-direction: column; align-items: stretch }
.kv-block .k { width: auto; margin-bottom: 4px }
.block { flex: 1; margin: 0; padding: 10px 12px; background: #f5f7fa; border-radius: 4px; font-family: ui-monospace, Consolas, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 200px; overflow: auto }
.ua { max-height: 120px }
:deep(.row-fail) { background-color: #fef0f0 !important }
:deep(.row-fail td) { background-color: #fef0f0 !important }
</style>
