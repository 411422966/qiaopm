<template>
  <div class="simulation-page">
    <el-row :gutter="20">
      <!-- 左侧：参数输入 -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <span>模拟参数</span>
          </template>
          <el-form :model="form" label-width="100px">
            <el-form-item label="技师">
              <el-select
                v-model="form.employeeId"
                placeholder="选择技师"
                filterable
                style="width: 100%"
                @change="onEmployeeChange"
              >
                <el-option
                  v-for="emp in employeeList"
                  :key="emp.id"
                  :label="emp.employeeName"
                  :value="emp.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="技师等级">
              <el-select v-model="form.employeeLevelId" placeholder="选择等级" style="width: 100%">
                <el-option
                  v-for="level in levelList"
                  :key="level.id"
                  :label="level.levelName"
                  :value="level.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="服务ID">
              <el-input-number v-model="form.serviceId" :min="0" placeholder="可留空" style="width: 100%" />
            </el-form-item>
            <el-form-item label="服务金额">
              <el-input-number v-model="form.serviceAmount" :min="0" :precision="2" :step="100" style="width: 100%" />
              <span class="form-tip">元</span>
            </el-form-item>
            <el-form-item label="客户类型">
              <el-select v-model="form.customerType" placeholder="客户类型" style="width: 100%">
                <el-option
                  v-for="opt in customerTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="会员等级">
              <el-input v-model="form.memberLevel" placeholder="如：金卡" />
            </el-form-item>
            <el-form-item label="模拟日期">
              <el-date-picker
                v-model="form.simulationDate"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleSimulate">
              <el-icon><Aim /></el-icon>
              开始模拟计算
            </el-button>
          </el-form>
        </el-card>

        <el-card style="margin-top: 16px">
          <template #header>
            <span>最近模拟记录</span>
          </template>
          <el-table :data="recentList" border size="small" max-height="300">
            <el-table-column label="服务金额" width="100">
              <template #default="{ row }">
                ¥{{ row.serviceAmount }}
              </template>
            </el-table-column>
            <el-table-column label="提成金额" width="100">
              <template #default="{ row }">
                <span style="color: #67C23A; font-weight: bold">¥{{ row.commissionAmount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="规则" min-width="150" show-overflow-tooltip prop="matchedRuleName" />
            <el-table-column label="日期" width="100" prop="simulationDate" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：计算结果 -->
      <el-col :span="14">
        <el-card v-if="result">
          <template #header>
            <span>计算结果</span>
          </template>

          <div v-if="!result.matched" class="no-match">
            <el-result icon="warning" title="未匹配到规则" :sub-title="result.message">
              <template #extra>
                <el-text type="info">
                  共检测到 {{ result.candidatesCount }} 条候选规则，但当前条件都不满足
                </el-text>
              </template>
            </el-result>
          </div>

          <div v-else>
            <!-- 大数字 -->
            <div class="result-hero">
              <div class="hero-label">预期提成金额</div>
              <div class="hero-value">¥{{ result.commissionAmount }}</div>
              <div class="hero-sub">
                服务金额 ¥{{ form.serviceAmount }} ·
                {{ calcMethodLabel }} ·
                命中率 {{ result.totalMatches }}
              </div>
            </div>

            <!-- 命中的规则 -->
            <el-card class="info-card" shadow="never">
              <template #header>
                <span>命中规则</span>
              </template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="规则ID">{{ result.rule.id }}</el-descriptions-item>
                <el-descriptions-item label="规则名称">{{ result.rule.ruleName }}</el-descriptions-item>
                <el-descriptions-item label="业务类型">{{ result.rule.businessType }}</el-descriptions-item>
                <el-descriptions-item label="优先级">{{ result.rule.priority }}</el-descriptions-item>
                <el-descriptions-item label="计算方式">
                  <el-tag>{{ calcMethodLabel }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="目标">
                  <span v-if="result.rule.targetId">ID: {{ result.rule.targetId }}</span>
                  <el-tag v-else type="info" size="small">通用</el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <!-- 命中的条件 -->
            <el-card v-if="result.conditions && result.conditions.length" class="info-card" shadow="never" style="margin-top: 12px">
              <template #header>
                <span>命中条件（{{ result.conditions.length }}条）</span>
              </template>
              <el-table :data="result.conditions" border size="small">
                <el-table-column label="条件类型" width="150">
                  <template #default="{ row }">
                    {{ getConditionLabel(row.conditionType) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作符" prop="operator" width="100" />
                <el-table-column label="条件值" prop="conditionValue" />
                <el-table-column label="显示名" prop="conditionLabel" />
              </el-table>
            </el-card>

            <!-- 阶梯详情 -->
            <el-card v-if="result.tiers && result.tiers.length" class="info-card" shadow="never" style="margin-top: 12px">
              <template #header>
                <span>阶梯设置</span>
              </template>
              <el-table :data="result.tiers" border size="small">
                <el-table-column label="区间(元)" width="200">
                  <template #default="{ row }">
                    {{ row.tierStart }} - {{ row.tierEnd || '∞' }}
                  </template>
                </el-table-column>
                <el-table-column label="方式" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.calculationMethod === 'rate'" type="success">比例</el-tag>
                    <el-tag v-else type="warning">固定</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="比例/金额" width="120">
                  <template #default="{ row }">
                    <span v-if="row.calculationMethod === 'rate'">{{ row.rate }}%</span>
                    <span v-else>¥{{ row.rate }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="说明" prop="description" />
              </el-table>
            </el-card>

            <!-- 计算明细 -->
            <el-card class="info-card" shadow="never" style="margin-top: 12px">
              <template #header>
                <span>计算明细</span>
              </template>
              <el-alert :title="result.detail" type="success" :closable="false" />
            </el-card>
          </div>
        </el-card>

        <el-card v-else>
          <el-empty description="请设置参数后点击'开始模拟计算'" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim } from '@element-plus/icons-vue'
import {
  commissionRuleApi,
  conditionTypeOptions,
  calculationMethodOptions,
  customerTypeOptions
} from '@/api/commission-rule'

const form = reactive({
  employeeId: null as number | null,
  employeeLevelId: null as number | null,
  serviceId: null as number | null,
  serviceAmount: 1000,
  customerType: '',
  memberLevel: '',
  simulationDate: new Date().toISOString().slice(0, 10),
  storeId: 1
})

const result = ref<any>(null)
const loading = ref(false)
const employeeList = ref<any[]>([])
const levelList = ref<any[]>([])
const recentList = ref<any[]>([])

const calcMethodLabel = computed(() => {
  if (!result.value?.calculationMethod) return ''
  const opt = calculationMethodOptions.find(o => o.value === result.value.calculationMethod)
  return opt ? opt.label : result.value.calculationMethod
})

const getConditionLabel = (type: string) => {
  const opt = conditionTypeOptions.find(o => o.value === type)
  return opt ? opt.label : type
}

const onEmployeeChange = async (empId: number) => {
  if (!empId) return
  // 加载该员工的最近模拟
  loadRecent(empId)
}

const loadRecent = async (employeeId: number) => {
  try {
    const res: any = await commissionRuleApi.recentSimulations(employeeId)
    recentList.value = res.data || []
  } catch (e) {
    recentList.value = []
  }
}

const loadEmployees = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/employee/list')
    employeeList.value = res.data || []
  } catch (e) {
    console.error('加载员工失败', e)
  }
}

const loadLevels = async () => {
  try {
    const request = (await import('@/api/request')).default
    const res: any = await request.get('/technician/level/active')
    levelList.value = res.data || []
  } catch (e) {
    console.error('加载等级失败', e)
  }
}

const handleSimulate = async () => {
  if (!form.serviceAmount) {
    ElMessage.warning('请输入服务金额')
    return
  }
  loading.value = true
  try {
    const res: any = await commissionRuleApi.simulate({
      employeeId: form.employeeId || undefined,
      employeeLevelId: form.employeeLevelId || undefined,
      serviceId: form.serviceId || undefined,
      serviceAmount: form.serviceAmount,
      customerType: form.customerType || undefined,
      memberLevel: form.memberLevel || undefined,
      simulationDate: form.simulationDate,
      storeId: form.storeId
    })
    result.value = res.data
    if (form.employeeId) {
      loadRecent(form.employeeId)
    }
  } catch (e) {
    ElMessage.error('模拟计算失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadEmployees()
  loadLevels()
})
</script>

<style scoped>
.simulation-page {
  padding: 20px;
}

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.no-match {
  padding: 40px 0;
}

.result-hero {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 12px;
  padding: 32px;
  text-align: center;
  color: #fff;
  margin-bottom: 16px;
}

.hero-label {
  font-size: 14px;
  opacity: 0.9;
}

.hero-value {
  font-size: 48px;
  font-weight: bold;
  margin: 8px 0;
}

.hero-sub {
  font-size: 14px;
  opacity: 0.9;
}

.info-card {
  margin-top: 12px;
}
</style>
