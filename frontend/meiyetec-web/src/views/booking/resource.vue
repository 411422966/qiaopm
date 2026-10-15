<template>
  <div class="resource-page">
    <el-card class="kpi-row">
      <div class="kpi-item">
        <div class="kpi-label">资源总数</div>
        <div class="kpi-value">{{ stats.total ?? '—' }}</div>
      </div>
      <div class="kpi-item">
        <div class="kpi-label">启用中</div>
        <div class="kpi-value text-success">{{ stats.enabled ?? '—' }}</div>
      </div>
      <div class="kpi-item">
        <div class="kpi-label">房间</div>
        <div class="kpi-value">{{ typeCount('ROOM') }}</div>
      </div>
      <div class="kpi-item">
        <div class="kpi-label">床位</div>
        <div class="kpi-value">{{ typeCount('BED') }}</div>
      </div>
      <div class="kpi-item">
        <div class="kpi-label">设备</div>
        <div class="kpi-value">{{ typeCount('DEVICE') }}</div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="编码 / 名称 / 位置" style="width: 200px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.resourceType" placeholder="类型" clearable style="width: 120px" @change="loadData">
        <el-option v-for="t in typeOptions" :key="t.code" :label="t.name" :value="t.code" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
        <el-option label="维护" :value="2" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openCreate()">+ 新增资源</el-button>
      <el-button type="success" @click="exportCsv">导出 CSV</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="resourceCode" label="编码" width="120" />
        <el-table-column prop="resourceName" label="名称" width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeColor(row.resourceType)">{{ typeLabel(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="80" align="center" />
        <el-table-column prop="location" label="位置" width="140" />
        <el-table-column prop="defaultDuration" label="时长(分)" width="100" />
        <el-table-column label="可用时段" width="200">
          <template #default="{ row }">{{ formatTime(row.availableFrom) }} ~ {{ formatTime(row.availableTo) }}</template>
        </el-table-column>
        <el-table-column prop="equipment" label="配套" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="warning" @click="openBind(row)">绑定项目</el-button>
            <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑资源' : '新增资源'" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="资源编码" required>
          <el-input v-model="form.resourceCode" :disabled="!!dialog.id" />
        </el-form-item>
        <el-form-item label="资源名称" required>
          <el-input v-model="form.resourceName" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.resourceType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.code" :label="t.name" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="容量">
              <el-input-number v-model="form.capacity" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时长(分)">
              <el-input-number v-model="form.defaultDuration" :min="10" :step="15" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="可用时段起">
              <el-time-picker v-model="form.availableFrom" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可用时段止">
              <el-time-picker v-model="form.availableTo" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="3楼东、VIP包间" />
        </el-form-item>
        <el-form-item label="配套设备">
          <el-input v-model="form.equipment" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
            <el-radio :value="2">维护</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 绑定项目弹窗 -->
    <el-dialog v-model="bindDialog.visible" :title="`绑定项目：${bindDialog.resourceName || ''}`" width="720px" @closed="resetBind">
      <el-button size="small" type="primary" @click="openAddService">+ 绑定项目</el-button>
      <el-table :data="bindDialog.services" v-loading="bindDialog.loading" border style="margin-top: 12px">
        <el-table-column prop="serviceCode" label="项目编码" width="140" />
        <el-table-column prop="serviceName" label="项目名称" width="200" />
        <el-table-column prop="isRequired" label="必选" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isRequired === 1 ? 'danger' : 'info'" size="small">{{ row.isRequired === 1 ? '必选' : '可选' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="unitPrice" label="价格" width="100">
          <template #default="{ row }">¥{{ row.unitPrice }}</template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分)" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link size="small" type="danger" @click="unbindService(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="bindDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addServiceDialog.visible" title="绑定项目" width="500px" @closed="resetAddService">
      <el-form :model="addServiceForm" label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="addServiceForm.serviceItemId" placeholder="选择项目" filterable style="width: 100%">
            <el-option v-for="s in serviceList" :key="s.id" :label="`${s.serviceCode} - ${s.serviceName}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="必选">
          <el-radio-group v-model="addServiceForm.isRequired">
            <el-radio :value="1">必选</el-radio>
            <el-radio :value="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="addServiceForm.priority" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addServiceDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddService">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

interface TypeOption { code: string; name: string }

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', resourceType: undefined as string | undefined, status: undefined as number | undefined
})
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const typeOptions = ref<TypeOption[]>([])
const stats = reactive({ total: 0, enabled: 0 })

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/biz/booking/resource/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
    stats.total = total.value
    stats.enabled = rows.value.filter((r: any) => r.status === 1).length
  } finally {
    loading.value = false
  }
}

async function loadTypeOptions() {
  const r = await http.get('/biz/booking/resource/types')
  typeOptions.value = r.data || []
}

function typeCount(code: string) {
  return rows.value.filter((r: any) => r.resourceType === code).length
}

function typeLabel(code: string) {
  return typeOptions.value.find((t) => t.code === code)?.name || code
}
function typeColor(code: string) {
  return code === 'ROOM' ? 'primary' : code === 'BED' ? 'warning' : 'success'
}

function statusLabel(s: number) {
  return s === 1 ? '启用' : s === 0 ? '停用' : '维护'
}
function statusTag(s: number) {
  return s === 1 ? 'success' : s === 0 ? 'info' : 'warning'
}

function formatTime(t: string) {
  if (!t) return ''
  return t.length > 5 ? t.substring(0, 5) : t
}

function resetFilter() {
  filter.keyword = ''
  filter.resourceType = undefined
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive<any>({
  resourceCode: '', resourceName: '', resourceType: 'ROOM',
  capacity: 1, defaultDuration: 60,
  availableFrom: '09:00:00', availableTo: '21:00:00',
  location: '', equipment: '', status: 1, sortOrder: 0
})

function openCreate() {
  dialog.id = undefined
  dialog.visible = true
}

function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, row)
  dialog.visible = true
}

function resetDialog() {
  Object.assign(form, {
    resourceCode: '', resourceName: '', resourceType: 'ROOM',
    capacity: 1, defaultDuration: 60,
    availableFrom: '09:00:00', availableTo: '21:00:00',
    location: '', equipment: '', status: 1, sortOrder: 0
  })
}

async function save() {
  if (!form.resourceCode || !form.resourceName) return ElMessage.warning('编码和名称必填')
  if (!form.resourceType) return ElMessage.warning('类型必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (dialog.id) {
      await http.put(`/biz/booking/resource/${dialog.id}`, form, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/biz/booking/resource', form, { headers })
      ElMessage.success('已创建')
    }
    dialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    dialog.saving = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除资源「${row.resourceName}」？`, '提示', { type: 'warning' })
  try {
    await http.delete(`/biz/booking/resource/${row.id}`)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

const bindDialog = reactive<any>({ visible: false, resourceId: undefined, resourceName: '', services: [], loading: false })
async function openBind(row: any) {
  bindDialog.resourceId = row.id
  bindDialog.resourceName = row.resourceName
  bindDialog.visible = true
  await loadBind()
}
async function loadBind() {
  bindDialog.loading = true
  try {
    const r = await http.get(`/biz/booking/resource/${bindDialog.resourceId}`)
    bindDialog.services = r.data?.services || []
  } finally {
    bindDialog.loading = false
  }
}
function resetBind() {
  bindDialog.resourceId = undefined
  bindDialog.resourceName = ''
  bindDialog.services = []
}

const addServiceDialog = reactive({ visible: false })
const addServiceForm = reactive({ serviceItemId: undefined as number | undefined, isRequired: 1, priority: 0 })
const serviceList = ref<any[]>([])

async function openAddService() {
  addServiceDialog.visible = true
  if (serviceList.value.length === 0) {
    const r = await http.get('/biz/service/item/page', { params: { pageNum: 1, pageSize: 200, status: 1 } })
    serviceList.value = r.data?.records || []
  }
}

function resetAddService() {
  addServiceForm.serviceItemId = undefined
  addServiceForm.isRequired = 1
  addServiceForm.priority = 0
}

async function confirmAddService() {
  if (!addServiceForm.serviceItemId) return ElMessage.warning('请选项目')
  try {
    await http.post(`/biz/booking/resource/${bindDialog.resourceId}/link`, addServiceForm)
    ElMessage.success('已绑定')
    addServiceDialog.visible = false
    await loadBind()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '绑定失败')
  }
}

async function unbindService(row: any) {
  await ElMessageBox.confirm('确认解绑？', '提示', { type: 'warning' })
  await http.delete(`/biz/booking/resource/${bindDialog.resourceId}/link/${row.serviceItemId}`)
  ElMessage.success('已解绑')
  await loadBind()
}

async function exportCsv() {
  try {
    const res = await http.get('/biz/booking/resource/export', {
      params: { resourceType: filter.resourceType, status: filter.status, keyword: filter.keyword },
      responseType: 'blob'
    })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '预约资源.csv'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  await loadTypeOptions()
  await loadData()
})
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-item { background: linear-gradient(135deg, #f0f9ff, #e0f2fe); border-radius: 10px; padding: 16px; text-align: center; }
.kpi-label { font-size: 12px; color: #1e40af; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1e3a8a; margin-top: 4px; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
</style>
