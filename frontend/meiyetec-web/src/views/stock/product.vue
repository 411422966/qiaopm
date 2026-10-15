<template>
  <div class="product-page">
    <!-- 顶部统计卡片 -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-blue">
        <div class="kpi-label">商品总数</div>
        <div class="kpi-value">{{ stats.totalProducts ?? '—' }}</div>
        <div class="kpi-sub">启用 {{ stats.activeProducts ?? '—' }} / 停用 {{ (stats.totalProducts ?? 0) - (stats.activeProducts ?? 0) }}</div>
      </div>
      <div class="kpi-card kpi-purple">
        <div class="kpi-label">库存总价值</div>
        <div class="kpi-value">¥{{ formatAmount(stats.totalValue) }}</div>
        <div class="kpi-sub">库存 {{ stats.totalQty ?? '—' }} 件 · 覆盖 {{ stats.coveredProducts ?? '—' }} 商品</div>
      </div>
      <div class="kpi-card kpi-orange">
        <div class="kpi-label">本月入库</div>
        <div class="kpi-value">{{ stats.monthIn ?? '—' }}</div>
        <div class="kpi-sub">本月出库 {{ stats.monthOutCount ?? '—' }} 次</div>
      </div>
      <div class="kpi-card kpi-red">
        <div class="kpi-label">缺货预警</div>
        <div class="kpi-value">{{ alertStats.outOfStock ?? '—' }}</div>
        <div class="kpi-sub">低于最低 {{ alertStats.lowStock ?? '—' }} · 超储 {{ alertStats.overStock ?? '—' }}</div>
      </div>
      <div class="kpi-card kpi-green">
        <div class="kpi-label">本月消耗</div>
        <div class="kpi-value">¥{{ formatAmount(stats.monthCost) }}</div>
        <div class="kpi-sub">临期 {{ alertStats.expiring ?? '—' }} · 过期 {{ alertStats.expired ?? '—' }}</div>
      </div>
    </div>

    <!-- 分类分布柱状图 -->
    <el-card class="chart-card">
      <template #header>
        <div class="chart-header">
          <span>商品分类分布</span>
          <span class="chart-meta" v-if="categoryStats.length">合计 {{ categoryStats.length }} 类</span>
        </div>
      </template>
      <div ref="categoryChartRef" style="height: 220px" />
      <div v-if="!categoryStats.length && !loadingCategory" class="chart-placeholder">暂无分类数据</div>
    </el-card>

    <!-- 搜索工具栏 -->
    <el-card class="search-bar">
      <el-input
        ref="keywordInputRef"
        v-model="filter.keyword"
        placeholder="编码/名称（Ctrl+F 聚焦）"
        style="width: 200px" clearable
        @keyup.enter="loadData" />
      <el-select v-model="filter.category" placeholder="分类" clearable style="width: 140px" @change="loadData">
        <el-option label="耗材" value="耗材" />
        <el-option label="产品" value="产品" />
        <el-option label="工具" value="工具" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openDialog()">+ 新增商品</el-button>
      <el-button type="success" @click="openImportDialog">批量导入</el-button>
      <el-button @click="downloadTemplate">下载模板</el-button>
      <el-button @click="exportExcel">导出</el-button>

      <span class="toolbar-spacer" />

      <!-- 密度切换 -->
      <el-tooltip content="表格密度" placement="top">
        <el-button-group>
          <el-button :type="tableSize === 'small' ? 'primary' : 'default'" size="small" @click="tableSize = 'small'">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button :type="tableSize === 'default' ? 'primary' : 'default'" size="small" @click="tableSize = 'default'">
            <el-icon><Menu /></el-icon>
          </el-button>
          <el-button :type="tableSize === 'large' ? 'primary' : 'default'" size="small" @click="tableSize = 'large'">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-button-group>
      </el-tooltip>

      <!-- 列设置 -->
      <el-popover :width="260" trigger="click" placement="bottom-end">
        <template #reference>
          <el-button :icon="Setting">列设置</el-button>
        </template>
        <div class="column-settings">
          <div class="column-settings-title">
            <span>显示列</span>
            <div>
              <el-button link size="small" @click="setAllCols(true)">全选</el-button>
              <el-button link size="small" @click="setAllCols(false)">反选</el-button>
              <el-button link size="small" @click="resetColumnSettings">重置</el-button>
            </div>
          </div>
          <el-checkbox
            v-for="col in columnDefs"
            :key="col.prop"
            v-model="col.visible"
            @change="persistColumnSettings">
            {{ col.label }}
          </el-checkbox>
        </div>
      </el-popover>

      <!-- 保存视图 -->
      <el-dropdown trigger="click" @command="handleViewCmd">
        <el-button :icon="Collection">视图</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="save">💾 保存当前为新视图</el-dropdown-item>
            <el-dropdown-item v-if="savedViews.length === 0" disabled>（暂无保存的视图）</el-dropdown-item>
            <el-dropdown-item
              v-for="v in savedViews"
              :key="v.id"
              :command="`apply:${v.id}`">
              📋 {{ v.name }}
              <span style="color:#909399; font-size:11px; margin-left:8px">
                [{{ v.size === 'small' ? '紧凑' : v.size === 'large' ? '舒适' : '默认' }}]
              </span>
            </el-dropdown-item>
            <el-dropdown-item divided v-if="savedViews.length > 0" command="manage">管理视图…</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-card>

    <!-- 商品列表 -->
    <el-card>
      <el-table
        :data="products"
        :size="tableSize"
        :height="tableHeight"
        border
        stripe
        v-loading="loading"
        @row-click="viewDetail">
        <el-table-column v-if="isColVisible('productCode')" prop="productCode" label="编码" width="130" />
        <el-table-column v-if="isColVisible('productName')" prop="productName" label="名称" min-width="160">
          <template #default="{ row }">
            <span v-if="row.productName">{{ row.productName }}</span>
            <span v-else class="text-muted">{{ row.productCode || '（未命名）' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('category')" prop="category" label="分类" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small">{{ row.category }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('spec')" prop="spec" label="规格" width="100" />
        <el-table-column v-if="isColVisible('unit')" prop="unit" label="单位" width="60" />
        <el-table-column v-if="isColVisible('costPrice')" label="成本价" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.costPrice">¥{{ Number(row.costPrice).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('salePrice')" label="售价" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.salePrice">¥{{ Number(row.salePrice).toFixed(2) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('minStock')" prop="minStock" label="最低" width="70" align="right" />
        <el-table-column v-if="isColVisible('maxStock')" prop="maxStock" label="最高" width="70" align="right" />
        <el-table-column v-if="isColVisible('enableBatch')" label="批次" width="70">
          <template #default="{ row }">
            <el-tag :type="row.enableBatch ? 'success' : 'info'" size="small">{{ row.enableBatch ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('status')" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status ? 'success' : 'danger'" size="small">{{ row.status ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDialog(row)">编辑</el-button>
            <el-button link type="success" @click.stop="openInbound(row)">入库</el-button>
            <el-button link type="warning" @click.stop="goTrace(row.id)">批次</el-button>
            <el-button link type="danger" :disabled="!row.productName" @click.stop="delProduct(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[20, 50, 100]"
        @current-change="loadData"
        @size-change="loadData"
        style="margin-top: 12px"
      />
    </el-card>

    <!-- 商品编辑弹窗 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑商品' : '新增商品'" width="640px">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="商品编码" prop="productCode">
              <el-input v-model="form.productCode" :disabled="!!form.id" placeholder="唯一编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="productName">
              <el-input v-model="form.productName" placeholder="商品名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" style="width: 100%">
                <el-option label="耗材" value="耗材" />
                <el-option label="产品" value="产品" />
                <el-option label="工具" value="工具" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="规格">
              <el-input v-model="form.spec" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位">
              <el-input v-model="form.unit" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="成本价">
              <el-input-number v-model="form.costPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="售价">
              <el-input-number v-model="form.salePrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="启用批次">
              <el-switch v-model="form.enableBatch" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="最低库存">
              <el-input-number v-model="form.minStock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最高库存">
              <el-input-number v-model="form.maxStock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="saveProduct" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 入库弹窗 -->
    <el-dialog v-model="inboundDialog" title="入库" width="540px">
      <el-form :model="inboundForm" label-width="100px">
        <el-form-item label="商品"><b>{{ inboundForm.productName }}</b></el-form-item>
        <el-form-item label="批次号"><el-input v-model="inboundForm.batchNo" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="数量" required><el-input-number v-model="inboundForm.quantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="inboundForm.unitPrice" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="inboundForm.supplier" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="生产日期"><el-date-picker v-model="inboundForm.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="过期日期"><el-date-picker v-model="inboundForm.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="inboundForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inboundDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmInbound" :loading="saving">入库</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialog" title="批量导入商品" width="780px">
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="导入模式">
          <el-radio-group v-model="importForm.mode">
            <el-radio value="UPSERT">存在则更新（推荐）</el-radio>
            <el-radio value="INSERT">仅新增</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="CSV 文件">
          <el-input type="textarea" v-model="importForm.text" :rows="8" placeholder="先把 CSV 文件内容粘贴到此处（或直接编辑 JSON）" />
          <div style="color: #c0c4cc; font-size: 12px; margin-top: 4px">
            支持 CSV 与 JSON 两种格式；CSV 必须含 productCode、productName 列
          </div>
        </el-form-item>
      </el-form>
      <el-alert v-if="importResult" :title="`导入完成：新增 ${importResult.inserted} · 更新 ${importResult.updated} · 跳过 ${importResult.skipped} · 错误 ${importResult.errorCount}`" :type="importResult.errorCount ? 'warning' : 'success'" :closable="false" style="margin-bottom: 8px" />
      <el-table v-if="importResult?.errors?.length" :data="importResult.errors" border size="small" max-height="200">
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column prop="msg" label="错误" />
      </el-table>
      <template #footer>
        <el-button @click="importDialog = false">关闭</el-button>
        <el-button type="primary" @click="confirmImport" :loading="importing">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Minus, Menu, Plus, Setting, Collection } from '@element-plus/icons-vue'
import stockApi from '@/api/stock'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const products = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filter = ref<any>({ keyword: '', category: '', status: null })
const dialog = ref(false)
const inboundDialog = ref(false)
const formRef = ref<FormInstance>()

// ===== 筛选条件持久化 =====
const FILTER_STORAGE_KEY = 'stock-product-filter'
function loadPersistedFilter() {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as any
    if (saved.keyword !== undefined) filter.value.keyword = saved.keyword
    if (saved.category !== undefined) filter.value.category = saved.category
    if (saved.status !== undefined) filter.value.status = saved.status
    if (saved.pageSize) pageSize.value = saved.pageSize
  } catch {}
}
watch(
  () => ({ k: filter.value.keyword, c: filter.value.category, s: filter.value.status, ps: pageSize.value }),
  (v) => localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(v)),
  { deep: false }
)

// ===== 表格密度 =====
const tableSize = ref<'small' | 'default' | 'large'>(
  (localStorage.getItem('stock-product-size') as any) || 'default'
)
watch(tableSize, (v) => localStorage.setItem('stock-product-size', v))

// ===== 列设置 =====
interface ColDef { prop: string; label: string; visible: boolean }
const DEFAULT_COLS: ColDef[] = [
  { prop: 'productCode', label: '编码', visible: true },
  { prop: 'productName', label: '名称', visible: true },
  { prop: 'category', label: '分类', visible: true },
  { prop: 'spec', label: '规格', visible: true },
  { prop: 'unit', label: '单位', visible: true },
  { prop: 'costPrice', label: '成本价', visible: true },
  { prop: 'salePrice', label: '售价', visible: true },
  { prop: 'minStock', label: '最低', visible: false },
  { prop: 'maxStock', label: '最高', visible: false },
  { prop: 'enableBatch', label: '批次', visible: true },
  { prop: 'status', label: '状态', visible: true }
]
const COL_STORAGE_KEY = 'stock-product-cols'
const columnDefs = ref<ColDef[]>([])
function loadColumnSettings() {
  try {
    const raw = localStorage.getItem(COL_STORAGE_KEY)
    if (raw) {
      const saved = JSON.parse(raw) as Record<string, boolean>
      columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c, visible: saved[c.prop] ?? c.visible }))
    } else {
      columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c }))
    }
  } catch {
    columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c }))
  }
}
function persistColumnSettings() {
  const obj: Record<string, boolean> = {}
  columnDefs.value.forEach((c) => (obj[c.prop] = c.visible))
  localStorage.setItem(COL_STORAGE_KEY, JSON.stringify(obj))
}
function resetColumnSettings() {
  columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c }))
  persistColumnSettings()
  ElMessage.success('已重置列设置')
}
function setAllCols(visible: boolean) {
  columnDefs.value.forEach((c) => (c.visible = visible))
  persistColumnSettings()
}
function isColVisible(prop: string) {
  return columnDefs.value.find((c) => c.prop === prop)?.visible ?? true
}

// ===== 保存视图 =====
interface SavedView { id: string; name: string; size: 'small' | 'default' | 'large'; cols: Record<string, boolean> }
const VIEWS_KEY = 'stock-product-views'
const savedViews = ref<SavedView[]>([])
function loadSavedViews() {
  try {
    const raw = localStorage.getItem(VIEWS_KEY)
    savedViews.value = raw ? JSON.parse(raw) : []
  } catch { savedViews.value = [] }
}
function persistSavedViews() {
  localStorage.setItem(VIEWS_KEY, JSON.stringify(savedViews.value))
}
async function saveCurrentAsView() {
  const { value: name } = await ElMessageBox.prompt('给当前视图起个名字（最多 20 字）', '保存视图', {
    inputValue: '',
    inputValidator: (v: string) => {
      const t = v?.trim() || ''
      if (!t) return '名称不能为空'
      if (t.length > 20) return '名称过长'
      if (savedViews.value.some((x) => x.name === t)) return '已存在同名视图'
      return true
    }
  }).catch(() => ({ value: '' }))
  if (!name) return
  const cols: Record<string, boolean> = {}
  columnDefs.value.forEach((c) => (cols[c.prop] = c.visible))
  savedViews.value.push({ id: 'v' + Date.now(), name: name.trim(), size: tableSize.value, cols })
  persistSavedViews()
  ElMessage.success('已保存视图')
}
function applyView(v: SavedView) {
  tableSize.value = v.size
  columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c, visible: v.cols[c.prop] ?? c.visible }))
  persistColumnSettings()
  ElMessage.success(`已应用视图：${v.name}`)
}
async function deleteView(v: SavedView) {
  await ElMessageBox.confirm(`确认删除视图「${v.name}」？`, '提示', { type: 'warning' })
  savedViews.value = savedViews.value.filter((x) => x.id !== v.id)
  persistSavedViews()
}
async function handleViewCmd(cmd: string) {
  if (cmd === 'save') return saveCurrentAsView()
  if (cmd === 'manage') {
    const { value: picked } = await ElMessageBox.prompt(
      `请输入要删除的视图名（${savedViews.value.map((v) => v.name).join(' / ')}）`,
      '管理视图',
      { inputValue: '', confirmButtonText: '删除', cancelButtonText: '关闭' }
    ).catch(() => ({ value: '' }))
    if (!picked) return
    const found = savedViews.value.find((v) => v.name === picked.trim())
    if (!found) return ElMessage.warning('未找到该视图')
    await deleteView(found)
    return
  }
  if (cmd.startsWith('apply:')) {
    const v = savedViews.value.find((x) => x.id === cmd.slice(6))
    if (v) applyView(v)
  }
}

// ===== 自适应高度 =====
const tableHeight = computed(() => {
  const h = window.innerHeight - 200 - 60 - 60 - 32
  return Math.max(420, h)
})

// ===== 搜索框 ref + 快捷键 =====
const keywordInputRef = ref<any>(null)
function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    keywordInputRef.value?.focus?.()
  }
}

// 批量导入
const importDialog = ref(false)
const importing = ref(false)
const importResult = ref<any>(null)
const importForm = ref<any>({ mode: 'UPSERT', text: '' })

// 顶部统计 + 分类图表
const stats = ref<any>({})
const alertStats = ref<any>({})
const categoryStats = ref<any[]>([])
const categoryChartRef = ref<HTMLElement>()
const loadingCategory = ref(false)
let categoryChart: any = null

const defaultForm = { productCode: '', productName: '', category: '耗材', unit: '瓶', minStock: 10, maxStock: 1000, enableBatch: 0, status: 1, costPrice: 0, salePrice: 0 }
const form = ref<any>({ ...defaultForm })

const inboundForm = ref<any>({ productId: 0, productName: '', batchNo: '', quantity: 1, unitPrice: 0, supplier: '', productionDate: '', expiryDate: '', remark: '' })

const rules: FormRules = {
  productCode: [{ required: true, message: '编码必填', trigger: 'blur' }],
  productName: [{ required: true, message: '名称必填', trigger: 'blur' }],
  category: [{ required: true, message: '分类必选', trigger: 'change' }]
}

const formatAmount = (v: any) => Number(v || 0).toFixed(2)

const loadStats = async () => {
  try {
    const res: any = await stockApi.stats({})
    const d = res.data || {}
    stats.value = d
    categoryStats.value = d.categoryStats || []
  } catch {}
  try {
    const res: any = await stockApi.alertStats({})
    alertStats.value = res.data || {}
  } catch {}
}

const renderCategoryChart = async () => {
  await nextTick()
  if (!categoryChartRef.value || !categoryStats.value.length) return
  if (!(window as any).echarts?.init) return
  categoryChart?.dispose()
  categoryChart = (window as any).echarts.init(categoryChartRef.value)
  const data = categoryStats.value.map((r: any) => ({ name: r.category, value: Number(r.value || 0) }))
  categoryChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0, left: 'center', icon: 'circle' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n¥{c}', fontSize: 11 }
    }]
  })
}

onMounted(async () => {
  loadColumnSettings()
  loadPersistedFilter()
  loadSavedViews()
  window.addEventListener('keydown', onGlobalKeydown)
  await loadStats()
  renderCategoryChart()
  await loadData()
  window.addEventListener('resize', () => categoryChart?.resize())
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await stockApi.productPage({ pageNum: pageNum.value, pageSize: pageSize.value, ...filter.value })
    products.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

const resetFilter = () => {
  filter.value = { keyword: '', category: '', status: null }
  pageNum.value = 1
  pageSize.value = 20
  loadData()
}

const openDialog = (row?: any) => {
  if (row) form.value = { ...row }
  else form.value = { ...defaultForm }
  dialog.value = true
  // 下一帧清空校验状态，避免旧提示残留
  setTimeout(() => formRef.value?.clearValidate(), 0)
}

const saveProduct = async () => {
  try { await formRef.value?.validate() } catch { return }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      enableBatch: form.value.enableBatch ? 1 : 0,
      status: form.value.status ? 1 : 0,
      costPrice: Number(form.value.costPrice) || 0,
      salePrice: Number(form.value.salePrice) || 0
    }
    if (payload.id) {
      await stockApi.updateProduct(payload.id, payload)
      ElMessage.success('更新成功')
    } else {
      await stockApi.createProduct(payload)
      ElMessage.success('创建成功')
    }
    dialog.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
  finally { saving.value = false }
}

const delProduct = async (row: any) => {
  await ElMessageBox.confirm(`确认删除商品 ${row.productName}?`, '提示', { type: 'warning' })
  try {
    await stockApi.deleteProduct(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '删除失败') }
}

const openInbound = (row: any) => {
  inboundForm.value = { productId: row.id, productName: row.productName, batchNo: '', quantity: 1, unitPrice: row.costPrice || 0, supplier: '', productionDate: '', expiryDate: '', remark: '' }
  inboundDialog.value = true
}

const confirmInbound = async () => {
  saving.value = true
  try {
    await stockApi.inbound(inboundForm.value)
    ElMessage.success('入库成功')
    inboundDialog.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '入库失败') }
  finally { saving.value = false }
}

const goTrace = (productId: number) => { router.push(`/stock/trace/${productId}`) }
const viewDetail = (row: any) => { router.push(`/stock/trace/${row.id}`) }
const exportExcel = () => { ElMessage.info('导出功能开发中') }

const openImportDialog = () => {
  importResult.value = null
  importForm.value = { mode: 'UPSERT', text: '' }
  importDialog.value = true
}

const downloadTemplate = () => {
  window.open('/api/stock/products/import-template', '_blank')
}

/** 简易 CSV 解析（支持双引号转义） */
const parseCsv = (text: string): string[][] => {
  const rows: string[][] = []
  let i = 0, field = '', row: string[] = [], inQuote = false
  while (i < text.length) {
    const c = text[i]
    if (inQuote) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i += 2; continue }
      if (c === '"') { inQuote = false; i++; continue }
      field += c; i++; continue
    }
    if (c === '"') { inQuote = true; i++; continue }
    if (c === ',') { row.push(field); field = ''; i++; continue }
    if (c === '\n' || c === '\r') {
      if (field.length > 0 || row.length > 0) { row.push(field); rows.push(row); row = []; field = '' }
      i++; if (c === '\r' && text[i] === '\n') i++
      continue
    }
    field += c; i++
  }
  if (field.length > 0 || row.length > 0) { row.push(field); rows.push(row) }
  return rows
}

const toItem = (headers: string[], values: string[]): any => {
  const obj: any = {}
  headers.forEach((h, idx) => {
    const v = (values[idx] ?? '').trim()
    if (v === '') return
    if (['costPrice', 'salePrice'].includes(h)) obj[h] = Number(v)
    else if (['minStock', 'maxStock', 'enableBatch', 'status'].includes(h)) obj[h] = Number(v)
    else obj[h] = v
  })
  return obj
}

const confirmImport = async () => {
  if (!importForm.value.text.trim()) { ElMessage.warning('请粘贴 CSV 内容'); return }
  let items: any[] = []
  try {
    const trimmed = importForm.value.text.trim()
    if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
      const parsed = JSON.parse(trimmed)
      items = Array.isArray(parsed) ? parsed : (parsed.items || [])
    } else {
      const rows = parseCsv(trimmed).filter(r => r.length && r.some(c => c.trim() !== ''))
      // 跳过注释行（以 # 开头）
      const dataRows = rows.filter(r => !(r[0] || '').trim().startsWith('#'))
      if (dataRows.length < 2) throw new Error('至少需要包含表头 + 1 行数据')
      const headers = dataRows[0].map(h => h.trim())
      items = dataRows.slice(1).map(r => toItem(headers, r))
    }
  } catch (e: any) {
    ElMessage.error('解析失败：' + (e.message || e))
    return
  }
  if (!items.length) { ElMessage.warning('无可导入的明细'); return }
  importing.value = true
  try {
    // 用 stock.product.import:{用户id}:{时间戳} 作幂等键
    const idemKey = 'product-import-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
    const res: any = await stockApi.productImport({ mode: importForm.value.mode, items }, idemKey)
    importResult.value = res.data || {}
    ElMessage.success('导入完成')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.product-page { padding: 12px; }
.search-bar { margin-bottom: 12px; }
.search-bar .el-input, .search-bar .el-select { margin-right: 8px; }
.text-muted { color: #c0c4cc; font-style: italic; font-size: 12px; }
:deep(.el-table__row) { cursor: pointer; }

/* 顶部 KPI 卡片 */
.kpi-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 12px; }
.kpi-card {
  padding: 16px 20px;
  border-radius: 8px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  position: relative;
  overflow: hidden;
}
.kpi-card::after {
  content: '';
  position: absolute;
  right: -20px; top: -20px;
  width: 80px; height: 80px;
  border-radius: 50%;
  background: rgba(255,255,255,0.12);
}
.kpi-label { font-size: 13px; opacity: 0.9; }
.kpi-value { font-size: 28px; font-weight: 600; margin: 6px 0 4px; letter-spacing: 0.5px; }
.kpi-sub { font-size: 12px; opacity: 0.85; }
.kpi-blue { background: linear-gradient(135deg, #1890ff, #36cfc9); }
.kpi-purple { background: linear-gradient(135deg, #722ed1, #b37feb); }
.kpi-orange { background: linear-gradient(135deg, #fa8c16, #ffc069); }
.kpi-red { background: linear-gradient(135deg, #f5222d, #ff7875); }
.kpi-green { background: linear-gradient(135deg, #52c41a, #95de64); }

/* 分类图表面板 */
.chart-card { margin-bottom: 12px; }
.chart-header { display: flex; justify-content: space-between; align-items: center; }
.chart-meta { font-size: 12px; color: #909399; }
.chart-placeholder {
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
}

@media (max-width: 1100px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 600px) { .kpi-grid { grid-template-columns: 1fr; } }

.toolbar-spacer { flex: 1; }
.column-settings {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 320px;
  overflow-y: auto;
}
.column-settings-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}
</style>
