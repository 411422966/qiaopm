<template>
  <div class="reconcile-page">
    <el-card>
      <el-form :inline="true">
        <el-form-item label="对账日期">
          <el-date-picker v-model="runDate" type="date" value-format="YYYY-MM-DD" placeholder="选日期" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onRun" :loading="running">执行对账</el-button>
        </el-form-item>
        <el-form-item>
          <el-tag :type="diffAlertCount > 0 ? 'danger' : 'success'">
            待处理差异: {{ diffAlertCount }}
          </el-tag>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt-4">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="reconcileDate" label="日期" width="120" />
        <el-table-column prop="channel" label="通道" width="120" />
        <el-table-column prop="channelTxnId" label="通道流水" width="220" />
        <el-table-column prop="paymentId" label="本地支付ID" width="100" />
        <el-table-column prop="channelAmount" label="通道金额" width="120" />
        <el-table-column prop="localAmount" label="本地金额" width="120" />
        <el-table-column prop="diffAmount" label="差异" width="100">
          <template #default="{ row }">
            <span :class="{ danger: Number(row.diffAmount) !== 0 }">¥{{ row.diffAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="对账状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleStatus" label="处理状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.handleStatus === 'PENDING' ? 'warning' : 'success'">
              {{ row.handleStatus === 'PENDING' ? '待处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleNote" label="备注" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.handleStatus === 'PENDING' && row.status !== 'MATCHED'"
                       link type="primary" @click="onHandle(row, 'HANDLED')">标处理</el-button>
            <el-button v-if="row.handleStatus === 'PENDING' && row.status !== 'MATCHED'"
                       link type="info" @click="onHandle(row, 'IGNORED')">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="reload"
        @size-change="reload"
        class="mt-3"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reconciliationApi } from '@/api/pos'

const query = reactive({ pageNum: 1, pageSize: 20, status: '', handleStatus: '', dateFrom: '', dateTo: '' })
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const running = ref(false)
const runDate = ref<string>(new Date(Date.now() - 86400000).toISOString().slice(0, 10))

const diffAlertCount = computed(() => rows.value.filter(r => r.handleStatus === 'PENDING' && r.status !== 'MATCHED').length)

const statusLabel = (s: string) => {
  switch (s) {
    case 'MATCHED': return '一致'
    case 'DIFF_LOCAL_MORE': return '本地多'
    case 'DIFF_CHANNEL_MORE': return '通道多'
    case 'DIFF_AMOUNT': return '金额差'
    case 'MISSING_LOCAL': return '本地缺失'
    case 'MISSING_CHANNEL': return '通道缺失'
    default: return s
  }
}
const statusTag = (s: string) => {
  switch (s) {
    case 'MATCHED': return 'success'
    case 'DIFF_AMOUNT': return 'danger'
    case 'DIFF_LOCAL_MORE':
    case 'DIFF_CHANNEL_MORE': return 'warning'
    default: return 'info'
  }
}

async function reload() {
  loading.value = true
  try {
    const r = await reconciliationApi.page(query)
    rows.value = r.data.records || []
    total.value = r.data.total || 0
  } finally {
    loading.value = false
  }
}

async function onRun() {
  running.value = true
  try {
    const r = await reconciliationApi.run(runDate.value)
    ElMessage.success(`对账完成：一致 ${r.data.matched}，差异 ${r.data.diff}`)
    reload()
  } finally {
    running.value = false
  }
}

async function onHandle(row: any, action: string) {
  const note = await ElMessageBox.prompt('处理备注', action === 'HANDLED' ? '标处理' : '忽略', {
    inputPlaceholder: '请输入'
  }).then((r: any) => r.value).catch(() => null)
  if (note === null) return
  await reconciliationApi.handle(row.id, { action, note, handleBy: 1, handleByName: 'admin' })
  ElMessage.success('已处理')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.mt-4 { margin-top: 16px; }
.mt-3 { margin-top: 12px; }
.danger { color: #f56c6c; font-weight: 600; }
</style>
