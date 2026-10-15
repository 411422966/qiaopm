<template>
  <div class="page">
    <el-row :gutter="16" class="mb16">
      <el-col :span="8"><el-card><div class="stat"><span>周订单预测</span><strong>{{ totalExpected }}</strong></div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat"><span>推荐人数</span><strong>{{ staffCount }}</strong></div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat"><span>平均负载</span><strong>{{ staffLoad.avgWorkload }}%</strong></div></el-card></el-col>
    </el-row>

    <el-tabs v-model="tab">
      <el-tab-pane label="客流预测" name="forecast">
        <el-form :inline="true">
          <el-form-item label="门店ID"><el-input-number v-model="forecastForm.storeId" /></el-form-item>
          <el-form-item label="日期"><el-date-picker v-model="forecastForm.date" type="date" /></el-form-item>
          <el-button type="primary" @click="runForecast">预测</el-button>
        </el-form>
        <el-table :data="forecast">
          <el-table-column prop="time" label="时段"/>
          <el-table-column prop="expectedBookings" label="预期订单数"/>
          <el-table-column label="可视化">
            <template #default="{ row }">
              <el-progress :percentage="Math.min(100, (row.expectedBookings / 30) * 100)" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="排班生成" name="schedule">
        <el-form :inline="true">
          <el-form-item label="门店ID"><el-input-number v-model="genForm.storeId" /></el-form-item>
          <el-form-item label="周开始"><el-date-picker v-model="genForm.weekStart" type="date" /></el-form-item>
          <el-button type="success" @click="generate">生成排班</el-button>
        </el-form>
        <el-table :data="schedules">
          <el-table-column prop="id" label="ID" width="80"/>
          <el-table-column prop="storeId" label="门店" width="80"/>
          <el-table-column prop="weekStart" label="周开始"/>
          <el-table-column prop="staffCount" label="技师数" width="100"/>
          <el-table-column prop="expectedBookings" label="预期订单" width="100"/>
          <el-table-column prop="generatedAt" label="生成时间" width="200"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="人员负载" name="load">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="平均负载">{{ staffLoad.avgWorkload }}%</el-descriptions-item>
          <el-descriptions-item label="峰值技师">{{ staffLoad.peakStaff }}</el-descriptions-item>
          <el-descriptions-item label="建议">{{ staffLoad.recommendation }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiSchedulerApi } from '@/api/ai-scheduler'

const tab = ref('forecast')
const forecast = ref<any[]>([])
const schedules = ref<any[]>([])
const staffLoad = ref<any>({ avgWorkload: 0, peakStaff: '', recommendation: '' })
const forecastForm = ref({ storeId: 1, date: new Date() })
const genForm = ref({ storeId: 1, weekStart: new Date() })
const totalExpected = computed(() => forecast.value.reduce((s, f) => s + f.expectedBookings, 0))
const staffCount = computed(() => Math.min(5, Math.max(2, Math.round(totalExpected.value / 20 + 2))))

async function runForecast() {
  const { data } = await aiSchedulerApi.forecastTraffic({
    storeId: forecastForm.value.storeId,
    date: forecastForm.value.date.toISOString().substring(0, 10)
  })
  forecast.value = data || []
  ElMessage.success('已预测')
}
async function generate() {
  const { data } = await aiSchedulerApi.generateSchedule({
    storeId: genForm.value.storeId,
    weekStart: genForm.value.weekStart.toISOString().substring(0, 10)
  })
  ElMessage.success(`已生成，需 ${data.staffCount} 名技师`)
  loadSchedules()
}
async function loadSchedules() {
  const { data } = await aiSchedulerApi.listSchedules()
  schedules.value = data || []
}
async function loadStaffLoad() {
  const { data } = await aiSchedulerApi.getStaffLoad()
  staffLoad.value = data || {}
}

onMounted(() => { loadSchedules(); loadStaffLoad() })
</script>

<style scoped>
.stat { display:flex; justify-content:space-between; align-items:center; }
.mb16 { margin-bottom:16px; }
</style>
