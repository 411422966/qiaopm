<template>
  <el-dialog
    :model-value="modelValue"
    title="双人核销复核"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
      <el-descriptions-item label="核销类型">
        {{ verifyTypeLabel }}
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag v-if="record.verifyStatus === 'PENDING'" type="warning" size="small">待复核</el-tag>
        <el-tag v-else-if="record.verifyStatus === 'VERIFIED'" type="success" size="small">已通过</el-tag>
        <el-tag v-else-if="record.verifyStatus === 'REJECTED'" type="danger" size="small">已驳回</el-tag>
        <el-tag v-else type="info" size="small">无需复核</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="主核销人">
        {{ record.mainVerifierName || '-' }} (ID: {{ record.mainVerifierId }})
      </el-descriptions-item>
      <el-descriptions-item label="金额">
        {{ record.totalAmount ? `¥${record.totalAmount}` : '盘点场景（无金额）' }}
      </el-descriptions-item>
      <el-descriptions-item label="阈值" v-if="record.threshold">
        ¥{{ record.threshold }}
      </el-descriptions-item>
      <el-descriptions-item label="关联单号" v-if="record.stockCheckId">
        盘点单 #{{ record.stockCheckId }}
      </el-descriptions-item>
    </el-descriptions>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" v-if="record.verifyStatus === 'PENDING'">
      <el-form-item label="复核人" prop="reviewerId">
        <el-select
          v-model="form.reviewerId"
          placeholder="请选择复核人（≠ 主核销人）"
          filterable
          style="width: 100%"
          :loading="employeeLoading"
        >
          <el-option
            v-for="emp in employeeOptions"
            :key="emp.id"
            :label="emp.name + (emp.code ? ` (${emp.code})` : '')"
            :value="emp.id"
            :disabled="emp.id === record.mainVerifierId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="GPS/指纹" v-if="collectGps">
        <el-input
          v-model="form.reviewerGps"
          placeholder="可选：移动端会自动采集 {lat,lng,fingerprint,ts}"
        />
      </el-form-item>
      <el-form-item label="操作" prop="action">
        <el-radio-group v-model="form.action">
          <el-radio value="VERIFY">通过</el-radio>
          <el-radio value="REJECT">驳回（需走冲正）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="驳回原因" v-if="form.action === 'REJECT'" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请填写驳回原因" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button
        v-if="record.verifyStatus === 'PENDING'"
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        提交复核
      </el-button>
      <el-button v-else type="info" plain @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import stockApi from '@/api/stock'
import request from '@/api/request'

const props = defineProps<{
  modelValue: boolean
  record: any
  /** 是否采集 GPS（移动端为 true，PC 为 false） */
  collectGps?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const employeeLoading = ref(false)
const employeeOptions = ref<any[]>([])

const form = reactive({
  reviewerId: null as number | null,
  reviewerName: '',
  reviewerGps: '',
  action: 'VERIFY' as 'VERIFY' | 'REJECT',
  remark: ''
})

const rules: FormRules = {
  reviewerId: [{ required: true, message: '请选择复核人', trigger: 'change' }],
  action: [{ required: true, message: '请选择操作', trigger: 'change' }],
  remark: [
    {
      validator: (_r: any, v: string, cb: (err?: Error) => void) => {
        if (form.action === 'REJECT' && !v) cb(new Error('驳回时必须填写原因'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const verifyTypeLabel = computed(() => {
  const map: Record<string, string> = {
    CHECK: '盘点确认',
    OUTBOUND: '出库',
    CONSUMPTION: '订单消耗'
  }
  return map[props.record.verifyType] || props.record.verifyType || '-'
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      // 重置表单
      form.reviewerId = null
      form.reviewerName = ''
      form.reviewerGps = ''
      form.action = 'VERIFY'
      form.remark = ''
      loadEmployees()
    }
  }
)

async function loadEmployees() {
  employeeLoading.value = true
  try {
    // 从员工列表接口拉（员工下拉必须满足 stock:check 权限）
    const resp: any = await request.get('/employee/list', { params: { pageSize: 500, status: 1 } })
    const list = resp?.data?.records || resp?.data || []
    employeeOptions.value = list
  } catch (err) {
    ElMessage.warning('员工列表加载失败，请刷新重试')
  } finally {
    employeeLoading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const emp = employeeOptions.value.find((e) => e.id === form.reviewerId)
    if (!emp) {
      ElMessage.error('所选员工不存在')
      return
    }
    submitting.value = true
    try {
      await stockApi.dualVerify({
        dualVerifyId: props.record.id,
        action: form.action,
        reviewerId: form.reviewerId!,
        reviewerName: emp.name,
        reviewerGps: form.reviewerGps || undefined,
        remark: form.action === 'REJECT' ? form.remark : undefined
      })
      ElMessage.success(form.action === 'VERIFY' ? '复核通过' : '已驳回')
      emit('success')
      emit('update:modelValue', false)
    } catch (err: any) {
      ElMessage.error(err?.message || '提交失败')
    } finally {
      submitting.value = false
    }
  })
}
</script>
