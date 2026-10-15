<template>
  <div class="commission-rule-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>提成规则配置</span>
          <div class="filter-form">
            <el-input
              v-model="keyword"
              placeholder="搜索规则名称"
              style="width: 200px"
              clearable
              @input="loadRules"
            />
            <el-select
              v-model="filterStatus"
              placeholder="状态"
              clearable
              style="width: 120px"
              @change="loadRules"
            >
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增规则
            </el-button>
            <el-button @click="loadRules">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="规则名称" prop="ruleName" width="180" fixed />
        <el-table-column label="业务类型" prop="businessType" width="120" />
        <el-table-column label="目标项目" width="150">
          <template #default="{ row }">
            <span v-if="row.targetId">{{ row.targetId }}</span>
            <el-tag v-else type="info" size="small">通用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计算方式" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.calculationMethod === 'RATE'" type="success">比例</el-tag>
            <el-tag v-else-if="row.calculationMethod === 'FIXED'" type="warning">固定</el-tag>
            <el-tag v-else-if="row.calculationMethod === 'TIER'" type="primary">阶梯</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="比例/固定金额" width="140">
          <template #default="{ row }">
            <span v-if="row.calculationMethod === 'RATE'">{{ row.rate ? (row.rate * 100).toFixed(1) : 0 }}%</span>
            <span v-else-if="row.calculationMethod === 'FIXED'">¥{{ row.fixedAmount || 0 }}</span>
            <span v-else-if="row.calculationMethod === 'TIER'">{{ tierCountMap[row.id] || 0 }} 阶梯</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" prop="priority" width="80" align="center" />
        <el-table-column label="生效日期" prop="effectiveDate" width="120" />
        <el-table-column label="失效日期" width="120">
          <template #default="{ row }">
            <span v-if="row.expiryDate">{{ row.expiryDate }}</span>
            <span v-else class="text-muted">长期</span>
          </template>
        </el-table-column>
        <el-table-column label="条件数" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="conditionCountMap[row.id] ? 'success' : 'info'">
              {{ conditionCountMap[row.id] || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleConfigCondition(row)">条件</el-button>
            <el-button type="warning" link size="small" @click="handleConfigTier(row)">阶梯</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 规则新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="规则名称">
          <el-input v-model="form.ruleName" placeholder="如：高级技师面部护理提成" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="form.businessType" placeholder="选择业务类型" style="width: 100%">
            <el-option label="服务提成" value="SERVICE" />
            <el-option label="卡项提成" value="CARD" />
            <el-option label="套餐提成" value="PACKAGE" />
            <el-option label="商品提成" value="GOODS" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input-number v-model="form.targetId" :min="0" placeholder="留空表示通用" />
          <span class="form-tip">留空适用于所有项目/服务</span>
        </el-form-item>
        <el-form-item label="计算方式">
          <el-select v-model="form.calculationMethod" placeholder="选择计算方式" style="width: 100%">
            <el-option
              v-for="opt in calculationMethodOptions"
              :key="opt.value"
              :label="`${opt.label} - ${opt.description}`"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.calculationMethod === 'RATE'" label="提成比例">
          <el-input-number v-model="ratePercent" :min="0" :max="100" :precision="2" :step="0.5" />
          <span class="form-tip">%</span>
        </el-form-item>
        <el-form-item v-if="form.calculationMethod === 'FIXED'" label="固定金额">
          <el-input-number v-model="form.fixedAmount" :min="0" :precision="2" />
          <span class="form-tip">元/单</span>
        </el-form-item>
        <el-form-item v-if="form.calculationMethod === 'TIER'" label="阶梯设置">
          <el-tag>请在保存后点击"阶梯"按钮配置阶梯</el-tag>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="100" />
          <span class="form-tip">数字越大优先级越高</span>
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker
            v-model="form.expiryDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 条件配置对话框 -->
    <el-dialog v-model="conditionDialogVisible" title="配置多维度条件" width="900px">
      <el-alert type="info" show-icon :closable="false" style="margin-bottom: 16px">
        只有所有条件都满足时，规则才会生效。例如：技师等级=高级技师 AND 服务金额≥500元
      </el-alert>
      <el-button type="primary" @click="handleAddCondition" style="margin-bottom: 12px">
        <el-icon><Plus /></el-icon>添加条件
      </el-button>
      <el-table :data="conditions" border>
        <el-table-column label="条件类型" width="180">
          <template #default="{ row }">
            <el-select v-model="row.conditionType" placeholder="选择类型" style="width: 100%">
              <el-option
                v-for="opt in conditionTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="条件值" min-width="240">
          <template #default="{ row }">
            <el-input
              v-if="!['service', 'service_category', 'employee_level', 'customer_type', 'member_level', 'weekday', 'time_slot'].includes(row.conditionType)"
              v-model="row.conditionValue"
              placeholder="输入数值"
            />
            <el-select
              v-else-if="row.conditionType === 'weekday'"
              v-model="row.conditionValue"
              placeholder="选择星期"
              style="width: 100%"
            >
              <el-option
                v-for="opt in weekdayOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-select
              v-else-if="row.conditionType === 'customer_type'"
              v-model="row.conditionValue"
              placeholder="选择客户类型"
              style="width: 100%"
            >
              <el-option
                v-for="opt in customerTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-input
              v-else-if="row.conditionType === 'time_slot'"
              v-model="row.conditionValue"
              placeholder="如：9-12 表示9点到12点"
            />
            <el-input-number
              v-else-if="row.conditionType === 'min_amount' || row.conditionType === 'max_amount'"
              v-model="row.conditionValue"
              :min="0"
              :precision="2"
              style="width: 100%"
            />
            <el-input v-else v-model="row.conditionValue" placeholder="输入ID或值" />
          </template>
        </el-table-column>
        <el-table-column label="显示名" width="150">
          <template #default="{ row }">
            <el-input v-model="row.conditionLabel" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="conditions.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="conditionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveConditions">保存条件</el-button>
      </template>
    </el-dialog>

    <!-- 阶梯配置对话框 -->
    <el-dialog v-model="tierDialogVisible" title="配置提成阶梯" width="900px">
      <el-alert type="info" show-icon :closable="false" style="margin-bottom: 16px">
        阶梯式提成：服务金额在哪个区间就应用哪个比例/固定金额。例如：0-3000元→10%, 3000-6000元→15%
      </el-alert>
      <el-button type="primary" @click="handleAddTier" style="margin-bottom: 12px">
        <el-icon><Plus /></el-icon>添加阶梯
      </el-button>
      <el-table :data="tiers" border>
        <el-table-column label="起始金额(元)" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.tierStart" :min="0" :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="结束金额(元)" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.tierEnd" :min="0" :precision="2" placeholder="留空=不封顶" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="计算方式" width="120">
          <template #default="{ row }">
            <el-select v-model="row.calculationMethod" style="width: 100%">
              <el-option label="比例" value="rate" />
              <el-option label="固定" value="fixed" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="比例/金额" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.rate"
              :min="0"
              :max="row.calculationMethod === 'rate' ? 100 : undefined"
              :precision="2"
              :step="row.calculationMethod === 'rate' ? 0.5 : 10"
              style="width: 100%"
            />
            <span class="form-tip">{{ row.calculationMethod === 'rate' ? '%' : '元' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.description" placeholder="阶梯说明" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="tiers.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="tierDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTiers">保存阶梯</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  commissionRuleApi,
  conditionTypeOptions,
  calculationMethodOptions,
  weekdayOptions,
  customerTypeOptions
} from '@/api/commission-rule'

const tableData = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const filterStatus = ref<number | null>(null)
const conditionCountMap = ref<Record<number, number>>({})
const tierCountMap = ref<Record<number, number>>({})

const dialogVisible = ref(false)
const dialogTitle = ref('新增规则')
const currentRuleId = ref<number | null>(null)

const form = reactive<any>({
  id: null,
  ruleName: '',
  businessType: 'SERVICE',
  targetId: null,
  calculationMethod: 'RATE',
  rate: 0.10,
  fixedAmount: null,
  priority: 0,
  effectiveDate: new Date().toISOString().slice(0, 10),
  expiryDate: null,
  remark: '',
  status: 1,
  isActive: 1
})

const ratePercent = computed({
  get: () => form.rate ? (form.rate * 100).toFixed(2) : 0,
  set: (val) => { form.rate = Number(val) / 100 }
})

const conditionDialogVisible = ref(false)
const conditions = ref<any[]>([])

const tierDialogVisible = ref(false)
const tiers = ref<any[]>([])

const loadRules = async () => {
  loading.value = true
  try {
    // 使用现有 CommissionController
    const res: any = await import('@/api/request').then(m => m.default.get('/commission/rules/list'))
    let list = res.data || []
    if (keyword.value) {
      list = list.filter((r: any) => r.ruleName?.includes(keyword.value))
    }
    if (filterStatus.value !== null) {
      list = list.filter((r: any) => r.status === filterStatus.value)
    }
    tableData.value = list

    // 加载每个规则的条件数与阶梯数
    for (const rule of list) {
      try {
        const condRes: any = await commissionRuleApi.conditionsByRule(rule.id)
        conditionCountMap.value[rule.id] = (condRes.data || []).length
      } catch (e) {}
      try {
        const tierRes: any = await commissionRuleApi.tiersByRule(rule.id)
        tierCountMap.value[rule.id] = (tierRes.data || []).length
      } catch (e) {}
    }
  } catch (e) {
    console.error('加载规则失败', e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, {
    id: null,
    ruleName: '',
    businessType: 'SERVICE',
    targetId: null,
    calculationMethod: 'RATE',
    rate: 0.10,
    fixedAmount: null,
    priority: 0,
    effectiveDate: new Date().toISOString().slice(0, 10),
    expiryDate: null,
    remark: '',
    status: 1,
    isActive: 1
  })
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增规则'
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  resetForm()
  Object.assign(form, row)
  dialogTitle.value = '编辑规则'
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!form.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }
  try {
    const request = (await import('@/api/request')).default
    if (form.id) {
      await request.put(`/commission/rule/${form.id}`, form)
    } else {
      await request.post('/commission/rule', form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除规则"${row.ruleName}"吗？`, '提示', { type: 'warning' })
    const request = (await import('@/api/request')).default
    await request.delete(`/commission/rule/${row.id}`)
    ElMessage.success('删除成功')
    loadRules()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleConfigCondition = async (row: any) => {
  currentRuleId.value = row.id
  try {
    const res: any = await commissionRuleApi.conditionsByRule(row.id)
    conditions.value = res.data || []
    if (!conditions.value.length) {
      conditions.value = [{
        ruleId: row.id,
        conditionType: 'employee_level',
        operator: 'eq',
        conditionValue: '',
        conditionLabel: '',
        sortOrder: 0
      }]
    }
    conditionDialogVisible.value = true
  } catch (e) {
    console.error('加载条件失败', e)
  }
}

const handleAddCondition = () => {
  conditions.value.push({
    ruleId: currentRuleId.value,
    conditionType: 'min_amount',
    operator: 'eq',
    conditionValue: '',
    conditionLabel: '',
    sortOrder: conditions.value.length
  })
}

const handleSaveConditions = async () => {
  try {
    await commissionRuleApi.saveConditions(currentRuleId.value!, conditions.value)
    ElMessage.success('保存成功')
    conditionDialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleConfigTier = async (row: any) => {
  currentRuleId.value = row.id
  try {
    const res: any = await commissionRuleApi.tiersByRule(row.id)
    tiers.value = res.data || []
    if (!tiers.value.length) {
      tiers.value = [{
        ruleId: row.id,
        tierStart: 0,
        tierEnd: 1000,
        calculationMethod: 'rate',
        rate: 10,
        sortOrder: 0,
        description: ''
      }]
    }
    tierDialogVisible.value = true
  } catch (e) {
    console.error('加载阶梯失败', e)
  }
}

const handleAddTier = () => {
  tiers.value.push({
    ruleId: currentRuleId.value,
    tierStart: 0,
    tierEnd: null,
    calculationMethod: 'rate',
    rate: 10,
    sortOrder: tiers.value.length,
    description: ''
  })
}

const handleSaveTiers = async () => {
  try {
    await commissionRuleApi.saveTiers(currentRuleId.value!, tiers.value)
    ElMessage.success('保存成功')
    tierDialogVisible.value = false
    loadRules()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadRules()
})
</script>

<style scoped>
.commission-rule-page {
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

.text-muted {
  color: #c0c4cc;
}
</style>
