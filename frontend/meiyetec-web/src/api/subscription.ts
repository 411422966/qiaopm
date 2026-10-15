import request from './request'

/**
 * P1-8 订阅/会员制（对应后端 SubscriptionService @ /subscription）
 * 模块：计划管理 / 订阅 / 使用 / 续费 / Dashboard
 */
export const subscriptionApi = {
  // 计划
  createPlan: (data: any) => request.post('/subscription/plan', data),
  listPlans: () => request.get('/subscription/plan/list'),
  initDefaultPlans: () => request.post('/subscription/plan/init-default'),

  // 订阅
  subscribe: (data: { planId: number; memberId: number; paymentMethod?: string }) =>
    request.post('/subscription/subscribe', data),
  cancel: (id: number, data?: { reason?: string }) =>
    request.post(`/subscription/${id}/cancel`, data || {}),
  pause: (id: number) => request.post(`/subscription/${id}/pause`),
  resume: (id: number) => request.post(`/subscription/${id}/resume`),
  use: (data: { memberId: number }) => request.post('/subscription/use', data),
  byMember: (memberId: number) => request.get(`/subscription/by-member/${memberId}`),

  // 自动续费
  triggerRenew: () => request.post('/subscription/trigger-renew'),

  // Dashboard
  dashboard: () => request.get('/subscription/dashboard')
}