<template>
  <div class="brand-page">
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="编码 / 名称" style="width: 200px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openCreate">+ 新增品牌</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="brandCode" label="编码" width="140" />
        <el-table-column prop="brandName" label="名称" min-width="180" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="brandTypeColor(row.brandType)">{{ brandTypeLabel(row.brandType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="会员共享" width="100">
          <template #default="{ row }">{{ row.memberShareScope === 'BRAND' ? '品牌共享' : '门店独立' }}</template>
        </el-table-column>
        <el-table-column label="积分共享" width="100">
          <template #default="{ row }">{{ row.pointsShareScope === 'BRAND' ? '品牌共享' : '门店独立' }}</template>
        </el-table-column>
        <el-table-column label="储值共享" width="100">
          <template #default="{ row }">{{ row.balanceShareScope === 'BRAND' ? '品牌共享' : '门店独立' }}</template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="电话" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openStores(row)">门店</el-button>
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
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

    <!-- 品牌弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑品牌' : '新增品牌'" width="600px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" required><el-input v-model="form.brandCode" :disabled="!!dialog.id" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required><el-input v-model="form.brandName" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="类型">
          <el-select v-model="form.brandType" style="width: 100%">
            <el-option label="直营连锁" value="CHAIN" />
            <el-option label="加盟" value="FRANCHISE" />
            <el-option label="旗舰店" value="FLAGSHIP" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话"><el-input v-model="form.contactPhone" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="会员共享">
              <el-select v-model="form.memberShareScope" style="width: 100%">
                <el-option label="品牌共享" value="BRAND" />
                <el-option label="门店独立" value="STORE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="积分共享">
              <el-select v-model="form.pointsShareScope" style="width: 100%">
                <el-option label="品牌共享" value="BRAND" />
                <el-option label="门店独立" value="STORE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="储值共享">
              <el-select v-model="form.balanceShareScope" style="width: 100%">
                <el-option label="品牌共享" value="BRAND" />
                <el-option label="门店独立" value="STORE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 品牌下门店 -->
    <el-dialog v-model="storeDialog.visible" :title="`${storeDialog.brand?.brandName} - 门店列表`" width="800px">
      <el-table :data="storeDialog.stores || []" border size="small">
        <el-table-column prop="storeName" label="门店名称" min-width="180" />
        <el-table-column prop="storeType" label="类型" width="100" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="电话" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '营业' : '停业' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const filter = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: undefined as number | undefined })
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/org/brand/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function brandTypeLabel(t: string) {
  return ({ CHAIN: '直营连锁', FRANCHISE: '加盟', FLAGSHIP: '旗舰店' } as any)[t] || t
}
function brandTypeColor(t: string) {
  return ({ CHAIN: 'primary', FRANCHISE: 'warning', FLAGSHIP: 'success' } as any)[t] || ''
}

function resetFilter() { filter.keyword = ''; filter.status = undefined; filter.pageNum = 1; loadData() }

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive<any>({
  brandCode: '', brandName: '', brandType: 'CHAIN',
  contactPerson: '', contactPhone: '',
  memberShareScope: 'BRAND', pointsShareScope: 'BRAND', balanceShareScope: 'BRAND',
  status: 1
})

function openCreate() { dialog.id = undefined; dialog.visible = true }
function openEdit(row: any) { dialog.id = row.id; Object.assign(form, row); dialog.visible = true }

function resetDialog() {
  Object.assign(form, {
    brandCode: '', brandName: '', brandType: 'CHAIN',
    contactPerson: '', contactPhone: '',
    memberShareScope: 'BRAND', pointsShareScope: 'BRAND', balanceShareScope: 'BRAND', status: 1
  })
}

async function save() {
  if (!form.brandCode || !form.brandName) return ElMessage.warning('编码和名称必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (dialog.id) {
      await http.put(`/org/brand/${dialog.id}`, form, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/org/brand', form, { headers })
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
  await ElMessageBox.confirm(`删除品牌「${row.brandName}」？`, '提示', { type: 'warning' })
  await http.delete(`/org/brand/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

const storeDialog = reactive<any>({ visible: false, brand: null, stores: [] })
async function openStores(row: any) {
  storeDialog.brand = row
  const r = await http.get(`/org/hq/brand/${row.id}/stores`)
  storeDialog.stores = r.data || []
  storeDialog.visible = true
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-input, .search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
</style>
