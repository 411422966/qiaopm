<template>
  <div class="level-container">
    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#409EFF"><el-icon><Medal /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.total || 0 }}</div>
              <div class="stat-label">等级总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#67C23A"><el-icon><CircleCheck /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.active || 0 }}</div>
              <div class="stat-label">启用中</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon" style="background:#E6A23C"><el-icon><Discount /></el-icon></div>
            <div>
              <div class="stat-value">{{ stats.avgDiscount || 0 }}%</div>
              <div class="stat-label">平均折扣率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 视图切换 -->
    <div class="view-switch" style="margin-top:16px">
      <el-button-group>
        <el-button :type="viewMode === 'card' ? 'primary' : ''" @click="viewMode = 'card'">
          <el-icon><Grid /></el-icon>卡片视图
        </el-button>
        <el-button :type="viewMode === 'table' ? 'primary' : ''" @click="viewMode = 'table'">
          <el-icon><List /></el-icon>列表视图
        </el-button>
      </el-button-group>
      <div style="flex:1"></div>
      <el-button type="primary" @click="handleAdd"><el-icon><Plus /></el-icon>新增等级</el-button>
    </div>

    <!-- 卡片视图 - 权益对比 -->
    <div v-if="viewMode === 'card'" class="level-cards">
      <el-row :gutter="16">
        <el-col v-for="level in tableData" :key="level.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card class="level-card" :body-style="{ padding: 0 }" shadow="hover">
            <div class="level-header" :style="{ background: `linear-gradient(135deg, ${level.levelColor}, ${shadeColor(level.levelColor, -20)})` }">
              <el-icon class="level-crown"><Trophy /></el-icon>
              <div class="level-name">{{ level.levelName }}</div>
              <div class="level-code">{{ level.levelCode }}</div>
              <el-tag v-if="level.status !== 1" size="small" type="info" style="position:absolute;top:10px;right:10px">已禁用</el-tag>
            </div>
            <div class="level-body">
              <div class="level-discount">
                <span class="discount-value">{{ (level.discountRate * 10).toFixed(1) }}</span>
                <span class="discount-label">折</span>
              </div>
              <el-divider />
              <div class="level-info">
                <div class="info-row">
                  <el-icon><Coin /></el-icon>
                  <span class="info-label">积分要求</span>
                  <span class="info-value">{{ level.minPoints }} ~ {{ level.maxPoints || '∞' }}</span>
                </div>
                <div class="info-row">
                  <el-icon><Star /></el-icon>
                  <span class="info-label">积分倍数</span>
                  <span class="info-value">{{ level.pointsMultiplier }}x</span>
                </div>
                <div class="info-row">
                  <el-icon><Present /></el-icon>
                  <span class="info-label">生日礼物</span>
                  <span class="info-value">{{ level.birthdayGift || '无' }}</span>
                </div>
              </div>
              <div class="level-desc">{{ level.description }}</div>
            </div>
            <div class="level-footer">
              <el-button link type="primary" @click="handleEdit(level)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(level)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 表格视图 -->
    <el-card v-else style="margin-top:16px">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索等级名称/编码" clearable style="width:240px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px;margin-left:8px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadData" style="margin-left:8px">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column prop="levelName" label="等级名称" width="150">
          <template #default="{ row }">
            <el-tag :style="{ backgroundColor: row.levelColor, color: '#fff', border: 'none' }">
              {{ row.levelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="levelCode" label="编码" width="150" />
        <el-table-column prop="minPoints" label="最低积分" width="100" />
        <el-table-column prop="maxPoints" label="最高积分" width="100">
          <template #default="{ row }">{{ row.maxPoints || '无上限' }}</template>
        </el-table-column>
        <el-table-column prop="discountRate" label="折扣率" width="100">
          <template #default="{ row }">{{ (row.discountRate * 10).toFixed(1) }} 折</template>
        </el-table-column>
        <el-table-column prop="pointsMultiplier" label="积分倍数" width="100" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="loadData"
        style="margin-top:16px;justify-content:flex-end;display:flex"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="等级名称" prop="levelName">
          <el-input v-model="form.levelName" />
        </el-form-item>
        <el-form-item label="等级编码" prop="levelCode">
          <el-input v-model="form.levelCode" placeholder="如 LEVEL_GOLD" />
        </el-form-item>
        <el-form-item label="等级颜色" prop="levelColor">
          <el-color-picker v-model="form.levelColor" />
        </el-form-item>
        <el-form-item label="积分范围">
          <el-input-number v-model="form.minPoints" :min="0" placeholder="最低" />
          <span style="margin:0 8px">~</span>
          <el-input-number v-model="form.maxPoints" :min="0" :precision="0" placeholder="最高(留空无上限)" />
        </el-form-item>
        <el-form-item label="折扣率" prop="discountRate">
          <el-input-number v-model="form.discountRate" :min="0" :max="1" :step="0.05" :precision="2" />
          <span style="margin-left:8px;color:#909399">0.95 表示 95折</span>
        </el-form-item>
        <el-form-item label="积分倍数" prop="pointsMultiplier">
          <el-input-number v-model="form.pointsMultiplier" :min="0.1" :max="10" :step="0.1" :precision="2" />
        </el-form-item>
        <el-form-item label="生日礼物">
          <el-input v-model="form.birthdayGift" placeholder="如 生日蛋糕" />
        </el-form-item>
        <el-form-item label="生日折扣">
          <el-input-number v-model="form.birthdayDiscount" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="等级说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Medal, CircleCheck, Discount, Grid, List, Trophy, Coin, Star, Present } from '@element-plus/icons-vue'
import { memberLevelApi } from '@/api/basic'

const tableData = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<any>({})
const viewMode = ref<'card' | 'table'>('card')
const dialogVisible = ref(false)
const dialogTitle = ref('新增等级')
const formRef = ref<FormInstance>()
const isEdit = ref(false)

const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined as number | undefined })

const form = reactive({
  id: null as number | null, levelName: '', levelCode: '', levelColor: '#409EFF',
  minPoints: 0, maxPoints: null as number | null, discountRate: 1,
  pointsMultiplier: 1, birthdayGift: '', birthdayDiscount: 1,
  sortOrder: 0, status: 1, description: ''
})

const rules: FormRules = {
  levelName: [{ required: true, message: '请输入等级名称', trigger: 'blur' }],
  levelCode: [{ required: true, message: '请输入等级编码', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await memberLevelApi.page(query)
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally { loading.value = false }
}

const loadStats = async () => {
  const res: any = await memberLevelApi.stats()
  stats.value = res.data || {}
}

const resetQuery = () => { query.keyword = ''; query.status = undefined; query.pageNum = 1; loadData() }

const shadeColor = (color: string, percent: number) => {
  if (!color || !color.startsWith('#')) return color
  const num = parseInt(color.replace('#', ''), 16)
  const amt = Math.round(2.55 * percent)
  const R = Math.max(0, Math.min(255, (num >> 16) + amt))
  const G = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amt))
  const B = Math.max(0, Math.min(255, (num & 0x0000FF) + amt))
  return '#' + (0x1000000 + (R << 16) + (G << 8) + B).toString(16).slice(1)
}

const handleAdd = () => {
  isEdit.value = false; dialogTitle.value = '新增等级'
  Object.assign(form, { id: null, levelName: '', levelCode: '', levelColor: '#409EFF', minPoints: 0, maxPoints: null, discountRate: 1, pointsMultiplier: 1, birthdayGift: '', birthdayDiscount: 1, sortOrder: 0, status: 1, description: '' })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true; dialogTitle.value = '编辑等级'
  Object.assign(form, row); dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确认删除等级 "${row.levelName}" ?`, '提示', { type: 'warning' })
  await memberLevelApi.delete(row.id); ElMessage.success('删除成功'); loadData(); loadStats()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      isEdit.value ? await memberLevelApi.update(form) : await memberLevelApi.create(form)
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false; loadData(); loadStats()
    }
  })
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.view-switch { display:flex; align-items:center; }
.toolbar { display:flex; align-items:center; margin-bottom:16px; }
.stat-item { display:flex; align-items:center; gap:16px; }
.stat-icon { width:48px; height:48px; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#fff; font-size:24px; }
.stat-value { font-size:24px; font-weight:bold; }
.stat-label { font-size:13px; color:#909399; margin-top:4px; }

.level-cards { margin-top:16px; }
.level-card { transition: transform .2s; margin-bottom:16px; }
.level-card:hover { transform: translateY(-4px); }
.level-header { padding:24px 16px; color:#fff; text-align:center; position:relative; }
.level-crown { font-size:32px; opacity:0.8; }
.level-name { font-size:20px; font-weight:bold; margin-top:8px; }
.level-code { font-size:12px; opacity:0.8; margin-top:4px; }
.level-body { padding:20px 16px; }
.level-discount { text-align:center; }
.discount-value { font-size:48px; font-weight:bold; color:#303133; }
.discount-label { font-size:16px; color:#909399; margin-left:4px; }
.level-info { padding:0 4px; }
.info-row { display:flex; align-items:center; gap:8px; padding:6px 0; font-size:13px; }
.info-row .el-icon { color:#909399; }
.info-label { color:#909399; min-width:64px; }
.info-value { color:#303133; font-weight:500; flex:1; text-align:right; }
.level-desc { padding:12px; background:#f5f7fa; border-radius:6px; font-size:13px; color:#606266; line-height:1.6; }
.level-footer { padding:8px 16px; border-top:1px solid #ebeef5; text-align:right; }
</style>