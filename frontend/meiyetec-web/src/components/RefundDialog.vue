<template>
  <el-dialog
    v-model="visible"
    title="退款单"
    width="640px"
    :close-on-click-modal="false"
    @close="onClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="原订单">
        <span class="readonly">#{{ order?.id }} / {{ order?.orderNo }}</span>
        <span class="ml-2">实付 ¥{{ order?.paidAmount }} · 已退 ¥{{ order?.refundedAmount || 0 }}</span>
      </el-form-item>
      <el-form-item label="可退金额">
        <span class="readonly">¥{{ refAble }}</span>
      </el-form-item>
      <el-form-item label="退款金额" prop="refundAmount">
        <el-input-number v-model="form.refundAmount" :min="0.01" :max="refAble" :precision="2" :step="50" />
      </el-form-item>
      <el-form-item label="退款策略" prop="strategy">
        <el-select v-model="form.strategy" placeholder="选择退款策略">
          <el-option label="原路退回" value="ORIGINAL" />
          <el-option label="储值返还" value="WALLET" />
          <el-option label="混合（部分原路 + 部分储值）" value="MIXED" />
        </el-select>
      </el-form-item>
      <template v-if="form.strategy === 'MIXED'">
        <el-form-item label="储值返还金额" prop="walletAmount">
          <el-input-number v-model="form.walletAmount" :min="0" :max="form.refundAmount" :precision="2" :step="50" />
        </el-form-item>
        <el-form-item label="原路退款金额">
          <span>¥{{ (form.refundAmount - (form.walletAmount || 0)).toFixed(2) }}</span>
        </el-form-item>
      </template>
      <el-form-item label="退款原因">
        <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>
      <el-form-item label="幂等键">
        <span class="mono">{{ idemKey }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <SubmitButton
        name="refund-create"
        type="primary"
        @submit="onSubmit"
        @duplicate="onDuplicate"
      >
        提交退款
      </SubmitButton>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import SubmitButton from '@/components/SubmitButton.vue'
import { refundApi } from '@/api/pos'
import { newIdempotencyKey } from '@/utils/idempotency'

const props = defineProps<{
  modelValue: boolean
  order: any | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success', refund: any): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const formRef = ref<FormInstance>()
const form = reactive({
  refundAmount: 0,
  strategy: 'ORIGINAL',
  reason: '',
  walletAmount: 0
})

const refAble = computed(() => {
  if (!props.order) return 0
  const paid = Number(props.order.paidAmount || 0)
  const refunded = Number(props.order.refundedAmount || 0)
  return Math.max(0, paid - refunded)
})

const idemKey = ref(newIdempotencyKey())

const rules: FormRules = {
  refundAmount: [{ required: true, message: '请输入退款金额', trigger: 'blur' }],
  strategy: [{ required: true, message: '请选择策略', trigger: 'change' }]
}

watch(() => props.modelValue, (v) => {
  if (v && props.order) {
    Object.assign(form, {
      refundAmount: refAble.value,
      strategy: 'ORIGINAL',
      reason: '',
      walletAmount: 0
    })
    idemKey.value = newIdempotencyKey()
  }
})

async function onSubmit(payload: { key: string }) {
  if (!props.order) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const body: any = {
    orderId: props.order.id,
    refundAmount: form.refundAmount,
    strategy: form.strategy,
    reason: form.reason || undefined,
    walletAmount: form.strategy === 'MIXED' ? form.walletAmount : undefined
  }
  try {
    const r = await refundApi.create(body, payload.key)
    ElMessage.success(r.message || '退款单已创建')
    emit('success', r.data)
    visible.value = false
  } catch (err: any) {
    ElMessage.error(err?.message || '创建退款单失败')
  }
}

function onDuplicate() {
  ElMessage.warning('请勿重复提交')
}

function onClose() {
  visible.value = false
}
</script>

<style scoped>
.readonly { color: #909399; }
.ml-2 { margin-left: 8px; }
.mono { font-family: monospace; color: #67c23a; }
</style>
