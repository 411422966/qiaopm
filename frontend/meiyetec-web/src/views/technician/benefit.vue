<template>
  <div class="benefit-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span>等级权益配置</span>
            <el-tag v-if="currentLevel" style="margin-left: 12px">
              {{ currentLevel.levelName }}
            </el-tag>
          </div>
          <div class="filter-form">
            <el-select
              v-model="queryLevelId"
              placeholder="选择等级"
              style="width: 200px"
              @change="loadData"
            >
              <el-option
                v-for="level in levelList"
                :key="level.id"
                :label="level.levelName"
                :value="level.id"
              />
            </el-select>
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增权益
            </el-button>
            <el-button type="success" @click="handleBatchSave">
              <el-icon><Check /></el-icon>
              保存全部
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="benefits" border stripe v-loading="loading">
        <el-table-column label="权益类型" width="150">
          <template #default="{ row }">
            <el-tag :style="{ background: getBenefitTypeColor(row.benefitType), color: '#fff' }">
              {{ getBenefitTypeLabel(row.benefitType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权益名称" prop="benefitName" width="180">
          <template #default="{ row }">
            <el-input v-model="row.benefitName" placeholder="请输入" />
          </template>
        </el-table-column>
        <el-table-column label="权益值" prop="benefitValue" width="120">
          <template #default="{ row }">
            <el-input v-model="row.benefitValue" placeholder="如：10% / 1.05" />
          </template>
        </el-table-column>
        <el-table-column label="权益说明" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.description" type="textarea" :rows="2" placeholder="请输入说明" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" :max="999" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="handleRemove($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!benefits.length && !loading" class="empty-state">
        <el-empty description="该等级暂无权益" />
      </div>
    </el-card>

    <!-- 新增权益对话框 -->
    <el-dialog v-model="dialogVisible" title="新增权益" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="权益类型">
          <el-select v-model="form.benefitType" placeholder="选择权益类型" style="width: 100%">
            <el-option
              v-for="opt in benefitTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="权益名称">
          <el-input v-model="form.benefitName" placeholder="如：服务九折" />
        </el-form-item>
        <el-form-item label="权益值">
          <el-input v-model="form.benefitValue" placeholder="如：10% 或 1.05" />
        </el-form-item>
        <el-form-item label="权益说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAddConfirm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Check } from '@element-plus/icons-vue'
import { technicianLevelApi, levelBenefitApi, benefitTypeOptions } from '@/api/technician-level'

const route = useRoute()
const queryLevelId = ref<number | null>(null)
const levelList = ref<any[]>([])
const benefits = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive<any>({
  benefitType: 'discount',
  benefitName: '',
  benefitValue: '',
  description: '',
  sortOrder: 0,
  status: 1
})

const currentLevel = computed(() => {
  return levelList.value.find(l => l.id === queryLevelId.value)
})

const getBenefitTypeLabel = (type: string) => {
  const opt = benefitTypeOptions.find(o => o.value === type)
  return opt ? opt.label : type
}

const getBenefitTypeColor = (type: string) => {
  const opt = benefitTypeOptions.find(o => o.value === type)
  return opt ? opt.color : '#909399'
}

const loadLevels = async () => {
  try {
    const res: any = await technicianLevelApi.list()
    levelList.value = res.data || []
    if (route.query.levelId) {
      queryLevelId.value = Number(route.query.levelId)
    } else if (levelList.value.length) {
      queryLevelId.value = levelList.value[0].id
    }
  } catch (e) {
    console.error('加载等级失败', e)
  }
}

const loadData = async () => {
  if (!queryLevelId.value) {
    benefits.value = []
    return
  }
  loading.value = true
  try {
    const res: any = await levelBenefitApi.getByLevelId(queryLevelId.value)
    benefits.value = res.data || []
  } catch (e) {
    console.error('加载权益失败', e)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  Object.assign(form, {
    benefitType: 'discount',
    benefitName: '',
    benefitValue: '',
    description: '',
    sortOrder: benefits.value.length,
    status: 1
  })
  dialogVisible.value = true
}

const handleAddConfirm = () => {
  if (!form.benefitName) {
    ElMessage.warning('请输入权益名称')
    return
  }
  benefits.value.push({
    ...form,
    id: null,
    levelId: queryLevelId.value,
    isNew: true
  })
  dialogVisible.value = false
  ElMessage.success('已添加，点击"保存全部"生效')
}

const handleRemove = (index: number) => {
  benefits.value.splice(index, 1)
}

const handleBatchSave = async () => {
  try {
    await levelBenefitApi.batchSave(queryLevelId.value!, benefits.value)
    ElMessage.success('保存成功')
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

watch(queryLevelId, () => {
  loadData()
})

onMounted(() => {
  loadLevels()
})
</script>

<style scoped>
.benefit-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  gap: 12px;
  align-items: center;
}

.empty-state {
  padding: 60px 0;
  text-align: center;
}
</style>
