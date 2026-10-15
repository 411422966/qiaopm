<template>
  <div class="store-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#409EFF"><el-icon><OfficeBuilding /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.total || 0 }}</div>
              <div class="stat-label">门店总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#67C23A"><el-icon><CircleCheck /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.active || 0 }}</div>
              <div class="stat-label">营业中</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#F56C6C"><el-icon><CircleClose /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.inactive || 0 }}</div>
              <div class="stat-label">已停业</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索门店名称/编码/联系人" clearable style="width:280px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px;margin-left:8px">
          <el-option label="营业" :value="1" />
          <el-option label="停业" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadData" style="margin-left:8px">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <div style="flex:1"></div>
        <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon>新增门店</el-button>
      </div>

      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column prop="storeName" label="门店名称" min-width="180">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-avatar :size="32" :src="row.logoUrl">{{ row.storeName?.[0] }}</el-avatar>
              <span>{{ row.storeName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="storeCode" label="编码" width="100" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="电话" width="130" />
        <el-table-column label="营业时间" width="140">
          <template #default="{ row }">
            <el-icon style="vertical-align:middle"><Clock /></el-icon>
            {{ row.businessHours || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="address" label="地址" show-overflow-tooltip min-width="200" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '营业' : '停业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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
            <el-form-item label="门店名称" prop="storeName">
              <el-input v-model="form.storeName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店编码" prop="storeCode">
              <el-input v-model="form.storeCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactPerson">
              <el-input v-model="form.contactPerson" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="省份" prop="province">
              <el-input v-model="form.province" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市" prop="city">
              <el-input v-model="form.city" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县" prop="district">
              <el-input v-model="form.district" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址" prop="address">
              <el-input v-model="form.address" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="营业时间" prop="businessHours">
              <el-input v-model="form.businessHours" placeholder="如 09:00-21:00" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">营业</el-radio>
                <el-radio :value="0">停业</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="LOGO" prop="logoUrl">
              <el-input v-model="form.logoUrl" placeholder="LOGO URL" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="3" />
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
import { Plus, OfficeBuilding, CircleCheck, CircleClose, Clock } from '@element-plus/icons-vue'
import { storeApi } from '@/api/basic'

const tableData = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<any>({})
const dialogVisible = ref(false)
const dialogTitle = ref('新增门店')
const formRef = ref<FormInstance>()
const isEdit = ref(false)

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined as number | undefined })

const form = reactive({
  id: null as number | null, storeName: '', storeCode: '', contactPerson: '',
  contactPhone: '', address: '', province: '', city: '', district: '',
  businessHours: '', status: 1, logoUrl: '', description: ''
})

const rules: FormRules = { storeName: [{ required: true, message: '请输入门店名称', trigger: 'blur' }] }

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await storeApi.page(query)
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally { loading.value = false }
}

const loadStats = async () => {
  const res: any = await storeApi.stats()
  stats.value = res.data || {}
}

const resetQuery = () => { query.keyword = ''; query.status = undefined; query.pageNum = 1; loadData() }

const handleAdd = () => {
  isEdit.value = false; dialogTitle.value = '新增门店'
  Object.assign(form, { id: null, storeName: '', storeCode: '', contactPerson: '', contactPhone: '', address: '', province: '', city: '', district: '', businessHours: '', status: 1, logoUrl: '', description: '' })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true; dialogTitle.value = '编辑门店'
  Object.assign(form, row); dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确认删除门店 "${row.storeName}" ?`, '提示', { type: 'warning' })
  await storeApi.delete(row.id); ElMessage.success('删除成功'); loadData(); loadStats()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      isEdit.value ? await storeApi.update(form) : await storeApi.create(form)
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false; loadData(); loadStats()
    }
  })
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.toolbar { display:flex; align-items:center; margin-bottom:16px; }
.stat-item { display:flex; align-items:center; gap:16px; }
.stat-icon { width:48px; height:48px; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#fff; font-size:24px; }
.stat-value { font-size:24px; font-weight:bold; }
.stat-label { font-size:13px; color:#909399; margin-top:4px; }
</style>