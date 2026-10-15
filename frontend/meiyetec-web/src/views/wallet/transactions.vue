<template>
  <div class="txn-page">
    <div class="page-head">
      <div>
        <h2><el-icon><Document /></el-icon> 储值流水查询</h2>
        <p>支持按会员、时间、类型查询所有钱包变动明细，留空会员即查询全部流水</p>
      </div>
      <el-button @click="goRecharge" :icon="Back">返回充值</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="query.memberId" placeholder="全部会员（不选则查全部）" clearable
        filterable remote :remote-method="searchMembers" :loading="searchLoading"
        style="width: 280px">
        <template #prefix><el-icon><User /></el-icon></template>
        <el-option v-for="m in memberOpts" :key="m.id"
          :label="`${m.name} | ${m.phone}`" :value="m.id">
          <div class="opt-row">
            <span class="opt-name">{{ m.name }}</span>
            <span class="opt-extra">{{ m.memberNo }} · ¥{{ formatMoney(m.balance) }}</span>
          </div>
        </el-option>
      </el-select>

      <el-select v-model="query.type" placeholder="流水类型" clearable style="width: 130px">
        <el-option label="充值" :value="1" />
        <el-option label="消费" :value="2" />
        <el-option label="退款" :value="3" />
        <el-option label="赠送" :value="4" />
        <el-option label="冻结" :value="5" />
        <el-option label="解冻" :value="6" />
      </el-select>

      <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="ID" prop="id" width="70" />
        <el-table-column label="会员" prop="memberName" width="120" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.type)" effect="dark" size="small">
              {{ row.typeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="160" align="right">
          <template #default="{ row }">
            <span :class="amountClass(row.type)">
              {{ amountPrefix(row.type) }}¥{{ formatMoney(row.amount) }}
            </span>
            <el-tag v-if="row.giftAmount && Number(row.giftAmount) > 0" size="small" type="warning" effect="plain" style="margin-left:4px">
              +送 ¥{{ formatMoney(row.giftAmount) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动前" prop="balanceBefore" width="110" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.balanceBefore) }}</template>
        </el-table-column>
        <el-table-column label="变动后" prop="balanceAfter" width="110" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column label="支付/方式" prop="payMethod" width="110" />
        <el-table-column label="关联订单" prop="orderId" width="100">
          <template #default="{ row }">{{ row.orderId || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作人" prop="operatorId" width="80">
          <template #default="{ row }">{{ row.operatorId || '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column label="时间" prop="createdAt" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="loadData" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Search, Refresh, Back, User } from '@element-plus/icons-vue'
import { walletApi, memberApi } from '@/api/basic'

const router = useRouter()
const loading = ref(false)
const searchLoading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const memberOpts = ref<any[]>([])

const query = reactive({
  memberId: null as number | null,
  type: null as number | null,
  pageNum: 1,
  pageSize: 20
})

const tagType = (t: number) => {
  const m: Record<number, string> = { 1: 'success', 2: 'warning', 3: 'info', 4: 'primary', 5: 'danger', 6: 'success' }
  return m[t] || ''
}
const amountClass = (t: number) => t === 1 || t === 3 || t === 4 || t === 6 ? 'text-success' : 'text-danger'
const amountPrefix = (t: number) => t === 1 || t === 3 || t === 4 || t === 6 ? '+' : '-'
const formatMoney = (v: any) => Number(v || 0).toFixed(2)
const formatDate = (v: any) => v ? new Date(v).toLocaleString('zh-CN') : '-'

const searchMembers = async (kw: string) => {
  searchLoading.value = true
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 50, keyword: kw || '', status: 1 })
    memberOpts.value = res.data?.records || []
  } finally {
    searchLoading.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    let res: any
    if (query.memberId) {
      res = await walletApi.transactions(query.memberId, {
        pageNum: query.pageNum, pageSize: query.pageSize, type: query.type || undefined
      })
    } else {
      res = await walletApi.allTransactions({
        pageNum: query.pageNum, pageSize: query.pageSize, type: query.type || undefined
      })
    }
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const reset = () => {
  query.type = null
  query.pageNum = 1
  loadData()
}

const goRecharge = () => router.push('/wallet/recharge')

// 默认展示全部流水（不选会员）
searchMembers('')
loadData()
</script>

<style scoped>
.txn-page { padding: 0; }
.page-head {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; border-radius: 16px; padding: 24px 32px; margin-bottom: 20px;
  display: flex; justify-content: space-between; align-items: center;
}
.page-head h2 { margin: 0; font-size: 22px; display: flex; align-items: center; gap: 8px; }
.page-head p { margin: 4px 0 0; font-size: 13px; opacity: 0.9; }

.filter-bar {
  background: #fff; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px;
  display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }

.pagination { display: flex; justify-content: flex-end; padding-top: 16px; }

.opt-row { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.opt-name { font-weight: 600; color: #1f2937; }
.opt-extra { font-size: 12px; color: #909399; }
:deep(.el-select-dropdown__item) { height: auto; padding: 6px 12px; line-height: 1.4; }
</style>