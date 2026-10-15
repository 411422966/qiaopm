<template>
  <div class="employee-container">
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#409EFF"><el-icon><User /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.total || 0 }}</div>
              <div class="stat-label">员工总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#67C23A"><el-icon><CircleCheck /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.active || 0 }}</div>
              <div class="stat-label">在职</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#E6A23C"><el-icon><Sunny /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.leave || 0 }}</div>
              <div class="stat-label">休假</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#F56C6C"><el-icon><CircleClose /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.quit || 0 }}</div>
              <div class="stat-label">离职</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索姓名/工号/手机号" clearable style="width:240px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width:110px;margin-left:8px">
          <el-option label="在职" :value="1" />
          <el-option label="休假" :value="2" />
          <el-option label="离职" :value="0" />
        </el-select>
        <el-select v-model="query.storeId" placeholder="门店" clearable style="width:140px;margin-left:8px">
          <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
        </el-select>
        <el-button type="primary" @click="loadData" style="margin-left:8px">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <div style="flex:1"></div>
        <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon>新增员工</el-button>
      </div>

      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column label="员工" min-width="200">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:10px">
              <el-avatar :size="36" :src="row.avatarUrl">
                {{ row.employeeName?.[0] }}
              </el-avatar>
              <div>
                <div>{{ row.employeeName }}</div>
                <div style="font-size:12px;color:#909399">{{ row.employeeCode || '-' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="性别" width="60">
          <template #default="{ row }">
            <el-icon v-if="row.gender === 1" color="#409EFF"><Male /></el-icon>
            <el-icon v-else color="#E6A23C"><Female /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="position" label="职位" width="100" />
        <el-table-column prop="storeName" label="所属门店" width="150" />
        <el-table-column label="入职日期" width="120">
          <template #default="{ row }">
            {{ row.entryDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'info'" size="small">
              {{ row.status === 1 ? '在职' : row.status === 2 ? '休假' : '离职' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="loadData"
        style="margin-top:16px;justify-content:flex-end;display:flex"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="姓名" prop="employeeName">
              <el-input v-model="form.employeeName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工号" prop="employeeCode">
              <el-input v-model="form.employeeCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证" prop="idCard">
              <el-input v-model="form.idCard" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="0">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属门店" prop="storeId">
              <el-select v-model="form.storeId" placeholder="选择门店" style="width:100%">
                <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职位" prop="position">
              <el-input v-model="form.position" placeholder="如 美容师/美发师" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" prop="department">
              <el-input v-model="form.department" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期" prop="entryDate">
              <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">在职</el-radio>
                <el-radio :value="2">休假</el-radio>
                <el-radio :value="0">离职</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="基本工资" prop="salary">
              <el-input-number v-model="form.salary" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提成比例" prop="commissionRate">
              <el-input-number v-model="form.commissionRate" :min="0" :max="1" :step="0.05" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, User, Male, Female, CircleCheck, CircleClose, Sunny } from '@element-plus/icons-vue'
import { employeeApi, storeApi } from '@/api/basic'

const tableData = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<any>({})
const stores = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增员工')
const formRef = ref<FormInstance>()
const isEdit = ref(false)

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined as number | undefined, storeId: undefined as number | undefined })

const form = reactive({
  id: null as number | null, employeeName: '', employeeCode: '', phone: '', password: '', gender: 1,
  idCard: '', avatarUrl: '', position: '', department: '', storeId: 1,
  entryDate: '', status: 1, salary: 0, commissionRate: 0
})

const rules: FormRules = {
  employeeName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await employeeApi.page(query)
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally { loading.value = false }
}

const loadStats = async () => {
  const res: any = await employeeApi.stats()
  stats.value = res.data || {}
}

const loadStores = async () => {
  const res: any = await storeApi.list()
  stores.value = res.data || []
}

const resetQuery = () => { query.keyword = ''; query.status = undefined; query.storeId = undefined; query.pageNum = 1; loadData() }

const handleAdd = () => {
  isEdit.value = false; dialogTitle.value = '新增员工'
  Object.assign(form, { id: null, employeeName: '', employeeCode: '', phone: '', password: '', gender: 1, idCard: '', avatarUrl: '', position: '', department: '', storeId: stores.value[0]?.id || 1, entryDate: '', status: 1, salary: 0, commissionRate: 0 })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true; dialogTitle.value = '编辑员工'
  Object.assign(form, row); dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确认删除员工 "${row.employeeName}" ?`, '提示', { type: 'warning' })
  await employeeApi.delete(row.id); ElMessage.success('删除成功'); loadData(); loadStats()
}

const handleResetPassword = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', {
      confirmButtonText: '确定', cancelButtonText: '取消', inputValue: '123456'
    })
    await employeeApi.resetPassword(row.id, value)
    ElMessage.success(`密码已重置为: ${value}`)
  } catch {}
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      isEdit.value ? await employeeApi.update(form) : await employeeApi.create(form)
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false; loadData(); loadStats()
    }
  })
}

onMounted(async () => {
  await loadStores()
  loadData(); loadStats()
})
</script>

<style scoped>
.toolbar { display:flex; align-items:center; margin-bottom:16px; }
.stat-item { display:flex; align-items:center; gap:16px; }
.stat-icon { width:48px; height:48px; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#fff; font-size:24px; }
.stat-value { font-size:24px; font-weight:bold; }
.stat-label { font-size:13px; color:#909399; margin-top:4px; }
</style>