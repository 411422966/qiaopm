<template>
  <div class="spa-scene-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="场景模式" name="scenes" />
      <el-tab-pane label="房间组合" name="slots" />
      <el-tab-pane label="预约演示" name="book" />
    </el-tabs>

    <!-- ============ 场景模式卡片 ============ -->
    <div v-if="activeTab === 'scenes'">
      <div class="action-bar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增场景
        </el-button>
        <el-button @click="loadScenes">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>

      <div class="scene-grid">
        <div
          v-for="s in scenes"
          :key="s.id"
          class="scene-card"
          :class="['scene-' + s.sceneCode.toLowerCase()]"
        >
          <div class="scene-icon">{{ s.sceneIcon || sceneEmoji(s.sceneCode) }}</div>
          <div class="scene-name">{{ s.sceneName }}</div>
          <div class="scene-tags">
            <el-tag
              v-for="tag in (s.tags || '').split(',').filter(Boolean)"
              :key="tag"
              size="small"
              effect="plain"
            >{{ tag }}</el-tag>
          </div>
          <div class="scene-meta">
            <span><el-icon><User /></el-icon> {{ s.guestCount }} 人</span>
            <span><el-icon><Timer /></el-icon> {{ s.durationMin }} min</span>
          </div>
          <div class="scene-price">¥{{ s.basePrice }}</div>
          <div class="scene-desc">{{ s.description || '—' }}</div>
          <div class="scene-actions">
            <el-button size="small" @click="handleEdit(s)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleOpenSlots(s)">房间组合</el-button>
            <el-button size="small" type="success" @click="handleTryBook(s)">预约演示</el-button>
            <el-button size="small" type="danger" @click="handleDelete(s)">删除</el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!scenes.length" description="暂无场景模式，点击「新增场景」开始" />
    </div>

    <!-- ============ 房间组合配置 ============ -->
    <div v-if="activeTab === 'slots'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>房间组合 - {{ activeScene?.sceneName || '请先在「场景模式」中选择' }}</span>
            <div>
              <el-select v-model="slotSceneId" placeholder="选择场景" style="width:200px;margin-right:8px" @change="loadSlots">
                <el-option v-for="s in scenes" :key="s.id" :label="s.sceneName" :value="s.id" />
              </el-select>
              <el-button type="primary" @click="handleAddSlot" :disabled="!slotSceneId">
                <el-icon><Plus /></el-icon>新增房间
              </el-button>
              <el-button type="success" @click="handleSaveSlots" :disabled="!slotSceneId || !slots.length">
                <el-icon><Check /></el-icon>保存组合
              </el-button>
            </div>
          </div>
        </template>

        <el-table v-if="slots.length" :data="slots" border>
          <el-table-column label="序号" width="80">
            <template #default="{ $index }">
              <el-tag>{{ $index + 1 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="房间" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.roomId" placeholder="选择房间" filterable style="width:100%">
                <el-option v-for="r in roomList" :key="r.id" :label="r.roomName" :value="r.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="角色标签" width="160">
            <template #default="{ row }">
              <el-select v-model="row.roleLabel" placeholder="如：主理疗房" style="width:100%">
                <el-option label="主理疗房" value="主理疗房" />
                <el-option label="次理疗房" value="次理疗房" />
                <el-option label="休息区" value="休息区" />
                <el-option label="茶歇区" value="茶歇区" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.requiredCount" :min="1" :max="9" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ $index }">
              <el-button type="danger" size="small" @click="slots.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="slotSceneId" description="该场景暂无房间组合，点击「新增房间」添加" />
        <el-empty v-else description="请先在上方选择一个场景" />
      </el-card>
    </div>

    <!-- ============ 预约演示 ============ -->
    <div v-if="activeTab === 'book'">
      <el-row :gutter="16">
        <el-col :span="10">
          <el-card>
            <template #header>
              <span>选择场景模式</span>
            </template>
            <div class="scene-grid compact">
              <div
                v-for="s in scenes"
                :key="s.id"
                class="scene-card mini"
                :class="{ active: bookForm.sceneId === s.id }"
                @click="selectSceneForBook(s)"
              >
                <div class="scene-icon">{{ s.sceneIcon || sceneEmoji(s.sceneCode) }}</div>
                <div class="scene-name">{{ s.sceneName }}</div>
                <div class="scene-meta">
                  <span>{{ s.guestCount }} 人 · {{ s.durationMin }} min</span>
                </div>
                <div class="scene-price">¥{{ s.basePrice }}</div>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="14">
          <el-card>
            <template #header>
              <span>推荐方案</span>
            </template>
            <div v-if="!recommend" class="placeholder">
              <el-empty description="请在左侧选择场景" />
            </div>
            <div v-else>
              <el-alert :title="recommend.suggestion" :type="recommendType" :closable="false" show-icon />
              <el-descriptions style="margin-top:12px" :column="2" border>
                <el-descriptions-item label="场景模式">{{ recommend.scene.sceneName }}</el-descriptions-item>
                <el-descriptions-item label="建议人数">{{ recommend.scene.guestCount }} 人</el-descriptions-item>
                <el-descriptions-item label="建议时长">{{ recommend.scene.durationMin }} 分钟</el-descriptions-item>
                <el-descriptions-item label="基础价格">¥{{ recommend.scene.basePrice }}</el-descriptions-item>
                <el-descriptions-item label="描述" :span="2">{{ recommend.scene.description }}</el-descriptions-item>
              </el-descriptions>

              <h4 style="margin-top:20px">📍 推荐房间组合</h4>
              <el-table :data="recommend.slots" border size="small">
                <el-table-column label="序号" type="index" width="60" />
                <el-table-column label="角色" prop="roleLabel" width="120" />
                <el-table-column label="房间" prop="roomName" />
                <el-table-column label="数量" prop="requiredCount" width="80" />
              </el-table>
              <div v-if="!recommend.slots.length" style="margin-top:8px;color:#909399">
                <el-text>该场景未配置房间组合 → <el-link type="primary" @click="activeTab='slots'">去配置</el-link></el-text>
              </div>

              <el-divider />
              <el-form label-width="100px">
                <el-form-item label="客户姓名">
                  <el-input v-model="bookForm.memberName" placeholder="选填" />
                </el-form-item>
                <el-form-item label="联系电话">
                  <el-input v-model="bookForm.memberPhone" placeholder="选填" />
                </el-form-item>
                <el-form-item label="预约时间">
                  <el-date-picker v-model="bookForm.bookingDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :disabled="!bookForm.sceneId" @click="handleSubmitBooking">
                    <el-icon><Check /></el-icon>确认预约
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- ============ 编辑/新增场景对话框 ============ -->
    <el-dialog v-model="editDialogVisible" :title="editing.id ? '编辑场景' : '新增场景'" width="560px">
      <el-form :model="editing" label-width="100px">
        <el-form-item label="场景编码">
          <el-select v-model="editing.sceneCode" :disabled="!!editing.id" placeholder="请选择">
            <el-option label="SINGLE(单人)" value="SINGLE" />
            <el-option label="COUPLE(情侣)" value="COUPLE" />
            <el-option label="GIRLFRIEND(闺蜜)" value="GIRLFRIEND" />
            <el-option label="FAMILY(家庭)" value="FAMILY" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景名称">
          <el-input v-model="editing.sceneName" />
        </el-form-item>
        <el-form-item label="场景图标">
          <el-input v-model="editing.sceneIcon" placeholder="如：🧘 / 💑 / 👯 / 👨‍👩‍👧" />
        </el-form-item>
        <el-form-item label="人数">
          <el-input-number v-model="editing.guestCount" :min="1" :max="9" />
        </el-form-item>
        <el-form-item label="建议时长">
          <el-input-number v-model="editing.durationMin" :min="15" :step="15" />
          <span style="margin-left:8px">分钟</span>
        </el-form-item>
        <el-form-item label="基础价格">
          <el-input-number v-model="editing.basePrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editing.tags" placeholder="逗号分隔，如：舒压,放松,单人" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editing.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editing.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editing.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveScene">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Check, User, Timer } from '@element-plus/icons-vue'
import http from '@/utils/http'

interface SpaScene {
  id?: number
  sceneCode: string
  sceneName: string
  sceneIcon?: string
  description?: string
  guestCount: number
  durationMin: number
  basePrice: number
  tags?: string
  sortNo?: number
  status: number
}

interface SpaSceneSlot {
  id?: number
  sceneId?: number
  roomId: number | null
  roomName?: string
  roleLabel?: string
  requiredCount?: number
  sortNo?: number
}

interface Room { id: number; roomName: string; }

const activeTab = ref('scenes')
const scenes = ref<SpaScene[]>([])
const roomList = ref<Room[]>([])

const editDialogVisible = ref(false)
const editing = ref<SpaScene>(freshEditing())

const slotSceneId = ref<number | null>(null)
const slots = ref<SpaSceneSlot[]>([])

const bookForm = ref({
  sceneId: null as number | null,
  memberName: '',
  memberPhone: '',
  bookingDate: ''
})

const recommend = ref<any>(null)

const activeScene = computed(() => scenes.value.find(s => s.id === slotSceneId.value))
const recommendType = computed(() => {
  if (!recommend.value) return 'info'
  if (recommend.value.availableRoomCount === 0) return 'error'
  if (recommend.value.availableRoomCount < (recommend.value.slots?.length || 0)) return 'warning'
  return 'success'
})

function freshEditing(): SpaScene {
  return {
    sceneCode: 'SINGLE',
    sceneName: '',
    sceneIcon: '',
    guestCount: 1,
    durationMin: 60,
    basePrice: 0,
    tags: '',
    description: '',
    status: 1,
    sortNo: 0
  }
}

function sceneEmoji(code: string) {
  return { SINGLE: '🧘', COUPLE: '💑', GIRLFRIEND: '👯', FAMILY: '👨‍👩‍👧' }[code] || '🌿'
}

async function loadScenes() {
  const r: any = await http.get('/api/spa-scene/list')
  scenes.value = r.data || []
}

async function loadRooms() {
  const r: any = await http.get('/room/list', { params: { status: 1 } })
  roomList.value = r.data || []
}

async function loadRecommend(id: number) {
  const r: any = await http.get(`/api/spa-scene/${id}/recommend`)
  recommend.value = r.data
}

async function loadSlots() {
  if (!slotSceneId.value) { slots.value = []; return }
  const r: any = await http.get(`/api/spa-scene/${slotSceneId.value}/slots`)
  slots.value = r.data || []
}

function handleAdd() {
  editing.value = freshEditing()
  editDialogVisible.value = true
}

function handleEdit(scene: SpaScene) {
  editing.value = { ...scene }
  editDialogVisible.value = true
}

async function handleSaveScene() {
  const e = editing.value
  if (!e.sceneName) { ElMessage.warning('请输入场景名称'); return }
  if (e.id) {
    await http.put(`/api/spa-scene/${e.id}`, e)
    ElMessage.success('已更新')
  } else {
    await http.post('/api/spa-scene', e)
    ElMessage.success('已创建')
  }
  editDialogVisible.value = false
  loadScenes()
}

async function handleDelete(scene: SpaScene) {
  try {
    await ElMessageBox.confirm(`确认删除场景「${scene.sceneName}」？`, '提示', { type: 'warning' })
    await http.delete(`/api/spa-scene/${scene.id}`)
    ElMessage.success('已删除')
    loadScenes()
  } catch { /* cancelled */ }
}

function handleOpenSlots(scene: SpaScene) {
  activeTab.value = 'slots'
  slotSceneId.value = scene.id || null
  loadSlots()
}

function handleAddSlot() {
  slots.value.push({
    sceneId: slotSceneId.value || undefined,
    roomId: null,
    roleLabel: '主理疗房',
    requiredCount: 1,
    sortNo: slots.value.length + 1
  })
}

async function handleSaveSlots() {
  if (!slotSceneId.value) return
  // 同步房间名称快照
  slots.value.forEach((s: any) => {
    const r = roomList.value.find(x => x.id === s.roomId)
    if (r) s.roomName = r.roomName
  })
  await http.put(`/api/spa-scene/${slotSceneId.value}/slots`, slots.value)
  ElMessage.success('房间组合已保存')
  loadSlots()
}

function handleTryBook(scene: SpaScene) {
  activeTab.value = 'book'
  selectSceneForBook(scene)
}

function selectSceneForBook(scene: SpaScene) {
  bookForm.value.sceneId = scene.id || null
  loadRecommend(scene.id!)
}

async function handleSubmitBooking() {
  if (!bookForm.value.sceneId) { ElMessage.warning('请选择场景'); return }
  if (!bookForm.value.bookingDate) { ElMessage.warning('请选择预约时间'); return }
  const scene = scenes.value.find(s => s.id === bookForm.value.sceneId)
  if (!scene) return
  // 简化演示：仅提示成功，不实际插 biz_booking
  ElMessageBox.alert(
    `场景：${scene.sceneName}\n人数：${scene.guestCount}\n时间：${bookForm.value.bookingDate}\n客户：${bookForm.value.memberName || '匿名'}`,
    '预约演示成功',
    { confirmButtonText: '好' }
  )
}

watch(slotSceneId, () => loadSlots())

onMounted(() => {
  loadScenes()
  loadRooms()
})
</script>

<style scoped>
.spa-scene-page { padding: 4px; }
.action-bar { margin-bottom: 16px; display: flex; gap: 8px; }
.scene-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.scene-grid.compact { grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; }
.scene-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.2s;
  cursor: default;
}
.scene-card:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(0,0,0,0.08); }
.scene-card.mini { padding: 12px; cursor: pointer; }
.scene-card.mini.active { border-color: #409EFF; background: #ecf5ff; }
.scene-icon { font-size: 48px; margin-bottom: 8px; }
.scene-card.mini .scene-icon { font-size: 32px; }
.scene-name { font-size: 18px; font-weight: 600; margin-bottom: 8px; color: #303133; }
.scene-meta { display: flex; gap: 12px; color: #909399; font-size: 13px; margin-bottom: 8px; }
.scene-meta span { display: flex; align-items: center; gap: 2px; }
.scene-price { font-size: 24px; font-weight: 700; color: #f56c6c; margin: 8px 0; }
.scene-card.mini .scene-price { font-size: 16px; }
.scene-desc { color: #909399; font-size: 13px; line-height: 1.5; min-height: 36px; margin-bottom: 12px; }
.scene-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
.scene-actions { display: flex; gap: 4px; flex-wrap: wrap; }
.scene-single { border-left: 4px solid #67c23a; }
.scene-couple { border-left: 4px solid #e6a23c; }
.scene-girlfriend { border-left: 4px solid #ec407a; }
.scene-family { border-left: 4px solid #409eff; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.placeholder { padding: 40px 0; }
</style>