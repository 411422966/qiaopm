<template>
  <div class="qo-page">
    <!-- ============ 顶部标题栏 ============ -->
    <div class="qo-header">
      <div class="qo-header__left" @click="router.back()">
        <el-icon size="20"><ArrowLeft /></el-icon>
      </div>
      <div class="qo-header__title">💅 快速下单</div>
      <div class="qo-header__right"></div>
    </div>

    <!-- ============ 步骤条 ============ -->
    <el-steps :active="step" finish-status="success" class="qo-steps" align-center>
      <el-step title="选款式" />
      <el-step title="选美甲师" />
      <el-step title="选时间" />
      <el-step title="确认下单" />
    </el-steps>

    <!-- ============ Step 0: 款式图库选择 ============ -->
    <div v-show="step === 0" class="qo-step">
      <div class="qo-section-title">请选择美甲款式</div>
      <div v-if="styleLoading" class="qo-loading">
        <el-icon class="is-loading" size="32"><Loading /></el-icon>
      </div>
      <div v-else class="qo-style-grid">
        <div
          v-for="s in styleList"
          :key="s.id"
          class="qo-style-card"
          :class="{ selected: selectedStyle?.id === s.id }"
          @click="onStyleSelect(s)">
          <div class="qo-style-card__cover" :style="{ background: coverBg(s.styleType) }">
            <img v-if="firstImage(s)" :src="firstImage(s)" :alt="s.styleName" class="qo-style-card__img" />
            <span v-else class="qo-style-card__emoji">{{ categoryEmoji(s.styleType) }}</span>
            <div v-if="selectedStyle?.id === s.id" class="qo-style-card__check">✓</div>
          </div>
          <div class="qo-style-card__body">
            <div class="qo-style-card__name">{{ s.styleName }}</div>
            <div class="qo-style-card__meta">
              <span class="qo-style-card__time">⏱ {{ s.durationMinutes || 60 }}分钟</span>
              <span class="qo-style-card__price">¥{{ s.basePrice || 68 }}</span>
            </div>
            <div class="qo-style-card__tag" v-if="s.styleTag">
              <el-tag size="small" type="info" effect="plain">{{ s.styleTag }}</el-tag>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!styleLoading && styleList.length === 0" class="qo-empty">
        <el-empty description="暂无款式数据，请先初始化" :image-size="80" />
      </div>
    </div>

    <!-- ============ Step 1: 选美甲师 ============ -->
    <div v-show="step === 1" class="qo-step">
      <div class="qo-section-title">请选择美甲师</div>
      <div v-if="techLoading" class="qo-loading">
        <el-icon class="is-loading" size="32"><Loading /></el-icon>
      </div>
      <div v-else class="qo-tech-list">
        <div
          v-for="t in technicianList"
          :key="t.id"
          class="qo-tech-card"
          :class="{ selected: form.technicianId === t.id }"
          @click="onTechSelect(t)">
          <div class="qo-tech-card__avatar">
            {{ t.employeeName?.charAt(0) || '?' }}
          </div>
          <div class="qo-tech-card__info">
            <div class="qo-tech-card__name">{{ t.employeeName || '未命名' }}</div>
            <div class="qo-tech-card__level">{{ t.position || '技师' }}</div>
          </div>
          <div v-if="form.technicianId === t.id" class="qo-tech-card__check">✓</div>
        </div>
      </div>
      <div v-if="!techLoading && technicianList.length === 0" class="qo-empty">
        <el-empty description="当前门店暂无美甲师，请先添加" :image-size="80" />
      </div>
      <div class="qo-skip-hint" @click="form.technicianId = null; step = 2">
        不指定美甲师 <el-icon><DArrowRight /></el-icon>
      </div>
    </div>

    <!-- ============ Step 2: 选时间 + 会员 ============ -->
    <div v-show="step === 2" class="qo-step">
      <div class="qo-section-title">选择预约时间</div>

      <!-- 会员选择 -->
      <div class="qo-form-group">
        <label class="qo-label">会员手机号</label>
        <el-input
          v-model="memberSearch"
          placeholder="输入手机号搜索会员"
          clearable
          @clear="onMemberClear">
          <template #append>
            <el-button :icon="Search" @click="onMemberSearch">搜索</el-button>
          </template>
        </el-input>
        <div v-if="selectedMember" class="qo-member-card">
          <span>{{ selectedMember.memberName }} ({{ selectedMember.phone }})</span>
          <el-button link type="danger" size="small" @click="onMemberClear">清除</el-button>
        </div>
      </div>

      <!-- 日期选择 -->
      <div class="qo-form-group">
        <label class="qo-label">预约日期</label>
        <el-date-picker
          v-model="form.bookingDate"
          type="date"
          placeholder="选择日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :disabled-date="disabledDate"
          style="width: 100%" />
      </div>

      <!-- 时间段选择 -->
      <div class="qo-form-group">
        <label class="qo-label">预约时间</label>
        <div class="qo-time-grid">
          <div
            v-for="slot in timeSlots"
            :key="slot"
            class="qo-time-slot"
            :class="{ disabled: isSlotDisabled(slot), selected: form.startTime === slot }"
            @click="!isSlotDisabled(slot) && (form.startTime = slot)">
            {{ slot }}
          </div>
        </div>
      </div>

      <!-- 备注 -->
      <div class="qo-form-group">
        <label class="qo-label">备注（选填）</label>
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          placeholder="如：加急、两手都要做等" />
      </div>
    </div>

    <!-- ============ Step 3: 确认下单 ============ -->
    <div v-show="step === 3" class="qo-step">
      <div class="qo-section-title">确认订单信息</div>

      <!-- 款式摘要 -->
      <div class="qo-summary-card">
        <div class="qo-summary-card__title">款式信息</div>
        <div class="qo-summary-row">
          <span class="qo-summary-row__label">款式</span>
          <span class="qo-summary-row__value">{{ selectedStyle?.styleName }}</span>
        </div>
        <div class="qo-summary-row">
          <span class="qo-summary-row__label">价格</span>
          <span class="qo-summary-row__value price">¥{{ selectedStyle?.basePrice }}</span>
        </div>
        <div class="qo-summary-row">
          <span class="qo-summary-row__label">耗时</span>
          <span class="qo-summary-row__value">{{ selectedStyle?.durationMinutes || 60 }} 分钟</span>
        </div>
      </div>

      <!-- 美甲师摘要 -->
      <div class="qo-summary-card" v-if="form.technicianId || form.startTime">
        <div class="qo-summary-card__title">预约信息</div>
        <div class="qo-summary-row" v-if="selectedTech">
          <span class="qo-summary-row__label">美甲师</span>
          <span class="qo-summary-row__value">{{ selectedTech.employeeName }}</span>
        </div>
        <div class="qo-summary-row" v-if="selectedMember">
          <span class="qo-summary-row__label">会员</span>
          <span class="qo-summary-row__value">{{ selectedMember.memberName }}</span>
        </div>
        <div class="qo-summary-row" v-if="form.bookingDate">
          <span class="qo-summary-row__label">日期</span>
          <span class="qo-summary-row__value">{{ form.bookingDate }}</span>
        </div>
        <div class="qo-summary-row" v-if="form.startTime">
          <span class="qo-summary-row__label">时间</span>
          <span class="qo-summary-row__value">{{ form.startTime }}
            <span v-if="estimatedEndTime"> ~ {{ estimatedEndTime }}</span>
          </span>
        </div>
      </div>

      <!-- 耗材预警 -->
      <div class="qo-summary-card qo-summary-card--warn" v-if="materialResult?.materialWarning">
        <div class="qo-summary-card__title">⚠️ 耗材提醒</div>
        <div class="qo-material-list">
          <div
            v-for="(m, idx) in materialResult.materials"
            :key="idx"
            class="qo-material-item"
            :class="{ substitute: m.isSubstitute }">
            <span class="qo-material-item__name">
              {{ m.isSubstitute ? '🔄 ' : '' }}{{ m.productName || m.originalTool }}
            </span>
            <span class="qo-material-item__stock">库存: {{ m.stockQty ?? 0 }}</span>
            <span v-if="m.substituteReason" class="qo-material-item__reason">{{ m.substituteReason }}</span>
          </div>
        </div>
      </div>

      <!-- 预约冲突警告 -->
      <div class="qo-summary-card qo-summary-card--warn" v-if="bookingWarning">
        <el-alert type="warning" :title="bookingWarning" :closable="false" show-icon />
      </div>

      <!-- 备注 -->
      <div class="qo-summary-card" v-if="form.remark">
        <div class="qo-summary-card__title">备注</div>
        <div class="qo-summary-row">
          <span class="qo-summary-row__value" style="color: #909399">{{ form.remark }}</span>
        </div>
      </div>
    </div>

    <!-- ============ 底部操作栏 ============ -->
    <div class="qo-footer">
      <el-button v-if="step > 0" @click="step--" size="large" plain>上一步</el-button>
      <el-button v-if="step === 0 && !selectedStyle" disabled size="large">请先选择款式</el-button>
      <el-button
        v-if="step === 0 && selectedStyle"
        type="primary"
        size="large"
        @click="step = 1">
        下一步：选美甲师
      </el-button>
      <el-button
        v-if="step === 1"
        type="primary"
        size="large"
        @click="step = 2">
        下一步：选时间
      </el-button>
      <el-button
        v-if="step === 2"
        type="primary"
        size="large"
        :disabled="!form.bookingDate || !form.startTime"
        @click="onStep3Enter">
        下一步：确认
      </el-button>
      <el-button
        v-if="step === 3"
        type="primary"
        size="large"
        :loading="submitting"
        @click="onSubmit">
        {{ form.startTime ? '确认预约' : '仅保存款式（暂不预约）' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading, Search, DArrowRight } from '@element-plus/icons-vue'
import { styleGalleryApi, coverBg, categoryEmoji, resolveImageList } from '@/api/style-gallery'
import { employeeApi } from '@/api/basic'
import request from '@/api/request'

const router = useRouter()

// ============ State ============
const step = ref(0)
const styleList = ref<any[]>([])
const styleLoading = ref(false)
const technicianList = ref<any[]>([])
const techLoading = ref(false)
const selectedStyle = ref<any | null>(null)
const selectedTech = ref<any | null>(null)
const memberSearch = ref('')
const selectedMember = ref<any | null>(null)
const submitting = ref(false)
const materialResult = ref<any | null>(null)
const bookingWarning = ref('')

const form = ref({
  technicianId: null as number | null,
  bookingDate: '',
  startTime: '',
  remark: ''
})

// ============ Computed ============
const timeSlots = computed(() => {
  const slots: string[] = []
  for (let h = 9; h < 21; h++) {
    slots.push(`${String(h).padStart(2, '0')}:00`)
    slots.push(`${String(h).padStart(2, '0')}:30`)
  }
  return slots
})

const estimatedEndTime = computed(() => {
  if (!form.value.startTime || !selectedStyle.value) return ''
  const [h, m] = form.value.startTime.split(':').map(Number)
  const total = h * 60 + m + (selectedStyle.value.durationMinutes || 60)
  const eh = Math.floor(total / 60)
  const em = total % 60
  return `${String(eh).padStart(2, '0')}:${String(em).padStart(2, '0')}`
})

// ============ Data Loading ============
onMounted(() => {
  loadStyles()
  loadTechnicians()
})

const loadStyles = async () => {
  styleLoading.value = true
  try {
    const res: any = await styleGalleryApi.list({ status: 1, sort: 'HOT' })
    styleList.value = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
  } catch (e: any) {
    ElMessage.error(e?.message || '款式加载失败')
  } finally {
    styleLoading.value = false
  }
}

const loadTechnicians = async () => {
  techLoading.value = true
  try {
    const res: any = await employeeApi.list()
    const all = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    // 过滤美甲师岗位（按 position 关键词）
    technicianList.value = all.filter((e: any) =>
      e.status === 1 &&
      (e.position?.includes('美甲') ||
       e.position?.includes('技师') ||
       e.position?.includes('美容') ||
       e.position?.includes('师'))
    )
    // 如果没有按岗位过滤结果，全量返回
    if (technicianList.value.length === 0) {
      technicianList.value = all.filter((e: any) => e.status === 1)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '技师加载失败')
  } finally {
    techLoading.value = false
  }
}

// ============ Event Handlers ============
const firstImage = (item: any) => resolveImageList(item)[0] || ''

const onStyleSelect = (s: any) => {
  selectedStyle.value = s
}

const onTechSelect = (t: any) => {
  selectedTech.value = t
  form.value.technicianId = t.id
}

const disabledDate = (date: Date) => {
  return date < new Date(new Date().setHours(0, 0, 0, 0))
}

const isSlotDisabled = (slot: string) => {
  if (!form.value.bookingDate) return false
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  if (form.value.bookingDate === today) {
    const [h, m] = slot.split(':').map(Number)
    const nowH = now.getHours()
    const nowM = now.getMinutes()
    return h < nowH || (h === nowH && m <= nowM)
  }
  return false
}

const onMemberSearch = async () => {
  if (!memberSearch.value) return
  try {
    const res: any = await request.get('/member/list', {
      params: { keyword: memberSearch.value }
    })
    const list: any[] = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    if (list.length === 0) {
      ElMessage.warning('未找到该手机号对应的会员')
      return
    }
    selectedMember.value = list[0]
    ElMessage.success('已选择会员：' + selectedMember.value.memberName)
  } catch (e: any) {
    ElMessage.error(e?.message || '会员搜索失败')
  }
}

const onMemberClear = () => {
  selectedMember.value = null
  memberSearch.value = ''
}

// Step 3 entered: load materials preview
const onStep3Enter = async () => {
  if (!selectedStyle.value) return
  step.value = 3
  // Pre-load material resolution in background
  try {
    const res: any = await styleGalleryApi.quickOrderFull({
      styleId: selectedStyle.value.id,
      memberId: selectedMember.value?.id
    })
    materialResult.value = res?.data || res
  } catch (e: any) {
    // 耗材加载失败不影响下单
    console.warn('[QuickOrder] 材料解析失败:', e)
  }
}

const onSubmit = async () => {
  if (!selectedStyle.value) {
    ElMessage.warning('请先选择款式')
    return
  }
  submitting.value = true
  try {
    const payload: any = {
      styleId: selectedStyle.value.id,
      memberId: selectedMember.value?.id,
      technicianId: form.value.technicianId,
      bookingDate: form.value.bookingDate || null,
      startTime: form.value.startTime || null,
      remark: form.value.remark || ''
    }

    const res: any = await styleGalleryApi.quickOrderFull(payload)
    const data = res?.data || res

    bookingWarning.value = data?.bookingWarning || ''

    if (data?.bookingId) {
      ElMessageBox.alert(
        `<div>
          <p style="font-size:16px;margin-bottom:8px">✅ 预约成功！</p>
          <p>预约单号：<b>${data.bookingNo}</b></p>
          <p>款式：${data.styleName}</p>
          <p>时间：${data.bookingDate} ${data.startTime} ~ ${data.endTime}</p>
          ${data.technicianName ? `<p>美甲师：${data.technicianName}</p>` : ''}
          ${data.materialWarning ? `<p style="color:#e6a23c">⚠️ ${data.materialWarning}</p>` : ''}
        </div>`,
        '下单完成',
        { confirmButtonText: '查看预约', type: 'success', dangerouslyUseHTMLString: true }
      ).then(() => {
        router.push('/booking/list')
      }).catch(() => {
        router.push('/style-gallery')
      })
    } else if (!data?.bookingId && !data?.bookingWarning) {
      // 款式信息加载成功但未创建预约
      ElMessageBox.alert(
        `<div>
          <p>款式信息已加载，请选择预约时间后再次提交。</p>
          ${data.materialWarning ? `<p style="color:#e6a23c">⚠️ ${data.materialWarning}</p>` : ''}
        </div>`,
        '信息确认',
        { confirmButtonText: '返回款式', type: 'info', dangerouslyUseHTMLString: true }
      ).then(() => {
        step.value = 2
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '下单失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.qo-page {
  min-height: 100vh;
  background: #f5f5f7;
  padding-bottom: 80px;
}

/* ============ Header ============ */
.qo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #ff7eb3, #ff758c);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
}
.qo-header__left,
.qo-header__right {
  width: 40px;
  cursor: pointer;
}
.qo-header__title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* ============ Steps ============ */
.qo-steps {
  padding: 12px 16px 4px;
  background: #fff;
}

/* ============ Step content ============ */
.qo-step {
  padding: 12px;
}
.qo-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 4px;
}
.qo-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
  color: #ff758c;
}
.qo-empty {
  padding: 20px 0;
}

/* ============ Style Grid (Step 0) ============ */
.qo-style-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
@media (min-width: 600px) {
  .qo-style-grid { grid-template-columns: repeat(3, 1fr); }
}

.qo-style-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  cursor: pointer;
  transition: all 0.18s;
  border: 2px solid transparent;
}
.qo-style-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}
.qo-style-card.selected {
  border-color: #ff5a7e;
  box-shadow: 0 0 0 2px rgba(255, 90, 126, 0.2);
}
.qo-style-card__cover {
  position: relative;
  width: 100%;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.qo-style-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.qo-style-card__emoji {
  font-size: 48px;
  opacity: 0.6;
}
.qo-style-card__check {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  background: #ff5a7e;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}
.qo-style-card__body {
  padding: 8px;
}
.qo-style-card__name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}
.qo-style-card__meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
}
.qo-style-card__price {
  color: #ff5a7e;
  font-weight: 600;
  font-size: 13px;
}

/* ============ Technician List (Step 1) ============ */
.qo-tech-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.qo-tech-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.18s;
  border: 2px solid transparent;
  box-shadow: 0 2px 6px rgba(0,0,0,0.04);
}
.qo-tech-card:hover {
  background: #fff5f7;
  border-color: #ffb6c1;
}
.qo-tech-card.selected {
  border-color: #ff5a7e;
  background: #fff5f7;
}
.qo-tech-card__avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff7eb3, #ff758c);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  flex-shrink: 0;
}
.qo-tech-card__info {
  flex: 1;
}
.qo-tech-card__name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.qo-tech-card__level {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.qo-tech-card__check {
  width: 24px;
  height: 24px;
  background: #ff5a7e;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.qo-skip-hint {
  text-align: center;
  font-size: 13px;
  color: #909399;
  padding: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 8px;
}
.qo-skip-hint:hover { color: #ff5a7e; }

/* ============ Form (Step 2) ============ */
.qo-form-group {
  margin-bottom: 16px;
}
.qo-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
.qo-member-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding: 8px 12px;
  background: #f0f9eb;
  border-radius: 8px;
  font-size: 13px;
  color: #67c23a;
}
.qo-time-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
@media (max-width: 480px) {
  .qo-time-grid { grid-template-columns: repeat(3, 1fr); }
}
.qo-time-slot {
  text-align: center;
  padding: 8px 4px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #606266;
  transition: all 0.15s;
  user-select: none;
}
.qo-time-slot:hover:not(.disabled) {
  border-color: #ff5a7e;
  color: #ff5a7e;
}
.qo-time-slot.selected {
  background: linear-gradient(135deg, #ff7eb3, #ff5a7e);
  border-color: #ff5a7e;
  color: #fff;
  font-weight: 600;
}
.qo-time-slot.disabled {
  background: #f5f5f5;
  color: #c0c4cc;
  cursor: not-allowed;
  text-decoration: line-through;
}

/* ============ Summary (Step 3) ============ */
.qo-summary-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.qo-summary-card--warn {
  background: #fdf6ec;
  box-shadow: none;
  border: 1px solid #f5dab1;
  padding: 10px 12px;
}
.qo-summary-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f3;
}
.qo-summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
.qo-summary-row__label {
  color: #909399;
  min-width: 60px;
}
.qo-summary-row__value {
  color: #303133;
  font-weight: 500;
  text-align: right;
}
.qo-summary-row__value.price {
  color: #ff5a7e;
  font-size: 16px;
  font-weight: 700;
}

.qo-material-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.qo-material-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 4px 0;
}
.qo-material-item.substitute {
  color: #e6a23c;
}
.qo-material-item__name {
  flex: 1;
  font-weight: 500;
}
.qo-material-item__stock {
  color: #909399;
  font-size: 11px;
}
.qo-material-item__reason {
  font-size: 11px;
  color: #e6a23c;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ Footer ============ */
.qo-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border-top: 1px solid #f0f0f3;
  padding: 10px 16px;
  display: flex;
  gap: 10px;
  z-index: 100;
}
.qo-footer .el-button {
  flex: 1;
}
</style>
