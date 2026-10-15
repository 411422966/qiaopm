<template>
  <div class="caliber-page">
    <el-card class="search-bar">
      <el-select v-model="filter.category" placeholder="类别" clearable style="width: 140px" @change="loadData">
        <el-option label="销售" value="SALES" />
        <el-option label="支付" value="PAY" />
        <el-option label="会员" value="MEMBER" />
        <el-option label="提成" value="COMMISSION" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="runSnapshot">⚡ 生成今日快照</el-button>
      <el-button type="success" @click="loadSnapshot">刷新快照</el-button>
    </el-card>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="指标口径" name="caliber">
        <el-card>
          <h4 style="margin: 0 0 12px">指标定义（v1）</h4>
          <el-table :data="rows" border>
            <el-table-column prop="category" label="类别" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.category }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="metricKey" label="指标编码" width="120" />
            <el-table-column prop="metricName" label="指标名称" width="160" />
            <el-table-column prop="unit" label="单位" width="80" align="center" />
            <el-table-column prop="formula" label="计算口径" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="queryOne(row)">在线查询</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="历史快照" name="snapshot">
        <el-card>
          <el-table :data="snapshots" border>
            <el-table-column prop="snapshotDate" label="快照日期" width="140" />
            <el-table-column prop="metricKey" label="指标" width="160" />
            <el-table-column prop="metricName" label="名称" width="200" />
            <el-table-column prop="metricValue" label="数值" align="right">
              <template #default="{ row }">{{ row.metricValue }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 在线查询 -->
    <el-dialog v-model="queryDialog.visible" :title="`查询指标：${queryDialog.caliber?.metricName || ''}`" width="640px">
      <el-date-picker v-model="queryPeriod" type="daterange" value-format="YYYY-MM-DD" range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width: 280px" />
      <el-button type="primary" @click="runQuery">查询</el-button>
      <pre v-if="queryDialog.result" style="margin-top: 12px; background: #f5f7fa; padding: 12px; border-radius: 6px;">{{ queryDialog.result }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const filter = reactive({ category: undefined as string | undefined })
const rows = ref<any[]>([])
const snapshots = ref<any[]>([])
const activeTab = ref('caliber')
const queryDialog = reactive<any>({ visible: false, caliber: null, result: null })
const queryPeriod = ref<any>([])

async function loadData() {
  const r = await http.get('/report/caliber/list')
  let list = r.data || []
  if (filter.category) list = list.filter((x: any) => x.category === filter.category)
  rows.value = list
}

async function loadSnapshot() {
  const r = await http.get('/report/snapshot/list')
  snapshots.value = r.data || []
}

async function runSnapshot() {
  const r = await http.post('/report/snapshot/run', {})
  ElMessage.success(`已生成快照：${r.data.updatedCount} 个指标`)
  loadSnapshot()
}

function queryOne(row: any) {
  queryDialog.caliber = row
  queryDialog.result = null
  queryDialog.visible = true
  runQuery()
}

async function runQuery() {
  if (!queryDialog.caliber) return
  const params: any = { storeId: 1 }
  if (queryPeriod.value && queryPeriod.value.length === 2) {
    params.startDate = queryPeriod.value[0]
    params.endDate = queryPeriod.value[1]
  }
  const r = await http.get(`/report/caliber/${queryDialog.caliber.metricKey}/query`, { params })
  queryDialog.result = JSON.stringify(r.data?.result || [], null, 2)
}

onMounted(() => {
  loadData()
  loadSnapshot()
})
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-select { margin-right: 8px; }
</style>
