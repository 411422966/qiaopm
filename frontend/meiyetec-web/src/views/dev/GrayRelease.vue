<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">灰度发布控制台</span>
          <el-tag :type="activeFlags > 0 ? 'success' : 'info'">
            {{ activeFlags }} 个灰度策略生效
          </el-tag>
        </div>
      </template>

      <!-- 灰度阶段说明 -->
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        <template #title>灰度发布三阶段：10% → 50% → 100%</template>
        <span>当前阶段：</span>
      </el-alert>
      <el-steps :space="120" :active="currentPhase" finish-status="success" align-center style="margin-bottom: 16px">
        <el-step title="10%" description="内测用户" />
        <el-step title="50%" description="会员用户" />
        <el-step title="100%" description="全量用户" />
      </el-steps>

      <!-- Feature Flag 列表 -->
      <el-table :data="flags" v-loading="loading" stripe>
        <el-table-column prop="flagKey" label="Feature Key" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row._enabled"
              :active-value="1"
              :inactive-value="0"
              @change="toggle(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="灰度比例" width="220">
          <template #default="{ row }">
            <el-slider
              v-model="row._rolloutPercent"
              :min="0" :max="100" :step="5"
              :disabled="row._enabled !== 1"
              @change="updateRollout(row)"
              show-input
            />
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">
            <span class="mono">{{ row.startAt }} ~ {{ row.endAt || '长期' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="evaluate(row)">测试评估</el-button>
            <el-button size="small" @click="viewStats(row)">统计</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 创建 Feature Flag -->
      <el-divider content-position="left">新建灰度策略</el-divider>
      <el-form :model="newFlag" inline @submit.prevent>
        <el-form-item label="Feature Key">
          <el-input v-model="newFlag.flagKey" placeholder="如: new-checkout-v2" />
        </el-form-item>
        <el-form-item label="灰度比例">
          <el-input-number v-model="newFlag.rolloutPercent" :min="0" :max="100" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="createFlag" :loading="creating">创建策略</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 灰度阶段控制 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <span class="title">灰度阶段控制</span>
      </template>
      <el-space wrap>
        <el-button
          v-for="phase in phases"
          :key="phase.value"
          :type="currentPhase >= phase.value ? 'success' : 'default'"
          :disabled="currentPhase >= phase.value"
          @click="advancePhase(phase.value)"
        >
          {{ phase.label }} ({{ phase.percent }}%)
        </el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sysApi } from '@/api/sys'

const loading = ref(false)
const creating = ref(false)
const flags = ref<any[]>([])
const currentPhase = ref(0)

const newFlag = reactive({
  flagKey: '',
  rolloutPercent: 10,
  enabled: 1
})

const phases = [
  { label: '10% 阶段', value: 0, percent: 10 },
  { label: '50% 阶段', value: 1, percent: 50 },
  { label: '100% 阶段', value: 2, percent: 100 }
]

const activeFlags = computed(() => flags.value.filter(f => f._enabled === 1).length)

async function load() {
  loading.value = true
  try {
    const r = await sysApi.grayFlagList()
    flags.value = ((r as any).data || []).map((f: any) => ({
      ...f,
      _enabled: f.enabled,
      _rolloutPercent: f.rolloutPercent || 0
    }))
  } finally {
    loading.value = false
  }
}

async function toggle(row: any) {
    await sysApi.grayFlagToggle(row.flagKey, { enabled: row._enabled })
  ElMessage.success(row._enabled ? '已启用' : '已禁用')
  await load()
}

async function updateRollout(row: any) {
    await sysApi.grayFlagToggle(row.flagKey, { rolloutPercent: row._rolloutPercent })
  ElMessage.success('灰度比例已更新')
}

async function createFlag() {
  if (!newFlag.flagKey) {
    ElMessage.warning('请输入 Feature Key')
    return
  }
  creating.value = true
  try {
    await sysApi.grayFlagCreate({
      flagKey: newFlag.flagKey,
      rolloutPercent: newFlag.rolloutPercent,
      enabled: 1,
      userRule: 'HASH'
    })
    ElMessage.success('策略已创建')
    newFlag.flagKey = ''
    newFlag.rolloutPercent = 10
    await load()
  } finally {
    creating.value = false
  }
}

async function evaluate(row: any) {
  const myUserId = 1 // 当前用户（实际应从 store 取）
  const r = await sysApi.grayFlagEvaluate(row.flagKey, myUserId)
  const d = (r as any).data || {}
  ElMessage.success(`当前用户 ${myUserId}: ${d.enabled ? '✅ 命中新版本' : '❌ 命中旧版本'}`)
}

async function viewStats(row: any) {
  const r = await sysApi.grayExpStats(row.flagKey)
  const d = (r as any).data || {}
  ElMessage.info(`累计命中用户: ${d.totalUsers || 0}`)
}

function advancePhase(phase: number) {
  currentPhase.value = phase + 1
  const percent = phases[phase + 1]?.percent || 100
  ElMessage.success(`灰度推进至 ${percent}%`)
  // 批量更新所有 Flag 的灰度比例
  flags.value.forEach(f => {
    if (f._enabled === 1) {
      updateRollout({ ...f, _rolloutPercent: percent })
    }
  })
}

onMounted(load)
</script>

<style scoped>
.page { padding: 16px; max-width: 1100px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; color: #606266 }
</style>
