<template>
  <div class="kiosk-page">
    <!-- ============ 离线告警 banner ============ -->
    <el-alert
      v-if="offlineAlerts.length > 0"
      class="mb16 offline-banner"
      :title="`检测到 ${offlineAlerts.length} 台设备无响应（推送渠道：${pushChannel}）`"
      type="error"
      show-icon
      :closable="false">
      <template #default>
        <div class="offline-list">
          <div
            v-for="a in offlineAlerts.slice(0, 3)"
            :key="a.id"
            class="offline-item">
            <el-tag
              :type="(OFFLINE_SEVERITY_TAG[a.severity]?.type as any)"
              size="small"
              effect="dark">
              {{ OFFLINE_SEVERITY_TAG[a.severity]?.label }}
            </el-tag>
            <span class="offline-name">
              {{ a.deviceCode }}
              <span class="muted" v-if="a.location">（{{ a.location }}）</span>
            </span>
            <span class="offline-age muted">
              <template v-if="a.offlineMinutes < 0">从未心跳</template>
              <template v-else>已离线 {{ a.offlineMinutes }} 分钟</template>
              <small v-if="a.thresholdInfoMin"> · INFO≥{{ a.thresholdInfoMin }}min</small>
            </span>
            <el-button
              link
              type="primary"
              size="small"
              @click="onHeartbeatManual(a.deviceCode)">
              立即探活
            </el-button>
          </div>
          <div v-if="offlineAlerts.length > 3" class="muted">
            等 {{ offlineAlerts.length - 3 }} 台...
          </div>
        </div>
      </template>
    </el-alert>

    <!-- ============ KPI 卡片（已接通 stats()） ============ -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="6">
        <el-card>
          <div class="stat" :class="{ 'stat-blink': blink.today }">
            <span class="stat-label">今日签到</span>
            <strong class="stat-value accent">{{ stats.todayCheckins }}</strong>
          </div>
          <div class="stat-sub">
            <el-tag :type="KIOSK_STATUS_LABEL.CHECKED_IN.type" size="small" effect="plain">
              等待 {{ stats.waitingCount }}
            </el-tag>
            <el-tag :type="KIOSK_STATUS_LABEL.IN_SERVICE.type" size="small" effect="plain">
              服务中 {{ stats.inServiceCount }}
            </el-tag>
            <el-tag :type="KIOSK_STATUS_LABEL.FINISHED.type" size="small" effect="plain">
              完成 {{ stats.finishedCount }}
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="stat" :class="{ 'stat-blink': blink.device }">
            <span class="stat-label">活跃设备</span>
            <strong class="stat-value">
              {{ stats.activeDevices }}<small> / {{ stats.totalDevices }}</small>
            </strong>
          </div>
          <div class="stat-sub">
            <el-tag type="success" size="small" effect="plain">
              <span class="dot dot-on"></span>在线 {{ stats.deviceStatusBreakdown.ACTIVE }}
            </el-tag>
            <el-tag type="info" size="small" effect="plain" v-if="stats.deviceStatusBreakdown.INACTIVE">
              停用 {{ stats.deviceStatusBreakdown.INACTIVE }}
            </el-tag>
            <el-tag type="danger" size="small" effect="plain" v-if="stats.deviceStatusBreakdown.OFFLINE">
              <span class="dot dot-off"></span>离线 {{ stats.deviceStatusBreakdown.OFFLINE }}
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="stat">
            <span class="stat-label">平均等待时长</span>
            <strong class="stat-value">
              {{ stats.avgWaitMinutes }}<small> min</small>
            </strong>
          </div>
          <div class="stat-sub">
            <span class="stat-tip">仅统计已完成记录</span>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card>
          <div class="stat">
            <span class="stat-label">设备心跳</span>
            <strong class="stat-value" :style="{ color: heartbeatAlive ? '#67c23a' : '#f56c6c' }">
              <span class="dot" :class="heartbeatAlive ? 'dot-on' : 'dot-off'"></span>
              {{ heartbeatAlive ? '正常' : '暂停' }}
            </strong>
          </div>
          <div class="stat-sub">
            <span class="stat-tip">每 30s 探活 · 上次 {{ heartbeatAgeText }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 主体 Tab ============ -->
    <el-tabs v-model="tab">
      <!-- ============================================
           Tab 1 · 设备管理（含实时心跳）
           ============================================ -->
      <el-tab-pane label="设备管理" name="device">
        <div class="toolbar">
          <el-button type="primary" :icon="Plus" @click="deviceDialog = true">
            注册设备
          </el-button>
          <el-button :icon="Refresh" :loading="loading" @click="loadAll">
            刷新
          </el-button>
          <span class="toolbar-tip">
            💡 心跳每 30 秒自动刷新 · 超过 5 分钟视为离线
          </span>
        </div>

        <el-table :data="devices" v-loading="loading" border style="margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="deviceCode" label="设备编码" min-width="120" />
          <el-table-column prop="deviceName" label="名称" min-width="120" />
          <el-table-column prop="location" label="位置" min-width="120" />
          <el-table-column prop="ipAddress" label="IP" min-width="120" />
          <el-table-column label="心跳" min-width="180">
            <template #default="{ row }">
              <span :class="heartbeatDot(row.lastHeartbeat).cls">
                ●
              </span>
              {{ formatTime(row.lastHeartbeat) || '从未心跳' }}
              <span class="muted" v-if="row.lastHeartbeat">
                · {{ heartbeatAge(row.lastHeartbeat) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="(DEVICE_STATUS_LABEL[deviceEffectiveStatus(row)]?.type as any) || 'info'"
                effect="plain"
                size="small">
                {{ DEVICE_STATUS_LABEL[deviceEffectiveStatus(row)]?.label || row.status || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="onHeartbeatManual(row.deviceCode)">
                手动心跳
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============================================
           Tab 2 · 签到记录（含服务生命周期按钮 + walk-in）
           ============================================ -->
      <el-tab-pane label="签到记录" name="checkin">
        <div class="toolbar">
          <el-button type="success" :icon="Promotion" @click="walkInDialog = true">
            Walk-in 散客签到
          </el-button>
          <el-button :icon="Refresh" :loading="loading" @click="loadAll">
            刷新
          </el-button>
          <el-select v-model="checkinFilter.status" placeholder="筛选状态" clearable style="width: 140px; margin-left: 8px" @change="loadCheckins">
            <el-option label="等待中" value="CHECKED_IN" />
            <el-option label="服务中" value="IN_SERVICE" />
            <el-option label="已完成" value="FINISHED" />
          </el-select>
          <span class="toolbar-tip">
            💡 状态机：CHECKED_IN → IN_SERVICE → FINISHED
          </span>
        </div>

        <el-table :data="filteredCheckins" v-loading="loading" border style="margin-top: 12px;">
          <el-table-column prop="id" label="签到 ID" width="80" />
          <el-table-column label="设备" width="100">
            <template #default="{ row }">
              {{ deviceCodeOf(row.deviceId) }}
            </template>
          </el-table-column>
          <el-table-column label="会员" width="120">
            <template #default="{ row }">
              <span v-if="row.memberId">
                #{{ row.memberId }}
                <span class="muted">{{ row.notes?.split(' ')?.slice(-1) || '' }}</span>
              </span>
              <el-tag v-else type="warning" size="small">Walk-in</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="checkinMethod" label="方式" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="(methodTagType(row.checkinMethod) as any)" effect="plain">
                {{ methodLabel(row.checkinMethod) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="checkinTime" label="签到时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.checkinTime) }}</template>
          </el-table-column>
          <el-table-column label="开始服务" width="170">
            <template #default="{ row }">{{ formatTime(row.serviceStartedAt) }}</template>
          </el-table-column>
          <el-table-column label="结束服务" width="170">
            <template #default="{ row }">{{ formatTime(row.serviceFinishedAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="(KIOSK_STATUS_LABEL[row.status]?.type as any) || 'info'"
                size="small">
                {{ KIOSK_STATUS_LABEL[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'CHECKED_IN'"
                link
                type="primary"
                :icon="VideoPlay"
                @click="onStartService(row)">
                开始服务
              </el-button>
              <el-button
                v-if="row.status === 'IN_SERVICE'"
                link
                type="success"
                :icon="VideoCameraFilled"
                @click="onFinishService(row)">
                结束服务
              </el-button>
              <el-button link :icon="View" @click="onViewCheckin(row)">
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && filteredCheckins.length === 0" description="暂无签到记录" />
      </el-tab-pane>

      <!-- ============================================
           Tab 3 · 运营分析（24h 客流分布 + 技师绩效 + 趋势）
           ============================================ -->
      <el-tab-pane label="运营分析" name="analytics">
        <div class="toolbar">
          <el-date-picker
            v-model="analyticsDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 180px"
            @change="loadAnalytics" />
          <el-button :icon="Refresh" :loading="analyticsLoading" @click="loadAnalytics">
            刷新
          </el-button>
          <el-divider direction="vertical" />
          <span class="toolbar-tip">当日客流目标：</span>
          <el-input-number
            v-model="dailyTarget"
            :min="1" :max="10000"
            size="small"
            style="width: 110px"
            @change="onUpdateDailyTarget" />
          <span class="toolbar-tip">
            <el-tag
              :type="targetReached ? 'success' : targetProgress >= 60 ? 'warning' : 'danger'"
              size="small">
              完成率 {{ targetProgress }}%
            </el-tag>
            <small class="muted" style="margin-left: 6px;">
              当前 {{ hourlyTotal }} / 目标 {{ dailyTarget }}
            </small>
          </span>
          <span class="toolbar-tip" style="margin-left: auto;">
            💡 客流分布按签到时间聚合；技师绩效按 staffId 聚合
          </span>
        </div>

        <el-row :gutter="16" style="margin-top: 12px;">
          <el-col :span="14">
            <el-card>
              <template #header>
                <span><el-icon><DataLine /></el-icon> 24h 客流分布（vs 目标线）</span>
              </template>
              <div ref="hourlyChartEl" class="chart-area" />
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card>
              <template #header>
                <span><el-icon><TrendCharts /></el-icon> 技师绩效对比</span>
              </template>
              <div ref="staffChartEl" class="chart-area" />
            </el-card>
          </el-col>
        </el-row>

        <el-card style="margin-top: 16px;">
          <template #header>
            <span>技师绩效明细</span>
          </template>
          <el-table :data="staffStats" v-loading="analyticsLoading" border>
            <el-table-column prop="staffId" label="技师 ID" width="100" />
            <el-table-column prop="total" label="服务总数" width="120" sortable />
            <el-table-column prop="finished" label="已完成" width="100" />
            <el-table-column label="完成率" width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.finishRate"
                  :color="row.finishRate >= 80 ? '#67c23a' :
                          row.finishRate >= 50 ? '#e6a23c' : '#f56c6c'"
                  :stroke-width="14"
                  :text-inside="true" />
              </template>
            </el-table-column>
            <el-table-column prop="avgWaitMinutes" label="平均等待 (min)" width="160">
              <template #default="{ row }">
                <span :style="{ color: row.avgWaitMinutes > 10 ? '#f56c6c' :
                                row.avgWaitMinutes > 5 ? '#e6a23c' : '#67c23a' }">
                  {{ row.avgWaitMinutes }} min
                </span>
              </template>
            </el-table-column>
            <el-table-column label="周/月趋势" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click="onShowTrend(row.staffId)">
                  查看趋势
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!analyticsLoading && !staffStats.length" description="当日暂无技师服务记录" />
        </el-card>
      </el-tab-pane>

    </el-tabs>

    <!-- ============================================
         注册设备 Dialog
         ============================================ -->
    <el-dialog v-model="deviceDialog" title="注册 Kiosk 设备" width="500px">
      <el-form :model="deviceForm" label-width="100px">
        <el-form-item label="设备编码">
          <el-input v-model="deviceForm.deviceCode" placeholder="例如 KIOSK-01" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="deviceForm.deviceName" placeholder="门店前台自助机" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="deviceForm.location" placeholder="门店前台" />
        </el-form-item>
        <el-form-item label="IP 地址">
          <el-input v-model="deviceForm.ipAddress" placeholder="例如 192.168.1.101" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialog = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="registerDevice">注册</el-button>
      </template>
    </el-dialog>

    <!-- ============================================
         Walk-in 散客签到 Dialog
         ============================================ -->
    <el-dialog v-model="walkInDialog" title="Walk-in 散客签到（无预约直接到店）" width="520px">
      <el-form :model="walkInForm" label-width="100px">
        <el-form-item label="设备">
          <el-select v-model="walkInForm.deviceCode" placeholder="选择 Kiosk 设备" filterable>
            <el-option
              v-for="d in activeDevices"
              :key="d.id"
              :label="`${d.deviceCode}（${d.location || '未设位置'}）`"
              :value="d.deviceCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员 ID">
          <el-input v-model.number="walkInForm.memberId" placeholder="可选（与手机号二选一）" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="walkInForm.phone" placeholder="可选（自动匹配会员）" />
        </el-form-item>
        <el-form-item label="服务项目">
          <el-input v-model="walkInForm.serviceItem" placeholder="例如 基础护理 / 头皮 SPA" />
        </el-form-item>
        <el-form-item label="技师 ID">
          <el-input v-model.number="walkInForm.employeeId" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="walkInForm.notes"
            type="textarea"
            :rows="2"
            placeholder="例如 首次到店" />
        </el-form-item>
      </el-form>

      <div class="walkin-info" v-if="lastWalkInResult">
        <el-divider>最近一次结果</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="签到 ID">#{{ lastWalkInResult.checkinId }}</el-descriptions-item>
          <el-descriptions-item label="预约 ID">#{{ lastWalkInResult.bookingId }}</el-descriptions-item>
          <el-descriptions-item label="预约编号">{{ lastWalkInResult.bookingNo }}</el-descriptions-item>
          <el-descriptions-item label="服务项目">{{ lastWalkInResult.serviceItem }}</el-descriptions-item>
          <el-descriptions-item label="会员">{{ lastWalkInResult.memberName }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ lastWalkInResult.memberPhone }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <template #footer>
        <el-button @click="walkInDialog = false">关闭</el-button>
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="actionLoading"
          @click="onWalkIn">
          立即签到
        </el-button>
      </template>
    </el-dialog>

    <!-- ============================================
         签到详情 Dialog
         ============================================ -->
    <el-dialog v-model="detailDialog" title="签到详情" width="500px">
      <el-descriptions v-if="currentCheckin" :column="2" border size="small">
        <el-descriptions-item label="签到 ID">#{{ currentCheckin.id }}</el-descriptions-item>
        <el-descriptions-item label="会员">{{ currentCheckin.memberId || 'Walk-in' }}</el-descriptions-item>
        <el-descriptions-item label="预约 ID">{{ currentCheckin.bookingId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ deviceCodeOf(currentCheckin.deviceId) }}</el-descriptions-item>
        <el-descriptions-item label="方式">{{ methodLabel(currentCheckin.checkinMethod) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="(KIOSK_STATUS_LABEL[currentCheckin.status]?.type as any)" size="small">
            {{ KIOSK_STATUS_LABEL[currentCheckin.status]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="签到时间">{{ formatTime(currentCheckin.checkinTime) }}</el-descriptions-item>
        <el-descriptions-item label="开始服务">{{ formatTime(currentCheckin.serviceStartedAt) }}</el-descriptions-item>
        <el-descriptions-item label="结束服务">{{ formatTime(currentCheckin.serviceFinishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="技师">{{ currentCheckin.staffId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentCheckin.notes || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- ============================================
         技师周/月趋势 Dialog
         ============================================ -->
    <el-dialog
      v-model="trendDialog"
      :title="`技师 #${trendStaffId} 周/月趋势对比`"
      width="780px"
      @opened="renderTrendChart">
      <div class="toolbar">
        <el-select v-model="trendPeriod" style="width: 110px" @change="loadStaffTrend">
          <el-option label="近 7 天" value="week" />
          <el-option label="近 30 天" value="month" />
        </el-select>
        <el-date-picker
          v-model="trendEndDate"
          type="date"
          placeholder="截止日期"
          value-format="YYYY-MM-DD"
          style="width: 160px"
          @change="loadStaffTrend" />
        <el-button :icon="Refresh" :loading="trendLoading" @click="loadStaffTrend">刷新</el-button>
        <el-button
          v-if="staffStats.length > 1"
          :type="compareMode ? 'primary' : 'default'"
          @click="compareMode = !compareMode">
          {{ compareMode ? '退出对比' : `对比 TOP${Math.min(8, staffStats.length)} 技师` }}
        </el-button>
        <span class="toolbar-tip">完成率 / 平均等待 双轴对比</span>
      </div>

      <div ref="trendChartEl" class="chart-area" style="height: 360px; margin-top: 12px;" />

      <el-table
        :data="trendRows"
        v-loading="trendLoading"
        border
        size="small"
        style="margin-top: 12px;">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column prop="total" label="服务总数" width="100" />
        <el-table-column prop="finished" label="已完成" width="100" />
        <el-table-column label="完成率" width="120">
          <template #default="{ row }">
            <el-progress
              :percentage="row.finishRate"
              :stroke-width="10"
              :color="row.finishRate >= 80 ? '#67c23a' :
                      row.finishRate >= 50 ? '#e6a23c' : '#f56c6c'" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="compareMode"
          label="对比组成员（每人当日）"
          min-width="240">
          <template #default="{ row }">
            <span
              v-for="(s, i) in row.perStaff"
              :key="s.staffId"
              class="trend-pill">
              <strong>#{{ s.staffId }}</strong> · {{ s.total }}单
              <small class="muted">({{ s.avgWaitMinutes }}min)</small>
              <span v-if="i < row.perStaff.length - 1"> | </span>
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Refresh, Promotion, VideoPlay, VideoCameraFilled, View,
  DataLine, TrendCharts
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  kioskApi,
  KIOSK_STATUS_LABEL,
  DEVICE_STATUS_LABEL,
  OFFLINE_SEVERITY_TAG
} from '@/api/kiosk'

echarts.use([
  BarChart, LineChart,
  TooltipComponent, LegendComponent, GridComponent,
  CanvasRenderer
])

// ====================================================================
// 状态
// ====================================================================
const tab = ref('device')

const stats = reactive({
  todayCheckins: 0,
  waitingCount: 0,
  inServiceCount: 0,
  finishedCount: 0,
  avgWaitMinutes: 0,
  activeDevices: 0,
  totalDevices: 0,
  deviceStatusBreakdown: { ACTIVE: 0, INACTIVE: 0, OFFLINE: 0 } as Record<string, number>,
  checkinStatusBreakdown: { CHECKED_IN: 0, IN_SERVICE: 0, FINISHED: 0 } as Record<string, number>,
  timestamp: ''
})

const devices  = ref<any[]>([])
const checkins = ref<any[]>([])
const loading  = ref(false)
const actionLoading = ref(false)

const checkinFilter = reactive({
  status: '' as '' | 'CHECKED_IN' | 'IN_SERVICE' | 'FINISHED'
})

// blink flags（数据更新时短闪一下）
const blink = reactive({ today: false, device: false })

// ====================================================================
// 心跳轮询（30s）+ 健康指示
// ====================================================================
let statsTimer: number | null = null
let deviceTimer: number | null = null
let heartbeatTimer: number | null = null
let lastHeartbeatAt = 0

const heartbeatAlive = ref(true)
const heartbeatAgeText = computed(() => {
  if (!lastHeartbeatAt) return '从未'
  const age = Math.round((Date.now() - lastHeartbeatAt) / 1000)
  if (age < 60) return `${age} 秒前`
  return `${Math.round(age / 60)} 分钟前`
})

const startStatsPolling = () => {
  // 立即拉一次 + 每 30 秒刷新
  loadStats()
  if (statsTimer) clearInterval(statsTimer)
  statsTimer = window.setInterval(loadStats, 30_000)
}

const stopStatsPolling = () => {
  if (statsTimer) { clearInterval(statsTimer); statsTimer = null }
}

// 设备轮询（用于 UI 上心跳时间跟着刷新；stats 已包含设备状态）
const startDevicePolling = () => {
  if (deviceTimer) clearInterval(deviceTimer)
  deviceTimer = window.setInterval(() => {
    if (tab.value === 'device') loadDevices()
  }, 60_000)
}
const stopDevicePolling = () => {
  if (deviceTimer) { clearInterval(deviceTimer); deviceTimer = null }
}

// 演示用：每 30 秒模拟一次心跳（仅在没有真实设备时）
const startDemoHeartbeat = () => {
  if (heartbeatTimer) return
  const demoDevices = devices.value.filter(d => d.status === 'ACTIVE')
  if (!demoDevices.length) return
  heartbeatTimer = window.setInterval(async () => {
    try {
      const r: any = await kioskApi.heartbeat(demoDevices[0].deviceCode, {})
      const ts = new Date().toLocaleTimeString()
      if (r?.success !== false) {
        lastHeartbeatAt = Date.now()
        heartbeatAlive.value = true
        // 静默更新该设备的心跳时间（避免轮询）
        const target = devices.value.find(d => d.deviceCode === demoDevices[0].deviceCode)
        if (target) target.lastHeartbeat = new Date().toISOString()
      } else {
        heartbeatAlive.value = false
      }
    } catch {
      heartbeatAlive.value = false
    }
  }, 30_000)
}
const stopDemoHeartbeat = () => {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
}

// ====================================================================
// 加载
// ====================================================================
async function loadStats() {
  try {
    const r: any = await kioskApi.stats()
    const data = r?.data || r || {}
    const beforeToday = stats.todayCheckins
    const beforeDevice = stats.activeDevices

    Object.assign(stats, data)

    // 触发 blink
    if (stats.todayCheckins !== beforeToday) {
      blink.today = true; setTimeout(() => blink.today = false, 1200)
    }
    if (stats.activeDevices !== beforeDevice) {
      blink.device = true; setTimeout(() => blink.device = false, 1200)
    }
    lastHeartbeatAt = Date.now()
    heartbeatAlive.value = true
  } catch (e: any) {
    heartbeatAlive.value = false
    ElMessage.warning(e?.message || 'KPI 拉取失败')
  }
}

async function loadDevices() {
  try {
    const r: any = await kioskApi.listDevices()
    devices.value = (r?.data && (Array.isArray(r.data) ? r.data : r.data.list)) || []
  } catch (e) {
    devices.value = []
  }
}

async function loadCheckins() {
  loading.value = true
  try {
    const r: any = await kioskApi.listCheckins({
      ...(checkinFilter.status ? { status: checkinFilter.status } : {})
    })
    checkins.value = (r?.data && (Array.isArray(r.data) ? r.data : r.data.list)) || []
  } catch {
    checkins.value = []
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadDevices(), loadCheckins()])
  } finally {
    loading.value = false
    if (tab.value === 'device') startDemoHeartbeat()
  }
}

// ====================================================================
// 设备 / 服务操作
// ====================================================================
const deviceDialog = ref(false)
const deviceForm = reactive({
  deviceCode: '',
  deviceName: '',
  location: '门店前台',
  ipAddress: ''
})

async function registerDevice() {
  if (!deviceForm.deviceCode) {
    ElMessage.warning('设备编码必填')
    return
  }
  actionLoading.value = true
  try {
    await kioskApi.registerDevice({ ...deviceForm })
    ElMessage.success('已注册')
    deviceDialog.value = false
    deviceForm.deviceCode = deviceForm.deviceName = deviceForm.ipAddress = ''
    await loadDevices()
    await loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '注册失败')
  } finally {
    actionLoading.value = false
  }
}

async function onHeartbeatManual(code: string) {
  try {
    await kioskApi.heartbeat(code, {})
    ElMessage.success(`设备 ${code} 心跳已上报`)
    await loadDevices()
  } catch (e: any) {
    ElMessage.error(e?.message || '心跳失败')
  }
}

async function onStartService(row: any) {
  try {
    await ElMessageBox.confirm(
      `签到 #${row.id}（会员 ${row.memberId || 'Walk-in'}）开始服务？`,
      '开始服务',
      { type: 'success', confirmButtonText: '开始' }
    )
  } catch { return }
  actionLoading.value = true
  try {
    await kioskApi.startService(row.id, {})
    ElMessage.success('服务已开始')
    await loadCheckins()
    await loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '开始服务失败')
  } finally {
    actionLoading.value = false
  }
}

async function onFinishService(row: any) {
  try {
    await ElMessageBox.confirm(
      `签到 #${row.id}（会员 ${row.memberId || 'Walk-in'}）结束服务？`,
      '结束服务',
      { type: 'success', confirmButtonText: '结束' }
    )
  } catch { return }
  actionLoading.value = true
  try {
    await kioskApi.finishService(row.id, { notes: '接待员在 Kiosk 完成' })
    ElMessage.success('服务已完成')
    await loadCheckins()
    await loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '结束服务失败')
  } finally {
    actionLoading.value = false
  }
}

// ====================================================================
// Walk-in 散客签到
// ====================================================================
const walkInDialog = ref(false)
const walkInForm = reactive({
  deviceCode: '',
  memberId: undefined as number | undefined,
  phone: '',
  serviceItem: '',
  employeeId: undefined as number | undefined,
  notes: ''
})
const lastWalkInResult = ref<any>(null)

const activeDevices = computed(() => devices.value.filter(d => d.status === 'ACTIVE'))

async function onWalkIn() {
  if (!walkInForm.deviceCode) { ElMessage.warning('请选择设备'); return }
  if (!walkInForm.serviceItem) { ElMessage.warning('服务项目必填'); return }
  if (!walkInForm.memberId && !walkInForm.phone) {
    ElMessage.warning('会员 ID 或手机号至少填一个'); return
  }
  actionLoading.value = true
  try {
    const r: any = await kioskApi.walkIn({
      deviceCode: walkInForm.deviceCode,
      memberId:   walkInForm.memberId || undefined,
      phone:      walkInForm.phone || undefined,
      serviceItem: walkInForm.serviceItem,
      employeeId: walkInForm.employeeId || undefined,
      notes:      walkInForm.notes || undefined
    })
    const data = r?.data || r
    lastWalkInResult.value = data
    ElMessage.success(`Walk-in 签到成功 · 预约 ${data?.bookingNo || ''}`)
    // 不关闭 dialog，让用户看到结果；重置表单
    walkInForm.memberId = undefined
    walkInForm.phone = ''
    walkInForm.serviceItem = ''
    walkInForm.employeeId = undefined
    walkInForm.notes = ''
    await loadCheckins()
    await loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || 'Walk-in 签到失败')
  } finally {
    actionLoading.value = false
  }
}

// ====================================================================
// 详情
// ====================================================================
const detailDialog   = ref(false)
const currentCheckin = ref<any>(null)

async function onViewCheckin(row: any) {
  detailDialog.value = true
  try {
    const r: any = await kioskApi.getCheckin(row.id)
    currentCheckin.value = r?.data || r
  } catch {
    currentCheckin.value = row
  }
}

// ====================================================================
// 计算/工具
// ====================================================================
const filteredCheckins = computed(() => {
  let arr = checkins.value
  if (checkinFilter.status) {
    arr = arr.filter(c => c.status === checkinFilter.status)
  }
  return arr.slice().sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
})

const deviceCodeOf = (deviceId: number) =>
  devices.value.find(d => d.id === deviceId)?.deviceCode || `#${deviceId}`

/** 设备有效状态：ACTIVE 字段 + 5min 心跳 → 离线 */
const HEARTBEAT_OFFLINE_MS = 5 * 60 * 1000

const deviceEffectiveStatus = (row: any): string => {
  if (row.status === 'INACTIVE') return 'INACTIVE'
  if (!row.lastHeartbeat) return 'OFFLINE'
  const age = Date.now() - new Date(row.lastHeartbeat).getTime()
  if (age > HEARTBEAT_OFFLINE_MS) return 'OFFLINE'
  return 'ACTIVE'
}

const heartbeatDot = (lastHeartbeat?: string) => {
  if (!lastHeartbeat) return { cls: 'dot dot-off' }
  const age = Date.now() - new Date(lastHeartbeat).getTime()
  if (age > HEARTBEAT_OFFLINE_MS) return { cls: 'dot dot-off' }
  return { cls: 'dot dot-on' }
}

const heartbeatAge = (lastHeartbeat: string) => {
  const age = Math.floor((Date.now() - new Date(lastHeartbeat).getTime()) / 1000)
  if (age < 60) return `${age}s`
  if (age < 3600) return `${Math.round(age / 60)}m`
  return `${Math.round(age / 3600)}h`
}

const methodTagType = (m?: string): 'success' | 'warning' | 'info' => {
  if (m === 'QRCODE')  return 'info'
  if (m === 'WALK_IN') return 'warning'
  if (m === 'MANUAL')  return 'success'
  return 'info'
}

const methodLabel = (m?: string) =>
  ({ QRCODE: '扫码', MANUAL: '人工', WALK_IN: 'Walk-in' } as any)[m || 'QRCODE'] || (m || '—')

const formatTime = (t: string | Date | null | undefined) =>
  t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : ''

// ====================================================================
// ⭐ P2-分析 · 24h 客流 + 技师绩效 + 离线告警
// ====================================================================

// --- 离线告警
const offlineAlerts = ref<any[]>([])
const pushChannel   = ref<string>('IN_APP')   // 与 backend kiosk.alert.push-channel 对齐
const loadOfflineAlerts = async () => {
  try {
    const r: any = await kioskApi.offlineAlerts()
    offlineAlerts.value = (r?.data && (Array.isArray(r.data) ? r.data : r.data.list)) || []
  } catch {
    offlineAlerts.value = []
  }
}

// --- 分析
const analyticsDate    = ref<string>(new Date().toISOString().slice(0, 10))  // yyyy-MM-dd
const staffStats       = ref<any[]>([])
const hourlyBuckets    = ref<any[]>([])
const analyticsLoading = ref(false)

const hourlyChartEl = ref<HTMLDivElement | null>(null)
const staffChartEl  = ref<HTMLDivElement | null>(null)
let hourlyChart: echarts.ECharts | null = null
let staffChart:  echarts.ECharts | null = null

// --- 客流日均目标
const dailyTarget = ref<number>(80)
const hourlyTotal = computed(() =>
  hourlyBuckets.value.reduce((s, b) => s + (b.count || 0), 0)
)
const targetProgress = computed(() =>
  dailyTarget.value > 0
    ? Math.round((hourlyTotal.value * 100) / dailyTarget.value)
    : 0
)
const targetReached = computed(() => targetProgress.value >= 100)

const loadAnalytics = async () => {
  analyticsLoading.value = true
  try {
    const [h, s, t] = await Promise.all([
      kioskApi.hourlyStats(analyticsDate.value || undefined),
      kioskApi.staffStats(analyticsDate.value || undefined),
      kioskApi.dailyTarget(analyticsDate.value || undefined)
    ])
    hourlyBuckets.value = (h as any)?.data || []
    staffStats.value    = (s as any)?.data || []
    const tData = (t as any)?.data
    if (tData && typeof tData.target === 'number') {
      dailyTarget.value = tData.target
    }
    await nextTick()
    renderHourlyChart()
    renderStaffChart()
  } catch (e: any) {
    ElMessage.warning('运营数据拉取失败：' + (e?.message || ''))
  } finally {
    analyticsLoading.value = false
  }
}

const onUpdateDailyTarget = async (val: number | undefined) => {
  if (!val) return
  try {
    await kioskApi.updateDailyTarget({ target: val, date: analyticsDate.value })
    ElMessage.success(`已更新客流目标 → ${val}`)
  } catch (e: any) {
    ElMessage.warning(e?.message || '更新失败')
  }
}

// ⭐ 趋势 Dialog
const trendDialog   = ref(false)
const trendStaffId  = ref<number | null>(null)
const trendPeriod   = ref<'week' | 'month'>('week')
const trendEndDate  = ref<string>(new Date().toISOString().slice(0, 10))
const trendRows     = ref<any[]>([])
const trendLoading  = ref(false)
const compareMode   = ref(false)        // 多技师对比
const trendChartEl  = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null

const onShowTrend = (staffId: number) => {
  trendStaffId.value = staffId
  trendDialog.value = true
}

const loadStaffTrend = async () => {
  if (!trendStaffId.value) return
  trendLoading.value = true
  try {
    const staffIds = compareMode.value
      ? staffStats.value.slice(0, 8).map(s => s.staffId).join(',')
      : String(trendStaffId.value)
    const r: any = await kioskApi.staffTrend({
      staffIds,
      period:  trendPeriod.value,
      endDate: trendEndDate.value
    })
    trendRows.value = (r?.data?.rows) || []
    await nextTick()
    renderTrendChart()
  } catch (e: any) {
    ElMessage.warning('趋势拉取失败：' + (e?.message || ''))
  } finally {
    trendLoading.value = false
  }
}

const renderTrendChart = () => {
  if (!trendChartEl.value) return
  if (!trendChart) trendChart = echarts.init(trendChartEl.value)
  const rows = trendRows.value
  const dates = rows.map(r => r.date.slice(5))   // MM-DD
  const totals = rows.map(r => r.total)
  const finishes = rows.map(r => r.finished)
  const rates = rows.map(r => r.finishRate)

  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const lines = params.map(p => `${p.marker}${p.seriesName}：<b>${p.value}</b>`).join('<br/>')
        return `<b>${params[0].axisValue}</b><br/>${lines}`
      }
    },
    legend: { data: ['服务总数', '已完成', '完成率 (%)'], top: 0 },
    grid: { left: 50, right: 50, top: 36, bottom: 28, containLabel: true },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '服务数', position: 'left',  minInterval: 1 },
      { type: 'value', name: '完成率(%)', position: 'right', max: 100 }
    ],
    series: [
      { name: '服务总数', type: 'bar', data: totals,
        itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] }, barWidth: 14 },
      { name: '已完成',   type: 'bar', data: finishes,
        itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] }, barWidth: 14 },
      { name: '完成率 (%)', type: 'line', yAxisIndex: 1, smooth: true,
        data: rates, lineStyle: { color: '#e6a23c', width: 2 },
        itemStyle: { color: '#e6a23c' }, symbolSize: 7 }
    ]
  }, true)
}

const renderHourlyChart = () => {
  if (!hourlyChartEl.value) return
  if (!hourlyChart) hourlyChart = echarts.init(hourlyChartEl.value)
  const buckets = hourlyBuckets.value.length ? hourlyBuckets.value :
    Array.from({ length: 24 }, (_, h) => ({ hour: h, label: `${String(h).padStart(2, '0')}:00`, count: 0, finished: 0 }))
  const labels  = buckets.map((b: any) => b.label)
  const counts  = buckets.map((b: any) => b.count)
  const finish  = buckets.map((b: any) => b.finished)

  // 目标线：日均目标 / 24 小时均摊（per-hour 期望值）
  const perHourTarget = +(dailyTarget.value / 24).toFixed(2)
  hourlyChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const lines = params.map(p => `${p.marker}${p.seriesName}：<b>${p.value}</b>`).join('<br/>')
        return `<b>${params[0].axisValue}</b><br/>${lines}`
      }
    },
    legend: { data: ['签到数', '已完成', `目标均摊 ${perHourTarget}`], top: 0 },
    grid: { left: 36, right: 16, top: 36, bottom: 28, containLabel: true },
    xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 10, interval: 1 } },
    yAxis: { type: 'value', name: '人数', minInterval: 1 },
    series: [
      {
        name: '签到数', type: 'line', smooth: true,
        data: counts, lineStyle: { color: '#409eff', width: 2 },
        itemStyle: { color: '#409eff' },
        symbolSize: 6,
        areaStyle: { color: 'rgba(64, 158, 255, 0.15)' },
        markLine: {
          symbol: ['none', 'none'],
          data: [
            { yAxis: perHourTarget, name: '目标线',
              label: { formatter: `目标 ${perHourTarget}`, position: 'end', color: '#f56c6c' },
              lineStyle: { color: '#f56c6c', type: 'dashed', width: 2 } }
          ]
        }
      },
      {
        name: '已完成', type: 'bar',
        data: finish, barWidth: 12,
        itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] }
      }
    ]
  }, true)
}

const renderStaffChart = () => {
  if (!staffChartEl.value) return
  if (!staffChart) staffChart = echarts.init(staffChartEl.value)
  const list = staffStats.value.slice(0, 10)   // top 10
  const names = list.map((r: any) => `#${r.staffId}`)
  const totals = list.map((r: any) => r.total)
  const waitMs = list.map((r: any) => r.avgWaitMinutes)
  staffChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const idx = params[0].dataIndex
        const row = list[idx]
        return `<b>技师 #${row.staffId}</b><br/>
                服务总数：<b>${row.total}</b><br/>
                已完成：<b>${row.finished}</b><br/>
                完成率：<b>${row.finishRate}%</b><br/>
                平均等待：<b>${row.avgWaitMinutes} min</b>`
      }
    },
    legend: { data: ['服务总数', '平均等待 (min)'], top: 0 },
    grid: { left: 60, right: 50, top: 36, bottom: 28, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: names.reverse() },   // 反转让最高在上方
    series: [
      {
        name: '服务总数', type: 'bar',
        data: totals.reverse(),
        itemStyle: { color: '#409eff', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', fontSize: 11 }
      },
      {
        name: '平均等待 (min)', type: 'bar',
        data: waitMs.reverse(),
        itemStyle: { color: '#e6a23c', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', fontSize: 11 }
      }
    ]
  }, true)
}

const resizeAnalyticsCharts = () => {
  hourlyChart?.resize()
  staffChart?.resize()
  trendChart?.resize()
}

const disposeAnalyticsCharts = () => {
  hourlyChart?.dispose()
  staffChart?.dispose()
  trendChart?.dispose()
  hourlyChart = staffChart = trendChart = null
}

// 趋势 Dialog 打开时自动加载
watch(trendDialog, async (v) => {
  if (v) await loadStaffTrend()
})

watch(tab, async (t) => {
  if (t === 'analytics' && !hourlyBuckets.value.length) {
    await loadAnalytics()
  }
})

let offlineAlertTimer: number | null = null
const startOfflineAlertPolling = () => {
  loadOfflineAlerts()
  if (offlineAlertTimer) clearInterval(offlineAlertTimer)
  offlineAlertTimer = window.setInterval(loadOfflineAlerts, 30_000)
}
const stopOfflineAlertPolling = () => {
  if (offlineAlertTimer) { clearInterval(offlineAlertTimer); offlineAlertTimer = null }
}

// ====================================================================
// 生命周期
// ====================================================================
onMounted(async () => {
  await loadAll()
  startStatsPolling()
  startDevicePolling()
  startOfflineAlertPolling()

  // 暴露给 offline banner 的「立即探活」按钮用
  window.addEventListener('resize', resizeAnalyticsCharts)
})

onBeforeUnmount(() => {
  stopStatsPolling()
  stopDevicePolling()
  stopDemoHeartbeat()
  stopOfflineAlertPolling()
  disposeAnalyticsCharts()
  window.removeEventListener('resize', resizeAnalyticsCharts)
})
</script>

<style scoped>
.kiosk-page {
  padding: 16px;
}

.mb16 { margin-bottom: 16px; }

/* ============ KPI 卡 ============ */
.stat {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.stat-label {
  font-size: 13px;
  color: #909399;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  transition: all 0.3s;
}
.stat-value.accent { color: #409eff; }
.stat-value small {
  font-size: 13px;
  font-weight: 500;
  color: #909399;
  margin-left: 4px;
}
.stat-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.stat-tip {
  font-size: 11px;
  color: #909399;
}

/* 心跳点 */
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 3px;
}
.dot-on  { background: #67c23a; box-shadow: 0 0 4px rgba(103, 194, 58, 0.6); }
.dot-off { background: #f56c6c; box-shadow: 0 0 4px rgba(245, 108, 108, 0.6); }

/* 数据变化闪烁 */
.stat-blink .stat-value {
  animation: blink 0.6s ease;
}
@keyframes blink {
  0% { transform: scale(1); }
  50% { transform: scale(1.15); color: #409eff; }
  100% { transform: scale(1); }
}

/* ============ 工具栏 ============ */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.toolbar-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
.muted { color: #c0c4cc; font-size: 11px; }

.walkin-info {
  margin-top: 12px;
}

/* ============ 离线告警 banner ============ */
.offline-banner {
  border-radius: 8px;
}
.offline-banner :deep(.el-alert__title) {
  font-weight: 600;
}
.offline-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}
.offline-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.offline-name {
  font-weight: 500;
  color: #303133;
  min-width: 140px;
}
.offline-age {
  font-size: 12px;
}

/* ============ 趋势 Dialog ============ */
.trend-pill {
  font-size: 12px;
  margin-right: 6px;
}
</style>
