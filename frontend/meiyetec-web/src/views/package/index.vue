<template>
  <div class="pkg-page">
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><Tickets /></el-icon>
        <div>
          <h2>套餐次卡管理</h2>
          <p>支持套餐上下架、会员购买与服务核销，剩余次数一目了然</p>
        </div>
      </div>
      <el-button type="warning" @click="goMemberPackages" :icon="User">
        会员套餐查询
      </el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="套餐名称 / 编码" clearable
        style="width: 220px" :prefix-icon="Search" @keyup.enter="loadData" />
      <el-select v-model="query.category" placeholder="分类" clearable style="width: 130px">
        <el-option label="面部护理" value="面部护理" />
        <el-option label="身体护理" value="身体护理" />
        <el-option label="肩颈护理" value="肩颈护理" />
        <el-option label="VIP尊享" value="VIP尊享" />
        <el-option label="通用" value="通用" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
      <div style="flex:1"></div>
      <el-button type="success" :icon="Plus" @click="openForm()">新增套餐</el-button>
    </div>

    <!-- 全店总剩余次数汇总头 -->
    <div class="stats-row" v-if="stats">
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">上架套餐</div>
        <div class="stat-value">{{ stats.listed }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">下架套餐</div>
        <div class="stat-value text-muted">{{ stats.unlisted }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">累计销量（次卡张数）</div>
        <div class="stat-value text-primary">{{ stats.totalSale }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">累计核销（次）</div>
        <div class="stat-value text-warning">{{ stats.totalRedeem }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card highlight">
        <div class="stat-label">有效会员订单</div>
        <div class="stat-value text-info">{{ stats.activeOrders }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card highlight">
        <div class="stat-label">总剩余次数</div>
        <div class="stat-value text-success">{{ stats.totalRemaining }}</div>
      </el-card>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column label="ID" prop="id" width="70" />
        <el-table-column label="封面" width="72" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.cover"
              :src="row.cover"
              fit="cover"
              style="width:48px;height:48px;border-radius:6px"
              :preview-src-list="[row.cover]"
              hide-on-click-modal
            />
            <div v-else class="cover-fallback">{{ row.name?.slice(0, 1) || '套' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="编码" prop="code" width="130" />
        <el-table-column label="分类" prop="category" width="100" />
        <el-table-column label="总次数" prop="totalTimes" width="80" align="center" />
        <el-table-column label="价格" prop="price" width="110" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.price) }}</template>
        </el-table-column>
        <el-table-column label="有效期" prop="validDays" width="90" align="center">
          <template #default="{ row }">{{ row.validDays }} 天</template>
        </el-table-column>
        <el-table-column label="销量/核销" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success" effect="plain">售 {{ row.saleCount || 0 }}</el-tag>
            <el-tag size="small" type="warning" effect="plain" style="margin-left:4px">销 {{ row.redeemCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="success" :icon="ShoppingCart" @click="quickPurchase(row)">购买</el-button>
            <el-button text type="primary" :icon="Edit" @click="openForm(row)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗（独立组件） -->
    <package-form
      v-model="dialogVisible"
      :row="editingRow"
      @success="loadData"
    />

    <!-- 套餐列表快捷购买（带会员下拉，无锁定） -->
    <purchase-dialog
      v-if="quickPurchaseVisible"
      v-model="quickPurchaseVisible"
      :preselect-package-id="prefillPackageId"
      @success="onQuickPurchaseSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, Edit, Delete, Tickets, User, ShoppingCart } from '@element-plus/icons-vue'
import { packageApi } from '@/api/package'
import PackageForm from './package-form.vue'
import PurchaseDialog from './purchase-dialog.vue'

const router = useRouter()
const loading = ref(false)
const dialogVisible = ref(false)
const quickPurchaseVisible = ref(false)
const prefillPackageId = ref<number | null>(null)
const editingRow = ref<any | null>(null)
const tableData = ref<any[]>([])
const total = ref(0)
const stats = ref<any | null>(null)

const query = reactive({
  keyword: '',
  category: '',
  status: null as number | null,
  pageNum: 1,
  pageSize: 20
})

const formatMoney = (v: any) => Number(v || 0).toFixed(2)

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await packageApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      category: query.category || undefined,
      status: query.status ?? undefined
    })
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const reset = () => {
  query.keyword = ''
  query.category = ''
  query.status = null
  query.pageNum = 1
  loadData()
}

const openForm = (row?: any) => {
  editingRow.value = row ? { ...row } : null
  dialogVisible.value = true
}

const quickPurchase = (row: any) => {
  prefillPackageId.value = row.id
  quickPurchaseVisible.value = true
}

const onQuickPurchaseSuccess = () => {
  prefillPackageId.value = null
  loadData()
}

const remove = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确认删除套餐「${row.name}」？\n若该套餐仍有有效会员订单将无法删除。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await packageApi.delete(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch { /* 业务拦截时由拦截器统一提示 */ }
}

const goMemberPackages = () => router.push('/package/member-orders')

const loadStats = async () => {
  try {
    const res: any = await packageApi.stats()
    if (res.code === 200) stats.value = res.data
  } catch {}
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.pkg-page { padding: 0; }
.banner {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; border-radius: 16px; padding: 24px 32px; margin-bottom: 20px;
  display: flex; justify-content: space-between; align-items: center;
}
.banner-left { display: flex; gap: 16px; align-items: center; }
.banner h2 { margin: 0; font-size: 22px; }
.banner p { margin: 4px 0 0; font-size: 13px; opacity: 0.9; }
.filter-bar {
  background: #fff; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px;
  display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.pagination { display: flex; justify-content: flex-end; padding-top: 16px; }
.cover-fallback {
  display: inline-flex; align-items: center; justify-content: center;
  width: 48px; height: 48px; border-radius: 6px;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #fff; font-size: 20px; font-weight: 600;
}

/* 套餐列表头统计卡 */
.stats-row {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px;
  margin-bottom: 16px;
}
.stat-card { padding: 14px 18px; }
.stat-label { color: #909399; font-size: 12px; margin-bottom: 6px; }
.stat-value { font-size: 22px; font-weight: 700; color: #303133; }
.stat-card.highlight { background: #fffbeb; border: 1px solid #fde68a; }
.text-primary  { color: #409eff; }
.text-success  { color: #67c23a; }
.text-warning  { color: #e6a23c; }
.text-danger   { color: #f56c6c; }
.text-info     { color: #909399; }
.text-muted    { color: #c0c4cc; }
@media (max-width: 1280px) {
  .stats-row { grid-template-columns: repeat(3, 1fr); }
}
</style>
