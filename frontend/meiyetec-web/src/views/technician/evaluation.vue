<template>
  <div class="evaluation-page">
    <!-- 顶部统计卡片 -->
    <div class="stats-row">
      <div class="stat-card stat-1">
        <div class="stat-icon"><el-icon :size="28"><Trophy /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total || 0 }}</div>
          <div class="stat-label">累计评定</div>
        </div>
      </div>
      <div class="stat-card stat-2">
        <div class="stat-icon"><el-icon :size="28"><Top /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.upgrade || 0 }}</div>
          <div class="stat-label">本月晋升</div>
        </div>
      </div>
      <div class="stat-card stat-3">
        <div class="stat-icon"><el-icon :size="28"><Bottom /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.downgrade || 0 }}</div>
          <div class="stat-label">本月降级</div>
        </div>
      </div>
      <div class="stat-card stat-4">
        <div class="stat-icon"><el-icon :size="28"><User /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.active || 0 }}</div>
          <div class="stat-label">活跃技师</div>
        </div>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>等级评定记录</span>
          <div class="filter-form">
            <el-select
              v-model="query.employeeId"
              placeholder="选择员工"
              clearable
              filterable
              style="width: 180px"
              @change="loadData"
            >
              <el-option
                v-for="emp in employeeList"
                :key="emp.id"
                :label="emp.employeeName"
                :value="emp.id"
              />
            </el-select>
            <el-select
              v-model="query.status"
              placeholder="状态"
              clearable
              style="width: 130px"
              @change="loadData"
            >
              <el-option
                v-for="opt in evaluationStatusOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-button type="primary" @click="handleAutoEvaluate">
              <el-icon><MagicStick /></el-icon>
              触发自动评定
            </el-button>
            <el-button type="success" @click="handleManualAdjust">
              <el-icon><Edit /></el-icon>
              手动调整
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="员工" prop="employeeName" width="120" fixed />
        <el-table-column label="门店" prop="storeId" width="100" />
        <el-table-column label="原等级" prop="fromLevelName" width="120">
          <template #default="{ row }">
            <span v-if="row.fromLevelName">{{ row.fromLevelName }}</span>
            <span v-else class="text-muted">未评定</span>
          </template>
        </el-table-column>
        <el-table-column label="目标等级" prop="toLevelName" width="120" />
        <el-table-column label="评定结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'upgrade'" type="success">晋升</el-tag>
            <el-tag v-else-if="row.result === 'downgrade'" type="danger">降级</el-tag>
            <el-tag v-else type="info">维持</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评定方式" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.evaluationType === 'auto'" type="primary">自动</el-tag>
            <el-tag v-else type="warning">手动</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="累计业绩" prop="totalSales" width="120">
          <template #default="{ row }">
            <span v-if="row.totalSales">¥{{ row.totalSales }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="好评率" prop="ratingScore" width="100">
          <template #default="{ row }">
            <span v-if="row.ratingScore">{{ row.ratingScore }}%</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="服务月数" prop="serviceMonths" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.serviceMonths">{{ row.serviceMonths }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="评定日期" prop="evaluationDate" width="120" />
        <el-table-column label="操作人" prop="operatorName" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'active'" type="success" size="small">已生效</el-tag>
            <el-tag v-else-if="row.status === 'reverted'" type="info" size="small">已撤销</el-tag>
            <el-tag v-else type="warning" size="small">待生效</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewMetrics(row)">
              查看指标
            </el-button>
            <el-button
              v-if="row.status === 'active'"
              type="danger"
              link
              size="small"
              @click="handleRevert(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 自动评定对话框 -->
    <el-dialog v-model="autoDialogVisible" title="自动评定" width="500px">
      <el-form :model="autoForm" label-width="100px">
        <el-form-item label="选择员工">
          <el-select
            v-model="autoForm.employeeId"
            placeholder="选择员工"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="emp in employeeList"
              :key="emp.id"
              :label="emp.employeeName"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="autoForm.operatorName" placeholder="请输入操作人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="autoDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAutoConfirm">开始评定</el-button>
      </template>
    </el-dialog>

    <!-- 手动调整对话框 -->
    <el-dialog v-model="adjustDialogVisible" title="手动调整等级" width="500px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="选择员工">
          <el-select
            v-model="adjustForm.employeeId"
            placeholder="选择员工"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="emp in employeeList"
              :key="emp.id"
              :label="emp.employeeName"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标等级">
          <el-select v-model="adjustForm.toLevelId" placeholder="选择目标等级" style="width: 100%">
            <el-option
              v-for="level in levelList"
              :key="level.id"
              :label="level.levelName"
              :value="level.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.note" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdjustConfirm">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 员工指标对话框 -->
    <el-dialog v-model="metricsDialogVisible" title="员工评定指标" width="600px">
      <el-descriptions :column="2" border v-if="metrics">
        <el-descriptions-item label="员工">{{ metrics.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="当前等级">{{ metrics.currentLevelName || '未评定' }}</el-descriptions-item>
        <el-descriptions-item label="累计业绩">¥{{ metrics.totalSales || 0 }}</el-descriptions-item>
        <el-descriptions-item label="好评率">{{ metrics.ratingScore || 0 }}%</el-descriptions-item>
        <el-descriptions-item label="服务月数">{{ metrics.serviceMonths || 0 }} 月</el-descriptions-item>
        <el-descriptions-item label="投诉率">{{ metrics.complaintRate || 0 }}%</el-descriptions-item>
      </el-descriptions>

      <el-divider v-if="nextLevel">晋升目标</el-divider>
      <el-card v-if="nextLevel" shadow="never">
        <div class="next-level">
          <div class="level-info">
            <span class="level-code" :style="{ color: nextLevel.levelColor }">
              {{ nextLevel.levelCode }}
            </span>
            <span class="level-name">{{ nextLevel.levelName }}</span>
          </div>
          <div class="level-meta">
            <span>折扣：{{ nextLevel.discountRate }}%</span>
            <span>提成加成：{{ nextLevel.commissionBonus }}%</span>
          </div>
        </div>
      </el-card>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Trophy, Top, Bottom, User, MagicStick, Edit
} from '@element-plus/icons-vue'
import {
  technicianLevelApi,
  levelEvaluationApi,
  evaluationStatusOptions
} from '@/api/technician-level'

const tableData = ref<any[]>([])
const levelList = ref<any[]>([])
const employeeList = ref<any[]>([])
const loading = ref(false)

const stats = reactive({
  total: 0,
  upgrade: 0,
  downgrade: 0,
  active: 0
})

const query = reactive({
  employeeId: null as number | null,
  status: null as string | null
})

const autoDialogVisible = ref(false)
const adjustDialogVisible = ref(false)
const metricsDialogVisible = ref(false)

const autoForm = reactive({
  employeeId: null as number | null,
  operatorName: ''
})

const adjustForm = reactive({
  employeeId: null as number | null,
  toLevelId: null as number | null,
  note: ''
})

const metrics = ref<any>(null)
const nextLevel = ref<any>(null)

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await levelEvaluationApi.list({
      employeeId: query.employeeId || undefined,
      status: query.status || undefined
    })
    tableData.value = res.data || []
    calculateStats()
  } catch (e) {
    console.error('加载评定记录失败', e)
  } finally {
    loading.value = false
  }
}

const loadLevels = async () => {
  try {
    const res: any = await technicianLevelApi.list()
    levelList.value = res.data || []
  } catch (e) {
    console.error('加载等级失败', e)
  }
}

const calculateStats = () => {
  stats.total = tableData.value.length
  stats.upgrade = tableData.value.filter(r => r.result === 'upgrade').length
  stats.downgrade = tableData.value.filter(r => r.result === 'downgrade').length
  stats.active = tableData.value.filter(r => r.status === 'active').length
}

const handleAutoEvaluate = () => {
  autoForm.employeeId = null
  autoForm.operatorName = ''
  autoDialogVisible.value = true
}

const handleAutoConfirm = async () => {
  if (!autoForm.employeeId) {
    ElMessage.warning('请选择员工')
    return
  }
  try {
    await levelEvaluationApi.autoEvaluate(autoForm.employeeId!, {
      operatorName: autoForm.operatorName,
      storeId: 1
    })
    ElMessage.success('评定完成')
    autoDialogVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '评定失败')
  }
}

const handleManualAdjust = () => {
  adjustForm.employeeId = null
  adjustForm.toLevelId = null
  adjustForm.note = ''
  adjustDialogVisible.value = true
}

const handleAdjustConfirm = async () => {
  if (!adjustForm.employeeId || !adjustForm.toLevelId) {
    ElMessage.warning('请完善信息')
    return
  }
  try {
    await levelEvaluationApi.adjustLevel({
      employeeId: adjustForm.employeeId!,
      toLevelId: adjustForm.toLevelId!,
      note: adjustForm.note,
      operatorName: '管理员',
      storeId: 1
    })
    ElMessage.success('调整成功')
    adjustDialogVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '调整失败')
  }
}

const handleRevert = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定撤销该评定记录吗？', '提示', { type: 'warning' })
    await levelEvaluationApi.revert(row.id)
    ElMessage.success('撤销成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleViewMetrics = async (row: any) => {
  try {
    const res: any = await levelEvaluationApi.getMetrics(row.employeeId)
    metrics.value = {
      ...res.data,
      employeeName: row.employeeName
    }

    // 计算下一等级
    const currentLevel = levelList.value.find(l => l.id === row.toLevelId)
    if (currentLevel) {
      const next = technicianLevelApi
      // 简单模拟下一等级
      const sortedLevels = levelList.value.sort((a: any, b: any) => a.sortOrder - b.sortOrder)
      const idx = sortedLevels.findIndex(l => l.id === currentLevel.id)
      if (idx > 0) {
        nextLevel.value = sortedLevels[idx - 1]
      } else {
        nextLevel.value = null
      }
    }
    metricsDialogVisible.value = true
  } catch (e) {
    console.error('获取指标失败', e)
  }
}

onMounted(() => {
  loadData()
  loadLevels()
})
</script>

<style scoped>
.evaluation-page {
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
  font-size: 28px;
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
  align-items: center;
}

.text-muted {
  color: #c0c4cc;
}

.next-level {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.level-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.level-code {
  font-weight: bold;
  font-size: 18px;
}

.level-name {
  font-size: 16px;
  color: #303133;
}

.level-meta {
  display: flex;
  gap: 16px;
  color: #606266;
}
</style>
