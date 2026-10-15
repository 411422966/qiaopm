<template>
  <div class="store-page">
    <!-- 统计卡片 -->
    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#409eff"><OfficeBuilding /></el-icon>
            <div>
              <div class="stat-value">{{ stats.total || 0 }}</div>
              <div class="stat-label">门店总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#67c23a"><CircleCheck /></el-icon>
            <div>
              <div class="stat-value">{{ stats.active || 0 }}</div>
              <div class="stat-label">营业中</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#f56c6c"><CircleClose /></el-icon>
            <div>
              <div class="stat-value">{{ stats.inactive || 0 }}</div>
              <div class="stat-label">已停业</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input v-model="filter.keyword" placeholder="编码 / 名称 / 联系人" style="width:200px" clearable @keyup.enter="loadData" />
        <el-select v-model="filter.brandId" placeholder="品牌" clearable style="width:160px" @change="loadData">
          <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
        </el-select>
        <el-select v-model="filter.status" placeholder="状态" clearable style="width:120px" @change="loadData">
          <el-option label="营业" :value="1" />
          <el-option label="停业" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadData"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
        <div style="flex:1" />
        <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon>&nbsp;新增门店</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="storeName" label="门店名称" min-width="180" />
        <el-table-column prop="storeCode" label="编码" width="120" />
        <el-table-column label="品牌" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.brandName || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="storeTypeTag(row.storeType)">{{ storeTypeLabel(row.storeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="province" label="省份" width="100" />
        <el-table-column prop="city" label="城市" width="100" />
        <el-table-column prop="contactPerson" label="负责人" width="100" />
        <el-table-column prop="managerName" label="店长" width="100" />
        <el-table-column label="总部" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isHq === 1" type="warning" size="small">总部</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '营业' : '停业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEmployees(row)">员工</el-button>
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 门店表单弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑门店' : '新增门店'" width="640px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="门店名称" prop="storeName">
              <el-input v-model="form.storeName" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店编码" prop="storeCode">
              <el-input v-model="form.storeCode" placeholder="唯一编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属品牌" prop="brandId">
              <el-select v-model="form.brandId" placeholder="必选" style="width:100%">
                <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店类型" prop="storeType">
              <el-select v-model="form.storeType" style="width:100%">
                <el-option label="直营" value="DIRECT" />
                <el-option label="加盟" value="FRANCHISE" />
                <el-option label="旗舰店" value="FLAGSHIP" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="省份">
              <el-input v-model="form.province" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市">
              <el-input v-model="form.city" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县">
              <el-input v-model="form.district" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址">
              <el-input v-model="form.address" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="营业时间">
              <el-input v-model="form.businessHours" placeholder="如 09:00-21:00" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">营业</el-radio>
                <el-radio :value="0">停业</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总部标记">
              <el-switch v-model="form.isHq" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介">
              <el-input v-model="form.description" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 门店员工 -->
    <el-dialog v-model="empDialog.visible" :title="`${empDialog.store?.storeName} - 员工`" width="700px">
      <el-table :data="empDialog.employees || []" border size="small">
        <el-table-column prop="employeeCode" label="工号" width="100" />
        <el-table-column prop="employeeName" label="姓名" width="120" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="position" label="职位" width="100" />
        <el-table-column label="门店角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTag(row.roleInStore)">{{ row.roleInStore || 'STAFF' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主店" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isPrimary === 1" type="success" size="small">主店</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  OfficeBuilding, CircleCheck, CircleClose,
  Search, Plus
} from '@element-plus/icons-vue'
import http from '@/utils/http'

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', brandId: undefined as number | undefined, status: undefined as number | undefined
})
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const brands = ref<any[]>([])
const stats = ref<any>({})

// 统计
async function loadStats() {
  try {
    const r: any = await http.get('/store/stats')
    stats.value = r.data || {}
  } catch {}
}

// 列表
async function loadData() {
  loading.value = true
  try {
    const r: any = await http.get('/org/store/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
    await loadStats()
  } finally {
    loading.value = false
  }
}

// 品牌列表
async function loadBrands() {
  try {
    const r: any = await http.get('/org/brand/page?pageNum=1&pageSize=100')
    brands.value = r.data?.records || []
  } catch {}
}

function resetFilter() {
  filter.keyword = ''
  filter.brandId = undefined
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

function storeTypeLabel(t: string) {
  return ({ DIRECT: '直营', FRANCHISE: '加盟', FLAGSHIP: '旗舰店' } as any)[t] || t || '直营'
}
function storeTypeTag(t: string) {
  return ({ DIRECT: 'primary', FRANCHISE: 'warning', FLAGSHIP: 'success' } as any)[t] || 'primary'
}
function roleTag(r: string) {
  return ({ 店长: 'warning', 副店长: 'info', 技师: 'success', 顾问: 'danger', 前台: '' } as any)[r] || ''
}

// ================================================================
// 表单
// ================================================================
const formVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({
  id: null, storeName: '', storeCode: '', brandId: undefined as number | undefined,
  storeType: 'DIRECT', contactPerson: '', contactPhone: '',
  province: '', city: '', district: '', address: '',
  businessHours: '', status: 1, isHq: 0, description: ''
})

const rules: FormRules = {
  storeName: [{ required: true, message: '门店名称不能为空', trigger: 'blur' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }]
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, {
    id: null, storeName: '', storeCode: '', brandId: undefined,
    storeType: 'DIRECT', contactPerson: '', contactPhone: '',
    province: '', city: '', district: '', address: '',
    businessHours: '', status: 1, isHq: 0, description: ''
  })
  formVisible.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, storeName: row.storeName, storeCode: row.storeCode || '',
    brandId: row.brandId, storeType: row.storeType || 'DIRECT',
    contactPerson: row.contactPerson || '', contactPhone: row.contactPhone || '',
    province: row.province || '', city: row.city || '', district: row.district || '',
    address: row.address || '', businessHours: row.businessHours || '',
    status: row.status ?? 1, isHq: row.isHq ?? 0, description: row.description || ''
  })
  formVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await http.put(`/org/store/${form.id}`, form)
        ElMessage.success('更新成功')
      } else {
        await http.post('/org/store', form)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      loadData()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确认删除门店「${row.storeName}」？`, '删除确认', { type: 'warning' })
  await http.delete(`/org/store/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

// ================================================================
// 员工
// ================================================================
const empDialog = reactive<any>({ visible: false, store: null, employees: [] })
async function openEmployees(row: any) {
  empDialog.store = row
  try {
    const r: any = await http.get(`/org/store/${row.id}/employees`)
    empDialog.employees = r.data || []
  } catch {
    empDialog.employees = []
  }
  empDialog.visible = true
}

onMounted(async () => { await loadBrands(); await loadData() })
</script>

<style scoped>
.toolbar { display:flex; align-items:center; margin-bottom:14px; gap:8px; flex-wrap:wrap }
.toolbar .el-input, .toolbar .el-select { margin-right: 0 }
.stat-card { cursor:default }
.stat-item { display:flex; align-items:center; gap:12px }
.stat-icon { width:40px; height:40px; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#fff; font-size:20px }
.stat-value { font-size:24px; font-weight:700; color:#303133 }
.stat-label { font-size:13px; color:#909399; margin-top:4px }
.pagination { margin-top:14px; display:flex; justify-content:flex-end }
</style>
