<template>
  <div class="config-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>考勤规则配置</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增配置
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="配置名称" prop="configName" width="200" />
        <el-table-column label="适用门店" width="120">
          <template #default="{ row }">
            <span v-if="row.storeId">门店ID: {{ row.storeId }}</span>
            <el-tag v-else type="success" size="small">通用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上班时间" prop="workStartTime" width="120" />
        <el-table-column label="下班时间" prop="workEndTime" width="120" />
        <el-table-column label="允许迟到" width="120">
          <template #default="{ row }">{{ row.allowLateMinutes || 0 }} 分钟</template>
        </el-table-column>
        <el-table-column label="早退警告" width="120">
          <template #default="{ row }">{{ row.earlyLeaveMinutes || 0 }} 分钟</template>
        </el-table-column>
        <el-table-column label="弹性打卡" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.flexibleEnabled === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="位置打卡" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.locationEnabled === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="人脸识别" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.faceEnabled === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="配置名称">
          <el-input v-model="form.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="适用门店">
          <el-select v-model="form.storeId" placeholder="通用配置（不选则为通用）" clearable style="width: 100%">
            <el-option v-for="s in storeList" :key="s.id" :label="s.storeName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上班时间">
          <el-time-picker v-model="form.workStartTime" placeholder="选择上班时间" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下班时间">
          <el-time-picker v-model="form.workEndTime" placeholder="选择下班时间" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="允许迟到分钟">
          <el-input-number v-model="form.allowLateMinutes" :min="0" :max="120" />
        </el-form-item>
        <el-form-item label="早退警告分钟">
          <el-input-number v-model="form.earlyLeaveMinutes" :min="0" :max="120" />
        </el-form-item>
        <el-form-item label="弹性打卡">
          <el-switch v-model="form.flexibleEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <template v-if="form.flexibleEnabled === 1">
          <el-form-item label="弹性上班开始">
            <el-time-picker v-model="form.flexibleStartTime" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
          </el-form-item>
          <el-form-item label="弹性上班结束">
            <el-time-picker v-model="form.flexibleEndTime" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item label="位置打卡">
          <el-switch v-model="form.locationEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="人脸识别">
          <el-switch v-model="form.faceEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { attendanceApi } from '@/api/attendance'

const tableData = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增考勤配置')
const storeList = ref<any[]>([])

const form = reactive<any>({
  id: null,
  configName: '',
  storeId: null,
  workStartTime: '09:00:00',
  workEndTime: '18:00:00',
  allowLateMinutes: 5,
  earlyLeaveMinutes: 30,
  flexibleEnabled: 0,
  flexibleStartTime: '',
  flexibleEndTime: '',
  locationEnabled: 0,
  faceEnabled: 0,
  status: 1
})

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await attendanceApi.configList()
    tableData.value = res.data || []
  } catch (e) {
    console.error('加载配置失败', e)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增考勤配置'
  Object.assign(form, {
    id: null,
    configName: '',
    storeId: null,
    workStartTime: '09:00:00',
    workEndTime: '18:00:00',
    allowLateMinutes: 5,
    earlyLeaveMinutes: 30,
    flexibleEnabled: 0,
    flexibleStartTime: '',
    flexibleEndTime: '',
    locationEnabled: 0,
    faceEnabled: 0,
    status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑考勤配置'
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (form.id) {
      await attendanceApi.updateConfig(form)
    } else {
      await attendanceApi.createConfig(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该配置吗？', '提示', { type: 'warning' })
    await attendanceApi.deleteConfig(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleStatusChange = async (row: any) => {
  try {
    await attendanceApi.updateConfig({ id: row.id, status: row.status })
    ElMessage.success('状态已更新')
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('更新失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.config-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
