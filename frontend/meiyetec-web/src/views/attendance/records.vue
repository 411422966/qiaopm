<template>
  <div class="attendance-records-page">
    <el-card class="search-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="员工">
          <el-select
            v-model="query.employeeId"
            placeholder="全部员工"
            clearable
            filterable
            style="width: 160px"
          >
            <el-option
              v-for="emp in employeeList"
              :key="emp.id"
              :label="emp.employeeName"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="门店">
          <el-select
            v-model="query.storeId"
            placeholder="全部门店"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="store in storeList"
              :key="store.id"
              :label="store.storeName"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="员工" prop="employeeName" width="120" fixed />
        <el-table-column label="门店" prop="storeName" width="120" />
        <el-table-column label="日期" prop="checkDate" width="120" sortable />
        <el-table-column label="上班打卡" prop="checkInTime" width="130">
          <template #default="{ row }">
            <span v-if="row.checkInTime">{{ formatTime(row.checkInTime) }}</span>
            <span v-else class="text-muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="下班打卡" prop="checkOutTime" width="130">
          <template #default="{ row }">
            <span v-if="row.checkOutTime">{{ formatTime(row.checkOutTime) }}</span>
            <span v-else class="text-muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="工作时长" width="120">
          <template #default="{ row }">
            <span v-if="row.workMinutes">{{ formatDuration(row.workMinutes) }}</span>
            <span v-else class="text-muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.isLate" type="warning" size="small">迟到</el-tag>
            <el-tag v-else-if="row.isEarlyLeave" type="warning" size="small">早退</el-tag>
            <el-tag v-else-if="row.isAbsence" type="danger" size="small">旷工</el-tag>
            <el-tag v-else-if="row.checkInTime" type="success" size="small">正常</el-tag>
            <span v-else class="text-muted">未打卡</span>
          </template>
        </el-table-column>
        <el-table-column label="迟到分钟" prop="lateMinutes" width="100">
          <template #default="{ row }">
            <span v-if="row.lateMinutes">{{ row.lateMinutes }}分钟</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="打卡方式" width="100">
          <template #default="{ row }">
            <span>{{ row.checkInMethod || 'app' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">补录</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[20, 50, 100, 200]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- 补录对话框 -->
    <el-dialog v-model="editDialogVisible" title="打卡补录" width="500px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="员工">
          <span>{{ editForm.employeeName }}</span>
        </el-form-item>
        <el-form-item label="日期">
          <span>{{ editForm.checkDate }}</span>
        </el-form-item>
        <el-form-item label="上班打卡">
          <el-time-picker
            v-model="editForm.checkInTime"
            placeholder="选择时间"
            format="HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="下班打卡">
          <el-time-picker
            v-model="editForm.checkOutTime"
            placeholder="选择时间"
            format="HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { attendanceApi } from '@/api/attendance'

const query = reactive({
  employeeId: null as number | null,
  storeId: null as number | null,
  startDate: null as string | null,
  endDate: null as string | null
})

const dateRange = ref<[string, string] | null>(null)
const tableData = ref<any[]>([])
const employeeList = ref<any[]>([])
const storeList = ref<any[]>([])
const loading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const editDialogVisible = ref(false)
const editForm = reactive<any>({
  id: null,
  employeeName: '',
  checkDate: '',
  checkInTime: '',
  checkOutTime: '',
  remark: ''
})

watch(dateRange, (val) => {
  if (val) {
    query.startDate = val[0]
    query.endDate = val[1]
  } else {
    query.startDate = null
    query.endDate = null
  }
})

const formatTime = (timeStr: string) => {
  if (!timeStr) return '--'
  return timeStr.slice(11, 19)
}

const formatDuration = (minutes: number) => {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${hours}小时${mins}分钟`
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await attendanceApi.records({
      employeeId: query.employeeId || undefined,
      storeId: query.storeId || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined
    })
    tableData.value = res.data || []
    pagination.total = tableData.value.length
  } catch (e) {
    console.error('加载记录失败', e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.employeeId = null
  query.storeId = null
  query.startDate = null
  query.endDate = null
  dateRange.value = null
  handleSearch()
}

const handleEdit = (row: any) => {
  editForm.id = row.id
  editForm.employeeName = row.employeeName
  editForm.checkDate = row.checkDate
  editForm.checkInTime = row.checkInTime
  editForm.checkOutTime = row.checkOutTime
  editForm.remark = row.remark || ''
  editDialogVisible.value = true
}

const handleSave = async () => {
  try {
    await attendanceApi.update({
      id: editForm.id,
      checkInTime: editForm.checkInTime,
      checkOutTime: editForm.checkOutTime,
      remark: editForm.remark
    })
    ElMessage.success('补录成功')
    editDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleExport = () => {
  ElMessage.success('已提交导出任务')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.attendance-records-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.text-muted {
  color: #c0c4cc;
}
</style>
