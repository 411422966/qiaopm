<template>
  <div class="rule-list-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>提成规则列表</span>
          <el-button type="primary" @click="$router.push('/commission/rules')">
            <el-icon><Setting /></el-icon>
            规则配置
          </el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col v-for="rule in tableData" :key="rule.id" :span="12" style="margin-bottom: 16px">
          <el-card shadow="hover" class="rule-card">
            <div class="rule-header">
              <div>
                <span class="rule-name">{{ rule.ruleName }}</span>
                <el-tag v-if="rule.status === 1" type="success" size="small" style="margin-left: 8px">启用</el-tag>
                <el-tag v-else type="info" size="small" style="margin-left: 8px">停用</el-tag>
              </div>
              <el-tag size="small">{{ getMethodLabel(rule.calculationMethod) }}</el-tag>
            </div>
            <el-descriptions :column="2" size="small" style="margin-top: 12px">
              <el-descriptions-item label="业务类型">{{ rule.businessType }}</el-descriptions-item>
              <el-descriptions-item label="优先级">{{ rule.priority }}</el-descriptions-item>
              <el-descriptions-item label="生效日期">{{ rule.effectiveDate }}</el-descriptions-item>
              <el-descriptions-item label="失效日期">
                <span v-if="rule.expiryDate">{{ rule.expiryDate }}</span>
                <span v-else class="text-muted">长期</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="rule.calculationMethod === 'RATE'" label="提成比例">
                {{ rule.rate ? (rule.rate * 100).toFixed(1) : 0 }}%
              </el-descriptions-item>
              <el-descriptions-item v-if="rule.calculationMethod === 'FIXED'" label="固定金额">
                ¥{{ rule.fixedAmount || 0 }}
              </el-descriptions-item>
              <el-descriptions-item v-if="rule.calculationMethod === 'TIER'" label="阶梯设置">
                <el-link type="primary" @click="$router.push('/commission/rules')">阶梯模式</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">
                <span v-if="rule.remark">{{ rule.remark }}</span>
                <span v-else class="text-muted">-</span>
              </el-descriptions-item>
            </el-descriptions>
            <div class="rule-footer">
              <el-button type="primary" link size="small" @click="handleSimulate(rule)">试算</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="!tableData.length" description="暂无提成规则" />
    </el-card>

    <!-- 试算对话框 -->
    <el-dialog v-model="simulateDialogVisible" title="快速试算" width="500px">
      <el-form :model="simulateForm" label-width="100px">
        <el-form-item label="规则">
          <span>{{ currentRule?.ruleName }}</span>
        </el-form-item>
        <el-form-item label="服务金额">
          <el-input-number v-model="simulateForm.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div v-if="simulateResult" class="simulate-result">
        <el-alert :title="`提成金额：¥${simulateResult.commissionAmount}`" type="success" :closable="false" />
        <div style="margin-top: 12px; color: #606266">{{ simulateResult.detail }}</div>
      </div>
      <template #footer>
        <el-button @click="simulateDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="doSimulate">计算</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { commissionRuleApi, calculationMethodOptions } from '@/api/commission-rule'

const tableData = ref<any[]>([])
const currentRule = ref<any>(null)
const simulateDialogVisible = ref(false)
const simulateForm = reactive({ amount: 1000 })
const simulateResult = ref<any>(null)

const getMethodLabel = (method: string) => {
  const opt = calculationMethodOptions.find(o => o.value === method)
  return opt ? opt.label : method
}

const loadData = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/commission/rule/list')
    tableData.value = res.data || []
  } catch (e) {
    console.error('加载失败', e)
  }
}

const handleSimulate = (rule: any) => {
  currentRule.value = rule
  simulateForm.amount = 1000
  simulateResult.value = null
  simulateDialogVisible.value = true
}

const doSimulate = async () => {
  if (!currentRule.value || !simulateForm.amount) return
  try {
    const res: any = await commissionRuleApi.simulate({
      serviceId: currentRule.value.targetId || undefined,
      serviceAmount: simulateForm.amount,
      storeId: 1
    })
    simulateResult.value = res.data
  } catch (e) {
    ElMessage.error('计算失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.rule-list-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rule-card {
  transition: all 0.3s;
}

.rule-card:hover {
  transform: translateY(-2px);
}

.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rule-name {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.rule-footer {
  margin-top: 12px;
  text-align: right;
}

.text-muted {
  color: #c0c4cc;
}

.simulate-result {
  margin-top: 16px;
}
</style>
