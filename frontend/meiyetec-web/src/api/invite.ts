import request from './request'

/** 邀请活动配置 */
export interface InviteCampaign {
  id?: number
  campaignCode: string
  campaignName: string
  campaignType?: string
  inviterRewardType: number
  inviterRewardValue?: number
  inviterRewardMeta?: string
  inviteeRewardType: number
  inviteeRewardValue?: number
  inviteeRewardMeta?: string
  inviteeMinConsume?: number
  inviteNeedRegister?: boolean
  inviterQuotaPerDay?: number
  inviterQuotaTotal?: number
  campaignQuotaTotal?: number
  shareTitle?: string
  shareDesc?: string
  shareImageUrl?: string
  shareLandingUrl?: string
  startTime: string
  endTime: string
  status?: number
  remark?: string
  operatorName?: string
  createdAt?: string
  // 漏斗（由后端计算返回）
  scanCount?: number
  registerCount?: number
  firstOrderCount?: number
  rewardCount?: number
  successCount?: number
  conversionRate?: number
  inviterRewardTypeText?: string
  inviteeRewardTypeText?: string
  statusText?: string
}

/** 邀请码 */
export interface InviteCode {
  id?: number
  campaignId: number
  memberId: number
  memberName?: string
  memberPhone?: string
  inviteCode: string
  shortLink?: string
  shareCount?: number
  scanCount?: number
  registerCount?: number
  successCount?: number
  createdAt?: string
  campaignName?: string
  shareTitle?: string
  shareDesc?: string
  shareImageUrl?: string
  endTime?: string
  status?: number
}

/** 邀请记录 */
export interface InviteRecord {
  id?: number
  campaignId: number
  campaignName?: string
  inviteCode: string
  inviterMemberId: number
  inviterName?: string
  inviteeMemberId?: number
  inviteePhone?: string
  inviteeName?: string
  channel?: string
  status: number
  statusText?: string
  registerAt?: string
  firstConsumeAt?: string
  inviterRewardedAt?: string
  inviteeRewardedAt?: string
  failReason?: string
  createdAt?: string
}

/** 奖励发放日志 */
export interface InviteRewardLog {
  id?: number
  recordId?: number
  campaignId: number
  campaignName?: string
  rewardTo?: string
  memberId: number
  memberName?: string
  memberPhone?: string
  rewardType?: number
  rewardTypeText?: string
  rewardName?: string
  rewardValue?: number
  couponGrantId?: number
  pointsRecordId?: number
  walletTxnId?: number
  status?: number
  statusText?: string
  failReason?: string
  isAuto?: boolean
  createdAt?: string
}

/** 统计看板 */
export interface InviteStats {
  activeCampaigns?: number
  totalInviteCodes?: number
  scanCount?: number
  registerCount?: number
  firstOrderCount?: number
  rewardCount?: number
  successCount?: number
  conversionRate?: number
  inviteNewRate?: number
}

export const inviteApi = {
  // === B 端：活动管理 ===
  campaignPage: (params: {
    pageNum?: number
    pageSize?: number
    keyword?: string
    status?: number
  }) => request.get('/m09/invite/campaign/page', { params }),

  campaignDetail: (id: number) => request.get(`/m09/invite/campaign/${id}`),

  campaignCreate: (data: InviteCampaign) => request.post('/m09/invite/campaign', data),

  campaignUpdate: (id: number, data: Partial<InviteCampaign>) =>
    request.put(`/m09/invite/campaign/${id}`, data),

  campaignDelete: (id: number) => request.delete(`/m09/invite/campaign/${id}`),

  campaignToggle: (id: number) => request.post(`/m09/invite/campaign/${id}/toggle`),

  // === B 端：邀请码 & 记录 ===
  codePage: (params: {
    pageNum?: number
    pageSize?: number
    campaignId?: number
    keyword?: string
  }) => request.get('/m09/invite/code/page', { params }),

  recordPage: (params: {
    pageNum?: number
    pageSize?: number
    campaignId?: number
    status?: number
    keyword?: string
  }) => request.get('/m09/invite/record/page', { params }),

  recordDetail: (id: number) => request.get(`/m09/invite/record/${id}`),

  resendReward: (id: number) => request.post(`/m09/invite/record/${id}/resend-reward`),

  // === B 端：看板 & 奖励日志 ===
  stats: (campaignId?: number) =>
    request.get('/m09/invite/stats', { params: campaignId ? { campaignId } : {} }),

  rewardLogPage: (params: {
    pageNum?: number
    pageSize?: number
    campaignId?: number
    status?: number
    rewardType?: number
  }) => request.get('/m09/invite/reward-log/page', { params }),

  // === C 端：会员操作 ===
  sharePage: (code: string) =>
    request.get('/invite/share-page', { params: { code } }),

  trackClick: (data: {
    code: string
    phone?: string
    channel?: string
  }) => request.post('/invite/track', data),

  bindOnRegister: (data: {
    phone: string
    memberId?: number
    name?: string
  }) => request.post('/invite/bind', data),

  redeem: (data: {
    inviteeMemberId: number
    orderId: number
    orderAmount: number
  }) => request.post('/invite/redeem', data),

  myCode: (campaignId: number, memberId: number) =>
    request.get('/invite/my-code', { params: { campaignId, memberId } }),

  myCodes: (memberId: number) =>
    request.get('/invite/my-codes', { params: { memberId } }),

  myStats: (memberId: number) =>
    request.get('/invite/my-stats', { params: { memberId } }),

  reportShare: (codeId: number) =>
    request.post(`/invite/${codeId}/report-share`),
}

export default inviteApi
