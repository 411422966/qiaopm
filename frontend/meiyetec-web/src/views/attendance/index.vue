<template>
  <div class="attendance-page">
    <!-- 顶部统计卡片 -->
    <div class="stats-row">
      <div class="stat-card stat-1">
        <div class="stat-icon"><el-icon :size="28"><Timer /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.onTime || 0 }}</div>
          <div class="stat-label">正常出勤</div>
        </div>
      </div>
      <div class="stat-card stat-2">
        <div class="stat-icon"><el-icon :size="28"><Clock /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.late || 0 }}</div>
          <div class="stat-label">迟到次数</div>
        </div>
      </div>
      <div class="stat-card stat-3">
        <div class="stat-icon"><el-icon :size="28"><Warning /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.early || 0 }}</div>
          <div class="stat-label">早退次数</div>
        </div>
      </div>
      <div class="stat-card stat-4">
        <div class="stat-icon"><el-icon :size="28"><Calendar /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.leave || 0 }}</div>
          <div class="stat-label">请假次数</div>
        </div>
      </div>
    </div>

    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span>打卡</span>
          <div class="header-actions">
            <el-date-picker
              v-model="currentDate"
              type="date"
              placeholder="选择日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              size="default"
              style="width: 140px"
              :disabled-date="disableFutureDate"
              @change="loadTodayStatus"
            />
          </div>
        </div>
      </template>

      <!-- 打卡状态展示 -->
      <div class="checkin-panel">
        <div class="current-time">
          <span class="time">{{ currentTime }}</span>
          <span class="date">{{ currentDateStr }}</span>
          <span class="weekday">{{ currentWeekday }}</span>
        </div>

        <div class="checkin-status">
          <!-- 上班打卡 -->
          <div class="status-item">
            <div class="status-label">上班打卡</div>
            <div class="status-time" :class="{ checked: todayStatus.hasCheckedIn }">
              {{ todayStatus.checkInTime || '--:--' }}
            </div>
            <div class="status-badge" v-if="todayStatus.isLate">
              <el-tag type="warning" size="small">迟到 {{ todayStatus.lateMinutes }}分钟</el-tag>
            </div>
            <div class="status-badge" v-else-if="todayStatus.hasCheckedIn">
              <el-tag type="success" size="small">已打卡</el-tag>
            </div>
            <el-button
              v-if="!todayStatus.hasCheckedIn"
              type="primary"
              size="large"
              :loading="checkinLoading"
              @click="handleCheckIn"
            >
              <el-icon><Position /></el-icon>
              上班打卡
            </el-button>
            <el-tag v-else type="info" size="large">已打卡</el-tag>
          </div>

          <!-- 下班打卡 -->
          <div class="status-item">
            <div class="status-label">下班打卡</div>
            <div class="status-time" :class="{ checked: todayStatus.hasCheckedOut }">
              {{ todayStatus.checkOutTime || '--:--' }}
            </div>
            <div class="status-badge" v-if="todayStatus.isEarlyLeave">
              <el-tag type="warning" size="small">早退</el-tag>
            </div>
            <div class="status-badge" v-else-if="todayStatus.hasCheckedOut">
              <el-tag type="success" size="small">已打卡</el-tag>
            </div>
            <el-button
              v-if="todayStatus.hasCheckedIn && !todayStatus.hasCheckedOut"
              type="danger"
              size="large"
              :loading="checkoutLoading"
              @click="handleCheckOut"
            >
              <el-icon><Position /></el-icon>
              下班打卡
            </el-button>
            <el-tag v-else-if="!todayStatus.hasCheckedIn" type="info" size="large">请先上班打卡</el-tag>
            <el-tag v-else type="info" size="large">已打卡</el-tag>
          </div>
        </div>

        <!-- 工作时长 -->
        <div class="work-duration" v-if="todayStatus.workMinutes">
          <span class="label">今日工作时⻓：</span>
          <span class="value">{{ formatDuration(todayStatus.workMinutes) }}</span>
        </div>
      </div>
    </el-card>

    <!-- 考勤配置信息 -->
    <el-card class="config-card" v-if="config">
      <template #header>
        <span>今日考勤规则</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="上班时间">{{ formatTime(config.workStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="下班时间">{{ formatTime(config.workEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="允许迟到">{{ config.allowLateMinutes || 0 }} 分钟</el-descriptions-item>
        <el-descriptions-item label="早退警告">{{ config.earlyLeaveMinutes || 0 }} 分钟</el-descriptions-item>
        <el-descriptions-item label="打卡方式">
          <el-tag v-if="config.locationEnabled" size="small" type="success">位置打卡</el-tag>
          <el-tag v-if="config.faceEnabled" size="small" type="success">人脸识别</el-tag>
          <el-tag v-if="!config.locationEnabled && !config.faceEnabled" size="small">APP打卡</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Timer, Clock, Warning, Calendar, Position } from '@element-plus/icons-vue'
import { attendanceApi } from '@/api/attendance'

const currentDate = ref(new Date().toISOString().slice(0, 10))
const currentTime = ref('')
const checkinLoading = ref(false)
const checkoutLoading = ref(false)
const todayStatus = ref<any>({
  hasCheckedIn: false,
  hasCheckedOut: false,
  checkInTime: null,
  checkOutTime: null,
  isLate: false,
  lateMinutes: 0,
  isEarlyLeave: false,
  workMinutes: null
})
const stats = ref({ onTime: 0, late: 0, early: 0, leave: 0 })
const config = ref<any>(null)

// 模拟当前登录员工ID（实际应从store获取）
const currentEmployeeId = ref(1)

const currentDateStr = computed(() => {
  const d = new Date(currentDate.value)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})

const currentWeekday = computed(() => {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekdays[new Date(currentDate.value).getDay()]
})

const disableFutureDate = (date: Date) => {
  return date > new Date()
}

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const formatDuration = (minutes: number) => {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${hours}小时${mins}分钟`
}

const formatTime = (timeStr: string) => {
  if (!timeStr) return '--:--'
  return timeStr.slice(0, 5)
}

const loadTodayStatus = async () => {
  try {
    const res: any = await attendanceApi.getTodayStatus(currentEmployeeId.value)
    todayStatus.value = res.data || todayStatus.value
  } catch (e) {
    console.error('获取打卡状态失败', e)
  }
}

const loadConfig = async () => {
  try {
    const res: any = await attendanceApi.getActiveConfig()
    config.value = res.data
  } catch (e) {
    console.error('获取考勤配置失败', e)
  }
}

const loadStats = async () => {
  try {
    const now = new Date()
    const startDate = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
    const endDate = now.toISOString().slice(0, 10)
    const res: any = await attendanceApi.statistics({
      storeId: 1,
      startDate,
      endDate
    })
    stats.value = {
      onTime: res.data?.normalCount || 0,
      late: res.data?.lateCount || 0,
      early: res.data?.earlyCount || 0,
      leave: res.data?.absenceCount || 0
    }
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
}

const handleCheckIn = async () => {
  checkinLoading.value = true
  try {
    await attendanceApi.checkIn({
      employeeId: currentEmployeeId.value,
      method: 'app',
      location: '门店位置',
      storeId: 1
    })
    ElMessage.success('上班打卡成功')
    loadTodayStatus()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    checkinLoading.value = false
  }
}

const handleCheckOut = async () => {
  checkoutLoading.value = true
  try {
    await attendanceApi.checkOut({
      employeeId: currentEmployeeId.value,
      method: 'app',
      location: '门店位置'
    })
    ElMessage.success('下班打卡成功')
    loadTodayStatus()
  } catch (e: any) {
    ElMessage.error(e.message || '打卡失败')
  } finally {
    checkoutLoading.value = false
  }
}

let timer: number
onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  loadTodayStatus()
  loadConfig()
  loadStats()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.attendance-page {
  padding: 20px;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
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

.stat-1 .stat-icon { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-2 .stat-icon { background: linear-gradient(135deg, #f093fb, #f5576c); }
.stat-3 .stat-icon { background: linear-gradient(135deg, #4facfe, #00f2fe); }
.stat-4 .stat-icon { background: linear-gradient(135deg, #43e97b, #38f9d7); }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.main-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checkin-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
}

.current-time {
  text-align: center;
  margin-bottom: 40px;
}

.current-time .time {
  display: block;
  font-size: 56px;
  font-weight: bold;
  color: #303133;
  font-family: 'SF Pro Display', -apple-system, sans-serif;
}

.current-time .date {
  display: block;
  font-size: 18px;
  color: #606266;
  margin-top: 8px;
}

.current-time .weekday {
  display: inline-block;
  background: #409EFF;
  color: #fff;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 14px;
  margin-top: 8px;
}

.checkin-status {
  display: flex;
  gap: 80px;
  margin-bottom: 30px;
}

.status-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.status-label {
  font-size: 16px;
  color: #606266;
}

.status-time {
  font-size: 36px;
  font-weight: bold;
  color: #909399;
  min-width: 120px;
  text-align: center;
}

.status-time.checked {
  color: #303133;
}

.status-badge {
  height: 24px;
}

.work-duration {
  font-size: 16px;
  color: #606266;
}

.work-duration .value {
  color: #409EFF;
  font-weight: bold;
}

.config-card {
  margin-top: 20px;
}
</style>
