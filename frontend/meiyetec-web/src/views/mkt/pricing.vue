<template>
  <div class="page-pricing">
    <el-tabs v-model="tab">
      <el-tab-pane label="规则列表" name="rules">
        <el-form :inline="true">
          <el-form-item label="规则类型">
            <el-select v-model="query.ruleType" style="width:140px" clearable>
              <el-option v-for="t in ruleTypes" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="load">查询</el-button>
            <el-button type="success" @click="openAdd">新增规则</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="rules" stripe>
          <el-table-column prop="ruleCode" label="编码" />
          <el-table-column prop="ruleName" label="名称" />
          <el-table-column prop="ruleType" label="类型" />
          <el-table-column prop="targetType" label="目标" />
          <el-table-column prop="pricingMode" label="模式" />
          <el-table-column prop="pricingValue" label="值" />
          <el-table-column prop="priority" label="优先级" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :total="total" layout="total,sizes,prev,pager,next" @current-change="load" />
      </el-tab-pane>

      <!-- M14 技师等级溢价配置 -->
      <el-tab-pane label="技师等级溢价" name="tech-level">
        <div class="tech-level-header">
          <el-alert type="info" :closable="false" style="margin-bottom:12px">
            <template #title>
              <b>M14 热门技师溢价规则</b>：按技师等级设置服务加价百分比。
              溢价在基价上叠加，不受其他折扣规则影响。
            </template>
          </el-alert>
          <el-button type="primary" @click="saveTechPremium" :loading="savingLevel">
            💾 保存配置
          </el-button>
        </div>
        <el-table :data="techLevels" stripe>
          <el-table-column prop="levelCode" label="等级编码" width="120" />
          <el-table-column label="等级名称" width="160">
            <template #default="{ row }">
              <el-tag :style="{ backgroundColor: row.levelColor || '#409eff', color: '#fff', border: 'none' }">
                {{ row.levelName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="服务折扣率" width="120">
            <template #default="{ row }">
              {{ row.discountRate != null ? (Number(row.discountRate) * 100).toFixed(1) + '%' : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="提成加成" width="100">
            <template #default="{ row }">
              {{ row.commissionBonus != null ? (Number(row.commissionBonus) * 100).toFixed(1) + '%' : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="溢价模式" width="160">
            <template #default="{ row }">
              <el-select v-model="row.premiumMode" style="width:140px" size="small">
                <el-option label="百分比溢价 PREMIUM" value="PREMIUM" />
                <el-option label="固定加价 PREMIUM_FIXED" value="PREMIUM_FIXED" />
                <el-option label="无溢价" value="" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="溢价值（%）" width="160">
            <template #default="{ row }">
              <el-input-number
                v-model="row.premiumValue"
                :min="0" :max="999" :step="5" :precision="1"
                size="small" style="width:140px"
                :disabled="!row.premiumMode"
                placeholder="溢价%" />
            </template>
          </el-table-column>
          <el-table-column label="示例服务价（¥100基价）">
            <template #default="{ row }">
              <span v-if="row.premiumMode === 'PREMIUM' && row.premiumValue > 0">
                ¥{{ (100 * (1 + Number(row.premiumValue) / 100)).toFixed(2) }}
              </span>
              <span v-else-if="row.premiumMode === 'PREMIUM_FIXED' && row.premiumValue > 0">
                ¥{{ (100 + Number(row.premiumValue)).toFixed(2) }}
              </span>
              <span v-else style="color:#909399">¥100.00</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="收银台报价" name="quote">
        <el-form :inline="true">
          <el-form-item label="项目ID"><el-input v-model.number="form.projectId" style="width:140px" /></el-form-item>
          <el-form-item label="技师ID"><el-input v-model.number="form.technicianId" style="width:140px" /></el-form-item>
          <el-form-item label="会员ID"><el-input v-model.number="form.memberId" style="width:140px" /></el-form-item>
          <el-form-item label="门店ID"><el-input v-model.number="form.storeId" style="width:140px" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="quote" :loading="loading">报价</el-button>
          </el-form-item>
        </el-form>
        <el-card v-if="result">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="原价">¥{{ result.baseAmount }}</el-descriptions-item>
            <el-descriptions-item label="最终价">¥{{ result.finalAmount }}</el-descriptions-item>
            <el-descriptions-item label="优惠">¥{{ result.discountAmount }}</el-descriptions-item>
          </el-descriptions>
          <el-divider />
          <el-table :data="result.hitRules || []" stripe>
            <el-table-column prop="ruleCode" label="编码" />
            <el-table-column prop="ruleName" label="名称" />
            <el-table-column prop="pricingMode" label="模式" />
            <el-table-column prop="pricingValue" label="值" />
            <el-table-column prop="delta" label="影响金额" />
            <el-table-column prop="description" label="说明" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="addVisible" title="新增定价规则" width="540">
      <el-form label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.ruleCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.ruleName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.ruleType" style="width:100%">
            <el-option v-for="t in ruleTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="form.targetType" style="width:100%">
            <el-option v-for="t in targetTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标编码"><el-input v-model="form.targetCode" /></el-form-item>
        <el-form-item label="计算模式">
          <el-select v-model="form.pricingMode" style="width:100%">
            <el-option label="百分比 PERCENT" value="PERCENT" />
            <el-option label="固定 FIXED" value="FIXED" />
            <el-option label="倍数 MULTIPLIER" value="MULTIPLIER" />
            <el-option label="技师溢价 PREMIUM" value="PREMIUM" />
            <el-option label="固定加价 PREMIUM_FIXED" value="PREMIUM_FIXED" />
          </el-select>
        </el-form-item>
        <el-form-item label="值"><el-input v-model.number="form.pricingValue" /></el-form-item>
        <el-form-item label="优先级"><el-input v-model.number="form.priority" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible=false">取消</el-button>
        <el-button type="primary" @click="submitAdd">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const tab = ref('rules')
const ruleTypes = ['TECHNICIAN','TIME_SLOT','PROJECT','MEMBER_LEVEL','STORE','TECHNICIAN_LEVEL']
const targetTypes = ['TECHNICIAN','TIME_SLOT','PROJECT','MEMBER_LEVEL','STORE','TECHNICIAN_LEVEL']
const query = reactive({ ruleType: '', pageNum: 1, pageSize: 20 })
const rules = ref([])
const total = ref(0)

// M14 技师等级溢价
const techLevels = ref([])
const savingLevel = ref(false)

async function loadTechLevels() {
  try {
    const { data } = await http.get('/mkt/pricing/rule/technician-level/levels')
    techLevels.value = data || []
  } catch (e) {
    techLevels.value = []
  }
}

async function saveTechPremium() {
  savingLevel.value = true
  try {
    const payload = techLevels.value
      .filter(l => l.premiumMode && l.premiumValue != null)
      .map(l => ({
        levelId: l.id,
        premiumMode: l.premiumMode,
        premiumValue: l.premiumValue
      }))
    const { data } = await http.post('/mkt/pricing/rule/technician-level/batch', payload)
    ElMessage.success(`已保存：新建 ${data.created} 条，更新 ${data.updated} 条`)
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || ''))
  } finally {
    savingLevel.value = false
  }
}

const form = reactive({ ruleCode: '', ruleName: '', ruleType: 'TECHNICIAN', targetType: 'TECHNICIAN',
  targetCode: '', pricingMode: 'PERCENT', pricingValue: 10, priority: 50 })
const addVisible = ref(false)

const result = ref(null)
const loading = ref(false)

async function load() {
  const { data } = await http.get('/mkt/pricing/rule/page', { params: query })
  rules.value = data.records || []
  total.value = data.total || 0
}
function openAdd() {
  addVisible.value = true
}
async function submitAdd() {
  await http.post('/mkt/pricing/rule', form)
  ElMessage.success('已创建')
  addVisible.value = false
  load()
}
async function quote() {
  loading.value = true
  try {
    const { data } = await http.post('/mkt/pricing/quote', form)
    result.value = data
  } finally { loading.value = false }
}
onMounted(() => { load(); loadTechLevels() })
</script>

<style scoped>
.page-pricing { padding: 16px; }
.tech-level-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
</style>