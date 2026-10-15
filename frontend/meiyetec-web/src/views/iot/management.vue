<template>
  <div class="page-link">
    <div class="page-header">
      <h2>设备联动</h2>
      <div class="header-actions">
        <el-select v-model="masterDeviceFilter" placeholder="按主设备筛选" clearable style="width: 240px" @change="loadLinks">
          <el-option v-for="d in deviceList" :key="d.id" :label="d.deviceName || d.deviceCode" :value="d.id" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增联动</el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="hover" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span><strong>联动列表</strong>（共 {{ links.length }} 条）</span>
        </div>
      </template>
      <el-table :data="links" stripe>
        <el-table-column label="联动 ID" prop="id" width="100" />
        <el-table-column label="主设备" min-width="160">
          <template #default="{ row }">
            {{ deviceName(row.masterDeviceId) }}
          </template>
        </el-table-column>
        <el-table-column label="从设备" min-width="160">
          <template #default="{ row }">
            {{ deviceName(row.slaveDeviceId) }}
          </template>
        </el-table-column>
        <el-table-column label="触发类型" prop="triggerType" width="120" />
        <el-table-column label="触发条件" min-width="200">
          <template #default="{ row }">
            <code class="json-text">{{ shortJson(row.triggerCondition) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="联动动作" min-width="200">
          <template #default="{ row }">
            <code class="json-text">{{ shortJson(row.actionJson) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="onToggle(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="主设备" required>
          <el-select v-model="form.masterDeviceId" placeholder="选择主设备" style="width: 100%">
            <el-option v-for="d in deviceList" :key="d.id" :label="d.deviceName || d.deviceCode" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="从设备" required>
          <el-select v-model="form.slaveDeviceId" placeholder="选择从设备" style="width: 100%">
            <el-option v-for="d in deviceList" :key="d.id" :label="d.deviceName || d.deviceCode" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发类型" required>
          <el-select v-model="form.triggerType" placeholder="选择触发类型" style="width: 100%">
            <el-option label="遥测数据" value="TELEMETRY" />
            <el-option label="指令响应" value="COMMAND" />
            <el-option label="告警事件" value="ALERT" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发条件">
          <el-input v-model="form.triggerCondition" type="textarea" :rows="3"
                    placeholder='JSON: {"operator":"gt","metric":"水分","value":30}' />
        </el-form-item>
        <el-form-item label="联动动作">
          <el-input v-model="form.actionJson" type="textarea" :rows="3"
                    placeholder='JSON: {"action":"send_command","params":{"commandId":"ON"}}' />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch :model-value="form.enabled === 1"
                     @change="(v:any) => form.enabled = v ? 1 : 0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import { iotApi } from '@/api/iot'

const links = ref<any[]>([])
const deviceList = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const masterDeviceFilter = ref<number | undefined>(undefined)
const dialogVisible = ref(false)
const dialogTitle = ref('新增联动')
const form = reactive<any>({
  id: null,
  masterDeviceId: undefined,
  slaveDeviceId: undefined,
  triggerType: 'TELEMETRY',
  triggerCondition: '',
  actionJson: '',
  enabled: 1,
  description: ''
})

const deviceName = (id?: number) => {
  const d = deviceList.value.find(x => x.id === id)
  return d ? (d.deviceName || d.deviceCode || `设备 ${id}`) : `#${id}`
}

const shortJson = (s: string) => {
  if (!s) return ''
  return s.length > 40 ? s.slice(0, 40) + '...' : s
}

async function loadLinks() {
  loading.value = true
  try {
    const res: any = await iotApi.links(masterDeviceFilter.value)
    links.value = res.data || []
  } catch {
    links.value = []
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  try {
    const res: any = await iotApi.listDevices()
    deviceList.value = res.data || []
  } catch {
    deviceList.value = []
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    masterDeviceId: undefined,
    slaveDeviceId: undefined,
    triggerType: 'TELEMETRY',
    triggerCondition: '',
    actionJson: '',
    enabled: 1,
    description: ''
  })
  dialogTitle.value = '新增联动'
  dialogVisible.value = true
}

function openEdit(row: any) {
  Object.assign(form, row)
  dialogTitle.value = '编辑联动'
  dialogVisible.value = true
}

async function onSave() {
  if (!form.masterDeviceId || !form.slaveDeviceId) {
    ElMessage.warning('请选择主设备和从设备')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await iotApi.updateLink(form.id, form)
    } else {
      await iotApi.createLink(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadLinks()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function onDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除联动 #${row.id}？`, '提示', { type: 'warning' })
    await iotApi.deleteLink(row.id)
    ElMessage.success('已删除')
    await loadLinks()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

async function onToggle(row: any, val: boolean | string | number) {
  const enabled = val ? 1 : 0
  try {
    await iotApi.toggleLink(row.id, enabled)
    row.enabled = enabled
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch {
    ElMessage.error('切换失败')
  }
}

onMounted(async () => {
  await loadDevices()
  await loadLinks()
})
</script>

<style scoped>
.page-link { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.header-actions { display: flex; gap: 12px; }
.table-card { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.json-text {
  font-family: 'Menlo', 'Monaco', monospace;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
