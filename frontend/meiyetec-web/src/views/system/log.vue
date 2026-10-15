<template>
  <div class="page">
    <div class="stats-row">
      <div class="stat-card stat-today">
        <div class="stat-icon">📈</div>
        <div class="stat-body">
          <div class="stat-label">今日操作</div>
          <div class="stat-value">{{ stats.todayCount ?? 0 }}</div>
          <div class="stat-sub">总 {{ stats.totalCount ?? 0 }} 条</div>
        </div>
      </div>
      <div class="stat-card stat-rate-today" :class="{ clickable: (stats.todayFailCount ?? 0) > 0 }" @click="onTodayFailClick">
        <div class="stat-icon">✅</div>
        <div class="stat-body">
          <div class="stat-label">今日成功率</div>
          <div class="stat-value">{{ stats.todaySuccessRate ?? 100 }}<small>%</small></div>
          <div class="stat-sub">
            <span v-if="(stats.todayFailCount ?? 0) > 0">
              失败 {{ stats.todayFailCount ?? 0 }} 条 <span class="link">查看失败 →</span>
            </span>
            <span v-else>今日无失败</span>
          </div>
        </div>
      </div>
      <div class="stat-card stat-rate-total">
        <div class="stat-icon">🛡️</div>
        <div class="stat-body">
          <div class="stat-label">累计成功率</div>
          <div class="stat-value">{{ stats.totalSuccessRate ?? 100 }}<small>%</small></div>
          <div class="stat-sub">覆盖全部模块</div>
        </div>
      </div>
      <div class="stat-card stat-topip">
        <div class="stat-icon">🌐</div>
        <div class="stat-body">
          <div class="stat-label">最活跃 IP</div>
          <div class="stat-value mono">{{ stats.topIp || '—' }}</div>
          <div class="stat-sub">{{ stats.topIpCount ?? 0 }} 次请求</div>
        </div>
      </div>
    </div>

    <el-card class="anomaly-card">
      <template #header>
        <div class="trend-header">
          <span class="trend-title">🚨 近 {{ anomalyDays }} 天异常 Top{{ anomalyTop }}</span>
          <div class="anomaly-actions">
            <el-radio-group v-model="anomalyDays" size="small" @change="loadAnomaly">
              <el-radio-button :value="1">24小时</el-radio-button>
              <el-radio-button :value="7">7天</el-radio-button>
              <el-radio-button :value="30">30天</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      <div class="anomaly-grid">
        <section>
          <header class="anom-head">📍 失败最多 IP</header>
          <ul v-if="anomaly.topIps?.length" class="anom-list">
            <li v-for="(it, i) in anomaly.topIps" :key="it.key" class="anom-item">
              <span class="rank" :class="rankClass(i)">{{ i + 1 }}</span>
              <span class="key mono" :title="displayIp(it.key)">{{ displayIp(it.key) }}</span>
              <span class="fail">{{ it.failCount }} 失败</span>
              <el-tag size="small" :type="it.failRate >= 50 ? 'danger' : it.failRate >= 20 ? 'warning' : 'info'">
                {{ it.failRate }}%
              </el-tag>
            </li>
          </ul>
          <div v-else class="muted small">无失败记录</div>
        </section>
        <section>
          <header class="anom-head">🔗 失败最多 接口</header>
          <ul v-if="anomaly.topUrls?.length" class="anom-list">
            <li v-for="(it, i) in anomaly.topUrls" :key="it.key" class="anom-item">
              <span class="rank" :class="rankClass(i)">{{ i + 1 }}</span>
              <span class="key mono" :title="it.key">{{ shortUrl(it.key) }}</span>
              <span class="fail">{{ it.failCount }} 失败</span>
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
          <span class="trend-title">📊 近 {{ trendDays }} 天操作趋势</span>
          <div class="trend-actions">
            <span v-if="trendEmpty" class="muted small">💡 提示：空数据日以 0 计数，便于观察业务低谷</span>
            <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
              <el-radio-button :value="7">7天</el-radio-button>
              <el-radio-button :value="14">14天</el-radio-button>
              <el-radio-button :value="30">30天</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>

    <el-card>
      <div class="toolbar">
        <el-input v-model="filter.username" placeholder="用户名" clearable style="width:140px" @keyup.enter="load(1)" />
        <el-input v-model="filter.keyword" placeholder="描述/URL/IP" clearable style="width:180px" @keyup.enter="load(1)" />
        <el-select v-model="filter.module" placeholder="模块" clearable style="width:120px" :loading="enumsLoading">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="filter.action" placeholder="操作" clearable style="width:120px" :loading="enumsLoading">
          <el-option v-for="a in actions" :key="a" :label="actionLabel(a)" :value="a" />
        </el-select>
        <el-select v-model="filter.status" placeholder="状态" clearable style="width:100px">
          <el-option :value="1" label="成功" />
          <el-option :value="0" label="失败" />
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
        <el-button :icon="Setting" @click="showColumnDialog = true">列设置</el-button>
        <el-button type="primary" plain :icon="Search" @click="showSearchDialog = true">全文搜索</el-button>
      </div>

      <div class="toolbar toolbar-row">
        <span class="muted small" style="margin-right:4px">快捷：</span>
        <el-button size="small" :type="rangeActive === 1 ? 'primary' : ''" @click="applyRange(1)">最近1小时</el-button>
        <el-button size="small" :type="rangeActive === 24 ? 'primary' : ''" @click="applyRange(24)">最近24小时</el-button>
        <el-button size="small" :type="rangeActive === 168 ? 'primary' : ''" @click="applyRange(168)">最近7天</el-button>
        <el-button size="small" :type="rangeActive === 720 ? 'primary' : ''" @click="applyRange(720)">最近30天</el-button>
        <span class="divider" />
        <el-checkbox v-model="onlyFail" @change="load(1)">只看失败</el-checkbox>
        <el-checkbox v-model="failFirst" @change="load(1)">失败置顶</el-checkbox>
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
        ref="tableRef"
        :max-height="tableMaxHeight"
        :virtual="list.length > 200"
        :row-key="(r: any) => r.id"
      >
        <el-table-column type="expand" width="44" fixed="left">
          <template #default="{ row }">
            <div class="expand">
              <div class="expand-grid">
                <section class="expand-section">
                  <header class="sec-head">
                    <span class="sec-title">📝 操作</span>
                  </header>
                  <div class="kv"><span class="k">用户</span><span class="v">{{ row.username }} <small v-if="row.userId" class="muted">(#{{ row.userId }})</small></span></div>
                  <div class="kv"><span class="k">模块</span><span class="v">{{ row.module || '—' }}</span></div>
                  <div class="kv"><span class="k">动作</span><span class="v">
                    <el-tag size="small" :type="actionTagType(row.action)">{{ actionLabel(row.action) }}</el-tag>
                  </span></div>
                  <div class="kv"><span class="k">描述</span><span class="v">{{ row.description || '—' }}</span></div>
                  <div class="kv"><span class="k">时间</span><span class="v mono">{{ row.createdAt }}</span></div>
                </section>

                <section class="expand-section">
                  <header class="sec-head">
                    <span class="sec-title">🌐 请求</span>
                    <el-button size="small" type="primary" link :icon="Refresh" @click="onReplay(row)">重放请求</el-button>
                  </header>
                  <div class="kv"><span class="k">方法</span><span class="v">
                    <el-tag :type="methodType(row.requestMethod)" size="small">{{ row.requestMethod }}</el-tag>
                  </span></div>
                  <div class="kv"><span class="k">URL</span><span class="v mono">{{ row.requestUrl }}</span></div>
                  <div class="kv kv-block">
                    <div class="block-head">
                      <span class="k">请求参数</span>
                      <el-button v-if="row.requestParams" size="small" link :icon="CopyDocument" @click="copy(row.requestParams)">复制</el-button>
                    </div>
                    <pre class="block">{{ prettyOrRaw(row.requestParams) }}</pre>
                  </div>
                </section>

                <section class="expand-section">
                  <header class="sec-head">
                    <span class="sec-title">🛠️ 环境</span>
                  </header>
                  <div class="kv"><span class="k">IP</span><span class="v mono">{{ row.ip || '—' }}</span></div>
                  <div class="kv"><span class="k">耗时</span><span class="v">
                    <span :class="costClass(row.costMs)">{{ row.costMs ?? '—' }} ms</span>
                  </span></div>
                  <div class="kv"><span class="k">状态</span><span class="v">
                    <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                      {{ row.status === 1 ? '成功' : '失败' }}
                    </el-tag>
                  </span></div>
                  <div class="kv kv-block">
                    <span class="k">User-Agent</span>
                    <pre class="block ua">{{ row.userAgent || '—' }}</pre>
                  </div>
                </section>

                <section v-if="row.status === 0 || row.errorMsg" class="expand-section err-section">
                  <header class="sec-head">
                    <span class="sec-title">❌ 错误</span>
                  </header>
                  <div class="kv kv-block">
                    <div class="block-head">
                      <span class="k">错误信息</span>
                      <el-button v-if="row.errorMsg" size="small" link :icon="CopyDocument" @click="copy(row.errorMsg)">复制</el-button>
                    </div>
                    <pre class="err-pre">{{ prettyOrRaw(row.errorMsg) }}</pre>
                  </div>
                </section>
              </div>

              <div class="expand-footer">
                <el-button :icon="Connection" size="small" @click="openContext(row)">查看上下文 ±10min</el-button>
                <el-button :icon="Aim" size="small" @click="openSession(row)">查看同会话</el-button>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column v-if="colVisible.id" prop="id" label="ID" width="70" />
        <el-table-column v-if="colVisible.username" prop="username" label="用户" width="100" />
        <el-table-column v-if="colVisible.module" prop="module" label="模块" width="100" />
        <el-table-column v-if="colVisible.action" label="操作" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTagType(row.action)">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="colVisible.description" prop="description" label="描述" min-width="180" width="240">
          <template #default="{ row }">
            <span :title="row.description">{{ truncate(row.description, 30) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="colVisible.requestMethod" prop="requestMethod" label="方法" width="70">
          <template #default="{ row }">
            <el-tag :type="methodType(row.requestMethod)" size="small">{{ row.requestMethod }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="colVisible.requestUrl" prop="requestUrl" label="URL" min-width="180" width="220" show-overflow-tooltip />
        <el-table-column v-if="colVisible.ip" prop="ip" label="IP" width="120" />
        <el-table-column v-if="colVisible.costMs" prop="costMs" label="耗时(ms)" width="90" sortable>
          <template #default="{ row }">
            <span :class="costClass(row.costMs)">{{ row.costMs }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="colVisible.status" label="状态" width="72">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="colVisible.createdAt" prop="createdAt" label="时间" width="160" />
        <el-table-column v-if="colVisible.errorMsg" prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMsg" class="err-text">{{ row.errorMsg }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openContext(row)">上下文</el-button>
            <el-button size="small" type="primary" link @click="openSession(row)">同会话</el-button>
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

    <!-- 列设置弹窗 -->
    <el-dialog v-model="showColumnDialog" title="列设置（保存到本地）" width="420px">
      <el-checkbox-group v-model="checkedCols">
        <div v-for="c in allCols" :key="c.key" class="col-row">
          <el-checkbox :value="c.key" :label="c.key">{{ c.label }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="resetColumns">恢复默认</el-button>
        <el-button type="primary" @click="showColumnDialog = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- 全文搜索 -->
    <el-dialog v-model="showSearchDialog" title="全文搜索（描述/URL/IP/用户/错误信息）" width="80%" top="5vh">
      <div class="toolbar">
        <el-input v-model="searchQ" placeholder="搜索关键字" clearable style="width:240px" @keyup.enter="onSearch" />
        <el-select v-model="searchModule" placeholder="模块" clearable style="width:120px">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="searchAction" placeholder="操作" clearable style="width:120px">
          <el-option v-for="a in actions" :key="a" :label="actionLabel(a)" :value="a" />
        </el-select>
        <el-select v-model="searchStatus" placeholder="状态" clearable style="width:100px">
          <el-option :value="1" label="成功" />
          <el-option :value="0" label="失败" />
        </el-select>
        <el-date-picker
          v-model="searchDateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width:340px"
        />
        <el-button type="primary" :icon="Search" @click="onSearch" :loading="searching">搜索</el-button>
        <el-button type="success" :icon="Download" @click="onSearchExport" :loading="searchExporting">导出结果</el-button>
        <span class="muted small">共 {{ searchTotal }} 条</span>
      </div>
      <el-table :data="searchList" v-loading="searching" size="small" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="90" />
        <el-table-column prop="action" label="动作" width="90" />
        <el-table-column prop="requestUrl" label="URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="applySearchResult(row)">带入主表</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="searchPageNum" v-model:page-size="searchPageSize"
          :total="searchTotal" :page-sizes="[20,50,100]"
          layout="total,sizes,prev,pager,next" @current-change="onSearch" @size-change="onSearch(1)" />
      </div>
    </el-dialog>

    <!-- 上下文抽屉 -->
    <el-drawer v-model="ctxDrawer.open" :title="ctxDrawer.title" size="60%">
      <div class="ctx-toolbar">
        <span class="muted small">总 {{ ctxDrawer.list.length }} 条</span>
        <el-button :icon="Refresh" size="small" @click="loadContext">刷新</el-button>
      </div>
      <el-table :data="ctxDrawer.list" size="small" stripe :row-class-name="rowClass">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="action" label="动作" width="80" />
        <el-table-column prop="requestUrl" label="URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-drawer>

    <!-- 重放请求弹窗 -->
    <el-dialog v-model="replayDialog.open" :title="replayDialog.title" width="640px">
      <el-alert :title="'将要向你发送一条完全相同的请求。GET 请求会直接跳转到 URL（带参数）。POST/PUT/DELETE 会在本页模拟（不会真正调用）。'" type="warning" :closable="false" style="margin-bottom: 12px" />
      <div class="kv"><span class="k">方法</span><span class="v"><el-tag :type="methodType(replayDialog.row?.requestMethod)" size="small">{{ replayDialog.row?.requestMethod }}</el-tag></span></div>
      <div class="kv"><span class="k">URL</span><span class="v mono">{{ replayDialog.row?.requestUrl }}</span></div>
      <div class="kv kv-block">
        <span class="k">请求参数</span>
        <pre class="block">{{ prettyOrRaw(replayDialog.row?.requestParams) }}</pre>
      </div>
      <template #footer>
        <el-button @click="replayDialog.open = false">取消</el-button>
        <el-button type="primary" @click="confirmReplay">确认重放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download, Delete, CopyDocument, Setting, Connection, Aim } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { sysApi, type SysLog, type SysLogExportQuery } from '@/api/sys'
import request from '@/api/request'

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, DataZoomComponent, CanvasRenderer])

const router = useRouter()
const route = useRoute()

const list = ref<SysLog[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const exporting = ref(false)
const clearing = ref(false)
const enumsLoading = ref(false)

const stats = ref<any>({})
const trend = ref<Array<{date: string; total: number; success: number; fail: number}>>([])
const trendDays = ref(7)
const chartRef = ref<HTMLDivElement>()
let chartInst: echarts.ECharts | null = null

const anomaly = ref<{ topIps: any[]; topUrls: any[] }>({ topIps: [], topUrls: [] })
const anomalyDays = ref(7)
const anomalyTop = 5

const filter = reactive({
  username: '',
  keyword: '',
  module: '',
  action: '',
  status: undefined as number | undefined,
  startTime: '',
  endTime: ''
})
const dateRange = ref<[string, string] | null>(null)
const rangeActive = ref<number | null>(null)
const onlyFail = ref(false)
const failFirst = ref(false)
const autoRefresh = ref(false)
let autoRefreshTimer: number | null = null

const modules = ref<string[]>([])
const actions = ref<string[]>([])

const tableRef = ref<any>()
const tableMaxHeight = computed(() => window.innerHeight - 580)

const allCols = [
  { key: 'id', label: 'ID' },
  { key: 'username', label: '用户' },
  { key: 'module', label: '模块' },
  { key: 'action', label: '操作' },
  { key: 'description', label: '描述' },
  { key: 'requestMethod', label: '方法' },
  { key: 'requestUrl', label: 'URL' },
  { key: 'ip', label: 'IP' },
  { key: 'costMs', label: '耗时(ms)' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '时间' },
  { key: 'errorMsg', label: '错误信息' }
]
const COL_LS_KEY = 'syslog_cols_v1'
const defaultCols = ['id', 'username', 'module', 'action', 'description', 'requestMethod', 'requestUrl', 'ip', 'costMs', 'status', 'createdAt']
const checkedCols = ref<string[]>(defaultCols.slice())
const colVisible = reactive<Record<string, boolean>>({})
function applyColVisible() {
  for (const c of allCols) colVisible[c.key] = checkedCols.value.includes(c.key)
}
function resetColumns() {
  checkedCols.value = defaultCols.slice()
  saveColConfig()
  applyColVisible()
  ElMessage.success('已恢复默认列')
}
function saveColConfig() {
  try { localStorage.setItem(COL_LS_KEY, JSON.stringify(checkedCols.value)) } catch {}
}
function loadColConfig() {
  try {
    const raw = localStorage.getItem(COL_LS_KEY)
    if (raw) {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr) && arr.length > 0) checkedCols.value = arr
    }
  } catch {}
}
watch(checkedCols, () => { saveColConfig(); applyColVisible() }, { deep: true })
// URL 同步：filter <-> query
watch([filter, () => pageNum.value, () => pageSize.value, onlyFail, failFirst], () => {
  const q: any = { ...(route.query || {}) }
  if (filter.username) q.username = filter.username; else delete q.username
  if (filter.keyword) q.keyword = filter.keyword; else delete q.keyword
  if (filter.module) q.module = filter.module; else delete q.module
  if (filter.action) q.action = filter.action; else delete q.action
  if (filter.status !== undefined) q.status = String(filter.status); else delete q.status
  if (filter.startTime) q.start = filter.startTime; else delete q.start
  if (filter.endTime) q.end = filter.endTime; else delete q.end
  if (onlyFail.value) q.fail = '1'; else delete q.fail
  if (pageNum.value !== 1) q.p = String(pageNum.value); else delete q.p
  if (pageSize.value !== 20) q.ps = String(pageSize.value); else delete q.ps
  router.replace({ query: q }).catch(() => {})
}, { deep: true })

function applyUrlFilter() {
  const q = route.query
  if (q.username) filter.username = String(q.username)
  if (q.keyword) filter.keyword = String(q.keyword)
  if (q.module) filter.module = String(q.module)
  if (q.action) filter.action = String(q.action)
  if (q.status) filter.status = parseInt(String(q.status), 10)
  if (q.start) filter.startTime = String(q.start)
  if (q.end) filter.endTime = String(q.end)
  if (q.fail === '1') onlyFail.value = true
  if (q.p) pageNum.value = parseInt(String(q.p), 10) || 1
  if (q.ps) pageSize.value = parseInt(String(q.ps), 10) || 20
  if (filter.startTime && filter.endTime) dateRange.value = [filter.startTime, filter.endTime]
}

const showColumnDialog = ref(false)

// 全文搜索
const showSearchDialog = ref(false)
const searchQ = ref('')
const searchModule = ref('')
const searchAction = ref('')
const searchStatus = ref<number | undefined>(undefined)
const searchDateRange = ref<[string, string] | null>(null)
const searchList = ref<SysLog[]>([])
const searchTotal = ref(0)
const searchPageNum = ref(1)
const searchPageSize = ref(20)
const searching = ref(false)
const searchExporting = ref(false)

async function onSearch(p?: number) {
  if (p) searchPageNum.value = p
  searching.value = true
  try {
    const r: any = await sysApi.logSearch({
      q: searchQ.value,
      module: searchModule.value || undefined,
      action: searchAction.value || undefined,
      status: searchStatus.value,
      startTime: searchDateRange.value?.[0],
      endTime: searchDateRange.value?.[1],
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

async function onSearchExport() {
  if (!searchTotal.value) { ElMessage.warning('当前搜索无结果'); return }
  searchExporting.value = true
  try {
    const query: SysLogExportQuery = {
      q: searchQ.value || undefined,
      module: searchModule.value || undefined,
      action: searchAction.value || undefined,
      status: searchStatus.value,
      startTime: searchDateRange.value?.[0],
      endTime: searchDateRange.value?.[1]
    } as any
    const url = sysApi.logExportUrl(query)
    const fields = checkedCols.value.join(',')
    const realUrl = url + (url.includes('?') ? '&' : '?') + 'fields=' + encodeURIComponent(fields)
    const token = (request as any).defaults?.headers?.common?.Authorization
    const res = await fetch(realUrl, { headers: token ? { Authorization: token } : {} })
    if (!res.ok) throw new Error('导出失败')
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `sys_log_search_${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('已导出搜索结果')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    searchExporting.value = false
  }
}

function applySearchResult(row: SysLog) {
  // 关弹窗并把该行的关键字精确回填到主表过滤（用描述/ip/username 三个候选）
  showSearchDialog.value = false
  // 优先用搜索关键字；其次用该行的 IP/用户名/URL
  const q = (searchQ.value || row.username || row.ip || '').trim()
  if (q) filter.keyword = q
  pageNum.value = 1
  load()
}

const FILTER_LS_KEY = 'syslog_filter_v1'
function saveFilter() {
  try {
    localStorage.setItem(FILTER_LS_KEY, JSON.stringify({
      filter: { ...filter },
      onlyFail: onlyFail.value,
      failFirst: failFirst.value,
      pageSize: pageSize.value
    }))
  } catch {}
}
function loadFilter() {
  try {
    const raw = localStorage.getItem(FILTER_LS_KEY)
    if (!raw) return
    const o = JSON.parse(raw)
    if (o.filter) Object.assign(filter, o.filter)
    if (o.onlyFail != null) onlyFail.value = !!o.onlyFail
    if (o.failFirst != null) failFirst.value = !!o.failFirst
    if (o.pageSize) pageSize.value = o.pageSize
    if (filter.startTime && filter.endTime) {
      dateRange.value = [filter.startTime, filter.endTime]
    }
  } catch {}
}

const ACTION_LABEL: Record<string, string> = {
  'create': '新增', 'update': '修改', 'delete': '删除', 'login': '登录',
  'export': '导出', 'import': '导入', 'enable': '启用', 'disable': '禁用', 'clear': '清空', 'query': '查询'
}
const ACTION_TYPE: Record<string, string> = {
  'create': 'success', 'update': 'warning', 'delete': 'danger', 'login': 'info',
  'export': '', 'import': '', 'enable': 'success', 'disable': 'info', 'clear': 'danger', 'query': 'info'
}
const actionLabel = (a?: string) => (a ? (ACTION_LABEL[a] || a) : '')
const actionTagType = (a?: string) => (a ? (ACTION_TYPE[a] || 'info') : 'info')

const methodType = (m?: string) => {
  if (m === 'POST') return 'success'
  if (m === 'PUT') return 'warning'
  if (m === 'DELETE') return 'danger'
  if (m === 'GET') return 'info'
  return ''
}

const costClass = (ms?: number) => {
  if (ms == null) return ''
  if (ms >= 1500) return 'cost-slow'
  if (ms >= 500) return 'cost-warn'
  return ''
}

const rankClass = (i: number) => {
  if (i === 0) return 'rank-1'
  if (i === 1) return 'rank-2'
  if (i === 2) return 'rank-3'
  return 'rank-default'
}

const shortUrl = (u?: string) => {
  if (!u) return '—'
  return u.length > 36 ? u.slice(0, 36) + '…' : u
}

const displayIp = (k?: string) => {
  if (!k) return '—'
  return k === '__UNKNOWN__' ? '未知' : k
}

const truncate = (s?: string, n = 30) => {
  if (!s) return '—'
  return s.length > n ? s.slice(0, n) + '…' : s
}

const rowClass = ({ row }: { row: SysLog }) => {
  if (row.status === 0) return 'row-fail'
  return ''
}

function buildParams() {
  const params: any = {}
  if (filter.username) params.username = filter.username
  if (filter.keyword) params.keyword = filter.keyword
  if (filter.module) params.module = filter.module
  if (filter.action) params.action = filter.action
  if (onlyFail.value) params.status = 0
  else if (filter.status !== undefined) params.status = filter.status
  if (filter.startTime) params.startTime = filter.startTime
  if (filter.endTime) params.endTime = filter.endTime
  if (failFirst.value) params.failFirst = true
  return params
}

async function load(p?: number) {
  if (p) pageNum.value = p
  loading.value = true
  try {
    const r: any = await sysApi.logPage(pageNum.value, pageSize.value, buildParams())
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
  filter.keyword = ''
  filter.module = ''
  filter.action = ''
  filter.status = undefined
  filter.startTime = ''
  filter.endTime = ''
  dateRange.value = null
  rangeActive.value = null
  onlyFail.value = false
  failFirst.value = false
  saveFilter()
  load(1)
}

function onTodayFailClick() {
  if ((stats.value.todayFailCount ?? 0) <= 0) return
  // 快捷切到"今日"+"只看失败"
  applyRange(24)
  onlyFail.value = false
  filter.status = 0
  saveFilter()
  load(1)
  ElMessage.info('已过滤今日失败日志')
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
  saveFilter()
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
  saveFilter()
})

watch([onlyFail, failFirst], () => {
  saveFilter()
  if (list.value.length > 0 || total.value > 0) load(1)
})

async function onExport() {
  exporting.value = true
  try {
    const query: SysLogExportQuery = {}
    if (filter.username) query.username = filter.username
    if (filter.keyword) query.keyword = filter.keyword
    if (filter.module) query.module = filter.module
    if (filter.action) query.action = filter.action
    if (onlyFail.value) query.status = 0
    else if (filter.status !== undefined) query.status = filter.status
    if (filter.startTime) query.startTime = filter.startTime
    if (filter.endTime) query.endTime = filter.endTime
    const url = sysApi.logExportUrl(query)
    const fields = checkedCols.value.join(',')
    const realUrl = url + (url.includes('?') ? '&' : '?') + 'fields=' + encodeURIComponent(fields)
    const token = (request as any).defaults?.headers?.common?.Authorization
    const res = await fetch(realUrl, { headers: token ? { Authorization: token } : {} })
    if (!res.ok) throw new Error('导出失败')
    const blob = await res.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `sys_log_${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('已导出')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function loadEnums() {
  enumsLoading.value = true
  try {
    const r: any = await sysApi.logEnums()
    modules.value = r.data?.modules || []
    actions.value = r.data?.actions || []
  } catch {
    modules.value = ['会员', '收银', '商品', '登录', '报表', '库存', '套餐', '系统']
    actions.value = ['create', 'update', 'delete', 'login', 'export', 'import', 'enable', 'disable', 'clear']
  } finally {
    enumsLoading.value = false
  }
}

async function onClear() {
  let days = 30
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入要清理几天前的日志（最小 1 天）。\n提示：会保留最近 N 天的日志，更早的全部删除，不可恢复。',
      '清理历史日志',
      {
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: '请输入正整数',
        inputValue: '30',
        type: 'warning'
      }
    )
    days = parseInt(value, 10)
  } catch { return }

  try {
    const { value: confirm } = await ElMessageBox.prompt(
      `确认清理 ${days} 天前的全部日志？\n此操作不可恢复！请输入 "清空" 以确认。`,
      '危险操作',
      {
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
        inputPattern: /^清空$/,
        inputErrorMessage: '请输入"清空"',
        inputValue: '',
        type: 'error'
      }
    )
    if (confirm !== '清空') return
  } catch { return }

  clearing.value = true
  try {
    const r: any = await sysApi.logClear(days)
    ElMessage.success(r.message || `已清理 ${days} 天前的日志`)
    refreshAll()
  } catch (e: any) {
    ElMessage.error(e.message || '清理失败')
  } finally {
    clearing.value = false
  }
}

function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function prettyOrRaw(v?: string) {
  if (!v || v === '—') return '—'
  try {
    const parsed = JSON.parse(v)
    return escapeHtml(JSON.stringify(parsed, null, 2))
  } catch {
    return escapeHtml(v)
  }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  }
}

async function remove(row: SysLog) {
  await ElMessageBox.confirm('确认删除该日志？', '提示', { type: 'warning' })
  await sysApi.logDelete(row.id!)
  ElMessage.success('已删除')
  load()
}

// 上下文抽屉
const ctxDrawer = reactive({
  open: false,
  title: '',
  list: [] as SysLog[]
})

async function openContext(row: SysLog) {
  ctxDrawer.title = `上下文 ±10min / 用户=${row.username} / ID=${row.id}`
  ctxDrawer.open = true
  ctxDrawer.list = []
  if (row.id) await loadContext(row.id)
}

async function openSession(row: SysLog) {
  ctxDrawer.title = `同会话 / 用户=${row.username} / IP=${row.ip || '—'} / ID=${row.id}`
  ctxDrawer.open = true
  ctxDrawer.list = []
  if (row.id) await loadSession(row.id)
}

async function loadContext(id?: number) {
  const i = id || ctxDrawer.list[0]?.id
  if (!i) return
  try {
    const r: any = await request.get('/sys/log/context', { params: { id: i, minutes: 10, limit: 50 } })
    ctxDrawer.list = r.data || []
  } catch (e) {
    ElMessage.error('加载上下文失败')
  }
}

async function loadSession(id?: number) {
  const i = id || ctxDrawer.list[0]?.id
  if (!i) return
  try {
    const r: any = await request.get('/sys/log/session', { params: { id: i, minutes: 30, limit: 50 } })
    ctxDrawer.list = r.data || []
  } catch (e) {
    ElMessage.error('加载同会话失败')
  }
}

// 重放
const replayDialog = reactive({
  open: false,
  title: '重放请求',
  row: null as SysLog | null
})

function onReplay(row: SysLog) {
  replayDialog.row = row
  replayDialog.open = true
}

function confirmReplay() {
  const row = replayDialog.row
  if (!row) return
  const base = window.location.origin
  const url = row.requestUrl || ''
  if (row.requestMethod === 'GET' || !row.requestMethod) {
    const full = base + url + (url.includes('?') ? '&' : '?') + '_rlog=' + (row.id || '')
    window.open(full, '_blank')
    ElMessage.success('已在新标签页打开 GET 请求')
  } else {
    ElMessageBox.confirm(`该请求是 ${row.requestMethod}，重放可能造成数据修改。确定要发吗？`, '警告', { type: 'warning' })
      .then(() => {
        request({
          url,
          method: (row.requestMethod as any),
          data: row.requestParams ? (() => { try { return JSON.parse(row.requestParams) } catch { return row.requestParams } })() : null
        }).then(() => {
          ElMessage.success('重放已提交')
        }).catch((e: any) => {
          ElMessage.error('重放失败：' + (e?.message || ''))
        })
      })
      .catch(() => {})
  }
  replayDialog.open = false
}

const tick = () => { load() }

watch(autoRefresh, (v) => {
  if (v) {
    autoRefreshTimer = window.setInterval(tick, 5000) as any
    tick()
    ElMessage.success('已开启 5s 自动刷新')
  } else {
    if (autoRefreshTimer) { clearInterval(autoRefreshTimer); autoRefreshTimer = null }
  }
})

function resizeChart() { chartInst?.resize() }

onMounted(() => {
  loadColConfig()
  loadFilter()
  applyUrlFilter()
  applyColVisible()
  loadEnums()
  refreshAll()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chartInst?.dispose()
  chartInst = null
  if (autoRefreshTimer) clearInterval(autoRefreshTimer)
})

async function loadStats() {
  try {
    const r: any = await sysApi.logStats()
    stats.value = r.data || {}
  } catch (e) {
    stats.value = {}
  }
}

async function loadTrend() {
  try {
    const r: any = await sysApi.logTrend(trendDays.value)
    const data = r.data || []
    // 补齐：把缺失日期补 0，让趋势图连续
    trend.value = fillTrendGaps(data, trendDays.value)
    await nextTick()
    drawChart()
  } catch (e) {
    trend.value = []
  }
}

function fillTrendGaps(rows: Array<{date: string; total: number; success: number; fail: number}>, days: number) {
  const map = new Map(rows.map(r => [r.date, r]))
  const out: typeof rows = []
  const now = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 86400000)
    const key = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    out.push(map.get(key) || { date: key, total: 0, success: 0, fail: 0 })
  }
  return out
}

const trendEmpty = computed(() => trend.value.every(p => p.total === 0))

async function loadAnomaly() {
  try {
    const r: any = await sysApi.logAnomaly(anomalyDays.value, anomalyTop)
    anomaly.value = r.data || { topIps: [], topUrls: [] }
  } catch {
    anomaly.value = { topIps: [], topUrls: [] }
  }
}

function drawChart() {
  if (!chartRef.value) return
  if (!chartInst) chartInst = echarts.init(chartRef.value)
  const dates = trend.value.map(p => p.date.slice(5))
  const succ = trend.value.map(p => p.success)
  const fail = trend.value.map(p => p.fail)
  const maxFail = Math.max(0, ...fail)
  const failDays = fail.filter(v => v > 0).length || 0
  const avgFail = failDays > 0 ? fail.reduce((a, b) => a + b, 0) / failDays : 0
  const yMax = Math.max(2, maxFail * 1.4)
  chartInst.setOption({
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['成功', '失败', '日均失败'], top: 0, right: 0 },
    xAxis: { type: 'category', data: dates, boundaryGap: true, axisLine: { lineStyle: { color: '#dcdfe6' } } },
    yAxis: { type: 'value', min: 0, max: Math.ceil(yMax), splitLine: { lineStyle: { color: '#f0f0f0' } } },
    series: [
      {
        name: '成功', type: 'bar', stack: 'total', data: succ,
        itemStyle: { color: '#67c23a', borderRadius: [3,3,0,0] },
        barWidth: 18
      },
      {
        name: '失败', type: 'bar', stack: 'total', data: fail,
        itemStyle: { color: '#f56c6c', borderRadius: [3,3,0,0] }
      },
      {
        name: '日均失败', type: 'line', data: fail.map(() => Number(avgFail.toFixed(2))),
        symbol: 'none',
        lineStyle: { color: '#e6a23c', type: 'dashed', width: 1.5 },
        itemStyle: { color: '#e6a23c' },
        z: 10,
        tooltip: { show: false }
      }
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

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}
.stat-card {
  display: flex; align-items: center; gap: 14px;
  padding: 18px 20px;
  background: #fff; border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  border: 1px solid #ebeef5;
  transition: transform .15s, box-shadow .15s;
}
.stat-card.clickable { cursor: pointer }
.stat-card.clickable:hover { border-color: #67c23a; box-shadow: 0 6px 14px rgba(103,194,58,0.18) }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.08) }
.link { color: #67c23a; font-weight: 500 }
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
.anomaly-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.anom-head {
  font-weight: 600; color: #303133; font-size: 13px;
  margin-bottom: 8px; padding-bottom: 6px;
  border-bottom: 1px dashed #ebeef5;
}
.anom-list { list-style: none; padding: 0; margin: 0 }
.anom-item {
  display: grid; grid-template-columns: 24px 1fr auto auto;
  gap: 8px; align-items: center;
  padding: 6px 4px; font-size: 13px;
  border-bottom: 1px dashed #f5f5f5;
}
.anom-item:last-child { border-bottom: none }
.rank {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px;
  border-radius: 4px; font-size: 12px;
  font-weight: 600; color: #fff;
}
.rank-1 { background: #f56c6c }
.rank-2 { background: #e6a23c }
.rank-3 { background: #67c23a }
.rank-default { background: #c0c4cc }
.anom-item .key { overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.anom-item .fail { color: #909399; font-size: 12px }

.trend-card { margin-bottom: 14px }
.trend-header { display: flex; justify-content: space-between; align-items: center }
.trend-title { font-size: 15px; font-weight: 600; color: #303133 }
.trend-actions { display: flex; gap: 14px; align-items: center }
.chart { width: 100%; height: 240px }
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 12px }
.block {
  flex: 1;
  margin: 0;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow: auto;
}
.ua { max-height: 120px }
.err-pre {
  flex: 1;
  margin: 0;
  padding: 12px;
  background: #fef0f0;
  border-radius: 4px;
  color: #c45656;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
}
.cost-slow { color: #f56c6c; font-weight: 600 }
.cost-warn { color: #e6a23c; font-weight: 600 }
.err-text { color: #c45656; font-size: 12px }
:deep(.row-fail) { background-color: #fef0f0 !important }
:deep(.row-fail td) { background-color: #fef0f0 !important }

.expand { padding: 4px 4px 4px 32px; background: #fafbfc }
.expand-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.expand-section {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 14px;
}
.expand-section.err-section {
  grid-column: 1 / -1;
  border-color: #fbc4c4;
  background: #fff8f8;
}
.sec-head {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 6px; margin-bottom: 6px;
  border-bottom: 1px dashed #ebeef5;
}
.sec-title { font-weight: 600; color: #303133; font-size: 13px }
.kv { display: flex; gap: 12px; padding: 4px 0; align-items: flex-start; font-size: 13px }
.kv .k { width: 80px; color: #909399; flex-shrink: 0 }
.kv .v { flex: 1; color: #303133; word-break: break-all }
.kv-block { flex-direction: column; align-items: stretch }
.kv-block .k { width: auto; margin-bottom: 4px }
.block-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 2px 0;
}
.expand-footer {
  display: flex; gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}
.expand :deep(.block) {
  margin: 0; padding: 10px 12px;
  background: #f5f7fa; border-radius: 4px;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap; word-break: break-all;
  max-height: 260px; overflow: auto;
  color: #303133;
}
.expand :deep(.block.ua) { max-height: 100px }
.expand :deep(.err-pre) {
  margin: 0; padding: 10px 12px;
  background: #fef0f0; border-radius: 4px;
  color: #c45656;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap; word-break: break-all;
  max-height: 240px; overflow: auto;
}

.muted { color: #c0c4cc }

.col-row {
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
}

.ctx-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
</style>
