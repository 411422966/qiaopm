import request from './request'

export const packageApi = {
  // 套餐 CRUD
  page: (params: any) => request.get('/packages', { params }),
  detail: (id: number) => request.get(`/packages/${id}`),
  save: (data: any) => request.post('/packages', data),
  update: (data: any) => request.post('/packages', data),
  delete: (id: number) => request.delete(`/packages/${id}`),

  // 购买 / 核销
  purchase: (data: any) => request.post('/packages/purchase', data),
  redeem: (data: any) => request.post('/packages/redeem', data),

  // 会员套餐
  memberPackages: (memberId: number, params: any) =>
    request.get(`/packages/member/${memberId}`, { params }),
  memberRedemptions: (memberId: number, params: any) =>
    request.get(`/packages/redemption/member/${memberId}`, { params }),
  redemptions: (orderId: number, params: any) =>
    request.get(`/packages/order/${orderId}/redemptions`, { params }),

  // 冻结 / 续期
  freeze: (orderId: number, freeze: boolean) =>
    request.post(`/packages/order/${orderId}/freeze`, null, { params: { freeze } }),
  // M04-04：冻结/解冻（带原因 DTO）
  freezeWithReason: (orderId: number, data: { freeze: boolean; operatorId?: number; reason?: string }) =>
    request.post(`/packages/order/${orderId}/freeze`, data, { params: { freeze: data.freeze } }),
  // M04-04：挂起
  suspend: (orderId: number, data: {
    action: 'SUSPEND'
    reason: string
    suspendUntil?: string
    operatorId?: number
  }) => request.post(`/packages/order/${orderId}/suspend`, data),
  // M04-04：恢复挂起
  resume: (orderId: number, data?: {
    action: 'RESUME'
    reason?: string
    operatorId?: number
  }) => request.post(`/packages/order/${orderId}/resume`, data || {}),
  // M04-04：状态变更历史
  statusLogs: (orderId: number) =>
    request.get(`/packages/order/${orderId}/status-logs`),
  extend: (orderId: number, days: number) =>
    request.post(`/packages/order/${orderId}/extend`, null, { params: { days } }),

  // 二期：退款 / 转赠 / 拆分核销
  refund: (orderId: number, params?: { amount?: number; operatorId?: number }) =>
    request.post(`/packages/order/${orderId}/refund`, null, { params: params || {} }),
  transfer: (orderId: number, data: { toMemberId: number; operatorId?: number; remark?: string }) =>
    request.post(`/packages/order/${orderId}/transfer`, data),
  redeemSplit: (data: any) => request.post('/packages/redeem-split', data),

  // M04-07：差价补款 / 套餐升级
  upgrade: (data: {
    fromOrderId: number
    toPackageId: number
    payMethod?: string
    transactionId?: string
    operatorId?: number
    remark?: string
    estimatedDiff?: number
    refundToWallet?: boolean
  }) => request.post('/packages/upgrade', data),

  // 统计
  stats: () => request.get('/packages/stats'),

  // 最近订单 / 最近核销 / 最近会员
  recentOrders: (params: any) => request.get('/packages/orders/recent', { params }),
  recentRedemptions: (params: any) => request.get('/packages/redemptions/recent', { params }),
  recentMembersWithOrders: (limit = 8) => request.get('/packages/members/recent-with-orders', { params: { limit } })
}
