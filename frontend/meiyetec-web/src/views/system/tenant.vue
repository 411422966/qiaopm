<template>
  <div class="page">
    <!-- 顶部统计卡片 -->
    <div class="stat-row">
      <div class="stat-card stat-total">
        <div class="stat-label">租户总数</div>
        <div class="stat-value">{{ stats.total }}</div>
        <el-icon class="stat-icon"><OfficeBuilding /></el-icon>
      </div>
      <div class="stat-card stat-active">
        <div class="stat-label">启用中</div>
        <div class="stat-value">{{ stats.active }}</div>
        <el-icon class="stat-icon"><CircleCheck /></el-icon>
      </div>
      <div class="stat-card stat-soon">
        <div class="stat-label">即将到期 (30天内)</div>
        <div class="stat-value">{{ stats.expiringSoon }}</div>
        <el-icon class="stat-icon"><Bell /></el-icon>
      </div>
      <div class="stat-card stat-expired">
        <div class="stat-label">已过期</div>
        <div class="stat-value">{{ stats.expired }}</div>
        <el-icon class="stat-icon"><CircleClose /></el-icon>
      </div>
    </div>

    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索租户名称/编码" clearable style="width:240px" @keyup.enter="load(1)" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width:120px" @change="load(1)">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-select v-model="filterExpire" placeholder="到期范围" clearable style="width:140px" @change="load(1)">
          <el-option label="已过期" value="expired" />
          <el-option label="30天内到期" value="soon" />
          <el-option label="90天内到期" value="90d" />
          <el-option label="长期有效" value="none" />
        </el-select>
        <el-button type="primary" @click="load(1)"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
        <el-button @click="openCreate"><el-icon><Plus /></el-icon>&nbsp;新增租户</el-button>
        <div class="toolbar-spacer" />
        <el-button size="small" @click="refreshStats"><el-icon><Refresh /></el-icon>&nbsp;刷新统计</el-button>
      </div>

      <el-table :data="filteredList" v-loading="loading" stripe @row-click="openDetail" style="cursor: pointer">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="tenantCode" label="租户编码" width="140" />
        <el-table-column prop="tenantName" label="租户名称" min-width="160" />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column label="用户数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.userCount ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0"
              @change="(v: number) => onToggleStatus(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="到期时间" width="220">
          <template #default="{ row }">
            <div class="expire-cell">
              <el-date-picker
                v-model="row.expireAt"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="长期"
                size="small"
                style="width: 170px"
                @change="(v: string) => onExpireChange(row, v)"
              />
              <el-tag v-if="expireTag(row.expireAt)" :type="expireTag(row.expireAt)!.type" size="small">
                {{ expireTag(row.expireAt)!.text }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="openConfig(row)">配置</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
          :total="total" :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next,jumper" @current-change="load()" @size-change="load(1)" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑租户' : '新增租户'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="租户编码" required>
          <el-input v-model="form.tenantCode" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="租户名称" required>
          <el-input v-model="form.tenantName" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="Logo URL">
          <el-input v-model="form.logoUrl" placeholder="https://" />
        </el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择到期时间" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 租户配置抽屉 -->
    <el-drawer v-model="configVisible" :title="`租户配置：${configTenantName}`" size="500px" direction="rtl">
      <div v-loading="configLoading" class="config-body">
        <el-form v-if="configList.length > 0" label-width="140px">
          <el-form-item v-for="item in configList" :key="item.cfgKey" :label="item.description || item.cfgKey">
            <el-input v-model="item.cfgValue" :placeholder="item.cfgKey" clearable />
          </el-form-item>
        </el-form>
        <el-empty v-else description="暂无配置项" />
        <div class="config-footer">
          <el-button type="primary" :loading="configSaving" @click="saveConfig">保存配置</el-button>
          <el-button @click="configVisible = false">取消</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 租户详情抽屉（行点击触发） -->
    <el-drawer v-model="detailVisible" :title="`租户详情：${detailData?.tenantName ?? ''}`" size="480px" direction="rtl">
      <div v-loading="detailLoading" v-if="detailData" class="detail-body">
        <div class="detail-header">
          <div class="detail-name">{{ detailData.tenantName }}</div>
          <div class="detail-code">{{ detailData.tenantCode }}</div>
          <div class="detail-tags">
            <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
              {{ detailData.status === 1 ? '启用中' : '已停用' }}
            </el-tag>
            <el-tag v-if="detailData.expired" type="danger" size="small">已过期</el-tag>
            <el-tag v-else-if="detailData.expireAt" type="info" size="small">到期：{{ detailData.expireAt }}</el-tag>
            <el-tag v-else size="small">长期有效</el-tag>
          </div>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="租户ID">{{ detailData.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailData.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="用户总数" :span="2">
            <span class="big-num">{{ detailData.userTotal ?? 0 }}</span>
            <span class="num-sub">（启用 {{ detailData.userActive ?? 0 }} / 停用 {{ detailData.userInactive ?? 0 }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="到期时间" :span="2">
            {{ detailData.expireAt || '长期有效' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-tip">
          <el-icon><InfoFilled /></el-icon>
          点击行查看详细统计，点击右侧"编辑/删除"按钮可对租户进行操作。
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  OfficeBuilding, CircleCheck, Bell, CircleClose,
  Search, Plus, Refresh, InfoFilled
} from '@element-plus/icons-vue'
import { sysApi, type SysTenant } from '@/api/sys'

const list = ref<SysTenant[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const filterStatus = ref<number | null>(null)
const filterExpire = ref<string | ''>('')
const loading = ref(false)
const saving = ref(false)

const stats = reactive({ total: 0, active: 0, expiringSoon: 0, expired: 0 })

const dialogVisible = ref(false)
const form = reactive<SysTenant>({
  id: undefined, tenantCode: '', tenantName: '', contactName: '',
  contactPhone: '', address: '', logoUrl: '', status: 1, expireAt: ''
})

// 前端按 filterStatus / filterExpire 在内存里再过滤一次（避免后端改）
const filteredList = computed(() => {
  return list.value.filter(r => {
    if (filterStatus.value !== null && r.status !== filterStatus.value) return false
    if (filterExpire.value) {
      const tag = expireTag(r.expireAt)
      if (filterExpire.value === 'expired' && tag?.type !== 'danger') return false
      if (filterExpire.value === 'soon' && tag?.type !== 'warning') return false
      if (filterExpire.value === '90d' && !(daysToExpire(r.expireAt) !== null && daysToExpire(r.expireAt)! <= 90 && daysToExpire(r.expireAt)! >= 0)) return false
      if (filterExpire.value === 'none' && r.expireAt) return false
    }
    return true
  })
})

function daysToExpire(expireAt?: string | null): number | null {
  if (!expireAt) return null
  const d = new Date(expireAt.replace(/-/g, '/')).getTime()
  if (isNaN(d)) return null
  return Math.floor((d - Date.now()) / 86400000)
}

function expireTag(expireAt?: string | null): { type: 'danger' | 'warning' | 'success'; text: string } | null {
  const days = daysToExpire(expireAt)
  if (days === null) return null
  if (days < 0) return { type: 'danger', text: `已过期 ${-days}天` }
  if (days <= 30) return { type: 'warning', text: `${days}天后到期` }
  return null
}

async function load(p?: number) {
  if (p) pageNum.value = p
  loading.value = true
  try {
    const r: any = await sysApi.tenantPage(pageNum.value, pageSize.value, keyword.value)
    list.value = r.data.records || []
    total.value = r.data.total || 0
    refreshStats()
  } finally {
    loading.value = false
  }
}

function refreshStats() {
  stats.total = list.value.length
  stats.active = list.value.filter(r => r.status === 1).length
  stats.expiringSoon = list.value.filter(r => {
    const d = daysToExpire(r.expireAt)
    return d !== null && d >= 0 && d <= 30
  }).length
  stats.expired = list.value.filter(r => {
    const d = daysToExpire(r.expireAt)
    return d !== null && d < 0
  }).length
}

function openCreate() {
  Object.assign(form, {
    id: undefined, tenantCode: '', tenantName: '', contactName: '',
    contactPhone: '', address: '', logoUrl: '', status: 1, expireAt: ''
  })
  dialogVisible.value = true
}

function openEdit(row: SysTenant) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submit() {
  if (!form.tenantCode || !form.tenantName) {
    ElMessage.warning('请填写租户编码和名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await sysApi.tenantUpdate(form)
    } else {
      await sysApi.tenantCreate(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row: SysTenant) {
  await ElMessageBox.confirm(`确认删除租户「${row.tenantName}」？`, '提示', { type: 'warning' })
  await sysApi.tenantDelete(row.id!)
  ElMessage.success('已删除')
  load()
}

// ① 状态开关：二次确认 + 过期自动冻结
async function onToggleStatus(row: SysTenant, v: number) {
  const days = daysToExpire(row.expireAt)
  const action = v === 1 ? '启用' : '停用'

  // 过期租户不允许启用
  if (v === 1 && days !== null && days < 0) {
    ElMessage.warning(`租户已过期 ${-days} 天，无法启用，请先延期`)
    row.status = 0
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认${action}租户「${row.tenantName}」？${days !== null && days <= 30 && days >= 0 ? `\n提示：该租户 ${days} 天后到期` : ''}`,
      '状态变更确认',
      { type: 'warning', confirmButtonText: `确认${action}`, cancelButtonText: '取消' }
    )
  } catch {
    row.status = v === 1 ? 0 : 1  // 用户取消，恢复原值
    return
  }

  try {
    await sysApi.tenantStatus(row.id!, v)
    ElMessage.success(`已${action}「${row.tenantName}」`)
    refreshStats()
  } catch {
    row.status = v === 1 ? 0 : 1
  }
}

// ④ 到期时间单独可编辑
async function onExpireChange(row: SysTenant, v: string | null) {
  const next = v || ''
  try {
    await sysApi.tenantUpdate({ ...row, expireAt: next })
    ElMessage.success(`已更新「${row.tenantName}」到期时间`)
    refreshStats()
  } catch {
    ElMessage.error('更新失败，已恢复')
    load()
  }
}

onMounted(() => load(1))

// ========== 租户配置抽屉 ==========
const configVisible = ref(false)
const configLoading = ref(false)
const configSaving = ref(false)
const configTenantId = ref<number | null>(null)
const configTenantName = ref('')
const configList = ref<{ cfgKey: string; cfgValue: string; description: string }[]>([])

async function openConfig(row: SysTenant) {
  configTenantId.value = row.id!
  configTenantName.value = row.tenantName
  configVisible.value = true
  configLoading.value = true
  configList.value = []
  try {
    const r: any = await sysApi.tenantConfig(row.id!)
    configList.value = Object.entries(r.data || {}).map(([k, v]) => ({
      cfgKey: k, cfgValue: String(v ?? ''), description: k
    }))
  } finally {
    configLoading.value = false
  }
}

async function saveConfig() {
  if (configTenantId.value === null) return
  configSaving.value = true
  const configs: Record<string, string> = {}
  for (const item of configList.value) {
    configs[item.cfgKey] = item.cfgValue
  }
  try {
    await sysApi.tenantConfigSave(configTenantId.value, configs)
    ElMessage.success('配置保存成功')
    configVisible.value = false
  } finally {
    configSaving.value = false
  }
}

// ========== 详情抽屉 ==========
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<any>(null)

async function openDetail(row: SysTenant) {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const r: any = await sysApi.tenantStats(row.id!)
    detailData.value = r.data
  } finally {
    detailLoading.value = false
  }
}
</script>

<style scoped>
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px }
.stat-card { position: relative; background: #fff; border-radius: 8px; padding: 16px 20px; box-shadow: 0 1px 4px rgba(0,0,0,.06); overflow: hidden }
.stat-label { color: #999; font-size: 13px }
.stat-value { font-size: 28px; font-weight: 700; margin-top: 4px; color: #303133 }
.stat-icon { position: absolute; right: 16px; top: 50%; transform: translateY(-50%); font-size: 40px; opacity: .15 }
.stat-total { border-left: 4px solid #409eff }
.stat-total .stat-icon, .stat-total .stat-value { color: #409eff }
.stat-active { border-left: 4px solid #67c23a }
.stat-active .stat-icon, .stat-active .stat-value { color: #67c23a }
.stat-soon { border-left: 4px solid #e6a23c }
.stat-soon .stat-icon, .stat-soon .stat-value { color: #e6a23c }
.stat-expired { border-left: 4px solid #f56c6c }
.stat-expired .stat-icon, .stat-expired .stat-value { color: #f56c6c }

.toolbar { display: flex; gap: 10px; margin-bottom: 14px; align-items: center }
.toolbar-spacer { flex: 1 }
.expire-cell { display: flex; align-items: center; gap: 6px }
.pagination { display: flex; justify-content: flex-end; margin-top: 14px }

/* 详情抽屉 */
.detail-body { padding: 0 12px }
.detail-header { padding: 8px 0 16px; border-bottom: 1px solid #eee; margin-bottom: 16px }
.detail-name { font-size: 20px; font-weight: 700; color: #303133 }
.detail-code { font-size: 13px; color: #909399; margin-top: 4px }
.detail-tags { margin-top: 10px; display: flex; gap: 6px; flex-wrap: wrap }
.big-num { font-size: 22px; font-weight: 700; color: #409eff }
.num-sub { margin-left: 8px; color: #909399; font-size: 13px }
.detail-tip { margin-top: 16px; padding: 10px 12px; background: #f4f4f5; border-radius: 6px; font-size: 13px; color: #909399; display: flex; gap: 6px; align-items: center }
.detail-tip .el-icon { color: #909399 }
.config-body { padding: 0 12px }
.config-footer { margin-top: 20px; display: flex; gap: 10px }
</style>