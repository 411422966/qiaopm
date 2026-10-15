<template>
  <div class="review-tpl-page">

    <!-- KPI -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.total || 0 }}</div>
          <div class="stat-label">模板总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-green">
          <div class="stat-value">{{ stats.enabled || 0 }}</div>
          <div class="stat-label">启用中</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-orange">
          <div class="stat-value">{{ stats.disabled || 0 }}</div>
          <div class="stat-label">已停用</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-blue">
          <div class="stat-value">{{ stats.avgDelay || 0 }}</div>
          <div class="stat-label">平均延迟(分钟)</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 触发事件 + 渠道分布 -->
    <el-row :gutter="12" class="stats-row">
      <el-col :span="14">
        <el-card>
          <template #header><span><el-icon><Operation /></el-icon> 触发事件分布</span></template>
          <div v-if="!triggerEntries.length" class="muted empty-mini">暂无模板</div>
          <div v-else class="bar-row">
            <div v-for="(e, i) in triggerEntries" :key="e.key" class="bar-line">
              <div class="bar-label">
                <span class="bar-rank" :class="{ 'top1': i === 0, 'top2': i === 1, 'top3': i === 2 }">{{ i + 1 }}</span>
                {{ triggerLabel(e.key) }}
              </div>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: pct(e.count) + '%', background: triggerColor(i) }">
                  <span class="bar-num">{{ e.count }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header><span><el-icon><Promotion /></el-icon> 触达渠道分布</span></template>
          <div v-if="!channelEntries.length" class="muted empty-mini">暂无数据</div>
          <div v-else class="channel-list">
            <div v-for="(e, i) in channelEntries" :key="e.key" class="chip-row">
              <el-tag :type="i === 0 ? 'success' : i === 1 ? 'warning' : 'info'" effect="dark">
                {{ channelLabel(e.key) }}
              </el-tag>
              <span class="chip-num"><strong>{{ e.count }}</strong></span>
              <span class="chip-pct">{{ pct(e.count) }}%</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="编码 / 名称" style="width: 220px" clearable
        @keyup.enter="resetPage" />
      <el-select v-model="filter.enabled" placeholder="状态" clearable style="width: 110px" @change="resetPage">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="resetPage">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <div style="flex: 1" />
      <el-button type="success" plain @click="seedDemo">灌入示例</el-button>
      <el-button type="primary" @click="openCreate">+ 新增模板</el-button>
      <el-button @click="refresh" :loading="loading">刷新</el-button>
    </el-card>

    <!-- 列表 -->
    <el-card class="table-card">
      <el-table :data="rows" border stripe highlight-current-row @row-click="selectRow">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="templateCode" label="编码" width="200" show-overflow-tooltip />
        <el-table-column prop="templateName" label="名称" min-width="160" />
        <el-table-column prop="triggerEvent" label="触发事件" width="160">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ triggerLabel(row.triggerEvent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dimensions" label="评价维度" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="d in (row.dimensions || '').split(',').filter(Boolean)" :key="d"
              size="small" effect="plain" style="margin-right: 3px">{{ d }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tagsPool" label="标签池" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="t in (row.tagsPool || '').split(',').filter(Boolean).slice(0, 3)" :key="t"
              size="small" type="info" style="margin-right: 3px">{{ t }}</el-tag>
            <span v-if="(row.tagsPool || '').split(',').length > 3" class="muted">
              +{{ (row.tagsPool || '').split(',').length - 3 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="introText" label="引导语" min-width="180" show-overflow-tooltip />
        <el-table-column prop="touchChannel" label="渠道" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.touchChannel === 'MINI_PROGRAM' ? 'success' : row.touchChannel === 'SMS' ? 'warning' : 'info'">
              {{ channelLabel(row.touchChannel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="延迟" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ 'dim-fade': !row.touchDelayMinutes }">
              {{ row.touchDelayMinutes || 0 }}分
            </span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1"
              @change="(v: boolean) => toggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="preview(row)">预览</el-button>
            <el-button link size="small" type="warning" @click="simulate(row)">模拟推送</el-button>
            <el-button link size="small" @click="clone(row)">克隆</el-button>
            <el-button link size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该模板？" @confirm="remove(row)">
              <template #reference>
                <el-button link size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新建/编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑模板' : '新增模板'" width="700px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" required><el-input v-model="form.templateCode" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required><el-input v-model="form.templateName" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="触发事件">
          <el-select v-model="form.triggerEvent" style="width: 100%">
            <el-option label="订单完成" value="ORDER_COMPLETED" />
            <el-option label="套餐交付" value="CARD_DELIVERED" />
            <el-option label="服务完成" value="SERVICE_COMPLETED" />
            <el-option label="生日" value="BIRTHDAY" />
            <el-option label="到期" value="EXPIRE" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="评价维度(CSV)">
          <el-input v-model="form.dimensions" placeholder="服务,技术,环境,价格" />
        </el-form-item>
        <el-form-item label="标签池(CSV)">
          <el-input v-model="form.tagsPool" placeholder="专业,细致,热情,手法好" />
        </el-form-item>
        <el-form-item label="引导语">
          <el-input v-model="form.introText" type="textarea" :rows="2" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="触达渠道">
              <el-select v-model="form.touchChannel" style="width: 100%">
                <el-option label="小程序" value="MINI_PROGRAM" />
                <el-option label="短信" value="SMS" />
                <el-option label="内部" value="INTERNAL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="延迟(分钟)">
              <el-input-number v-model="form.touchDelayMinutes" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板预览（手机端评价卡样） -->
    <el-drawer v-model="previewVisible" :title="`模板预览 · ${previewRow?.templateName || ''}`" size="420px">
      <div v-if="previewRow" class="preview-wrap">
        <div class="phone-frame">
          <div class="phone-header">
            <div class="phone-brand">美业管家</div>
            <div class="phone-channel">
              <el-tag size="small" :type="previewRow.touchChannel === 'MINI_PROGRAM' ? 'success' : 'warning'">
                {{ channelLabel(previewRow.touchChannel) }}
              </el-tag>
            </div>
          </div>
          <div class="phone-body">
            <div class="phone-title">亲爱的会员，给我们打个分吧～</div>
            <div class="phone-intro">{{ previewRow.introText || '——' }}</div>

            <div class="phone-section">
              <div class="sec-label">本次评分</div>
              <div class="rate-row">
                <el-rate v-model="previewRate" disabled show-score />
              </div>
            </div>

            <div class="phone-section" v-if="previewDims.length">
              <div class="sec-label">维度评价</div>
              <div v-for="d in previewDims" :key="d" class="dim-row">
                <span class="dim-name">{{ d }}</span>
                <el-rate v-model="previewDimsRate[d]" disabled size="small" />
              </div>
            </div>

            <div class="phone-section" v-if="previewTags.length">
              <div class="sec-label">打个标签</div>
              <div class="tag-pool">
                <el-tag v-for="t in previewTags" :key="t" size="small" effect="plain" style="margin: 2px">
                  {{ t }}
                </el-tag>
              </div>
            </div>

            <div class="phone-section">
              <el-input v-model="previewContent" type="textarea" :rows="3"
                placeholder="想说点什么…" disabled />
            </div>

            <el-button type="primary" style="width: 100%; margin-top: 16px" disabled>提交评价</el-button>
          </div>
          <div class="phone-footer">
            <small>预览 · DEMO</small>
          </div>
        </div>

        <el-divider>模板信息</el-divider>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="编码">{{ previewRow.templateCode }}</el-descriptions-item>
          <el-descriptions-item label="触发事件">{{ triggerLabel(previewRow.triggerEvent) }}</el-descriptions-item>
          <el-descriptions-item label="触达渠道">{{ channelLabel(previewRow.touchChannel) }}</el-descriptions-item>
          <el-descriptions-item label="延迟">{{ previewRow.touchDelayMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ previewRow.createTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <!-- 模拟推送结果 -->
    <el-dialog v-model="simVisible" title="模拟推送结果" width="540px">
      <div v-if="simData" class="sim-card">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="模板">
            <strong>{{ simData.templateName }}</strong>
            <small style="color: #909399"> · {{ simData.templateCode }}</small>
          </el-descriptions-item>
          <el-descriptions-item label="触发事件">{{ triggerLabel(simData.triggerEvent) }}</el-descriptions-item>
          <el-descriptions-item label="触达渠道">{{ channelLabel(simData.channel) }}</el-descriptions-item>
          <el-descriptions-item label="延迟">{{ simData.delayMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="模拟时间">{{ simData.simulateAt }}</el-descriptions-item>
          <el-descriptions-item label="预计发送">{{ simData.willSendAt }}</el-descriptions-item>
          <el-descriptions-item label="预览链接">
            <a :href="simData.previewUrl" target="_blank">{{ simData.previewUrl }}</a>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert :title="simData.note" type="info" :closable="false" style="margin-top: 12px" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const filter = reactive({ pageNum: 1, pageSize: 20, keyword: '', enabled: undefined as number | undefined })
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = reactive<any>({ total: 0, enabled: 0, disabled: 0, avgDelay: 0, triggerDist: {}, channelDist: {} })

const triggerEntries = computed(() => Object.entries(stats.triggerDist || {})
  .map(([k, v]) => ({ key: k, count: v as number }))
  .sort((a, b) => b.count - a.count))
const channelEntries = computed(() => Object.entries(stats.channelDist || {})
  .map(([k, v]) => ({ key: k, count: v as number }))
  .sort((a, b) => b.count - a.count))
function pct(n: number) {
  const max = Math.max(1, ...triggerEntries.value.map((x: any) => x.count))
  return Math.round((n / max) * 100)
}

function triggerLabel(t: string) {
  return ({ ORDER_COMPLETED: '订单完成', CARD_DELIVERED: '套餐交付', SERVICE_COMPLETED: '服务完成', BIRTHDAY: '生日', EXPIRE: '到期', CUSTOM: '自定义' } as any)[t] || t || '—'
}
function channelLabel(t: string) {
  return ({ MINI_PROGRAM: '小程序', SMS: '短信', INTERNAL: '内部' } as any)[t] || t || '—'
}
function triggerColor(i: number) {
  return ['#67c23a', '#409eff', '#e6a23c', '#f56c6c', '#909399', '#9c27b0', '#00bcd4'][i] || '#909399'
}

async function loadData() {
  loading.value = true
  try {
    const params: any = { pageNum: filter.pageNum, pageSize: filter.pageSize }
    if (filter.keyword) params.keyword = filter.keyword
    if (filter.enabled !== undefined) params.enabled = filter.enabled
    const r: any = await http.get('/crm/care/review/tpl/list', { params })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const r: any = await http.get('/crm/care/review/tpl/stats')
    Object.assign(stats, r.data || {})
  } catch { /* 静默 */ }
}

async function refresh() {
  await Promise.all([loadStats(), loadData()])
}

function resetPage() { filter.pageNum = 1; loadData() }
function resetFilter() {
  Object.assign(filter, { pageNum: 1, pageSize: 20, keyword: '', enabled: undefined })
  loadData()
}
function selectRow(row: any) { /* 选中行（可扩展详情） */ }

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive({
  templateCode: '', templateName: '', triggerEvent: 'ORDER_COMPLETED',
  dimensions: '', tagsPool: '', introText: '',
  touchChannel: 'MINI_PROGRAM', touchDelayMinutes: 60, enabled: 1, operatorName: 'admin'
})

function openCreate() {
  dialog.id = undefined
  Object.assign(form, {
    templateCode: '', templateName: '', triggerEvent: 'ORDER_COMPLETED',
    dimensions: '', tagsPool: '', introText: '',
    touchChannel: 'MINI_PROGRAM', touchDelayMinutes: 60, enabled: 1, operatorName: 'admin'
  })
  dialog.visible = true
}
function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, {
    templateCode: row.templateCode, templateName: row.templateName,
    triggerEvent: row.triggerEvent, dimensions: row.dimensions,
    tagsPool: row.tagsPool, introText: row.introText,
    touchChannel: row.touchChannel, touchDelayMinutes: row.touchDelayMinutes,
    enabled: row.enabled, operatorName: row.operatorName || 'admin'
  })
  dialog.visible = true
}
function resetDialog() {
  dialog.id = undefined
  dialog.saving = false
}

async function save() {
  if (!form.templateCode || !form.templateName) return ElMessage.warning('编码 / 名称必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    const body = { ...form }
    if (dialog.id) (body as any).id = dialog.id
    await http.post('/crm/care/review/tpl', body, { headers })
    ElMessage.success('已保存')
    dialog.visible = false
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    dialog.saving = false
  }
}

async function toggle(row: any, v: boolean) {
  try {
    await http.post(`/crm/care/review/tpl/${row.id}/toggle`, { enabled: v ? 1 : 0 })
    row.enabled = v ? 1 : 0
    ElMessage.success(v ? '已启用' : '已停用')
    await loadStats()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function clone(row: any) {
  try {
    const r: any = await http.post(`/crm/care/review/tpl/${row.id}/clone`)
    ElMessage.success(`已克隆：${r.data?.templateCode}`)
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '克隆失败')
  }
}

async function remove(row: any) {
  try {
    await http.post(`/crm/care/review/tpl/${row.id}/delete`)
    ElMessage.success('已删除')
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

// 预览
const previewVisible = ref(false)
const previewRow = ref<any>(null)
const previewRate = ref(5)
const previewDims = ref<string[]>([])
const previewDimsRate = reactive<Record<string, number>>({})
const previewTags = ref<string[]>([])
const previewContent = ref('DEMO 内容')
function preview(row: any) {
  previewRow.value = row
  previewDims.value = (row.dimensions || '').split(',').filter(Boolean)
  for (const d of previewDims.value) previewDimsRate[d] = 5
  previewTags.value = (row.tagsPool || '').split(',').filter(Boolean)
  previewRate.value = 5
  previewContent.value = 'DEMO 内容'
  previewVisible.value = true
}

// 模拟推送
const simVisible = ref(false)
const simData = ref<any>(null)
async function simulate(row: any) {
  try {
    const r: any = await http.post(`/crm/care/review/tpl/${row.id}/simulate`)
    simData.value = r.data
    simVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '模拟失败')
  }
}

// 灌入示例
async function seedDemo() {
  try {
    await ElMessageBox.confirm('将灌入 5 条示例模板。继续？', '提示', { type: 'warning' })
  } catch { return }
  await http.post('/crm/care/review/tpl/demo-seed')
  ElMessage.success('已灌入')
  await refresh()
}

onMounted(async () => { await refresh() })
</script>

<style scoped>
.review-tpl-page { padding: 12px; }
.stat-card { text-align: center; padding: 14px 0; }
.stat-value { font-size: 26px; font-weight: 600; color: #303133; }
.stat-label { color: #909399; font-size: 12px; margin-top: 4px; }
.stat-card.stat-blue .stat-value { color: #409eff; }
.stat-card.stat-orange .stat-value { color: #e6a23c; }
.stat-card.stat-green .stat-value { color: #67c23a; }
.stats-row { margin-bottom: 12px; }
.muted { color: #909399; }
.empty-mini { padding: 24px; text-align: center; }
.search-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.bar-row { padding: 4px 0; }
.bar-line { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.bar-label { width: 130px; font-size: 13px; color: #606266; display: flex; align-items: center; gap: 6px; }
.bar-rank { width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; background: #f0f2f5; color: #909399; font-size: 11px; }
.bar-rank.top1 { background: #f56c6c; color: #fff; }
.bar-rank.top2 { background: #e6a23c; color: #fff; }
.bar-rank.top3 { background: #67c23a; color: #fff; }
.bar-track { flex: 1; height: 16px; background: #f5f7fa; border-radius: 8px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 8px; min-width: 32px; display: flex; align-items: center; justify-content: flex-end; padding-right: 8px; color: #fff; font-size: 12px; font-weight: 600; transition: width .3s; }
.bar-num { color: #fff; }
.channel-list { padding: 4px 0; }
.chip-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; }
.chip-num { color: #303133; font-size: 13px; }
.chip-num strong { font-size: 18px; }
.chip-pct { color: #909399; font-size: 12px; }
.dim-fade { color: #c0c4cc; }
.pagination { display: flex; justify-content: flex-end; margin-top: 12px; }

.preview-wrap { padding: 0 8px; }
.phone-frame {
  width: 300px;
  margin: 0 auto;
  border: 1px solid #e6e8eb;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(0,0,0,0.06);
  overflow: hidden;
}
.phone-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
}
.phone-brand { font-weight: 600; font-size: 14px; }
.phone-body { padding: 16px; }
.phone-title { font-size: 16px; font-weight: 600; margin-bottom: 8px; color: #303133; }
.phone-intro { font-size: 12px; color: #909399; margin-bottom: 16px; line-height: 1.6; }
.phone-section { margin-bottom: 14px; }
.sec-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.dim-row { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
.dim-name { font-size: 13px; color: #303133; }
.tag-pool { display: flex; flex-wrap: wrap; gap: 4px; }
.phone-footer {
  text-align: center;
  padding: 8px;
  background: #f8f9fb;
  color: #c0c4cc;
  font-size: 11px;
}
.sim-card { padding: 0 12px; }
</style>