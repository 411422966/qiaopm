<template>
  <div class="category-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分类管理</span>
          <div>
            <el-button type="success" @click="handleExpandAll">
              <el-icon><Expand /></el-icon>展开全部
            </el-button>
            <el-button @click="handleCollapseAll">
              <el-icon><Fold /></el-icon>折叠全部
            </el-button>
            <el-button type="primary" @click="handleAdd()">
              <el-icon><Plus /></el-icon>新增顶级分类
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="tableData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAll"
        border
        stripe
        draggable
        @row-drop="handleRowDrop"
      >
        <el-table-column prop="categoryName" label="分类名称" min-width="250">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-icon v-if="row.icon" :size="18" :color="row.level === 1 ? '#409EFF' : '#67C23A'">
                <component :is="row.icon" />
              </el-icon>
              <el-icon v-else :size="18" color="#909399">
                <Folder />
              </el-icon>
              <span :style="{ fontWeight: row.level === 1 ? 'bold' : 'normal' }">
                {{ row.categoryName }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryCode" label="编码" width="120" />
        <el-table-column prop="level" label="层级" width="80">
          <template #default="{ row }">
            <el-tag :type="row.level === 1 ? 'primary' : 'success'" size="small">
              Lv.{{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.categoryType === 'goods' ? 'warning' : 'info'" size="small">
              {{ row.categoryType === 'goods' ? '服务' : '商品' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleAdd(row)" v-if="row.level < 3">
              <el-icon><Plus /></el-icon>添加子分类
            </el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:12px;color:#909399;font-size:13px">
        <el-icon><InfoFilled /></el-icon>
        提示：拖拽行可调整顺序，点击操作列可添加子分类
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" />
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="form.categoryCode" placeholder="如 HAIR" />
        </el-form-item>
        <el-form-item label="父级分类" prop="parentId">
          <el-cascader
            v-model="form.parentId"
            :options="parentOptions"
            :props="{ checkStrictly: true, value: 'id', label: 'categoryName' }"
            clearable
            placeholder="顶级分类"
            :disabled="isSubCategory"
          />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-select v-model="form.icon" placeholder="选择图标" clearable filterable>
            <el-option v-for="icon in iconOptions" :key="icon" :label="icon" :value="icon">
              <el-icon><component :is="icon" /></el-icon>
              <span style="margin-left:8px">{{ icon }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="categoryType">
          <el-radio-group v-model="form.categoryType">
            <el-radio value="goods">服务项目</el-radio>
            <el-radio value="product">商品</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { Plus, Folder, Expand, Fold, InfoFilled } from '@element-plus/icons-vue'
import * as ElIcons from '@element-plus/icons-vue'
import { categoryApi } from '@/api/basic'

const tableData = ref<any[]>([])
const parentOptions = ref<any[]>([])
const expandAll = ref(true)
const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const isSubCategory = ref(false)

const iconOptions = Object.keys(ElIcons).filter(k => k.endsWith('Filled')).slice(0, 30)

const form = reactive({
  id: null as number | null, categoryName: '', categoryCode: '',
  parentId: 0 as number, icon: '', categoryType: 'goods', sortOrder: 0, status: 1
})

const rules: FormRules = { categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }] }

const loadData = async () => {
  const res: any = await categoryApi.tree()
  tableData.value = res.data || []
  const listRes: any = await categoryApi.list()
  parentOptions.value = listRes.data || []
}

const handleAdd = (parent?: any) => {
  isEdit.value = false
  isSubCategory.value = !!parent
  dialogTitle.value = parent ? `添加子分类 (父级: ${parent.categoryName})` : '新增顶级分类'
  Object.assign(form, {
    id: null, categoryName: '', categoryCode: '',
    parentId: parent?.id || 0, icon: '', categoryType: parent?.categoryType || 'goods',
    sortOrder: 0, status: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true; isSubCategory.value = false
  dialogTitle.value = '编辑分类'
  Object.assign(form, { ...row, parentId: row.parentId || 0 })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确认删除分类 "${row.categoryName}" ? 子分类将一并删除`, '提示', { type: 'warning' })
  await categoryApi.delete(row.id)
  ElMessage.success('删除成功'); loadData()
}

const handleExpandAll = () => { expandAll.value = true }
const handleCollapseAll = () => { expandAll.value = false }

const handleRowDrop = async (e: any) => {
  try {
    const ids = tableData.value.map((c: any) => c.id)
    await categoryApi.sort(ids)
    ElMessage.success('排序已更新')
    loadData()
  } catch { ElMessage.error('排序更新失败') }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      const submitData = { ...form, parentId: form.parentId || 0 }
      isEdit.value ? await categoryApi.update(submitData) : await categoryApi.create(submitData)
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false; loadData()
    }
  })
}

onMounted(() => loadData())
</script>

<style scoped>
.card-header { display:flex; justify-content:space-between; align-items:center; }
</style>