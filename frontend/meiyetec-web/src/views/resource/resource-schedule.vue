<template>
  <div class="resource-schedule-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="排程甘特图" name="gantt" />
      <el-tab-pane label="智能推荐" name="suggest" />
      <el-tab-pane label="冲突记录" name="conflict" />
      <el-tab-pane label="利用率统计" name="utilization" />
    </el-tabs>

    <!-- ============ 排程甘特图 ============ -->
    <div v-if="activeTab === 'gantt'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>房间+技师双资源排程 - {{ filterDate }}</span>
            <div class="filter-form">
              <el-select v-model="viewType" style="width:120px" @change="handleViewTypeChange">
                <el-option label="房间视图" value="room" />
                <el-option label="技师视图" value="technician" />
              </el-select>
              <el-date-picker v-model="filterDate" type="date" value-format="YYYY-MM-DD" @change="loadBookings" />
              <el-button type="primary" @click="handleNewSchedule">
                <el-icon><Plus /></el-icon>新增排程
              </el-button>
            </div>
          </div>
        </template>

        <div class="gantt-wrapper">
          <table class="gantt-table">
            <thead>
              <tr>
                <th class="resource-cell">{{ viewType === 'room' ? '房间' : '技师' }}</th>
                <th v-for="h in hours" :key="h" class="hour-cell">
                  <div class="hour-label">{{ h }}:00</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in resources" :key="r.id">
                <td class="resource-cell">
                  <div class="resource-info">
                    <el-icon v-if="viewType === 'room'"><OfficeBuilding /></el-icon>
                    <el-icon v-else><User /></el-icon>
                    <span>{{ r.name }}</span>
                  </div>
                </td>
                <td v-for="h in hours" :key="h" class="time-cell">
                  <div
                    v-for="b in getBookingsAt(r.id, h)"
                    :key="b.id"
                    class="booking-block"
                    :class="getBookingClass(b)"
                    @click="handleBookingClick(b)"
                  >
                    <div class="block-title">{{ b.memberName }}</div>
                    <div class="block-time">{{ formatTime(b.startTime) }} - {{ formatTime(b.endTime) }}</div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </el-card>
    </div>

    <!-- ============ 智能推荐 ============ -->
    <div v-if="activeTab === 'suggest'">
      <el-card>
        <template #header>
          <span>智能排程推荐</span>
        </template>
        <el-form :model="suggestForm" label-width="100px" style="max-width:600px">
          <el-form-item label="服务">
            <el-select v-model="suggestForm.serviceId" placeholder="选择服务" style="width:100%">
              <el-option v-for="s in serviceList" :key="s.id" :label="s.serviceName" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="开始时间">
            <el-date-picker v-model="suggestForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
          </el-form-item>
          <el-form-item label="时长">
            <el-input-number v-model="suggestForm.durationMinutes" :min="15" :step="15" /> 分钟
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadSuggestions" :loading="suggestLoading">
              <el-icon><Search /></el-icon>生成推荐
            </el-button>
          </el-form-item>
        </el-form>

        <el-divider v-if="suggestions.length">推荐结果(Top 10)</el-divider>

        <el-row :gutter="12" v-if="suggestions.length">
          <el-col v-for="(s, idx) in suggestions" :key="idx" :xs="24" :sm="12" :md="8" :lg="6">
            <div class="suggestion-card">
              <div class="suggestion-rank" :class="idx < 3 ? 'top' : ''">#{{ idx + 1 }}</div>
              <div class="suggestion-score">{{ s.score }}分</div>
              <div class="suggestion-detail">
                <div class="detail-row">
                  <el-icon><OfficeBuilding /></el-icon>
                  <span>{{ s.roomName }}</span>
                </div>
                <div class="detail-row">
                  <el-icon><User /></el-icon>
                  <span>{{ s.primaryEmployeeName || '未指定' }}</span>
                </div>
                <div v-if="s.assistantEmployeeName" class="detail-row">
                  <el-icon><UserFilled /></el-icon>
                  <span>{{ s.assistantEmployeeName }}</span>
                </div>
                <div class="reason">{{ s.reason }}</div>
              </div>
              <el-button type="primary" size="small" @click="handleUseSuggestion(s)">使用此方案</el-button>
            </div>
          </el-col>
        </el-row>
        <el-empty v-if="!suggestLoading && !suggestions.length && suggestLoaded" description="暂无可用推荐" />
      </el-card>
    </div>

    <!-- ============ 冲突记录 ============ -->
    <div v-if="activeTab === 'conflict'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>资源冲突记录</span>
            <el-select v-model="conflictTypeFilter" placeholder="冲突类型" clearable style="width:140px" @change="loadConflicts">
              <el-option v-for="o in conflictTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </div>
        </template>
        <el-table :data="conflictList" border stripe v-loading="conflictLoading">
          <el-table-column label="冲突时间" prop="detectTime" width="160" />
          <el-table-column label="冲突类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getConflictType(row.conflictType).type">
                {{ getConflictType(row.conflictType).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="冲突资源" prop="resourceName" width="160" />
          <el-table-column label="冲突时段" width="240">
            <template #default="{ row }">
              {{ formatTime(row.conflictStart) }} - {{ formatTime(row.conflictEnd) }}
            </template>
          </el-table-column>
          <el-table-column label="处理方式" width="120">
            <template #default="{ row }">
              <el-tag>{{ getResolveLabel(row.resolveAction) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" />
        </el-table>
        <el-empty v-if="!conflictLoading && !conflictList.length" description="暂无冲突记录" />
      </el-card>
    </div>

    <!-- ============ 利用率统计 ============ -->
    <div v-if="activeTab === 'utilization'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="8">
          <el-card class="stat-card">
            <div class="stat-icon blue"><el-icon :size="24"><OfficeBuilding /></el-icon></div>
            <div>
              <div class="stat-value">{{ avgRoomUtil }}%</div>
              <div class="stat-label">房间平均利用率</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="stat-card">
            <div class="stat-icon green"><el-icon :size="24"><User /></el-icon></div>
            <div>
              <div class="stat-value">{{ avgTechUtil }}%</div>
              <div class="stat-label">技师平均利用率</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="stat-card">
            <div class="stat-icon orange"><el-icon :size="24"><Coin /></el-icon></div>
            <div>
              <div class="stat-value">¥{{ totalRevenue }}</div>
              <div class="stat-label">总收入</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>资源利用率排行</span>
            <el-button type="primary" @click="handleRecalculate" :loading="recalculating">
              <el-icon><Refresh /></el-icon>重新统计
            </el-button>
          </div>
        </template>

        <el-table :data="topUtilization" border stripe v-loading="utilLoading">
          <el-table-column label="排行" width="60" type="index" />
          <el-table-column label="资源类型" width="120">
            <template #default="{ row }">
              <el-tag>{{ row.resource_type === 'room' ? '房间' : '技师' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="资源名称" prop="resource_name" />
          <el-table-column label="平均利用率" width="200">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.avg_utilization || 0)" :color="getUtilColor" />
            </template>
          </el-table-column>
          <el-table-column label="订单数" prop="total_orders" width="100" align="center" />
          <el-table-column label="总收入" prop="total_revenue" width="120" align="right">
            <template #default="{ row }">
              ¥{{ Number(row.total_revenue || 0).toFixed(0) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 排程对话框 -->
    <el-dialog v-model="scheduleDialogVisible" title="新增排程" width="600px">
      <el-form :model="scheduleForm" label-width="100px">
        <el-form-item label="客户">
          <el-select v-model="scheduleForm.memberId" filterable style="width:100%">
            <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务">
          <el-select v-model="scheduleForm.serviceId" filterable style="width:100%">
            <el-option v-for="s in serviceList" :key="s.id" :label="s.serviceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="房间">
              <el-select v-model="scheduleForm.roomId" style="width:100%">
                <el-option v-for="r in roomList" :key="r.id" :label="r.roomName" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="技师">
              <el-select v-model="scheduleForm.employeeId" style="width:100%">
                <el-option v-for="e in employeeList" :key="e.id" :label="e.name" :value="e.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="开始时间">
          <el-date-picker v-model="scheduleForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="scheduleForm.durationMinutes" :min="15" :step="15" />
        </el-form-item>

        <el-alert v-if="conflictResult?.hasConflict" type="warning" :closable="false" style="margin-bottom:12px">
          <strong>检测到冲突:</strong> {{ conflictResult.message }}
          <div style="margin-top:8px">
            <el-radio-group v-model="resolveAction">
              <el-radio v-for="o in resolveActionOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
            </el-radio-group>
          </div>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="checkConflict" :loading="checking">冲突检测</el-button>
        <el-button type="primary" @click="submitSchedule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, OfficeBuilding, User, UserFilled, Search, Refresh, Coin
} from '@element-plus/icons-vue'
import request from '@/utils/http'
import {
  resourceScheduleApi,
  conflictTypeOptions,
  resolveActionOptions
} from '@/api/resource-schedule'

const activeTab = ref('gantt')

// ============ 甘特图 ============
const viewType = ref('room')
const filterDate = ref(new Date().toISOString().slice(0, 10))
const hours = ref(Array.from({ length: 12 }, (_, i) => i + 9)) // 9:00 - 20:00
const bookings = ref<any[]>([])
const roomList = ref<any[]>([])
const employeeList = ref<any[]>([])

const resources = computed(() => {
  if (viewType.value === 'room') return roomList.value
  return employeeList.value
})

const loadBookings = async () => {
  try {
    const res: any = await request.get('/booking/list', { params: { date: filterDate.value } })
    bookings.value = res.data || []
  } catch (e) {
    bookings.value = []
  }
}

const loadRooms = async () => {
  try {
    const res: any = await request.get('/room/list')
    roomList.value = res.data || []
  } catch (e) {
    roomList.value = []
  }
}

const loadEmployees = async () => {
  try {
    const res: any = await request.get('/employee/list')
    employeeList.value = res.data || []
  } catch (e) {
    employeeList.value = []
  }
}

const handleViewTypeChange = () => {
  loadBookings()
}

const getBookingsAt = (resourceId: number, hour: number) => {
  return bookings.value.filter(b => {
    if (viewType.value === 'room') {
      if (b.roomId !== resourceId) return false
    } else {
      if (b.employeeId !== resourceId) return false
    }
    if (!b.startTime) return false
    const startHour = new Date(b.startTime).getHours()
    return startHour === hour
  })
}

const getBookingClass = (b: any) => {
  const statusMap: any = {
    1: 'pending',
    2: 'confirmed',
    3: 'checked-in',
    4: 'completed',
    5: 'cancelled'
  }
  return statusMap[b.status] || 'pending'
}

const formatTime = (dt: string) => {
  if (!dt) return '-'
  return dt.slice(11, 16)
}

const handleBookingClick = (b: any) => {
  ElMessageBox.alert(
    `客户: ${b.memberName}\n服务: ${b.serviceItem}\n时间: ${formatTime(b.startTime)} - ${formatTime(b.endTime)}`,
    '排程详情'
  )
}

// ============ 智能推荐 ============
const serviceList = ref<any[]>([])
const suggestForm = reactive({
  serviceId: null,
  startTime: '',
  durationMinutes: 60
})
const suggestions = ref<any[]>([])
const suggestLoading = ref(false)
const suggestLoaded = ref(false)

const loadServices = async () => {
  try {
    const res: any = await request.get('/service/list')
    serviceList.value = res.data || []
  } catch (e) {}
}

const loadSuggestions = async () => {
  suggestLoading.value = true
  suggestLoaded.value = true
  try {
    const res: any = await resourceScheduleApi.suggest(suggestForm)
    suggestions.value = res.data || []
  } catch (e) {
    suggestions.value = []
  } finally {
    suggestLoading.value = false
  }
}

const handleUseSuggestion = (s: any) => {
  scheduleForm.roomId = s.roomId
  scheduleForm.employeeId = s.primaryEmployeeId
  scheduleDialogVisible.value = true
}

// ============ 冲突记录 ============
const conflictList = ref<any[]>([])
const conflictTypeFilter = ref('')
const conflictLoading = ref(false)

const loadConflicts = async () => {
  conflictLoading.value = true
  try {
    const res: any = await resourceScheduleApi.conflicts({
      conflictType: conflictTypeFilter.value || undefined
    })
    conflictList.value = res.data || []
  } catch (e) {
    conflictList.value = []
  } finally {
    conflictLoading.value = false
  }
}

const getConflictType = (t: string) => conflictTypeOptions.find(o => o.value === t) || { label: t, type: 'info' }
const getResolveLabel = (a: string) => resolveActionOptions.find(o => o.value === a)?.label || a

// ============ 利用率 ============
const topUtilization = ref<any[]>([])
const utilLoading = ref(false)
const recalculating = ref(false)

const avgRoomUtil = computed(() => {
  const rooms = topUtilization.value.filter(r => r.resource_type === 'room')
  if (!rooms.length) return 0
  const sum = rooms.reduce((s, r) => s + Number(r.avg_utilization || 0), 0)
  return (sum / rooms.length).toFixed(1)
})

const avgTechUtil = computed(() => {
  const techs = topUtilization.value.filter(r => r.resource_type === 'technician')
  if (!techs.length) return 0
  const sum = techs.reduce((s, r) => s + Number(r.avg_utilization || 0), 0)
  return (sum / techs.length).toFixed(1)
})

const totalRevenue = computed(() => topUtilization.value.reduce((s, r) => s + Number(r.total_revenue || 0), 0).toFixed(0))

const loadUtilization = async () => {
  utilLoading.value = true
  try {
    const startDate = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
    const endDate = new Date().toISOString().slice(0, 10)
    const res: any = await resourceScheduleApi.topUtilization({ startDate, endDate })
    topUtilization.value = res.data || []
  } catch (e) {
    topUtilization.value = []
  } finally {
    utilLoading.value = false
  }
}

const handleRecalculate = async () => {
  recalculating.value = true
  try {
    await resourceScheduleApi.recalculate({ storeId: 1, date: filterDate.value })
    ElMessage.success('重新统计完成')
    loadUtilization()
  } catch (e) {
    ElMessage.error('重新统计失败')
  } finally {
    recalculating.value = false
  }
}

const getUtilColor = (percentage: number) => {
  if (percentage < 30) return '#F56C6C'
  if (percentage < 60) return '#E6A23C'
  return '#67C23A'
}

// ============ 排程对话框 ============
const scheduleDialogVisible = ref(false)
const scheduleForm = reactive<any>({
  memberId: null,
  memberName: '',
  serviceId: null,
  roomId: null,
  employeeId: null,
  startTime: '',
  durationMinutes: 60
})
const conflictResult = ref<any>(null)
const resolveAction = ref('manual_resolve')
const checking = ref(false)
const memberList = ref<any[]>([])

const handleNewSchedule = () => {
  Object.assign(scheduleForm, {
    memberId: null,
    serviceId: null,
    roomId: null,
    employeeId: null,
    startTime: filterDate.value + ' 10:00:00',
    durationMinutes: 60
  })
  conflictResult.value = null
  scheduleDialogVisible.value = true
}

const checkConflict = async () => {
  if (!scheduleForm.startTime) {
    ElMessage.warning('请选择开始时间')
    return
  }
  checking.value = true
  try {
    const startTime = new Date(scheduleForm.startTime)
    const endTime = new Date(startTime.getTime() + scheduleForm.durationMinutes * 60000)
    const res: any = await resourceScheduleApi.checkConflict({
      roomId: scheduleForm.roomId,
      employeeId: scheduleForm.employeeId,
      startTime: scheduleForm.startTime,
      endTime: endTime.toISOString().slice(0, 19).replace('T', ' ')
    })
    conflictResult.value = res.data
    if (res.data.hasConflict) {
      ElMessage.warning('检测到资源冲突')
    } else {
      ElMessage.success('无冲突,可以排程')
    }
  } catch (e) {
    ElMessage.error('检测失败')
  } finally {
    checking.value = false
  }
}

const submitSchedule = async () => {
  try {
    const startTime = new Date(scheduleForm.startTime)
    const endTime = new Date(startTime.getTime() + scheduleForm.durationMinutes * 60000)
    const booking = {
      memberId: scheduleForm.memberId,
      memberName: memberList.value.find(m => m.id === scheduleForm.memberId)?.name,
      employeeId: scheduleForm.employeeId,
      roomId: scheduleForm.roomId,
      startTime: scheduleForm.startTime,
      endTime: endTime.toISOString().slice(0, 19).replace('T', ' '),
      bookingDate: scheduleForm.startTime.slice(0, 10)
    }
    await resourceScheduleApi.createSchedule(booking, resolveAction.value)
    ElMessage.success('排程成功')
    scheduleDialogVisible.value = false
    loadBookings()
  } catch (e: any) {
    ElMessage.error(e.message || '排程失败')
  }
}

// ============ Tab 切换 ============
const handleTabChange = (tab: string) => {
  if (tab === 'gantt') {
    loadBookings()
    loadRooms()
    loadEmployees()
  } else if (tab === 'suggest') {
    loadServices()
  } else if (tab === 'conflict') {
    loadConflicts()
  } else if (tab === 'utilization') {
    loadUtilization()
  }
}

onMounted(() => {
  loadBookings()
  loadRooms()
  loadEmployees()
  loadServices()
})
</script>

<style scoped>
.resource-schedule-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 甘特图 */
.gantt-wrapper {
  overflow-x: auto;
  margin-top: 16px;
}

.gantt-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 1200px;
}

.gantt-table th {
  background: #fafbfc;
  padding: 8px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  border: 1px solid #ebeef5;
}

.gantt-table td {
  border: 1px solid #ebeef5;
  padding: 0;
  vertical-align: top;
  min-height: 50px;
  position: relative;
}

.resource-cell {
  width: 120px;
  position: sticky;
  left: 0;
  background: #fff;
  z-index: 1;
  text-align: left !important;
  padding: 12px !important;
}

.resource-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.hour-cell {
  min-width: 80px;
}

.time-cell {
  height: 60px;
  padding: 4px !important;
}

.booking-block {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  padding: 4px 6px;
  font-size: 11px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: all 0.2s;
}

.booking-block:hover {
  background: #d9ecff;
  transform: translateY(-1px);
}

.booking-block.pending { background: #fdf6ec; border-color: #e6a23c; }
.booking-block.confirmed { background: #ecf5ff; border-color: #409eff; }
.booking-block.checked-in { background: #f0f9ff; border-color: #67c23a; }
.booking-block.completed { background: #f0f9ff; border-color: #909399; }
.booking-block.cancelled { background: #fef0f0; border-color: #f56c6c; text-decoration: line-through; opacity: 0.6; }

.block-title {
  font-weight: 600;
  color: #303133;
}

.block-time {
  color: #606266;
  font-size: 10px;
}

/* 推荐卡片 */
.suggestion-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  position: relative;
  text-align: center;
}

.suggestion-rank {
  position: absolute;
  top: 8px;
  left: 8px;
  background: #909399;
  color: #fff;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 12px;
}

.suggestion-rank.top {
  background: linear-gradient(135deg, #f56c6c, #ff9c9c);
}

.suggestion-score {
  font-size: 24px;
  font-weight: 700;
  color: #f56c6c;
  margin: 8px 0;
}

.suggestion-detail {
  text-align: left;
  margin: 12px 0;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 13px;
  color: #303133;
}

.reason {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  background: #fafbfc;
  padding: 4px 8px;
  border-radius: 4px;
}

/* 统计卡片 */
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-icon.blue { background: linear-gradient(135deg, #409eff, #79bbff); }
.stat-icon.green { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-icon.orange { background: linear-gradient(135deg, #e6a23c, #f3d19e); }

.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; }
</style>