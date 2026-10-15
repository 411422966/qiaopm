import request from './request'

/**
 * P0-3 多人共享套餐（对应后端 P2FeaturesService @ /crm/package/share）
 */
export const packageShareApi = {
  // 统计
  stats: () => request.get('/crm/package/share/stats'),

  // 分页列表
  page: (params?: any) => request.get('/crm/package/share/page', { params }),

  // 开启共享
  enableShare: (orderId: number, maxMembers = 5, extra?: {
    defaultQuota?: number | null
    memberPhone?: string
    memberName?: string
  }) => request.post(`/crm/package/${orderId}/enable-share`, extra || {}, { params: { maxMembers } }),

  // 邀请共享（owner 拉亲友进套餐）
  inviteShare: (data: { packageOrderId: number; memberId: number; maxTimes?: number }) =>
    request.post('/crm/package/share/invite', data),

  // 查某套餐所有共享成员
  listShares: (packageOrderId: number) =>
    request.get('/crm/package/share/list', { params: { packageOrderId } }),

  // 共享核销（被邀请人扣原套餐次数）
  redeemShared: (data: {
    packageOrderId: number
    shareMemberId: number
    qty?: number
    version: number
  }) => request.post('/crm/package/share/redeem', data),

  // 暂停共享
  pause: (packageOrderId: number, memberId: number) =>
    request.post('/crm/package/share/pause', null, { params: { packageOrderId, memberId } }),

  // 恢复共享
  resume: (packageOrderId: number, memberId: number) =>
    request.post('/crm/package/share/resume', null, { params: { packageOrderId, memberId } }),

  // 撤销共享
  revoke: (packageOrderId: number, memberId: number) =>
    request.post('/crm/package/share/revoke', null, { params: { packageOrderId, memberId } }),

  // 调整配额
  adjustQuota: (packageOrderId: number, memberId: number, newQuota: number | null) =>
    request.put('/crm/package/share/quota', null, { params: { packageOrderId, memberId, newQuota } }),
}
