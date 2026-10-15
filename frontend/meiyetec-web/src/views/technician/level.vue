<template>
  <div class="level-page">
    <!-- 等级统计卡片 -->
    <div class="level-cards">
      <div
        v-for="level in levelList"
        :key="level.id"
        class="level-card"
        :style="{ borderTopColor: level.levelColor || '#409EFF' }"
        @click="goToBenefit(level)"
      >
        <div class="card-icon">
          <el-icon :size="32">
            <Medal />
          </el-icon>
        </div>
        <div class="card-content">
          <div class="card-title">
            <span class="level-code" :style="{ color: level.levelColor || '#409EFF' }">
              {{ level.levelCode }}
            </span>
            <span class="level-name">{{ level.levelName }}</span>
            <el-tag v-if="level.status === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </div>
          <div class="card-stats">
            <div class="stat-item">
              <div class="label">服务折扣</div>
              <div class="value">{{ level.discountRate }}%</div>
            </div>
            <div class="stat-item">
              <div class="label">提成加成</div>
              <div class="value">{{ level.commissionBonus }}%</div>
            </div>
            <div class="stat-item">
              <div class="label">技师数</div>
              <div class="value">{{ level.employeeCount || 0 }}</div>
            </div>
          </div>
          <div class="card-requirements" v-if="level.promotionSales">
            <el-text size="small" type="info">
              晋升需：业绩 ≥ ¥{{ level.promotionSales }} | 好评 ≥ {{ level.promotionRating }}%
            </el-text>
          </div>
        </div>
        <div class="card-actions">
          <el-button type="primary" link size="small" @click.stop="handleEdit(level)">
            编辑
          </el-button>
          <el-dropdown @command="(cmd) => handleCommand(cmd, level)">
            <el-button type="info" link size="small" @click.stop>
              更多<el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="benefit">权益配置</el-dropdown-item>
                <el-dropdown-item :command="level.status === 1 ? 'disable' : 'enable'">
                  {{ level.status === 1 ? '停用' : '启用' }}
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div v-if="!levelList.length" class="empty-state">
        <el-empty description="暂无等级数据，点击新增创建" />
      </div>
    </div>

    <el-card class="toolbar-card">
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增等级
      </el-button>
      <el-button @click="loadData">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </el-card>

    <el-card>
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="等级编码" prop="levelCode" width="100" />
        <el-table-column label="等级名称" prop="levelName" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.levelColor || '#303133', fontWeight: 'bold' }">
              {{ row.levelName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
        <el-table-column label="服务折扣" prop="discountRate" width="100" align="center">
          <template #default="{ row }">
            {{ row.discountRate }}%
          </template>
        </el-table-column>
        <el-table-column label="提成加成" prop="commissionBonus" width="100" align="center">
          <template #default="{ row }">
            {{ row.commissionBonus }}%
          </template>
        </el-table-column>
        <el-table-column label="晋升要求" min-width="250">
          <template #default="{ row }">
            <span v-if="row.promotionSales">业绩 ≥ ¥{{ row.promotionSales }}</span>
            <span v-if="row.promotionRating"> | 好评 ≥ {{ row.promotionRating }}%</span>
            <span v-if="row.promotionMonths"> | 服务 ≥ {{ row.promotionMonths }}月</span>
            <span v-if="!row.promotionSales && !row.promotionRating && !row.promotionMonths">-</span>
          </template>
        </el-table-column>
        <el-table-column label="特权描述" prop="privileges" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="goToBenefit(row)">权益配置</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="等级编码">
          <el-input v-model="form.levelCode" placeholder="如：L1/L2/L3" style="width: 200px" />
        </el-form-item>
        <el-form-item label="等级名称">
          <el-input v-model="form.levelName" placeholder="如：高级技师" style="width: 200px" />
        </el-form-item>
        <el-form-item label="等级颜色">
          <el-color-picker v-model="form.levelColor" />
        </el-form-item>
        <el-form-item label="等级排序">
          <el-input-number v-model="form.sortOrder" :min="1" :max="100" />
          <span class="form-tip">数字越小等级越高</span>
        </el-form-item>
        <el-form-item label="服务折扣">
          <el-input-number v-model="form.discountRate" :min="0" :max="100" :precision="1" :step="1" />
          <span class="form-tip">% (0表示不打折)</span>
        </el-form-item>
        <el-form-item label="提成加成">
          <el-input-number v-model="form.commissionBonus" :min="0" :max="100" :precision="1" :step="1" />
          <span class="form-tip">%</span>
        </el-form-item>
        <el-divider content-position="left">晋升要求</el-divider>
        <el-form-item label="累计业绩">
          <el-input-number v-model="form.promotionSales" :min="0" :precision="2" :step="1000" />
          <span class="form-tip">元</span>
        </el-form-item>
        <el-form-item label="最低好评率">
          <el-input-number v-model="form.promotionRating" :min="0" :max="100" :precision="1" :step="1" />
          <span class="form-tip">%</span>
        </el-form-item>
        <el-form-item label="最低服务月">
          <el-input-number v-model="form.promotionMonths" :min="0" :max="120" />
          <span class="form-tip">月</span>
        </el-form-item>
        <el-form-item label="特权描述">
          <el-input v-model="form.privileges" type="textarea" :rows="3" placeholder="如：可承接VIP客户、享受专属培训等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Medal, Plus, Refresh, ArrowDown } from '@element-plus/icons-vue'
import { technicianLevelApi } from '@/api/technician-level'

const router = useRouter()
const levelList = ref<any[]>([])
const tableData = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增等级')

const form = reactive<any>({
  id: null,
  levelCode: '',
  levelName: '',
  levelColor: '#409EFF',
  sortOrder: 1,
  discountRate: 0,
  commissionBonus: 0,
  promotionSales: null,
  promotionRating: null,
  promotionMonths: null,
  privileges: '',
  status: 1
})

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await technicianLevelApi.list()
    levelList.value = res.data || []
    tableData.value = res.data || []

    // 加载技师分布
    const statRes: any = await technicianLevelApi.statistics()
    const distribution = statRes.data?.levelDistribution || []
    distribution.forEach((item: any) => {
      const found = levelList.value.find(l => l.id === item.id)
      if (found) {
        found.employeeCount = item.employee_count
      }
    })
  } catch (e) {
    console.error('加载等级失败', e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, {
    id: null,
    levelCode: '',
    levelName: '',
    levelColor: '#409EFF',
    sortOrder: 1,
    discountRate: 0,
    commissionBonus: 0,
    promotionSales: null,
    promotionRating: null,
    promotionMonths: null,
    privileges: '',
    status: 1
  })
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增等级'
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  resetForm()
  Object.assign(form, row)
  dialogTitle.value = '编辑等级'
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!form.levelCode || !form.levelName) {
    ElMessage.warning('请填写等级编码和名称')
    return
  }
  try {
    if (form.id) {
      await technicianLevelApi.update(form)
    } else {
      await technicianLevelApi.create(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleStatusChange = async (row: any) => {
  try {
    await technicianLevelApi.toggleStatus(row.id, row.status)
    ElMessage.success('状态已更新')
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('更新失败')
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除等级"${row.levelName}"吗？`, '提示', { type: 'warning' })
    await technicianLevelApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleCommand = async (cmd: string, row: any) => {
  if (cmd === 'delete') {
    handleDelete(row)
  } else if (cmd === 'enable' || cmd === 'disable') {
    const status = cmd === 'enable' ? 1 : 0
    await technicianLevelApi.toggleStatus(row.id, status)
    ElMessage.success('操作成功')
    loadData()
  } else if (cmd === 'benefit') {
    goToBenefit(row)
  }
}

const goToBenefit = (row: any) => {
  router.push({
    path: '/technician/level/benefit',
    query: { levelId: row.id }
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.level-page {
  padding: 20px;
}

.level-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.level-card {
  background: #fff;
  border-radius: 8px;
  border-top: 4px solid #409EFF;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.level-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.card-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f0f9ff, #e0f2fe);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409EFF;
}

.card-content {
  flex: 1;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.level-code {
  font-weight: bold;
  font-size: 14px;
}

.level-name {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.card-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
}

.stat-item {
  text-align: center;
}

.stat-item .label {
  font-size: 12px;
  color: #909399;
}

.stat-item .value {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.card-requirements {
  margin-top: 8px;
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toolbar-card {
  margin-bottom: 16px;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 60px 0;
  background: #fff;
  border-radius: 8px;
}

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
