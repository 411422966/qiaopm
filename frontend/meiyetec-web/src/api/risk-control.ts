import request from './request'

/**
 * 补齐-P1-13 独立资金风控系统
 */
export const riskControlApi = {
  // 规则
  createRule: (data: any) => request.post('/risk-control/rule', data),
  listRules: () => request.get('/risk-control/rule/list'),
  toggleRule: (id: number, data: { enabled: boolean }) => request.post(`/risk-control/rule/${id}/toggle`, data),

  // 触发检测
  checkTransaction: (data: { amount: number; channel: string; memberId?: number }) =>
    request.post('/risk-control/check', data),

  // 拦截记录
  listAlerts: (params?: { level?: string }) => request.get('/risk-control/alert/list', { params }),

  // Dashboard
  dashboard: () => request.get('/risk-control/dashboard')
}