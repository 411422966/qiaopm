<template>
  <div class="task-page">
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="任务号 / 名称 / 操作人" style="width: 220px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.reportCode" placeholder="报表" clearable style="width: 140px" @change="loadData">
        <el-option label="销售" value="SALES" />
        <el-option label="支付" value="PAY" />
        <el-option label="会员" value="MEMBER" />
        <el-option label="提成" value="COMMISSION" />
        <el-option label="商品" value="PRODUCT" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 140px" @change="loadData">
        <el-option label="待处理" :value="1" />
        <el-option label="处理中" :value="2" />
        <el-option label="成功" :value="3" />
        <el-option label="失败" :value="4" />
        <el-option label="已取消" :value="5" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button @click="refresh">刷新</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="taskNo" label="任务号" width="170" />
        <el-table-column label="报表" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.reportName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="区间" width="220">
          <template #default="{ row }">
            {{ row.dateRangeStart || '—' }} ~ {{ row.dateRangeEnd || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" />
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80" align="center" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ row.fileSize ? formatSize(row.fileSize) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100">
          <template #default="{ row }">{{ row.durationMs || '—' }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" :disabled="row.status !== 3" @click="download(row)">下载</el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 3 || row.status === 4" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', reportCode: undefined as string | undefined, status: undefined as number | undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/report/task/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function statusLabel(s: number) {
  return { 1: '待处理', 2: '处理中', 3: '成功', 4: '失败', 5: '已取消' }[s] || '未知'
}
function statusTag(s: number) {
  return ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'danger', 5: 'info' } as any)[s] || 'info'
}

function formatSize(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(2) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

function resetFilter() {
  filter.keyword = ''
  filter.reportCode = undefined
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

function refresh() { loadData() }

async function download(row: any) {
  try {
    const res = await http.get(`/report/task/${row.id}/download`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.taskNo}_${row.reportCode}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已下载')
  } catch {
    ElMessage.error('下载失败')
  }
}

async function cancel(row: any) {
  await ElMessageBox.confirm(`取消任务 ${row.taskNo}？`, '提示', { type: 'warning' })
  await http.post(`/report/task/${row.id}/cancel`)
  ElMessage.success('已取消')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
</style>
