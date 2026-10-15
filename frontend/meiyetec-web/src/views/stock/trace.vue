<template>
  <div class="stock-trace">
    <el-card>
      <div class="header">
        <h3>📦 批次追溯：{{ product?.productName }} ({{ product?.productCode }})</h3>
        <el-button @click="router.back()">返回</el-button>
      </div>
      <el-descriptions :column="3" border v-if="product">
        <el-descriptions-item label="分类">{{ product.category }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ product.spec }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ product.unit }}</el-descriptions-item>
        <el-descriptions-item label="成本价">¥{{ product.costPrice }}</el-descriptions-item>
        <el-descriptions-item label="售价">¥{{ product.salePrice }}</el-descriptions-item>
        <el-descriptions-item label="启用批次">{{ product.enableBatch ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 12px">
      <h4>当前库存（按批次）</h4>
      <el-table :data="inventory" border stripe v-loading="loading">
        <el-table-column prop="batchNo" label="批次号" width="200" />
        <el-table-column prop="quantity" label="库存" width="100" align="right" />
        <el-table-column prop="productionDate" label="生产日期" width="120" />
        <el-table-column prop="expiryDate" label="过期日期" width="120" />
        <el-table-column prop="supplier" label="供应商" />
        <el-table-column prop="lastInTime" label="最后入库" width="160" />
        <el-table-column prop="lastOutTime" label="最后出库" width="160" />
      </el-table>
    </el-card>

    <el-card style="margin-top: 12px">
      <h4>出入库流水</h4>
      <el-table :data="records" border stripe>
        <el-table-column prop="recordTime" label="时间" width="160" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="RECORD_TAG[row.type] || 'primary'">{{ RECORD_LABEL[row.type] || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="180" />
        <el-table-column label="数量" width="100" align="right">
          <template #default="{ row }">
            <span :class="row.quantity > 0 ? 'pos' : 'neg'">{{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="beforeQty" label="操作前" width="80" align="right" />
        <el-table-column prop="afterQty" label="操作后" width="80" align="right" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="remark" label="备注" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import stockApi from '@/api/stock'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const product = ref<any>(null)
const inventory = ref<any[]>([])
const records = ref<any[]>([])

const RECORD_LABEL: Record<number, string> = { 1: '入库', 2: '出库', 3: '盘点', 4: '领用', 5: '报损' }
const RECORD_TAG: Record<number, any> = { 1: 'success', 2: 'warning', 3: 'info', 4: 'primary', 5: 'danger' }

const load = async () => {
  loading.value = true
  try {
    const productId = Number(route.params.id)
    const res: any = await stockApi.trace(productId)
    product.value = res.data?.product
    inventory.value = res.data?.inventory || []
    records.value = res.data?.records || []
  } finally { loading.value = false }
}

onMounted(load)
</script>

<style scoped>
.stock-trace { padding: 12px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.pos { color: #67c23a; font-weight: bold; }
.neg { color: #f56c6c; font-weight: bold; }
</style>