<template>
  <div class="leave-page">
    <el-card class="action-card">
      <el-button type="primary" @click="handleApply">
        <el-icon><Plus /></el-icon>
        申请请假
      </el-button>
      <el-button type="success" @click="handleApprove" v-if="canApprove">
        <el-icon><Check /></el-icon>
        审批中心
      </el-button>
      <el-tag v-if="pendingCount > 0" type="warning" style="margin-left: 12px">
        待审批 {{ pendingCount }} 条
      </el-tag>
    </el-card>

    <el-tabs v-model="activeTab" @tab-change="loadData">
      <el-tab-pane label="我的请假" name="my" />
      <el-tab-pane label="请假记录" name="list" />
    </el-tabs>

    <el-card>
      <el-form :inline="true" :model="query" class="search-form" v-if="activeTab === 'list'">
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
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="opt in leaveStatusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
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
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="员工" prop="employeeName" width="120" fixed />
        <el-table-column label="门店" prop="storeName" width="120" />
        <el-table-column label="请假类型" prop="leaveTypeName" width="100" />
        <el-table-column label="开始日期" prop="startDate" width="120" />
        <el-table-column label="结束日期" prop="endDate" width="120" />
        <el-table-column label="请假天数" prop="leaveDays" width="100" align="center" />
        <el-table-column label="原因" prop="reason" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'pending'" type="warning" size="small">待审批</el-tag>
            <el-tag v-else-if="row.status === 'approved'" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.status === 'rejected'" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else type="info" size="small">已取消</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批人" prop="approverName" width="120" />
        <el-table-column label="审批意见" prop="approveRemark" min-width="150" show-overflow-tooltip />
        <el-table-column label="申请时间" prop="createTime" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending' && canApprove">
              <el-button type="success" link size="small" @click="handleApproveRow(row, true)">通过</el-button>
              <el-button type="danger" link size="small" @click="handleApproveRow(row, false)">拒绝</el-button>
            </template>
            <el-button
              v-if="row.status === 'pending' && activeTab === 'my'"
              type="danger"
              link
              size="small"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadData"
      />
    </el-card>

    <!-- 申请请假对话框 -->
    <el-dialog v-model="applyDialogVisible" title="申请请假" width="500px">
      <el-form :model="applyForm" :rules="applyRules" ref="applyFormRef" label-width="100px">
        <el-form-item label="请假类型" prop="leaveType">
          <el-select v-model="applyForm.leaveType" placeholder="请选择请假类型" style="width: 100%">
            <el-option
              v-for="opt in leaveTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="applyForm.startDate"
            type="date"
            placeholder="选择开始日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="applyForm.endDate"
            type="date"
            placeholder="选择结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="请假原因" prop="reason">
          <el-input
            v-model="applyForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请说明请假原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveDialogVisible" title="审批请假" width="500px">
      <el-descriptions :column="1" border size="small" v-if="approveForm.row">
        <el-descriptions-item label="员工">{{ approveForm.row.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="请假类型">{{ approveForm.row.leaveTypeName }}</el-descriptions-item>
        <el-descriptions-item label="时间">
          {{ approveForm.row.startDate }} 至 {{ approveForm.row.endDate }} ({{ approveForm.row.leaveDays }}天)
        </el-descriptions-item>
        <el-descriptions-item label="原因">{{ approveForm.row.reason }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="approveForm" label-width="100px" style="margin-top: 16px">
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleSubmitApprove(false)">拒绝</el-button>
        <el-button type="primary" @click="handleSubmitApprove(true)">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Check, Search } from '@element-plus/icons-vue'
import { leaveApi, leaveTypeOptions, leaveStatusOptions } from '@/api/attendance'

const activeTab = ref('my')
const tableData = ref<any[]>([])
const loading = ref(false)
const pendingCount = ref(0)
const canApprove = ref(true)

// 模拟当前登录员工ID
const currentEmployeeId = ref(1)
const currentUserId = ref(1)

const query = reactive({
  employeeId: null as number | null,
  status: null as string | null,
  startDate: null as string | null,
  endDate: null as string | null
})

const dateRange = ref<[string, string] | null>(null)
const employeeList = ref<any[]>([])

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const applyDialogVisible = ref(false)
const applyFormRef = ref()
const applyForm = reactive({
  leaveType: '',
  startDate: '',
  endDate: '',
  reason: ''
})

const applyRules = {
  leaveType: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  reason: [{ required: true, message: '请输入请假原因', trigger: 'blur' }]
}

const approveDialogVisible = ref(false)
const approveForm = reactive({
  row: null as any,
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

const loadData = async () => {
  loading.value = true
  try {
    let res: any
    if (activeTab.value === 'my') {
      res = await leaveApi.myList(currentEmployeeId.value)
      tableData.value = res.data || []
      enrichLeaveTypeName(tableData.value)
    } else {
      res = await leaveApi.list({
        employeeId: query.employeeId || undefined,
        status: query.status || undefined,
        startDate: query.startDate || undefined,
        endDate: query.endDate || undefined
      })
      tableData.value = res.data || []
    }
    pagination.total = tableData.value.length
  } catch (e) {
    console.error('加载请假记录失败', e)
  } finally {
    loading.value = false
  }
}

const enrichLeaveTypeName = (list: any[]) => {
  list.forEach(item => {
    const type = leaveTypeOptions.find(o => o.value === item.leaveType)
    item.leaveTypeName = type ? type.label : item.leaveType
  })
}

const loadPendingCount = async () => {
  try {
    const res: any = await leaveApi.pendingCount(1)
    pendingCount.value = res.data || 0
  } catch (e) {
    console.error('获取待审批数量失败', e)
  }
}

const handleApply = () => {
  applyForm.leaveType = ''
  applyForm.startDate = ''
  applyForm.endDate = ''
  applyForm.reason = ''
  applyDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!applyFormRef.value) return
  await applyFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    try {
      await leaveApi.submit({
        employeeId: currentEmployeeId.value,
        leaveType: applyForm.leaveType,
        startDate: applyForm.startDate,
        endDate: applyForm.endDate,
        reason: applyForm.reason,
        storeId: 1
      })
      ElMessage.success('请假申请已提交')
      applyDialogVisible.value = false
      loadData()
    } catch (e: any) {
      ElMessage.error(e.message || '提交失败')
    }
  })
}

const handleApprove = () => {
  activeTab.value = 'list'
  loadData()
}

const handleApproveRow = (row: any, approved: boolean) => {
  approveForm.row = row
  approveForm.remark = ''
  approveDialogVisible.value = true
}

const handleSubmitApprove = async (approved: boolean) => {
  try {
    await leaveApi.approve(approveForm.row.id, {
      approverId: currentUserId.value,
      remark: approveForm.remark,
      approved
    })
    ElMessage.success(approved ? '已通过审批' : '已拒绝审批')
    approveDialogVisible.value = false
    loadData()
    loadPendingCount()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

const handleCancel = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要取消该请假申请吗？', '提示', { type: 'warning' })
    await leaveApi.cancel(row.id, currentEmployeeId.value)
    ElMessage.success('已取消请假申请')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('取消失败', e)
    }
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.employeeId = null
  query.status = null
  query.startDate = null
  query.endDate = null
  dateRange.value = null
  handleSearch()
}

onMounted(() => {
  loadData()
  loadPendingCount()
})
</script>

<style scoped>
.leave-page {
  padding: 20px;
}

.action-card {
  margin-bottom: 16px;
}

.search-form {
  margin-bottom: 16px;
}
</style>
