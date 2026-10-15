import request from './request'

/**
 * 效期追踪 API —— 后端 ExpiryController (/expiry/*)
 */
export const expiryApi = {
  // 看板
  dashboard: () => request.get('/expiry/dashboard'),
  totalLoss: () => request.get('/expiry/total-loss'),
  statisticsByMethod: (params: any) =>
    request.get('/expiry/statistics/method', { params }),

  // 扫描
  triggerScan: (params?: any) =>
    request.post('/expiry/scan/trigger', params || {}),
  nearExpiry: (params: { withinDays?: number; storeId?: number }) =>
    request.get('/expiry/near-expiry', { params }),

  // 预警
  alerts: (params: any) => request.get('/expiry/alerts', { params }),
  handleAlert: (id: number, data: any) =>
    request.post(`/expiry/alerts/${id}/handle`, data),
  ignoreAlert: (id: number) =>
    request.post(`/expiry/alerts/${id}/ignore`),

  // 处理记录
  handles: (params: any) => request.get('/expiry/handles', { params }),
  createHandle: (data: any) => request.post('/expiry/handles', data),

  // 规则
  rules: (params?: any) => request.get('/expiry/rules', { params: params || {} }),
  createRule: (data: any) => request.post('/expiry/rules', data),
  updateRule: (data: any) => request.put(`/expiry/rules/${data.id}`, data),
  deleteRule: (id: number) => request.delete(`/expiry/rules/${id}`)
}

export const alertLevelOptions = [
  { value: 'critical', label: '严重', type: 'danger' },
  { value: 'warning', label: '警告', type: 'warning' },
  { value: 'normal', label: '正常', type: 'success' }
]

export const alertStatusOptions = [
  { value: 'pending', label: '待处理', type: 'warning' },
  { value: 'processing', label: '处理中', type: 'info' },
  { value: 'resolved', label: '已处理', type: 'success' },
  { value: 'ignored', label: '已忽略', type: 'info' }
]

export const alertTypeOptions = [
  { value: 'near_expiry', label: '临期' },
  { value: 'expired', label: '已过期' },
  { value: 'stale', label: '呆滞' }
]

export const handleMethodOptions = [
  { value: 'dispose', label: '销毁' },
  { value: 'return', label: '退货' },
  { value: 'promote', label: '促销' },
  { value: 'transfer', label: '调拨' },
  { value: 'use_first', label: '先用先出' }
]

export const categoryOptions = [
  { value: 'skincare', label: '护肤类' },
  { value: 'cleanse', label: '清洁类' },
  { value: 'mask', label: '面膜类' },
  { value: 'medicine', label: '医药类' },
  { value: 'tool', label: '工具类' }
]

export const notifyChannelOptions = [
  { value: 'system', label: '系统消息' },
  { value: 'wechat', label: '微信' },
  { value: 'sms', label: '短信' },
  { value: 'all', label: '全部' }
]
