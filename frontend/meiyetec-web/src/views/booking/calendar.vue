<template>
  <div class="bc-page">
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><Calendar /></el-icon>
        <div>
          <h2>预约日历</h2>
          <p>按日期切换视图，绿色=已确认/已签到、橙色=待确认、灰色=已取消/已完成</p>
        </div>
      </div>
      <div class="right-actions">
        <el-radio-group v-model="viewMode" size="default">
          <el-radio-button value="today">当日</el-radio-button>
          <el-radio-button value="room">房间</el-radio-button>
          <el-radio-button value="week">本周</el-radio-button>
          <el-radio-button value="list">列表</el-radio-button>
        </el-radio-group>
        <el-button-group>
          <el-button type="warning" :icon="Printer" @click="printToday" plain>打印当日</el-button>
          <el-button type="success" :icon="Download" @click="exportCsv" plain>导出Excel</el-button>
        </el-button-group>
        <el-button type="primary" :icon="Plus" @click="openCreate()">新建预约</el-button>
      </div>
    </div>

    <!-- 当日视图 -->
    <el-card v-if="viewMode === 'today'" shadow="never">
      <div class="date-bar">
        <el-button :icon="ArrowLeft" circle @click="shiftDate(-1)" />
        <div class="date-main">
          <h3>{{ todayStr }}</h3>
          <small>{{ weekDayStr }}</small>
        </div>
        <el-button :icon="ArrowRight" circle @click="shiftDate(1)" />
        <el-button type="success" :icon="Calendar" @click="goToday">回到今天</el-button>
      </div>

      <!-- 统计卡片 -->
      <div class="stat-row">
        <div class="stat-card stat-total">
          <div class="stat-label">今日预约</div>
          <div class="stat-num">{{ stats.total }}</div>
          <small>全部</small>
        </div>
        <div class="stat-card stat-pending">
          <div class="stat-label">待确认</div>
          <div class="stat-num">{{ stats.pending }}</div>
          <small>需前台确认</small>
        </div>
        <div class="stat-card stat-confirmed">
          <div class="stat-label">已确认</div>
          <div class="stat-num">{{ stats.confirmed }}</div>
          <small>待到店</small>
        </div>
        <div class="stat-card stat-arrived">
          <div class="stat-label">在服务</div>
          <div class="stat-num">{{ stats.arrived }}</div>
          <small>已签到</small>
        </div>
        <div class="stat-card stat-done">
          <div class="stat-label">已完成</div>
          <div class="stat-num">{{ stats.done }}</div>
          <small>已结单</small>
        </div>
        <div class="stat-card stat-miss">
          <div class="stat-label">爽约 / 取消</div>
          <div class="stat-num">{{ stats.miss + stats.cancel }}</div>
          <small>关注</small>
        </div>
        <div class="stat-card stat-conflict" v-if="conflicts.length > 0">
          <div class="stat-label">房间冲突</div>
          <div class="stat-num">{{ conflicts.length }}</div>
          <small>⚠️需处理</small>
        </div>
      </div>

      <!-- 颜色图例 -->
      <div class="legend-bar">
        <span class="lg-item"><span class="lg-dot" style="background:#f59e0b"></span>待确认</span>
        <span class="lg-item"><span class="lg-dot" style="background:#3b82f6"></span>已确认</span>
        <span class="lg-item"><span class="lg-dot" style="background:#10b981"></span>已签到（在服务）</span>
        <span class="lg-item"><span class="lg-dot" style="background:#6b7280"></span>已完成</span>
        <span class="lg-item"><span class="lg-dot" style="background:#ef4444"></span>取消/爽约</span>
        <span class="lg-line"></span>
        <span class="lg-item"><span class="lg-dot" style="background:#dc2626; border-radius: 50%; width: 10px; height: 10px;"></span>当前时间</span>
        <span class="lg-spacer"></span>
        <span class="lg-now">⏰ 当前：<strong>{{ nowClock }}</strong> · {{ stats.total }} 条预约</span>
      </div>

      <el-table :data="timelineRows" border :row-class-name="hourRowClass">
        <el-table-column label="时间" width="120" align="center" fixed>
          <template #default="{ row }">
            <strong>{{ String(row.hour).padStart(2,'0') }}:00</strong>
            <div style="font-size: 10px; color: #c0c4cc;">{{ String(row.hour).padStart(2,'0') }}:30</div>
          </template>
        </el-table-column>
        <el-table-column v-for="emp in employees" :key="emp.id" :label="emp.employeeName" min-width="160" align="center">
          <template #default="{ row }">
            <div class="emp-cell"
                 :class="{ off: !row.visibleEmps?.find((e: any) => e.id === emp.id) }">
              <div v-if="row.visibleEmps?.find((e: any) => e.id === emp.id)">
                <div v-for="b in row.bookings?.filter((b: any) => b.employeeId === emp.id)"
                     :key="b.id" class="bk"
                     :class="statusClass(b.status)"
                     :style="bookingStyle(b)"
                     draggable="true"
                     @click="showDetail(b)"
                     @dragstart="onDragStart($event, b)"
                     @dragend="onDragEnd"
                     @dragover.prevent
                     @drop="onDrop($event, emp, row.hour)"
                     @contextmenu.prevent="openStatusMenu($event, b)">
                  <strong>{{ b.startTime?.slice(11,16) }}~{{ b.endTime?.slice(11,16) }}</strong>
                  <div>{{ b.serviceItem }}</div>
                  <small>{{ b.memberName }}</small>
                  <!-- 快捷操作按钮 -->
                  <div class="bk-quick" @click.stop>
                    <el-button v-if="b.status === 1" size="small" type="primary" plain @click.stop="quickAct(b, 2)">确认</el-button>
                    <el-button v-if="b.status === 2" size="small" type="success" plain @click.stop="quickAct(b, 3)">签到</el-button>
                    <el-button v-if="b.status === 3" size="small" type="info" plain @click.stop="quickAct(b, 4)">完成</el-button>
                  </div>
                </div>
                <div v-if="!row.bookings?.filter((b: any) => b.employeeId === emp.id).length"
                     class="empty" @click="quickCreate(emp, row.hour)">+空闲</div>
              </div>
              <div v-else class="off">休息</div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 房间维度视图 -->
    <el-card v-else-if="viewMode === 'room'" shadow="never">
      <div class="date-bar">
        <el-button :icon="ArrowLeft" circle @click="shiftDate(-1)" />
        <div class="date-main">
          <h3>{{ todayStr }} · 房间维度</h3>
          <small>{{ weekDayStr }} · 横向看房间占用</small>
        </div>
        <el-button :icon="ArrowRight" circle @click="shiftDate(1)" />
        <el-button type="success" :icon="Calendar" @click="goToday">回到今天</el-button>
      </div>

      <!-- 冲突提示横幅 -->
      <el-alert v-if="conflicts.length > 0"
        :title="`检测到 ${conflicts.length} 组房间冲突`"
        type="error" show-icon :closable="false" class="conflict-alert">
        <template #default>
          <ul class="conflict-list">
            <li v-for="(c, idx) in conflicts" :key="idx">
              <strong>{{ c.roomName }}</strong> ·
              {{ c.startTime?.slice(11, 16) }} ~ {{ c.endTime?.slice(11, 16) }}
              有 {{ c.bookings.length }} 条预约重叠
              <span v-for="(b, bi) in c.bookings" :key="bi" class="conflict-tag">
                {{ b.memberName }}/{{ b.serviceItem }} [{{ statusLabel(b.status) }}]
              </span>
            </li>
          </ul>
        </template>
      </el-alert>

      <el-table :data="roomTimelineRows" border>
        <el-table-column label="时间" width="120" align="center" fixed>
          <template #default="{ row }">
            <strong>{{ row.hour }}:00</strong>
          </template>
        </el-table-column>
        <el-table-column v-for="room in rooms" :key="room.id" :label="`${room.roomName} (${room.roomType})`" min-width="160" align="center">
          <template #default="{ row }">
            <div class="emp-cell">
              <div v-for="b in row.bookings?.filter((b: any) => b.roomId === room.id)"
                   :key="b.id" class="bk"
                   :style="bookingStyle(b)"
                   @click="showDetail(b)">
                <strong>{{ b.serviceItem }}</strong>
                <small>{{ b.memberName }} · {{ b.employeeName }}</small>
              </div>
              <div v-if="!row.bookings?.filter((b: any) => b.roomId === room.id).length"
                   class="empty" @click="quickCreateRoom(room, row.hour)">+空闲</div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 本周视图 -->
    <el-card v-else-if="viewMode === 'week'" shadow="never">
      <div class="date-bar">
        <el-button :icon="ArrowLeft" circle @click="shiftDate(-7)" />
        <strong>本周 {{ weekRangeStr }}</strong>
        <el-button :icon="ArrowRight" circle @click="shiftDate(7)" />
      </div>
      <el-table border :data="weekGrid">
        <el-table-column label="技师" width="120" align="center" fixed>
          <template #default="{ row }">
            <strong>{{ row.employeeName }}</strong>
          </template>
        </el-table-column>
        <el-table-column v-for="d in weekDayList" :key="d.date" :label="d.label" align="center" min-width="120">
          <template #default="{ row }">
            <div v-for="b in (row.bookings[d.date] || [])" :key="b.id" class="b-pill"
                 :class="statusClass(b.status)"
                 @click="showDetail(b)">
              {{ b.startTime?.slice(11, 16) }} {{ b.memberName }}
            </div>
            <div v-if="!(row.bookings[d.date] || []).length" class="empty-day">—</div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 列表视图 -->
    <booking-list v-else />

    <!-- 新建/编辑 弹窗 -->
    <booking-form v-model:visible="formVisible" :editing="editing"
                  @saved="onSaved" />

    <!-- 右键状态菜单 -->
    <div v-if="statusMenu.visible" class="status-menu"
      :style="{ left: statusMenu.x + 'px', top: statusMenu.y + 'px' }"
      @click.stop
      v-click-outside="closeStatusMenu">
      <div class="menu-title">变更状态</div>
      <el-button size="small" type="warning" @click="applyStatus(1)">待确认</el-button>
      <el-button size="small" type="primary" @click="applyStatus(2)">已确认</el-button>
      <el-button size="small" type="success" @click="applyStatus(3)">已签到</el-button>
      <el-button size="small" type="info" @click="applyStatus(4)">已完成</el-button>
      <el-button size="small" type="danger" @click="applyStatus(5)">取消</el-button>
      <el-button size="small" type="warning" @click="applyStatus(6)">爽约</el-button>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="`预约详情 ${detail?.bookingNo || ''}`" width="600">
      <el-descriptions v-if="detail" :column="2" border size="default">
        <el-descriptions-item label="会员">
          <strong>{{ detail.memberName }}</strong> <small style="color:#909399">{{ detail.memberPhone }}</small>
        </el-descriptions-item>
        <el-descriptions-item label="技师">{{ detail.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">
          <el-tag size="small">{{ detail.serviceItem }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="房间">{{ detail.roomName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.bookingDate }} {{ weekDayStr }}</el-descriptions-item>
        <el-descriptions-item label="时段">
          <strong style="color:#3b82f6">{{ detail.startTime?.slice(11, 16) }} ~ {{ detail.endTime?.slice(11, 16) }}</strong>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="statusTagType(detail.status)" effect="dark">{{ statusLabel(detail.status) }}</el-tag>
          <small style="color:#909399; margin-left: 8px">下单时间：{{ detail.createTime?.slice(0, 19) }}</small>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.checkinTime" label="签到时间">{{ detail.checkinTime?.slice(0, 19) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.completeTime" label="完成时间">{{ detail.completeTime?.slice(0, 19) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.cancelReason" label="取消原因">{{ detail.cancelReason }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button v-if="detail?.status === 1" type="primary" @click="act(detail, 2)">确认预约</el-button>
        <el-button v-if="detail?.status === 2" type="success" @click="checkin(detail)">签到</el-button>
        <el-button v-if="detail?.status === 3" type="warning" @click="complete(detail)">完成服务</el-button>
        <el-button v-if="[1,2].includes(detail?.status)" type="danger" @click="cancel(detail)">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, ArrowLeft, ArrowRight, Plus, Printer, Download } from '@element-plus/icons-vue'
import { bookingApi } from '@/api/booking'
import BookingForm from './booking-form.vue'
import BookingList from './list.vue'

const viewMode = ref<'today' | 'week' | 'list' | 'room'>('today')
const today = ref(new Date())
const nowClock = ref('')
const currentHour = ref(-1)
let timer: any = null
const updateClock = () => {
  const d = new Date()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  nowClock.value = `${hh}:${mm}:${ss}`
  currentHour.value = d.getHours()
}
const bookings = ref<any[]>([])
const employees = ref<any[]>([])
const rooms = ref<any[]>([])
const schedules = ref<any[]>([])
const detailVisible = ref(false)
const detail = ref<any>(null)
const formVisible = ref(false)
const editing = ref<any>(null)

const todayStr = computed(() => today.value.toISOString().slice(0, 10))
const weekDayStr = computed(() => '星期' + ['日','一','二','三','四','五','六'][today.value.getDay()])

const shiftDate = (delta: number) => {
  const d = new Date(today.value)
  d.setDate(d.getDate() + delta)
  today.value = d
}
const goToday = () => today.value = new Date()

const weekDayList = computed(() => {
  const d = new Date(today.value)
  const dow = d.getDay() || 7
  d.setDate(d.getDate() - dow + 1)
  const days: any[] = []
  for (let i = 0; i < 7; i++) {
    const dd = new Date(d); dd.setDate(dd.getDate() + i)
    days.push({ date: dd.toISOString().slice(0, 10), label: `${dd.getMonth() + 1}/${dd.getDate()} (${'日一二三四五六'[dd.getDay()]})` })
  }
  return days
})
const weekRangeStr = computed(() => {
  const arr = weekDayList.value
  return `${arr[0].date} ~ ${arr[6].date}`
})

// 技师 × 7天 二维网格
const weekGrid = computed(() => {
  const days = weekDayList.value
  const startDate = days[0].date
  const endDate = days[6].date
  // 只看当前周 + 未完成 + 7 位技师
  const weekBookings = bookings.value.filter(b =>
    b.bookingDate >= startDate &&
    b.bookingDate <= endDate &&
    [1, 2, 3, 6].includes(b.status)
  )
  return employees.value.map(emp => {
    const row: any = {
      employeeId: emp.id,
      employeeName: emp.employeeName,
      bookings: {} as Record<string, any[]>
    }
    days.forEach(d => row.bookings[d.date] = [])
    weekBookings
      .filter(b => b.employeeId === emp.id)
      .forEach(b => {
        if (row.bookings[b.bookingDate]) row.bookings[b.bookingDate].push(b)
      })
    // 每格按时间排序
    Object.keys(row.bookings).forEach(k => row.bookings[k].sort((a, b) => a.startTime.localeCompare(b.startTime)))
    return row
  })
})

const timelineRows = computed(() => {
  // 只看"今天"的预约 + 按技师 ID 索引 + 排除已取消/已完成的
  const todayBookings = bookings.value.filter(b =>
    b.bookingDate === todayStr.value &&
    [1, 2, 3, 6].includes(b.status)  // 1待确认 2已确认 3已签到 6爽约
  )
  // 时间槽：基于排班表（无排班时用默认 9-18）
  const hourSet = new Set<number>()
  const daySchedules = schedules.value.filter(s => s.workDate === todayStr.value)
  if (daySchedules.length > 0) {
    daySchedules.forEach(s => {
      const start = parseInt(s.startTime?.slice(0, 2) || '9')
      const end   = parseInt(s.endTime?.slice(0, 2) || '18')
      for (let h = start; h < end; h++) hourSet.add(h)
    })
  } else {
    for (let h = 9; h < 18; h++) hourSet.add(h)
  }
  const hours = Array.from(hourSet).sort((a, b) => a - b)
  // 当日有排班的技师（决定要显示几列）
  const dayEmpIds = new Set(daySchedules.map(s => s.employeeId))
  const visibleEmps = employees.value.filter(e => dayEmpIds.has(e.id))
  return hours.map(h => ({
    hour: h,
    visibleEmps,
    bookings: todayBookings.filter(b => new Date(b.startTime).getHours() === h)
  }))
})

const statusClass = (s: number) => ({
  '1': 'pending', '2': 'confirmed', '3': 'arrived', '4': 'done', '5': 'cancel', '6': 'miss'
}[s] || '')
const hourRowClass = ({ row }: any) => {
  if (row.hour === currentHour.value && todayStr.value === new Date().toISOString().slice(0, 10)) return 'row-current-hour'
  return ''
}
const statusLabel = (s: number) => ({
  '1': '待确认', '2': '已确认', '3': '已签到', '4': '已完成', '5': '已取消', '6': '爽约'
}[s] || '未知')
const statusTagType = (s: number) => ({
  '1': 'warning', '2': 'primary', '3': 'success', '4': 'info', '5': 'danger', '6': 'danger'
}[s] || '')
const bookingStyle = (b: any) => ({
  background: b.status === 1 ? '#fef3c7' : b.status === 2 ? '#dbeafe' : b.status === 3 ? '#dcfce7' : '#f3f4f6',
  color: b.status === 1 ? '#92400e' : b.status === 2 ? '#1e3a8a' : b.status === 3 ? '#14532d' : '#374151'
})

const showDetail = (b: any) => {
  detail.value = b
  detailVisible.value = true
}

const openCreate = (preset?: any) => {
  // P2-13：30 分钟精度，取最近的下个整点或半点
  const now = new Date()
  let h = now.getHours()
  let m = now.getMinutes() >= 30 ? 30 : 0
  if (preset?.startTime) {
    // 有预设时间时使用预设的分钟（但确保 30-min 对齐）
    const dt = new Date(preset.startTime)
    h = dt.getHours()
    m = dt.getMinutes()
    m = m % 30 === 0 ? m : (m < 30 ? 0 : 30)
  }
  editing.value = {
    ...preset,
    bookingDate: todayStr.value,
    startTime: preset?.startTime || `${todayStr.value}T${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:00`,
    endTime: preset?.endTime || `${todayStr.value}T${String(m === 30 ? h + 1 : h).padStart(2,'0')}:${String(m === 30 ? 0 : m).padStart(2,'0')}:00`
  }
  formVisible.value = true
}

// ===== 打印当日预约表 =====
const printToday = () => {
  const todayB = bookings.value
    .filter(b => b.bookingDate === todayStr.value)
    .sort((a, b) => (a.startTime || '').localeCompare(b.startTime || ''))

  const statusMap = ['', '待确认', '已确认', '已签到', '已完成', '已取消', '爽约']

  const rows = todayB.map((b, i) => `
    <tr>
      <td>${i + 1}</td>
      <td>${b.startTime?.slice(11, 16) || ''} ~ ${b.endTime?.slice(11, 16) || ''}</td>
      <td>${b.memberName || ''}<br/><small>${b.memberPhone || ''}</small></td>
      <td>${b.employeeName || ''}</td>
      <td>${b.serviceItem || ''}</td>
      <td>${b.roomName || ''}</td>
      <td>${statusMap[b.status] || ''}</td>
      <td>${b.remark || ''}</td>
    </tr>
  `).join('')

  const html = `
  <!DOCTYPE html>
  <html><head><meta charset="utf-8"><title>预约日报 - ${todayStr.value}</title>
  <style>
    body { font-family: "Microsoft YaHei", sans-serif; padding: 20px; }
    h1 { text-align: center; font-size: 22px; margin: 0 0 8px; }
    .meta { text-align: center; color: #666; margin-bottom: 16px; font-size: 13px; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th, td { border: 1px solid #333; padding: 6px 8px; text-align: center; }
    th { background: #f0f0f0; }
    td small { color: #888; font-size: 11px; }
    .stat { margin-bottom: 16px; font-size: 14px; }
    .stat span { margin-right: 16px; padding: 2px 8px; background: #f5f5f5; border-radius: 4px; }
    @media print { .no-print { display: none; } }
  </style></head>
  <body>
    <h1>💆 美业管理 · 预约日报</h1>
    <div class="meta">
      日期：<strong>${todayStr.value}</strong> (${weekDayStr.value})
      ｜ 门店：默认门店 ｜ 打印时间：${new Date().toLocaleString('zh-CN')}
    </div>
    <div class="stat">
      <span>共 ${todayB.length} 条</span>
      <span>待确认 ${todayB.filter(b => b.status === 1).length}</span>
      <span>已确认 ${todayB.filter(b => b.status === 2).length}</span>
      <span>已签到 ${todayB.filter(b => b.status === 3).length}</span>
      <span>已完成 ${todayB.filter(b => b.status === 4).length}</span>
      <span>已取消 ${todayB.filter(b => b.status === 5).length}</span>
      <span>爽约 ${todayB.filter(b => b.status === 6).length}</span>
    </div>
    <table>
      <thead>
        <tr><th>#</th><th>时段</th><th>会员</th><th>技师</th><th>服务项目</th><th>房间</th><th>状态</th><th>备注</th></tr>
      </thead>
      <tbody>${rows || '<tr><td colspan="8" style="color:#999">当天无预约</td></tr>'}</tbody>
    </table>
    <div class="no-print" style="text-align:center; margin-top: 20px;">
      <button onclick="window.print()" style="padding: 8px 24px; font-size: 14px;">🖨️ 立即打印</button>
      <button onclick="window.close()" style="padding: 8px 24px; font-size: 14px; margin-left: 8px;">关闭</button>
    </div>
  </body></html>`

  const w = window.open('', '_blank', 'width=900,height=700')
  if (!w) {
    ElMessage.error('请允许浏览器弹窗')
    return
  }
  w.document.write(html)
  w.document.close()
}

// ===== 导出 CSV =====
const exportCsv = () => {
  const token = localStorage.getItem('token') || ''
  // 选默认范围：本周
  const arr = weekDayList.value
  const url = `/api/booking/export?startDate=${arr[0].date}&endDate=${arr[6].date}&token=${encodeURIComponent(token)}`
  // 后端过滤器基于 Spring Security JWT；这里用 token 走 axios 时会带上，但 a 标签下载不带 header
  // 改用 fetch + blob
  fetch(url, { headers: { 'Authorization': token } })
    .then(r => r.blob())
    .then(blob => {
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `预约列表_${todayStr.value}.csv`
      a.click()
      URL.revokeObjectURL(a.href)
      ElMessage.success('已导出 CSV')
    })
    .catch(err => ElMessage.error('导出失败：' + err.message))
}
const quickCreate = (e: any, hour?: number) => {
  // P2-13：30 分钟精度，取最近的下个整点或半点
  const now = new Date()
  let h = hour ?? now.getHours()
  let m = now.getMinutes() >= 30 ? 30 : 0
  if (hour !== undefined) m = 0 // 有指定 hour 时从整点开始
  editing.value = {
    employeeId: e.id,
    employeeName: e.employeeName,
    bookingDate: todayStr.value,
    startTime: `${todayStr.value}T${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:00`,
    endTime:   `${todayStr.value}T${String(m === 30 ? h + 1 : h).padStart(2,'0')}:00:00`
  }
  formVisible.value = true
}

const act = async (b: any, s: number) => {
  try { await ElMessageBox.confirm(`确定要变更状态为「${statusLabel(s)}」？`, '确认') } catch { return }
  const r = await bookingApi.changeStatus(b.id, s)
  ElMessage.success(r.message)
  detailVisible.value = false
  load()
}
const checkin = async (b: any) => { const r = await bookingApi.checkin(b.id); ElMessage.success(r.message); detailVisible.value = false; load() }
const complete = async (b: any) => { const r = await bookingApi.complete(b.id); ElMessage.success(r.message); detailVisible.value = false; load() }
const cancel = async (b: any) => {
  try { await ElMessageBox.confirm('确定取消？', '确认', { type: 'warning' }) } catch { return }
  const r = await bookingApi.changeStatus(b.id, 5, '用户取消')
  ElMessage.success(r.message); detailVisible.value = false; load()
}

// ===== 拖拽改技师 =====
let dragBooking: any = null
function onDragStart(e: DragEvent, b: any) {
  dragBooking = b
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(b.id))
  }
}
function onDragEnd() { dragBooking = null }
async function onDrop(_e: DragEvent, emp: any, _hour: number) {
  if (!dragBooking) return
  const bid = dragBooking.id
  const newEmpId = emp.id
  const newEmpName = emp.employeeName
  if (dragBooking.employeeId === newEmpId) { dragBooking = null; return }
  try {
    await bookingApi.assign(bid, newEmpId)
    dragBooking.employeeId = newEmpId
    dragBooking.employeeName = newEmpName
    ElMessage.success(`已转给「${newEmpName}」`)
  } catch (e: any) {
    ElMessage.error(e?.message || '转派失败')
  }
  dragBooking = null
}

// ===== 快捷状态变更 =====
async function quickAct(b: any, newStatus: number) {
  try {
    if (newStatus === 2) {
      await bookingApi.changeStatus(b.id, 2)
      b.status = 2
      ElMessage.success('已确认')
    } else if (newStatus === 3) {
      await bookingApi.checkin(b.id)
      b.status = 3
      ElMessage.success('已签到')
    } else if (newStatus === 4) {
      await bookingApi.complete(b.id)
      b.status = 4
      ElMessage.success('已完成')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ===== 右键状态菜单（桌面端）=====
const statusMenu = reactive({ visible: false, x: 0, y: 0, booking: null as any })
function openStatusMenu(e: MouseEvent, b: any) {
  statusMenu.booking = b
  statusMenu.x = e.clientX
  statusMenu.y = e.clientY
  statusMenu.visible = true
}
function closeStatusMenu() { statusMenu.visible = false }
async function applyStatus(s: number) {
  if (!statusMenu.booking) return
  await quickAct(statusMenu.booking, s)
  closeStatusMenu()
}

const load = async () => {
  bookings.value = (await bookingApi.list()).data
}
const loadEmployees = async () => {
  const emp = await import('@/api/basic').then(m => m.employeeApi.list())
  employees.value = emp.data
}
const loadSchedules = async () => {
  const r = await import('@/api/booking').then(m => m.scheduleApi.list({
    startDate: todayStr.value,
    endDate: todayStr.value
  }))
  schedules.value = r.data || []
}
const loadRooms = async () => {
  const r = await import('@/api/booking').then(m => m.roomApi.list())
  rooms.value = r.data || []
}

// ===== 统计卡片 =====
const stats = computed(() => {
  const todayB = bookings.value.filter(b => b.bookingDate === todayStr.value)
  return {
    total: todayB.length,
    pending: todayB.filter(b => b.status === 1).length,
    confirmed: todayB.filter(b => b.status === 2).length,
    arrived: todayB.filter(b => b.status === 3).length,
    done: todayB.filter(b => b.status === 4).length,
    miss: todayB.filter(b => b.status === 6).length,
    cancel: todayB.filter(b => b.status === 5).length
  }
})

// ===== 房间冲突检测 =====
const conflicts = computed(() => {
  const todayB = bookings.value.filter(b =>
    b.bookingDate === todayStr.value &&
    [1, 2, 3].includes(b.status) &&  // 未完成
    b.roomId
  )
  // 按 roomId 分组
  const map = new Map<number, any[]>()
  todayB.forEach(b => {
    if (!map.has(b.roomId)) map.set(b.roomId, [])
    map.get(b.roomId)!.push(b)
  })
  const result: any[] = []
  map.forEach((list, roomId) => {
    if (list.length < 2) return
    // 按开始时间排序
    list.sort((a, b) => a.startTime.localeCompare(b.startTime))
    // 暴力两两比：O(n²) n 一般很小
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        const a = list[i], b = list[j]
        if (a.startTime < b.endTime && b.startTime < a.endTime) {
          // 冲突！把这两条合并到一条冲突记录
          let entry = result.find(r => r.roomId === roomId && r.startTime === a.startTime)
          if (!entry) {
            entry = {
              roomId, roomName: a.roomName,
              startTime: a.startTime, endTime: a.endTime,
              bookings: [a]
            }
            result.push(entry)
          }
          if (!entry.bookings.find((x: any) => x.id === b.id)) entry.bookings.push(b)
        }
      }
    }
  })
  return result
})

// ===== 房间维度时间轴 =====
const roomTimelineRows = computed(() => {
  const todayB = bookings.value.filter(b =>
    b.bookingDate === todayStr.value &&
    [1, 2, 3, 6].includes(b.status)
  )
  const hourSet = new Set<number>()
  todayB.forEach(b => {
    const h = new Date(b.startTime).getHours()
    const he = new Date(b.endTime).getHours()
    for (let x = h; x < he; x++) hourSet.add(x)
  })
  if (hourSet.size === 0) for (let h = 9; h < 18; h++) hourSet.add(h)
  const hours = Array.from(hourSet).sort((a, b) => a - b)
  return hours.map(h => ({ hour: h, bookings: todayB.filter(b => new Date(b.startTime).getHours() === h) }))
})

const quickCreateRoom = (room: any, hour?: number) => {
  // P2-13：30 分钟精度，取最近的下个整点或半点
  const now = new Date()
  let h = hour ?? now.getHours()
  let m = now.getMinutes() >= 30 ? 30 : 0
  if (hour !== undefined) m = 0 // 有指定 hour 时从整点开始
  editing.value = {
    roomId: room.id,
    roomName: room.roomName,
    bookingDate: todayStr.value,
    startTime: `${todayStr.value}T${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:00`,
    endTime:   `${todayStr.value}T${String(m === 30 ? h + 1 : h).padStart(2,'0')}:00:00`
  }
  formVisible.value = true
}
const onSaved = () => { formVisible.value = false; load() }

watch(today, () => { load(); loadSchedules() })
onMounted(() => {
  load(); loadEmployees(); loadSchedules(); loadRooms()
  updateClock()
  timer = setInterval(updateClock, 1000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.bc-page { padding: 16px; }
.banner {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff; padding: 24px; border-radius: 10px;
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.banner h2 { margin: 0; font-size: 22px; }
.banner p { margin: 4px 0 0; opacity: 0.85; font-size: 13px; }
.right-actions { display: flex; gap: 8px; align-items: center; }
.date-bar { display: flex; align-items: center; gap: 12px; padding: 8px 0 16px; }
.date-main { text-align: center; min-width: 220px; }
.date-main h3 { margin: 0; font-size: 20px; }
.emp-cells { display: flex; gap: 8px; flex-wrap: wrap; }
.emp-cell { min-height: 60px; border: 1px dashed #c0c4cc; border-radius: 6px; padding: 4px; }
.emp-cell.off { background: #f5f7fa; border-style: solid; border-color: #e4e7ed; }
.emp-cell .off { color: #c0c4cc; font-size: 12px; padding: 16px 0; }
.bk { cursor: pointer; padding: 6px; border-radius: 4px; position: relative; }
.bk small { display: block; opacity: 0.85; }
.bk:hover .bk-quick { display: flex; }
.bk-quick {
  display: none;
  position: absolute;
  bottom: 2px;
  right: 2px;
  gap: 2px;
}
.bk-quick .el-button { padding: 2px 6px; font-size: 11px; min-height: 20px; line-height: 1; }

.empty { color: #909399; text-align: center; cursor: pointer; padding: 12px 0; }
.empty:hover { color: #6366f1; }

/* 右键菜单 */
.status-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
}
.menu-title {
  font-size: 11px;
  color: #909399;
  padding: 2px 4px 6px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 4px;
}
.status-menu .el-button { width: 100%; text-align: left; }

/* 统计卡片 */
.stat-row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 130px; padding: 14px 16px;
  border-radius: 8px; color: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}
.stat-label { font-size: 13px; opacity: 0.9; }
.stat-num   { font-size: 28px; font-weight: bold; margin: 4px 0; line-height: 1.2; }
.stat-card small { font-size: 12px; opacity: 0.8; }
.stat-total     { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.stat-pending   { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
.stat-confirmed { background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%); }
.stat-arrived   { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.stat-done      { background: linear-gradient(135deg, #6b7280 0%, #374151 100%); }
.stat-miss      { background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); }
.stat-conflict  { background: linear-gradient(135deg, #dc2626 0%, #7f1d1d 100%); }

/* 冲突提示 */
.conflict-alert { margin-bottom: 16px; }
.conflict-alert ul { margin: 0; padding-left: 0; list-style: none; }
.conflict-alert li { padding: 6px 0; border-bottom: 1px dashed #fecaca; }
.conflict-alert li:last-child { border-bottom: none; }
.conflict-tag {
  display: inline-block; margin-left: 6px;
  padding: 2px 6px; background: #fee2e2; color: #b91c1c;
  border-radius: 3px; font-size: 12px;
}
.b-pill { padding: 4px 8px; margin: 2px 0; border-radius: 12px; font-size: 12px; cursor: pointer; }
.b-pill.pending  { background: #fef3c7; color: #92400e; }
.b-pill.confirmed{ background: #dbeafe; color: #1e3a8a; }
.b-pill.arrived  { background: #dcfce7; color: #14532d; }
.b-pill.done     { background: #f3f4f6; color: #374151; }
.b-pill.cancel   { background: #fee2e2; color: #991b1b; }
.b-pill.miss     { background: #fde68a; color: #92400e; }
.empty-day { color: #c0c4cc; font-size: 12px; text-align: center; }

/* 颜色图例 */
.legend-bar {
  display: flex; align-items: center; gap: 16px;
  padding: 10px 14px; margin-bottom: 12px;
  background: #f9fafb; border-radius: 6px;
  border: 1px solid #f3f4f6; font-size: 12px;
}
.lg-item { display: inline-flex; align-items: center; gap: 6px; color: #4b5563; }
.lg-dot { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.lg-line { width: 1px; height: 14px; background: #e5e7eb; }
.lg-spacer { flex: 1; }
.lg-now { font-size: 13px; color: #6b7280; }
.lg-now strong { color: #dc2626; font-family: monospace; font-size: 14px; }

/* 当前时间行高亮 */
:deep(.row-current-hour) td {
  background: linear-gradient(90deg, #fef2f2 0%, transparent 100%) !important;
  border-left: 3px solid #dc2626 !important;
}
:deep(.row-current-hour) td:first-child {
  font-weight: bold; color: #dc2626;
}

/* 进行中动画 */
:deep(.bk.arrived) {
  position: relative;
  animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
}
</style>