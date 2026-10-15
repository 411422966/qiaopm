import request from './request'

/**
 * 项目自动扣减 API
 */
export const serviceMaterialApi = {
  // ========== 绑定规则 ==========
  rules: (params: { serviceId?: number; productId?: number }) =>
    request.get('/stock/service-material/rule/list', { params }),
  rulesByService: (serviceId: number) => request.get(`/stock/service-material/rule/service/${serviceId}`),
  createRule: (data: any) => request.post('/stock/service-material/rule', data),
  updateRule: (data: any) => request.put(`/stock/service-material/rule/${data.id}`, data),
  deleteRule: (id: number) => request.delete(`/stock/service-material/rule/${id}`),
  replaceRules: (serviceId: number, rules: any[]) =>
    request.put(`/stock/service-material/rule/service/${serviceId}/replace`, rules),

  // ========== 扣减日志 ==========
  deductLogs: (params: { orderId?: number; status?: string; storeId?: number }) =>
    request.get('/stock/service-material/deduct-log/list', { params }),
  statistics: (params: { storeId?: number; startDate?: string; endDate?: string }) =>
    request.get('/stock/service-material/deduct-log/statistics', { params }),
  successRate: (params: { storeId?: number; date?: string }) =>
    request.get('/stock/service-material/deduct-log/success-rate', { params }),

  // ========== 库存不足 ==========
  shortages: (params: { status?: string; storeId?: number }) =>
    request.get('/stock/shortage/list', { params }),

  // ========== 扣减执行 ==========
  execute: (data: any) => request.post('/stock/service-material/deduct/execute', data),
  rollback: (data: { orderId: number; reason: string }) =>
    request.post('/stock/service-material/deduct/rollback', data)
}

/**
 * 扣减状态选项
 */
export const deductStatusOptions = [
  { value: 'success', label: '成功', type: 'success' },
  { value: 'partial', label: '部分成功', type: 'warning' },
  { value: 'failed', label: '失败', type: 'danger' }
]

/**
 * 扣减策略选项
 */
export const deductStrategyOptions = [
  { value: 'fefo', label: 'FEFO(先过期先出)', description: '优先消耗即将过期批次' },
  { value: 'fifo', label: 'FIFO(先入先出)', description: '按入库时间顺序' },
  { value: 'substitute', label: '替代耗材', description: '使用预设替代品' },
  { value: 'manual', label: '手动指定', description: '指定批次' }
]

/**
 * 库存不足告警级别
 */
export const shortageLevelOptions = [
  { value: 'critical', label: '严重不足', type: 'danger', description: '缺口>10' },
  { value: 'warning', label: '轻微不足', type: 'warning', description: '缺口≤10' }
]

/**
 * 短缺状态
 */
export const shortageStatusOptions = [
  { value: 'pending', label: '待处理', type: 'danger' },
  { value: 'resolved', label: '已替代', type: 'success' }
]