<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    title="创建盘点单"
    width="900px"
    :close-on-click-modal="false"
  >
    <el-steps :active="step" finish-status="success" simple style="margin-bottom: 16px">
      <el-step title="选择范围" />
      <el-step title="录入实盘" />
      <el-step title="预览差异" />
      <el-step title="确认提交" />
    </el-steps>

    <!-- Step 1: 选择范围 -->
    <div v-show="step === 0">
      <el-form label-width="100px">
        <el-form-item label="盘点门店" required>
          <el-select v-model="form.storeId" placeholder="选择门店" style="width: 240px">
            <el-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.storeName || s.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点类型">
          <el-radio-group v-model="form.scope">
            <el-radio-button value="all">全部商品</el-radio-button>
            <el-radio-button value="category">按分类</el-radio-button>
            <el-radio-button value="custom">自选商品</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类筛选" v-if="form.scope === 'category'">
          <el-select v-model="form.category" placeholder="选择分类" style="width: 240px">
            <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="盘点说明（选填）" /></el-form-item>
      </el-form>
    </div>

    <!-- Step 2: 录入实盘 -->
    <div v-show="step === 1">
      <div style="margin-bottom: 8px">
        <el-button @click="autoFill" type="primary" link :loading="autoFilling">自动填入系统库存</el-button>
        <el-button @click="addRow" type="success" link>+ 添加商品</el-button>
        <el-button @click="form.items = []" type="danger" link>清空</el-button>
      </div>
      <el-table :data="form.items" border height="380" empty-text="请先选择盘点范围">
        <el-table-column label="商品" width="240">
          <template #default="{ row }">
            <el-select v-model="row.productId" filterable :disabled="form.scope !== 'custom'" placeholder="选择商品" style="width: 100%" @change="(v: any) => onProductChange(row, v)">
              <el-option v-for="p in filteredProducts" :key="p.id" :value="p.id" :label="`${p.productCode} - ${p.productName}`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="批次" width="160">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="留空 = 全部" />
          </template>
        </el-table-column>
        <el-table-column label="系统库存" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.systemQty != null">{{ row.systemQty }} {{ row.unit || '' }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="实盘库存" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.actualQty" :min="0" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="差异" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.actualQty != null && row.systemQty != null" :class="diffClass(row)">
              {{ diffPrefix(row) }}{{ (row.actualQty - row.systemQty) }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" v-if="form.scope === 'custom'">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Step 3: 预览差异 -->
    <div v-show="step === 2">
      <el-descriptions :column="3" border style="margin-bottom: 12px">
        <el-descriptions-item label="门店">{{ storeLabel }}</el-descriptions-item>
        <el-descriptions-item label="范围">{{ scopeLabel }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ form.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总项">{{ form.items.length }}</el-descriptions-item>
        <el-descriptions-item label="差异项">
          <span class="warn">{{ diffItemCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="盘盈 / 盘亏">
          <span class="pos">+{{ totalProfit }}</span> / <span class="neg">-{{ totalLoss }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-table :data="form.items.filter((r: any) => (r.actualQty - r.systemQty) !== 0)" border height="380" empty-text="无差异">
        <el-table-column label="商品">
          <template #default="{ row }">
            <span>{{ row.productName }}</span>
            <span style="margin-left: 8px; font-size: 11px; color: #909399">{{ row.productCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="批次">
          <template #default="{ row }">
            <span v-if="row.batchNo">{{ row.batchNo }}</span>
            <span v-else class="text-muted">全部</span>
          </template>
        </el-table-column>
        <el-table-column label="系统 → 实盘" align="right" width="120">
          <template #default="{ row }">{{ row.systemQty }} → {{ row.actualQty }}</template>
        </el-table-column>
        <el-table-column label="差异" align="right">
          <template #default="{ row }">
            <span :class="diffClass(row)">{{ diffPrefix(row) }}{{ row.actualQty - row.systemQty }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Step 4: 确认提交 -->
    <div v-show="step === 3">
      <el-result icon="info" title="确认提交盘点单？" sub-title="提交后将根据实盘数量调整库存，确认提交后无法撤销。">
        <template #extra>
          <el-radio-group v-model="form.confirmImmediately">
            <el-radio :value="true">立即调整库存</el-radio>
            <el-radio :value="false">保存为草稿（暂不调整库存）</el-radio>
          </el-radio-group>
        </template>
      </el-result>
    </div>

    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button v-if="step > 0" @click="step--">上一步</el-button>
      <el-button v-if="step < 3" type="primary" @click="nextStep" :disabled="!canNext">下一步</el-button>
      <el-button v-else type="success" @click="submit" :loading="saving">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import stockApi from '@/api/stock'
import { storeApi } from '@/api/basic'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue', 'submitted'])

const step = ref(0)
const saving = ref(false)
const autoFilling = ref(false)
const stores = ref<any[]>([])
const products = ref<any[]>([])

const form = ref<any>({
  storeId: null as number | null,
  scope: 'all' as 'all' | 'category' | 'custom',
  category: '',
  remark: '',
  items: [] as any[],
  confirmImmediately: true
})

watch(() => props.modelValue, (v) => {
  if (v) {
    step.value = 0
    form.value = { storeId: stores.value[0]?.id || 1, scope: 'all', category: '', remark: '', items: [], confirmImmediately: true }
    loadAll()
  }
})

const categories = computed(() => Array.from(new Set(products.value.map(p => p.category).filter(Boolean))))

const filteredProducts = computed(() => {
  if (form.value.scope === 'category' && form.value.category) {
    return products.value.filter(p => p.category === form.value.category)
  }
  return products.value
})

const storeLabel = computed(() => {
  const s = stores.value.find(x => x.id === form.value.storeId)
  return s ? (s.storeName || s.name) : '-'
})

const scopeLabel = computed(() => {
  if (form.value.scope === 'all') return '全部商品'
  if (form.value.scope === 'category') return `分类: ${form.value.category}`
  return '自选商品'
})

const totalProfit = computed(() =>
  form.value.items.reduce((s: number, r: any) => {
    const d = (r.actualQty || 0) - (r.systemQty || 0)
    return d > 0 ? s + d : s
  }, 0)
)
const totalLoss = computed(() =>
  form.value.items.reduce((s: number, r: any) => {
    const d = (r.actualQty || 0) - (r.systemQty || 0)
    return d < 0 ? s + Math.abs(d) : s
  }, 0)
)
const diffItemCount = computed(() =>
  form.value.items.filter((r: any) => (r.actualQty - r.systemQty) !== 0).length
)

const canNext = computed(() => {
  if (step.value === 0) return !!form.value.storeId
  if (step.value === 1) return form.value.items.length > 0
  return true
})

const loadAll = async () => {
  await Promise.all([loadStores(), loadProducts()])
}

const loadStores = async () => {
  try {
    const res: any = await storeApi.list()
    stores.value = res.data || []
    if (!form.value.storeId && stores.value.length) form.value.storeId = stores.value[0].id
  } catch {}
}

const loadProducts = async () => {
  try {
    const res: any = await stockApi.productPage({ pageNum: 1, pageSize: 200 })
    products.value = res.data?.records || []
  } catch {}
}

const addRow = () => {
  form.value.items.push({ productId: null, batchNo: '', systemQty: null, actualQty: 0 })
}

const autoFill = async () => {
  if (!products.value.length) await loadProducts()
  autoFilling.value = true
  try {
    const sid = form.value.storeId || 1
    const list = filteredProducts.value.length ? filteredProducts.value : products.value
    const results = await Promise.allSettled(
      list.map(p => stockApi.inventoryPage({ pageNum: 1, pageSize: 100, storeId: sid, productId: p.id }))
    )
    const all: any[] = []
    results.forEach((r, i) => {
      if (r.status !== 'fulfilled') return
      const records = r.value?.data?.records || []
      if (records.length === 0) return
      for (const inv of records) {
        all.push({
          productId: inv.product_id ?? list[i].id,
          productName: inv.product_name ?? list[i].productName,
          batchNo: inv.batch_no || '',
          systemQty: inv.quantity ?? 0,
          actualQty: inv.quantity ?? 0,
          unit: inv.unit || list[i].unit || ''
        })
      }
    })
    if (all.length) {
      form.value.items = all
      ElMessage.success(`已自动填入 ${all.length} 条库存记录`)
    } else {
      ElMessage.info('当前范围内没有库存数据')
    }
  } finally { autoFilling.value = false }
}

const onProductChange = async (row: any, productId: number) => {
  const product = products.value.find(p => p.id === productId)
  if (product) {
    row.productName = product.productName
    row.unit = product.unit
  }
  const sid = form.value.storeId || 1
  const res: any = await stockApi.inventoryPage({ pageNum: 1, pageSize: 100, storeId: sid, productId })
  const records = res.data?.records || []
  const found = records.find((x: any) => !row.batchNo || x.batch_no === row.batchNo)
  row.systemQty = found ? found.quantity : 0
  row.actualQty = row.systemQty
}

const diffClass = (row: any) => {
  const d = (row.actualQty || 0) - (row.systemQty || 0)
  if (d === 0) return ''
  return d > 0 ? 'pos' : 'neg'
}
const diffPrefix = (row: any) => {
  const d = (row.actualQty || 0) - (row.systemQty || 0)
  return d > 0 ? '+' : ''
}

const nextStep = () => {
  if (step.value === 0) {
    // 跳到第2步时，根据 scope 自动加载
    autoFill()
  }
  step.value++
}

const cancel = () => {
  emit('update:modelValue', false)
}

const submit = async () => {
  saving.value = true
  try {
    const res: any = await stockApi.check({
      storeId: form.value.storeId,
      remark: form.value.remark,
      items: form.value.items,
      confirm: form.value.confirmImmediately
    })
    const c = res.data?.check || {}
    if (form.value.confirmImmediately) {
      ElMessage.success(`盘点完成，差异${c.diffItems ?? 0} 项，盘盈 +${c.profitQty ?? 0}，盘亏 -${c.lossQty ?? 0}`)
    } else {
      ElMessage.success(`已保存草稿 [${c.checkNo}]`)
    }
    emit('update:modelValue', false)
    emit('submitted')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally { saving.value = false }
}

onMounted(() => {
  if (props.modelValue) loadAll()
})
</script>

<style scoped>
.pos { color: #67c23a; font-weight: bold; }
.neg { color: #f56c6c; font-weight: bold; }
.warn { color: #e6a23c; font-weight: bold; }
.text-muted { color: #c0c4cc; font-style: italic; font-size: 12px; }
</style>