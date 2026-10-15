<template>
  <div class="batch-tab">
    <el-card class="batch-card">
      <div class="batch-card__title">批量计算</div>
      <div class="batch-card__desc">对当前租户下会员重新计算 RFM 标签（受 limit 限制）。</div>

      <el-form :inline="true" class="batch-form">
        <el-form-item label="品牌ID">
          <el-input
            v-model.number="brandId"
            placeholder="可选"
            style="width: 140px"
            clearable
          />
        </el-form-item>
        <el-form-item label="限制">
          <el-input-number
            v-model="limit"
            :min="1"
            :max="50000"
            :step="500"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item>
          <el-popconfirm
            :title="`将对最多 ${limit} 位会员执行批量计算，可能耗时数十秒，是否继续？`"
            confirm-button-text="开始"
            cancel-button-text="取消"
            @confirm="runBatch"
          >
            <template #reference>
              <el-button type="primary" :loading="isComputing" :disabled="isRunning">开始批量计算</el-button>
            </template>
          </el-popconfirm>
          <el-button
            v-if="isRunning && currentAction === 'BATCH'"
            type="danger"
            plain
            :loading="isPolling"
            @click="onCancel"
          >停止</el-button>
        </el-form-item>
      </el-form>

      <!-- 实时进度（BATCH / SEED 通用） -->
      <div v-if="progress && currentAction === 'BATCH'" class="progress-block">
        <div class="progress-block__head">
          <span>
            <el-tag :type="progress.status === 'RUNNING' ? 'primary' : (progress.status === 'DONE' ? 'success' : (progress.status === 'CANCELLED' ? 'info' : 'danger'))" size="small">
              {{ statusLabel(progress.status) }}
            </el-tag>
            <span class="progress-block__text">
              处理 {{ progress.processed }} / {{ progress.total }}　成功 {{ progress.success }}　失败 {{ progress.fail }}
            </span>
          </span>
          <span class="progress-block__pct">{{ progressPercent }}%</span>
        </div>
        <el-progress
          :percentage="progressPercent"
          :status="progress.status === 'DONE' ? 'success' : (progress.status === 'CANCELLED' || progress.status === 'FAILED' ? 'exception' : undefined)"
          :stroke-width="14"
        />
      </div>

      <el-alert
        v-if="result && !progress"
        type="success"
        :closable="true"
        show-icon
        class="batch-result"
      >
        <template #title>
          <span class="batch-result__title">✅ 批量计算完成</span>
        </template>
        <div class="batch-result__grid">
          <div class="batch-result__item">
            <div class="batch-result__label">总数</div>
            <div class="batch-result__value">{{ result.total }}</div>
          </div>
          <div class="batch-result__item">
            <div class="batch-result__label">成功</div>
            <div class="batch-result__value ok">{{ result.success }}</div>
          </div>
          <div class="batch-result__item">
            <div class="batch-result__label">失败</div>
            <div class="batch-result__value" :class="result.fail > 0 ? 'fail' : 'ok'">{{ result.fail }}</div>
          </div>
          <div class="batch-result__item">
            <div class="batch-result__label">耗时</div>
            <div class="batch-result__value">{{ formatDuration(result.durationMs) }}</div>
          </div>
        </div>
      </el-alert>
    </el-card>

    <el-card class="batch-card">
      <div class="batch-card__title">示例数据</div>
      <div class="batch-card__desc">
        当前 tenant 下若 RFM 快照为空，可点击下方按钮对前 N 位会员生成示例快照（不影响真实订单）。
      </div>

      <div class="seed-actions">
        <el-popconfirm
          title="将生成示例数据，是否继续？"
          confirm-button-text="生成"
          cancel-button-text="取消"
          @confirm="runSeed(10)"
        >
          <template #reference>
            <el-button :loading="isSeeding" :disabled="isRunning">生成 10 条</el-button>
          </template>
        </el-popconfirm>
        <el-popconfirm
          title="将生成 30 条示例数据，是否继续？"
          confirm-button-text="生成"
          cancel-button-text="取消"
          @confirm="runSeed(30)"
        >
          <template #reference>
            <el-button :loading="isSeeding" :disabled="isRunning">生成 30 条</el-button>
          </template>
        </el-popconfirm>
        <el-popconfirm
          title="将生成 100 条示例数据，是否继续？"
          confirm-button-text="生成"
          cancel-button-text="取消"
          @confirm="runSeed(100)"
        >
          <template #reference>
            <el-button :loading="isSeeding" :disabled="isRunning">生成 100 条</el-button>
          </template>
        </el-popconfirm>
        <el-popconfirm
          title="确定清空今天生成的所有示例快照？该操作不可恢复。"
          confirm-button-text="清空"
          cancel-button-text="取消"
          @confirm="onClearSeed"
        >
          <template #reference>
            <el-button type="danger" plain :loading="isClearing">清空今日示例</el-button>
          </template>
        </el-popconfirm>
        <el-button
          v-if="isRunning && currentAction === 'SEED'"
          type="danger"
          plain
          :loading="isPolling"
          @click="onCancel"
        >停止</el-button>
      </div>

      <!-- 实时进度（SEED） -->
      <div v-if="progress && currentAction === 'SEED'" class="progress-block">
        <div class="progress-block__head">
          <span>
            <el-tag :type="progress.status === 'RUNNING' ? 'primary' : (progress.status === 'DONE' ? 'success' : (progress.status === 'CANCELLED' ? 'info' : 'danger'))" size="small">
              {{ statusLabel(progress.status) }}
            </el-tag>
            <span class="progress-block__text">
              重算 {{ progress.processed }} / {{ progress.total }}　成功 {{ progress.success }}　失败 {{ progress.fail }}
            </span>
          </span>
          <span class="progress-block__pct">{{ progressPercent }}%</span>
        </div>
        <el-progress
          :percentage="progressPercent"
          :status="progress.status === 'DONE' ? 'success' : (progress.status === 'CANCELLED' || progress.status === 'FAILED' ? 'exception' : undefined)"
          :stroke-width="14"
        />
      </div>

      <el-alert
        v-if="info"
        :title="info"
        type="success"
        :closable="false"
        show-icon
        class="seed-info"
      />
    </el-card>

    <el-card class="batch-card">
      <div class="batch-card__head">
        <div>
          <div class="batch-card__title">最近执行历史</div>
          <div class="batch-card__desc">展示最近 20 次批量/示例任务，含耗时与失败数。</div>
        </div>
        <el-button size="small" :loading="isLoadingHistory" @click="loadHistory">🔄 刷新</el-button>
      </div>

      <el-alert
        v-if="historyError"
        :title="historyError"
        type="error"
        :closable="false"
        show-icon
        class="history-error"
      />

      <el-empty v-if="!isLoadingHistory && history.length === 0" description="暂无历史记录" />

      <el-table v-else :data="history" stripe size="small" class="history-table">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag :type="row.action === 'SEED' ? 'warning' : 'primary'" size="small">
              {{ actionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总数" width="80" align="right">
          <template #default="{ row }">{{ row.total }}</template>
        </el-table-column>
        <el-table-column label="成功" width="80" align="right">
          <template #default="{ row }">
            <span class="ok">{{ row.success }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失败" width="80" align="right">
          <template #default="{ row }">
            <span :class="row.fail > 0 ? 'fail' : 'ok'">{{ row.fail }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.operatorName ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <span class="remark">{{ row.remark ?? '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRfmBatch } from './useRfmBatch'

const {
  brandId,
  limit,
  isComputing,
  isSeeding,
  isClearing,
  isLoadingHistory,
  isRunning,
  isPolling,
  currentAction,
  progress,
  progressPercent,
  result,
  history,
  error,
  info,
  historyError,
  batch,
  seed,
  cancel,
  clearSeed,
  loadHistory
} = useRfmBatch()

async function runBatch() { await batch() }
async function runSeed(n: number) { await seed(n) }
async function onClearSeed() { await clearSeed() }
async function onCancel() { await cancel() }

function statusLabel(s: string): string {
  if (s === 'RUNNING') return '运行中'
  if (s === 'DONE') return '已完成'
  if (s === 'CANCELLED') return '已取消'
  if (s === 'FAILED') return '失败'
  return s
}

function formatTime(t?: string) {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

function actionLabel(a?: string) {
  if (a === 'SEED') return '示例数据'
  if (a === 'BATCH') return '批量计算'
  return a ?? '—'
}

function formatDuration(ms?: number) {
  const v = Number(ms) || 0
  if (v < 1000) return `${v} ms`
  if (v < 60000) return `${(v / 1000).toFixed(2)} 秒`
  const m = Math.floor(v / 60000)
  const s = Math.floor((v % 60000) / 1000)
  return `${m} 分 ${s} 秒`
}

onMounted(() => { loadHistory() })
</script>

<style scoped>
.batch-tab { display: flex; flex-direction: column; gap: 12px; }
.batch-card { background: #ffffff; border-radius: 8px; }
.batch-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.batch-card__title { font-weight: 600; color: #303133; margin-bottom: 4px; }
.batch-card__desc { font-size: 13px; color: #909399; margin-bottom: 12px; }
.batch-card__head .batch-card__desc { margin-bottom: 0; }
.batch-form { margin-bottom: 8px; }
.seed-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.history-table { margin-top: 12px; }
.history-error { margin-top: 12px; }
.batch-result { margin-top: 8px; }
.batch-result__title { font-weight: 600; }
.batch-result__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 6px;
}
.batch-result__item {
  text-align: center;
  padding: 6px 8px;
  background: rgba(255,255,255,0.6);
  border-radius: 6px;
}
.batch-result__label { font-size: 12px; color: #909399; }
.batch-result__value { font-size: 18px; font-weight: 600; color: #303133; margin-top: 2px; }
.seed-info { margin-top: 12px; }
.progress-block { margin-top: 12px; padding: 10px 12px; background: #f7f9fc; border-radius: 6px; }
.progress-block__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
.progress-block__text { margin-left: 8px; }
.progress-block__pct { font-weight: 600; color: #303133; }
.ok { color: #67C23A; font-weight: 600; }
.fail { color: #F56C6C; font-weight: 600; }
.remark { color: #606266; font-size: 12px; }
</style>
