<template>
  <div class="sc-page">
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><Timer /></el-icon>
        <div>
          <h2>技师排班管理</h2>
          <p>横向周历视图 · 一键批量排班 · 排班冲突自动预警</p>
        </div>
      </div>
      <div class="banner-right">
        <el-button-group>
          <el-button :icon="ArrowLeft" @click="shiftWeek(-7)">上一周</el-button>
          <el-button type="success" @click="goCurrentWeek">本周</el-button>
          <el-button :icon="ArrowRight" @click="shiftWeek(7)">下一周</el-button>
        </el-button-group>
        <el-button type="warning" :icon="MagicStick" @click="batchDialogVisible = true" plain>批量排班</el-button>
        <el-button type="primary" :icon="Plus" @click="openSingle()">新增排班</el-button>
      </div>
    </div>

    <!-- 冲突预警横幅 -->
    <el-alert v-if="scheduleConflicts.length > 0"
      type="error" show-icon :closable="false" class="conflict-alert">
      <template #title>
        ⚠️ 检测到 {{ scheduleConflicts.length }} 组排班冲突（技师同时段多任务）
      </template>
      <ul class="conflict-list">
        <li v-for="(c, i) in scheduleConflicts" :key="i">
          <strong>{{ c.employeeName }}</strong> ·
          {{ c.date }} {{ c.time }} 有 {{ c.bookings.length }} 个预约
          <span v-for="b in c.bookings" :key="b.id" class="conflict-tag">
            {{ b.memberName }}/{{ b.serviceItem }}
          </span>
        </li>
      </ul>
    </el-alert>

    <!-- 排班统计卡片 -->
    <div class="stat-row">
      <div class="stat-card s-1">
        <div class="stat-label">本周排班数</div>
        <div class="stat-num">{{ stats.total }}</div>
        <small>条</small>
      </div>
      <div class="stat-card s-2">
        <div class="stat-label">正常上班</div>
        <div class="stat-num">{{ stats.normal }}</div>
        <small>绿色</small>
      </div>
      <div class="stat-card s-3">
        <div class="stat-label">请假</div>
        <div class="stat-num">{{ stats.leave }}</div>
        <small>橙色</small>
      </div>
      <div class="stat-card s-4">
        <div class="stat-label">休息</div>
        <div class="stat-num">{{ stats.rest }}</div>
        <small>灰色</small>
      </div>
      <div class="stat-card s-5">
        <div class="stat-label">已预约/最大</div>
        <div class="stat-num">{{ stats.booked }}/{{ stats.max }}</div>
        <small>预约占用率 {{ stats.rate }}%</small>
      </div>
    </div>

    <!-- 周历网格 -->
    <el-card shadow="never">
      <el-table border :data="weekGrid" style="font-size: 14px;">
        <el-table-column label="技师" width="110" align="center" fixed>
          <template #default="{ row }">
            <strong>{{ row.employeeName }}</strong>
            <div class="emp-info">
              <small>{{ row.positionName || '技师' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-for="d in weekDayList" :key="d.date" :label="d.label" align="center" min-width="110">
          <template #default="{ row }">
            <div v-if="row.scheduleMap[d.date]"
                 class="sch-cell"
                 :class="statusClass(row.scheduleMap[d.date].status)"
                 @click="openSingle(row.scheduleMap[d.date])">
              <div class="sch-shift">{{ SHIFT[row.scheduleMap[d.date].shiftType] }}</div>
              <div class="sch-time">
                {{ row.scheduleMap[d.date].startTime?.slice(0,5) }} ~
                {{ row.scheduleMap[d.date].endTime?.slice(0,5) }}
              </div>
              <div class="sch-meta">
                <el-tag size="small" :type="statusTag(row.scheduleMap[d.date].status)">
                  {{ statusLabel(row.scheduleMap[d.date].status) }}
                </el-tag>
                <small v-if="row.scheduleMap[d.date].bookedCount">
                  约 {{ row.scheduleMap[d.date].bookedCount }}/{{ row.scheduleMap[d.date].maxBookings }}
                </small>
              </div>
            </div>
            <div v-else class="sch-empty" @click="openSingle(null, row, d.date)">
              <el-icon><Plus /></el-icon>
              <small>排班</small>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 列表视图 -->
    <el-card shadow="never" style="margin-top: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <strong>📋 排班明细</strong>
          <div>
            <el-date-picker v-model="listDate" type="daterange"
                            range-separator="至" start-placeholder="开始" end-placeholder="结束"
                            value-format="YYYY-MM-DD" @change="load" style="width: 240px; margin-right: 8px" />
            <el-button type="success" :icon="Download" @click="exportSchedule" plain>导出CSV</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredRows" border stripe>
        <el-table-column prop="employeeName" label="技师" width="100" />
        <el-table-column prop="workDate"     label="日期" width="110" />
        <el-table-column label="班次" width="80">
          <template #default="{ row }">{{ SHIFT[row.shiftType] }}</template>
        </el-table-column>
        <el-table-column label="时段" width="150">
          <template #default="{ row }">{{ row.startTime?.slice(0,5) }} ~ {{ row.endTime?.slice(0,5) }}</template>
        </el-table-column>
        <el-table-column prop="maxBookings" label="最大预约" width="100" />
        <el-table-column label="已预约" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-danger': (row.bookedCount||0) > row.maxBookings }">
              {{ row.bookedCount || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS[row.status]?.tag">{{ STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openSingle(row)">编辑</el-button>
            <el-button size="small" link type="warning" @click="quickLeave(row)">请假</el-button>
            <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 单条排班弹窗 -->
    <el-dialog v-model="visible" :title="editing?.id ? '编辑排班' : '新增排班'" width="480">
      <el-form :model="form" label-width="100">
        <el-form-item label="技师" required>
          <el-select v-model="form.employeeId" filterable :disabled="!!editing?.id" @change="onEmpChange" style="width: 100%">
            <el-option v-for="e in employees" :key="e.id" :value="e.id" :label="e.employeeName" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.workDate" type="date" value-format="YYYY-MM-DD" :disabled="!!editing?.id" style="width: 100%" />
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="form.shiftType" style="width: 100%" @change="onShiftChange">
            <el-option v-for="(label, k) in SHIFT" :key="k" :value="Number(k)" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="时段">
          <el-time-picker v-model="form.startTimeRange" is-range range-separator="~"
                          value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大预约数">
          <el-input-number v-model="form.maxBookings" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :value="1" label="正常" />
            <el-option :value="2" label="请假" />
            <el-option :value="3" label="休息" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量排班弹窗 -->
    <el-dialog v-model="batchDialogVisible" title="⚡ 批量排班（一键复制到多天）" width="520">
      <el-form :model="batchForm" label-width="100">
        <el-form-item label="技师" required>
          <el-select v-model="batchForm.employeeId" filterable @change="onEmpChangeBatch" style="width: 100%">
            <el-option v-for="e in employees" :key="e.id" :value="e.id" :label="e.employeeName" />
          </el-select>
        </el-form-item>
        <el-form-item label="复制自" required>
          <el-date-picker v-model="batchForm.sourceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          <small style="color:#909399">从这一天读取排班时段</small>
        </el-form-item>
        <el-form-item label="应用到" required>
          <el-checkbox-group v-model="batchForm.targetDays">
            <el-checkbox :value="1" label="周一" />
            <el-checkbox :value="2" label="周二" />
            <el-checkbox :value="3" label="周三" />
            <el-checkbox :value="4" label="周四" />
            <el-checkbox :value="5" label="周五" />
            <el-checkbox :value="6" label="周六" />
            <el-checkbox :value="7" label="周日" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="周数">
          <el-input-number v-model="batchForm.weeks" :min="1" :max="4" />
          <small style="color:#909399">未来几周生效（最多 4 周）</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="MagicStick" @click="batchSubmit">批量提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'  // watch 已可省略
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Timer, MagicStick, Download, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { scheduleApi, bookingApi } from '@/api/booking'
import { employeeApi } from '@/api/basic'

const SHIFT: any = { 1: '早班', 2: '中班', 3: '晚班', 4: '全天' }
const STATUS: any = {
  1: { label: '正常', tag: 'success' },
  2: { label: '请假', tag: 'warning' },
  3: { label: '休息', tag: 'info' }
}

const SHIFT_DEFAULT: any = {
  1: ['06:00:00', '14:00:00'],
  2: ['14:00:00', '22:00:00'],
  3: ['22:00:00', '06:00:00'],
  4: ['09:00:00', '18:00:00']
}

const rows = ref<any[]>([])
const employees = ref<any[]>([])
const bookings = ref<any[]>([])
const weekAnchor = ref(new Date())
const visible = ref(false)
const editing = ref<any>(null)
const form = ref<any>({ shiftType: 4, maxBookings: 8, status: 1 })

const batchDialogVisible = ref(false)
const batchForm = ref<any>({ employeeId: undefined, sourceDate: '', targetDays: [1,2,3,4,5], weeks: 1 })

const listDate = ref<[string, string] | null>(null)
const filteredRows = computed(() => {
  if (!listDate.value) return rows.value
  const [s, e] = listDate.value
  return rows.value.filter(r => r.workDate >= s && r.workDate <= e)
})

const weekDayList = computed(() => {
  // 找到本周一
  const d = new Date(weekAnchor.value)
  const dow = d.getDay() || 7
  d.setDate(d.getDate() - dow + 1)
  const days: any[] = []
  for (let i = 0; i < 7; i++) {
    const dd = new Date(d); dd.setDate(dd.getDate() + i)
    days.push({ date: dd.toISOString().slice(0, 10), label: `${dd.getMonth() + 1}/${dd.getDate()}\n${'日一二三四五六'[dd.getDay()]}` })
  }
  return days
})

const weekRangeStr = computed(() => `${weekDayList.value[0].date} ~ ${weekDayList.value[6].date}`)

// 给每个排班计算 bookedCount
const rowsWithCount = computed(() => {
  const map = new Map<string, number>() // key = employeeId + '|' + date
  bookings.value.forEach(b => {
    if ([1,2,3].includes(b.status)) {
      const k = `${b.employeeId}|${b.bookingDate}`
      map.set(k, (map.get(k) || 0) + 1)
    }
  })
  return rows.value.map(r => ({
    ...r,
    bookedCount: map.get(`${r.employeeId}|${r.workDate}`) || 0
  }))
})

const weekGrid = computed(() => {
  const days = weekDayList.value
  const startDate = days[0].date
  const endDate = days[6].date
  // 每个技师的当周排班映射
  return employees.value.map(emp => {
    const scheduleMap: Record<string, any> = {}
    days.forEach(d => scheduleMap[d.date] = null)
    rowsWithCount.value
      .filter(r => r.employeeId === emp.id && r.workDate >= startDate && r.workDate <= endDate)
      .forEach(r => { scheduleMap[r.workDate] = r })
    return { employeeId: emp.id, employeeName: emp.employeeName, scheduleMap }
  })
})

const stats = computed(() => {
  const startDate = weekDayList.value[0].date
  const endDate = weekDayList.value[6].date
  const weekRows = rowsWithCount.value.filter(r => r.workDate >= startDate && r.workDate <= endDate)
  const booked = weekRows.reduce((sum, r) => sum + (r.bookedCount || 0), 0)
  const max = weekRows.reduce((sum, r) => sum + (r.maxBookings || 0), 0)
  return {
    total: weekRows.length,
    normal: weekRows.filter(r => r.status === 1).length,
    leave:  weekRows.filter(r => r.status === 2).length,
    rest:   weekRows.filter(r => r.status === 3).length,
    booked, max,
    rate: max > 0 ? Math.round(booked / max * 100) : 0
  }
})

// 冲突：技师某天预约数 > maxBookings
const scheduleConflicts = computed(() => {
  const startDate = weekDayList.value[0].date
  const endDate = weekDayList.value[6].date
  const conflicts: any[] = []
  rowsWithCount.value.forEach(r => {
    if (r.workDate < startDate || r.workDate > endDate) return
    if ((r.bookedCount || 0) > (r.maxBookings || 0)) {
      conflicts.push({
        employeeName: r.employeeName,
        date: r.workDate,
        time: `${r.startTime?.slice(0,5)}~${r.endTime?.slice(0,5)}`,
        max: r.maxBookings,
        bookings: bookings.value.filter(b =>
          b.employeeId === r.employeeId &&
          b.bookingDate === r.workDate &&
          [1,2,3].includes(b.status)
        )
      })
    }
  })
  return conflicts
})

const shiftWeek = (delta: number) => {
  const d = new Date(weekAnchor.value)
  d.setDate(d.getDate() + delta)
  weekAnchor.value = d
}
const goCurrentWeek = () => { weekAnchor.value = new Date() }

const statusClass = (s: number) => ({ 1: 'sch-normal', 2: 'sch-leave', 3: 'sch-rest' }[s] || '')
const statusTag   = (s: number) => STATUS[s]?.tag || ''
const statusLabel = (s: number) => STATUS[s]?.label || ''

const load = async () => { rows.value = (await scheduleApi.list()).data || [] }
const loadEmployees = async () => { employees.value = (await employeeApi.list()).data || [] }
const loadBookings = async () => { bookings.value = (await bookingApi.list()).data || [] }

const onEmpChange = () => {
  const e = employees.value.find(x => x.id === form.value.employeeId)
  if (e) form.value.employeeName = e.employeeName
}
const onShiftChange = () => {
  form.value.startTimeRange = SHIFT_DEFAULT[form.value.shiftType] || ['09:00:00', '18:00:00']
}

const openSingle = (row?: any, employee?: any, date?: string) => {
  if (row) {
    editing.value = row
    form.value = { ...row, startTimeRange: [row.startTime, row.endTime] }
  } else {
    editing.value = null
    form.value = {
      shiftType: 4, maxBookings: 8, status: 1,
      workDate: date || new Date().toISOString().slice(0, 10),
      startTimeRange: SHIFT_DEFAULT[4],
      employeeId: employee?.id,
      employeeName: employee?.employeeName
    }
  }
  visible.value = true
}

const submit = async () => {
  if (form.value.startTimeRange) {
    form.value.startTime = form.value.startTimeRange[0]
    form.value.endTime = form.value.startTimeRange[1]
    delete form.value.startTimeRange
  }
  try {
    if (editing.value?.id) {
      const r = await scheduleApi.update({ ...form.value, id: editing.value.id })
      ElMessage.success(r.message || '更新成功')
    } else {
      const r = await scheduleApi.create(form.value)
      ElMessage.success(r.message || '创建成功')
    }
    visible.value = false
    load()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
}

const remove = async (row: any) => {
  try { await ElMessageBox.confirm(`确定删除 ${row.employeeName} ${row.workDate} 的排班？`, '警告', { type: 'warning' }) } catch { return }
  await scheduleApi.delete(row.id)
  ElMessage.success('删除成功')
  load()
}

const quickLeave = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请假原因', '快速请假', {
      inputPlaceholder: '例：家中有事 / 病假',
      inputValidator: v => !!v || '请输入原因'
    })
    const r = await scheduleApi.update({ ...row, status: 2, remark: reason })
    ElMessage.success(r.message || '已设为请假')
    load()
  } catch { /* 用户取消 */ }
}

const onEmpChangeBatch = () => {
  const e = employees.value.find(x => x.id === batchForm.value.employeeId)
  if (e) batchForm.value.employeeName = e.employeeName
}

const batchSubmit = async () => {
  if (!batchForm.value.employeeId || !batchForm.value.sourceDate || !batchForm.value.targetDays.length) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try { await ElMessageBox.confirm(`将为 ${batchForm.value.employeeName} 复制 ${batchForm.value.weeks} 周排班，确认？`, '批量排班', { type: 'warning' }) } catch { return }

  // 读取源日期的排班
  const src = rows.value.find(r => r.employeeId === batchForm.value.employeeId && r.workDate === batchForm.value.sourceDate)
  if (!src) { ElMessage.error(`源日期 ${batchForm.value.sourceDate} 没有排班记录`); return }

  let count = 0
  const baseDate = new Date(batchForm.value.sourceDate)
  for (let w = 0; w < batchForm.value.weeks; w++) {
    for (const dow of batchForm.value.targetDays) {
      const target = new Date(baseDate)
      target.setDate(target.getDate() + w * 7 + (dow - 1))
      const targetStr = target.toISOString().slice(0, 10)
      // 跳过已存在的
      if (rows.value.some(r => r.employeeId === batchForm.value.employeeId && r.workDate === targetStr)) continue
      await scheduleApi.create({
        employeeId: batchForm.value.employeeId,
        employeeName: batchForm.value.employeeName,
        workDate: targetStr,
        shiftType: src.shiftType,
        startTime: src.startTime,
        endTime: src.endTime,
        maxBookings: src.maxBookings,
        status: 1,
        remark: src.remark || '批量复制'
      })
      count++
    }
  }
  ElMessage.success(`批量排班成功 · 新增 ${count} 条`)
  batchDialogVisible.value = false
  load()
}

const exportSchedule = async () => {
  const token = localStorage.getItem('token') || ''
  const headers = ['技师', '日期', '班次', '开始', '结束', '最大预约', '已预约', '状态', '备注']
  const statusMap: any = { 1: '正常', 2: '请假', 3: '休息' }
  const shiftMap: any = { 1: '早班', 2: '中班', 3: '晚班', 4: '全天' }
  const rows = filteredRows.value.map(r => [
    r.employeeName, r.workDate, shiftMap[r.shiftType] || '',
    r.startTime?.slice(0,5), r.endTime?.slice(0,5),
    r.maxBookings, r.bookedCount || 0,
    statusMap[r.status] || '', r.remark || ''
  ])
  const csv = [headers, ...rows].map(row =>
    row.map((v: any) => {
      const s = String(v ?? '')
      return s.includes(',') ? `"${s}"` : s
    }).join(',')
  ).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `排班表_${new Date().toISOString().slice(0,10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出排班 CSV')
}

onMounted(() => { load(); loadEmployees(); loadBookings() })

// 周切换时刷新排班
watch(weekAnchor, () => { load() })
</script>

<style scoped>
.sc-page { padding: 16px; }
.banner {
  background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%);
  color: #fff; padding: 24px; border-radius: 10px;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.banner h2 { margin: 0; font-size: 22px; }
.banner p  { margin: 4px 0 0; opacity: 0.85; font-size: 13px; }
.banner-right { display: flex; gap: 8px; align-items: center; }

/* 冲突 */
.conflict-alert { margin-bottom: 16px; }
.conflict-alert ul { margin: 0; padding-left: 0; list-style: none; }
.conflict-alert li { padding: 4px 0; font-size: 13px; }
.conflict-tag {
  display: inline-block; margin-left: 6px;
  padding: 2px 6px; background: #fee2e2; color: #b91c1c;
  border-radius: 3px; font-size: 12px;
}

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
.s-2 { background: linear-gradient(135deg, #10b981 0%, #059669 100%); }
.s-3 { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
.s-4 { background: linear-gradient(135deg, #6b7280 0%, #374151 100%); }
.s-5 { background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%); }

/* 排班格子 */
.emp-info { color: #909399; font-size: 12px; margin-top: 2px; }
.sch-cell {
  cursor: pointer; padding: 8px 4px; border-radius: 6px;
  transition: transform 0.15s;
}
.sch-cell:hover { transform: translateY(-2px); }
.sch-normal { background: #ecfdf5; border: 1px solid #10b981; }
.sch-leave  { background: #fef3c7; border: 1px solid #f59e0b; }
.sch-rest   { background: #f3f4f6; border: 1px solid #9ca3af; }
.sch-shift { font-weight: bold; font-size: 13px; color: #1f2937; }
.sch-time  { color: #6b7280; font-size: 12px; margin: 4px 0; }
.sch-meta  { font-size: 11px; color: #6b7280; }
.sch-empty {
  cursor: pointer; padding: 20px 4px; color: #c0c4cc;
  border: 1px dashed #d1d5db; border-radius: 6px;
  text-align: center;
}
.sch-empty:hover { color: #06b6d4; border-color: #06b6d4; }

.text-danger { color: #ef4444; font-weight: bold; }
</style>