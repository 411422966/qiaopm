<template>
  <el-dialog :model-value="visible" @update:model-value="(v: boolean) => $emit('update:visible', v)"
             :title="editing?.id ? '编辑预约' : '新建预约'" width="560"
             @open="init">
    <el-form :model="form" label-width="84px">
      <el-form-item label="会员" required>
        <el-select v-model="form.memberId" filterable remote :remote-method="searchMembers" :loading="searchLoading"
                   placeholder="搜索会员" style="width: 100%">
          <el-option v-for="m in memberOpts" :key="m.id" :value="m.id" :label="`${m.name} | ${m.phone}`" />
        </el-select>
      </el-form-item>

      <el-form-item label="服务门店" required>
        <el-select v-model="form.storeId" filterable placeholder="选择服务门店" style="width: 100%" @change="onStoreChange">
          <el-option v-for="s in stores" :key="s.id" :value="s.id" :label="s.storeName" />
        </el-select>
      </el-form-item>

      <el-form-item label="技师" required>
        <!-- 本店技师选择 -->
        <div v-if="!crossStoreMode">
          <el-select v-model="form.employeeId" filterable placeholder="选择技师" style="width:calc(100% - 100px)" @change="onEmployeeChange" :loading="employeeLoading">
            <el-option v-for="e in employees" :key="e.id" :value="e.id" :label="e.employeeName + (e.positionType ? ' · ' + positionTypeText(e.positionType) : '')" />
          </el-select>
          <el-button style="width:90px" @click="crossStoreMode = true; loadCrossStoreEmployees()">跨店技师</el-button>
        </div>
        <!-- 跨店技师搜索 -->
        <div v-else>
          <el-input v-model="crossStoreKeyword" placeholder="输入技师姓名搜索全部门店" clearable style="width:calc(100% - 100px)"
                   @clear="crossStoreResults = []" @keyup.enter="loadCrossStoreEmployees(true)">
            <template #append>
              <el-button :loading="crossStoreLoading" @click="loadCrossStoreEmployees(true)">搜索</el-button>
            </template>
          </el-input>
          <el-button style="width:90px" @click="crossStoreMode = false; crossStoreResults = []">本店</el-button>
          <div v-if="crossStoreResults.length" style="margin-top:6px; max-height:200px; overflow-y:auto; border:1px solid #dcdfe6; border-radius:4px; padding:4px">
            <div v-for="e in crossStoreResults" :key="e.id"
                 style="padding:6px 8px; cursor:pointer; border-radius:3px"
                 :style="form.employeeId === e.id ? 'background:#ecf5ff' : 'background:#fff'"
                 @click="selectCrossStoreTech(e)">
              <span style="font-weight:bold">{{ e.employeeName }}</span>
              <span style="color:#909399; margin-left:8px">{{ e.storeName || '未知门店' }}</span>
              <span v-if="e.positionType" style="color:#67c23a; margin-left:8px">{{ positionTypeText(e.positionType) }}</span>
            </div>
          </div>
          <div v-else-if="crossStoreKeyword && !crossStoreLoading" style="margin-top:4px; color:#909399; font-size:12px">
            未找到匹配的跨店技师
          </div>
        </div>
      </el-form-item>

      <el-form-item label="服务项目">
        <el-input v-model="form.serviceItem" placeholder="例：面部清洁" />
      </el-form-item>

      <el-form-item label="房间">
        <el-select v-model="form.roomId" placeholder="空闲房间" style="width: 100%">
          <el-option v-for="r in rooms" :key="r.id" :value="r.id" :label="r.roomName" />
        </el-select>
      </el-form-item>

      <el-form-item label="预约日期">
        <el-date-picker v-model="form.bookingDate" type="date" value-format="YYYY-MM-DD"
                        @change="loadAvailable" style="width: 100%" />
      </el-form-item>

      <el-form-item label="可用时间段" v-if="availableSlots.length">
        <el-radio-group v-model="selectedSlot" @change="onSlotChange">
          <el-radio-button v-for="(slot, idx) in availableSlots" :key="idx" :value="slot">
            {{ slot }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="开始时间">
        <el-time-select v-model="startTimeSelect" value-format="YYYY-MM-DDTHH:mm:ss"
                        :start="workStart" :step="slotStep" :end="workEnd"
                        placeholder="选择开始时间" style="width: 100%"
                        @change="onTimeChange" />
      </el-form-item>

      <el-form-item label="结束时间">
        <el-time-select v-model="endTimeSelect" value-format="YYYY-MM-DDTHH:mm:ss"
                        :start="workStart" :step="slotStep" :end="workEnd"
                        placeholder="选择结束时间" style="width: 100%"
                        @change="onTimeChange" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { bookingApi, roomApi } from '@/api/booking'
import { memberApi, employeeApi, storeApi } from '@/api/basic'

const props = defineProps<{ visible: boolean; editing: any }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'saved'): void }>()

const form = ref<any>({})
const members = ref<any[]>([])
const memberOpts = ref<any[]>([])
const stores = ref<any[]>([])
const employees = ref<any[]>([])
const rooms = ref<any[]>([])
const availableSlots = ref<string[]>([])
const searchLoading = ref(false)
const employeeLoading = ref(false)
// M01 跨店技师支持
const crossStoreMode = ref(false)
const crossStoreKeyword = ref('')
const crossStoreResults = ref<any[]>([])
const crossStoreLoading = ref(false)

async function loadCrossStoreEmployees(search = false) {
  if (search) crossStoreLoading.value = true
  try {
    const params: any = {}
    if (crossStoreKeyword.value) params.keyword = crossStoreKeyword.value
    const res = await employeeApi.list(params)
    crossStoreResults.value = ((res.data || []) as any[]).filter(
      (e: any) => e.positionType != null && e.positionType >= 1 && e.positionType <= 4)
  } finally {
    crossStoreLoading.value = false
  }
}

function selectCrossStoreTech(e: any) {
  form.value.employeeId = e.id
  form.value.employeeName = e.employeeName
  if (e.storeId) form.value.storeId = e.storeId
  crossStoreResults.value = []
  loadAvailable()
}

const startTimeSelect = ref<string>('')
const endTimeSelect = ref<string>('')
const selectedSlot = ref<string>('')
const workStart = '08:00'
const workEnd = '21:00'
const slotStep = '00:30'

const init = () => {
  form.value = props.editing?.id ? { ...props.editing } : { ...(props.editing || {}) }
  selectedSlot.value = ''
  // P2-13：从 startTime/endTime 提取 HH:mm 部分给 time-select
  if (form.value.startTime) {
    const t = form.value.startTime.slice(11, 16) // "10:00"
    startTimeSelect.value = t
  } else {
    startTimeSelect.value = ''
  }
  if (form.value.endTime) {
    const t = form.value.endTime.slice(11, 16)
    endTimeSelect.value = t
  } else {
    endTimeSelect.value = ''
  }
  loadStores()
  loadRooms()
  loadEmployees(form.value.storeId)
  if (form.value.memberId) {
    memberOpts.value = [{ id: form.value.memberId, name: form.value.memberName, phone: form.value.memberPhone }]
  }
  if (form.value.employeeId && form.value.bookingDate) loadAvailable()
}

const searchMembers = async (q: string) => {
  if (!q) return
  searchLoading.value = true
  try {
    const r = await memberApi.list({ keyword: q })
    memberOpts.value = (r.data || []).slice(0, 20)
  } finally {
    searchLoading.value = false
  }
}

const loadStores = async () => { stores.value = (await storeApi.list()).data || [] }
const loadEmployees = async (storeId?: number) => {
  employeeLoading.value = true
  try {
    employees.value = (await employeeApi.list({ storeId })).data || []
  } finally {
    employeeLoading.value = false
  }
}
const positionTypeText = (t: number) => ({ 1: '技师', 2: '高级技师', 3: '主管', 4: '店长' }[t] || '技师')

const loadRooms = async () => { rooms.value = (await roomApi.list()).data || [] }
const loadAvailable = async () => {
  if (!form.value.employeeId || !form.value.bookingDate) return
  availableSlots.value = (await bookingApi.available(form.value.employeeId, form.value.bookingDate)).data || []
}

const onStoreChange = () => {
  form.value.employeeId = undefined
  form.value.employeeName = undefined
  loadEmployees(form.value.storeId)
}

const onEmployeeChange = () => {
  const e = employees.value.find(x => x.id === form.value.employeeId)
  if (e) form.value.employeeName = e.employeeName
  loadAvailable()
}
const onSlotChange = (slot: string) => {
  if (!slot || !form.value.bookingDate) return
  // slot 格式 "09:00~09:30"，取前半段
  const startHHmm = slot.split('~')[0] // "09:00"
  startTimeSelect.value = startHHmm
  // 结束时间自动加 1 小时（30-min 精度时加 60 分钟）
  const [hh, mm] = startHHmm.split(':').map(Number)
  const endDate = new Date(form.value.bookingDate + 'T' + startHHmm + ':00')
  endDate.setMinutes(endDate.getMinutes() + 60)
  const endHHmm = String(endDate.getHours()).padStart(2, '0') + ':' + String(endDate.getMinutes()).padStart(2, '0')
  endTimeSelect.value = endHHmm
  onTimeChange()
}
const onTimeChange = () => {
  if (!form.value.bookingDate || !startTimeSelect.value) return
  form.value.startTime = form.value.bookingDate + 'T' + startTimeSelect.value + ':00'
  if (endTimeSelect.value) {
    form.value.endTime = form.value.bookingDate + 'T' + endTimeSelect.value + ':00'
  }
}

const submit = async () => {
  if (!form.value.memberId) return ElMessage.warning('请选择会员')
  if (!form.value.employeeId) return ElMessage.warning('请选择技师')
  if (!form.value.startTime || !form.value.endTime) return ElMessage.warning('请选择时间')
  const room = rooms.value.find(r => r.id === form.value.roomId)
  if (room) form.value.roomName = room.roomName
  const r = props.editing?.id
    ? await bookingApi.update({ ...form.value, id: props.editing.id })
    : await bookingApi.create(form.value)
  ElMessage.success(r.message || '保存成功')
  emit('saved')
}

watch(() => props.visible, (v) => { if (v) init() })
watch(() => props.visible, (v) => { if (!v) { crossStoreMode.value = false; crossStoreResults.value = [] } })
</script>