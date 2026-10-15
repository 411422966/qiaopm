<template>
  <div class="material-binding-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="服务耗材绑定" name="binding" />
      <el-tab-pane label="用量分组" name="group" />
      <el-tab-pane label="耗材替代规则" name="substitute" />
    </el-tabs>

    <!-- ============ 服务耗材绑定 ============ -->
    <el-card v-if="activeTab === 'binding'">
      <template #header>
        <div class="card-header">
          <span>服务-耗材绑定配置</span>
          <div class="filter-form">
            <el-select
              v-model="bindingServiceFilter"
              placeholder="选择服务"
              filterable
              clearable
              style="width: 240px"
              @change="loadBindingData"
            >
              <el-option
                v-for="s in serviceList"
                :key="s.id"
                :label="s.serviceName"
                :value="s.id"
              />
            </el-select>
            <el-button type="primary" @click="handleAddBinding">
              <el-icon><Plus /></el-icon>
              添加耗材绑定
            </el-button>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        服务的耗材 BOM 清单，每次服务将按此清单消耗库存。订单支付成功后会自动扣减。
      </el-alert>

      <el-table :data="bindingData" border stripe v-loading="bindingLoading">
        <el-table-column label="服务" prop="serviceName" width="160" fixed />
        <el-table-column label="耗材" prop="productName" width="180" />
        <el-table-column label="规格" prop="spec" width="120" />
        <el-table-column label="用量" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="2" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="80" />
        <el-table-column label="属性" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isRequired === 1" type="success" size="small">必需</el-tag>
            <el-tag v-else type="info" size="small">可选</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单次成本" width="120">
          <template #default="{ row }">
            <span v-if="row.unitCost">¥{{ (row.quantity * row.unitCost).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleSaveBinding(row)">保存</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteBinding(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!bindingData.length" description="暂无耗材绑定" />
    </el-card>

    <!-- ============ 用量分组 ============ -->
    <el-card v-if="activeTab === 'group'">
      <template #header>
        <div class="card-header">
          <span>用量分组配置</span>
          <el-button type="primary" @click="handleAddGroup">
            <el-icon><Plus /></el-icon>
            新增分组
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        配置不同场景下的差异化用量，如高级技师额外消耗、客户类型差异化等
      </el-alert>

      <el-table :data="groupData" border stripe v-loading="groupLoading">
        <el-table-column label="分组名称" prop="groupName" width="180" />
        <el-table-column label="服务" prop="serviceName" width="160" />
        <el-table-column label="维度" width="120">
          <template #default="{ row }">
            <el-tag>{{ getDimensionLabel(row.dimension) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="维度值" prop="dimensionValue" min-width="200" />
        <el-table-column label="优先级" prop="priority" width="100" align="center" />
        <el-table-column label="耗材数" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.itemCount ? 'success' : 'info'">
              {{ row.itemCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditGroup(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteGroup(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!groupData.length" description="暂无用量分组" />
    </el-card>

    <!-- ============ 替代规则 ============ -->
    <el-card v-if="activeTab === 'substitute'">
      <template #header>
        <div class="card-header">
          <span>耗材替代规则</span>
          <el-button type="primary" @click="handleAddSubstitute">
            <el-icon><Plus /></el-icon>
            新增替代规则
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        当主耗材库存不足时，自动使用替代耗材。支持设置替代比例和触发条件
      </el-alert>

      <el-table :data="substituteData" border stripe v-loading="substituteLoading">
        <el-table-column label="主耗材" prop="mainProductName" width="180" />
        <el-table-column label="替代耗材" prop="substituteProductName" width="180" />
        <el-table-column label="替代比例" width="120">
          <template #default="{ row }">
            1:{{ row.substituteRatio }}
          </template>
        </el-table-column>
        <el-table-column label="触发条件" width="120">
          <template #default="{ row }">
            <el-tag>{{ getTriggerLabel(row.triggerCondition) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库存阈值" prop="stockThreshold" width="120">
          <template #default="{ row }">
            <span v-if="row.stockThreshold">{{ row.stockThreshold }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" width="100" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditSubstitute(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteSubstitute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!substituteData.length" description="暂无替代规则" />
    </el-card>

    <!-- ============ 绑定对话框 ============ -->
    <el-dialog v-model="bindingDialogVisible" title="添加耗材绑定" width="500px">
      <el-form :model="bindingForm" label-width="100px">
        <el-form-item label="服务">
          <el-select v-model="bindingForm.serviceId" placeholder="选择服务" filterable style="width: 100%">
            <el-option v-for="s in serviceList" :key="s.id" :label="s.serviceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="耗材">
          <el-select v-model="bindingForm.productId" placeholder="选择耗材" filterable style="width: 100%">
            <el-option v-for="p in stockProductList" :key="p.id" :label="p.productName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用量">
          <el-input-number v-model="bindingForm.quantity" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="bindingForm.unit" placeholder="如：ml/g/片" />
        </el-form-item>
        <el-form-item label="属性">
          <el-radio-group v-model="bindingForm.isRequired">
            <el-radio :value="1">必需</el-radio>
            <el-radio :value="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bindingForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmAddBinding">确认</el-button>
      </template>
    </el-dialog>

    <!-- ============ 分组对话框 ============ -->
    <el-dialog v-model="groupDialogVisible" :title="groupDialogTitle" width="500px">
      <el-form :model="groupForm" label-width="100px">
        <el-form-item label="分组名称">
          <el-input v-model="groupForm.groupName" placeholder="如：高级技师" />
        </el-form-item>
        <el-form-item label="服务">
          <el-select v-model="groupForm.serviceId" placeholder="选择服务" filterable style="width: 100%">
            <el-option v-for="s in serviceList" :key="s.id" :label="s.serviceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度">
          <el-select v-model="groupForm.dimension" placeholder="选择维度" style="width: 100%">
            <el-option
              v-for="opt in dimensionOptions"
              :key="opt.value"
              :label="`${opt.label} - ${opt.description}`"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="维度值">
          <el-input v-model="groupForm.dimensionValue" placeholder="如：L3,L4 或 NEW,VIP" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="groupForm.priority" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="groupForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveGroup">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 替代规则对话框 ============ -->
    <el-dialog v-model="substituteDialogVisible" :title="substituteDialogTitle" width="500px">
      <el-form :model="substituteForm" label-width="120px">
        <el-form-item label="主耗材">
          <el-select v-model="substituteForm.mainProductId" placeholder="选择主耗材" filterable style="width: 100%">
            <el-option v-for="p in stockProductList" :key="p.id" :label="p.productName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="替代耗材">
          <el-select v-model="substituteForm.substituteProductId" placeholder="选择替代耗材" filterable style="width: 100%">
            <el-option v-for="p in stockProductList" :key="p.id" :label="p.productName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="替代比例">
          <el-input-number v-model="substituteForm.substituteRatio" :min="0" :precision="2" />
          <span class="form-tip">1份替代耗材 = 多少主耗材</span>
        </el-form-item>
        <el-form-item label="触发条件">
          <el-select v-model="substituteForm.triggerCondition" placeholder="选择触发条件" style="width: 100%">
            <el-option
              v-for="opt in triggerOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="substituteForm.triggerCondition === 'stock_low'" label="库存阈值">
          <el-input-number v-model="substituteForm.stockThreshold" :min="0" />
          <span class="form-tip">低于该值时启用</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="substituteForm.priority" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="substituteForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="substituteDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveSubstitute">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  materialApi,
  dimensionOptions,
  triggerOptions
} from '@/api/material-binding'

const activeTab = ref('binding')

// 服务列表、耗材列表
const serviceList = ref<any[]>([])
const stockProductList = ref<any[]>([])

// ============ 绑定 ============
const bindingData = ref<any[]>([])
const bindingLoading = ref(false)
const bindingServiceFilter = ref<number | null>(null)
const bindingDialogVisible = ref(false)
const bindingForm = reactive<any>({
  serviceId: null,
  productId: null,
  quantity: 1,
  unit: '',
  isRequired: 1,
  remark: ''
})

const loadBindingData = async () => {
  bindingLoading.value = true
  try {
    // 简易实现：调用后端服务获取绑定列表
    // 实际可调用 materialApi.materialsByService()
    bindingData.value = []
  } catch (e) {
    console.error('加载耗材绑定失败', e)
  } finally {
    bindingLoading.value = false
  }
}

const handleAddBinding = () => {
  bindingForm.serviceId = bindingServiceFilter.value
  bindingForm.productId = null
  bindingForm.quantity = 1
  bindingForm.unit = ''
  bindingForm.isRequired = 1
  bindingForm.remark = ''
  bindingDialogVisible.value = true
}

const handleConfirmAddBinding = async () => {
  try {
    await materialApi.createMaterial(bindingForm)
    ElMessage.success('添加成功')
    bindingDialogVisible.value = false
    loadBindingData()
  } catch (e) {
    ElMessage.error('添加失败')
  }
}

const handleSaveBinding = async (row: any) => {
  try {
    await materialApi.updateMaterial(row)
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteBinding = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该耗材绑定吗？', '提示', { type: 'warning' })
    await materialApi.deleteMaterial(row.id)
    ElMessage.success('删除成功')
    loadBindingData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 用量分组 ============
const groupData = ref<any[]>([])
const groupLoading = ref(false)
const groupDialogVisible = ref(false)
const groupDialogTitle = ref('新增分组')
const groupForm = reactive<any>({
  id: null,
  serviceId: null,
  groupName: '',
  dimension: 'employee_level',
  dimensionValue: '',
  priority: 0,
  remark: ''
})

const getDimensionLabel = (dim: string) => {
  const opt = dimensionOptions.find(o => o.value === dim)
  return opt ? opt.label : dim
}

const loadGroupData = async () => {
  groupLoading.value = true
  try {
    const res: any = await materialApi.groupList()
    groupData.value = res.data || []
  } catch (e) {
    console.error('加载分组失败', e)
  } finally {
    groupLoading.value = false
  }
}

const handleAddGroup = () => {
  Object.assign(groupForm, {
    id: null,
    serviceId: null,
    groupName: '',
    dimension: 'employee_level',
    dimensionValue: '',
    priority: 0,
    remark: ''
  })
  groupDialogTitle.value = '新增分组'
  groupDialogVisible.value = true
}

const handleEditGroup = (row: any) => {
  Object.assign(groupForm, row)
  groupDialogTitle.value = '编辑分组'
  groupDialogVisible.value = true
}

const handleSaveGroup = async () => {
  try {
    if (groupForm.id) {
      await materialApi.updateGroup(groupForm)
    } else {
      await materialApi.createGroup(groupForm)
    }
    ElMessage.success('保存成功')
    groupDialogVisible.value = false
    loadGroupData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteGroup = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该分组吗？', '提示', { type: 'warning' })
    await materialApi.deleteGroup(row.id)
    ElMessage.success('删除成功')
    loadGroupData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 替代规则 ============
const substituteData = ref<any[]>([])
const substituteLoading = ref(false)
const substituteDialogVisible = ref(false)
const substituteDialogTitle = ref('新增替代规则')
const substituteForm = reactive<any>({
  id: null,
  mainProductId: null,
  substituteProductId: null,
  substituteRatio: 1,
  triggerCondition: 'stock_low',
  stockThreshold: 10,
  priority: 0,
  status: 1,
  remark: ''
})

const getTriggerLabel = (trigger: string) => {
  const opt = triggerOptions.find(o => o.value === trigger)
  return opt ? opt.label : trigger
}

const loadSubstituteData = async () => {
  substituteLoading.value = true
  try {
    const res: any = await materialApi.substituteList()
    substituteData.value = res.data || []
  } catch (e) {
    console.error('加载替代规则失败', e)
  } finally {
    substituteLoading.value = false
  }
}

const handleAddSubstitute = () => {
  Object.assign(substituteForm, {
    id: null,
    mainProductId: null,
    substituteProductId: null,
    substituteRatio: 1,
    triggerCondition: 'stock_low',
    stockThreshold: 10,
    priority: 0,
    status: 1,
    remark: ''
  })
  substituteDialogTitle.value = '新增替代规则'
  substituteDialogVisible.value = true
}

const handleEditSubstitute = (row: any) => {
  Object.assign(substituteForm, row)
  substituteDialogTitle.value = '编辑替代规则'
  substituteDialogVisible.value = true
}

const handleSaveSubstitute = async () => {
  try {
    if (substituteForm.id) {
      await materialApi.updateSubstitute(substituteForm)
    } else {
      await materialApi.createSubstitute(substituteForm)
    }
    ElMessage.success('保存成功')
    substituteDialogVisible.value = false
    loadSubstituteData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteSubstitute = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该替代规则吗？', '提示', { type: 'warning' })
    await materialApi.deleteSubstitute(row.id)
    ElMessage.success('删除成功')
    loadSubstituteData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleTabChange = (tab: string) => {
  if (tab === 'binding') loadBindingData()
  else if (tab === 'group') loadGroupData()
  else if (tab === 'substitute') loadSubstituteData()
}

const loadServices = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/service/list')
    serviceList.value = res.data || []
  } catch (e) {
    console.error('加载服务失败', e)
  }
}

const loadStockProducts = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/stock/product/list')
    stockProductList.value = res.data || []
  } catch (e) {
    console.error('加载耗材失败', e)
  }
}

onMounted(() => {
  loadServices()
  loadStockProducts()
  loadBindingData()
})
</script>

<style scoped>
.material-binding-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  gap: 12px;
  align-items: center;
}

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>