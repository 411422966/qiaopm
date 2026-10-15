<template>
  <div class="points-page">
    <el-card class="search-bar">
      <el-button type="primary" @click="openCreate">+ 新增规则</el-button>
      <el-button type="success" @click="loadData">刷新</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rules" v-loading="loading" border>
        <el-table-column prop="ruleName" label="规则名称" min-width="200" />
        <el-table-column label="消费比例" width="120">
          <template #default="{ row }">{{ row.consumeRate }} 分/元</template>
        </el-table-column>
        <el-table-column label="储值倍率" width="120">
          <template #default="{ row }">× {{ row.rechargeMultiplier }}</template>
        </el-table-column>
        <el-table-column label="活动倍率" width="120">
          <template #default="{ row }">× {{ row.promotionMultiplier }}</template>
        </el-table-column>
        <el-table-column label="积分兑现金" width="120">
          <template #default="{ row }">{{ row.pointsToCash }} 元/分</template>
        </el-table-column>
        <el-table-column label="单笔上限" width="120">
          <template #default="{ row }">{{ row.maxUsePercent }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openAward">手动发分</el-button>
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑规则' : '新增规则'" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.ruleName" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="消费比例">
              <el-input-number v-model="form.consumeRate" :min="0" :step="0.5" :precision="2" style="width: 100%" />
              <div class="form-tip">消费 1 元积 X 分</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="储值倍率">
              <el-input-number v-model="form.rechargeMultiplier" :min="0" :step="0.5" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="活动倍率">
              <el-input-number v-model="form.promotionMultiplier" :min="0" :step="0.5" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="积分兑现金">
              <el-input-number v-model="form.pointsToCash" :min="0" :step="0.01" :precision="2" style="width: 100%" />
              <div class="form-tip">X 分 = 1 元</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="单笔上限">
          <el-input-number v-model="form.maxUsePercent" :min="0" :max="100" :step="5" :precision="2" style="width: 100%" />
          <div class="form-tip">单笔最多可抵扣比例</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-radio-group v-model="form.enabled">
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

    <el-dialog v-model="awardDialog.visible" title="手动发分" width="500px" @closed="resetAward">
      <el-form :model="awardForm" label-width="100px">
        <el-form-item label="会员ID" required>
          <el-input-number v-model="awardForm.memberId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="消费金额">
          <el-input-number v-model="awardForm.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="awardForm.sourceType" style="width: 100%">
            <el-option label="消费" value="CONSUME" />
            <el-option label="储值" value="RECHARGE" />
            <el-option label="人工" value="MANUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="awardForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="awardDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="runAward" :loading="awardDialog.running">发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const rules = ref<any[]>([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/mkt/points/rule/list')
    rules.value = r.data || []
  } finally {
    loading.value = false
  }
}

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive<any>({
  ruleName: '',
  consumeRate: 1.0,
  rechargeMultiplier: 1.0,
  promotionMultiplier: 1.0,
  pointsToCash: 0.01,
  maxUsePercent: 30.0,
  enabled: 1,
  priority: 0
})

function openCreate() { dialog.id = undefined; dialog.visible = true }
function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, row)
  dialog.visible = true
}

function resetDialog() {
  Object.assign(form, {
    ruleName: '',
    consumeRate: 1.0,
    rechargeMultiplier: 1.0,
    promotionMultiplier: 1.0,
    pointsToCash: 0.01,
    maxUsePercent: 30.0,
    enabled: 1,
    priority: 0
  })
}

async function save() {
  if (!form.ruleName) return ElMessage.warning('名称必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (dialog.id) {
      await http.put(`/mkt/points/rule/${dialog.id}`, form, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/mkt/points/rule', form, { headers })
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

const awardDialog = reactive({ visible: false, running: false })
const awardForm = reactive<any>({
  memberId: undefined,
  amount: 100,
  sourceType: 'MANUAL',
  remark: ''
})

function openAward() {
  awardDialog.visible = true
}

function resetAward() {
  awardForm.memberId = undefined
  awardForm.amount = 100
  awardForm.sourceType = 'MANUAL'
  awardForm.remark = ''
}

async function runAward() {
  if (!awardForm.memberId) return ElMessage.warning('会员ID必填')
  if (!awardForm.amount) return ElMessage.warning('金额必填')
  awardDialog.running = true
  try {
    const r = await http.post('/mkt/points/award', awardForm)
    ElMessage.success(`已发放 ${r.data.points} 分`)
    awardDialog.visible = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发放失败')
  } finally {
    awardDialog.running = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.form-tip { font-size: 12px; color: #94a3b8; margin-top: 4px; }
</style>
