<template>
  <div class="statistics-page">
    <!-- 顶部统计卡片 -->
    <div class="stats-row">
      <div class="stat-card stat-1">
        <div class="stat-icon"><el-icon :size="28"><Calendar /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalRecords || 0 }}</div>
          <div class="stat-label">打卡总次数</div>
        </div>
      </div>
      <div class="stat-card stat-2">
        <div class="stat-icon"><el-icon :size="28"><Check /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.normalCount || 0 }}</div>
          <div class="stat-label">正常出勤</div>
        </div>
      </div>
      <div class="stat-card stat-3">
        <div class="stat-icon"><el-icon :size="28"><Warning /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ (stats.lateCount || 0) + (stats.earlyCount || 0) }}</div>
          <div class="stat-label">异常出勤</div>
        </div>
      </div>
      <div class="stat-card stat-4">
        <div class="stat-icon"><el-icon :size="28"><Timer /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ formatDuration(stats.avgWorkMinutes || 0) }}</div>
          <div class="stat-label">平均工时</div>
        </div>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>考勤统计</span>
          <div class="filter-form">
            <el-select
              v-model="query.storeId"
              placeholder="选择门店"
              clearable
              style="width: 160px"
              @change="loadData"
            >
              <el-option
                v-for="store in storeList"
                :key="store.id"
                :label="store.storeName"
                :value="store.id"
              />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="monthrange"
              range-separator="-"
              start-placeholder="开始月份"
              end-placeholder="结束月份"
              format="YYYY-MM"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="loadData"
            />
          </div>
        </div>
      </template>

      <!-- 详细统计数据 -->
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="detail-card">
            <div class="detail-label">正常出勤率</div>
            <div class="detail-value">
              <span class="number">{{ normalRate }}%</span>
            </div>
            <el-progress :percentage="normalRate" :stroke-width="6" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="detail-card">
            <div class="detail-label">迟到率</div>
            <div class="detail-value">
              <span class="number">{{ lateRate }}%</span>
            </div>
            <el-progress :percentage="lateRate" status="warning" :stroke-width="6" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="detail-card">
            <div class="detail-label">早退率</div>
            <div class="detail-value">
              <span class="number">{{ earlyRate }}%</span>
            </div>
            <el-progress :percentage="earlyRate" status="warning" :stroke-width="6" />
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="detail-card">
            <div class="detail-label">旷工率</div>
            <div class="detail-value">
              <span class="number">{{ absenceRate }}%</span>
            </div>
            <el-progress :percentage="absenceRate" status="exception" :stroke-width="6" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 员工考勤月报 -->
      <el-card style="margin-top: 20px">
        <template #header>
          <div class="card-header">
            <span>员工考勤月报</span>
            <div>
              <el-select
                v-model="reportEmployeeId"
                placeholder="选择员工"
                filterable
                style="width: 200px; margin-right: 12px"
              >
                <el-option
                  v-for="emp in employeeList"
                  :key="emp.id"
                  :label="emp.employeeName"
                  :value="emp.id"
                />
              </el-select>
              <el-date-picker
                v-model="reportMonth"
                type="month"
                placeholder="选择月份"
                format="YYYY-MM"
                value-format="YYYY-MM"
                style="width: 140px"
              />
              <el-button type="primary" @click="loadMonthlyReport">查询</el-button>
            </div>
          </div>
        </template>

        <el-descriptions v-if="monthlyReport" :column="3" border>
          <el-descriptions-item label="员工">{{ monthlyReport.employeeName }}</el-descriptions-item>
          <el-descriptions-item label="月份">{{ monthlyReport.year }}年{{ monthlyReport.month }}月</el-descriptions-item>
          <el-descriptions-item label="应出勤天数">{{ monthlyReport.totalDays }}天</el-descriptions-item>
          <el-descriptions-item label="实际出勤">{{ monthlyReport.checkInDays }}天</el-descriptions-item>
          <el-descriptions-item label="正常出勤">{{ monthlyReport.normalDays }}天</el-descriptions-item>
          <el-descriptions-item label="迟到天数">{{ monthlyReport.lateDays }}天</el-descriptions-item>
          <el-descriptions-item label="早退天数">{{ monthlyReport.earlyDays }}天</el-descriptions-item>
          <el-descriptions-item label="迟到分钟">{{ monthlyReport.totalLateMinutes }}分钟</el-descriptions-item>
          <el-descriptions-item label="总工时">{{ formatDuration(monthlyReport.totalWorkMinutes) }}</el-descriptions-item>
        </el-descriptions>

        <el-table
          v-if="monthlyReport && monthlyReport.records"
          :data="monthlyReport.records"
          border
          stripe
          style="margin-top: 16px"
          size="small"
        >
          <el-table-column label="日期" prop="checkDate" width="120" />
          <el-table-column label="上班" width="100">
            <template #default="{ row }">
              {{ row.checkInTime ? row.checkInTime.slice(11, 19) : '--' }}
            </template>
          </el-table-column>
          <el-table-column label="下班" width="100">
            <template #default="{ row }">
              {{ row.checkOutTime ? row.checkOutTime.slice(11, 19) : '--' }}
            </template>
          </el-table-column>
          <el-table-column label="工时" width="100">
            <template #default="{ row }">
              <span v-if="row.workMinutes">{{ formatDuration(row.workMinutes) }}</span>
              <span v-else>--</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.isLate" type="warning" size="small">迟到</el-tag>
              <el-tag v-else-if="row.isEarlyLeave" type="warning" size="small">早退</el-tag>
              <el-tag v-else type="success" size="small">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, Check, Warning, Timer } from '@element-plus/icons-vue'
import { attendanceApi } from '@/api/attendance'

const stats = ref<any>({})
const monthlyReport = ref<any>(null)
const employeeList = ref<any[]>([])
const storeList = ref<any[]>([])

const query = reactive({
  storeId: null as number | null
})

const dateRange = ref<[string, string] | null>(null)
const reportEmployeeId = ref<number | null>(null)
const reportMonth = ref(new Date().toISOString().slice(0, 7))

const formatDuration = (minutes: number) => {
  if (!minutes) return '0分钟'
  const hours = Math.floor(minutes / 60)
  const mins = Math.round(minutes % 60)
  return `${hours}小时${mins}分钟`
}

const normalRate = computed(() => {
  const total = stats.value.totalRecords || 0
  if (!total) return 0
  return Math.round((stats.value.normalCount || 0) / total * 100)
})

const lateRate = computed(() => {
  const total = stats.value.totalRecords || 0
  if (!total) return 0
  return Math.round((stats.value.lateCount || 0) / total * 100)
})

const earlyRate = computed(() => {
  const total = stats.value.totalRecords || 0
  if (!total) return 0
  return Math.round((stats.value.earlyCount || 0) / total * 100)
})

const absenceRate = computed(() => {
  const total = stats.value.totalRecords || 0
  if (!total) return 0
  return Math.round((stats.value.absenceCount || 0) / total * 100)
})

const loadData = async () => {
  try {
    let startDate: string | undefined
    let endDate: string | undefined

    if (dateRange.value) {
      startDate = dateRange.value[0]
      endDate = dateRange.value[1]
    }

    const res: any = await attendanceApi.statistics({
      storeId: query.storeId || undefined,
      startDate,
      endDate
    })
    stats.value = res.data || {}
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

const loadMonthlyReport = async () => {
  if (!reportEmployeeId.value || !reportMonth.value) {
    ElMessage.warning('请选择员工和月份')
    return
  }
  try {
    const [year, month] = reportMonth.value.split('-').map(Number)
    const res: any = await attendanceApi.monthlyReport(
      reportEmployeeId.value,
      year,
      month
    )
    monthlyReport.value = res.data
  } catch (e) {
    console.error('加载月报失败', e)
  }
}

watch(dateRange, () => {
  loadData()
})

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.statistics-page {
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
.stat-2 .stat-icon { background: linear-gradient(135deg, #43e97b, #38f9d7); }
.stat-3 .stat-icon { background: linear-gradient(135deg, #f5576c, #ff6b6b); }
.stat-4 .stat-icon { background: linear-gradient(135deg, #4facfe, #00f2fe); }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  gap: 12px;
}

.detail-card {
  text-align: center;
}

.detail-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.detail-value .number {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
}
</style>
