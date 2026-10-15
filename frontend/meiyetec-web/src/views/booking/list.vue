<template>
  <div class="bk-page">
    <!-- 顶部统计 -->
    <div class="stat-row">
      <div class="stat-card s-1">
        <div class="stat-label">今日预约</div>
        <div class="stat-num">{{ stats.today }}</div>
        <small>今日共 {{ stats.today }} 条</small>
      </div>
      <div class="stat-card s-2">
        <div class="stat-label">待确认</div>
        <div class="stat-num">{{ stats.pending }}</div>
        <small>需店长/前台跟进</small>
      </div>
      <div class="stat-card s-3">
        <div class="stat-label">已签到</div>
        <div class="stat-num">{{ stats.checkedIn }}</div>
        <small>当前服务中</small>
      </div>
      <div class="stat-card s-4">
        <div class="stat-label">已完成</div>
        <div class="stat-num">{{ stats.done }}</div>
        <small>历史总完成</small>
      </div>
      <div class="stat-card s-5">
        <div class="stat-label">已取消/爽约</div>
        <div class="stat-num">{{ stats.cancel + stats.noShow }}</div>
        <small>取消 {{ stats.cancel }} · 爽约 {{ stats.noShow }}</small>
      </div>
    </div>

    <!-- 筛选条 -->
    <el-card shadow="never">
      <div class="filter-bar">
        <el-button-group>
          <el-button :type="rangeTab === 'today' ? 'primary' : 'default'" @click="pickRange('today')">今日</el-button>
          <el-button :type="rangeTab === 'tomorrow' ? 'primary' : 'default'" @click="pickRange('tomorrow')">明日</el-button>
          <el-button :type="rangeTab === 'week' ? 'primary' : 'default'" @click="pickRange('week')">本周</el-button>
          <el-button :type="rangeTab === 'all' ? 'primary' : 'default'" @click="pickRange('all')">全部</el-button>
        </el-button-group>
        <el-date-picker v-model="filterRange" type="daterange" range-separator="至"
                        start-placeholder="开始日期" end-placeholder="结束日期"
                        value-format="YYYY-MM-DD" @change="onRangeChange" style="width: 260px" />
        <el-select v-model="filterStatus" placeholder="状态" clearable @change="onSearch" style="width: 130px">
          <el-option v-for="(l, k) in STATUS" :key="k" :value="Number(k)" :label="l" />
        </el-select>
        <el-select v-model="filterMember" placeholder="会员" clearable filterable @change="onSearch" style="width: 140px">
          <el-option v-for="m in members" :key="m.id" :value="m.id" :label="m.name" />
        </el-select>
        <el-select v-model="filterEmployee" placeholder="技师" clearable filterable @change="onSearch" style="width: 140px">
          <el-option v-for="e in employees" :key="e.id" :value="e.id" :label="e.employeeName" />
        </el-select>
        <el-input v-model="kw" placeholder="预约号 / 项目" clearable style="width: 200px" @input="onSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="onReset">重置</el-button>
        <div class="filter-spacer"></div>
        <el-button type="primary" :icon="Plus" @click="formVisible = true">新建预约</el-button>
        <el-button type="success" :icon="Download" @click="exportCsv" plain>导出CSV</el-button>
        <el-button :icon="Printer" @click="printList" plain>打印</el-button>
      </div>

      <el-table :data="paged" border stripe :row-class-name="rowClass">
        <el-table-column type="index" label="#" width="50" align="center" fixed />
        <el-table-column prop="bookingNo" label="预约号" width="140" fixed>
          <template #default="{ row }">
            <span style="font-family: monospace; color: #3b82f6; font-size: 12px">{{ row.bookingNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="会员" width="120">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 6px;">
              <el-avatar :size="26" style="background: linear-gradient(135deg,#06b6d4,#3b82f6); font-size: 11px;">
                {{ row.memberName?.slice(0,1) }}
              </el-avatar>
              <div>
                <div style="font-size: 13px"><strong>{{ row.memberName }}</strong></div>
                <small style="color:#909399; font-size: 11px">{{ row.memberPhone || '' }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="employeeName" label="技师" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ row.employeeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serviceItem" label="服务项目" width="110" show-overflow-tooltip />
        <el-table-column label="房间" width="90">
          <template #default="{ row }">
            <span style="font-size: 12px"><el-icon style="color:#3b82f6"><HomeFilled /></el-icon> {{ row.roomName || '未指定' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <div style="font-size: 12px">
              <strong>{{ row.bookingDate }}</strong>
              <small style="color:#909399; margin-left:4px">{{ row.startTime?.slice(11,16) }}~{{ row.endTime?.slice(11,16) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="dark" size="small">{{ STATUS[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click">
              <el-button size="small" link type="primary">
                操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :icon="View" @click="showDetail(row)">详情</el-dropdown-item>
                  <el-dropdown-item v-if="[1,2].includes(row.status)" @click="openEdit(row)">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" :icon="CircleCheck" @click="act(row, 2)">确认</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 2" :icon="Promotion" @click="checkin(row)">签到</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 3" :icon="Select" @click="complete(row)">完成</el-dropdown-item>
                  <el-dropdown-item v-if="[1,2].includes(row.status)" :icon="CloseBold" divided @click="cancel(row)">取消</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filtered.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <!-- 新建/编辑表单 -->
    <booking-form v-model:visible="formVisible" :editing="editingBooking" @saved="onSaved" />

    <!-- 详情 + 操作弹窗 -->
    <el-dialog v-model="detailVisible" :title="`预约详情 ${detail?.bookingNo || ''}`" width="640">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="会员">{{ detail.memberName }} ({{ detail.memberPhone }})</el-descriptions-item>
        <el-descriptions-item label="技师">{{ detail.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">{{ detail.serviceItem }}</el-descriptions-item>
        <el-descriptions-item label="房间">{{ detail.roomName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.bookingDate }}</el-descriptions-item>
        <el-descriptions-item label="时段">
          <strong style="color:#3b82f6">{{ detail.startTime?.slice(11, 16) }} ~ {{ detail.endTime?.slice(11, 16) }}</strong>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="statusTag(detail.status)" effect="dark">{{ STATUS[detail.status] }}</el-tag>
          <small style="color:#909399; margin-left:8px">下单时间：{{ detail.createTime?.slice(0, 19) }}</small>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="签到时间" v-if="detail.checkinTime">{{ detail.checkinTime?.slice(0, 19) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间" v-if="detail.completeTime">{{ detail.completeTime?.slice(0, 19) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail?.status === 1" type="primary" @click="act(detail, 2)">确认预约</el-button>
        <el-button v-if="detail?.status === 2" type="success" @click="checkin(detail)">签到</el-button>
        <el-button v-if="detail?.status === 3" type="warning" @click="complete(detail)">完成服务</el-button>
        <el-button v-if="[1,2].includes(detail?.status)" type="danger" @click="cancel(detail)">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus, Download, Printer, Search, View, CircleCheck, Promotion, Select, CloseBold, HomeFilled, ArrowDown, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bookingApi } from '@/api/booking'
import { memberApi, employeeApi } from '@/api/basic'
import BookingForm from './booking-form.vue'

const STATUS: any = { 1: '待确认', 2: '已确认', 3: '已签到', 4: '已完成', 5: '已取消', 6: '爽约' }
const statusTag = (s: number) => ({ 1:'warning', 2:'primary', 3:'success', 4:'info', 5:'danger', 6:'danger' }[s] || '')

const all = ref<any[]>([])
const members = ref<any[]>([])
const employees = ref<any[]>([])

// 筛选
const rangeTab = ref<string>('today')
const filterRange = ref<[string, string] | null>(null)
const filterStatus = ref<number>()
const filterMember = ref<number>()
const filterEmployee = ref<number>()
const kw = ref('')

// 分页
const page = ref(1)
const pageSize = ref(20)

const detailVisible = ref(false)
const detail = ref<any>(null)
const formVisible = ref(false)
const editingBooking = ref<any>(null)
const today = new Date().toISOString().slice(0, 10)

const onSaved = () => { formVisible.value = false; load() }
const openEdit = (row: any) => { editingBooking.value = { ...row }; formVisible.value = true }

// 快捷日期
const pickRange = (k: string) => {
  rangeTab.value = k
  const now = new Date()
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  const add = (d: Date, n: number) => { const x = new Date(d); x.setDate(x.getDate() + n); return x }
  const monday = (d: Date) => { const day = d.getDay() || 7; const x = new Date(d); x.setDate(x.getDate() - day + 1); return x }
  if (k === 'today') filterRange.value = [fmt(now), fmt(now)]
  else if (k === 'tomorrow') filterRange.value = [fmt(add(now, 1)), fmt(add(now, 1))]
  else if (k === 'week') filterRange.value = [fmt(monday(now)), fmt(add(monday(now), 6))]
  else filterRange.value = null
  onSearch()
}
const onRangeChange = () => { rangeTab.value = ''; onSearch() }

const filtered = computed(() => {
  const [s, e] = filterRange.value || ['', '']
  return all.value.filter(b =>
    (!filterRange.value || (b.bookingDate >= s && b.bookingDate <= e)) &&
    (!filterStatus.value || b.status === filterStatus.value) &&
    (!filterMember.value || b.memberId === filterMember.value) &&
    (!filterEmployee.value || b.employeeId === filterEmployee.value) &&
    (!kw.value || (b.bookingNo + b.serviceItem).includes(kw.value))
  )
})

const paged = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const list = all.value
  return {
    today:   list.filter(b => b.bookingDate === today).length,
    pending: list.filter(b => b.status === 1).length,
    checkedIn: list.filter(b => b.status === 3).length,
    done:    list.filter(b => b.status === 4).length,
    cancel:  list.filter(b => b.status === 5).length,
    noShow:  list.filter(b => b.status === 6).length,
  }
})

// 过期未签到的预约 → 红色行
const rowClass = ({ row }: any) => {
  if (row.status === 1 && row.bookingDate < today) return 'row-overdue'
  if (row.status === 2 && row.bookingDate < today) return 'row-overdue'
  return ''
}

const onSearch = () => { page.value = 1 }
const onReset = () => {
  filterRange.value = null
  filterStatus.value = undefined
  filterMember.value = undefined
  filterEmployee.value = undefined
  kw.value = ''
  rangeTab.value = 'all'
  onSearch()
}

const load = async () => { all.value = (await bookingApi.list()).data || [] }
const loadMembers = async () => { members.value = (await memberApi.list()).data || [] }
const loadEmployees = async () => { employees.value = (await employeeApi.list()).data || [] }

const showDetail = (row: any) => { detail.value = row; detailVisible.value = true }
const reload = async () => { detailVisible.value = false; await load() }

const exportCsv = async () => {
  const token = localStorage.getItem('token') || ''
  const params = new URLSearchParams()
  if (filterRange.value) {
    params.set('startDate', filterRange.value[0])
    params.set('endDate', filterRange.value[1])
  }
  if (filterStatus.value) params.set('status', String(filterStatus.value))
  const url = `/api/booking/export?${params.toString()}`
  try {
    const r = await fetch(url, { headers: { 'Authorization': token } })
    if (!r.ok) throw new Error('HTTP ' + r.status)
    const blob = await r.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `预约列表_${new Date().toISOString().slice(0,10)}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
    ElMessage.success('已导出 CSV')
  } catch (e: any) { ElMessage.error('导出失败：' + e.message) }
}

const printList = () => {
  const list = filtered.value
  const statusMap = STATUS
  const rows = list.map((b, i) => `
    <tr>
      <td>${i + 1}</td>
      <td>${b.bookingNo || ''}</td>
      <td>${b.memberName || ''}<br/><small>${b.memberPhone || ''}</small></td>
      <td>${b.employeeName || ''}</td>
      <td>${b.serviceItem || ''}</td>
      <td>${b.roomName || ''}</td>
      <td>${b.bookingDate || ''} ${b.startTime?.slice(11, 16) || ''} ~ ${b.endTime?.slice(11, 16) || ''}</td>
      <td>${statusMap[b.status] || ''}</td>
    </tr>
  `).join('')
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>预约列表</title>
    <style>
      body { font-family: "Microsoft YaHei", sans-serif; padding: 20px; }
      h1 { text-align: center; font-size: 22px; margin: 0 0 16px; }
      table { width: 100%; border-collapse: collapse; font-size: 13px; }
      th, td { border: 1px solid #333; padding: 6px 8px; text-align: center; }
      th { background: #f0f0f0; }
      td small { color: #888; font-size: 11px; }
      .no-print { text-align: center; margin-top: 20px; }
      .no-print button { padding: 8px 24px; font-size: 14px; margin: 0 8px; }
      @media print { .no-print { display: none; } }
    </style></head><body>
      <h1>💆 美业管理 · 预约列表（共 ${list.length} 条）</h1>
      <table>
        <thead><tr><th>#</th><th>预约号</th><th>会员</th><th>技师</th><th>服务</th><th>房间</th><th>时间</th><th>状态</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="8">无数据</td></tr>'}</tbody>
      </table>
      <div class="no-print">
        <button onclick="window.print()">🖨️ 打印</button>
        <button onclick="window.close()">关闭</button>
      </div>
    </body></html>`
  const w = window.open('', '_blank')
  if (!w) { ElMessage.error('请允许弹窗'); return }
  w.document.write(html)
  w.document.close()
}

const act = async (b: any, s: number) => {
  try { await ElMessageBox.confirm(`确定要变更状态为「${STATUS[s]}」？`, '确认') } catch { return }
  const r = await bookingApi.changeStatus(b.id, s)
  ElMessage.success(r.message)
  await reload()
}
const checkin = async (b: any) => {
  const r = await bookingApi.checkin(b.id); ElMessage.success(r.message); await reload()
}
const complete = async (b: any) => {
  const r = await bookingApi.complete(b.id); ElMessage.success(r.message); await reload()
}
const cancel = async (b: any) => {
  try { await ElMessageBox.confirm('确定取消？', '确认', { type: 'warning' }) } catch { return }
  const r = await bookingApi.changeStatus(b.id, 5, '用户取消')
  ElMessage.success(r.message); await reload()
}

onMounted(() => { pickRange('today'); load(); loadMembers(); loadEmployees() })
</script>

<style scoped>
.bk-page { padding: 0; }

/* 统计 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 14px 16px;
  border-radius: 8px;
  color: #fff;
}
.stat-label { font-size: 13px; opacity: 0.9; }
.stat-num   { font-size: 24px; font-weight: bold; margin: 4px 0; line-height: 1.2; }
.stat-card small { font-size: 12px; opacity: 0.8; }
.s-1 { background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%); }
.s-2 { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
.s-3 { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.s-4 { background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%); }
.s-5 { background: linear-gradient(135deg, #6b7280 0%, #374151 100%); }

/* 筛选条 */
.filter-bar {
  display: flex; gap: 8px; margin-bottom: 12px;
  align-items: center; flex-wrap: wrap;
}
.filter-spacer { flex: 1; }

/* 过期未签到红行 */
.row-overdue :deep(td) {
  background: #fef2f2 !important;
  color: #b91c1c;
}

/* 分页 */
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>