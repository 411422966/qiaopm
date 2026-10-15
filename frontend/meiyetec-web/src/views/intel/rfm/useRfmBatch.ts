import { ref, computed, onUnmounted } from 'vue'
import { rfmApi } from '../../../api/rfm'
import type {
  BatchProgress,
  BatchHistoryRow,
  BatchTaskHandle
} from '../../../api/rfm'

/**
 * 批量计算 + 示例数据生成 composable
 *
 * 设计：
 *   - batch / seed 都走异步 + 实时进度（POST /intel/rfm/batch/run & /seed/run）
 *   - 后台启动 setInterval(1500ms) 轮询 /batch/progress/{taskId}
 *   - 状态变为 DONE / FAILED / CANCELLED 时自动停轮询
 *   - 暴露 cancel(taskId) 给 UI 调 POST /batch/cancel/{taskId}
 */
export function useRfmBatch() {
  const brandId = ref<number | undefined>(undefined)
  const limit = ref<number>(5000)

  // 任务态
  const currentTaskId = ref<number | null>(null)
  const currentAction = ref<'BATCH' | 'SEED' | null>(null)
  const progress = ref<BatchProgress | null>(null)
  const isPolling = ref(false)

  // 兼容老 UI：批量计算按钮的 loading 态
  const isComputing = ref(false)
  const isSeeding = ref(false)
  const isClearing = ref(false)
  const isLoadingHistory = ref(false)
  const result = ref<BatchComputeResult | null>(null)
  const history = ref<BatchHistoryRow[]>([])
  const error = ref<string | null>(null)
  const info = ref<string | null>(null)
  const historyError = ref<string | null>(null)

  let pollTimer: number | null = null

  // 进度百分比（0-100）
  const progressPercent = computed(() => {
    const p = progress.value
    if (!p) return 0
    if (!p.total || p.total <= 0) return 0
    return Math.min(100, Math.round((p.processed / p.total) * 100))
  })

  const isRunning = computed(() =>
    progress.value?.status === 'RUNNING' && !!currentTaskId.value)

  function stopPolling() {
    if (pollTimer != null) {
      window.clearInterval(pollTimer)
      pollTimer = null
    }
    isPolling.value = false
  }

  async function startPolling(taskId: number) {
    stopPolling()
    isPolling.value = true
    const tick = async () => {
      try {
        const p = await rfmApi.batchProgress(taskId)
        progress.value = p
        if (p.status !== 'RUNNING') {
          stopPolling()
          // 把终止态翻译给 result（批量计算）/ info（seed）
          if (currentAction.value === 'BATCH') {
            result.value = {
              total: p.total,
              success: p.success,
              fail: p.fail,
              durationMs: 0
            }
          }
          if (p.status === 'DONE') {
            info.value = `${currentAction.value === 'SEED' ? '种子' : '批量'}完成：成功 ${p.success} / 失败 ${p.fail}`
          } else if (p.status === 'CANCELLED') {
            info.value = `已取消（已处理 ${p.processed}/${p.total}）`
          } else if (p.status === 'FAILED') {
            error.value = `任务失败：${p.remark ?? '未知错误'}`
          }
          await loadHistory()
        }
      } catch (e: any) {
        stopPolling()
        error.value = e?.message ?? '查询进度失败'
      }
    }
    // 立刻拉一次，再起定时器
    await tick()
    if (progress.value?.status === 'RUNNING') {
      pollTimer = window.setInterval(tick, 1500)
    }
  }

  async function batch() {
    isComputing.value = true
    error.value = null
    result.value = null
    progress.value = null
    currentAction.value = 'BATCH'
    try {
      const handle: BatchTaskHandle = await rfmApi.runBatchAsync({
        brandId: brandId.value,
        limit: limit.value
      })
      currentTaskId.value = handle.taskId
      await startPolling(handle.taskId)
    } catch (e: any) {
      error.value = e?.message ?? '批量计算启动失败'
    } finally {
      isComputing.value = false
    }
  }

  async function seed(amt = 30) {
    isSeeding.value = true
    info.value = null
    progress.value = null
    currentAction.value = 'SEED'
    try {
      const handle: BatchTaskHandle = await rfmApi.runSeedAsync({ limit: amt })
      currentTaskId.value = handle.taskId
      await startPolling(handle.taskId)
    } catch (e: any) {
      error.value = e?.message ?? '示例数据启动失败'
    } finally {
      isSeeding.value = false
    }
  }

  async function cancel() {
    if (!currentTaskId.value) return
    try {
      await rfmApi.cancelBatch(currentTaskId.value)
      // 进度轮询会自动捕获 CANCELLED，无需立即停
    } catch (e: any) {
      error.value = e?.message ?? '取消失败'
    }
  }

  async function clearSeed() {
    isClearing.value = true
    error.value = null
    try {
      const env: any = await rfmApi.clearSeed()
      info.value = env?.message ?? '已清理示例数据'
      await loadHistory()
    } catch (e: any) {
      error.value = e?.message ?? '清理示例数据失败'
    } finally {
      isClearing.value = false
    }
  }

  async function loadHistory() {
    isLoadingHistory.value = true
    historyError.value = null
    try {
      const data = await rfmApi.batchHistory(20, brandId.value)
      history.value = Array.isArray(data) ? data : []
    } catch (e: any) {
      const status = e?.response?.status
      const serverMsg =
        e?.response?.data?.message ||
        e?.response?.data?.msg ||
        e?.message ||
        '加载历史失败'
      historyError.value = status
        ? `加载历史失败（HTTP ${status}）：${serverMsg}`
        : `加载历史失败：${serverMsg}`
      history.value = []
    } finally {
      isLoadingHistory.value = false
    }
  }

  function clear() {
    result.value = null
    error.value = null
    info.value = null
    progress.value = null
    currentTaskId.value = null
    currentAction.value = null
  }

  onUnmounted(() => stopPolling())

  return {
    // 表单
    brandId,
    limit,
    // 状态
    isComputing,
    isSeeding,
    isClearing,
    isLoadingHistory,
    isRunning,
    isPolling,
    currentTaskId,
    currentAction,
    progress,
    progressPercent,
    result,
    history,
    error,
    info,
    historyError,
    // 操作
    batch,
    seed,
    cancel,
    clearSeed,
    loadHistory,
    clear
  }
}
