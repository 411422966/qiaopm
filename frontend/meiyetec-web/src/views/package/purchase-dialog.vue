<template>
  <el-dialog v-model="dialogVisible" title="会员购买套餐" width="640px" :close-on-click-modal="false">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="选择套餐">
        <el-select v-model="form.packageId" placeholder="选择套餐" filterable style="width:100%">
          <el-option v-for="p in packageOpts" :key="p.id" :value="p.id"
            :label="`${p.name} | ¥${formatMoney(p.price)} | ${p.totalTimes}次`">
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>{{ p.name }}</span>
              <span style="font-size:12px;color:#909399">¥{{ formatMoney(p.price) }} · {{ p.totalTimes }}次</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedPkg" label="套餐详情">
        <div class="pkg-info">
          <div><span class="lbl">总价</span><span class="val">¥{{ formatMoney(selectedPkg.price) }}</span></div>
          <div><span class="lbl">总次数</span><span class="val">{{ selectedPkg.totalTimes }} 次</span></div>
          <div><span class="lbl">有效天数</span><span class="val">{{ selectedPkg.validDays }} 天</span></div>
        </div>
      </el-form-item>
      <el-form-item label="选择会员" prop="memberId">
        <el-select v-model="form.memberId" placeholder="选择会员" filterable
          :disabled="props.lockMember" :clearable="!props.lockMember" style="width:100%">
          <template #prefix><el-icon><User /></el-icon></template>
          <el-option v-if="props.lockMember && lockedMemberLabel" :value="form.memberId"
            :label="lockedMemberLabel" />
          <el-option v-for="m in memberOpts" :key="m.id" :value="m.id"
            :label="`${m.name} | ${m.phone}`">
            <div style="display:flex;justify-content:space-between">
              <span>{{ m.name }}</span>
              <span style="font-size:12px;color:#909399">{{ m.memberNo || m.phone }} · ¥{{ formatMoney(m.balance) }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="支付方式" prop="payMethod">
        <el-radio-group v-model="form.payMethod">
          <el-radio value="微信">微信</el-radio>
          <el-radio value="支付宝">支付宝</el-radio>
          <el-radio value="银行卡">银行卡</el-radio>
          <el-radio value="现金">现金</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="实付金额" prop="price">
        <el-input-number v-model="form.price" :min="0" :precision="2" :step="100" style="width:200px" />
        <span class="amount-tip">元（默认取套餐售价）</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认购买</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { packageApi } from '@/api/package'
import { memberApi } from '@/api/basic'

const props = defineProps<{
  modelValue: boolean
  /** 来自会员套餐页时预填该会员，套餐页不传 */
  memberId?: number | null
  /** 来自会员套餐页时锁定会员选择 */
  lockMember?: boolean
  /** 来自套餐列表时预填套餐ID（需用户仍选会员） */
  preselectPackageId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  success: [order: any]
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const formRef = ref<any>(null)
const submitting = ref(false)
const searchLoading = ref(false)
const memberOpts = ref<any[]>([])
const packageOpts = ref<any[]>([])

const form = reactive({
  packageId: null as number | null,
  memberId: null as number | null,
  payMethod: '微信',
  price: 0,
  operatorId: 1,
  remark: ''
})

const rules = {
  memberId: [{ required: true, message: '请选择会员', trigger: 'change' }],
  payMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }],
  price: [{ required: true, message: '请填写金额', trigger: 'blur' }]
}

const formatMoney = (v: any) => Number(v || 0).toFixed(2)

const selectedPkg = computed(() =>
  packageOpts.value.find((p: any) => p.id === form.packageId)
)

const lockedMemberLabel = ref('')

watch(selectedPkg, p => {
  if (p) form.price = Number(p.price)
})

watch(() => [props.lockMember, props.memberId, dialogVisible.value] as const,
  async ([lock, mid, visible]) => {
    if (!visible || !lock || !mid) return
    if (lockedMemberLabel.value) return
    try {
      const res: any = await memberApi.detail(mid)
      const m = res.data
      lockedMemberLabel.value = m ? `${m.name} | ${m.phone}` : `会员#${mid}`
    } catch {
      lockedMemberLabel.value = `会员#${mid}`
    }
  }, { immediate: true })

const searchMembers = async (kw: string) => {
  searchLoading.value = true
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 30, keyword: kw || '', status: 1 })
    memberOpts.value = res.data?.records || []
  } finally {
    searchLoading.value = false
  }
}

const loadPackages = async () => {
  const res: any = await packageApi.page({ pageNum: 1, pageSize: 100, status: 1 })
  packageOpts.value = res.data?.records || []
}

const submit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const res: any = await packageApi.purchase({ ...form, price: Number(form.price) })
    if (res.code === 200) {
      ElMessage.success(`购买成功！订单号：${res.data.orderNo}，剩余 ${res.data.remainingTimes} 次`)
      emit('success', res.data)
      dialogVisible.value = false
    }
  } finally {
    submitting.value = false
  }
}

watch(() => dialogVisible.value, async v => {
  if (!v) return
  Object.assign(form, { packageId: null, memberId: null, payMethod: '微信', price: 0, remark: '' })
  // 会员套餐页带入：预填并锁定会员选择，不拉会员列表
  if (props.lockMember && props.memberId) {
    form.memberId = props.memberId
  } else {
    await searchMembers('')
  }
  await loadPackages()
  // 套餐列表快捷购买：预选套餐
  if (props.preselectPackageId) {
    form.packageId = props.preselectPackageId
  }
})
</script>

<style scoped>
.pkg-info {
  display: flex; gap: 24px; padding: 12px 16px; background: #f5f7fa;
  border-radius: 8px;
}
.pkg-info .lbl { color: #909399; margin-right: 8px; font-size: 13px; }
.pkg-info .val { font-weight: 600; color: #303133; }
.amount-tip { margin-left: 12px; color: #909399; font-size: 13px; }
</style>