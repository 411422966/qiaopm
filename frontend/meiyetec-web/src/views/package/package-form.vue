<template>
  <el-dialog
    :model-value="modelValue"
    :title="form.id ? '编辑套餐' : '新增套餐'"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="套餐名称" prop="name">
            <el-input v-model="form.name" placeholder="如：面部基础护理 5 次卡" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="套餐编码" prop="code">
            <el-input v-model="form.code" placeholder="可选，用于识别" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="分类" prop="category">
            <el-select v-model="form.category" placeholder="选择分类" style="width:100%">
              <el-option label="面部护理" value="面部护理" />
              <el-option label="身体护理" value="身体护理" />
              <el-option label="肩颈护理" value="肩颈护理" />
              <el-option label="VIP尊享" value="VIP尊享" />
              <el-option label="通用" value="通用" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="总次数" prop="totalTimes">
            <el-input-number v-model="form.totalTimes" :min="1" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="价格" prop="price">
            <el-input-number v-model="form.price" :min="0" :precision="2" :step="100" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="成本价" prop="costPrice">
            <el-input-number v-model="form.costPrice" :min="0" :precision="2" :step="50" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="有效天数" prop="validDays">
            <el-input-number v-model="form.validDays" :min="1" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上架状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">上架</el-radio>
              <el-radio :value="0">下架</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="套餐类型">
            <el-radio-group v-model="form.bundleType" @change="onBundleTypeChange">
              <el-radio :value="0">单商品</el-radio>
              <el-radio :value="1">组合套餐</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="套餐介绍" />
      </el-form-item>

      <!-- M04 组合配置 -->
      <template v-if="form.bundleType === 1">
        <el-divider content-position="left">
          <el-icon><Connection /></el-icon>
          <span style="margin-left:4px">组合子项目配置（M04）</span>
        </el-divider>
        <el-form-item label-width="0">
          <el-button type="primary" size="small" @click="addBundleItem">
            <el-icon><Plus /></el-icon>添加子项目
          </el-button>
          <el-button size="small" @click="autoFillBundleServices" :loading="servicesLoading">
            <el-icon><Refresh /></el-icon>加载服务列表
          </el-button>
        </el-form-item>
        <el-table :data="bundleItems" border size="small" empty-text="尚未添加子项目">
          <el-table-column label="服务" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.serviceId" filterable style="width:100%"
                         @change="(v: number) => onServiceChange(row, v)">
                <el-option v-for="s in services" :key="s.id" :value="s.id"
                           :label="`${s.serviceName}${s.serviceCode ? ' · ' + s.serviceCode : ''}`" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="次数" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.times" :min="1" :max="999" size="small" style="width:90px" @change="recomputeBundle" />
            </template>
          </el-table-column>
          <el-table-column label="单价(元)" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" :step="10" size="small" style="width:120px" @change="recomputeBundle" />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="100">
            <template #default="{ row }">
              {{ ((row.unitPrice || 0) * (row.times || 0)).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeBundleItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:8px; color:#909399; font-size:12px">
          合计次数：<b>{{ bundleTotalTimes }}</b>　·　子项目：<b>{{ bundleItems.length }}</b>　·　成本小计：<b>¥{{ bundleCostSubtotal.toFixed(2) }}</b>
        </div>
      </template>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Plus, Refresh } from '@element-plus/icons-vue'
import { packageApi } from '@/api/package'
import { serviceApi } from '@/api/pos'

interface BundleSubItem {
  serviceId: number | null
  serviceName: string
  times: number
  unitPrice: number
}

interface PackageRow {
  id?: number | null
  name: string
  code: string
  category: string
  totalTimes: number
  price: number
  costPrice: number
  validDays: number
  status: number
  description: string
  remark: string
  bundleType?: number
  bundleConfig?: string
  bundleItemCount?: number
  bundleItems?: BundleSubItem[]
}

const props = defineProps<{
  modelValue: boolean
  row: PackageRow | null
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  success: []
}>()

const formRef = ref<any>(null)
const submitting = ref(false)

// M04 组合子项目
const bundleItems = ref<BundleSubItem[]>([])
const services = ref<any[]>([])
const servicesLoading = ref(false)

const bundleTotalTimes = computed(() => bundleItems.value.reduce((s, it) => s + (it.times || 0), 0))
const bundleCostSubtotal = computed(() =>
  bundleItems.value.reduce((s, it) => s + ((it.unitPrice || 0) * (it.times || 0)), 0))

function addBundleItem() {
  bundleItems.value.push({ serviceId: null, serviceName: '', times: 1, unitPrice: 0 })
}

function removeBundleItem(idx: number) {
  bundleItems.value.splice(idx, 1)
  recomputeBundle()
}

function onBundleTypeChange(v: number | string) {
  const t = Number(v)
  if (t === 1 && bundleItems.value.length === 0) {
    addBundleItem()
  }
}

function onServiceChange(row: BundleSubItem, sid: number) {
  const svc = services.value.find(s => s.id === sid)
  if (svc) {
    row.serviceName = svc.serviceName || svc.name || ''
    if (!row.unitPrice && (svc.basePrice || svc.price)) {
      row.unitPrice = Number(svc.basePrice || svc.price)
    }
  }
  recomputeBundle()
}

function recomputeBundle() {
  // 自动汇总到套餐次数
  if (form.bundleType === 1) {
    form.totalTimes = bundleTotalTimes.value
    if (!form.costPrice) form.costPrice = Number(bundleCostSubtotal.value.toFixed(2))
  }
}

async function autoFillBundleServices() {
  servicesLoading.value = true
  try {
    const res: any = await serviceApi.list({ status: 1 })
    services.value = res.data || []
    ElMessage.success(`已加载 ${services.value.length} 个服务`)
  } catch (e) {
    ElMessage.error('服务列表加载失败')
  } finally {
    servicesLoading.value = false
  }
}

function parseBundleConfig(json?: string): BundleSubItem[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    if (!Array.isArray(arr)) return []
    return arr.map((it: any) => ({
      serviceId: it.serviceId,
      serviceName: it.serviceName || '',
      times: it.times || 1,
      unitPrice: it.unitPrice || 0
    }))
  } catch {
    return []
  }
}

const defaultForm = (): PackageRow => ({
  id: null,
  name: '',
  code: '',
  category: '通用',
  totalTimes: 5,
  price: 0,
  costPrice: 0,
  validDays: 365,
  status: 1,
  description: '',
  remark: '',
  bundleType: 0,
  bundleConfig: '',
  bundleItemCount: 0,
  bundleItems: []
})

const form = reactive<PackageRow>(defaultForm())

const rules = {
  name: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  totalTimes: [{ required: true, message: '请输入总次数', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }]
}

watch(
  () => [props.modelValue, props.row] as const,
  ([visible, row]) => {
    if (!visible) return
    if (row) {
      Object.assign(form, row)
      bundleItems.value = parseBundleConfig(row.bundleConfig)
      // 首次进入组合模式但无子项时自动添加一行
      if (form.bundleType === 1 && bundleItems.value.length === 0) addBundleItem()
    } else {
      Object.assign(form, defaultForm())
      bundleItems.value = []
    }
    formRef.value?.clearValidate?.()
  },
  { immediate: true, deep: true }
)

const handleCancel = () => emit('update:modelValue', false)

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 组合模式：序列化 bundleConfig + 校验 + 汇总 totalTimes
  if (form.bundleType === 1) {
    if (bundleItems.value.length === 0) {
      ElMessage.warning('组合套餐至少需要 1 个子项目')
      return
    }
    if (bundleItems.value.some(it => !it.serviceId)) {
      ElMessage.warning('所有子项目必须选择服务')
      return
    }
    form.bundleConfig = JSON.stringify(bundleItems.value)
    form.bundleItemCount = bundleItems.value.length
    form.totalTimes = bundleTotalTimes.value
    form.costPrice = Number(bundleCostSubtotal.value.toFixed(2))
  } else {
    form.bundleConfig = ''
    form.bundleItemCount = 0
  }
  submitting.value = true
  try {
    await packageApi.save({ ...form })
    ElMessage.success(form.id ? '套餐更新成功' : '套餐创建成功')
    emit('update:modelValue', false)
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>