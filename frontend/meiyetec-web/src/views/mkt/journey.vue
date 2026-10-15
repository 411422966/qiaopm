<template>
  <div class="journey-page">
    <el-card class="search-bar">
      <el-input v-model="filter.keyword" placeholder="名称 / 编码" style="width: 220px" clearable @keyup.enter="loadData" />
      <el-select v-model="filter.journeyType" placeholder="类型" clearable style="width: 140px" @change="loadData">
        <el-option label="营销" value="MARKETING" />
        <el-option label="关怀" value="CARE" />
        <el-option label="挽回" value="RETENTION" />
        <el-option label="新客" value="ONBOARD" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="loadData">
        <el-option label="草稿" :value="1" />
        <el-option label="已发布" :value="2" />
        <el-option label="已暂停" :value="3" />
        <el-option label="已结束" :value="4" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button type="primary" @click="openCreate">+ 新建旅程</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="journeyCode" label="编码" width="140" />
        <el-table-column prop="journeyName" label="名称" min-width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.journeyType)">{{ typeLabel(row.journeyType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerEvent" label="触发事件" width="160" />
        <el-table-column label="节点数" width="80" align="center">
          <template #default="{ row }">
            <span>{{ (row.nodesJson ? JSON.parse(row.nodesJson || '[]').length : 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进入/完成" width="120">
          <template #default="{ row }">
            <span>{{ row.enterCount }}</span>
            <span> / </span>
            <span style="color: #67c23a">{{ row.completeCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openCanvas(row)">画布</el-button>
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="primary" :disabled="row.status !== 1" @click="publish(row)">发布</el-button>
            <el-button link size="small" type="warning" :disabled="row.status !== 2" @click="pause(row)">暂停</el-button>
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

    <!-- 编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑旅程' : '新建旅程'" width="800px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" required><el-input v-model="form.journeyCode" :disabled="!!dialog.id" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required><el-input v-model="form.journeyName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.journeyType" style="width: 100%">
                <el-option label="营销" value="MARKETING" />
                <el-option label="关怀" value="CARE" />
                <el-option label="挽回" value="RETENTION" />
                <el-option label="新客" value="ONBOARD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="触发事件">
              <el-select v-model="form.triggerEvent" style="width: 100%">
                <el-option label="会员注册" value="MEMBER_REGISTER" />
                <el-option label="订单完成" value="ORDER_COMPLETED" />
                <el-option label="套餐交付" value="CARD_DELIVERED" />
                <el-option label="生日" value="BIRTHDAY" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="节点 JSON">
          <el-input v-model="form.nodesJson" type="textarea" :rows="6"
            placeholder='[{"id":"n1","type":"START","name":"进入"},{"id":"n2","type":"WAIT","hours":24,"name":"等1天"},{"id":"n3","type":"SMS","template":"TPL-001","name":"发短信"}]' />
          <div class="form-tip">节点类型：START/WAIT/SMS/MINI_PROGRAM/EMAIL/CONDITION/END</div>
        </el-form-item>
        <el-form-item label="连线 JSON">
          <el-input v-model="form.edgesJson" type="textarea" :rows="4"
            placeholder='[{"from":"n1","to":"n2"},{"from":"n2","to":"n3"}]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 画布编辑器 -->
    <el-dialog v-model="canvasDialog.visible"
      :title="`旅程画布 — ${canvasDialog.journey?.journeyName || ''}`"
      width="92%" top="3vh" :close-on-click-modal="false"
      @closed="onCanvasClosed">
      <div class="journey-editor" v-if="canvasDialog.journey">
        <!-- 左侧：节点面板 -->
        <div class="node-palette">
          <div class="palette-title">节点组件</div>
          <div
            v-for="n in NODE_PALETTE" :key="n.type"
            class="palette-node"
            :class="`node-${n.type.toLowerCase()}`"
            draggable="true"
            @dragstart="onPaletteDragStart($event, n)"
            @click="addNodeToCanvas(n)">
            <el-icon size="16"><component :is="n.icon" /></el-icon>
            <span>{{ n.label }}</span>
          </div>

          <el-divider />
          <div class="palette-title">旅程属性</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="编码">{{ canvasDialog.journey.journeyCode }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag size="small">{{ typeLabel(canvasDialog.journey.journeyType) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="触发">{{ canvasDialog.journey.triggerEvent }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusTag(canvasDialog.journey.status)">{{ statusLabel(canvasDialog.journey.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="进入/完成">{{ canvasDialog.journey.enterCount }} / {{ canvasDialog.journey.completeCount }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 中间：画布 -->
        <div class="canvas-area"
          ref="canvasRef"
          @dragover.prevent
          @drop="onCanvasDrop"
          @click.self="selectedNode = null">

          <!-- SVG 连线层 -->
          <svg class="edge-layer" :width="canvasWidth" :height="canvasHeight">
            <defs>
              <marker id="arrowhead" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
                <polygon points="0 0, 8 3, 0 6" fill="#8b5cf6" />
              </marker>
            </defs>
            <path v-for="e in canvasEdges" :key="e.id"
              :d="edgePath(e)" fill="none" stroke="#8b5cf6" stroke-width="2"
              marker-end="url(#arrowhead)" class="edge-path" />

            <!-- 正在画的连线 -->
            <path v-if="drawingEdge" :d="drawingEdgePath" fill="none"
              stroke="#8b5cf6" stroke-width="2" stroke-dasharray="5,5" />
          </svg>

          <!-- 节点层 -->
          <div
            v-for="n in canvasNodes" :key="n.id"
            class="canvas-node"
            :class="[`canvas-node-${n.type.toLowerCase()}`, { selected: selectedNode?.id === n.id, 'has-error': n._error }]"
            :style="{ left: n.x + 'px', top: n.y + 'px' }"
            @mousedown="onNodeMouseDown($event, n)"
            @click.stop="selectNode(n)"
            @dblclick.stop="editNodeProp(n)">

            <div class="cn-icon">
              <el-icon size="18"><component :is="getNodeIcon(n.type)" /></el-icon>
            </div>
            <div class="cn-body">
              <div class="cn-name">{{ n.name || n.id }}</div>
              <div class="cn-type">{{ n.type }}</div>
              <div v-if="n.hours" class="cn-sub">⏰ {{ n.hours }}h</div>
              <div v-if="n.template" class="cn-sub">📋 {{ n.template }}</div>
              <div v-if="n.condition" class="cn-sub">🔀 {{ n.condition }}</div>
            </div>

            <!-- 连接桩 -->
            <div class="cn-handle cn-handle-out"
              @mousedown.stop="startDrawingEdge($event, n)"></div>
            <div class="cn-handle cn-handle-in"></div>

            <!-- 删除 -->
            <el-icon class="cn-delete" @click.stop="removeNode(n)"><Close /></el-icon>
          </div>

          <!-- 空状态 -->
          <div v-if="!canvasNodes.length" class="canvas-empty">
            <el-icon :size="48" color="#dcdfe6"><Tickets /></el-icon>
            <p>从左侧拖入节点或点击添加</p>
          </div>
        </div>

        <!-- 右侧：节点属性 -->
        <div class="node-props">
          <div class="props-title">
            {{ selectedNode ? '节点属性' : '选择节点以编辑' }}
          </div>
          <template v-if="selectedNode">
            <el-form label-width="80px" size="small">
              <el-form-item label="节点 ID">
                <el-input v-model="selectedNode.id" disabled />
              </el-form-item>
              <el-form-item label="名称">
                <el-input v-model="selectedNode.name" placeholder="节点显示名称" />
              </el-form-item>
              <el-form-item label="类型">
                <el-tag size="small">{{ selectedNode.type }}</el-tag>
              </el-form-item>

              <template v-if="selectedNode.type === 'WAIT'">
                <el-form-item label="等待时长">
                  <el-input-number v-model="selectedNode.hours" :min="1" :max="720" />
                  <span class="unit">小时</span>
                </el-form-item>
              </template>

              <template v-if="selectedNode.type === 'SMS' || selectedNode.type === 'EMAIL' || selectedNode.type === 'MINI_PROGRAM'">
                <el-form-item label="模板 ID">
                  <el-input v-model="selectedNode.template" placeholder="模板标识" />
                </el-form-item>
                <el-form-item label="内容">
                  <el-input v-model="selectedNode.content" type="textarea" :rows="3" />
                </el-form-item>
              </template>

              <template v-if="selectedNode.type === 'CONDITION'">
                <el-form-item label="条件表达式">
                  <el-input v-model="selectedNode.condition" placeholder="如: rating < 3" />
                </el-form-item>
                <el-form-item label="为真分支">
                  <el-select v-model="selectedNode.trueTo" placeholder="选择目标节点" clearable style="width: 100%">
                    <el-option v-for="n in otherNodes(selectedNode)" :key="n.id" :label="n.name || n.id" :value="n.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="为假分支">
                  <el-select v-model="selectedNode.falseTo" placeholder="选择目标节点" clearable style="width: 100%">
                    <el-option v-for="n in otherNodes(selectedNode)" :key="n.id" :label="n.name || n.id" :value="n.id" />
                  </el-select>
                </el-form-item>
              </template>

              <template v-if="selectedNode.type === 'COUPON'">
                <el-form-item label="优惠券">
                  <el-input v-model="selectedNode.couponId" placeholder="优惠券 ID" />
                </el-form-item>
              </template>

              <template v-if="selectedNode.type === 'TAG'">
                <el-form-item label="打标签">
                  <el-input v-model="selectedNode.tag" placeholder="标签名，如 VIP" />
                </el-form-item>
              </template>
            </el-form>
          </template>
          <template v-else>
            <el-empty :image-size="60" description="点击画布中的节点可编辑属性" />
          </template>

          <!-- 操作按钮 -->
          <div class="props-actions">
            <el-button type="primary" @click="saveCanvas" :loading="canvasSaving">保存画布</el-button>
            <el-button @click="resetCanvas">重置</el-button>
            <el-button type="success" @click="previewJourney" plain>预览 JSON</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 预览 JSON -->
    <el-dialog v-model="previewVisible" title="旅程节点预览" width="640px">
      <pre class="json-preview">{{ previewJson }}</pre>
      <template #footer>
        <el-button @click="copyJson">复制 JSON</el-button>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
import {
  Tickets, CircleCheck, VideoPause as Pause, VideoPlay as Play, Plus, Delete, Edit, Close, Refresh,
  Message, ChatLineRound as Mail, Ticket as DiscountCoupon, PriceTag, Clock, QuestionFilled,
  Promotion, User, DocumentCopy
} from '@element-plus/icons-vue'

const filter = reactive({
  pageNum: 1, pageSize: 20, keyword: '',
  journeyType: undefined as string | undefined, status: undefined as number | undefined
})
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

// 节点图例
const NODE_PALETTE = [
  { type: 'START',      label: '开始',      icon: 'Promotion',      color: '#67c23a' },
  { type: 'WAIT',      label: '等待',      icon: 'Clock',           color: '#e6a23c' },
  { type: 'SMS',        label: '短信',      icon: 'Message',         color: '#409eff' },
  { type: 'EMAIL',      label: '邮件',      icon: 'Mail',            color: '#909399' },
  { type: 'CONDITION',  label: '条件分支',  icon: 'QuestionFilled',   color: '#f56c6c' },
  { type: 'COUPON',    label: '发优惠券',  icon: 'DiscountCoupon',  color: '#f56c6c' },
  { type: 'TAG',        label: '打标签',    icon: 'PriceTag',        color: '#9c27b0' },
  { type: 'PUSH',      label: '小程序推送',icon: 'Promotion',       color: '#00bcd4' },
  { type: 'END',        label: '结束',      icon: 'CircleCheck',      color: '#909399' },
]

function getNodeIcon(type: string) {
  const m: Record<string, string> = {
    START: 'Promotion', WAIT: 'Clock', SMS: 'Message', EMAIL: 'Mail',
    CONDITION: 'QuestionFilled', COUPON: 'DiscountCoupon', TAG: 'PriceTag',
    PUSH: 'Promotion', END: 'CircleCheck'
  }
  return m[type] || 'Tickets'
}

async function loadData() {
  loading.value = true
  try {
    const r = await http.get('/mkt/journey/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.keyword = ''; filter.journeyType = undefined
  filter.status = undefined; filter.pageNum = 1
  loadData()
}

function typeLabel(t: string) {
  return ({ MARKETING: '营销', CARE: '关怀', RETENTION: '挽回', ONBOARD: '新客' } as any)[t] || t
}
function typeTag(t: string) {
  return ({ MARKETING: 'primary', CARE: 'success', RETENTION: 'warning', ONBOARD: 'info' } as any)[t] || ''
}
function statusLabel(s: number) {
  return ({ 1: '草稿', 2: '已发布', 3: '已暂停', 4: '已结束' } as any)[s] || '—'
}
function statusTag(s: number) {
  return ({ 1: 'info', 2: 'success', 3: 'warning', 4: '' } as any)[s] || 'info'
}

const dialog = reactive({ visible: false, id: undefined as number | undefined })
const form = reactive<any>({
  journeyCode: '', journeyName: '', journeyType: 'MARKETING',
  description: '', triggerEvent: 'MEMBER_REGISTER',
  nodesJson: '[{"id":"n1","type":"START","name":"进入"},{"id":"n2","type":"END","name":"结束"}]',
  edgesJson: '[{"from":"n1","to":"n2"}]'
})

function openCreate() { dialog.id = undefined; dialog.visible = true }
function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, {
    journeyCode: row.journeyCode, journeyName: row.journeyName,
    journeyType: row.journeyType, description: row.description,
    triggerEvent: row.triggerEvent, nodesJson: row.nodesJson, edgesJson: row.edgesJson
  })
  dialog.visible = true
}
function resetDialog() {
  Object.assign(form, {
    journeyCode: '', journeyName: '', journeyType: 'MARKETING',
    description: '', triggerEvent: 'MEMBER_REGISTER',
    nodesJson: '[{"id":"n1","type":"START","name":"进入"},{"id":"n2","type":"END","name":"结束"}]',
    edgesJson: '[{"from":"n1","to":"n2"}]'
  })
}
async function save() {
  if (!form.journeyCode || !form.journeyName) return ElMessage.warning('编码/名称 必填')
  if (dialog.id) {
    await http.put(`/mkt/journey/${dialog.id}`, form)
    ElMessage.success('已更新')
  } else {
    await http.post('/mkt/journey', form)
    ElMessage.success('已创建')
  }
  dialog.visible = false
  loadData()
}

async function publish(row: any) {
  await http.post(`/mkt/journey/${row.id}/publish`)
  ElMessage.success('已发布'); loadData()
}
async function pause(row: any) {
  await http.post(`/mkt/journey/${row.id}/pause`)
  ElMessage.success('已暂停'); loadData()
}

// ===== 画布编辑器 =====
const canvasDialog = reactive<any>({ visible: false, journey: null })
const canvasNodes = ref<any[]>([])
const canvasEdges = ref<any[]>([])
const canvasWidth = ref(900)
const canvasHeight = ref(500)
const canvasRef = ref<any>(null)
const selectedNode = ref<any>(null)
const canvasSaving = ref(false)
let nodeCounter = 1
let edgeCounter = 1

const drawingEdge = ref<any>(null)   // 正在画的连线起点
const drawingEdgePath = ref('')
const mousePos = ref({ x: 0, y: 0 })

async function openCanvas(row: any) {
  const r = await http.get(`/mkt/journey/${row.id}`)
  canvasDialog.journey = r.data?.journey || row
  canvasDialog.visible = true

  try {
    const rawNodes = JSON.parse(canvasDialog.journey.nodesJson || '[]')
    canvasNodes.value = rawNodes.map((n: any) => ({ ...n, x: n.x || 50 + Math.random() * 200, y: n.y || 50 + Math.random() * 200 }))
  } catch { canvasNodes.value = [] }
  try {
    canvasEdges.value = JSON.parse(canvasDialog.journey.edgesJson || '[]')
  } catch { canvasEdges.value = [] }
  nodeCounter = canvasNodes.value.length + 1
  edgeCounter = canvasEdges.value.length + 1
}

function onCanvasClosed() {
  selectedNode.value = null
  drawingEdge.value = null
  drawingEdgePath.value = ''
  canvasNodes.value = []
  canvasEdges.value = []
}

function otherNodes(cur: any) {
  return canvasNodes.value.filter(n => n.id !== cur.id && n.type !== 'START')
}

// 从面板点击添加节点
function addNodeToCanvas(paletteNode: any) {
  const newNode = {
    id: `n${nodeCounter++}`,
    type: paletteNode.type,
    name: paletteNode.label,
    x: 100 + Math.random() * 200,
    y: 100 + Math.random() * 100,
    hours: paletteNode.type === 'WAIT' ? 24 : undefined,
    template: (paletteNode.type === 'SMS' || paletteNode.type === 'EMAIL') ? '' : undefined,
    condition: paletteNode.type === 'CONDITION' ? '' : undefined,
  }
  canvasNodes.value.push(newNode)
}

// 拖拽添加
let dragPaletteNode: any = null
function onPaletteDragStart(e: DragEvent, n: any) { dragPaletteNode = n }
function onCanvasDrop(e: DragEvent) {
  if (!dragPaletteNode) return
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  const x = e.clientX - rect.left - 40
  const y = e.clientY - rect.top - 20
  canvasNodes.value.push({
    id: `n${nodeCounter++}`,
    type: dragPaletteNode.type,
    name: dragPaletteNode.label,
    x, y,
    hours: dragPaletteNode.type === 'WAIT' ? 24 : undefined,
    template: dragPaletteNode.type === 'SMS' ? '' : undefined,
  })
  dragPaletteNode = null
}

// 节点选择和移动
function selectNode(n: any) { selectedNode.value = n }
function editNodeProp(_n: any) {}
let dragging = false
let dragOffset = { x: 0, y: 0 }
function onNodeMouseDown(e: MouseEvent, n: any) {
  selectedNode.value = n
  dragging = true
  dragOffset = { x: e.clientX - n.x, y: e.clientY - n.y }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}
function onMouseMove(e: MouseEvent) {
  mousePos.value = { x: e.clientX, y: e.clientY }
  if (drawingEdge.value) {
    const fromNode = canvasNodes.value.find(n => n.id === drawingEdge.value)
    if (fromNode) {
      const rect = canvasRef.value?.getBoundingClientRect()
      if (rect) {
        const x1 = fromNode.x + 80
        const y1 = fromNode.y + 25
        const x2 = e.clientX - rect.left
        const y2 = e.clientY - rect.top
        drawingEdgePath.value = `M ${x1} ${y1} C ${x1 + 60} ${y1}, ${x2 - 60} ${y2}, ${x2} ${y2}`
      }
    }
  }
  if (dragging && selectedNode.value) {
    const rect = canvasRef.value?.getBoundingClientRect()
    if (!rect) return
    selectedNode.value.x = Math.max(0, Math.min(e.clientX - dragOffset.x, canvasWidth.value - 160))
    selectedNode.value.y = Math.max(0, Math.min(e.clientY - dragOffset.y, canvasHeight.value - 60))
  }
}
function onMouseUp() {
  dragging = false
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
}

function removeNode(n: any) {
  canvasNodes.value = canvasNodes.value.filter(x => x.id !== n.id)
  canvasEdges.value = canvasEdges.value.filter(e => e.from !== n.id && e.to !== n.id)
  if (selectedNode.value?.id === n.id) selectedNode.value = null
}

// 连线
function startDrawingEdge(e: MouseEvent, n: any) {
  if (n.type === 'END') return
  drawingEdge.value = n
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onEdgeMouseUp)
}
function onEdgeMouseUp(e: MouseEvent) {
  if (!drawingEdge.value) return
  const fromNode = drawingEdge.value
  // 找目标节点
  const rect = canvasRef.value?.getBoundingClientRect()
  if (rect) {
    const mx = e.clientX - rect.left
    const my = e.clientY - rect.top
    const target = canvasNodes.value.find(n => {
      if (n.id === fromNode.id) return false
      return mx >= n.x && mx <= n.x + 160 && my >= n.y && my <= n.y + 50
    })
    if (target) {
      // 避免重复连线
      if (!canvasEdges.value.find(e => e.from === fromNode.id && e.to === target.id)) {
        canvasEdges.value.push({ id: `e${edgeCounter++}`, from: fromNode.id, to: target.id })
      }
    }
  }
  drawingEdge.value = null
  drawingEdgePath.value = ''
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onEdgeMouseUp)
}

// 计算连线路径
function edgePath(e: any) {
  const from = canvasNodes.value.find(n => n.id === e.from)
  const to = canvasNodes.value.find(n => n.id === e.to)
  if (!from || !to) return ''
  const x1 = from.x + 80; const y1 = from.y + 25
  const x2 = to.x + 80; const y2 = to.y + 25
  const dx = Math.abs(x2 - x1)
  return `M ${x1} ${y1} C ${x1 + Math.max(dx * 0.5, 60)} ${y1}, ${x2 - Math.max(dx * 0.5, 60)} ${y2}, ${x2} ${y2}`
}

// 保存画布
async function saveCanvas() {
  canvasSaving.value = true
  try {
    const nodesJson = JSON.stringify(canvasNodes.value.map(({ x, y, ...n }) => n))
    const edgesJson = JSON.stringify(canvasEdges.value)
    await http.put(`/mkt/journey/${canvasDialog.journey.id}`, {
      nodesJson, edgesJson
    })
    ElMessage.success('画布已保存')
    canvasDialog.journey.nodesJson = nodesJson
    canvasDialog.journey.edgesJson = edgesJson
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    canvasSaving.value = false
  }
}

function resetCanvas() {
  try {
    const rawNodes = JSON.parse(canvasDialog.journey.nodesJson || '[]')
    canvasNodes.value = rawNodes.map((n: any) => ({ ...n, x: n.x || 50 + Math.random() * 200, y: n.y || 50 + Math.random() * 100 }))
    canvasEdges.value = JSON.parse(canvasDialog.journey.edgesJson || '[]')
  } catch { canvasNodes.value = []; canvasEdges.value = [] }
}

// 预览
const previewVisible = ref(false)
const previewJson = ref('')
function previewJourney() {
  previewJson.value = JSON.stringify({ nodes: canvasNodes.value, edges: canvasEdges.value }, null, 2)
  previewVisible.value = true
}
function copyJson() {
  navigator.clipboard.writeText(previewJson.value).then(() => ElMessage.success('已复制'))
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-input, .search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }
.form-tip { font-size: 12px; color: #94a3b8; margin-top: 4px; }

/* 旧画布（保留兼容） */
.canvas-wrap { display: flex; gap: 12px; flex-wrap: wrap; padding: 20px; background: #f8fafc; border-radius: 8px; }
.canvas-node { min-width: 160px; padding: 12px; background: white; border: 2px solid #cbd5e1; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.canvas-node[data-type="START"] { border-color: #67c23a; }
.canvas-node[data-type="END"] { border-color: #909399; }
.canvas-node[data-type="SMS"] { border-color: #409eff; }
.canvas-node[data-type="WAIT"] { border-color: #e6a23c; }
.node-title { font-weight: 600; margin-bottom: 4px; }
.node-type { font-size: 11px; color: #94a3b8; }
.node-extra { font-size: 11px; color: #475569; margin-top: 2px; }

/* ===== 新画布编辑器 ===== */
.journey-editor {
  display: flex;
  gap: 0;
  height: 72vh;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}

/* 左侧节点面板 */
.node-palette {
  width: 200px;
  flex-shrink: 0;
  background: #fafbfc;
  border-right: 1px solid #e4e7ed;
  padding: 12px;
  overflow-y: auto;
}
.palette-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.palette-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: grab;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  border: 1px solid transparent;
  transition: all 0.15s;
  user-select: none;
}
.palette-node:active { cursor: grabbing; transform: scale(0.97); }
.palette-node:hover { filter: brightness(1.1); box-shadow: 0 2px 8px rgba(0,0,0,0.15); }

.node-start, .canvas-node-start   { background: linear-gradient(135deg, #67c23a, #85ce61); }
.node-wait, .canvas-node-wait   { background: linear-gradient(135deg, #e6a23c, #f3d19e); color: #92400e; }
.node-sms, .canvas-node-sms     { background: linear-gradient(135deg, #409eff, #79bbff); }
.node-email, .canvas-node-email { background: linear-gradient(135deg, #909399, #c0c4cc); }
.node-condition, .canvas-node-condition { background: linear-gradient(135deg, #f56c6c, #fab6b6); }
.node-coupon, .canvas-node-coupon { background: linear-gradient(135deg, #f56c6c, #f78918); }
.node-tag, .canvas-node-tag     { background: linear-gradient(135deg, #9c27b0, #ce93d8); }
.node-push, .canvas-node-push  { background: linear-gradient(135deg, #00bcd4, #80deea); }
.node-end, .canvas-node-end    { background: linear-gradient(135deg, #909399, #c0c4cc); }

/* 中间画布 */
.canvas-area {
  flex: 1;
  position: relative;
  background: #f5f7fa url("data:image/svg+xml,%3Csvg width='20' height='20' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M 20 0 L 0 0 0 20' fill='none' stroke='%23e4e7ed' stroke-width='0.5'/%3E%3C/svg%3E");
  overflow: auto;
  cursor: default;
}
.edge-layer {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 1;
}
.edge-path { pointer-events: stroke; }
.canvas-node {
  position: absolute;
  width: 160px;
  min-height: 50px;
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  cursor: move;
  z-index: 2;
  border: 2px solid transparent;
  transition: box-shadow 0.15s, border-color 0.15s;
  display: flex;
  align-items: center;
  gap: 8px;
}
.canvas-node:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.15); }
.canvas-node.selected { border-color: #8b5cf6; box-shadow: 0 0 0 3px rgba(139,92,246,0.2), 0 4px 16px rgba(0,0,0,0.15); }
.canvas-node.has-error { border-color: #f56c6c; }
.cn-icon { flex-shrink: 0; color: rgba(255,255,255,0.9); }
.canvas-node-wait .cn-icon, .canvas-node-email .cn-icon, .canvas-node-end .cn-icon { color: rgba(0,0,0,0.4); }
.cn-body { flex: 1; min-width: 0; }
.cn-name { font-weight: 600; font-size: 13px; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.canvas-node-wait .cn-name, .canvas-node-email .cn-name, .canvas-node-end .cn-name { color: rgba(0,0,0,0.75); }
.cn-type { font-size: 10px; opacity: 0.75; color: #fff; }
.canvas-node-wait .cn-type, .canvas-node-email .cn-type, .canvas-node-end .cn-type { color: rgba(0,0,0,0.5); }
.cn-sub { font-size: 10px; color: rgba(255,255,255,0.8); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cn-delete {
  position: absolute; top: -8px; right: -8px;
  background: #f56c6c; color: #fff; border-radius: 50%; width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center; font-size: 12px;
  cursor: pointer; opacity: 0; transition: opacity 0.15s;
}
.canvas-node:hover .cn-delete { opacity: 1; }
.cn-handle {
  position: absolute; width: 10px; height: 10px; border-radius: 50%;
  border: 2px solid #8b5cf6; background: #fff;
}
.cn-handle-out { right: -6px; top: 50%; transform: translateY(-50%); cursor: crosshair; }
.cn-handle-in { left: -6px; top: 50%; transform: translateY(-50%); background: #8b5cf6; border-color: #8b5cf6; }
.canvas-empty {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px;
  color: #c0c4cc; pointer-events: none;
}
.canvas-empty p { font-size: 14px; margin: 0; }

/* 右侧属性面板 */
.node-props {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.props-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.node-props .el-form { padding: 8px 12px; }
.node-props .unit { margin-left: 6px; font-size: 12px; color: #909399; }
.props-actions {
  padding: 12px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: auto;
}

/* JSON 预览 */
.json-preview {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 6px;
  font-size: 12px;
  max-height: 400px;
  overflow-y: auto;
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
